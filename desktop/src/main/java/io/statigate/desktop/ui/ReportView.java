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

package io.statigate.desktop.ui;

import io.statigate.core.Advice;
import io.statigate.core.Entity;
import io.statigate.core.RiskFlag;
import io.statigate.pipeline.ClauseFinding;
import io.statigate.pipeline.PipelineResult;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Assembles a full {@link PipelineResult} into the report dashboard: summary stats, document-level
 * findings, entities, and one {@link ClauseCard} per clause. Every subsection degrades to an
 * {@link EmptyState} rather than an empty header when the pipeline found nothing for it, which is a
 * legitimate, informative outcome (e.g. a clean contract with no risk flags) rather than a fault.
 */
public final class ReportView extends VBox {

    public ReportView(PipelineResult result) {
        setSpacing(20);
        setPadding(new Insets(28, 32, 40, 32));

        getChildren().add(summaryHeader(result));
        getChildren().add(statTiles(result));

        getChildren().add(new SectionCard("Document-level risk flags",
                "Things worth checking that aren't tied to one specific clause.",
                result.documentRisks().isEmpty()
                        ? new EmptyState("Nothing flagged at the document level",
                                "No document-wide gaps (like a missing liability cap) were detected.")
                        : riskList(result.documentRisks())));

        if (!result.documentAdvice().isEmpty()) {
            getChildren().add(new SectionCard("Document-level notes", null, adviceList(result.documentAdvice())));
        }

        getChildren().add(new SectionCard("Extracted entities",
                "Parties, dates, amounts and other key terms found by pattern matching.",
                result.entities().isEmpty()
                        ? new EmptyState("No entities found", "Nothing matched the known entity patterns.")
                        : entityFlow(result.entities())));

        getChildren().add(new SectionCard("Clauses", clauseSubtitle(result),
                result.clauses().isEmpty()
                        ? new EmptyState("No clauses identified",
                                "The document may be too short, or use a heading style Statigate doesn't recognize.")
                        : clauseList(result.clauses())));
    }

    private VBox summaryHeader(PipelineResult result) {
        String fileName = Path.of(result.document().sourcePath()).getFileName().toString();
        Label title = new Label(fileName);
        title.getStyleClass().add("section-card-title");

        Label meta = new Label(result.document().pageCount() + " page(s) · "
                + result.document().charCount() + " characters · "
                + result.document().sentenceCount() + " sentences");
        meta.getStyleClass().add("section-card-subtitle");

        Label backends = new Label(backendSummary(result.backends()));
        backends.getStyleClass().add("backend-note");
        backends.setWrapText(true);

        return new VBox(4, title, meta, backends);
    }

    private static String backendSummary(Map<String, String> backends) {
        if (backends.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        backends.forEach((stage, backend) -> {
            if (sb.length() > 0) {
                sb.append("  ·  ");
            }
            sb.append(stage).append(": ").append(backend);
        });
        return sb.toString();
    }

    private HBox statTiles(PipelineResult result) {
        int totalRisks = result.documentRisks().size()
                + result.clauses().stream().mapToInt(f -> f.risks().size()).sum();
        long totalMs = result.totalMillis();
        String elapsed = totalMs >= 1000
                ? String.format(Locale.ROOT, "%.1fs", totalMs / 1000.0)
                : totalMs + "ms";

        HBox row = new HBox(14,
                new StatTile(String.valueOf(result.clauses().size()), "Clauses"),
                new StatTile(String.valueOf(totalRisks), "Risk flags"),
                new StatTile(String.valueOf(result.entities().size()), "Entities"),
                new StatTile(elapsed, "Analysis time"));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox riskList(java.util.List<RiskFlag> risks) {
        VBox box = new VBox(14);
        for (RiskFlag risk : risks) {
            box.getChildren().add(new RiskFlagRow(risk));
        }
        return box;
    }

    private VBox adviceList(java.util.List<Advice> advice) {
        VBox box = new VBox(14);
        for (Advice a : advice) {
            box.getChildren().add(new AdviceRow(a));
        }
        return box;
    }

    private FlowPane entityFlow(java.util.List<Entity> entities) {
        FlowPane flow = new FlowPane(8, 8);
        for (Entity entity : entities) {
            flow.getChildren().add(new EntityChip(entity));
        }
        return flow;
    }

    private VBox clauseList(java.util.List<ClauseFinding> findings) {
        VBox box = new VBox(14);
        for (ClauseFinding finding : findings) {
            box.getChildren().add(new ClauseCard(finding));
        }
        return box;
    }

    private static String clauseSubtitle(PipelineResult result) {
        return result.clauses().size() + " clause(s) found, in reading order.";
    }
}
