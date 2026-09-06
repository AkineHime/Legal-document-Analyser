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

package io.statigate.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.statigate.nlp.ModelLocator;
import io.statigate.nlp.NlpRuntime;
import io.statigate.pipeline.AnalysisPipeline;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CounterfactualAuditorTest {

    private static final Path MODELS = Path.of("..", "models").toAbsolutePath().normalize();

    @Test
    void identitySwapsDoNotChangeTheAnalysisSignal() throws Exception {
        List<CounterfactualCase> cases = BiasAuditMain.loadCases();
        assertFalse(cases.isEmpty());

        Path tmp = Files.createTempDirectory("cf-audit-test");
        try (NlpRuntime runtime = new NlpRuntime(new ModelLocator(MODELS), 256, 2)) {
            var auditor = new CounterfactualAuditor(AnalysisPipeline.create(runtime));
            for (CounterfactualCase c : cases) {
                var result = auditor.audit(c, tmp);
                assertTrue(result.stable(),
                        () -> "case '" + c.name() + "' changed under identity swap: "
                                + CounterfactualAuditor.render(List.of(result)));
            }
        }
    }
}
