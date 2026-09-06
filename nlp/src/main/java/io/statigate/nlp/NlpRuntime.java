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

package io.statigate.nlp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the single shared InLegalBERT encoder for a run. Extraction and grounding both need it;
 * loading the 100 MB quantized model twice would be wasteful, so they take an {@code NlpRuntime}
 * and share one {@link OnnxTextEncoder}.
 *
 * <p>The encoder is created lazily on first request and closed with the runtime. If the model
 * assets are absent, {@link #encoder()} is empty and callers fall back to model-free behaviour.
 */
public final class NlpRuntime implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NlpRuntime.class);

    private final ModelLocator locator;
    private final int maxSeqLen;
    private final int intraOpThreads;

    private OnnxTextEncoder encoder;
    private boolean initialized;

    public NlpRuntime(ModelLocator locator) {
        this(locator, 256, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
    }

    public NlpRuntime(ModelLocator locator, int maxSeqLen, int intraOpThreads) {
        this.locator = locator;
        this.maxSeqLen = maxSeqLen;
        this.intraOpThreads = intraOpThreads;
    }

    public ModelLocator locator() {
        return locator;
    }

    public boolean modelAvailable() {
        return locator.inLegalBertAvailable();
    }

    public synchronized Optional<OnnxTextEncoder> encoder() {
        if (!initialized) {
            initialized = true;
            if (locator.inLegalBertAvailable()) {
                boolean lowercase = readDoLowerCase();
                int seq = Math.min(maxSeqLen, readMaxSeq());
                BertTokenizer tok = BertTokenizer.fromVocabFile(
                        locator.inLegalBertVocab(), lowercase, lowercase);
                encoder = new OnnxTextEncoder(locator.inLegalBertOnnx(), tok, seq, intraOpThreads);
            } else {
                log.warn("InLegalBERT model not found under {} - running model-free.",
                        locator.inLegalBertDir());
            }
        }
        return Optional.ofNullable(encoder);
    }

    private boolean readDoLowerCase() {
        JsonNode meta = readMeta();
        return meta == null || meta.path("do_lower_case").asBoolean(true);
    }

    private int readMaxSeq() {
        JsonNode meta = readMeta();
        return meta == null ? 512 : meta.path("max_sequence_length").asInt(512);
    }

    private JsonNode readMeta() {
        Path p = locator.inLegalBertDir().resolve("export_metadata.json");
        if (!Files.isRegularFile(p)) {
            return null;
        }
        try {
            return new ObjectMapper().readTree(p.toFile());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public synchronized void close() {
        if (encoder != null) {
            encoder.close();
            encoder = null;
        }
    }
}
