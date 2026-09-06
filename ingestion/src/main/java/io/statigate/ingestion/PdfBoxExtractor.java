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
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PDF text extraction via PDFBox, page by page, so downstream spans can carry a real page number.
 *
 * <p>Hardening for untrusted input: memory-mapped/scratch-file buffering above a small heap budget,
 * a hard page cap, a total-character cap, refusal of encrypted files that forbid extraction, and no
 * rendering or font loading (pure text stripping).
 */
public final class PdfBoxExtractor implements DocumentExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfBoxExtractor.class);

    private static final long MAX_HEAP_BYTES = 48L * 1024 * 1024;
    private static final int MAX_PAGES = 3_000;
    private static final int MAX_TOTAL_CHARS = 15_000_000;

    @Override
    public boolean supports(String mediaType) {
        return "application/pdf".equalsIgnoreCase(mediaType);
    }

    @Override
    public ExtractedDoc extract(Path file) throws IOException {
        MemoryUsageSetting mem = MemoryUsageSetting.setupMixed(MAX_HEAP_BYTES);
        try (PDDocument doc = PDDocument.load(file.toFile(), "", mem)) {
            if (doc.isEncrypted() && !doc.getCurrentAccessPermission().canExtractContent()) {
                throw new IOException("PDF is encrypted and does not permit text extraction: " + file);
            }
            int pageCount = doc.getNumberOfPages();
            if (pageCount <= 0) {
                throw new IOException("PDF has no pages: " + file);
            }
            int pagesToRead = Math.min(pageCount, MAX_PAGES);
            if (pagesToRead < pageCount) {
                log.warn("PDF has {} pages; reading only the first {}", pageCount, pagesToRead);
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setSuppressDuplicateOverlappingText(true);
            stripper.setAddMoreFormatting(false);

            List<PageText> pages = new ArrayList<>(pagesToRead);
            int total = 0;
            for (int p = 1; p <= pagesToRead; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc);
                total += text.length();
                if (total > MAX_TOTAL_CHARS) {
                    throw new IOException("PDF text exceeds the " + MAX_TOTAL_CHARS + "-char limit: " + file);
                }
                pages.add(new PageText(p, text));
            }
            log.info("Extracted {} chars from {} across {} page(s)", total, file.getFileName(), pages.size());
            return new ExtractedDoc(pages, "application/pdf");
        } catch (InvalidPasswordException e) {
            throw new IOException("PDF is password-protected: " + file, e);
        }
    }
}
