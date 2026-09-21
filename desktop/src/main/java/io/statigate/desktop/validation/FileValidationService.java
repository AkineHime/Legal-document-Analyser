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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * The one place a candidate input file is checked before it is ever handed to the analysis
 * pipeline. Every entry point that can produce a file to analyze - the file chooser, drag-and-drop,
 * a future "recent files" list - must route through {@link #validate(File)} first.
 *
 * <p>This is a first line of defense, not the only one: the ingestion layer's own parsers (Tika,
 * PDFBox) are already scoped to a minimal parser set and carry their own output caps. What this
 * class adds is failing fast, with a message the user can act on, before any of that heavier work
 * starts - and refusing the categories of input (wrong type, empty, oversized, a directory, a
 * symlink to nowhere) that have no business reaching the pipeline at all.
 *
 * <p>Stateless and side-effect-free apart from the file-system reads needed to check the candidate
 * file itself, so it is safe to share and to call from any thread.
 */
public final class FileValidationService {

    /** Extensions the ingestion layer (PDFBox direct + the scoped Tika parsers) actually accepts. */
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx", "txt");

    private final long maxSizeBytes;

    public FileValidationService(long maxSizeBytes) {
        if (maxSizeBytes <= 0) {
            throw new IllegalArgumentException("maxSizeBytes must be > 0");
        }
        this.maxSizeBytes = maxSizeBytes;
    }

    public long maxSizeBytes() {
        return maxSizeBytes;
    }

    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    /**
     * Validates a candidate file from the file chooser or a drag-and-drop event.
     *
     * @throws FileValidationException with a message safe to show directly to the user
     */
    public ValidatedFile validate(File candidate) {
        if (candidate == null) {
            throw new FileValidationException("No file was given.");
        }
        return validate(candidate.toPath());
    }

    /** @throws FileValidationException with a message safe to show directly to the user */
    public ValidatedFile validate(Path candidate) {
        if (candidate == null) {
            throw new FileValidationException("No file was given.");
        }

        Path real;
        try {
            // Resolves symlinks and requires the target to actually exist, in one call - so a
            // dangling symlink or a path that vanished between the drop event and this check is
            // rejected here rather than surfacing as a confusing failure deeper in the pipeline.
            real = candidate.toRealPath();
        } catch (IOException e) {
            throw new FileValidationException(
                    "Couldn't find or access \"" + candidate.getFileName() + "\".");
        }

        if (!Files.isRegularFile(real)) {
            throw new FileValidationException(
                    "\"" + real.getFileName() + "\" is not a file Statigate can read "
                            + "(is it a folder?).");
        }

        String extension = extensionOf(real);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new FileValidationException(
                    "Unsupported file type" + (extension.isEmpty() ? "" : " \"." + extension + "\"")
                            + ". Statigate reads " + formatSupportedExtensions() + " files.");
        }

        long size;
        try {
            size = Files.size(real);
        } catch (IOException e) {
            throw new FileValidationException("Couldn't read the size of \"" + real.getFileName() + "\".");
        }

        if (size == 0) {
            throw new FileValidationException("\"" + real.getFileName() + "\" is empty.");
        }
        if (size > maxSizeBytes) {
            throw new FileValidationException(
                    "\"" + real.getFileName() + "\" is " + humanSize(size) + ", which is over the "
                            + humanSize(maxSizeBytes) + " limit for this app.");
        }

        return new ValidatedFile(real, real.getFileName().toString(), size);
    }

    private static String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String formatSupportedExtensions() {
        return String.join(", ", SUPPORTED_EXTENSIONS.stream().map(e -> "." + e).sorted().toList());
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.0f KB", kb);
        }
        double mb = kb / 1024.0;
        return String.format(Locale.ROOT, "%.1f MB", mb);
    }
}
