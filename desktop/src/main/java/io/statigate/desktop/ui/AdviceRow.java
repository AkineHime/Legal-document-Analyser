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

import io.statigate.core.Advice;
import io.statigate.desktop.format.TextSanitizer;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * One plain-language advisory note. {@code Advice} can only be constructed with at least one
 * citation (enforced by the domain type itself), so every note this renders is, by construction,
 * traceable to a span of the analyzed document - this view does not need to (and does not) check
 * that itself.
 */
public final class AdviceRow extends VBox {

    private static final int MAX_BODY_CHARS = 500;

    public AdviceRow(Advice advice) {
        setSpacing(3);

        Label headline = new Label(TextSanitizer.truncate(advice.headline(), 140));
        headline.getStyleClass().add("clause-card-type");
        headline.setWrapText(true);

        Label body = new Label(TextSanitizer.truncate(advice.body(), MAX_BODY_CHARS));
        body.getStyleClass().add("clause-card-text");
        body.setWrapText(true);

        getChildren().addAll(headline, body);
    }
}
