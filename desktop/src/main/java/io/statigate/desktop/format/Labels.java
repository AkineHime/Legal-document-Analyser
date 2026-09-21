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

package io.statigate.desktop.format;

import io.statigate.core.Severity;

/**
 * Turns the pipeline's machine-oriented constant strings (clause types, entity types, risk
 * categories) into short, human-readable labels for display.
 *
 * <p>{@link io.statigate.core.ClauseType} and {@link io.statigate.core.EntityType} are deliberately
 * open string sets, not enums, so that a future model can emit a type this UI has never seen without
 * a code change breaking. {@link #humanize} degrades gracefully for any such unknown value instead
 * of throwing or showing a raw {@code UPPER_SNAKE_CASE} token.
 */
public final class Labels {

    private Labels() {
    }

    /** {@code "NON_COMPETE"} -&gt; {@code "Non compete"}; blank or {@code null} -&gt; {@code "Unclassified"}. */
    public static String clauseType(String rawType) {
        if (rawType == null || rawType.isBlank() || "UNCLASSIFIED".equalsIgnoreCase(rawType)) {
            return "Unclassified";
        }
        return humanize(rawType);
    }

    /** {@code "EFFECTIVE_DATE"} -&gt; {@code "Effective date"}. */
    public static String entityType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "Other";
        }
        return humanize(rawType);
    }

    /** {@code "one-sided-indemnity"} / {@code "auto_renewal"} -&gt; {@code "One sided indemnity"}. */
    public static String riskCategory(String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return "Flagged clause";
        }
        return humanize(rawCategory.replace('-', '_'));
    }

    public static String severity(Severity severity) {
        if (severity == null) {
            return "Unrated";
        }
        return switch (severity) {
            case LOW -> "Low";
            case MEDIUM -> "Medium";
            case HIGH -> "High";
        };
    }

    /** {@code "hybrid"} / {@code "inlegalbert"} / {@code "bm25"} -&gt; a short reader-facing phrase. */
    public static String groundingMethod(String method) {
        if (method == null) {
            return "matched";
        }
        return switch (method.toLowerCase(java.util.Locale.ROOT)) {
            case "inlegalbert" -> "semantic match";
            case "bm25" -> "keyword match";
            case "hybrid" -> "combined match";
            default -> humanize(method).toLowerCase(java.util.Locale.ROOT);
        };
    }

    /** {@code UPPER_SNAKE_CASE} / {@code kebab-case} -&gt; {@code "Sentence case"}. */
    private static String humanize(String raw) {
        String withSpaces = raw.replace('_', ' ').replace('-', ' ').trim().toLowerCase(java.util.Locale.ROOT);
        if (withSpaces.isEmpty()) {
            return raw;
        }
        return Character.toUpperCase(withSpaces.charAt(0)) + withSpaces.substring(1);
    }
}
