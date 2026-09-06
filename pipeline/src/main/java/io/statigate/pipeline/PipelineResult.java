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

import io.statigate.core.Advice;
import io.statigate.core.AnalysisReport;
import io.statigate.core.Document;
import io.statigate.core.Entity;
import io.statigate.core.RiskFlag;
import java.util.List;
import java.util.Map;

/**
 * The full output of one pipeline run: the document, per-clause findings, whole-document findings,
 * entities, stage timings, and which backend each stage used.
 */
public record PipelineResult(
        Document document,
        List<ClauseFinding> clauses,
        List<RiskFlag> documentRisks,
        List<Advice> documentAdvice,
        List<Entity> entities,
        Map<String, Long> stageMillis,
        Map<String, String> backends) {

    public PipelineResult {
        clauses = List.copyOf(clauses);
        documentRisks = List.copyOf(documentRisks == null ? List.of() : documentRisks);
        documentAdvice = List.copyOf(documentAdvice == null ? List.of() : documentAdvice);
        entities = List.copyOf(entities);
        stageMillis = Map.copyOf(stageMillis);
        backends = Map.copyOf(backends);
    }

    /** Flattens to the shared {@link AnalysisReport} domain object. */
    public AnalysisReport toReport() {
        return new AnalysisReport(
                document,
                entities,
                clauses.stream().map(ClauseFinding::clause).toList(),
                java.util.stream.Stream.concat(
                        clauses.stream().flatMap(f -> f.risks().stream()),
                        documentRisks.stream()).toList(),
                java.util.stream.Stream.concat(
                        clauses.stream().flatMap(f -> f.advice().stream()),
                        documentAdvice.stream()).toList());
    }

    public long totalMillis() {
        return stageMillis.getOrDefault("total", 0L);
    }
}
