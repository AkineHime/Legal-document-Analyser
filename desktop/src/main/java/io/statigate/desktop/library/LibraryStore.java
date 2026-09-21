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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.statigate.core.RiskFlag;
import io.statigate.core.Severity;
import io.statigate.pipeline.PipelineResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A local, offline library of previously analyzed documents, so a report can be revisited without
 * re-running the pipeline. Every document lives in its own subdirectory of {@link #root()}, named
 * by a generated id (never derived from the original file name), holding:
 *
 * <pre>
 * &lt;root&gt;/&lt;id&gt;/meta.json     - a LibraryEntry (fast to list)
 * &lt;root&gt;/&lt;id&gt;/result.json   - the full PipelineResult (loaded lazily, only when opened)
 * &lt;root&gt;/&lt;id&gt;/source.&lt;ext&gt; - a copy of the original file (present only if requested)
 * </pre>
 *
 * <p>A directory-per-document layout with no central index file is deliberate: a single corrupt or
 * half-written entry affects only itself, never the rest of the library, and {@link #listEntries()}
 * simply skips (and logs) whatever it cannot read.
 *
 * <p>This is local, offline storage in the same spirit as the rest of the application - nothing
 * here ever makes a network call, and everything stays under one directory the user can find,
 * back up, or delete themselves.
 */
public final class LibraryStore {

    private static final Logger log = LoggerFactory.getLogger(LibraryStore.class);
    private static final String META_FILE = "meta.json";
    private static final String RESULT_FILE = "result.json";
    private static final String SOURCE_FILE_PREFIX = "source.";

    private final Path root;
    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public LibraryStore(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    /** {@code %APPDATA%\Statigate\library} on Windows, {@code ~/.statigate/library} elsewhere. */
    public static Path defaultRoot() {
        String appData = System.getenv("APPDATA");
        Path base = (appData != null && !appData.isBlank())
                ? Path.of(appData, "Statigate")
                : Path.of(System.getProperty("user.home", "."), ".statigate");
        return base.resolve("library");
    }

    public Path root() {
        return root;
    }

    /**
     * Saves a completed analysis. If {@code copySource} is true but copying the original file fails
     * for any reason (it was moved, a permissions issue, a full disk), the analysis is still saved
     * without a source copy rather than losing the whole entry over one failed step.
     *
     * @param displayName     the original file name, for display
     * @param sourceFile      the analyzed file's current location, or {@code null} to never copy it
     * @param sourceExtension the original file's extension (without the dot)
     * @param copySource      whether to also store a copy of {@code sourceFile}
     * @param result          the analysis to save
     */
    public synchronized LibraryEntry save(String displayName, Path sourceFile, String sourceExtension,
            boolean copySource, PipelineResult result) throws IOException {
        String id = UUID.randomUUID().toString();
        Path tempDir = root.resolve(id + ".tmp");
        Path finalDir = requireInRoot(id);
        Files.createDirectories(tempDir);
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(tempDir.resolve(RESULT_FILE).toFile(), result);

            boolean stored = false;
            if (copySource && sourceFile != null) {
                try {
                    Files.copy(sourceFile, tempDir.resolve(SOURCE_FILE_PREFIX + safeExtension(sourceExtension)),
                            StandardCopyOption.REPLACE_EXISTING);
                    stored = true;
                } catch (IOException e) {
                    log.warn("Could not copy source file for library entry {} ({}); saving analysis only.",
                            id, e.toString());
                }
            }

            int clauseRisks = result.clauses().stream().mapToInt(f -> f.risks().size()).sum();
            LibraryEntry entry = new LibraryEntry(id, displayName, System.currentTimeMillis(),
                    result.clauses().size(), result.documentRisks().size() + clauseRisks,
                    result.entities().size(), result.totalMillis(), result.backends(),
                    stored, safeExtension(sourceExtension), highestSeverityOf(result),
                    sourceFile == null ? null : contentHashOf(sourceFile).orElse(null));
            mapper.writerWithDefaultPrettyPrinter().writeValue(tempDir.resolve(META_FILE).toFile(), entry);

            moveIntoPlace(tempDir, finalDir);
            return entry;
        } catch (IOException | RuntimeException e) {
            deleteRecursively(tempDir);
            throw e instanceof IOException io ? io : new IOException("Failed to save library entry", e);
        }
    }

    /**
     * Updates {@code id}'s saved timestamp to now, in place - no new directory, no duplicate entry.
     * Used when a document that was already analyzed is dropped in again unchanged: rather than
     * saving a second copy of the same content, the existing entry is bumped to the front of the
     * library (as "most recently confirmed") and left otherwise untouched.
     */
    public synchronized Optional<LibraryEntry> touch(String id) {
        Path dir = safeDocDir(id);
        if (dir == null || !Files.isDirectory(dir)) {
            return Optional.empty();
        }
        Path metaFile = dir.resolve(META_FILE);
        if (!Files.isRegularFile(metaFile)) {
            return Optional.empty();
        }
        try {
            LibraryEntry current = mapper.readValue(metaFile.toFile(), LibraryEntry.class);
            LibraryEntry refreshed = new LibraryEntry(current.id(), current.displayName(),
                    System.currentTimeMillis(), current.clauseCount(), current.riskCount(),
                    current.entityCount(), current.totalMillis(), current.backends(),
                    current.hasStoredSource(), current.sourceExtension(), current.highestSeverity(),
                    current.contentHash());
            Path tempFile = dir.resolve(META_FILE + ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), refreshed);
            replaceAtomically(tempFile, metaFile);
            return Optional.of(refreshed);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not refresh library entry {}: {}", id, e.toString());
            return Optional.empty();
        }
    }

    /** Newest first. Never throws: a library that cannot be read at all is reported as empty. */
    public synchronized List<LibraryEntry> listEntries() {
        List<LibraryEntry> entries = new ArrayList<>();
        try (Stream<Path> children = Files.list(root)) {
            for (Path dir : (Iterable<Path>) children::iterator) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                Path metaFile = dir.resolve(META_FILE);
                if (!Files.isRegularFile(metaFile)) {
                    continue;
                }
                try {
                    entries.add(mapper.readValue(metaFile.toFile(), LibraryEntry.class));
                } catch (IOException | RuntimeException e) {
                    log.warn("Skipping unreadable library entry at {}: {}", dir, e.toString());
                }
            }
        } catch (IOException e) {
            log.warn("Could not list the document library at {}: {}", root, e.toString());
        }
        entries.sort(Comparator.comparingLong(LibraryEntry::analyzedAtEpochMillis).reversed());
        return List.copyOf(entries);
    }

    /**
     * The most recently saved entry whose stored file has this exact content, if any - lets a
     * caller recognize "this exact document again" (byte-for-byte, regardless of file name) before
     * re-running the pipeline on it. Entries with no recorded hash (saved before this field existed,
     * or whose source file could not be read at save time) never match.
     */
    public synchronized Optional<LibraryEntry> findByContentHash(String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return Optional.empty();
        }
        return listEntries().stream()
                .filter(e -> contentHash.equals(e.contentHash()))
                .findFirst();
    }

    /** A SHA-256 hex digest of a file's bytes, or empty if it could not be read or hashed. */
    public static Optional<String> contentHashOf(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream digestIn = new DigestInputStream(in, digest)) {
                byte[] buffer = new byte[8192];
                while (digestIn.read(buffer) != -1) {
                    // draining the stream is enough - DigestInputStream updates the digest as it goes
                }
            }
            return Optional.of(HexFormat.of().formatHex(digest.digest()));
        } catch (IOException e) {
            log.warn("Could not hash '{}' for duplicate detection: {}", file, e.toString());
            return Optional.empty();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JDK algorithm (JLS/JCA spec); unreachable in practice.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public synchronized Optional<PipelineResult> loadResult(String id) {
        Path dir = safeDocDir(id);
        if (dir == null) {
            return Optional.empty();
        }
        Path resultFile = dir.resolve(RESULT_FILE);
        if (!Files.isRegularFile(resultFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(resultFile.toFile(), PipelineResult.class));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not load library entry {}: {}", id, e.toString());
            return Optional.empty();
        }
    }

    public synchronized Optional<Path> sourceFileFor(String id) {
        Path dir = safeDocDir(id);
        if (dir == null) {
            return Optional.empty();
        }
        try (Stream<Path> children = Files.list(dir)) {
            return children.filter(p -> p.getFileName().toString().startsWith(SOURCE_FILE_PREFIX)).findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public synchronized boolean delete(String id) {
        Path dir = safeDocDir(id);
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        return deleteRecursively(dir);
    }

    /**
     * Resolves {@code id} against {@link #root} and confirms the result cannot escape it, so a
     * malformed or hostile id (e.g. containing {@code ..}) is refused rather than resolved. Every
     * id this class itself hands out is a generated UUID and always safe; this exists for the
     * public {@code loadResult}/{@code sourceFileFor}/{@code delete} methods, whose contract does
     * not otherwise prevent a caller from passing an arbitrary string.
     */
    private Path safeDocDir(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Path candidate = root.resolve(id).normalize();
        if (!candidate.startsWith(root) || candidate.equals(root)) {
            log.warn("Rejected a library id that would resolve outside the library root: {}", id);
            return null;
        }
        return candidate;
    }

    private Path requireInRoot(String generatedId) {
        Path candidate = safeDocDir(generatedId);
        if (candidate == null) {
            // Unreachable in practice (generatedId is always a fresh UUID), but fail loudly rather
            // than silently writing somewhere unexpected if that ever changes.
            throw new IllegalStateException("Generated library id was rejected: " + generatedId);
        }
        return candidate;
    }

    private static void moveIntoPlace(Path tempDir, Path finalDir) throws IOException {
        try {
            Files.move(tempDir, finalDir, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempDir, finalDir);
        }
    }

    /** Like {@link #moveIntoPlace}, but for overwriting a file that already exists (e.g. {@link #touch}). */
    private static void replaceAtomically(Path tempFile, Path finalFile) throws IOException {
        try {
            Files.move(tempFile, finalFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, finalFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return true;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            boolean[] ok = { true };
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    ok[0] = false;
                    log.warn("Could not delete {}: {}", p, e.toString());
                }
            });
            return ok[0];
        } catch (IOException e) {
            log.warn("Could not walk {} for deletion: {}", dir, e.toString());
            return false;
        }
    }

    private static String highestSeverityOf(PipelineResult result) {
        Severity highest = Stream.concat(
                        result.documentRisks().stream(),
                        result.clauses().stream().flatMap(f -> f.risks().stream()))
                .map(RiskFlag::severity)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return highest == null ? null : highest.name();
    }

    private static String safeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "dat";
        }
        String cleaned = extension.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return cleaned.isEmpty() ? "dat" : cleaned;
    }
}
