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

/**
 * A reference to a specific provision of an Indian statute in the local grounding corpus.
 *
 * @param act        short act identifier, e.g. {@code "Indian Contract Act, 1872"}
 * @param provision  provision label, e.g. {@code "Section 23"} or {@code "Section 73"}
 * @param snippet    the exact statute text the reference points at (verbatim from the corpus)
 * @param corpusId   identifier of the corpus document the snippet came from
 */
public record StatuteRef(String act, String provision, String snippet, String corpusId) {

    public StatuteRef {
        if (act == null || act.isBlank()) {
            throw new IllegalArgumentException("act must not be blank");
        }
        if (provision == null || provision.isBlank()) {
            throw new IllegalArgumentException("provision must not be blank");
        }
        if (snippet == null || snippet.isBlank()) {
            throw new IllegalArgumentException("snippet must not be blank");
        }
        if (corpusId == null || corpusId.isBlank()) {
            throw new IllegalArgumentException("corpusId must not be blank");
        }
    }
}
