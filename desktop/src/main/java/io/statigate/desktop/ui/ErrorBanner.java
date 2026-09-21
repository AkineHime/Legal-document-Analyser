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

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Shown when validation or analysis fails. The message is always the pipeline's own, already
 * user-facing text (see {@link io.statigate.desktop.validation.FileValidationException} and the
 * ingestion layer's own error messages) - never a raw stack trace. */
public final class ErrorBanner extends VBox {

    public ErrorBanner(String message, Runnable onTryAgain) {
        getStyleClass().add("error-banner");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(8);

        HBox titleRow = new HBox(8, Icons.alertTriangle(16), new Label("Couldn't finish that"));
        titleRow.setAlignment(Pos.CENTER_LEFT);
        ((Label) titleRow.getChildren().get(1)).getStyleClass().add("error-banner-title");

        Label body = new Label(message);
        body.getStyleClass().add("error-banner-body");
        body.setWrapText(true);

        getChildren().addAll(titleRow, body);

        if (onTryAgain != null) {
            Button retry = new Button("Try another file");
            retry.getStyleClass().add("error-banner-retry");
            retry.setOnAction(e -> onTryAgain.run());
            getChildren().add(retry);
        }
    }
}
