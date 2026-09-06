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

package io.statigate.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DomainModelTest {

    @Test
    void adviceRejectsEmptyCitations() {
        assertThrows(IllegalArgumentException.class,
                () -> new Advice("headline", "body", List.of()));
    }

    @Test
    void adviceAcceptsGroundedCitation() {
        var span = SourceSpan.of(0, 10);
        var citation = Citation.documentOnly(span, "the clause text");
        var advice = new Advice("Auto-renewal is aggressive",
                "This contract renews automatically unless cancelled 90 days in advance.",
                List.of(citation));
        assertEquals(1, advice.citations().size());
    }

    @Test
    void sourceSpanSlicesCleanText() {
        var text = "PARTIES: Acme Pvt Ltd and Beta LLP.";
        var span = new SourceSpan(9, 22, 1);
        assertEquals("Acme Pvt Ltd ", span.slice(text));
    }

    @Test
    void sourceSpanRejectsInvertedRange() {
        assertThrows(IllegalArgumentException.class, () -> new SourceSpan(10, 5, 1));
    }

    @Test
    void riskFlagRequiresCitation() {
        assertThrows(IllegalArgumentException.class,
                () -> new RiskFlag(Severity.HIGH, "one-sided-indemnity", "rationale", null));
    }

    @Test
    void ingestedOnlyReportHasEmptyDownstreamStages() {
        var doc = new Document("/tmp/x.pdf", "application/pdf", 1, "hello world", List.of());
        var report = AnalysisReport.ingestedOnly(doc);
        assertTrue(report.entities().isEmpty());
        assertTrue(report.clauses().isEmpty());
        assertTrue(report.riskFlags().isEmpty());
        assertTrue(report.advice().isEmpty());
    }
}
