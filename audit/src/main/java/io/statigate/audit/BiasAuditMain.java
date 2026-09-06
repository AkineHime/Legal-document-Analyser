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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runs the counterfactual fairness audit over the bundled cases and prints the result. */
public final class BiasAuditMain {

    public static void main(String[] args) throws Exception {
        List<CounterfactualCase> cases = loadCases();
        Path tmp = Files.createTempDirectory("statigate-audit");
        CounterfactualAuditor auditor = CounterfactualAuditor.withDefaults();
        System.out.println("backends: " + auditor.backends());

        List<CounterfactualAuditor.CaseResult> results = new ArrayList<>();
        for (CounterfactualCase c : cases) {
            results.add(auditor.audit(c, tmp));
        }
        String rendered = CounterfactualAuditor.render(results);
        System.out.println();
        System.out.println(rendered);

        if (args.length > 0) {
            Files.writeString(Path.of(args[0]), rendered);
            System.out.println("wrote " + args[0]);
        }
        boolean allStable = results.stream().allMatch(CounterfactualAuditor.CaseResult::stable);
        System.exit(allStable ? 0 : 1);
    }

    static List<CounterfactualCase> loadCases() throws Exception {
        try (InputStream in = BiasAuditMain.class.getResourceAsStream("/counterfactual_cases.json")) {
            JsonNode root = new ObjectMapper().readTree(in);
            List<CounterfactualCase> out = new ArrayList<>();
            for (JsonNode c : root.path("cases")) {
                Map<String, String> subs = new LinkedHashMap<>();
                c.path("substitutions").fields().forEachRemaining(e -> subs.put(e.getKey(), e.getValue().asText()));
                out.add(new CounterfactualCase(c.path("name").asText(), c.path("base_text").asText(), subs));
            }
            return out;
        }
    }

    private BiasAuditMain() {
    }
}
