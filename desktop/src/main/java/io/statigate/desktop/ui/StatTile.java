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

/** A single metric tile (clause count, risk count, elapsed time, ...), used several times per report. */
public final class StatTile extends VBox {

    public StatTile(String value, String label) {
        getStyleClass().add("stat-tile");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(2);

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("stat-tile-value");
        Label captionLabel = new Label(label.toUpperCase(java.util.Locale.ROOT));
        captionLabel.getStyleClass().add("stat-tile-label");

        getChildren().addAll(valueLabel, captionLabel);
    }
}
