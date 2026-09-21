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

import static org.junit.jupiter.api.Assertions.assertNull;

import io.statigate.desktop.FxTestSupport;
import io.statigate.desktop.state.AppState;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Scene;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Builds {@link LoadingOverlay} (and its nested {@link LoadingAnimation}) attached to a real
 * {@link Scene}, then detaches it - the path that must stop both the paper-flight loop and the
 * elapsed-time clock rather than leaking a timer that runs forever - to catch construction and
 * animation-wiring bugs a pure logic test cannot see.
 */
class LoadingOverlaySmokeTest {

    @Test
    void buildsPlaysAndDetachesWithoutThrowing() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.available(), "JavaFX toolkit unavailable in this environment");

        CompletableFuture<Throwable> outcome = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                AppState state = new AppState();
                state.startAnalyzing("contract.pdf");
                state.reportStageComplete("ingestion");

                LoadingOverlay overlay = new LoadingOverlay(state);
                Group root = new Group(overlay);
                new Scene(root, 400, 300); // attaches overlay to a real scene
                overlay.applyCss();
                overlay.layout();

                // Detach - must stop the animation/clock rather than leave them running unseen.
                root.getChildren().clear();

                outcome.complete(null);
            } catch (Throwable t) {
                outcome.complete(t);
            }
        });

        Throwable thrown = outcome.get(10, TimeUnit.SECONDS);
        assertNull(thrown, "LoadingOverlay must build, play, and detach without throwing: " + thrown);
    }
}
