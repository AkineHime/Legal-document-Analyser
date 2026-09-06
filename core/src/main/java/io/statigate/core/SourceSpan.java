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
 * A character range within a {@link Document}'s clean text, plus the 1-based page it starts on.
 *
 * <p>Every entity, clause, citation and piece of advice in this system carries a {@code SourceSpan}
 * back to the input document. Nothing is presented to the user as advice unless it is traceable to
 * a span of the source.
 *
 * @param start inclusive start offset into {@link Document#cleanText()}
 * @param end   exclusive end offset into {@link Document#cleanText()}
 * @param page  1-based page number the span starts on, or {@code 0} if unknown
 */
public record SourceSpan(int start, int end, int page) {

    public SourceSpan {
        if (start < 0) {
            throw new IllegalArgumentException("start must be >= 0, was " + start);
        }
        if (end < start) {
            throw new IllegalArgumentException("end (" + end + ") must be >= start (" + start + ")");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0, was " + page);
        }
    }

    /** A span with an unknown page. */
    public static SourceSpan of(int start, int end) {
        return new SourceSpan(start, end, 0);
    }

    public int length() {
        return end - start;
    }

    /** Extracts the text this span refers to from the given clean text. */
    public String slice(String cleanText) {
        return cleanText.substring(start, Math.min(end, cleanText.length()));
    }
}
