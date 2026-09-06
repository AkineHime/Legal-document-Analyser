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
 * A named entity extracted from the document, e.g. a party name, a date, a monetary amount,
 * a governing-law reference or a jurisdiction.
 *
 * @param type       entity type label (see {@link EntityType} for the expected contract set)
 * @param text       the surface text of the entity
 * @param span       where the entity sits in {@link Document#cleanText()}
 * @param confidence model confidence in {@code [0, 1]}, or {@code 1.0} for rule-based extraction
 */
public record Entity(String type, String text, SourceSpan span, double confidence) {

    public Entity {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (span == null) {
            throw new IllegalArgumentException("span must not be null");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0,1], was " + confidence);
        }
    }

    public static Entity ruleBased(String type, String text, SourceSpan span) {
        return new Entity(type, text, span, 1.0);
    }
}
