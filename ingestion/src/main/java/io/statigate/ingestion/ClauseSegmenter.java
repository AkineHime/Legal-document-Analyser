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

import io.statigate.core.Clause;
import io.statigate.core.Document;
import io.statigate.core.SourceSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits a {@link Document}'s clean text into clauses using the numbered/lettered headings that
 * {@link TextNormalizer} isolated. Each clause carries a span back into the clean text and is left
 * {@link Clause#UNCLASSIFIED} - the extraction module assigns a type and risk level later.
 *
 * <p>Falls back to paragraph-level segmentation when a document has no recognizable heading
 * structure, so downstream stages always receive some segmentation.
 */
public final class ClauseSegmenter {

    private static final Pattern PARAGRAPH_SPLIT = Pattern.compile("\\n\\s*\\n");

    private static final Pattern HEADING = Pattern.compile(
            "^("
                    + "(?:\\d{1,2}(?:\\.\\d{1,2}){0,3})\\.?"
                    + "|(?:\\((?:[a-z]|[ivx]{1,4}|\\d{1,2})\\))"
                    + "|(?:ARTICLE|SECTION|CLAUSE)\\s+(?:[IVXLC]{1,6}|\\d{1,2})"
                    + ")\\s+"
                    + "(\\p{Lu}[\\p{Lu}0-9 ,&()/'\\-]{2,70})$");

    private static final int MIN_PREAMBLE_CHARS = 80;

    public List<Clause> segment(Document document) {
        String text = document.cleanText();
        if (text.isBlank()) {
            return List.of();
        }
        List<int[]> paragraphs = paragraphRanges(text);
        List<Clause> clauses = new ArrayList<>();

        int clauseStart = -1;
        int clauseEnd = -1;
        int index = 0;
        boolean sawHeading = false;

        for (int[] range : paragraphs) {
            String para = text.substring(range[0], range[1]).strip();
            boolean isHeading = HEADING.matcher(para).matches();
            if (isHeading) {
                sawHeading = true;
                if (clauseStart >= 0) {
                    clauses.add(makeClause(document, ++index, text, clauseStart, clauseEnd));
                } else if (clauseEnd > 0 && clauseEnd - firstNonWs(text) >= MIN_PREAMBLE_CHARS) {
                    clauses.add(preamble(document, text, firstNonWs(text), clauseEnd));
                }
                clauseStart = range[0];
                clauseEnd = range[1];
            } else if (clauseStart >= 0) {
                clauseEnd = range[1];
            } else {
                clauseEnd = range[1]; // still in the preamble
            }
        }
        if (clauseStart >= 0) {
            clauses.add(makeClause(document, ++index, text, clauseStart, clauseEnd));
        }

        if (!sawHeading) {
            return paragraphFallback(document, text, paragraphs);
        }
        return List.copyOf(clauses);
    }

    private static Clause makeClause(Document doc, int index, String text, int start, int end) {
        return new Clause("clause-" + index, Clause.UNCLASSIFIED,
                text.substring(start, end).strip(), new SourceSpan(start, end, doc.pageOf(start)));
    }

    private static Clause preamble(Document doc, String text, int start, int end) {
        return new Clause("clause-0", "PREAMBLE", text.substring(start, end).strip(),
                new SourceSpan(start, end, doc.pageOf(start)));
    }

    private static List<Clause> paragraphFallback(Document doc, String text, List<int[]> paragraphs) {
        List<Clause> clauses = new ArrayList<>();
        int i = 0;
        for (int[] range : paragraphs) {
            String para = text.substring(range[0], range[1]).strip();
            if (para.length() < 40) {
                continue;
            }
            clauses.add(new Clause("para-" + (++i), Clause.UNCLASSIFIED, para,
                    new SourceSpan(range[0], range[1], doc.pageOf(range[0]))));
        }
        return List.copyOf(clauses);
    }

    private static List<int[]> paragraphRanges(String text) {
        List<int[]> ranges = new ArrayList<>();
        int pos = 0;
        var m = PARAGRAPH_SPLIT.matcher(text);
        while (m.find()) {
            if (m.start() > pos) {
                ranges.add(new int[] {pos, m.start()});
            }
            pos = m.end();
        }
        if (pos < text.length()) {
            ranges.add(new int[] {pos, text.length()});
        }
        return ranges;
    }

    private static int firstNonWs(String text) {
        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }
}
