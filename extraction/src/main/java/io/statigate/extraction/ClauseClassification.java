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

package io.statigate.extraction;

import io.statigate.core.Clause;

/**
 * Result of classifying one clause.
 *
 * @param type       predicted clause type, or {@link Clause#UNCLASSIFIED}
 * @param confidence similarity score of the winning type in {@code [0, 1]}
 * @param runnerUp   second-best type (useful for review), or {@code null}
 * @param method     how the label was assigned, e.g. {@code "inlegalbert-centroid"}, {@code "keyword"}
 */
public record ClauseClassification(String type, double confidence, String runnerUp, String method) {

    public boolean isClassified() {
        return !Clause.UNCLASSIFIED.equals(type);
    }
}
