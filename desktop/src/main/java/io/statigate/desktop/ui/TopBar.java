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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The bar above the content area: a page heading, a search box (functional only on the Library
 * page - hidden elsewhere rather than shown-but-inert), and a persistent "New analysis" action
 * that works from any page.
 */
public final class TopBar extends VBox {

    private final StringProperty searchQuery = new SimpleStringProperty("");
    private final Label heading = new Label();
    private final Label subheading = new Label();

    public TopBar(ObjectProperty<NavSection> currentSection, Runnable onNewAnalysis) {
        getStyleClass().add("topbar");
        setSpacing(16);

        heading.getStyleClass().add("topbar-heading");
        subheading.getStyleClass().add("topbar-subheading");
        VBox headingBox = new VBox(2, heading, subheading);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        StackPane search = buildSearchField();
        search.managedProperty().bind(search.visibleProperty());
        search.visibleProperty().bind(currentSection.isEqualTo(NavSection.LIBRARY));

        Button newAnalysis = new Button("New analysis");
        newAnalysis.getStyleClass().add("drop-zone-browse");
        if (onNewAnalysis != null) {
            newAnalysis.setOnAction(e -> onNewAnalysis.run());
        }

        HBox row = new HBox(16, headingBox, spacer, search, newAnalysis);
        row.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(row);

        currentSection.addListener((obs, oldSection, newSection) -> applyHeading(newSection));
        applyHeading(currentSection.get());
    }

    private void applyHeading(NavSection section) {
        if (section == NavSection.LIBRARY) {
            heading.setText("Library");
            subheading.setText("Every contract you've analyzed, stored locally on this machine.");
        } else {
            heading.setText("Dashboard");
            subheading.setText("Drop in a contract to get a statute-grounded, plain-language review.");
        }
    }

    private StackPane buildSearchField() {
        TextField field = new TextField();
        field.getStyleClass().add("search-field");
        field.setPromptText("Search your library...");
        field.setPrefWidth(240);
        field.textProperty().bindBidirectional(searchQuery);

        // The magnifier sits over the field's left padding (see .search-field's left inset in
        // theme.css, which leaves exactly enough room for it) rather than being a separate control
        // in the row, so the whole thing behaves as one search box.
        StackPane withIcon = new StackPane(field, iconOverlay());
        StackPane.setAlignment(field, Pos.CENTER);
        return withIcon;
    }

    private javafx.scene.Node iconOverlay() {
        // A bare Node here, not a wrapping StackPane: StackPane.layoutInArea stretches a
        // *resizable* child (any Region, including an unconstrained StackPane) to fill the whole
        // shared content area before applying its alignment - which silently pulled a wrapper
        // StackPane out to the full width of the search box, centering the tiny icon in the
        // middle of the field instead of pinning it to the left edge. A Group is not resizable, so
        // it is positioned at its own natural size instead of stretched.
        javafx.scene.Node icon = Icons.search(13);
        icon.getStyleClass().add("search-icon");
        icon.setMouseTransparent(true);
        StackPane.setAlignment(icon, Pos.CENTER_LEFT);
        icon.setTranslateX(12);
        return icon;
    }

    public ReadOnlyStringProperty searchQueryProperty() {
        return searchQuery;
    }
}
