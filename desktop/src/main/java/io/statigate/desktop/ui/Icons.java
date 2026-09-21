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

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;

/**
 * Small vector icons built from plain JavaFX shapes rather than an icon font or bundled image
 * assets. This keeps the whole application self-contained - nothing to download, nothing that can
 * fail to load at runtime, and no third-party icon set license to track - and every icon inherits
 * its color from the CSS {@code -fx-fill}/{@code -fx-stroke} of the {@code icon-shape} style class,
 * so a single theme change recolors all of them.
 *
 * <p>Every factory method returns a fresh {@link Node} sized to a {@code size x size} box; callers
 * are free to transform or restyle the result.
 */
public final class Icons {

    private Icons() {
    }

    public static Node menu(double size) {
        Group g = new Group();
        double w = size, gap = size / 3.4;
        for (int i = 0; i < 3; i++) {
            Line line = new Line(0, i * gap, w, i * gap);
            line.getStyleClass().add("icon-shape");
            line.setStrokeWidth(Math.max(1.4, size / 10));
            line.setStrokeLineCap(StrokeLineCap.ROUND);
            g.getChildren().add(line);
        }
        return g;
    }

    public static Node info(double size) {
        Group g = new Group();
        Circle ring = ring(size);
        Line stem = new Line(size / 2, size * 0.46, size / 2, size * 0.74);
        Circle dot = new Circle(size / 2, size * 0.28, Math.max(1.0, size / 14));
        for (Node n : new Node[] { stem, dot }) {
            n.getStyleClass().add("icon-shape");
        }
        stem.setStrokeWidth(Math.max(1.3, size / 11));
        stem.setStrokeLineCap(StrokeLineCap.ROUND);
        g.getChildren().addAll(ring, stem, dot);
        return g;
    }

    public static Node gear(double size) {
        Group g = new Group();
        double cx = size / 2, cy = size / 2;
        Circle body = ring(size * 0.62);
        body.setCenterX(cx);
        body.setCenterY(cy);
        // Solid hub dot: deliberately left with no explicit fill/stroke calls so the CSS
        // `-fx-fill` on `icon-shape` (not a Java-side setFill) is free to color it.
        Circle hub = new Circle(cx, cy, size * 0.09);
        hub.getStyleClass().add("icon-shape");
        g.getChildren().add(body);
        int teeth = 6;
        double toothLen = size * 0.14;
        double r = size * 0.31;
        for (int i = 0; i < teeth; i++) {
            double angle = Math.toRadians(i * (360.0 / teeth));
            double x1 = cx + Math.cos(angle) * r;
            double y1 = cy + Math.sin(angle) * r;
            double x2 = cx + Math.cos(angle) * (r + toothLen);
            double y2 = cy + Math.sin(angle) * (r + toothLen);
            Line tooth = new Line(x1, y1, x2, y2);
            tooth.getStyleClass().add("icon-shape");
            tooth.setStrokeWidth(Math.max(1.6, size / 8));
            tooth.setStrokeLineCap(StrokeLineCap.ROUND);
            g.getChildren().add(tooth);
        }
        g.getChildren().add(hub);
        return g;
    }

    /** A stack of "pages" with a folded corner, breaking through the hero much like the reference's product photo. */
    public static Node document(double size) {
        Group g = new Group();
        double w = size * 0.72, h = size;
        double x = (size - w) / 2;
        Rectangle back = new Rectangle(x + size * 0.08, size * 0.05, w, h * 0.9);
        back.setArcWidth(6);
        back.setArcHeight(6);
        back.getStyleClass().add("icon-shape");
        back.setOpacity(0.35);
        Rectangle front = new Rectangle(x, size * 0.02, w, h * 0.9);
        front.setArcWidth(6);
        front.setArcHeight(6);
        front.getStyleClass().add("icon-shape");
        front.setFill(javafx.scene.paint.Color.TRANSPARENT);
        front.setStrokeWidth(Math.max(1.4, size / 16));
        g.getChildren().addAll(back, front);
        for (int i = 0; i < 3; i++) {
            Line line = new Line(x + w * 0.18, h * 0.28 + i * h * 0.16, x + w * 0.82, h * 0.28 + i * h * 0.16);
            line.getStyleClass().add("icon-shape");
            line.setStrokeWidth(Math.max(1.0, size / 22));
            line.setOpacity(0.7);
            g.getChildren().add(line);
        }
        return g;
    }

    public static Node chevronDown(double size) {
        Polygon chevron = new Polygon(
                0.0, size * 0.32,
                size / 2, size * 0.72,
                size, size * 0.32,
                size, size * 0.12,
                size / 2, size * 0.52,
                0.0, size * 0.12);
        chevron.getStyleClass().add("icon-shape");
        return chevron;
    }

    public static Node arrowRight(double size) {
        Group g = new Group();
        Line shaft = new Line(0, size / 2, size * 0.7, size / 2);
        Polygon head = new Polygon(
                size * 0.55, size * 0.22,
                size, size / 2,
                size * 0.55, size * 0.78);
        shaft.getStyleClass().add("icon-shape");
        shaft.setStrokeWidth(Math.max(1.6, size / 8));
        shaft.setStrokeLineCap(StrokeLineCap.ROUND);
        head.getStyleClass().add("icon-shape");
        g.getChildren().addAll(shaft, head);
        return g;
    }

    public static Node alertTriangle(double size) {
        Group g = new Group();
        Polygon tri = new Polygon(
                size / 2, size * 0.06,
                size * 0.96, size * 0.92,
                size * 0.04, size * 0.92);
        tri.getStyleClass().add("icon-shape");
        tri.setFill(javafx.scene.paint.Color.TRANSPARENT);
        tri.setStrokeWidth(Math.max(1.5, size / 10));
        tri.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        Line stem = new Line(size / 2, size * 0.38, size / 2, size * 0.66);
        Circle dot = new Circle(size / 2, size * 0.78, Math.max(1.0, size / 16));
        stem.getStyleClass().add("icon-shape");
        stem.setStrokeWidth(Math.max(1.4, size / 11));
        stem.setStrokeLineCap(StrokeLineCap.ROUND);
        dot.getStyleClass().add("icon-shape");
        g.getChildren().addAll(tri, stem, dot);
        return g;
    }

    public static Node check(double size) {
        Polygon tick = new Polygon(
                size * 0.12, size * 0.52,
                size * 0.4, size * 0.78,
                size * 0.88, size * 0.2,
                size * 0.78, size * 0.12,
                size * 0.4, size * 0.56,
                size * 0.2, size * 0.4);
        tick.getStyleClass().add("icon-shape");
        return tick;
    }

    public static Node search(double size) {
        Group g = new Group();
        Circle lens = new Circle(size * 0.4, size * 0.4, size * 0.32);
        lens.getStyleClass().add("icon-shape");
        lens.setFill(javafx.scene.paint.Color.TRANSPARENT);
        lens.setStrokeWidth(Math.max(1.4, size / 10));
        Line handle = new Line(size * 0.65, size * 0.65, size * 0.95, size * 0.95);
        handle.getStyleClass().add("icon-shape");
        handle.setStrokeWidth(Math.max(1.6, size / 8));
        handle.setStrokeLineCap(StrokeLineCap.ROUND);
        g.getChildren().addAll(lens, handle);
        return g;
    }

    /** A simple grid of four tiles - "overview of everything at a glance". */
    public static Node dashboard(double size) {
        Group g = new Group();
        double gap = size * 0.12;
        double tile = (size - gap) / 2;
        double[][] pos = { { 0, 0 }, { tile + gap, 0 }, { 0, tile + gap }, { tile + gap, tile + gap } };
        for (double[] p : pos) {
            Rectangle r = new Rectangle(p[0], p[1], tile, tile);
            r.setArcWidth(4);
            r.setArcHeight(4);
            r.getStyleClass().add("icon-shape");
            g.getChildren().add(r);
        }
        return g;
    }

    /** A small stack of documents - the library of past analyses. */
    public static Node library(double size) {
        Group g = new Group();
        double w = size * 0.7, h = size * 0.82;
        for (int i = 0; i < 2; i++) {
            Rectangle back = new Rectangle(size * 0.14 + i * size * 0.06, size * 0.06 - i * size * 0.05, w, h);
            back.setArcWidth(5);
            back.setArcHeight(5);
            back.getStyleClass().add("icon-shape");
            back.setOpacity(i == 0 ? 1.0 : 0.5);
            back.setFill(javafx.scene.paint.Color.TRANSPARENT);
            back.setStrokeWidth(Math.max(1.2, size / 16));
            g.getChildren().add(back);
        }
        return g;
    }

    public static Node trash(double size) {
        Group g = new Group();
        Rectangle lid = new Rectangle(size * 0.18, size * 0.18, size * 0.64, size * 0.08);
        lid.getStyleClass().add("icon-shape");
        Rectangle body = new Rectangle(size * 0.24, size * 0.3, size * 0.52, size * 0.58);
        body.setArcWidth(4);
        body.setArcHeight(4);
        body.getStyleClass().add("icon-shape");
        body.setFill(javafx.scene.paint.Color.TRANSPARENT);
        body.setStrokeWidth(Math.max(1.3, size / 14));
        g.getChildren().addAll(lid, body);
        for (int i = 0; i < 2; i++) {
            Line line = new Line(size * (0.4 + i * 0.2), size * 0.4, size * (0.4 + i * 0.2), size * 0.78);
            line.getStyleClass().add("icon-shape");
            line.setStrokeWidth(Math.max(1.0, size / 20));
            g.getChildren().add(line);
        }
        return g;
    }

    /** A small desktop-monitor silhouette - the destination in {@link LoadingAnimation}. */
    public static Node monitor(double size) {
        Group g = new Group();
        double w = size, h = size * 0.68;
        Rectangle screen = new Rectangle(0, 0, w, h);
        screen.setArcWidth(6);
        screen.setArcHeight(6);
        screen.getStyleClass().add("icon-shape");
        screen.setFill(javafx.scene.paint.Color.TRANSPARENT);
        screen.setStrokeWidth(Math.max(1.4, size / 14));
        Line neck = new Line(w / 2, h, w / 2, h + size * 0.12);
        Line base = new Line(w * 0.3, h + size * 0.12, w * 0.7, h + size * 0.12);
        for (Line l : new Line[] { neck, base }) {
            l.getStyleClass().add("icon-shape");
            l.setStrokeWidth(Math.max(1.4, size / 14));
        }
        g.getChildren().addAll(screen, neck, base);
        return g;
    }

    private static Circle ring(double size) {
        Circle c = new Circle(size / 2, size / 2, size / 2 - 1);
        c.getStyleClass().add("icon-shape");
        c.setFill(javafx.scene.paint.Color.TRANSPARENT);
        c.setStrokeWidth(Math.max(1.4, size / 12));
        return c;
    }
}
