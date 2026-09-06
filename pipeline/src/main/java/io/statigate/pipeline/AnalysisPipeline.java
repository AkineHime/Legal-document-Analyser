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

package io.statigate.pipeline;

import io.statigate.advisory.AdvisoryService;
import io.statigate.advisory.ClauseAdvisoryInput;
import io.statigate.advisory.ExtractiveAdvisoryService;
import io.statigate.advisory.LlmAdvisoryService;
import io.statigate.advisory.LlmClient;
import io.statigate.core.Advice;
import io.statigate.core.Clause;
import io.statigate.core.Document;
import io.statigate.core.RiskFlag;
import io.statigate.core.StatuteRef;
import io.statigate.extraction.ClauseClassification;
import io.statigate.extraction.ExtractionResult;
import io.statigate.extraction.ExtractionService;
import io.statigate.grounding.GroundingMatch;
import io.statigate.grounding.GroundingService;
import io.statigate.ingestion.IngestionPipeline;
import io.statigate.nlp.NlpRuntime;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * End-to-end contract analysis: ingestion, extraction, statute grounding, advisory generation.
 * A single {@link NlpRuntime} (and therefore a single InLegalBERT encoder) is shared across stages.
 */
public final class AnalysisPipeline {

    private static final Logger log = LoggerFactory.getLogger(AnalysisPipeline.class);
    private static final int STATUTES_PER_CLAUSE = 3;

    private final IngestionPipeline ingestion = new IngestionPipeline();
    private final ExtractionService extraction;
    private final GroundingService grounding;
    private final AdvisoryService advisory;
    private final Map<String, String> backends;

    private AnalysisPipeline(ExtractionService extraction, GroundingService grounding,
            AdvisoryService advisory) {
        this.extraction = extraction;
        this.grounding = grounding;
        this.advisory = advisory;
        this.backends = Map.of(
                "extraction", extraction.backend(),
                "grounding", grounding.backend(),
                "advisory", advisory.backend());
    }

    /** Default: extractive (deterministic) advisory. */
    public static AnalysisPipeline create(NlpRuntime runtime) {
        return new AnalysisPipeline(
                ExtractionService.create(runtime),
                GroundingService.create(runtime),
                new ExtractiveAdvisoryService());
    }

    /** Advisory rephrased by a local LLM, with the extractive engine as the guardrail/fallback. */
    public static AnalysisPipeline withLlm(NlpRuntime runtime, LlmClient llm) {
        return new AnalysisPipeline(
                ExtractionService.create(runtime),
                GroundingService.create(runtime),
                new LlmAdvisoryService(llm));
    }

    public Map<String, String> backends() {
        return backends;
    }

    public PipelineResult analyze(Path file) throws IOException {
        Map<String, Long> timings = new LinkedHashMap<>();
        long tStart = System.nanoTime();

        long t = System.nanoTime();
        Document document = ingestion.ingest(file);
        timings.put("ingestion", ms(t));

        t = System.nanoTime();
        ExtractionResult extracted = extraction.analyze(document);
        timings.put("extraction", ms(t));

        t = System.nanoTime();
        List<ClauseFinding> findings = new ArrayList<>();
        for (Clause clause : extracted.clauses()) {
            ClauseClassification cc = extracted.classifications().getOrDefault(clause.id(),
                    new ClauseClassification(clause.type(), 0, null, "n/a"));
            List<RiskFlag> risks = risksFor(clause, extracted.riskFlags());
            List<GroundingMatch> statutes = clause.text().isBlank()
                    ? List.of()
                    : grounding.ground(clause.text(), clause.type(), STATUTES_PER_CLAUSE);
            findings.add(new ClauseFinding(clause, cc, risks, statutes, List.of()));
        }
        timings.put("grounding", ms(t));

        t = System.nanoTime();
        List<ClauseFinding> withAdvice = new ArrayList<>(findings.size());
        for (ClauseFinding f : findings) {
            List<StatuteRef> refs = f.statutes().stream().map(GroundingMatch::toRef).toList();
            List<Advice> advice = advisory.adviseClause(
                    new ClauseAdvisoryInput(f.clause(), f.risks(), refs));
            withAdvice.add(new ClauseFinding(f.clause(), f.classification(), f.risks(),
                    f.statutes(), advice));
        }
        List<RiskFlag> docRisks = extracted.documentRiskFlags();
        List<Advice> docAdvice = List.of();
        if (!docRisks.isEmpty() && !extracted.clauses().isEmpty()) {
            docAdvice = advisory.adviseClause(new ClauseAdvisoryInput(
                    extracted.clauses().get(0), docRisks, List.of())).stream()
                    .filter(a -> !a.headline().startsWith("What this clause does"))
                    .toList();
        }
        timings.put("advisory", ms(t));
        timings.put("total", ms(tStart));

        log.info("Analysis complete in {} ms (ingest {}, extract {}, ground {}, advise {})",
                timings.get("total"), timings.get("ingestion"), timings.get("extraction"),
                timings.get("grounding"), timings.get("advisory"));

        return new PipelineResult(document, withAdvice, docRisks, docAdvice,
                extracted.entities(), timings, backends);
    }

    private static List<RiskFlag> risksFor(Clause clause, List<RiskFlag> all) {
        List<RiskFlag> out = new ArrayList<>();
        int cs = clause.span().start();
        int ce = clause.span().end();
        for (RiskFlag f : all) {
            int fs = f.citation().clauseSpan().start();
            if (fs >= cs && fs < ce) {
                out.add(f);
            }
        }
        return out;
    }

    private static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
