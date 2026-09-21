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

import java.util.regex.Pattern;

/**
 * Defends the UI against the one thing it cannot control: the content of an arbitrary, possibly
 * malformed or adversarial input document. Every piece of extracted text (clause text, entity text,
 * statute snippets) passes through here before it reaches a JavaFX node.
 *
 * <p>Statigate never renders document text as HTML or markup (every view uses plain
 * {@code Label}/{@code Text} nodes), so there is no injection surface in the traditional web sense.
 * What remains is simpler but still worth guarding: stray control characters that a badly-formed
 * PDF/DOCX extraction can leave behind, and pathologically long strings that would otherwise bloat
 * the scene graph or make a card unreadable.
 *
 * <p>Pure and stateless - safe to unit test without the JavaFX toolkit, and safe to call from any
 * thread.
 */
public final class TextSanitizer {

    /**
     * Matches C0/C1 control characters other than tab, newline and carriage return. A single
     * character class with no repetition or alternation, compiled once, so there is no
     * catastrophic-backtracking risk even on a large, adversarial input.
     */
    private static final Pattern CONTROL_CHARS =
            Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F-\\x9F]");

    private static final String ELLIPSIS = "…"; // "…"

    private TextSanitizer() {
    }

    /**
     * Strips control characters that have no business reaching a UI label. Never truncates -
     * combine with {@link #truncate(String, int)} for display-bounded text.
     */
    public static String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return CONTROL_CHARS.matcher(raw).replaceAll(" ");
    }

    /**
     * Sanitizes and clamps to at most {@code maxChars} visible characters, breaking on a word
     * boundary when one is reasonably close to the limit so a card does not end mid-word. Text at
     * or under the limit is returned unchanged (after sanitizing). {@code maxChars <= 0} yields an
     * empty string rather than throwing, since a caller-supplied layout budget should never be able
     * to crash the app.
     */
    public static String truncate(String raw, int maxChars) {
        String clean = sanitize(raw).strip();
        if (maxChars <= 0) {
            return "";
        }
        if (clean.length() <= maxChars) {
            return clean;
        }
        int cut = clean.lastIndexOf(' ', maxChars);
        // Only prefer the word boundary if it does not throw away more than ~20% of the budget;
        // otherwise a hard cut reads better than a tiny fragment.
        if (cut < maxChars * 0.8) {
            cut = maxChars;
        }
        return clean.substring(0, cut).stripTrailing() + ELLIPSIS;
    }
}
