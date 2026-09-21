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

import io.statigate.desktop.AppConfig;
import java.util.EnumMap;
import java.util.Map;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;

/**
 * The dark left-hand navigation column: brand mark, the two real pages ({@link NavSection}), and
 * two dialog-opening actions (Settings, About). Bound to {@code currentSection} so the active
 * highlight always matches {@link MainView}'s content without either side polling the other.
 */
public final class Sidebar extends VBox {

    public Sidebar(ObjectProperty<NavSection> currentSection, Runnable onShowSettings, Runnable onShowAbout) {
        getStyleClass().add("sidebar");
        setSpacing(2);
        setPrefWidth(220);
        setMinWidth(190);

        getChildren().add(brandRow());
        getChildren().add(spacer(18));
        getChildren().add(sectionLabel("General"));

        Map<NavSection, NavItem> pageItems = new EnumMap<>(NavSection.class);
        NavItem dashboard = new NavItem(Icons.dashboard(15), "Dashboard", () -> currentSection.set(NavSection.DASHBOARD));
        NavItem library = new NavItem(Icons.library(15), "Library", () -> currentSection.set(NavSection.LIBRARY));
        pageItems.put(NavSection.DASHBOARD, dashboard);
        pageItems.put(NavSection.LIBRARY, library);
        getChildren().addAll(dashboard, library);

        getChildren().add(spacer(10));
        getChildren().add(sectionLabel("Tools"));
        getChildren().add(new NavItem(Icons.gear(15), "Settings", onShowSettings));
        getChildren().add(new NavItem(Icons.info(15), "About", onShowAbout));

        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        getChildren().add(grow);
        getChildren().add(footerNote());

        currentSection.addListener((obs, oldSection, newSection) ->
                pageItems.forEach((section, item) -> item.setActive(section == newSection)));
        pageItems.forEach((section, item) -> item.setActive(section == currentSection.get()));
    }

    private HBox brandRow() {
        StackPane badge = new StackPane();
        Circle ring = new Circle(13);
        ring.getStyleClass().add("sidebar-brand-mark");
        Text mark = new Text("S");
        mark.getStyleClass().add("sidebar-brand-letter");
        badge.getChildren().addAll(ring, mark);

        Label name = new Label(AppConfig.APP_NAME);
        name.getStyleClass().add("sidebar-brand-name");
        Label tag = new Label("Offline contract review");
        tag.getStyleClass().add("sidebar-brand-tag");
        VBox text = new VBox(1, name, tag);

        HBox row = new HBox(10, badge, text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new javafx.geometry.Insets(4, 10, 0, 10));
        return row;
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text.toUpperCase(java.util.Locale.ROOT));
        label.getStyleClass().add("sidebar-section-label");
        return label;
    }

    private Region spacer(double height) {
        Region r = new Region();
        r.setPrefHeight(height);
        return r;
    }

    private Label footerNote() {
        Label label = new Label("Advisory information only, not legal advice (Advocates Act, 1961).");
        label.getStyleClass().add("sidebar-footer-note");
        label.setWrapText(true);
        label.setPadding(new javafx.geometry.Insets(0, 10, 6, 10));
        return label;
    }
}
