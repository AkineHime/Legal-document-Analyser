/*
 * Copyright 2026 The Statigate Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.statigate.desktop.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.statigate.desktop.FxTestSupport;
import io.statigate.desktop.state.AppState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalysisEngineTest {

    private static final Path SAMPLE =
            Path.of("..", "samples", "sample_service_agreement.pdf").toAbsolutePath().normalize();
    // Points nowhere, so the engine runs keyword-only: fast, deterministic, no 100MB model needed.
    private static final EngineSettings MODEL_FREE = new EngineSettings(Path.of("__none__"), 1, false);

    private AnalysisEngine engine;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(FxTestSupport.available(), "JavaFX toolkit unavailable in this environment");
        Assumptions.assumeTrue(Files.isRegularFile(SAMPLE), "sample fixture missing");
        engine = new AnalysisEngine(MODEL_FREE);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void secondSubmitWhileBusyIsRejected() throws Exception {
        AppState stateA = new AppState();
        AppState stateB = new AppState();

        var taskA = engine.submit(SAMPLE, stateA);
        assertTrue(taskA.isPresent(), "first submit should be accepted");

        // No sleep/yield here on purpose: busy flips to true synchronously inside submit(), so this
        // second call is guaranteed to observe it before the (much slower) analysis can finish.
        var taskB = engine.submit(SAMPLE, stateB);
        assertTrue(taskB.isEmpty(), "second submit while busy must be rejected, not queued");

        awaitTerminal(taskA.get());
    }

    @Test
    void busyFlagReleasesAfterCompletionAndAllowsAnotherRun() throws Exception {
        AppState stateA = new AppState();
        var taskA = engine.submit(SAMPLE, stateA).orElseThrow();
        awaitTerminal(taskA);

        assertFalse(engine.isBusy(), "engine should be idle again once the task reaches a terminal state");

        AppState stateB = new AppState();
        var taskB = engine.submit(SAMPLE, stateB);
        assertTrue(taskB.isPresent(), "a fresh submit after completion should be accepted");
        awaitTerminal(taskB.get());
    }

    @Test
    void successfulAnalysisReportsAllFourStagesToAppState() throws Exception {
        AppState state = new AppState();
        var task = engine.submit(SAMPLE, state).orElseThrow();
        Worker.State terminal = awaitTerminal(task);

        assertEquals(Worker.State.SUCCEEDED, terminal);
        // The stage callback hops onto the FX Application Thread via Platform.runLater, which may
        // land a moment after the task itself reports SUCCEEDED, so give it a short, bounded window.
        // AppState's list is only ever touched on the FX Application Thread, so it is read back
        // through the same thread here rather than racing it from the test thread directly.
        List<String> stages = waitUntilOnFxThread(() -> List.copyOf(state.completedStages()),
                list -> list.size() == 4, 2000);
        assertEquals(List.of("ingestion", "extraction", "grounding", "advisory"), stages);
    }

    @Test
    void updateSettingsIsRejectedWhileBusyAndAcceptedWhenIdle() throws Exception {
        assertTrue(engine.updateSettings(new EngineSettings(Path.of("__still_none__"), 2, false)),
                "should be accepted while idle");

        AppState state = new AppState();
        var task = engine.submit(SAMPLE, state).orElseThrow();
        assertFalse(engine.updateSettings(new EngineSettings(Path.of("__nope__"), 3, false)),
                "should be rejected while an analysis is running");
        awaitTerminal(task);

        assertTrue(engine.updateSettings(new EngineSettings(Path.of("__ok_now__"), 4, false)),
                "should be accepted again once idle");
    }

    @Test
    void aTaskThatExceedsItsTimeoutIsCancelledAndReleasesBusy() throws Exception {
        // A near-zero timeout guarantees the watchdog fires before (or immediately as) the task
        // runs, without needing a genuinely slow file to prove the cancellation path works.
        try (AnalysisEngine impatient = new AnalysisEngine(MODEL_FREE, Duration.ofMillis(1))) {
            AppState state = new AppState();
            var task = impatient.submit(SAMPLE, state).orElseThrow();

            Worker.State terminal = awaitTerminal(task);

            assertEquals(Worker.State.CANCELLED, terminal);
            assertFalse(impatient.isBusy(), "busy must release even when the task was cancelled by timeout");

            // Not reusing `impatient` for this part on purpose: its own 1ms timeout would cancel
            // any submission, which would prove nothing further. This instead confirms that a
            // wholly separate engine's analyses are unaffected by another engine's abandoned,
            // timed-out thread (each submission gets its own disposable executor - see class
            // javadoc - so there is no shared thread pool either could get stuck behind).
            var next = engine.submit(SAMPLE, new AppState());
            assertTrue(next.isPresent());
            assertEquals(Worker.State.SUCCEEDED, awaitTerminal(next.get()));
        }
    }

    @Test
    void closeIsIdempotentAndSubmitAfterCloseIsRejected() {
        engine.close();
        engine.close(); // must not throw a second time

        var task = engine.submit(SAMPLE, new AppState());
        assertTrue(task.isEmpty(), "submit after close must be rejected");
    }

    /**
     * Waits for a task to reach a terminal state from a plain (non-FX) test thread and returns that
     * state.
     *
     * <p>Every touch of {@code task} - even {@code getState()} - is thread-checked by JavaFX and
     * throws if called off the FX Application Thread, so both the "did it already finish" check and
     * the listener attachment happen together inside one {@code Platform.runLater}, which also
     * closes the race where the task could otherwise finish in the gap between checking and
     * attaching. The observed terminal state is hopped back to this thread through an
     * {@link AtomicReference} written immediately before {@link CountDownLatch#countDown()}; {@code
     * CountDownLatch} guarantees that write is visible after {@link CountDownLatch#await}.
     */
    private static Worker.State awaitTerminal(Task<?> task) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Worker.State> terminal = new AtomicReference<>();
        Platform.runLater(() -> {
            task.stateProperty().addListener((obs, oldState, newState) -> {
                if (isTerminal(newState)) {
                    terminal.set(newState);
                    latch.countDown();
                }
            });
            if (isTerminal(task.getState())) {
                terminal.set(task.getState());
                latch.countDown();
            }
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS), "analysis did not finish within 15s");
        return terminal.get();
    }

    private static boolean isTerminal(Worker.State state) {
        return state == Worker.State.SUCCEEDED || state == Worker.State.FAILED
                || state == Worker.State.CANCELLED;
    }

    /**
     * Polls a value that must only be read on the FX Application Thread, via {@link
     * CompletableFuture} (which provides real happens-before edges, unlike racing a plain field
     * from a test thread), until {@code done} is satisfied or the timeout elapses.
     */
    private static <T> T waitUntilOnFxThread(java.util.function.Supplier<T> read,
            java.util.function.Predicate<T> done, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        T last;
        do {
            CompletableFuture<T> future = new CompletableFuture<>();
            Platform.runLater(() -> future.complete(read.get()));
            last = future.get(2, TimeUnit.SECONDS);
            if (done.test(last)) {
                return last;
            }
            Thread.sleep(20);
        } while (System.currentTimeMillis() < deadline);
        assertTrue(done.test(last), "condition did not become true within " + timeoutMillis + "ms (last=" + last + ")");
        return last;
    }
}
