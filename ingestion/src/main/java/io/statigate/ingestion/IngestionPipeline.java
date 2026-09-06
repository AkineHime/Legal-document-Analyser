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

import io.statigate.core.Document;
import io.statigate.core.Sentence;
import io.statigate.core.SourceSpan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns an input file into a {@link Document}: media-type detection, per-page text extraction,
 * normalization, page-offset indexing, and sentence segmentation with source spans that carry the
 * page they came from.
 *
 * <pre>{@code
 * var doc = new IngestionPipeline().ingest(Path.of("contract.pdf"));
 * }</pre>
 */
public final class IngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);
    private static final String PAGE_SEPARATOR = "\n\n";

    private final Tika tika = new Tika();
    private final List<DocumentExtractor> extractors;
    private final SentenceSplitter sentenceSplitter;

    public IngestionPipeline() {
        this(List.of(new PdfBoxExtractor(), new TikaExtractor()), new OpenNlpSentenceSplitter());
    }

    public IngestionPipeline(List<DocumentExtractor> extractors, SentenceSplitter sentenceSplitter) {
        this.extractors = List.copyOf(extractors);
        this.sentenceSplitter = sentenceSplitter;
    }

    public Document ingest(Path file) throws IOException {
        Path normalized = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IOException("Not a readable file: " + normalized);
        }
        long size = Files.size(normalized);
        if (size == 0) {
            throw new IOException("File is empty: " + normalized);
        }

        String mediaType = tika.detect(normalized);
        DocumentExtractor extractor = extractors.stream()
                .filter(e -> e.supports(mediaType))
                .findFirst()
                .orElseThrow(() -> new IOException("Unsupported document type '" + mediaType
                        + "'. Supported: PDF, DOCX, plain text."));

        ExtractedDoc extracted = extractor.extract(normalized);

        StringBuilder clean = new StringBuilder();
        int[] pageStarts = new int[extracted.pageCount()];
        for (int i = 0; i < extracted.pageCount(); i++) {
            if (i > 0) {
                clean.append(PAGE_SEPARATOR);
            }
            pageStarts[i] = clean.length();
            clean.append(TextNormalizer.normalize(extracted.pages().get(i).text()));
        }
        String cleanText = clean.toString().strip();
        PageIndex pageIndex = new PageIndex(pageStarts);

        List<Sentence> sentences = withPages(sentenceSplitter.split(cleanText), pageIndex);
        log.info("Ingested {} ({}): {} chars, {} pages, {} sentences",
                normalized.getFileName(), mediaType, cleanText.length(),
                extracted.pageCount(), sentences.size());

        return new Document(normalized.toString(), mediaType, extracted.pageCount(), cleanText, sentences);
    }

    private static List<Sentence> withPages(List<Sentence> sentences, PageIndex pageIndex) {
        List<Sentence> out = new ArrayList<>(sentences.size());
        for (Sentence s : sentences) {
            SourceSpan span = s.span();
            out.add(new Sentence(s.index(), s.text(),
                    new SourceSpan(span.start(), span.end(), pageIndex.pageOf(span.start()))));
        }
        return List.copyOf(out);
    }
}
