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

import io.statigate.core.Clause;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Combines the InLegalBERT centroid classifier with the keyword prior. The embedding model captures
 * semantics the keyword list misses; the keyword prior anchors the many clause types whose language
 * is highly conventional. Score = {@code embWeight * cosine + kwWeight * normalizedKeywordScore}.
 */
public final class EnsembleClauseClassifier implements ClauseClassifier {

    private final EmbeddingClauseClassifier embedding;
    private final KeywordClauseClassifier keyword;
    private final double embWeight;
    private final double kwWeight;
    private final double acceptScore;

    public EnsembleClauseClassifier(EmbeddingClauseClassifier embedding, KeywordClauseClassifier keyword) {
        this(embedding, keyword, 0.6, 0.4, 0.42);
    }

    public EnsembleClauseClassifier(EmbeddingClauseClassifier embedding, KeywordClauseClassifier keyword,
            double embWeight, double kwWeight, double acceptScore) {
        this.embedding = embedding;
        this.keyword = keyword;
        this.embWeight = embWeight;
        this.kwWeight = kwWeight;
        this.acceptScore = acceptScore;
    }

    @Override
    public ClauseClassification classify(String clauseText) {
        Map<String, Double> sims = embedding.similarities(clauseText);
        Map<String, Double> kw = keyword.scores(clauseText);
        double kwMax = kw.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);

        Map<String, Double> combined = new LinkedHashMap<>();
        for (var e : sims.entrySet()) {
            double kwNorm = kwMax > 0 ? kw.getOrDefault(e.getKey(), 0.0) / kwMax : 0.0;
            combined.put(e.getKey(), embWeight * e.getValue() + kwWeight * kwNorm);
        }

        String best = null;
        String second = null;
        double bestScore = -1;
        double secondScore = -1;
        for (var e : combined.entrySet()) {
            if (e.getValue() > bestScore) {
                second = best;
                secondScore = bestScore;
                best = e.getKey();
                bestScore = e.getValue();
            } else if (e.getValue() > secondScore) {
                second = e.getKey();
                secondScore = e.getValue();
            }
        }
        if (best == null || bestScore < acceptScore) {
            return new ClauseClassification(Clause.UNCLASSIFIED, Math.max(0, bestScore), best, "ensemble");
        }
        return new ClauseClassification(best, bestScore, second, "ensemble");
    }
}
