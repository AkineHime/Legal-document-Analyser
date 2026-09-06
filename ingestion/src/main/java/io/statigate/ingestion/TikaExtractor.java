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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;
import org.apache.tika.parser.txt.TXTParser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * Fallback extractor for non-PDF contract formats (DOCX, plain text) using Apache Tika.
 *
 * <p>Only the OOXML and plain-text parsers are wired in — not Tika's auto-detect parser — so the
 * attack surface is limited to the formats a contract tool actually needs. Output is a single
 * "page" since these formats have no reliable page model in extracted text.
 */
public final class TikaExtractor implements DocumentExtractor {

    private static final Logger log = LoggerFactory.getLogger(TikaExtractor.class);
    private static final int WRITE_LIMIT = 15_000_000;

    private final OOXMLParser ooxmlParser = new OOXMLParser();
    private final TXTParser txtParser = new TXTParser();

    @Override
    public boolean supports(String mediaType) {
        if (mediaType == null) {
            return false;
        }
        String m = mediaType.toLowerCase();
        return m.startsWith("text/")
                || m.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Override
    public ExtractedDoc extract(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Not a readable file: " + file);
        }
        var handler = new BodyContentHandler(WRITE_LIMIT);
        var metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getFileName().toString());

        // No EmbeddedDocumentExtractor is registered, so embedded objects are not recursed into.
        var context = new ParseContext();
        boolean docx = file.getFileName().toString().toLowerCase().endsWith(".docx");

        try (InputStream in = Files.newInputStream(file)) {
            (docx ? ooxmlParser : txtParser).parse(in, handler, metadata, context);
        } catch (SAXException | TikaException e) {
            throw new IOException("Failed to parse " + file + ": " + e.getMessage(), e);
        }

        String mediaType = metadata.get(Metadata.CONTENT_TYPE);
        String text = handler.toString();
        log.info("Extracted {} chars from {} ({})", text.length(), file.getFileName(), mediaType);
        return new ExtractedDoc(List.of(new PageText(1, text)),
                mediaType == null ? (docx
                        ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        : "text/plain") : mediaType);
    }
}
