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

import io.statigate.desktop.state.AppState;
import io.statigate.nlp.ModelLocator;
import io.statigate.nlp.NlpRuntime;
import io.statigate.pipeline.AnalysisPipeline;
import io.statigate.pipeline.PipelineResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the analysis pipeline's lifecycle and turns "analyze this file" into a JavaFX {@link Task}
 * the UI can observe, without ever blocking the FX Application Thread.
 *
 * <h2>Concurrency model</h2>
 *
 * <p>Exactly one analysis may be in flight at a time. This is not a correctness requirement of the
 * underlying pipeline - {@code OnnxTextEncoder.embed} is explicitly documented as safe to call
 * concurrently - it is a deliberate desktop-app policy: a user mashing "Analyze" (or a
 * programmatic flood of drag-and-drop events) must not be able to spin up unbounded concurrent
 * ONNX inference on a single machine. {@link #submit} enforces this with a single {@link
 * AtomicBoolean} busy flag; a second call while one analysis is running returns {@link
 * Optional#empty()} instead of queuing silently or throwing.
 *
 * <p>{@link #submit} and {@link #updateSettings} must both be called from the FX Application
 * Thread (the natural place for UI-triggered actions), which is also what makes the busy-flag
 * check-then-act free of races without extra locking: the FX Application Thread is single-threaded
 * by definition, so "check busy, then set it" cannot interleave with another call to either method.
 * The one field genuinely written from one thread (FX) and read from another (a worker thread) is
 * {@code settings}, which is {@code volatile} for that reason.
 *
 * <h2>Timeout and executor lifetime</h2>
 *
 * <p>{@link #submit} arms a watchdog that cancels the task if it has not finished within {@link
 * #ANALYSIS_TIMEOUT}. Without this, a pathological input (or the first-ever run paying the
 * one-time cost of loading the full transformer model while the machine is otherwise busy) could
 * leave the UI on its loading screen indefinitely with no way to tell "still working" from "stuck".
 * Cancellation is currently the only way a submitted task ever reaches {@code CANCELLED}, so a
 * caller can treat that state as "this took too long" without needing extra signaling.
 *
 * <p>Timeout cancellation deliberately does <em>not</em> interrupt the running worker thread:
 * {@code Task.cancel()} flips the task's own state to {@code CANCELLED} (freeing {@code busy})
 * regardless, but forcibly interrupting arbitrary native/library code (PDFBox, ONNX Runtime)
 * risks corrupting whatever it was in the middle of rather than stopping cleanly - concretely
 * observed as a following analysis failing to open a PDF that had just been force-interrupted
 * mid-read. A cancelled task's thread is instead simply abandoned to finish (or not) on its own; a
 * fresh, disposable single-thread executor is created for every {@link #submit} call rather than
 * one long-lived shared executor, specifically so an abandoned thread can never block a later,
 * unrelated analysis from starting. The cached {@code pipeline}/{@code runtime} are likewise
 * dropped (not closed - the abandoned thread may still be using them) on a timeout, so a later
 * analysis always builds its own rather than racing the abandoned one for shared native state; the
 * cost is re-paying model load if that happens to land while a load was in progress. Access to
 * {@code pipeline}/{@code runtime}/{@code builtWith} is synchronized on {@link #pipelineLock} to
 * make that handoff safe now that more than one worker thread can exist at once.
 */
public final class AnalysisEngine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AnalysisEngine.class);

    /**
     * A hard ceiling on one analysis, so a pathological or unexpectedly large/complex document can
     * never leave the UI stuck on the loading screen forever. Generous on purpose: a genuine cold
     * start (loading the ~110MB quantized model for the first time) plus a large document can
     * legitimately take tens of seconds on a loaded machine.
     */
    public static final Duration ANALYSIS_TIMEOUT = Duration.ofMinutes(3);

    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "statigate-analysis-watchdog");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private volatile EngineSettings settings;
    /** The executor backing whichever analysis is currently in flight, for {@link #close}. */
    private volatile ExecutorService currentExecutor;

    private final Object pipelineLock = new Object();
    private NlpRuntime runtime;
    private AnalysisPipeline pipeline;
    private EngineSettings builtWith;

    private final Duration timeout;

    public AnalysisEngine(EngineSettings initialSettings) {
        this(initialSettings, ANALYSIS_TIMEOUT);
    }

    /** As {@link #AnalysisEngine(EngineSettings)}, with an explicit timeout - mainly for tests. */
    AnalysisEngine(EngineSettings initialSettings, Duration timeout) {
        this.settings = Objects.requireNonNull(initialSettings, "initialSettings");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    public boolean isBusy() {
        return busy.get();
    }

    public EngineSettings settings() {
        return settings;
    }

    /**
     * Applies new settings for the next analysis. Refused while an analysis is running (the model
     * runtime it would need to rebuild may be in active use), so the UI should disable its settings
     * control whenever {@link #isBusy()} is true.
     *
     * @return true if applied, false if an analysis is currently running
     */
    public boolean updateSettings(EngineSettings newSettings) {
        Objects.requireNonNull(newSettings, "newSettings");
        if (busy.get()) {
            return false;
        }
        this.settings = newSettings;
        return true;
    }

    /**
     * Starts analyzing {@code file} in the background and returns its {@link Task}, or {@link
     * Optional#empty()} if an analysis is already running. The caller is expected to attach {@code
     * setOnSucceeded}/{@code setOnFailed}/{@code setOnCancelled} handlers (these always run on the
     * FX Application Thread, a guarantee {@link Task} itself provides) to update the UI, then submit
     * nothing further until that task reaches a terminal state.
     *
     * <p>Real, non-simulated stage progress is reported to {@code state} as the pipeline completes
     * each of its four stages; that callback runs on the analysis thread, so it is marshalled back
     * to the FX Application Thread here rather than requiring every caller to remember to.
     */
    public Optional<Task<PipelineResult>> submit(Path file, AppState state) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(state, "state");
        if (closed.get()) {
            return Optional.empty();
        }
        if (!busy.compareAndSet(false, true)) {
            return Optional.empty();
        }

        EngineSettings requestedSettings = this.settings;
        Task<PipelineResult> task = new Task<>() {
            @Override
            protected PipelineResult call() throws Exception {
                AnalysisPipeline p = pipelineFor(requestedSettings);
                return p.analyze(file, (stage, elapsedMs) -> reportStageOnFxThread(state, stage));
            }
        };

        // task.cancel(false) - no interrupt - flips the task's state to CANCELLED regardless of
        // what its worker thread is doing, which is what actually frees the busy flag below and
        // lets the UI move on; see the class javadoc for why interrupting is deliberately avoided.
        ScheduledFuture<?> timeoutFuture = watchdog.schedule(() -> {
            if (task.cancel(false)) {
                log.warn("Analysis of {} exceeded {} and was cancelled.", file, timeout);
                abandonCachedPipeline();
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        task.stateProperty().addListener((obs, oldState, newState) -> {
            if (isTerminal(newState)) {
                busy.set(false);
                timeoutFuture.cancel(false);
            }
        });

        // A fresh, disposable executor per submission (rather than one long-lived shared one) so a
        // timed-out task's abandoned thread - which may keep running for a while in the background,
        // since it was not interrupted - can never block a later, unrelated analysis from starting.
        ExecutorService taskExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "statigate-analysis");
            t.setDaemon(true);
            return t;
        });
        currentExecutor = taskExecutor;
        taskExecutor.execute(task);
        taskExecutor.shutdown(); // stop accepting new work; the already-submitted task is unaffected
        return Optional.of(task);
    }

    /**
     * Drops the cached pipeline/runtime after a timeout, without closing them - the just-abandoned
     * worker thread may still be using them - so the next analysis builds (and owns) its own rather
     * than racing the abandoned thread for shared native state.
     */
    private void abandonCachedPipeline() {
        synchronized (pipelineLock) {
            pipeline = null;
            runtime = null;
            builtWith = null;
        }
    }

    private static void reportStageOnFxThread(AppState state, String stage) {
        try {
            Platform.runLater(() -> {
                try {
                    state.reportStageComplete(stage);
                } catch (RuntimeException e) {
                    log.warn("UI stage-progress callback failed for stage '{}'", stage, e);
                }
            });
        } catch (IllegalStateException e) {
            // The FX toolkit has already shut down (window closed while analysis was in flight).
            // The analysis itself is unaffected; there is simply no UI left to tell.
            log.debug("Toolkit unavailable while reporting stage '{}'", stage);
        }
    }

    private static boolean isTerminal(Worker.State state) {
        return state == Worker.State.SUCCEEDED
                || state == Worker.State.FAILED
                || state == Worker.State.CANCELLED;
    }

    /**
     * Builds (or reuses) the pipeline for the given settings. Synchronized because, since a timed
     * out analysis's thread is abandoned rather than interrupted (see class javadoc), more than one
     * worker thread can now be alive at once, both capable of reaching this method.
     */
    private AnalysisPipeline pipelineFor(EngineSettings requested) {
        synchronized (pipelineLock) {
            if (pipeline != null && requested.equals(builtWith)) {
                return pipeline;
            }
            log.info("(Re)building analysis runtime for settings: {}", requested);
            if (runtime != null) {
                runtime.close();
                runtime = null;
            }
            ModelLocator locator = requested.modelsDir() != null
                    ? new ModelLocator(requested.modelsDir())
                    : new ModelLocator();
            runtime = new NlpRuntime(locator, 256, requested.threads(), requested.useGpu());
            pipeline = AnalysisPipeline.create(runtime);
            builtWith = requested;
            return pipeline;
        }
    }

    /**
     * Shuts down the watchdog and the current analysis executor (if any) and releases the model
     * runtime. Idempotent: safe to call more than once (e.g. once from a window-close handler and
     * once from an explicit "quit"). All executor threads are daemon threads regardless, so this is
     * a best-effort prompt cleanup rather than a correctness requirement for JVM exit.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        watchdog.shutdownNow();
        ExecutorService executor = currentExecutor;
        if (executor != null) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.warn("Analysis worker did not stop within 2s of shutdown; proceeding anyway.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (pipelineLock) {
            if (runtime != null) {
                runtime.close();
                runtime = null;
                pipeline = null;
            }
        }
    }
}
