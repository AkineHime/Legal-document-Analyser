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

import io.statigate.core.Severity;
import io.statigate.desktop.format.Labels;
import javafx.scene.control.Label;

/** A small colored pill for a {@link Severity}. Color comes entirely from CSS via the severity name. */
public final class SeverityBadge extends Label {

    public SeverityBadge(Severity severity) {
        super(Labels.severity(severity).toUpperCase(java.util.Locale.ROOT));
        getStyleClass().add("severity-badge");
        getStyleClass().add(severity == null ? "unrated" : severity.name().toLowerCase(java.util.Locale.ROOT));
    }
}
