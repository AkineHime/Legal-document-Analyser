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

package io.statigate.desktop.state;

import io.statigate.pipeline.AnalysisPipeline;
import io.statigate.pipeline.PipelineResult;
import java.util.List;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * The single source of truth the whole UI binds to. Every view reads this; only the classes in the
 * {@code engine} package write to it, and always on the FX Application Thread (see the
 * thread-safety note on each mutator).
 *
 * <p>Deliberately framework-light: this class depends on JavaFX's property types (the natural
 * reactive substrate for a JavaFX app) but not on {@code Application}, a {@code Stage}, or a running
 * toolkit, so its transitions can be unit-tested with plain JUnit.
 */
public final class AppState {

    /** The four real pipeline stages, in the order Statigate always runs them. */
    public static final List<String> STAGES = List.of("ingestion", "extraction", "grounding", "advisory");

    private final ObjectProperty<AnalysisPhase> phase = new SimpleObjectProperty<>(AnalysisPhase.IDLE);
    private final StringProperty fileName = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final ObjectProperty<PipelineResult> result = new SimpleObjectProperty<>();
    private final ObservableList<String> completedStages =
            FXCollections.observableArrayList();
    private final ReadOnlyDoubleWrapper stageProgress = new ReadOnlyDoubleWrapper(0.0);

    public AppState() {
        completedStages.addListener((javafx.collections.ListChangeListener<String>) c ->
                stageProgress.set(STAGES.isEmpty() ? 0.0 : (double) completedStages.size() / STAGES.size()));
    }

    // ---- read-only views for the UI to bind against ----

    public ObjectProperty<AnalysisPhase> phaseProperty() {
        return phase;
    }

    public AnalysisPhase phase() {
        return phase.get();
    }

    public StringProperty fileNameProperty() {
        return fileName;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public ObjectProperty<PipelineResult> resultProperty() {
        return result;
    }

    public PipelineResult result() {
        return result.get();
    }

    public ObservableList<String> completedStages() {
        return FXCollections.unmodifiableObservableList(completedStages);
    }

    public ReadOnlyDoubleProperty stageProgressProperty() {
        return stageProgress.getReadOnlyProperty();
    }

    // ---- mutators; callers must be on the FX Application Thread ----
    //
    // Every mutator below sets `phase` LAST, after every other field it touches. A JavaFX property
    // listener fires synchronously, inline, the instant `.set()` is called - so a phase-change
    // listener (this is how DashboardPane decides what to render) that fires partway through one
    // of these methods would see whichever fields had already been updated and whichever had not,
    // rather than a consistent new state. That was a real, shipped bug: ErrorBanner and ReportView
    // each take their data as a one-time constructor argument (not a live binding), read at the
    // moment the phase listener fires - so when `fail()` used to set `phase` first, the banner was
    // built from the *previous* error message (often empty), not the one just passed in.

    public void reset() {
        fileName.set("");
        errorMessage.set("");
        result.set(null);
        completedStages.clear();
        phase.set(AnalysisPhase.IDLE);
    }

    public void startAnalyzing(String validatedFileName) {
        fileName.set(validatedFileName == null ? "" : validatedFileName);
        errorMessage.set("");
        result.set(null);
        completedStages.clear();
        phase.set(AnalysisPhase.ANALYZING);
    }

    /**
     * Records that one pipeline stage finished. Safe to call for a stage more than once, or for a
     * stage this UI does not otherwise recognize (an older/newer pipeline version) - both are
     * ignored rather than corrupting the step count.
     *
     * <p>Must be called on the FX Application Thread; {@link AnalysisPipeline.StageListener}
     * callbacks run on the analysis worker thread, so the caller wiring the listener is responsible
     * for hopping back via {@code Platform.runLater}.
     */
    public void reportStageComplete(String stage) {
        if (stage != null && STAGES.contains(stage) && !completedStages.contains(stage)) {
            completedStages.add(stage);
        }
    }

    public void complete(PipelineResult analysisResult) {
        result.set(analysisResult);
        errorMessage.set("");
        // Ensure the step indicator always ends "full" even if a stage-complete callback were ever
        // missed - the report is done regardless, and the UI should not look stuck mid-progress.
        for (String stage : STAGES) {
            if (!completedStages.contains(stage)) {
                completedStages.add(stage);
            }
        }
        phase.set(AnalysisPhase.COMPLETE);
    }

    public void fail(String message) {
        errorMessage.set(message == null || message.isBlank() ? "Something went wrong." : message);
        result.set(null);
        phase.set(AnalysisPhase.FAILED);
    }
}
