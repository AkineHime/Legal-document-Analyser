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

package io.statigate.grounding;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.statigate.core.ClauseType;
import io.statigate.nlp.ModelLocator;
import io.statigate.nlp.NlpRuntime;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GroundingServiceTest {

    private static final Path MODELS = Path.of("..", "models").toAbsolutePath().normalize();

    @Test
    void corpusLoads() {
        StatuteCorpus corpus = StatuteCorpus.loadDefault();
        assertTrue(corpus.size() >= 20, "expected a populated corpus, got " + corpus.size());
        assertTrue(corpus.provisions().stream()
                .anyMatch(p -> p.act().contains("Indian Contract Act")));
    }

    @Test
    void indemnityClauseGroundsInContractActIndemnityProvisions() {
        try (NlpRuntime runtime = new NlpRuntime(new ModelLocator(MODELS), 256, 2)) {
            GroundingService g = GroundingService.create(runtime);
            List<GroundingMatch> matches = g.ground(
                    "The Client shall indemnify and hold harmless the Service Provider against all "
                            + "claims arising out of the deliverables, without any monetary cap.",
                    ClauseType.INDEMNITY, 3);
            assertFalse(matches.isEmpty());
            assertTrue(matches.stream().anyMatch(m -> m.provision().corpusId().startsWith("ica-1872-s12")),
                    "expected an Indian Contract Act indemnity section, got "
                            + matches.stream().map(m -> m.provision().corpusId()).toList());
        }
    }

    @Test
    void bm25OnlyStillGroundsPaymentClauseInSection74() {
        try (NlpRuntime runtime = new NlpRuntime(new ModelLocator(Path.of("__none__")))) {
            GroundingService g = GroundingService.create(runtime);
            List<GroundingMatch> matches = g.ground(
                    "Overdue amounts shall carry interest at 3% per month as liquidated damages.",
                    ClauseType.PAYMENT, 3);
            assertTrue(matches.stream().anyMatch(m -> m.provision().provision().contains("74")),
                    "expected Section 74, got "
                            + matches.stream().map(m -> m.provision().provision()).toList());
        }
    }
}
