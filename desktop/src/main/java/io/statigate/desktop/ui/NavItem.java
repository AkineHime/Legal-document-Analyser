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
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/**
 * One row in {@link Sidebar}: an icon, a label, and a click action. Used both for the two
 * page-switching items (Dashboard, Library), which can be marked {@link #setActive}, and for the
 * two action items (Settings, About), which just open a dialog and are never "active".
 */
public final class NavItem extends HBox {

    public NavItem(Node icon, String label, Runnable onClick) {
        getStyleClass().add("nav-item");
        setSpacing(10);
        setAlignment(Pos.CENTER_LEFT);

        Label text = new Label(label);
        text.getStyleClass().add("nav-item-label");
        getChildren().addAll(icon, text);

        if (onClick != null) {
            setOnMouseClicked(e -> onClick.run());
        }
    }

    public void setActive(boolean active) {
        if (active) {
            if (!getStyleClass().contains("active")) {
                getStyleClass().add("active");
            }
        } else {
            getStyleClass().remove("active");
        }
    }
}
