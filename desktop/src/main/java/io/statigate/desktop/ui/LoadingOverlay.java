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

import io.statigate.desktop.state.AppState;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Shown while an analysis is running. The progress bar is bound directly to {@link
 * AppState#stageProgressProperty()}, so it reflects the four real pipeline stages actually
 * completing rather than a simulated animation; the paper-flight loop above it is the one purely
 * decorative element (see {@link LoadingAnimation}). The elapsed-time counter exists so a
 * genuinely slow document (as opposed to a frozen one) is visibly still working rather than
 * indistinguishable from a hang - the analysis is hard-capped regardless (see {@code
 * AnalysisEngine.ANALYSIS_TIMEOUT}), but that can be minutes away, which is a long time to stare at
 * an unchanging screen with no clock on it.
 */
public final class LoadingOverlay extends VBox {

    private static final Map<String, String> STAGE_CAPTIONS = new LinkedHashMap<>();
    static {
        STAGE_CAPTIONS.put("ingestion", "Reading the document…");
        STAGE_CAPTIONS.put("extraction", "Classifying clauses and extracting entities…");
        STAGE_CAPTIONS.put("grounding", "Checking Indian statute for related provisions…");
        STAGE_CAPTIONS.put("advisory", "Drafting plain-language notes…");
    }

    private static final int REASSURANCE_AFTER_SECONDS = 20;

    private final Label caption = new Label();
    private final Label elapsed = new Label();
    private final Timeline clock;

    public LoadingOverlay(AppState state) {
        getStyleClass().add("loading-overlay");
        setAlignment(Pos.CENTER);
        setSpacing(10);

        Label title = new Label("Analyzing " + safe(state.fileNameProperty().get()));
        title.getStyleClass().add("loading-title");
        state.fileNameProperty().addListener((obs, oldV, newV) -> title.setText("Analyzing " + safe(newV)));

        LoadingAnimation animation = new LoadingAnimation();

        ProgressBar bar = new ProgressBar();
        bar.setPrefWidth(220);
        bar.progressProperty().bind(state.stageProgressProperty());

        caption.getStyleClass().add("loading-caption");
        Runnable refreshCaption = () -> caption.setText(captionFor(state));
        state.completedStages().addListener((javafx.collections.ListChangeListener<String>) c -> refreshCaption.run());
        refreshCaption.run();

        elapsed.getStyleClass().add("loading-elapsed");

        getChildren().addAll(title, animation, bar, caption, elapsed);

        long startNanos = System.nanoTime();
        clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateElapsed(startNanos)));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
        updateElapsed(startNanos);

        // Neither the animation loop nor this clock stops just because the node is removed from
        // the scene graph (e.g. the phase leaves ANALYZING and this overlay is discarded) - both
        // would otherwise keep firing forever in the background.
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                clock.stop();
            }
        });
        animation.play();
    }

    private void updateElapsed(long startNanos) {
        long seconds = (System.nanoTime() - startNanos) / 1_000_000_000L;
        StringBuilder text = new StringBuilder(seconds < 60
                ? seconds + "s elapsed"
                : (seconds / 60) + "m " + (seconds % 60) + "s elapsed");
        if (seconds >= REASSURANCE_AFTER_SECONDS) {
            text.append(" — larger or more complex documents can take a little longer.");
        }
        elapsed.setText(text.toString());
    }

    private static String captionFor(AppState state) {
        String next = AppState.STAGES.stream()
                .filter(s -> !state.completedStages().contains(s))
                .findFirst()
                .orElse(null);
        if (next == null) {
            return "Putting the report together…";
        }
        return STAGE_CAPTIONS.getOrDefault(next, "Working…");
    }

    private static String safe(String name) {
        return name == null || name.isBlank() ? "your document" : "“" + name + "”";
    }
}
