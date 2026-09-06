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

package io.statigate.grounding;

import io.statigate.core.Clause;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The production retriever. Combines one or two {@link ProvisionScorer}s (BM25 always; InLegalBERT
 * when available) with a strong clause-type prior:
 *
 * <pre>
 *   score = bm25Weight * minMax(bm25)
 *         + embWeight  * minMax(embedding)
 *         + tagBonus   * [provision is tagged with this clause type]
 * </pre>
 *
 * The clause-type tag is the most reliable signal (curated per provision), so it dominates; the
 * scorers rank within. Matches below {@code minScore} are dropped so a clause with no real statute
 * link returns nothing rather than noise.
 */
public final class RankingStatuteRetriever implements StatuteRetriever {

    private final List<StatuteProvision> provisions;
    private final ProvisionScorer bm25;
    private final ProvisionScorer embeddingOrNull;
    private final double bm25Weight;
    private final double embWeight;
    private final double tagBonus;
    private final double minScore;

    public RankingStatuteRetriever(StatuteCorpus corpus, ProvisionScorer bm25,
            ProvisionScorer embeddingOrNull) {
        this(corpus, bm25, embeddingOrNull,
                embeddingOrNull != null ? 0.45 : 0.75,
                embeddingOrNull != null ? 0.30 : 0.0,
                0.55, 0.30);
    }

    RankingStatuteRetriever(StatuteCorpus corpus, ProvisionScorer bm25, ProvisionScorer embeddingOrNull,
            double bm25Weight, double embWeight, double tagBonus, double minScore) {
        this.provisions = corpus.provisions();
        this.bm25 = bm25;
        this.embeddingOrNull = embeddingOrNull;
        this.bm25Weight = bm25Weight;
        this.embWeight = embWeight;
        this.tagBonus = tagBonus;
        this.minScore = minScore;
    }

    @Override
    public List<GroundingMatch> retrieve(String clauseText, String clauseType, int k) {
        if (provisions.isEmpty() || clauseText == null || clauseText.isBlank()) {
            return List.of();
        }
        double[] bm = minMax(bm25.scoreAll(clauseText));
        double[] emb = embeddingOrNull != null ? minMax(embeddingOrNull.scoreAll(clauseText))
                : new double[provisions.size()];
        boolean typed = clauseType != null && !Clause.UNCLASSIFIED.equals(clauseType);

        String method = embeddingOrNull != null ? "hybrid" : "bm25";
        List<GroundingMatch> matches = new ArrayList<>();
        for (int i = 0; i < provisions.size(); i++) {
            StatuteProvision p = provisions.get(i);
            double score = bm25Weight * bm[i] + embWeight * emb[i];
            if (typed && p.clauseTypes().contains(clauseType)) {
                score += tagBonus;
            }
            if (score >= minScore) {
                matches.add(new GroundingMatch(p, Math.min(1.0, score), method));
            }
        }
        matches.sort(Comparator.comparingDouble(GroundingMatch::score).reversed());
        return List.copyOf(matches.subList(0, Math.min(k, matches.size())));
    }

    private static double[] minMax(double[] raw) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double v : raw) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double range = max - min;
        double[] out = new double[raw.length];
        if (range <= 1e-9) {
            return out;
        }
        for (int i = 0; i < raw.length; i++) {
            out[i] = (raw[i] - min) / range;
        }
        return out;
    }
}
