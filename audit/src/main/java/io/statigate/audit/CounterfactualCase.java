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

package io.statigate.audit;

import java.util.Map;

/**
 * One counterfactual-fairness test: a base document plus a set of substitutions that should not
 * change the analysis (party names, gendered pronouns, region/state names). Milestone 5 runs the
 * pipeline on the base and each variant and asserts the risk flags and advice are equivalent.
 *
 * @param name          case identifier
 * @param baseText      the original document text
 * @param substitutions ordered replacement pairs applied to produce a variant
 */
public record CounterfactualCase(String name, String baseText, Map<String, String> substitutions) {

    public CounterfactualCase {
        substitutions = Map.copyOf(substitutions == null ? Map.of() : substitutions);
    }

    public String variantText() {
        String s = baseText;
        for (var e : substitutions.entrySet()) {
            s = s.replace(e.getKey(), e.getValue());
        }
        return s;
    }
}
