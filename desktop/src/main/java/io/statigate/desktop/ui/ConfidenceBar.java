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

import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * A small horizontal meter for a classifier confidence in {@code [0, 1]}.
 *
 * <p>{@code lowConfidenceThreshold} is a UI-only "worth a second look" heuristic for this
 * component, not a restatement of any classifier's internal acceptance cutoff - the extraction
 * module does not expose that as public API, and this control deliberately does not couple to it.
 */
public final class ConfidenceBar extends HBox {

    private static final double TRACK_WIDTH = 88;
    private static final double TRACK_HEIGHT = 6;

    public ConfidenceBar(double confidence, double lowConfidenceThreshold) {
        double clamped = Math.max(0, Math.min(1, confidence));

        Region track = new Region();
        track.getStyleClass().add("confidence-track");
        track.setPrefSize(TRACK_WIDTH, TRACK_HEIGHT);
        track.setMaxSize(TRACK_WIDTH, TRACK_HEIGHT);

        Region fill = new Region();
        fill.getStyleClass().add("confidence-fill");
        if (clamped < lowConfidenceThreshold) {
            fill.getStyleClass().add("low-confidence");
        }
        fill.setPrefSize(TRACK_WIDTH * clamped, TRACK_HEIGHT);
        fill.setMaxSize(TRACK_WIDTH * clamped, TRACK_HEIGHT);

        StackPane bar = new StackPane(track, fill);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);

        Label percent = new Label(String.format(Locale.ROOT, "%.0f%%", clamped * 100));
        percent.getStyleClass().add("confidence-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.NEVER);
        spacer.setPrefWidth(8);

        setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(bar, spacer, percent);
    }
}
