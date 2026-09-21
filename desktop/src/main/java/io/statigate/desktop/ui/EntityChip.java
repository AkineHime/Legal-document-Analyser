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

import io.statigate.core.Entity;
import io.statigate.desktop.format.Labels;
import io.statigate.desktop.format.TextSanitizer;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** A small tile for one extracted entity: its type and the exact text found for it. */
public final class EntityChip extends VBox {

    public EntityChip(Entity entity) {
        getStyleClass().add("entity-chip");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(2);

        Label type = new Label(Labels.entityType(entity.type()).toUpperCase(java.util.Locale.ROOT));
        type.getStyleClass().add("entity-chip-type");

        Label text = new Label(TextSanitizer.truncate(entity.text(), 80));
        text.getStyleClass().add("entity-chip-text");
        text.setWrapText(true);
        text.setMaxWidth(200);

        getChildren().addAll(type, text);
    }
}
