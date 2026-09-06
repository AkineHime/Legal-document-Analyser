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

import java.util.List;

/**
 * The result of ingesting an input file: cleaned plain text plus its sentence segmentation.
 *
 * <p>All downstream spans (entities, clauses, citations) are offsets into {@link #cleanText()}.
 *
 * @param sourcePath absolute path of the ingested file
 * @param mediaType  detected media type, e.g. {@code application/pdf}
 * @param pageCount  number of pages if known (PDF), otherwise {@code 0}
 * @param cleanText  normalized plain text extracted from the file
 * @param sentences  sentence segmentation of {@link #cleanText()}, in reading order
 */
public record Document(
        String sourcePath,
        String mediaType,
        int pageCount,
        String cleanText,
        List<Sentence> sentences) {

    public Document {
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new IllegalArgumentException("sourcePath must not be blank");
        }
        if (cleanText == null) {
            throw new IllegalArgumentException("cleanText must not be null");
        }
        if (pageCount < 0) {
            throw new IllegalArgumentException("pageCount must be >= 0, was " + pageCount);
        }
        sentences = List.copyOf(sentences == null ? List.of() : sentences);
    }

    public int charCount() {
        return cleanText.length();
    }

    public int sentenceCount() {
        return sentences.size();
    }

    /**
     * @return the 1-based page a clean-text offset falls on, inferred from the enclosing sentence,
     *         or {@code 0} if unknown
     */
    public int pageOf(int offset) {
        int best = 0;
        for (Sentence s : sentences) {
            if (offset >= s.span().start() && offset < s.span().end()) {
                return s.span().page();
            }
            if (s.span().start() <= offset) {
                best = s.span().page();
            }
        }
        return best;
    }
}
