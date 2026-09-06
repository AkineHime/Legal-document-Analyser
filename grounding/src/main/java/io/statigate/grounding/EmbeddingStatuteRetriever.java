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

import io.statigate.nlp.OnnxTextEncoder;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** InLegalBERT cosine-similarity provision scorer. Provision vectors are computed once. */
final class EmbeddingStatuteRetriever implements ProvisionScorer {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingStatuteRetriever.class);

    private final OnnxTextEncoder encoder;
    private final float[][] provisionVectors;

    EmbeddingStatuteRetriever(StatuteCorpus corpus, OnnxTextEncoder encoder) {
        this.encoder = encoder;
        this.provisionVectors = encoder.embedAll(
                corpus.provisions().stream().map(StatuteProvision::searchText).toList());
        log.info("Embedded {} statute provisions with InLegalBERT", provisionVectors.length);
    }

    @Override
    public double[] scoreAll(String clauseText) {
        float[] q = encoder.embed(clauseText);
        double[] scores = new double[provisionVectors.length];
        for (int i = 0; i < provisionVectors.length; i++) {
            scores[i] = OnnxTextEncoder.cosine(q, provisionVectors[i]);
        }
        return scores;
    }

    @Override
    public String name() {
        return "inlegalbert";
    }
}
