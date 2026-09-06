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

package io.statigate.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.statigate.core.Clause;
import io.statigate.core.ClauseType;
import io.statigate.core.Document;
import io.statigate.core.Entity;
import io.statigate.core.EntityType;
import io.statigate.core.RiskFlag;
import io.statigate.ingestion.IngestionPipeline;
import io.statigate.nlp.ModelLocator;
import io.statigate.nlp.NlpRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Extraction against the synthetic sample contract. The keyword path always runs; the InLegalBERT
 * path runs when the exported model is present under {@code ../models}.
 */
class ExtractionServiceTest {

    private static final Path SAMPLE =
            Path.of("..", "samples", "sample_service_agreement.pdf").toAbsolutePath().normalize();
    private static final Path MODELS = Path.of("..", "models").toAbsolutePath().normalize();

    static boolean modelPresent() {
        return new ModelLocator(MODELS).inLegalBertAvailable();
    }

    private static Document doc;

    @BeforeAll
    static void ingest() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(SAMPLE), "sample PDF missing");
        doc = new IngestionPipeline().ingest(SAMPLE);
    }

    @Test
    void keywordOnlyClassifiesKeyClausesAndGroundsEveryRiskFlag() {
        ExtractionResult r = ExtractionService.keywordOnly().analyze(doc);

        Set<String> types = r.clauses().stream().map(Clause::type).collect(Collectors.toSet());
        assertTrue(types.contains(ClauseType.INDEMNITY), "indemnity not classified: " + types);
        assertTrue(types.contains(ClauseType.TERMINATION), "termination not classified: " + types);
        assertTrue(types.contains(ClauseType.GOVERNING_LAW) || types.contains(ClauseType.ARBITRATION));

        List<String> categories = r.riskFlags().stream().map(RiskFlag::category).toList();
        assertTrue(categories.contains("uncapped-indemnity"), "flags: " + categories);
        for (RiskFlag f : r.riskFlags()) {
            assertTrue(f.citation().clauseSpan().end() <= doc.charCount());
            assertFalse(f.rationale().isBlank());
        }

        Set<String> entityTypes = r.entities().stream().map(Entity::type).collect(Collectors.toSet());
        assertTrue(entityTypes.contains(EntityType.MONETARY_VALUE), entityTypes.toString());
        assertTrue(entityTypes.contains(EntityType.PARTY), entityTypes.toString());
    }

    @Test
    @EnabledIf("modelPresent")
    void inLegalBertBackendTypesEveryClause() {
        try (NlpRuntime runtime = new NlpRuntime(new ModelLocator(MODELS), 256, 2)) {
            ExtractionService svc = ExtractionService.create(runtime);
            assertEquals("inlegalbert+keyword", svc.backend());

            long t0 = System.nanoTime();
            ExtractionResult r = svc.analyze(doc);
            long ms = (System.nanoTime() - t0) / 1_000_000;

            Set<String> types = r.clauses().stream().map(Clause::type).collect(Collectors.toSet());
            assertTrue(types.contains(ClauseType.INDEMNITY));
            assertTrue(types.contains(ClauseType.TERMINATION));
            long typed = r.clauses().stream()
                    .filter(c -> !Clause.UNCLASSIFIED.equals(c.type()) && !"PREAMBLE".equals(c.type()))
                    .count();
            assertTrue(typed >= 7, "InLegalBERT ensemble typed only " + typed);
            assertTrue(r.riskFlags().stream().anyMatch(f -> f.category().equals("uncapped-indemnity")));
            System.out.printf("[InLegalBERT] analyze() = %d ms, %d/%d clauses typed%n",
                    ms, typed, r.clauses().size());
        }
    }
}
