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

import io.statigate.nlp.NlpRuntime;
import io.statigate.nlp.OnnxTextEncoder;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Milestone 3 stage: given a clause, retrieve the Indian statute provisions it is grounded in.
 *
 * <p>BM25 always; hybrid BM25 + InLegalBERT when the shared encoder is available. No database, no
 * network - the corpus is a bundled JSON resource.
 */
public final class GroundingService {

    private static final Logger log = LoggerFactory.getLogger(GroundingService.class);

    private final StatuteCorpus corpus;
    private final StatuteRetriever retriever;
    private final String backend;

    private GroundingService(StatuteCorpus corpus, StatuteRetriever retriever, String backend) {
        this.corpus = corpus;
        this.retriever = retriever;
        this.backend = backend;
    }

    public static GroundingService create(NlpRuntime runtime) {
        return create(runtime, StatuteCorpus.loadDefault());
    }

    public static GroundingService create(NlpRuntime runtime, StatuteCorpus corpus) {
        var bm25 = new Bm25StatuteRetriever(corpus);
        Optional<OnnxTextEncoder> encoder = runtime.encoder();
        EmbeddingStatuteRetriever embedding = encoder
                .map(e -> new EmbeddingStatuteRetriever(corpus, e))
                .orElse(null);
        var retriever = new RankingStatuteRetriever(corpus, bm25, embedding);
        String backend = embedding != null ? "hybrid" : "bm25";
        log.info("Grounding backend: {} over {} provisions", backend, corpus.size());
        return new GroundingService(corpus, retriever, backend);
    }

    public String backend() {
        return backend;
    }

    public StatuteCorpus corpus() {
        return corpus;
    }

    /** Provisions most relevant to a clause, most relevant first. */
    public List<GroundingMatch> ground(String clauseText, String clauseType, int k) {
        return retriever.retrieve(clauseText, clauseType, k);
    }
}
