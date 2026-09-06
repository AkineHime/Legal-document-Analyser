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

import io.statigate.core.Clause;
import io.statigate.core.Entity;
import io.statigate.core.RiskFlag;
import java.util.List;
import java.util.Map;

/**
 * Output of {@link ExtractionService}: classified clauses, entities, risk flags, and the
 * per-clause classification detail (confidence, runner-up) for reporting and evaluation.
 *
 * @param clauses           clauses with their assigned {@link io.statigate.core.ClauseType}
 * @param entities          extracted named entities
 * @param riskFlags         detected risks, each grounded in a clause span
 * @param classifications   clause id to classification detail
 * @param backend           {@code "inlegalbert+keyword"} or {@code "keyword-only"}
 */
public record ExtractionResult(
        List<Clause> clauses,
        List<Entity> entities,
        List<RiskFlag> riskFlags,
        List<RiskFlag> documentRiskFlags,
        Map<String, ClauseClassification> classifications,
        String backend) {

    public ExtractionResult {
        clauses = List.copyOf(clauses);
        entities = List.copyOf(entities);
        riskFlags = List.copyOf(riskFlags);
        documentRiskFlags = List.copyOf(documentRiskFlags == null ? List.of() : documentRiskFlags);
        classifications = Map.copyOf(classifications);
    }
}
