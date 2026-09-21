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
import io.statigate.core.RiskFlag;
import io.statigate.desktop.AppConfig;
import io.statigate.desktop.format.Labels;
import io.statigate.desktop.format.TextSanitizer;
import io.statigate.grounding.GroundingMatch;
import io.statigate.pipeline.ClauseFinding;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Everything the pipeline learned about one clause, in one card: its type and confidence, the
 * clause text itself (collapsed by default), any risk flags, the statute provisions it grounded
 * in, and the advisory notes derived from it.
 *
 * <p>A UI-only "worth a second look" confidence threshold (see {@link ConfidenceBar}) is used here
 * purely to decide the meter's color, not whether anything is shown - every clause the pipeline
 * found is shown, confident or not, exactly as {@link io.statigate.desktop.state.AppState}
 * receives it.
 */
public final class ClauseCard extends VBox {

    private static final double LOW_CONFIDENCE_HINT = 0.6;
    private static final int FULL_TEXT_CAP = 4000; // guards the scene graph against a pathological clause

    public ClauseCard(ClauseFinding finding) {
        getStyleClass().add("clause-card");
        // A colored left edge lets a reader scanning down a ten-clause report spot the two or
        // three that need attention without reading every pill - the same severity tokens the
        // badges already use, just as a stripe on the card itself.
        finding.risks().stream()
                .map(RiskFlag::severity)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .ifPresent(highest -> getStyleClass().add(highest.name().toLowerCase(Locale.ROOT)));
        setSpacing(10);

        getChildren().add(header(finding));
        getChildren().add(clauseText(finding));

        if (!finding.risks().isEmpty()) {
            getChildren().add(sectionLabel("Worth a look"));
            for (RiskFlag risk : finding.risks()) {
                getChildren().add(new RiskFlagRow(risk));
            }
        }

        if (!finding.statutes().isEmpty()) {
            getChildren().add(sectionLabel("Related Indian statute"));
            FlowPane chips = new FlowPane(6, 6);
            for (GroundingMatch match : finding.statutes()) {
                chips.getChildren().add(new StatuteChip(StatuteChip.from(match)));
            }
            getChildren().add(chips);
        }

        if (!finding.advice().isEmpty()) {
            getChildren().add(sectionLabel("Advisory notes"));
            for (Advice advice : finding.advice()) {
                getChildren().add(new AdviceRow(advice));
            }
        }
    }

    private HBox header(ClauseFinding finding) {
        Label type = new Label(Labels.clauseType(finding.clause().type()));
        type.getStyleClass().add("clause-card-type");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ConfidenceBar confidenceBar = new ConfidenceBar(finding.classification().confidence(), LOW_CONFIDENCE_HINT);

        HBox row = new HBox(10, type, spacer, confidenceBar);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox clauseText(ClauseFinding finding) {
        String full = TextSanitizer.truncate(finding.clause().text(), FULL_TEXT_CAP);
        String preview = TextSanitizer.truncate(full, AppConfig.SNIPPET_PREVIEW_CHARS);

        Label textLabel = new Label(preview);
        textLabel.getStyleClass().add("clause-card-text");
        textLabel.setWrapText(true);

        VBox box = new VBox(6, textLabel);

        if (!preview.equals(full)) {
            Button toggle = new Button("Show full clause");
            toggle.getStyleClass().add("clause-card-toggle");
            boolean[] expanded = { false };
            toggle.setOnAction(e -> {
                expanded[0] = !expanded[0];
                textLabel.setText(expanded[0] ? full : preview);
                toggle.setText(expanded[0] ? "Show less" : "Show full clause");
            });
            box.getChildren().add(toggle);
        }
        return box;
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text.toUpperCase(java.util.Locale.ROOT));
        label.getStyleClass().add("stat-tile-label");
        return label;
    }
}
