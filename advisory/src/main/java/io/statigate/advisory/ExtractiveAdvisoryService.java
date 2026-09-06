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

package io.statigate.advisory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.statigate.core.Advice;
import io.statigate.core.Citation;
import io.statigate.core.Clause;
import io.statigate.core.RiskFlag;
import io.statigate.core.StatuteRef;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministic advisory generation from templates. It cannot hallucinate: every sentence is either
 * a fixed explanation of the clause type, a rule-derived risk rationale, or a verbatim statute
 * summary from the local corpus - and every note is cited to the clause span.
 *
 * <p>This is the default backend. The LLM backend only rephrases what this produces.
 */
public final class ExtractiveAdvisoryService implements AdvisoryService {

    private final Map<String, String> explainers;

    public ExtractiveAdvisoryService() {
        this.explainers = loadExplainers();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> loadExplainers() {
        try (InputStream in = ExtractiveAdvisoryService.class.getResourceAsStream(
                "/clause_explainers.json")) {
            if (in == null) {
                throw new IllegalStateException("clause_explainers.json missing");
            }
            JsonNode root = new ObjectMapper().readTree(in);
            Map<String, String> map = new java.util.HashMap<>();
            root.fields().forEachRemaining(e -> {
                if (!e.getKey().startsWith("_")) {
                    map.put(e.getKey(), e.getValue().asText());
                }
            });
            return Map.copyOf(map);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read clause_explainers.json", e);
        }
    }

    @Override
    public String backend() {
        return "extractive";
    }

    @Override
    public List<Advice> adviseClause(ClauseAdvisoryInput input) {
        Clause clause = input.clause();
        List<Advice> out = new ArrayList<>();
        Citation clauseCite = Citation.documentOnly(clause.span(), clause.text());

        String explainer = explainers.get(clause.type());
        if (explainer != null) {
            out.add(new Advice(
                    "What this clause does: " + humanize(clause.type()),
                    explainer,
                    List.of(clauseCite)));
        }

        for (RiskFlag risk : input.risks()) {
            StringBuilder body = new StringBuilder(risk.rationale());
            List<Citation> cites = new ArrayList<>();
            cites.add(risk.citation());

            StatuteRef statute = pickStatute(input.statutes(), risk);
            if (statute != null) {
                body.append(" Related Indian law - ").append(statute.act()).append(", ")
                        .append(statute.provision()).append(": ").append(firstSentence(statute.snippet()));
                cites.add(new Citation(clause.span(), clause.text(), statute));
            }
            out.add(new Advice(
                    severityPrefix(risk) + humanize(risk.category()),
                    body.toString(),
                    cites));
        }
        return List.copyOf(out);
    }

    private static StatuteRef pickStatute(List<StatuteRef> statutes, RiskFlag risk) {
        if (statutes.isEmpty()) {
            return null;
        }
        return statutes.get(0);
    }

    private static String severityPrefix(RiskFlag risk) {
        return switch (risk.severity()) {
            case HIGH -> "High-priority point: ";
            case MEDIUM -> "Worth checking: ";
            case LOW -> "Minor note: ";
        };
    }

    private static String humanize(String token) {
        String s = token.toLowerCase().replace('_', ' ').replace('-', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String firstSentence(String text) {
        String t = text.strip();
        int dot = t.indexOf(". ");
        if (dot > 30) {
            return t.substring(0, dot + 1);
        }
        return t.length() <= 200 ? t : t.substring(0, 197) + "...";
    }
}
