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

import io.statigate.desktop.engine.AnalysisEngine;
import io.statigate.desktop.state.AnalysisPhase;
import io.statigate.desktop.state.AppState;
import io.statigate.desktop.validation.FileValidationService;
import io.statigate.desktop.validation.ValidatedFile;
import io.statigate.desktop.library.LibraryEntry;
import io.statigate.desktop.library.LibraryStore;
import io.statigate.pipeline.PipelineResult;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Dashboard page: upload a document, watch it analyze, read the report. Every successful fresh
 * analysis (never one reopened from the library - see {@link #showStoredResult}) is saved to the
 * {@link LibraryStore} automatically, so nothing analyzed here is ever silently lost.
 */
public final class DashboardPane extends BorderPane {

    private static final Logger log = LoggerFactory.getLogger(DashboardPane.class);

    private final AppState state;
    private final AnalysisEngine engine;
    private final LibraryStore library;
    private final DropZone dropZone;
    private final StackPane contentHost = new StackPane();

    public DashboardPane(AppState state, AnalysisEngine engine, FileValidationService validator,
            LibraryStore library) {
        this.state = state;
        this.engine = engine;
        this.library = library;

        this.dropZone = new DropZone(validator, this::onFileAccepted, this::onFileRejected);
        dropZone.disableProperty().bind(state.phaseProperty().isEqualTo(AnalysisPhase.ANALYZING));

        VBox uploadArea = new VBox(dropZone);
        uploadArea.setAlignment(Pos.CENTER);
        uploadArea.setPadding(new Insets(28, 32, 8, 32));
        // Only IDLE needs the full drop target: once a report (or an error, or a run in progress)
        // is on screen, the big dashed box just pushes real content below the fold, and TopBar's
        // "New analysis" button already re-opens the file browser without it being visible.
        uploadArea.visibleProperty().bind(state.phaseProperty().isEqualTo(AnalysisPhase.IDLE));
        uploadArea.managedProperty().bind(uploadArea.visibleProperty());

        ScrollPane scroll = new ScrollPane(contentHost);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("content-scroll");

        setTop(uploadArea);
        setCenter(scroll);

        state.phaseProperty().addListener((obs, oldPhase, newPhase) -> renderContent());
        renderContent();
    }

    /** Shows a report loaded from the library, without touching the engine or saving anything. */
    public void showStoredResult(PipelineResult result) {
        state.complete(result);
    }

    private void onFileAccepted(ValidatedFile file) {
        Optional<LibraryEntry> existing = LibraryStore.contentHashOf(file.path())
                .flatMap(library::findByContentHash);
        if (existing.isPresent()) {
            Optional<PipelineResult> stored = library.loadResult(existing.get().id());
            if (stored.isPresent()) {
                notifyAlreadyAnalyzed(existing.get());
                // Same content, so no new library entry - just bump the existing one to the front
                // of the library as "most recently confirmed" instead of leaving its old timestamp
                // in place (which, over repeated re-checks, reads like several stale copies).
                library.touch(existing.get().id());
                state.complete(stored.get());
                return;
            }
            // The library entry's meta.json matched but its result.json is gone or unreadable
            // (e.g. hand-deleted) - fall through and analyze it fresh rather than telling the user
            // "no changes" and then showing nothing.
        }
        runFreshAnalysis(file);
    }

    private void notifyAlreadyAnalyzed(LibraryEntry entry) {
        String when = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withZone(ZoneId.systemDefault())
                .format(entry.analyzedAt());
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Already analyzed");
        alert.setHeaderText("No changes since last time");
        alert.setContentText("“" + entry.displayName() + "” is byte-for-byte the same document "
                + "you analyzed on " + when + " — showing that saved result instead of "
                + "re-running the analysis.");
        if (getScene() != null && getScene().getWindow() != null) {
            alert.initOwner(getScene().getWindow());
        }
        alert.showAndWait();
    }

    private void runFreshAnalysis(ValidatedFile file) {
        state.startAnalyzing(file.displayName());
        var maybeTask = engine.submit(file.path(), state);
        if (maybeTask.isEmpty()) {
            state.fail("An analysis is already running — please wait for it to finish.");
            return;
        }
        var task = maybeTask.get();
        task.setOnSucceeded(e -> {
            @SuppressWarnings("unchecked")
            PipelineResult result = (PipelineResult) task.getValue();
            state.complete(result);
            saveToLibrary(file, result);
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            log.warn("Analysis failed", ex);
            state.fail(ex != null && ex.getMessage() != null ? ex.getMessage() : "Analysis failed.");
        });
        task.setOnCancelled(e -> state.fail(
                "This document took longer than " + AnalysisEngine.ANALYSIS_TIMEOUT.toMinutes()
                        + " minutes to analyze and was stopped. This can happen with an unusually "
                        + "large or complex file, or a very large model configured under Settings. "
                        + "Try a smaller file, or check Settings."));
    }

    private void saveToLibrary(ValidatedFile file, PipelineResult result) {
        try {
            library.save(file.displayName(), file.path(), extensionOf(file.displayName()), true, result);
        } catch (IOException e) {
            // Saving to the library is a convenience on top of a successful analysis, not a
            // precondition for seeing it: the report the user is looking at right now is
            // unaffected, so this is logged rather than surfaced as an error banner.
            log.warn("Could not save '{}' to the library: {}", file.displayName(), e.toString());
        }
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1 ? "" : fileName.substring(dot + 1);
    }

    private void onFileRejected(String message) {
        state.fail(message);
    }

    private void renderContent() {
        contentHost.getChildren().setAll(switch (state.phase()) {
            case IDLE -> idlePlaceholder();
            case ANALYZING -> new LoadingOverlay(state);
            case COMPLETE -> new ReportView(state.result());
            case FAILED -> centeredNarrow(new ErrorBanner(state.errorMessageProperty().get(), state::reset));
        });
    }

    private StackPane idlePlaceholder() {
        return centeredNarrow(new EmptyState("Nothing analyzed yet",
                "Drop a contract into the box above, or click “Browse files…”, to get started."));
    }

    private StackPane centeredNarrow(javafx.scene.Node content) {
        StackPane pane = new StackPane(content);
        pane.setPadding(new Insets(48));
        pane.setMaxWidth(560);
        StackPane wrapper = new StackPane(pane);
        wrapper.setAlignment(Pos.TOP_CENTER);
        return wrapper;
    }
}
