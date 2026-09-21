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

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * A purely decorative loop shown while an analysis runs: a few pages fly in from the left into a
 * small monitor. It carries no information (see {@link LoadingOverlay}'s progress bar and caption
 * for that) - it exists to make an otherwise static wait feel alive.
 *
 * <p>Stops itself automatically when removed from the scene (e.g. once the phase leaves {@code
 * ANALYZING} and this overlay is swapped out): a running {@link javafx.animation.Animation} is not
 * implicitly stopped just because its target node is detached, so leaving that to chance would leak
 * a timer that fires forever.
 */
public final class LoadingAnimation extends Pane {

    private static final double WIDTH = 280;
    private static final double HEIGHT = 90;
    private static final int PAPER_COUNT = 3;

    private final SequentialTransition loop;

    public LoadingAnimation() {
        setPrefSize(WIDTH, HEIGHT);
        setMinSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);

        StackPane monitor = new StackPane(Icons.monitor(34));
        monitor.setLayoutX(WIDTH / 2 - 17);
        monitor.setLayoutY(HEIGHT / 2 - 24);
        getChildren().add(monitor);

        ParallelTransition papersConverging = new ParallelTransition();
        for (int i = 0; i < PAPER_COUNT; i++) {
            Node paper = Icons.document(20);
            double startY = HEIGHT / 2 - 10 + (i - 1) * 14;
            paper.setLayoutX(-24);
            paper.setLayoutY(startY);
            getChildren().add(paper);

            TranslateTransition move = new TranslateTransition(Duration.seconds(0.9), paper);
            move.setToX(WIDTH / 2 - 24 - paper.getLayoutX());
            move.setToY((HEIGHT / 2 - 12) - startY);
            move.setInterpolator(javafx.animation.Interpolator.EASE_IN);

            FadeTransition fade = new FadeTransition(Duration.seconds(0.9), paper);
            fade.setFromValue(1.0);
            fade.setToValue(0.15);

            ScaleTransition shrink = new ScaleTransition(Duration.seconds(0.9), paper);
            shrink.setToX(0.35);
            shrink.setToY(0.35);

            ParallelTransition onePaper = new ParallelTransition(paper, move, fade, shrink);
            onePaper.setDelay(Duration.seconds(i * 0.18));
            papersConverging.getChildren().add(onePaper);
        }

        // Reset every animated node's transient state (position/opacity/scale) between loops,
        // since transitions leave a node at its final value rather than rewinding it.
        var reset = new PauseTransition(Duration.ZERO);
        reset.setOnFinished(e -> resetChildren());

        loop = new SequentialTransition(papersConverging, new PauseTransition(Duration.seconds(0.5)), reset);
        loop.setCycleCount(SequentialTransition.INDEFINITE);

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                loop.stop();
            }
        });
    }

    private void resetChildren() {
        for (Node child : getChildren()) {
            child.setTranslateX(0);
            child.setTranslateY(0);
            child.setScaleX(1);
            child.setScaleY(1);
            child.setRotate(0);
        }
    }

    public void play() {
        loop.playFromStart();
    }
}
