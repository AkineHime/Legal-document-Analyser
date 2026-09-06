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

import java.util.List;

/**
 * A single piece of plain-language advisory output about the document.
 *
 * <p>The compact constructor enforces the system's core invariant: <b>no advice without grounding</b>.
 * An {@code Advice} cannot be constructed with an empty citation list, so nothing can reach the user
 * as advice unless it is traceable to at least one span of the source document.
 *
 * @param headline   one-line summary of the point
 * @param body       the plain-language explanation
 * @param citations  the source spans (and optional statute provisions) this advice is derived from;
 *                   must be non-empty
 */
public record Advice(String headline, String body, List<Citation> citations) {

    public Advice {
        if (headline == null || headline.isBlank()) {
            throw new IllegalArgumentException("headline must not be blank");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body must not be blank");
        }
        if (citations == null || citations.isEmpty()) {
            throw new IllegalArgumentException(
                    "advice must cite at least one source span - ungrounded advice is not permitted");
        }
        citations = List.copyOf(citations);
    }
}
