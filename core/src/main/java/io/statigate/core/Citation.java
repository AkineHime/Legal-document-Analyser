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
 * The grounding link that makes a statement traceable: a span of the user's document paired with
 * the statute provision it relates to. The statute side is optional (some observations are grounded
 * purely in the document itself), but the clause span is always required.
 *
 * @param clauseSpan the span of {@link Document#cleanText()} this statement is derived from
 * @param clauseText the text of that span, carried for display and audit
 * @param statute    the related Indian statute provision, or {@code null} if grounded only in the document
 */
public record Citation(SourceSpan clauseSpan, String clauseText, StatuteRef statute) {

    public Citation {
        if (clauseSpan == null) {
            throw new IllegalArgumentException("clauseSpan must not be null");
        }
        if (clauseText == null || clauseText.isBlank()) {
            throw new IllegalArgumentException("clauseText must not be blank");
        }
    }

    public boolean hasStatute() {
        return statute != null;
    }

    public static Citation documentOnly(SourceSpan clauseSpan, String clauseText) {
        return new Citation(clauseSpan, clauseText, null);
    }
}
