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

package io.statigate.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import io.statigate.core.Clause;
import io.statigate.nlp.OnnxTextEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nearest-centroid clause classification over InLegalBERT sentence embeddings. Seed phrases per
 * type ({@code /clause_seeds.json}) are embedded once at construction; a clause is labelled by the
 * centroid it is most similar to.
 *
 * <p>This is the module that puts the legal-domain transformer to work: no fine-tuning, no training
 * data, just the pretrained encoder and a handful of prototypes.
 */
public final class EmbeddingClauseClassifier implements ClauseClassifier {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClauseClassifier.class);

    private final OnnxTextEncoder encoder;
    private final Map<String, float[]> centroids = new LinkedHashMap<>();
    private final double acceptSimilarity;
    private final double acceptMargin;

    public EmbeddingClauseClassifier(OnnxTextEncoder encoder) {
        this(encoder, 0.55, 0.015);
    }

    public EmbeddingClauseClassifier(OnnxTextEncoder encoder, double acceptSimilarity, double acceptMargin) {
        this.encoder = encoder;
        this.acceptSimilarity = acceptSimilarity;
        this.acceptMargin = acceptMargin;
        buildCentroids();
    }

    private void buildCentroids() {
        JsonNode root = JsonResources.load("/clause_seeds.json");
        root.fields().forEachRemaining(e -> {
            if (e.getKey().startsWith("_") || !e.getValue().isArray()) {
                return;
            }
            List<String> seeds = new ArrayList<>();
            e.getValue().forEach(n -> seeds.add(n.asText()));
            float[][] vectors = encoder.embedAll(seeds);
            centroids.put(e.getKey(), normalize(mean(vectors)));
        });
        log.info("Built {} clause centroids from InLegalBERT embeddings", centroids.size());
    }

    Map<String, Double> similarities(String clauseText) {
        float[] v = encoder.embed(clauseText);
        Map<String, Double> out = new LinkedHashMap<>();
        centroids.forEach((type, centroid) -> out.put(type, OnnxTextEncoder.cosine(v, centroid)));
        return out;
    }

    @Override
    public ClauseClassification classify(String clauseText) {
        Map<String, Double> sims = similarities(clauseText);
        String best = null;
        String second = null;
        double bestSim = -1;
        double secondSim = -1;
        for (var e : sims.entrySet()) {
            if (e.getValue() > bestSim) {
                second = best;
                secondSim = bestSim;
                best = e.getKey();
                bestSim = e.getValue();
            } else if (e.getValue() > secondSim) {
                second = e.getKey();
                secondSim = e.getValue();
            }
        }
        boolean accept = best != null && bestSim >= acceptSimilarity && (bestSim - secondSim) >= acceptMargin;
        return new ClauseClassification(accept ? best : Clause.UNCLASSIFIED,
                Math.max(0, bestSim), second, "inlegalbert-centroid");
    }

    private static float[] mean(float[][] vectors) {
        int dim = vectors[0].length;
        float[] m = new float[dim];
        for (float[] v : vectors) {
            for (int d = 0; d < dim; d++) {
                m[d] += v[d];
            }
        }
        for (int d = 0; d < dim; d++) {
            m[d] /= vectors.length;
        }
        return m;
    }

    private static float[] normalize(float[] v) {
        double norm = 0;
        for (float x : v) {
            norm += (double) x * x;
        }
        norm = Math.sqrt(norm);
        if (norm > 1e-12) {
            for (int i = 0; i < v.length; i++) {
                v[i] /= (float) norm;
            }
        }
        return v;
    }
}
