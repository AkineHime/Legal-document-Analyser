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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.statigate.nlp.ModelLocator;
import io.statigate.nlp.NlpRuntime;
import io.statigate.pipeline.AnalysisPipeline;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CounterfactualAuditorTest {

    private static final Path MODELS = Path.of("..", "models").toAbsolutePath().normalize();

    @Test
    void identitySwapsDoNotChangeTheAnalysisSignal() throws Exception {
        List<CounterfactualCase> cases = identityCases();
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

    /**
     * Characterises a known, real finding (Statigate_Project_Documentation Section 4.8/5.1)
     * rather than asserting an ideal that the system does not meet: substituting only the
     * corporate-form suffix in the PARTIES clause ("Private Limited" -> "Proprietorship") flips
     * the vendor-msa PARTIES clause to SCOPE_OF_WORK, because the keyword classifier's PARTIES
     * cues lean on that suffix. This test locks the finding in place so a future change to the
     * classifier either fixes it (and this test starts failing, prompting an update here and in
     * the paper) or leaves it as a documented limitation.
     */
    @Test
    void corporateFormSubstitutionExposesAKnownClassifierSensitivity() throws Exception {
        List<CounterfactualCase> cases = corporateFormCases();
        assertEquals(3, cases.size());

        Path tmp = Files.createTempDirectory("cf-audit-corporate-form-test");
        try (NlpRuntime runtime = new NlpRuntime(new ModelLocator(MODELS), 256, 2)) {
            var auditor = new CounterfactualAuditor(AnalysisPipeline.create(runtime));
            for (CounterfactualCase c : cases) {
                var result = auditor.audit(c, tmp);
                if (c.name().equals("vendor-msa-corporate-form")) {
                    assertFalse(result.stable(), "expected the known vendor-msa corporate-form "
                            + "instability to still reproduce - if this now passes, the "
                            + "classifier sensitivity documented in Section 4.8/5.1 has been "
                            + "fixed and the paper should be updated to say so");
                    var variant = result.variants().get(0);
                    assertEquals(Set.of("clause:SCOPE_OF_WORK"), variant.added());
                    assertEquals(Set.of("clause:PARTIES"), variant.removed());
                } else {
                    assertTrue(result.stable(),
                            () -> "case '" + c.name() + "' changed under corporate-form swap: "
                                    + CounterfactualAuditor.render(List.of(result)));
                }
            }
        }
    }

    private static List<CounterfactualCase> identityCases() throws Exception {
        return BiasAuditMain.loadCases().stream()
                .filter(c -> !c.name().endsWith("-corporate-form"))
                .toList();
    }

    private static List<CounterfactualCase> corporateFormCases() throws Exception {
        return BiasAuditMain.loadCases().stream()
                .filter(c -> c.name().endsWith("-corporate-form"))
                .toList();
    }
}
