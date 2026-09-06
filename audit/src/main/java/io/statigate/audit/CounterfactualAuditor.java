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

package io.statigate.audit;

import io.statigate.core.Clause;
import io.statigate.core.RiskFlag;
import io.statigate.nlp.ModelLocator;
import io.statigate.nlp.NlpRuntime;
import io.statigate.pipeline.AnalysisPipeline;
import io.statigate.pipeline.ClauseFinding;
import io.statigate.pipeline.PipelineResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Counterfactual fairness audit: analyse a base contract, then re-analyse variants in which only
 * party names, gendered pronouns and Indian region names are swapped. The set of risk-flag
 * categories and clause types must not change - if it does, the pipeline is reacting to identity
 * rather than substance.
 */
public final class CounterfactualAuditor {

    public record CaseResult(String name, Set<String> baseSignal, List<VariantResult> variants) {
        public boolean stable() {
            return variants.stream().allMatch(VariantResult::matches);
        }
    }

    public record VariantResult(String label, Set<String> signal, Set<String> added,
            Set<String> removed) {
        public boolean matches() {
            return added.isEmpty() && removed.isEmpty();
        }
    }

    private final AnalysisPipeline pipeline;

    public CounterfactualAuditor(AnalysisPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public static CounterfactualAuditor withDefaults() {
        NlpRuntime runtime = new NlpRuntime(new ModelLocator());
        return new CounterfactualAuditor(AnalysisPipeline.create(runtime));
    }

    public CaseResult audit(CounterfactualCase testCase, Path tmpDir) throws IOException {
        Set<String> baseSignal = signalOf(analyzeText(testCase.baseText(), tmpDir, testCase.name() + "-base"));
        List<VariantResult> variants = new java.util.ArrayList<>();

        for (var variant : testCase.substitutions().entrySet()) {
            String variantText = testCase.baseText().replace(variant.getKey(), variant.getValue());
            Set<String> vs = signalOf(analyzeText(variantText, tmpDir,
                    testCase.name() + "-" + variant.getValue()));
            Set<String> added = new TreeSet<>(vs);
            added.removeAll(baseSignal);
            Set<String> removed = new TreeSet<>(baseSignal);
            removed.removeAll(vs);
            variants.add(new VariantResult(variant.getKey() + " -> " + variant.getValue(),
                    vs, added, removed));
        }
        return new CaseResult(testCase.name(), baseSignal, List.copyOf(variants));
    }

    private PipelineResult analyzeText(String text, Path tmpDir, String name) throws IOException {
        Files.createDirectories(tmpDir);
        Path f = tmpDir.resolve(name.replaceAll("[^a-zA-Z0-9_-]", "_") + ".txt");
        Files.writeString(f, text);
        return pipeline.analyze(f);
    }

    /** The identity-invariant signal: clause types plus risk-flag categories. */
    private static Set<String> signalOf(PipelineResult r) {
        Set<String> signal = new LinkedHashSet<>();
        for (ClauseFinding f : r.clauses()) {
            if (!Clause.UNCLASSIFIED.equals(f.clause().type())) {
                signal.add("clause:" + f.clause().type());
            }
            for (RiskFlag risk : f.risks()) {
                signal.add("risk:" + risk.category());
            }
        }
        return signal;
    }

    public static String render(List<CaseResult> results) {
        StringBuilder sb = new StringBuilder();
        long stable = results.stream().filter(CaseResult::stable).count();
        sb.append(String.format("Counterfactual fairness audit: %d/%d cases stable%n%n",
                stable, results.size()));
        for (CaseResult r : results) {
            sb.append(String.format("  %-28s %s%n", r.name(), r.stable() ? "STABLE" : "CHANGED"));
            for (VariantResult v : r.variants()) {
                if (!v.matches()) {
                    sb.append(String.format("      %s  +%s  -%s%n", v.label(), v.added(), v.removed()));
                }
            }
        }
        return sb.toString();
    }

    public String backends() {
        return pipeline.backends().toString();
    }
}
