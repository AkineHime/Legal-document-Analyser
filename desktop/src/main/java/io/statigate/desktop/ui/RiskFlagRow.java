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

import io.statigate.core.RiskFlag;
import io.statigate.desktop.format.Labels;
import io.statigate.desktop.format.TextSanitizer;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * One risk flag: a severity badge, its category, and the plain-language reason. Shared between
 * {@link ClauseCard} (clause-level flags) and the document-level flags section of the report, so
 * the two never drift apart visually.
 */
public final class RiskFlagRow extends HBox {

    private static final int MAX_RATIONALE_CHARS = 400;

    public RiskFlagRow(RiskFlag risk) {
        setSpacing(10);
        setAlignment(Pos.TOP_LEFT);

        SeverityBadge badge = new SeverityBadge(risk.severity());

        Label category = new Label(Labels.riskCategory(risk.category()));
        category.getStyleClass().add("clause-card-type");

        Label rationale = new Label(TextSanitizer.truncate(risk.rationale(), MAX_RATIONALE_CHARS));
        rationale.getStyleClass().add("clause-card-text");
        rationale.setWrapText(true);

        VBox text = new VBox(2, category, rationale);
        getChildren().addAll(badge, text);
    }
}
