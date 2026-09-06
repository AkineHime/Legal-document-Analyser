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

import io.statigate.core.Advice;
import io.statigate.core.Clause;
import io.statigate.core.RiskFlag;
import io.statigate.extraction.ClauseClassification;
import io.statigate.grounding.GroundingMatch;
import java.util.List;

/**
 * Everything the pipeline learned about one clause, in one place.
 *
 * @param clause         the clause with its assigned type
 * @param classification classifier detail (confidence, runner-up, method)
 * @param risks          risk flags grounded in this clause
 * @param statutes       related Indian statute provisions, most relevant first
 * @param advice         grounded plain-language notes for this clause
 */
public record ClauseFinding(
        Clause clause,
        ClauseClassification classification,
        List<RiskFlag> risks,
        List<GroundingMatch> statutes,
        List<Advice> advice) {

    public ClauseFinding {
        risks = List.copyOf(risks == null ? List.of() : risks);
        statutes = List.copyOf(statutes == null ? List.of() : statutes);
        advice = List.copyOf(advice == null ? List.of() : advice);
    }
}
