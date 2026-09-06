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

import java.util.Arrays;

/**
 * Maps a character offset in the final clean text back to the 1-based source page it came from.
 *
 * @param pageStarts clean-text offset at which each page's text begins; {@code pageStarts[i]} is the
 *                   start of page {@code i + 1}. Must be non-empty and non-decreasing.
 */
public record PageIndex(int[] pageStarts) {

    public PageIndex {
        if (pageStarts == null || pageStarts.length == 0) {
            throw new IllegalArgumentException("pageStarts must be non-empty");
        }
        pageStarts = pageStarts.clone();
    }

    /** @return the 1-based page containing {@code offset} (clamped to the first/last page) */
    public int pageOf(int offset) {
        int i = Arrays.binarySearch(pageStarts, offset);
        if (i >= 0) {
            return i + 1;
        }
        int insertionPoint = -i - 1;
        return Math.max(1, insertionPoint);
    }

    public int pageCount() {
        return pageStarts.length;
    }
}
