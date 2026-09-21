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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.statigate.core.Citation;
import io.statigate.core.Clause;
import io.statigate.core.ClauseType;
import io.statigate.core.Document;
import io.statigate.core.RiskFlag;
import io.statigate.core.Severity;
import io.statigate.core.SourceSpan;
import io.statigate.extraction.ClauseClassification;
import io.statigate.pipeline.ClauseFinding;
import io.statigate.pipeline.PipelineResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void savedEntryRoundTripsThroughListAndLoad() throws IOException {
        LibraryStore store = new LibraryStore(tempDir.resolve("library"));
        Path sourceFile = Files.writeString(tempDir.resolve("contract.txt"), "1. Indemnity. ...");

        PipelineResult result = sampleResult();
        LibraryEntry saved = store.save("contract.txt", sourceFile, "txt", true, result);

        assertTrue(saved.hasStoredSource());
        assertEquals(1, saved.clauseCount());
        assertEquals(1, saved.riskCount());
        assertEquals(0, saved.entityCount());
        assertEquals("HIGH", saved.highestSeverity());

        List<LibraryEntry> listed = store.listEntries();
        assertEquals(1, listed.size());
        assertEquals(saved.id(), listed.get(0).id());
        assertEquals("contract.txt", listed.get(0).displayName());

        PipelineResult loaded = store.loadResult(saved.id()).orElseThrow();
        assertEquals(result.clauses().size(), loaded.clauses().size());
        assertEquals(result.clauses().get(0).clause().type(), loaded.clauses().get(0).clause().type());
        assertEquals(result.document().cleanText(), loaded.document().cleanText());
    }

    @Test
    void sourceFileIsRetrievableAfterSaving() throws IOException {
        LibraryStore store = new LibraryStore(tempDir.resolve("library"));
        Path sourceFile = Files.writeString(tempDir.resolve("nda.pdf"), "pretend pdf bytes");

        LibraryEntry saved = store.save("nda.pdf", sourceFile, "pdf", true, sampleResult());

        Path stored = store.sourceFileFor(saved.id()).orElseThrow();
        assertTrue(Files.isRegularFile(stored));
        assertEquals("pretend pdf bytes", Files.readString(stored));
        assertTrue(stored.getFileName().toString().endsWith(".pdf"));
    }

    @Test
    void copySourceFalseNeverStoresTheOriginalFile() throws IOException {
        LibraryStore store = new LibraryStore(tempDir.resolve("library"));
        Path sourceFile = Files.writeString(tempDir.resolve("contract.txt"), "text");

        LibraryEntry saved = store.save("contract.txt", sourceFile, "txt", false, sampleResult());

        assertFalse(saved.hasStoredSource());
        assertTrue(store.sourceFileFor(saved.id()).isEmpty());
    }

    @Test
    void missingSourceFileDegradesToAnalysisOnlyRatherThanFailingTheWholeSave() throws IOException {
        LibraryStore store = new LibraryStore(tempDir.resolve("library"));
        Path doesNotExist = tempDir.resolve("vanished.txt");

        LibraryEntry saved = store.save("vanished.txt", doesNotExist, "txt", true, sampleResult());

        assertFalse(saved.hasStoredSource());
        assertEquals(1, store.listEntries().size(), "the analysis itself must still be saved");
    }

    @Test
    void deleteRemovesTheEntryEntirely() throws IOException {
        LibraryStore store = new LibraryStore(tempDir.resolve("library"));
        LibraryEntry saved = store.save("contract.txt", null, "txt", false, sampleResult());

        assertTrue(store.delete(saved.id()));

        assertTrue(store.listEntries().isEmpty());
        assertTrue(store.loadResult(saved.id()).isEmpty());
    }

    @Test
    void deleteOfUnknownIdReturnsFalseRatherThanThrowing() throws IOException {
        LibraryStore store = new LibraryStore(tempDir.resolve("library"));
        assertFalse(store.delete("no-such-id"));
    }

    @Test
    void listIsSortedNewestFirst() throws Exception {
        LibraryStore store = new LibraryStore(tempDir.resolve("library"));
        store.save("first.txt", null, "txt", false, sampleResult());
        Thread.sleep(5); // epoch-millis resolution guard, keeps the ordering assertion meaningful
        store.save("second.txt", null, "txt", false, sampleResult());

        List<LibraryEntry> entries = store.listEntries();
        assertEquals(2, entries.size());
        assertEquals("second.txt", entries.get(0).displayName());
        assertEquals("first.txt", entries.get(1).displayName());
    }

    @Test
    void corruptEntryIsSkippedNotFatal() throws IOException {
        LibraryStore store = new LibraryStore(tempDir.resolve("library"));
        store.save("good.txt", null, "txt", false, sampleResult());

        Path corruptDir = Files.createDirectory(store.root().resolve("corrupt-entry"));
        Files.writeString(corruptDir.resolve("meta.json"), "{ not valid json at all");

        List<LibraryEntry> entries = store.listEntries();
        assertEquals(1, entries.size(), "the corrupt entry should be skipped, not crash the listing");
        assertEquals("good.txt", entries.get(0).displayName());
    }

    @Test
    void pathTraversalIdsAreRejectedRatherThanEscapingTheLibraryRoot() throws IOException {
        LibraryStore store = new LibraryStore(tempDir.resolve("library"));
        // A sentinel file just outside the library root that a traversal attempt might target.
        Path sentinel = Files.writeString(tempDir.resolve("secret.txt"), "do not touch");

        assertTrue(store.loadResult("../secret").isEmpty());
        assertTrue(store.sourceFileFor("../secret.txt").isEmpty());
        assertFalse(store.delete("../secret.txt"));
        assertFalse(store.delete(".."));
        assertFalse(store.delete("."));

        assertEquals("do not touch", Files.readString(sentinel), "the sentinel file must be untouched");
    }

    @Test
    void highestSeverityIsNullWhenThereAreNoRiskFlags() throws IOException {
        LibraryStore store = new LibraryStore(tempDir.resolve("library"));
        Document document = new Document("/tmp/clean.txt", "text/plain", 1, "clean text", List.of());
        PipelineResult noRisks = new PipelineResult(document, List.of(), List.of(), List.of(), List.of(),
                Map.of("total", 1L), Map.of());

        LibraryEntry saved = store.save("clean.txt", null, "txt", false, noRisks);

        assertNull(saved.highestSeverity());
    }

    @Test
    void highestSeverityPicksTheMaxAcrossDocumentAndClauseLevelFlags() throws IOException {
        LibraryStore store = new LibraryStore(tempDir.resolve("library"));
        Document document = new Document("/tmp/mixed.txt", "text/plain", 1, "some clause text", List.of());
        SourceSpan span = new SourceSpan(0, document.cleanText().length(), 1);
        RiskFlag lowFlag = new RiskFlag(Severity.LOW, "auto-renewal", "Renews automatically.",
                Citation.documentOnly(span, document.cleanText()));
        RiskFlag highFlag = new RiskFlag(Severity.HIGH, "uncapped-indemnity", "No cap found.",
                Citation.documentOnly(span, document.cleanText()));
        PipelineResult mixed = new PipelineResult(document, List.of(), List.of(lowFlag, highFlag), List.of(),
                List.of(), Map.of("total", 1L), Map.of());

        LibraryEntry saved = store.save("mixed.txt", null, "txt", false, mixed);

        assertEquals("HIGH", saved.highestSeverity());
    }

    @Test
    void findByContentHashMatchesTheSameBytesEvenUnderADifferentFileName() throws IOException {
        LibraryStore store = new LibraryStore(tempDir.resolve("library"));
        Path original = Files.writeString(tempDir.resolve("contract.txt"), "identical bytes");
        LibraryEntry saved = store.save("contract.txt", original, "txt", true, sampleResult());

        Path renamedCopy = Files.writeString(tempDir.resolve("renamed-copy.txt"), "identical bytes");
        String hash = LibraryStore.contentHashOf(renamedCopy).orElseThrow();

        LibraryEntry found = store.findByContentHash(hash).orElseThrow();
        assertEquals(saved.id(), found.id());
    }

    @Test
    void findByContentHashIsEmptyWhenTheBytesDiffer() throws IOException {
        LibraryStore store = new LibraryStore(tempDir.resolve("library"));
        Path original = Files.writeString(tempDir.resolve("contract.txt"), "version one");
        store.save("contract.txt", original, "txt", true, sampleResult());

        Path edited = Files.writeString(tempDir.resolve("edited.txt"), "version two");
        String hash = LibraryStore.contentHashOf(edited).orElseThrow();

        assertTrue(store.findByContentHash(hash).isEmpty());
    }

    @Test
    void findByContentHashIsEmptyForBlankOrNullInput() throws IOException {
        LibraryStore store = new LibraryStore(tempDir.resolve("library"));
        store.save("contract.txt", null, "txt", false, sampleResult());

        assertTrue(store.findByContentHash(null).isEmpty());
        assertTrue(store.findByContentHash("").isEmpty());
    }

    @Test
    void entriesSavedWithoutASourceFileHaveNoContentHash() throws IOException {
        LibraryStore store = new LibraryStore(tempDir.resolve("library"));
        LibraryEntry saved = store.save("contract.txt", null, "txt", false, sampleResult());

        assertNull(saved.contentHash());
    }

    @Test
    void touchUpdatesTheTimestampInPlaceWithoutCreatingANewEntry() throws Exception {
        LibraryStore store = new LibraryStore(tempDir.resolve("library"));
        LibraryEntry saved = store.save("contract.txt", null, "txt", false, sampleResult());
        Thread.sleep(5); // epoch-millis resolution guard

        LibraryEntry touched = store.touch(saved.id()).orElseThrow();

        assertEquals(saved.id(), touched.id(), "touch must update the existing entry, not create one");
        assertTrue(touched.analyzedAtEpochMillis() > saved.analyzedAtEpochMillis());
        assertEquals(1, store.listEntries().size(), "touch must never add a second entry");
        assertEquals(saved.displayName(), touched.displayName());
        assertEquals(saved.contentHash(), touched.contentHash());
    }

    @Test
    void touchOfUnknownIdReturnsEmptyRatherThanThrowing() throws IOException {
        LibraryStore store = new LibraryStore(tempDir.resolve("library"));
        assertTrue(store.touch("no-such-id").isEmpty());
    }

    @Test
    void defaultRootIsUnderTheCurrentUsersOwnAppData() {
        Path root = LibraryStore.defaultRoot();
        assertTrue(root.toString().toLowerCase(java.util.Locale.ROOT).contains("statigate"));
        assertTrue(root.getFileName().toString().equals("library"));
    }

    private static PipelineResult sampleResult() {
        Document document = new Document("/tmp/contract.txt", "text/plain", 1,
                "1. Indemnity. The Client shall indemnify the Provider without any monetary cap.", List.of());
        SourceSpan span = new SourceSpan(0, document.cleanText().length(), 1);
        Clause clause = new Clause("clause-1", ClauseType.INDEMNITY, document.cleanText(), span);
        ClauseClassification classification = new ClauseClassification(ClauseType.INDEMNITY, 0.95, null, "keyword");
        RiskFlag risk = new RiskFlag(Severity.HIGH, "uncapped-indemnity", "No monetary cap was found.",
                Citation.documentOnly(span, document.cleanText()));
        ClauseFinding finding = new ClauseFinding(clause, classification, List.of(risk), List.of(), List.of());
        return new PipelineResult(document, List.of(finding), List.of(), List.of(), List.of(),
                Map.of("total", 12L), Map.of("extraction", "keyword-only"));
    }
}
