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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.statigate.desktop.FxTestSupport;
import io.statigate.nlp.ModelLocator;
import io.statigate.nlp.NlpRuntime;
import io.statigate.pipeline.AnalysisPipeline;
import io.statigate.pipeline.PipelineResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Builds the entire report view tree - every {@link ClauseCard}, {@link RiskFlagRow}, {@link
 * StatuteChip}, {@link EntityChip} and {@link AdviceRow} it contains - from a real {@link
 * PipelineResult} produced by the actual pipeline (in fast, model-free mode). Pure-class unit tests
 * elsewhere check formatting and sanitization logic in isolation; this is the one test that proves
 * the view layer doesn't throw when handed genuine pipeline output, which is exactly the kind of
 * failure (a null field, an unexpected empty list, a bad cast) that isolated unit tests miss.
 */
class ReportViewSmokeTest {

    private static final Path SAMPLE =
            Path.of("..", "samples", "sample_service_agreement.pdf").toAbsolutePath().normalize();

    @Test
    void reportViewBuildsWithoutThrowingForARealAnalysisResult() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.available(), "JavaFX toolkit unavailable in this environment");
        Assumptions.assumeTrue(Files.isRegularFile(SAMPLE), "sample fixture missing");

        PipelineResult result;
        try (NlpRuntime runtime = new NlpRuntime(new ModelLocator(Path.of("__none__")))) {
            result = AnalysisPipeline.create(runtime).analyze(SAMPLE);
        }
        assertTrue(result.clauses().size() > 0, "expected the sample contract to yield clauses");

        CompletableFuture<Throwable> outcome = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                ReportView view = new ReportView(result);
                // Force layout so bindings/CSS-affecting code paths in child controls actually run,
                // not just their constructors.
                view.applyCss();
                view.layout();
                outcome.complete(null);
            } catch (Throwable t) {
                outcome.complete(t);
            }
        });

        Throwable thrown = outcome.get(10, TimeUnit.SECONDS);
        assertNull(thrown, "ReportView must not throw for real pipeline output: " + thrown);
    }

    @Test
    void reportViewHandlesAnEmptyResultGracefully() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.available(), "JavaFX toolkit unavailable in this environment");

        var document = new io.statigate.core.Document(
                "/tmp/empty.txt", "text/plain", 0, "", List.of());
        var empty = new PipelineResult(document, List.of(), List.of(), List.of(), List.of(),
                java.util.Map.of("total", 0L), java.util.Map.of());

        CompletableFuture<Throwable> outcome = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                ReportView view = new ReportView(empty);
                view.applyCss();
                view.layout();
                outcome.complete(null);
            } catch (Throwable t) {
                outcome.complete(t);
            }
        });

        Throwable thrown = outcome.get(10, TimeUnit.SECONDS);
        assertNull(thrown, "ReportView must degrade to empty states, not throw, for an empty result: " + thrown);
    }
}
