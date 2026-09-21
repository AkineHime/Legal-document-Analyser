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

import io.statigate.desktop.format.TextSanitizer;
import io.statigate.desktop.library.LibraryEntry;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * One tile in the {@link LibraryPane} grid: a severity-colored accent strip, the file name, when it
 * was analyzed, a couple of summary stats, and a delete action. The whole card opens the stored
 * report; only the small trash icon deletes it, and a click there never also opens the card (the
 * delete button consumes its own click before it can bubble up).
 */
public final class DocumentCard extends VBox {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .withLocale(Locale.getDefault());

    public DocumentCard(LibraryEntry entry, Consumer<LibraryEntry> onOpen, Consumer<LibraryEntry> onDelete) {
        getStyleClass().add("doc-card");
        setPrefWidth(240);
        setMaxWidth(240);

        Region accent = new Region();
        accent.getStyleClass().addAll("doc-card-accent", severityStyleClass(entry.highestSeverity()));
        accent.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label(TextSanitizer.truncate(entry.displayName(), 42));
        title.getStyleClass().add("doc-card-title");
        title.setWrapText(true);

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);

        Button delete = new Button();
        delete.setGraphic(Icons.trash(12));
        delete.getStyleClass().add("doc-card-delete");
        delete.setFocusTraversable(false);
        // Consumed at the source rather than relying on the card's click handler to distinguish
        // targets: guarantees a delete click can never also open the card, regardless of exactly
        // how far a MouseEvent would otherwise bubble.
        delete.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        delete.setOnAction(e -> {
            if (onDelete != null) {
                onDelete.accept(entry);
            }
        });
        HBox titleRow = new HBox(6, title, titleSpacer, delete);
        titleRow.setAlignment(Pos.TOP_LEFT);

        Label meta = new Label(DATE_FORMAT.format(entry.analyzedAt().atZone(ZoneId.systemDefault()))
                + "  ·  ." + entry.sourceExtension());
        meta.getStyleClass().add("doc-card-meta");

        HBox stats = new HBox(14,
                statLabel(entry.clauseCount() + (entry.clauseCount() == 1 ? " clause" : " clauses")),
                statLabel(entry.riskCount() + (entry.riskCount() == 1 ? " flag" : " flags")));

        VBox body = new VBox(8, titleRow, meta, stats);
        body.getStyleClass().add("doc-card-body");
        VBox.setMargin(body, new Insets(0, 0, 4, 0));

        getChildren().addAll(accent, body);

        if (onOpen != null) {
            setOnMouseClicked(e -> onOpen.accept(entry));
        }
    }

    private Label statLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("doc-card-stat");
        return label;
    }

    private static String severityStyleClass(String highestSeverity) {
        if (highestSeverity == null) {
            return "none";
        }
        return switch (highestSeverity) {
            case "HIGH" -> "high";
            case "MEDIUM" -> "medium";
            case "LOW" -> "low";
            default -> "none";
        };
    }
}
