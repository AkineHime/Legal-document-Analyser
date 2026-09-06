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

import java.util.List;

/**
 * Raw output of a {@link DocumentExtractor}: per-page text plus the detected media type.
 *
 * @param pages     page texts in reading order (a single entry for non-paged formats)
 * @param mediaType detected media type, e.g. {@code application/pdf}
 */
public record ExtractedDoc(List<PageText> pages, String mediaType) {

    public ExtractedDoc {
        pages = List.copyOf(pages == null ? List.of() : pages);
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("at least one page is required");
        }
        if (mediaType == null || mediaType.isBlank()) {
            mediaType = "application/octet-stream";
        }
    }

    public int pageCount() {
        return pages.size();
    }
}
