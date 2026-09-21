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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.statigate.core.Document;
import io.statigate.desktop.FxTestSupport;
import io.statigate.desktop.library.LibraryStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import io.statigate.pipeline.PipelineResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Builds {@link LibraryPane} (and, through it, {@link DocumentCard}) against a real, populated
 * {@link LibraryStore} to catch view-layer bugs (date formatting, severity-to-style mapping, the
 * search filter) that the pure-logic {@code LibraryStoreTest} cannot see.
 */
class LibraryPaneSmokeTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsAndFiltersWithoutThrowing() throws Exception {
        Assumptions.assumeTrue(FxTestSupport.available(), "JavaFX toolkit unavailable in this environment");

        LibraryStore store = new LibraryStore(tempDir.resolve("library"));
        store.save("service-agreement.pdf", null, "pdf", false, emptyResult());
        store.save("nda.txt", null, "txt", false, emptyResult());

        SimpleStringProperty query = new SimpleStringProperty("");

        CompletableFuture<Object> outcome = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                LibraryPane pane = new LibraryPane(store, query, entry -> { });
                pane.applyCss();
                pane.layout();
                int allCount = gridChildCount(pane);

                query.set("nda");
                pane.applyCss();
                pane.layout();
                int filteredCount = gridChildCount(pane);

                outcome.complete(new int[] { allCount, filteredCount });
            } catch (Throwable t) {
                outcome.complete(t);
            }
        });

        Object result = outcome.get(10, TimeUnit.SECONDS);
        assertNull(result instanceof Throwable ? (Throwable) result : null,
                "building/filtering the library pane must not throw: " + result);
        int[] counts = (int[]) result;
        assertEquals(2, counts[0], "both saved documents should appear unfiltered");
        assertEquals(1, counts[1], "only the matching document should remain after searching \"nda\"");
    }

    /** {@code LibraryPane}'s second child is the grid host (a StackPane wrapping either the
     * FlowPane grid or an EmptyState); this reaches into it the same way a real layout pass would. */
    private static int gridChildCount(LibraryPane pane) {
        StackPane gridHost = (StackPane) pane.getChildren().get(1);
        Object content = gridHost.getChildren().get(0);
        return content instanceof FlowPane grid ? grid.getChildren().size() : 0;
    }

    private static PipelineResult emptyResult() {
        Document document = new Document("/tmp/doc.txt", "text/plain", 1, "text", List.of());
        return new PipelineResult(document, List.of(), List.of(), List.of(), List.of(),
                Map.of("total", 1L), Map.of());
    }
}
