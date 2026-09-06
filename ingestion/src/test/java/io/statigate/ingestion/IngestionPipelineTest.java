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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.statigate.core.Clause;
import io.statigate.core.Document;
import io.statigate.core.Sentence;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** End-to-end ingestion against the synthetic sample contract PDF. */
class IngestionPipelineTest {

    private static final Path SAMPLE =
            Path.of("..", "samples", "sample_service_agreement.pdf").toAbsolutePath().normalize();

    static boolean sampleExists() {
        return Files.isRegularFile(SAMPLE);
    }

    private static Document doc;

    @BeforeAll
    static void ingestOnce() throws IOException {
        if (sampleExists()) {
            doc = new IngestionPipeline().ingest(SAMPLE);
        }
    }

    @Test
    @EnabledIf("sampleExists")
    void extractsTextAndSentences() {
        assertEquals("application/pdf", doc.mediaType());
        assertEquals(2, doc.pageCount());
        assertTrue(doc.charCount() > 2500, "expected a few thousand chars, got " + doc.charCount());
        assertTrue(doc.sentenceCount() >= 20, "expected >= 20 sentences, got " + doc.sentenceCount());
    }

    @Test
    @EnabledIf("sampleExists")
    void sentenceSpansSliceBackToCleanText() {
        for (Sentence s : doc.sentences()) {
            assertEquals(s.text(), doc.cleanText().substring(s.span().start(), s.span().end()));
        }
    }

    @Test
    @EnabledIf("sampleExists")
    void sentencesCarryPlausiblePageNumbers() {
        List<Sentence> sentences = doc.sentences();
        assertEquals(1, sentences.get(0).span().page());
        int maxPage = sentences.stream().mapToInt(s -> s.span().page()).max().orElse(0);
        assertTrue(maxPage >= 1 && maxPage <= 2, "page numbers out of range: " + maxPage);
        // pages are non-decreasing through the document
        int prev = 0;
        for (Sentence s : sentences) {
            assertTrue(s.span().page() >= prev, "page went backwards at sentence " + s.index());
            prev = s.span().page();
        }
    }

    @Test
    @EnabledIf("sampleExists")
    void clauseSegmenterFindsTheNumberedClauses() {
        List<Clause> clauses = new ClauseSegmenter().segment(doc);
        assertTrue(clauses.size() >= 8, "expected >= 8 clauses, got " + clauses.size());
        for (Clause c : clauses) {
            assertEquals(c.text(), doc.cleanText().substring(c.span().start(), c.span().end()).strip());
        }
        String joined = clauses.stream().map(Clause::text).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(joined.contains("INDEMNITY"), "indemnity clause missing");
        assertTrue(joined.contains("GOVERNING LAW"), "governing-law clause missing");
        assertTrue(joined.contains("Arbitration and Conciliation Act, 1996"));
    }

    @Test
    void rejectsMissingFile() {
        assertThrows(IOException.class,
                () -> new IngestionPipeline().ingest(Path.of("does-not-exist-9f3a.pdf")));
    }

    @Test
    void rejectsEmptyFile(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        Path empty = Files.createFile(tmp.resolve("empty.txt"));
        assertThrows(IOException.class, () -> new IngestionPipeline().ingest(empty));
    }

    @Test
    void ingestsPlainTextWithoutHeadings(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        Path txt = tmp.resolve("note.txt");
        Files.writeString(txt, "The parties agree as follows. The fee is INR 10,000. "
                + "This is governed by Indian law.");
        Document d = new IngestionPipeline().ingest(txt);
        assertTrue(d.mediaType().startsWith("text/"));
        assertEquals(1, d.pageCount());
        assertFalse(d.sentences().isEmpty());
    }
}
