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

package io.statigate.desktop.library;

import java.time.Instant;
import java.util.Map;

/**
 * The lightweight, fast-to-list summary of one saved analysis - what {@link LibraryStore#listEntries()}
 * reads to build the library grid without deserializing every stored {@code PipelineResult} in full.
 *
 * <p>Serialized as {@code meta.json} by {@link LibraryStore}. Uses {@code long} epoch millis rather
 * than {@link Instant} directly because {@code jackson-databind} needs the (unused-elsewhere)
 * {@code jackson-datatype-jsr310} module to handle {@code java.time} types; a primitive avoids that
 * dependency entirely.
 *
 * @param id                  generated identifier (also the storage directory name); never derived
 *                            from user input, so it is safe to use in a filesystem path
 * @param displayName         the original file name, for display only
 * @param analyzedAtEpochMillis when this analysis was saved
 * @param clauseCount         convenience summary so cards do not need the full result
 * @param riskCount           clause-level and document-level risk flags combined
 * @param entityCount         convenience summary
 * @param totalMillis         the pipeline's own reported analysis time
 * @param backends            which backend ran each stage (extraction/grounding/advisory)
 * @param hasStoredSource     whether a copy of the original file is stored alongside this entry
 * @param sourceExtension     the original file's extension (without the dot), for display/icon choice
 * @param highestSeverity     the name of the highest {@link io.statigate.core.Severity} among this
 *                            document's risk flags, or {@code null} if it has none
 * @param contentHash         a SHA-256 hex digest of the analyzed file's bytes, used to recognize
 *                            "this exact document again" so it is not silently re-analyzed; {@code
 *                            null} for entries saved before this field existed, or when the source
 *                            file could not be read at save time - such an entry simply never
 *                            matches a future hash lookup rather than falsely claiming a duplicate
 */
public record LibraryEntry(
        String id,
        String displayName,
        long analyzedAtEpochMillis,
        int clauseCount,
        int riskCount,
        int entityCount,
        long totalMillis,
        Map<String, String> backends,
        boolean hasStoredSource,
        String sourceExtension,
        String highestSeverity,
        String contentHash) {

    public LibraryEntry {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        backends = backends == null ? Map.of() : Map.copyOf(backends);
        sourceExtension = sourceExtension == null ? "" : sourceExtension;
    }

    public Instant analyzedAt() {
        return Instant.ofEpochMilli(analyzedAtEpochMillis);
    }
}
