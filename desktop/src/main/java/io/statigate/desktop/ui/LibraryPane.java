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

import io.statigate.desktop.library.LibraryEntry;
import io.statigate.desktop.library.LibraryStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The grid of every document Statigate has analyzed and saved, filterable by the top bar's search
 * box. Built fresh each time the user navigates here (a directory scan of small JSON files is
 * cheap - see {@link LibraryStore#listEntries()}), so it always reflects what is actually on disk.
 */
public final class LibraryPane extends VBox {

    private final LibraryStore library;
    private final List<LibraryEntry> allEntries;
    private final FlowPane grid = new FlowPane(16, 16);
    private final StackPane gridHost = new StackPane(grid);
    private final Label countLabel = new Label();
    private final Consumer<LibraryEntry> onOpen;
    private final ReadOnlyStringProperty searchQuery;

    public LibraryPane(LibraryStore library, ReadOnlyStringProperty searchQuery, Consumer<LibraryEntry> onOpen) {
        this.library = library;
        this.onOpen = onOpen;
        this.searchQuery = searchQuery;
        this.allEntries = new ArrayList<>(library.listEntries());

        setSpacing(16);
        setPadding(new Insets(24, 32, 40, 32));

        countLabel.getStyleClass().add("section-card-subtitle");
        getChildren().addAll(countLabel, gridHost);

        searchQuery.addListener((obs, oldQuery, newQuery) -> rebuild(newQuery));
        rebuild(searchQuery.get());
    }

    private void rebuild(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<LibraryEntry> filtered = allEntries.stream()
                .filter(e -> needle.isEmpty() || e.displayName().toLowerCase(Locale.ROOT).contains(needle))
                .toList();

        countLabel.setText(allEntries.isEmpty()
                ? "Nothing analyzed yet."
                : filtered.size() + " of " + allEntries.size() + " document(s)");

        grid.getChildren().clear();
        if (filtered.isEmpty()) {
            gridHost.getChildren().setAll(emptyState(needle));
        } else {
            for (LibraryEntry entry : filtered) {
                grid.getChildren().add(new DocumentCard(entry, onOpen, this::confirmDelete));
            }
            gridHost.getChildren().setAll(grid);
        }
    }

    private EmptyState emptyState(String needle) {
        if (!needle.isEmpty()) {
            return new EmptyState("No matches", "No saved document's name contains “" + needle + "”.");
        }
        return new EmptyState("Nothing analyzed yet",
                "Documents you analyze on the Dashboard are saved here automatically.");
    }

    private void confirmDelete(LibraryEntry entry) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove “" + entry.displayName() + "” from your library? "
                        + (entry.hasStoredSource() ? "This also deletes the stored copy of the file. " : "")
                        + "This cannot be undone.",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.setTitle("Remove from library");
        confirm.setHeaderText(null);
        if (getScene() != null && getScene().getWindow() != null) {
            confirm.initOwner(getScene().getWindow());
        }
        confirm.showAndWait().filter(button -> button == ButtonType.OK).ifPresent(button -> {
            library.delete(entry.id());
            allEntries.removeIf(e -> e.id().equals(entry.id()));
            rebuild(searchQuery.get());
        });
    }
}
