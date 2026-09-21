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

package io.statigate.desktop.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.statigate.desktop.FxTestSupport;
import io.statigate.desktop.engine.AnalysisEngine;
import io.statigate.desktop.engine.EngineSettings;
import io.statigate.desktop.state.AnalysisPhase;
import io.statigate.desktop.state.AppState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Node;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Reproduces {@link DashboardPane}'s actual wiring end to end - a real {@link AnalysisEngine}
 * submission, its {@code setOnSucceeded} handler calling {@link AppState#complete}, and a phase
 * listener building the view for whatever phase results, exactly as {@code DashboardPane.
 * renderContent} does - rather than testing the engine, the state, and the views each in
 * isolation.
 *
 * <p>This is the level a real, shipped bug lived at and none of those isolated tests could see:
 * {@link AppState} used to flip {@code phase} to {@code COMPLETE} before setting {@code result},
 * so a phase listener built {@code ReportView(null)} - a {@link NullPointerException} at
 * construction, since {@code ReportView} reads {@code result.document()} unconditionally - and the
 * screen was left showing whatever it displayed before (the loading overlay) forever, even though
 * the analysis itself had already finished successfully. This test fails loudly if that ordering
 * regresses, instead of the failure being an exception silently swallowed by the FX toolkit's
 * uncaught-exception handling with nothing on screen ever changing.
 */
class DashboardIntegrationTest {

    private static final Path SAMPLE =
            Path.of("..", "samples", "sample_service_agreement.pdf").toAbsolutePath().normalize();

    @Test
    void successfulAnalysisEndsWithARenderedReportViewNotAnException() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.available(), "JavaFX toolkit unavailable in this environment");
        Assumptions.assumeTrue(Files.isRegularFile(SAMPLE), "sample fixture missing");

        // Not try-with-resources: the engine must stay open until the background task (submitted
        // from inside the Platform.runLater below, which returns long before that task finishes)
        // actually completes, so it is closed explicitly after `outcome` resolves instead.
        AnalysisEngine engine = new AnalysisEngine(new EngineSettings(Path.of("__none__"), 1, false));
        CompletableFuture<Object> outcome = new CompletableFuture<>();
        try {
            Platform.runLater(() -> {
                try {
                    AppState state = new AppState();
                    Node[] rendered = { null };

                    // The exact pattern DashboardPane.renderContent uses: a phase listener that
                    // builds a different view per phase, reading AppState's other properties at
                    // that instant.
                    state.phaseProperty().addListener((obs, oldPhase, newPhase) -> {
                        rendered[0] = switch (newPhase) {
                            case COMPLETE -> new ReportView(state.result());
                            case FAILED -> new ErrorBanner(state.errorMessageProperty().get(), state::reset);
                            default -> null;
                        };
                    });

                    Task<?> task = engine.submit(SAMPLE, state).orElseThrow();
                    // The exact pattern DashboardPane.onFileAccepted uses: state.complete()/fail()
                    // fire the phase listener above synchronously and return only once it has, so
                    // `rendered[0]` is guaranteed set by the time each handler completes below.
                    task.setOnSucceeded(e -> {
                        state.complete((io.statigate.pipeline.PipelineResult) task.getValue());
                        outcome.complete(rendered[0]);
                    });
                    task.setOnFailed(e -> {
                        state.fail(task.getException() == null ? null : task.getException().getMessage());
                        outcome.complete(rendered[0]);
                    });
                } catch (Throwable t) {
                    outcome.complete(t);
                }
            });

            Object result = outcome.get(15, TimeUnit.SECONDS);
            assertNull(result instanceof Throwable ? (Throwable) result : null,
                    "the real engine-to-view path must not throw: " + result);
            assertEquals(ReportView.class, result.getClass(),
                    "a successful analysis must end with a real, non-null-backed ReportView on screen");
        } finally {
            engine.close();
        }
    }
}
