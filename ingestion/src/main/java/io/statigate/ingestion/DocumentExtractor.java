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

import java.io.IOException;
import java.nio.file.Path;

/** Extracts per-page text from a document file. Implementations must be fully local. */
public interface DocumentExtractor {

    /** @return true if this extractor handles the given detected media type */
    boolean supports(String mediaType);

    ExtractedDoc extract(Path file) throws IOException;
}
