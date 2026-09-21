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

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * A titled card container used for every section of the report (entities, clauses, risk flags,
 * advisory notes, efficiency). One reusable shell instead of five near-duplicate ones.
 */
public final class SectionCard extends VBox {

    public SectionCard(String title, String subtitle, Node content) {
        getStyleClass().add("section-card");
        setSpacing(12);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-card-title");

        VBox header = new VBox(2, titleLabel);
        if (subtitle != null && !subtitle.isBlank()) {
            Label subtitleLabel = new Label(subtitle);
            subtitleLabel.getStyleClass().add("section-card-subtitle");
            subtitleLabel.setWrapText(true);
            header.getChildren().add(subtitleLabel);
        }

        getChildren().addAll(header, content);
    }
}
