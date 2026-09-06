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

import io.statigate.core.StatuteRef;

/**
 * A statute provision retrieved as relevant to a clause, with the relevance score and the method
 * that produced it.
 *
 * @param provision the matched provision
 * @param score     relevance score in {@code [0, 1]} (normalized within a single retrieval)
 * @param method    {@code "bm25"}, {@code "inlegalbert"}, {@code "hybrid"}
 */
public record GroundingMatch(StatuteProvision provision, double score, String method) {

    public StatuteRef toRef() {
        return provision.toRef();
    }
}
