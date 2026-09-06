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
 * A single sentence detected during ingestion, with its span into the document's clean text.
 *
 * @param index 0-based position of this sentence in the document
 * @param text  the sentence text
 * @param span  where the sentence sits in {@link Document#cleanText()}
 */
public record Sentence(int index, String text, SourceSpan span) {

    public Sentence {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0, was " + index);
        }
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        if (span == null) {
            throw new IllegalArgumentException("span must not be null");
        }
    }
}
