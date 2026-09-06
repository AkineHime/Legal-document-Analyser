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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.statigate.core.Advice;
import io.statigate.core.Citation;
import io.statigate.core.ClauseType;
import io.statigate.nlp.ModelLocator;
import io.statigate.nlp.NlpRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class AnalysisPipelineTest {

    private static final Path SAMPLE =
            Path.of("..", "samples", "sample_service_agreement.pdf").toAbsolutePath().normalize();
    private static final Path MODELS = Path.of("..", "models").toAbsolutePath().normalize();

    @Test
    void endToEndProducesGroundedTraceableFindings() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(SAMPLE));
        try (NlpRuntime runtime = new NlpRuntime(new ModelLocator(MODELS), 256, 2)) {
            var result = AnalysisPipeline.create(runtime).analyze(SAMPLE);

            assertEquals(10, result.clauses().size());

            var indemnity = result.clauses().stream()
                    .filter(f -> ClauseType.INDEMNITY.equals(f.clause().type()))
                    .findFirst().orElseThrow();
            assertTrue(indemnity.risks().stream().anyMatch(r -> r.category().equals("uncapped-indemnity")));
            assertFalse(indemnity.statutes().isEmpty(), "indemnity clause should be grounded");
            assertFalse(indemnity.advice().isEmpty());

            // Every advice everywhere is grounded, and every citation points inside the document.
            int chars = result.document().charCount();
            List<Advice> allAdvice = result.toReport().advice();
            assertFalse(allAdvice.isEmpty());
            for (Advice a : allAdvice) {
                assertFalse(a.citations().isEmpty());
                for (Citation c : a.citations()) {
                    assertTrue(c.clauseSpan().start() >= 0 && c.clauseSpan().end() <= chars);
                }
            }

            // Statute retrieval scores are ordered.
            for (var f : result.clauses()) {
                double prev = Double.MAX_VALUE;
                for (var m : f.statutes()) {
                    assertTrue(m.score() <= prev + 1e-9);
                    prev = m.score();
                }
            }
        }
    }

    @Test
    void runsWithoutModelAndStaysConsistent() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(SAMPLE));
        try (NlpRuntime runtime = new NlpRuntime(new ModelLocator(Path.of("__none__")))) {
            var pipeline = AnalysisPipeline.create(runtime);
            assertEquals("keyword-only", pipeline.backends().get("extraction"));
            var result = pipeline.analyze(SAMPLE);
            assertTrue(result.clauses().stream()
                    .anyMatch(f -> ClauseType.INDEMNITY.equals(f.clause().type())));
        }
    }
}
