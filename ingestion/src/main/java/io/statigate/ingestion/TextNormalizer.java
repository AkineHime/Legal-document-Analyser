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

package io.statigate.ingestion;

import java.util.regex.Pattern;

/**
 * Normalizes raw extracted text into the canonical "clean text" all downstream spans point into.
 *
 * <p>The transformation is deliberately layout-lossy but wording-faithful: line-wrapping introduced
 * by the source is undone so sentences are contiguous, while paragraph breaks and clause headings
 * are preserved as blank-line-separated blocks so {@link ClauseSegmenter} can find them.
 */
public final class TextNormalizer {

    private static final Pattern CR = Pattern.compile("\\r\\n?");
    private static final Pattern NBSP = Pattern.compile("[\\u00A0\\u2007\\u202F]");
    private static final Pattern HYPHEN_LINE_BREAK = Pattern.compile("(\\p{L})-\\n(\\p{L})");
    private static final Pattern TRAILING_WS = Pattern.compile("[ \\t]+\\n");
    private static final Pattern MANY_BLANK_LINES = Pattern.compile("\\n{3,}");
    private static final Pattern MANY_SPACES = Pattern.compile("[ \\t]{2,}");
    private static final Pattern SINGLE_NEWLINE = Pattern.compile("(?<!\\n)\\n(?!\\n)");

    /**
     * A clause heading on its own line: an enumerator ({@code 7.}, {@code 7.2}, {@code (a)},
     * {@code ARTICLE IV}, {@code SECTION 3}) followed by a short mostly-capitalized title.
     */
    private static final Pattern HEADING_LINE = Pattern.compile(
            "(?m)^[ \\t]{0,6}("
                    + "(?:\\d{1,2}(?:\\.\\d{1,2}){0,3}\\.?)"
                    + "|(?:\\((?:[a-z]|[ivx]{1,4}|\\d{1,2})\\))"
                    + "|(?:ARTICLE|SECTION|CLAUSE)\\s+(?:[IVXLC]{1,6}|\\d{1,2})"
                    + ")[ \\t]+"
                    + "(\\p{Lu}[\\p{Lu}0-9 ,&()/'\\-]{2,70})[ \\t]*$");

    private TextNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String s = CR.matcher(raw).replaceAll("\n");
        s = NBSP.matcher(s).replaceAll(" ");
        s = HYPHEN_LINE_BREAK.matcher(s).replaceAll("$1$2");
        s = TRAILING_WS.matcher(s).replaceAll("\n");
        // Isolate clause headings so the wrapped-line join below cannot swallow them.
        s = HEADING_LINE.matcher(s).replaceAll("\n\n$1 $2\n\n");
        s = MANY_BLANK_LINES.matcher(s).replaceAll("\n\n");
        s = SINGLE_NEWLINE.matcher(s).replaceAll(" ");
        s = MANY_SPACES.matcher(s).replaceAll(" ");
        s = MANY_BLANK_LINES.matcher(s).replaceAll("\n\n");
        return s.strip();
    }
}
