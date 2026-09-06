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
 * A contiguous clause identified in the document, with a coarse type label.
 *
 * @param id    stable identifier within the document, e.g. {@code "clause-7"}
 * @param type  clause type label, e.g. {@code INDEMNITY}, {@code ARBITRATION}, {@code TERMINATION},
 *              or {@code UNCLASSIFIED}
 * @param text  the clause text
 * @param span  where the clause sits in {@link Document#cleanText()}
 */
public record Clause(String id, String type, String text, SourceSpan span) {

    public static final String UNCLASSIFIED = "UNCLASSIFIED";

    public Clause {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (span == null) {
            throw new IllegalArgumentException("span must not be null");
        }
    }
}
