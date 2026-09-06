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
import io.statigate.core.Document;
import io.statigate.core.Entity;
import io.statigate.core.RiskFlag;
import io.statigate.ingestion.ClauseSegmenter;
import io.statigate.nlp.NlpRuntime;
import io.statigate.nlp.OnnxTextEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Milestone 2 stage: segment a {@link Document} into clauses, classify each clause type, extract
 * entities, and detect risks.
 *
 * <p>Runs with or without the InLegalBERT ONNX model. With it, clause classification is an
 * embedding + keyword ensemble; without it, the keyword classifier alone. Everything else
 * (entities, risk rules) is model-independent, so the pipeline degrades gracefully.
 */
public final class ExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);

    private final ClauseSegmenter segmenter = new ClauseSegmenter();
    private final EntityExtractor entityExtractor = new EntityExtractor();
    private final RiskScorer riskScorer = new RiskScorer();
    private final ClauseClassifier classifier;
    private final String backend;

    private ExtractionService(ClauseClassifier classifier, String backend) {
        this.classifier = classifier;
        this.backend = backend;
    }

    /**
     * Builds the service, using the shared InLegalBERT encoder from {@code runtime} if available.
     * The runtime owns the encoder lifecycle; this service does not close it.
     */
    public static ExtractionService create(NlpRuntime runtime) {
        KeywordClauseClassifier keyword = new KeywordClauseClassifier();
        Optional<OnnxTextEncoder> encoder = runtime.encoder();
        if (encoder.isEmpty()) {
            log.warn("Clause classification will use keyword rules only "
                    + "(run scripts/export_inlegalbert_onnx.py to enable the transformer).");
            return new ExtractionService(keyword, "keyword-only");
        }
        EmbeddingClauseClassifier embedding = new EmbeddingClauseClassifier(encoder.get());
        ClauseClassifier ensemble = new EnsembleClauseClassifier(embedding, keyword);
        log.info("Extraction backend: InLegalBERT ONNX encoder + keyword ensemble");
        return new ExtractionService(ensemble, "inlegalbert+keyword");
    }

    /** Keyword-only service, for tests and model-free operation. */
    public static ExtractionService keywordOnly() {
        return new ExtractionService(new KeywordClauseClassifier(), "keyword-only");
    }

    public String backend() {
        return backend;
    }

    public ExtractionResult analyze(Document document) {
        List<Clause> raw = segmenter.segment(document);
        List<Clause> classified = new ArrayList<>(raw.size());
        Map<String, ClauseClassification> details = new LinkedHashMap<>();

        for (Clause clause : raw) {
            ClauseClassification c = classifier.classify(clause.text());
            details.put(clause.id(), c);
            String type = c.isClassified() ? c.type()
                    : ("PREAMBLE".equals(clause.type()) ? clause.type() : Clause.UNCLASSIFIED);
            classified.add(new Clause(clause.id(), type, clause.text(), clause.span()));
        }

        List<Entity> entities = entityExtractor.extract(document);
        RiskScorer.Assessment risk = riskScorer.score(classified, document.cleanText());

        log.info("Extraction: {} clauses ({} classified), {} entities, {} clause + {} document risk flags",
                classified.size(),
                classified.stream().filter(c -> !Clause.UNCLASSIFIED.equals(c.type())
                        && !"PREAMBLE".equals(c.type())).count(),
                entities.size(), risk.clauseFlags().size(), risk.documentFlags().size());
        return new ExtractionResult(classified, entities, risk.clauseFlags(), risk.documentFlags(),
                details, backend);
    }
}
