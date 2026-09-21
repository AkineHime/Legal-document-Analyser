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

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** A plain "nothing to show" placeholder, reused for any empty report section or the initial screen. */
public final class EmptyState extends VBox {

    public EmptyState(String title, String body) {
        getStyleClass().add("empty-state");
        setAlignment(Pos.CENTER);
        setSpacing(6);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-state-title");

        Label bodyLabel = new Label(body);
        bodyLabel.getStyleClass().add("empty-state-body");
        bodyLabel.setWrapText(true);
        bodyLabel.setAlignment(Pos.CENTER);
        bodyLabel.setStyle("-fx-text-alignment: center;");

        getChildren().addAll(titleLabel, bodyLabel);
    }
}
