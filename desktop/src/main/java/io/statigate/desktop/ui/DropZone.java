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
import io.statigate.desktop.validation.FileValidationException;
import io.statigate.desktop.validation.FileValidationService;
import io.statigate.desktop.validation.ValidatedFile;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * The file-intake control: click to browse, or drag a file in. Stands in for the reference design's
 * centered product photograph, since here the "hero image" the user cares about is not decorative -
 * it is the one control that actually starts the whole flow.
 *
 * <p>Every path that can produce a file - the chooser and the drop event - is required to go
 * through the same {@link FileValidationService} before {@code onFileAccepted} ever fires, so there
 * is exactly one place file-intake rules live.
 */
public final class DropZone extends VBox {

    private final FileValidationService validator;
    private final Consumer<ValidatedFile> onFileAccepted;
    private final Consumer<String> onRejected;

    public DropZone(FileValidationService validator, Consumer<ValidatedFile> onFileAccepted,
            Consumer<String> onRejected) {
        this.validator = validator;
        this.onFileAccepted = onFileAccepted;
        this.onRejected = onRejected;

        getStyleClass().add("drop-zone");
        setAlignment(Pos.CENTER);
        setSpacing(10);
        setPrefSize(300, 260);
        setMaxSize(300, 260);

        javafx.scene.Node icon = Icons.document(46);
        Label title = new Label("Drop a contract here");
        title.getStyleClass().add("drop-zone-title");
        Label subtitle = new Label(String.join(", ",
                validator.supportedExtensions().stream().map(e -> "." + e).sorted().toList())
                + "  ·  up to " + (AppConfig.MAX_UPLOAD_BYTES / (1024 * 1024)) + " MB");
        subtitle.getStyleClass().add("drop-zone-subtitle");

        Button browse = new Button("Browse files…");
        browse.getStyleClass().add("drop-zone-browse");
        browse.setOnAction(e -> openChooser());

        getChildren().addAll(icon, title, subtitle, browse);

        // Only treat a click on the pane's own background as "open the chooser" - a click that
        // landed on the button already triggers its own action, and would otherwise also bubble
        // here and open a second dialog.
        setOnMouseClicked(e -> {
            if (e.getTarget() == this && !isDisabled()) {
                openChooser();
            }
        });

        setOnDragOver(this::handleDragOver);
        setOnDragEntered(e -> {
            if (acceptable(e.getDragboard())) {
                getStyleClass().add("drag-over");
            }
        });
        setOnDragExited(e -> getStyleClass().remove("drag-over"));
        setOnDragDropped(this::handleDragDropped);
    }

    private void handleDragOver(DragEvent event) {
        if (!isDisabled() && acceptable(event.getDragboard())) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    private void handleDragDropped(DragEvent event) {
        getStyleClass().remove("drag-over");
        Dragboard db = event.getDragboard();
        boolean success = false;
        if (!isDisabled() && db.hasFiles()) {
            List<File> files = db.getFiles();
            if (files.size() != 1) {
                onRejected.accept("Drop one file at a time.");
            } else {
                acceptCandidate(files.get(0));
            }
            success = true;
        }
        event.setDropCompleted(success);
        event.consume();
    }

    private boolean acceptable(Dragboard db) {
        // A cheap, permissive pre-check for the drag-over visual only; the real decision (extension,
        // size, existence) always happens in FileValidationService once the file is actually dropped
        // or chosen, so this never needs to be kept in lock-step with that logic.
        return db.hasFiles();
    }

    private void openChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a contract to analyze");
        List<String> extensions = validator.supportedExtensions().stream()
                .map(e -> "*." + e).sorted().toList();
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Supported documents", extensions));
        Window owner = getScene() != null ? getScene().getWindow() : null;
        File chosen = chooser.showOpenDialog(owner);
        if (chosen != null) {
            acceptCandidate(chosen);
        }
    }

    private void acceptCandidate(File candidate) {
        try {
            ValidatedFile validated = validator.validate(candidate);
            onFileAccepted.accept(validated);
        } catch (FileValidationException e) {
            onRejected.accept(e.getMessage());
        }
    }

    /** Convenience for a "Begin analysis" call to action elsewhere in the hero. */
    public void triggerBrowse() {
        if (!isDisabled()) {
            openChooser();
        }
    }
}
