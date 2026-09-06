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

import io.statigate.core.Advice;
import io.statigate.core.Clause;
import io.statigate.core.Entity;
import io.statigate.core.RiskFlag;
import io.statigate.grounding.GroundingMatch;
import io.statigate.pipeline.ClauseFinding;
import io.statigate.pipeline.PipelineResult;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Human-readable rendering of a {@link PipelineResult}. */
final class ReportPrinter {

    private static final int WIDTH = 78;

    private ReportPrinter() {
    }

    static void print(PipelineResult r, PrintStream out) {
        var doc = r.document();
        rule(out, '=');
        out.println("  STATIGATE  -  legal document analysis  -  advisory only, not legal advice");
        rule(out, '=');
        out.printf("  source        : %s%n", doc.sourcePath());
        out.printf("  media / pages : %s / %d%n", doc.mediaType(), doc.pageCount());
        out.printf("  clean chars   : %d   sentences: %d   clauses: %d%n",
                doc.charCount(), doc.sentenceCount(), r.clauses().size());
        out.printf("  backends      : extraction=%s  grounding=%s  advisory=%s%n",
                r.backends().get("extraction"), r.backends().get("grounding"),
                r.backends().get("advisory"));
        out.printf("  stage timings : %s%n", timings(r.stageMillis()));
        out.println();

        printEntities(r.entities(), out);
        printDocumentRisks(r, out);

        long risky = r.clauses().stream().filter(f -> !f.risks().isEmpty()).count();
        out.printf("CLAUSES  (%d classified, %d with risk flags)%n",
                r.clauses().stream().filter(f -> isTyped(f.clause())).count(), risky);
        rule(out, '-');
        for (ClauseFinding f : r.clauses()) {
            printClause(f, out);
        }
    }

    private static void printEntities(List<Entity> entities, PrintStream out) {
        if (entities.isEmpty()) {
            return;
        }
        out.println("KEY TERMS");
        rule(out, '-');
        Map<String, List<Entity>> byType = entities.stream()
                .collect(Collectors.groupingBy(Entity::type));
        byType.forEach((type, list) -> {
            String values = list.stream().map(Entity::text).distinct().limit(6)
                    .collect(Collectors.joining("; "));
            out.printf("  %-16s %s%n", type, values);
        });
        out.println();
    }

    private static void printDocumentRisks(PipelineResult r, PrintStream out) {
        if (r.documentRisks().isEmpty()) {
            return;
        }
        out.println("WHOLE-DOCUMENT NOTES");
        rule(out, '-');
        for (RiskFlag risk : r.documentRisks()) {
            out.printf("  (!) %-7s %s%n", risk.severity(), risk.category());
        }
        for (Advice a : r.documentAdvice()) {
            out.printf("    - %s%n", a.headline());
            out.printf("      %s%n", oneLine(a.body(), 500));
        }
        out.println();
    }

    private static void printClause(ClauseFinding f, PrintStream out) {
        Clause c = f.clause();
        String conf = f.classification().confidence() > 0
                ? String.format(" (%.2f, %s)", f.classification().confidence(), f.classification().method())
                : "";
        out.printf("%n[%s]  %s%s  p.%d%n", c.id(), c.type(), conf, c.span().page());
        out.printf("    %s%n", oneLine(c.text(), 300));

        for (RiskFlag risk : f.risks()) {
            out.printf("    (!) %-7s %s%n", risk.severity(), risk.category());
        }
        if (!f.statutes().isEmpty()) {
            String refs = f.statutes().stream()
                    .map(m -> m.provision().act() + " " + m.provision().provision()
                            + String.format(" [%.2f]", m.score()))
                    .collect(Collectors.joining("; "));
            out.printf("    grounded in: %s%n", refs);
        }
        for (Advice a : f.advice()) {
            out.printf("    - %s%n", a.headline());
            out.printf("      %s%n", oneLine(a.body(), 500));
        }
    }

    private static boolean isTyped(Clause c) {
        return !Clause.UNCLASSIFIED.equals(c.type()) && !"PREAMBLE".equals(c.type());
    }

    private static String timings(Map<String, Long> m) {
        return m.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue() + "ms")
                .collect(Collectors.joining("  "));
    }

    private static String oneLine(String s, int max) {
        String t = s.replaceAll("\\s+", " ").strip();
        return t.length() <= max ? t : t.substring(0, max - 3) + "...";
    }

    private static void rule(PrintStream out, char c) {
        out.println(String.valueOf(c).repeat(WIDTH));
    }
}
