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

package io.statigate.core;

import java.util.List;

/**
 * The end-to-end output of the pipeline for one document. Stages that have not run yet contribute
 * empty lists, so a partial pipeline (e.g. ingestion only, during early milestones) still produces
 * a valid report.
 *
 * @param document  the ingested document
 * @param entities  extracted named entities
 * @param clauses   identified clauses
 * @param riskFlags clauses worth attention
 * @param advice    plain-language advisory points, each grounded
 */
public record AnalysisReport(
        Document document,
        List<Entity> entities,
        List<Clause> clauses,
        List<RiskFlag> riskFlags,
        List<Advice> advice) {

    public AnalysisReport {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        entities = List.copyOf(entities == null ? List.of() : entities);
        clauses = List.copyOf(clauses == null ? List.of() : clauses);
        riskFlags = List.copyOf(riskFlags == null ? List.of() : riskFlags);
        advice = List.copyOf(advice == null ? List.of() : advice);
    }

    /** A report with only ingestion done. */
    public static AnalysisReport ingestedOnly(Document document) {
        return new AnalysisReport(document, List.of(), List.of(), List.of(), List.of());
    }
}
