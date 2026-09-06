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

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a quantized InLegalBERT encoder natively in the JVM via the ONNX Runtime Java API and turns
 * a span of contract text into a single dense sentence embedding (masked mean pooling of the last
 * hidden state, L2-normalized).
 *
 * <p>This is the component that demonstrates the project's core claim: a legal-domain transformer
 * doing useful work in-process, on CPU, with no Python and no network. One session is shared for
 * the process lifetime; {@link #embed} is safe to call concurrently.
 */
public final class OnnxTextEncoder implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OnnxTextEncoder.class);
    private static final String LAST_HIDDEN_STATE = "last_hidden_state";

    private final OrtEnvironment env;
    private final OrtSession session;
    private final BertTokenizer tokenizer;
    private final int maxSeqLen;
    private final String hiddenStateOutput;
    private final Set<String> inputNames;

    /** The same clause text is embedded by both the extraction and grounding stages; cache it. */
    private final java.util.concurrent.ConcurrentHashMap<String, float[]> cache =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_CACHE = 4096;

    public OnnxTextEncoder(Path onnxModel, BertTokenizer tokenizer, int maxSeqLen, int intraOpThreads) {
        if (!Files.isRegularFile(onnxModel)) {
            throw new UncheckedIOException(new IOException("ONNX model not found: " + onnxModel));
        }
        this.tokenizer = tokenizer;
        this.maxSeqLen = Math.max(8, maxSeqLen);
        this.env = OrtEnvironment.getEnvironment();
        try {
            var opts = new OrtSession.SessionOptions();
            opts.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);
            opts.setInterOpNumThreads(1);
            opts.setIntraOpNumThreads(Math.max(1, intraOpThreads));
            opts.setMemoryPatternOptimization(true);

            // Cache the graph-optimized model so later cold starts skip re-optimization.
            Path optimized = onnxModel.resolveSibling(
                    onnxModel.getFileName().toString().replace(".onnx", ".opt.onnx"));
            if (Files.isRegularFile(optimized)) {
                opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
                this.session = env.createSession(optimized.toString(), opts);
                log.debug("Loaded pre-optimized encoder graph {}", optimized.getFileName());
            } else {
                opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
                try {
                    opts.setOptimizedModelFilePath(optimized.toString());
                } catch (OrtException e) {
                    log.debug("Could not set optimized-model path: {}", e.toString());
                }
                this.session = env.createSession(onnxModel.toString(), opts);
            }
            this.inputNames = session.getInputNames();
            this.hiddenStateOutput = session.getOutputNames().contains(LAST_HIDDEN_STATE)
                    ? LAST_HIDDEN_STATE
                    : session.getOutputNames().iterator().next();
            log.info("Loaded ONNX encoder {} (inputs={}, output={}, maxSeq={}, intraOpThreads={})",
                    onnxModel.getFileName(), inputNames, hiddenStateOutput, this.maxSeqLen, intraOpThreads);
        } catch (OrtException e) {
            throw new IllegalStateException("Failed to load ONNX encoder " + onnxModel, e);
        }
    }

    public int dimension() {
        return 768;
    }

    /** Embeds one text; returns an L2-normalized vector. Results are cached by text. */
    public float[] embed(String text) {
        String key = text == null ? "" : text;
        float[] hit = cache.get(key);
        if (hit != null) {
            return hit;
        }
        float[] v = embedUncached(key);
        if (cache.size() < MAX_CACHE) {
            cache.putIfAbsent(key, v);
        }
        return v;
    }

    private float[] embedUncached(String text) {
        BertTokenizer.Encoding enc = tokenizer.encode(text == null ? "" : text, maxSeqLen);
        int len = Math.max(enc.length(), 1);
        long[][] ids = {pad(enc.inputIds(), len, tokenizer.padId())};
        long[][] mask = {pad(enc.attentionMask(), len, 0)};
        long[][] types = {pad(enc.tokenTypeIds(), len, 0)};

        Map<String, OnnxTensor> feeds = new HashMap<>();
        try {
            feeds.put("input_ids", OnnxTensor.createTensor(env, ids));
            feeds.put("attention_mask", OnnxTensor.createTensor(env, mask));
            if (inputNames.contains("token_type_ids")) {
                feeds.put("token_type_ids", OnnxTensor.createTensor(env, types));
            }
            feeds.keySet().retainAll(inputNames);

            try (OrtSession.Result result = session.run(feeds)) {
                float[][][] hidden = (float[][][]) result.get(hiddenStateOutput).orElseThrow().getValue();
                return maskedMeanPool(hidden[0], mask[0]);
            }
        } catch (OrtException e) {
            throw new IllegalStateException("ONNX inference failed", e);
        } finally {
            feeds.values().forEach(OnnxTensor::close);
        }
    }

    /** Drops all cached embeddings. Used by the benchmark to model a stream of unique documents. */
    public void clearCache() {
        cache.clear();
    }

    public int cacheSize() {
        return cache.size();
    }

    public float[][] embedAll(List<String> texts) {
        float[][] out = new float[texts.size()][];
        for (int i = 0; i < texts.size(); i++) {
            out[i] = embed(texts.get(i));
        }
        return out;
    }

    static float[] maskedMeanPool(float[][] tokenVectors, long[] mask) {
        int dim = tokenVectors.length == 0 ? 0 : tokenVectors[0].length;
        float[] sum = new float[dim];
        int count = 0;
        for (int t = 0; t < tokenVectors.length; t++) {
            if (mask[t] == 0L) {
                continue;
            }
            count++;
            float[] v = tokenVectors[t];
            for (int d = 0; d < dim; d++) {
                sum[d] += v[d];
            }
        }
        if (count == 0) {
            return sum;
        }
        double norm = 0;
        for (int d = 0; d < dim; d++) {
            sum[d] /= count;
            norm += (double) sum[d] * sum[d];
        }
        norm = Math.sqrt(norm);
        if (norm > 1e-12) {
            for (int d = 0; d < dim; d++) {
                sum[d] /= (float) norm;
            }
        }
        return sum;
    }

    public static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        return dot; // inputs are already L2-normalized
    }

    private static long[] pad(long[] src, int len, long padValue) {
        if (src.length == len) {
            return src;
        }
        long[] out = new long[len];
        System.arraycopy(src, 0, out, 0, Math.min(src.length, len));
        for (int i = src.length; i < len; i++) {
            out[i] = padValue;
        }
        return out;
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (OrtException e) {
            log.warn("Error closing ONNX session: {}", e.toString());
        }
    }
}
