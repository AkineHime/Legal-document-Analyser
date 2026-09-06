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

package io.statigate.advisory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.statigate.core.Advice;
import io.statigate.core.Clause;
import io.statigate.core.ClauseType;
import io.statigate.core.RiskFlag;
import io.statigate.core.Severity;
import io.statigate.core.SourceSpan;
import io.statigate.core.StatuteRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdvisoryServiceTest {

    private final Clause indemnity = new Clause("clause-5", ClauseType.INDEMNITY,
            "The Client shall indemnify the Service Provider without any monetary cap.",
            new SourceSpan(100, 180, 1));

    @Test
    void extractiveAdviceIsGroundedInTheClauseAndStatute() {
        var risk = new RiskFlag(Severity.HIGH, "uncapped-indemnity", "Open-ended exposure.",
                io.statigate.core.Citation.documentOnly(indemnity.span(), indemnity.text()));
        var statute = new StatuteRef("Indian Contract Act, 1872", "Section 124",
                "A contract of indemnity is one by which one party promises to save the other from loss.",
                "ica-1872-s124");

        List<Advice> advice = new ExtractiveAdvisoryService()
                .adviseClause(new ClauseAdvisoryInput(indemnity, List.of(risk), List.of(statute)));

        assertTrue(advice.size() >= 2, "expected an explainer and a risk note");
        for (Advice a : advice) {
            assertFalse(a.citations().isEmpty());
        }
        boolean citesStatute = advice.stream()
                .flatMap(a -> a.citations().stream())
                .anyMatch(c -> c.hasStatute() && c.statute().provision().equals("Section 124"));
        assertTrue(citesStatute, "risk advice should carry the statute citation");
    }

    @Test
    void llmGuardrailFallsBackWhenModelInventsASection() {
        LlmClient liar = new LlmClient() {
            public String generate(String s, String u, int n) {
                return "This clause is unusual under Section 999 of the Fictitious Act, 2099. "
                        + "This is not legal advice.";
            }

            public String modelId() {
                return "stub:liar";
            }

            public boolean inProcess() {
                return true;
            }
        };
        var risk = new RiskFlag(Severity.HIGH, "uncapped-indemnity", "Open-ended exposure.",
                io.statigate.core.Citation.documentOnly(indemnity.span(), indemnity.text()));
        List<Advice> advice = new LlmAdvisoryService(liar)
                .adviseClause(new ClauseAdvisoryInput(indemnity, List.of(risk), List.of()));
        // The invented "Section 999" must not survive; deterministic text is used instead.
        assertTrue(advice.stream().noneMatch(a -> a.body().contains("Section 999")));
    }

    @Test
    void adviceRecordStillRejectsEmptyCitations() {
        assertThrows(IllegalArgumentException.class, () -> new Advice("h", "b", List.of()));
    }
}
