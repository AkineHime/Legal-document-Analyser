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
import io.statigate.desktop.engine.AnalysisEngine;
import io.statigate.desktop.engine.EngineSettings;
import io.statigate.desktop.library.LibraryStore;
import io.statigate.desktop.state.AnalysisPhase;
import io.statigate.desktop.state.AppState;
import io.statigate.desktop.validation.FileValidationService;
import io.statigate.nlp.ModelLocator;
import java.io.File;
import java.nio.file.Path;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composition root for the whole window: a dark {@link Sidebar} on the left switches between the
 * two real pages ({@link NavSection}); the right side stacks a {@link TopBar} over whichever page
 * is current. {@link DashboardPane} is a persistent instance (its in-progress/completed report
 * must survive the user briefly visiting the Library), while {@link LibraryPane} is rebuilt fresh
 * on every visit so it always reflects what is actually saved on disk.
 */
public final class MainView extends BorderPane {

    private static final Logger log = LoggerFactory.getLogger(MainView.class);

    private final AppState state;
    private final AnalysisEngine engine;
    private final LibraryStore library;
    private final ObjectProperty<NavSection> currentSection = new SimpleObjectProperty<>(NavSection.DASHBOARD);
    private final DashboardPane dashboardPane;
    private final StackPane pageHost = new StackPane();

    public MainView(AppState state, AnalysisEngine engine, FileValidationService validator, LibraryStore library) {
        this.state = state;
        this.engine = engine;
        this.library = library;

        getStyleClass().add("app-shell");

        this.dashboardPane = new DashboardPane(state, engine, validator, library);

        Sidebar sidebar = new Sidebar(currentSection, this::showSettings, this::showAbout);
        TopBar topBar = new TopBar(currentSection, this::startNewAnalysis);

        BorderPane contentColumn = new BorderPane();
        contentColumn.setTop(topBar);
        contentColumn.setCenter(pageHost);

        setLeft(sidebar);
        setCenter(contentColumn);

        currentSection.addListener((obs, oldSection, newSection) -> renderPage(newSection, topBar));
        renderPage(currentSection.get(), topBar);
    }

    private void renderPage(NavSection section, TopBar topBar) {
        if (section == NavSection.LIBRARY) {
            pageHost.getChildren().setAll(
                    new LibraryPane(library, topBar.searchQueryProperty(), this::openFromLibrary));
        } else {
            pageHost.getChildren().setAll(dashboardPane);
        }
    }

    private void openFromLibrary(io.statigate.desktop.library.LibraryEntry entry) {
        library.loadResult(entry.id()).ifPresentOrElse(result -> {
            currentSection.set(NavSection.DASHBOARD);
            dashboardPane.showStoredResult(result);
        }, () -> {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "Couldn't open “" + entry.displayName() + "” — its saved data may have been "
                            + "moved or deleted outside Statigate.");
            if (getScene() != null && getScene().getWindow() != null) {
                alert.initOwner(getScene().getWindow());
            }
            alert.showAndWait();
        });
    }

    private void startNewAnalysis() {
        currentSection.set(NavSection.DASHBOARD);
        state.reset();
    }

    private void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        if (getScene() != null && getScene().getWindow() != null) {
            alert.initOwner(getScene().getWindow());
        }
        alert.setTitle("About " + AppConfig.APP_NAME);
        alert.setHeaderText(AppConfig.APP_NAME + " " + AppConfig.VOLUME_LABEL);
        alert.setContentText(
                "An offline, single-process contract analyzer for the Indian context.\n\n"
                        + "Every clause is typed, checked against a local Indian statute corpus, and "
                        + "flagged with a plain-language reason. Nothing about the document ever leaves "
                        + "this machine - the analysis makes no network calls. Every document you analyze "
                        + "is saved to a local library (" + LibraryStore.defaultRoot() + ") so you can "
                        + "reopen it without re-analyzing.\n\n"
                        + "Advisory information only, not legal advice (Advocates Act, 1961).");
        alert.setResizable(true);
        alert.showAndWait();
    }

    private void showSettings() {
        Dialog<EngineSettings> dialog = new Dialog<>();
        dialog.setTitle("Settings");
        dialog.setHeaderText("Model directory and thread count for the next analysis.");
        if (getScene() != null && getScene().getWindow() != null) {
            dialog.initOwner(getScene().getWindow());
            dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());
        }
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        EngineSettings current = engine.settings();

        TextField modelsField = new TextField(current.modelsDir() == null ? "" : current.modelsDir().toString());
        modelsField.setPromptText(new ModelLocator().modelsDir().toString() + "  (default)");
        modelsField.setPrefWidth(300);
        javafx.scene.control.Button browse = new javafx.scene.control.Button("Browse…");
        browse.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Choose the models directory");
            File dir = chooser.showDialog(dialog.getOwner());
            if (dir != null) {
                modelsField.setText(dir.getAbsolutePath());
            }
        });

        int maxThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
        // Not editable, deliberately: an editable Spinner needs explicit focus-lost-commit wiring
        // to avoid silently discarding a typed value that was never committed to the value
        // property, and arrow-only selection over this small a range (1..CPU count) loses nothing.
        Spinner<Integer> threadsSpinner = new Spinner<>(1, maxThreads, Math.min(current.threads(), maxThreads));

        javafx.scene.control.CheckBox gpuCheck = new javafx.scene.control.CheckBox(
                "Use GPU acceleration (NVIDIA CUDA)");
        gpuCheck.setSelected(current.useGpu());
        Label gpuNote = new Label(
                "Needs a build compiled with the ‘gpu’ Maven profile, plus a matching "
                        + "CUDA Toolkit and cuDNN installed. Falls back to CPU automatically (see the "
                        + "log) if either is missing — it will not break analysis.");
        gpuNote.setWrapText(true);
        gpuNote.setStyle("-fx-font-size: 10px; -fx-opacity: 0.75;");

        boolean busy = state.phase() == AnalysisPhase.ANALYZING;
        modelsField.setDisable(busy);
        browse.setDisable(busy);
        threadsSpinner.setDisable(busy);
        gpuCheck.setDisable(busy);
        dialog.getDialogPane().lookupButton(ButtonType.OK).setDisable(busy);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label("Models directory:"), modelsField, browse);
        grid.addRow(1, new Label("ONNX threads:"), threadsSpinner);
        grid.addRow(2, new Label("Library folder:"), new Label(LibraryStore.defaultRoot().toString()));
        grid.add(gpuCheck, 0, 3, 3, 1);
        grid.add(gpuNote, 0, 4, 3, 1);
        if (busy) {
            Label notice = new Label("An analysis is running — settings will apply to the next one.");
            notice.setWrapText(true);
            grid.add(notice, 0, 5, 3, 1);
        }
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            String text = modelsField.getText() == null ? "" : modelsField.getText().trim();
            try {
                Path dir = text.isEmpty() ? null : Path.of(text);
                return new EngineSettings(dir, threadsSpinner.getValue(), gpuCheck.isSelected());
            } catch (java.nio.file.InvalidPathException e) {
                // Typed garbage rather than a real path: keep the previous settings rather than
                // letting an unchecked exception escape the dialog's close sequence.
                log.warn("Ignoring invalid models directory '{}': {}", text, e.getMessage());
                return null;
            }
        });

        dialog.showAndWait().ifPresent(newSettings -> {
            if (!engine.updateSettings(newSettings)) {
                Alert warn = new Alert(Alert.AlertType.WARNING,
                        "Settings weren't applied because an analysis was running. Please try again once it finishes.");
                warn.initOwner(dialog.getOwner());
                warn.showAndWait();
            }
        });
    }
}
