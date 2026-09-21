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

package io.statigate.pipeline;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.statigate.nlp.ModelLocator;
import io.statigate.nlp.NlpRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@link AnalysisPipeline.StageListener} hook a UI can use for real (not simulated)
 * progress reporting: exactly the four stages, in pipeline order, each with a non-negative elapsed
 * time, and a throwing listener must not affect the returned result.
 */
class AnalysisPipelineStageListenerTest {

    private static final Path SAMPLE =
            Path.of("..", "samples", "sample_service_agreement.pdf").toAbsolutePath().normalize();

    @Test
    void reportsAllFourStagesInOrder() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(SAMPLE));
        List<String> seen = new CopyOnWriteArrayList<>();
        try (NlpRuntime runtime = new NlpRuntime(new ModelLocator(Path.of("__none__")))) {
            AnalysisPipeline pipeline = AnalysisPipeline.create(runtime);
            PipelineResult result = pipeline.analyze(SAMPLE,
                    (stage, elapsedMs) -> {
                        seen.add(stage);
                        assertTrue(elapsedMs >= 0, "elapsed time must not be negative");
                    });
            assertEquals(List.of("ingestion", "extraction", "grounding", "advisory"), seen);
            assertEquals(10, result.clauses().size());
        }
    }

    @Test
    void nullListenerIsTreatedAsNoOp() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(SAMPLE));
        try (NlpRuntime runtime = new NlpRuntime(new ModelLocator(Path.of("__none__")))) {
            AnalysisPipeline pipeline = AnalysisPipeline.create(runtime);
            assertDoesNotThrow(() -> pipeline.analyze(SAMPLE, null));
        }
    }

    @Test
    void misbehavingListenerCannotBreakOrAlterTheAnalysis() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(SAMPLE));
        try (NlpRuntime runtime = new NlpRuntime(new ModelLocator(Path.of("__none__")))) {
            AnalysisPipeline pipeline = AnalysisPipeline.create(runtime);
            PipelineResult result = assertDoesNotThrow(() -> pipeline.analyze(SAMPLE,
                    (stage, elapsedMs) -> {
                        throw new IllegalStateException("simulated buggy UI callback");
                    }));
            assertEquals(10, result.clauses().size());
        }
    }
}
