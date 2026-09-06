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
 * A clause worth the reader's attention, with a plain-language rationale and a grounding citation.
 *
 * <p>Framing is advisory only: a flag means "this may be worth asking an advocate about", never a
 * legal verdict.
 *
 * @param severity  relative attention level
 * @param category  short category label, e.g. {@code "one-sided-indemnity"}, {@code "auto-renewal"}
 * @param rationale plain-language explanation of why the clause is flagged
 * @param citation  the document span (and optional statute) this flag is grounded in
 */
public record RiskFlag(Severity severity, String category, String rationale, Citation citation) {

    public RiskFlag {
        if (severity == null) {
            throw new IllegalArgumentException("severity must not be null");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category must not be blank");
        }
        if (rationale == null || rationale.isBlank()) {
            throw new IllegalArgumentException("rationale must not be blank");
        }
        if (citation == null) {
            throw new IllegalArgumentException("citation must not be null - a flag must be grounded");
        }
    }
}
