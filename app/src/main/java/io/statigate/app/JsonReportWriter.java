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

package io.statigate.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.statigate.core.Advice;
import io.statigate.core.Citation;
import io.statigate.core.Entity;
import io.statigate.core.RiskFlag;
import io.statigate.pipeline.ClauseFinding;
import io.statigate.pipeline.PipelineResult;
import java.io.PrintStream;

/** Machine-readable rendering of a {@link PipelineResult}. Traceability fields are always present. */
final class JsonReportWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonReportWriter() {
    }

    static void write(PipelineResult r, PrintStream out) throws java.io.IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("disclaimer", "Advisory information only, not legal advice (Advocates Act, 1961).");

        ObjectNode doc = root.putObject("document");
        doc.put("source", r.document().sourcePath());
        doc.put("mediaType", r.document().mediaType());
        doc.put("pages", r.document().pageCount());
        doc.put("chars", r.document().charCount());
        doc.put("sentences", r.document().sentenceCount());

        ObjectNode backends = root.putObject("backends");
        r.backends().forEach(backends::put);
        ObjectNode timings = root.putObject("stageMillis");
        r.stageMillis().forEach(timings::put);

        ArrayNode entities = root.putArray("entities");
        for (Entity e : r.entities()) {
            ObjectNode n = entities.addObject();
            n.put("type", e.type());
            n.put("text", e.text());
            span(n.putObject("span"), e.span().start(), e.span().end(), e.span().page());
        }

        ArrayNode docRisks = root.putArray("documentRiskFlags");
        for (RiskFlag rf : r.documentRisks()) {
            ObjectNode n = docRisks.addObject();
            n.put("severity", rf.severity().name());
            n.put("category", rf.category());
            n.put("rationale", rf.rationale());
        }
        ArrayNode docAdvice = root.putArray("documentAdvice");
        for (Advice a : r.documentAdvice()) {
            ObjectNode n = docAdvice.addObject();
            n.put("headline", a.headline());
            n.put("body", a.body());
        }

        ArrayNode clauses = root.putArray("clauses");
        for (ClauseFinding f : r.clauses()) {
            ObjectNode c = clauses.addObject();
            c.put("id", f.clause().id());
            c.put("type", f.clause().type());
            c.put("classifierConfidence", f.classification().confidence());
            c.put("classifierMethod", f.classification().method());
            c.put("text", f.clause().text());
            span(c.putObject("span"), f.clause().span().start(), f.clause().span().end(),
                    f.clause().span().page());

            ArrayNode risks = c.putArray("riskFlags");
            for (RiskFlag rf : f.risks()) {
                ObjectNode n = risks.addObject();
                n.put("severity", rf.severity().name());
                n.put("category", rf.category());
                n.put("rationale", rf.rationale());
            }
            ArrayNode statutes = c.putArray("groundedIn");
            f.statutes().forEach(m -> {
                ObjectNode n = statutes.addObject();
                n.put("act", m.provision().act());
                n.put("provision", m.provision().provision());
                n.put("heading", m.provision().heading());
                n.put("summary", m.provision().text());
                n.put("score", m.score());
                n.put("method", m.method());
            });
            ArrayNode advice = c.putArray("advice");
            for (Advice a : f.advice()) {
                ObjectNode n = advice.addObject();
                n.put("headline", a.headline());
                n.put("body", a.body());
                ArrayNode cites = n.putArray("citations");
                for (Citation cit : a.citations()) {
                    ObjectNode cn = cites.addObject();
                    span(cn.putObject("clauseSpan"), cit.clauseSpan().start(),
                            cit.clauseSpan().end(), cit.clauseSpan().page());
                    cn.put("clauseText", cit.clauseText());
                    if (cit.hasStatute()) {
                        ObjectNode sn = cn.putObject("statute");
                        sn.put("act", cit.statute().act());
                        sn.put("provision", cit.statute().provision());
                        sn.put("summary", cit.statute().snippet());
                    }
                }
            }
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(out, root);
        out.println();
    }

    private static void span(ObjectNode n, int start, int end, int page) {
        n.put("start", start);
        n.put("end", end);
        n.put("page", page);
    }
}
