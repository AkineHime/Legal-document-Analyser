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

import io.statigate.core.Clause;
import io.statigate.core.RiskFlag;
import io.statigate.core.StatuteRef;
import java.util.List;

/**
 * Everything the advisory layer needs about one clause: the clause itself, the risks the extraction
 * layer flagged on it, and the statute provisions the grounding layer retrieved for it.
 *
 * @param clause    the classified clause
 * @param risks     risk flags whose citation points into this clause
 * @param statutes  related Indian statute provisions, most relevant first
 */
public record ClauseAdvisoryInput(Clause clause, List<RiskFlag> risks, List<StatuteRef> statutes) {

    public ClauseAdvisoryInput {
        risks = List.copyOf(risks == null ? List.of() : risks);
        statutes = List.copyOf(statutes == null ? List.of() : statutes);
    }
}
