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

import io.statigate.core.StatuteRef;
import io.statigate.desktop.format.Labels;
import io.statigate.desktop.format.TextSanitizer;
import io.statigate.grounding.GroundingMatch;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

/**
 * A small pill naming one Indian statute provision, with the full citation text available on
 * hover. Accepts either a {@link GroundingMatch} (from clause-to-statute retrieval, which also
 * carries a relevance score) or a plain {@link StatuteRef} (as carried by {@link
 * io.statigate.core.Citation}, which does not) through the shared {@link Info} model, so the report
 * view does not need two near-identical chip classes.
 */
public final class StatuteChip extends HBox {

    /** The fields a chip needs, independent of which pipeline type they came from. */
    public record Info(String act, String provision, String citationText, Double score, String method) {
    }

    public StatuteChip(Info info) {
        getStyleClass().add("statute-chip");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(4);

        String short_ = shorten(info.act()) + " · " + info.provision();
        Label label = new Label(short_);
        label.getStyleClass().add("statute-chip-text");
        getChildren().add(label);

        StringBuilder tooltipText = new StringBuilder();
        tooltipText.append(info.act()).append(", ").append(info.provision());
        if (info.method() != null) {
            tooltipText.append("  (").append(Labels.groundingMethod(info.method())).append(")");
        }
        tooltipText.append("\n\n").append(TextSanitizer.truncate(info.citationText(), 600));
        Tooltip tooltip = new Tooltip(tooltipText.toString());
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(340);
        Tooltip.install(this, tooltip);
    }

    public static Info from(GroundingMatch match) {
        var p = match.provision();
        return new Info(p.act(), p.provision(), p.citationText(), match.score(), match.method());
    }

    public static Info from(StatuteRef ref) {
        return new Info(ref.act(), ref.provision(), ref.snippet(), null, null);
    }

    private static String shorten(String act) {
        // "Indian Contract Act, 1872" -> unchanged (already short); this guards against a future,
        // longer act name in the corpus making the chip unreasonably wide.
        return TextSanitizer.truncate(act, 42);
    }
}
