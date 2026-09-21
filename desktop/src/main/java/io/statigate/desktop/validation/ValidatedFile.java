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

package io.statigate.desktop.validation;

import java.nio.file.Path;

/**
 * A file that has passed {@link FileValidationService} and is safe to hand to the analysis pipeline:
 * it exists, is a regular file (not a directory, device, or symlink loop), is within the configured
 * size budget, and has an extension the ingestion layer actually supports.
 *
 * <p>Carrying this type through the code, rather than a bare {@link Path}, makes "has this file been
 * validated" a compile-time fact instead of something every call site has to remember to check.
 *
 * @param path         the resolved, real (symlink-following) path
 * @param displayName  the file name, for showing in the UI
 * @param sizeBytes    file size at validation time
 */
public record ValidatedFile(Path path, String displayName, long sizeBytes) {

    public ValidatedFile {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must be >= 0");
        }
    }
}
