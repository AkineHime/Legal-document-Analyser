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

/**
 * Text of a single source page (or the whole document, for non-paged formats).
 *
 * @param number 1-based page number
 * @param text   raw text of the page, before normalization
 */
public record PageText(int number, String text) {

    public PageText {
        if (number < 1) {
            throw new IllegalArgumentException("page number must be >= 1, was " + number);
        }
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
    }
}
