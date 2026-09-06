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

import java.util.List;

/**
 * Retrieves the Indian statute provisions most relevant to a clause. Implementations must be fully
 * local (no network, no external service).
 */
public interface StatuteRetriever {

    /**
     * @param clauseText text of the clause to ground
     * @param clauseType classified {@link io.statigate.core.ClauseType}, or {@code null}/UNCLASSIFIED
     * @param k          maximum number of provisions to return
     * @return up to {@code k} matches, most relevant first; may be empty
     */
    List<GroundingMatch> retrieve(String clauseText, String clauseType, int k);
}
