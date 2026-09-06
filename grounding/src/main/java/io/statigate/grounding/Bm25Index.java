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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A small, self-contained Okapi BM25 index over a fixed set of documents. No external search
 * library - a few hundred provisions do not need Lucene, and keeping it in-process preserves the
 * single-JVM, zero-dependency-at-runtime property.
 */
public final class Bm25Index {

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{Nd}]+");
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private final List<String[]> docTokens = new ArrayList<>();
    private final List<Map<String, Integer>> termFreqs = new ArrayList<>();
    private final Map<String, Integer> docFreq = new HashMap<>();
    private double avgDocLen;

    public Bm25Index(List<String> documents) {
        long totalLen = 0;
        for (String doc : documents) {
            String[] toks = tokenize(doc);
            docTokens.add(toks);
            totalLen += toks.length;
            Map<String, Integer> tf = new HashMap<>();
            for (String t : toks) {
                tf.merge(t, 1, Integer::sum);
            }
            termFreqs.add(tf);
            for (String term : tf.keySet()) {
                docFreq.merge(term, 1, Integer::sum);
            }
        }
        avgDocLen = documents.isEmpty() ? 0 : (double) totalLen / documents.size();
    }

    public int size() {
        return docTokens.size();
    }

    /** BM25 scores for every document against the query, indexed by document position. */
    public double[] scores(String query) {
        int n = docTokens.size();
        double[] scores = new double[n];
        String[] queryTerms = tokenize(query);
        for (String term : queryTerms) {
            Integer df = docFreq.get(term);
            if (df == null) {
                continue;
            }
            double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
            for (int d = 0; d < n; d++) {
                Integer tf = termFreqs.get(d).get(term);
                if (tf == null) {
                    continue;
                }
                double len = docTokens.get(d).length;
                double denom = tf + K1 * (1 - B + B * len / Math.max(avgDocLen, 1e-9));
                scores[d] += idf * (tf * (K1 + 1)) / denom;
            }
        }
        return scores;
    }

    static String[] tokenize(String text) {
        if (text == null || text.isBlank()) {
            return new String[0];
        }
        var m = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        List<String> out = new ArrayList<>();
        while (m.find()) {
            String tok = m.group();
            if (tok.length() > 1 && !STOPWORDS.contains(tok)) {
                out.add(tok);
            }
        }
        return out.toArray(new String[0]);
    }

    private static final java.util.Set<String> STOPWORDS = java.util.Set.of(
            "the", "a", "an", "of", "to", "in", "or", "and", "any", "is", "are", "be", "by", "for",
            "on", "as", "at", "it", "its", "that", "this", "which", "with", "such", "shall", "may",
            "not", "no", "if", "from", "under", "other", "than", "party", "parties", "agreement");
}
