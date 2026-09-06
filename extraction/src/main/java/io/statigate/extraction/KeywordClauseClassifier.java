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

import com.fasterxml.jackson.databind.JsonNode;
import io.statigate.core.Clause;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Explainable clause classification from weighted, case-insensitive textual cues
 * ({@code /clause_keywords.json}). Works with no model present, and serves as a prior for
 * {@link EnsembleClauseClassifier}.
 */
public final class KeywordClauseClassifier implements ClauseClassifier {

    private record Cue(Pattern pattern, double weight) {
    }

    private final Map<String, List<Cue>> cues = new LinkedHashMap<>();
    private final double minScore;

    public KeywordClauseClassifier() {
        this(1.5);
    }

    public KeywordClauseClassifier(double minScore) {
        this.minScore = minScore;
        JsonNode root = JsonResources.load("/clause_keywords.json");
        root.fields().forEachRemaining(e -> {
            if (e.getKey().startsWith("_") || !e.getValue().isArray()) {
                return;
            }
            List<Cue> list = new java.util.ArrayList<>();
            for (JsonNode pair : e.getValue()) {
                String needle = pair.get(0).asText();
                double weight = pair.get(1).asDouble(1.0);
                list.add(new Cue(Pattern.compile(needle, Pattern.CASE_INSENSITIVE), weight));
            }
            cues.put(e.getKey(), List.copyOf(list));
        });
    }

    Map<String, Double> scores(String clauseText) {
        String text = clauseText == null ? "" : clauseText;
        Map<String, Double> out = new LinkedHashMap<>();
        cues.forEach((type, list) -> {
            double score = 0;
            for (Cue cue : list) {
                if (cue.pattern().matcher(text).find()) {
                    score += cue.weight();
                }
            }
            if (score > 0) {
                out.put(type, score);
            }
        });
        return out;
    }

    @Override
    public ClauseClassification classify(String clauseText) {
        Map<String, Double> scores = scores(clauseText);
        String best = null;
        String second = null;
        double bestScore = 0;
        double secondScore = 0;
        for (var e : scores.entrySet()) {
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
        if (best == null || bestScore < minScore) {
            return new ClauseClassification(Clause.UNCLASSIFIED, 0, best, "keyword");
        }
        double confidence = Math.min(1.0, bestScore / 6.0);
        return new ClauseClassification(best, confidence, second, "keyword");
    }
}
