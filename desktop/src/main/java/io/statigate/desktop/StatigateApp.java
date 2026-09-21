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

package io.statigate.desktop;

import io.statigate.desktop.engine.AnalysisEngine;
import io.statigate.desktop.engine.EngineSettings;
import io.statigate.desktop.library.LibraryStore;
import io.statigate.desktop.state.AppState;
import io.statigate.desktop.ui.MainView;
import io.statigate.desktop.validation.FileValidationService;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX entry point. Talks to the same {@link io.statigate.pipeline.AnalysisPipeline} the CLI
 * uses, entirely offline - the UI layer adds file validation, progress reporting, and presentation,
 * and nothing here ever opens a socket.
 */
public final class StatigateApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(StatigateApp.class);

    private AnalysisEngine engine;

    @Override
    public void start(Stage stage) {
        // Anything that escapes a background thread (including the analysis worker) without being
        // caught closer to its source lands here instead of being silently dropped.
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) ->
                log.error("Uncaught exception on thread '{}'", thread.getName(), ex));

        engine = new AnalysisEngine(EngineSettings.defaults());
        AppState state = new AppState();
        FileValidationService validator = new FileValidationService(AppConfig.MAX_UPLOAD_BYTES);
        LibraryStore library = openLibrary();

        MainView root = new MainView(state, engine, validator, library);
        Scene scene = new Scene(root, 1280, 860);
        URL theme = StatigateApp.class.getResource("theme.css");
        if (theme != null) {
            scene.getStylesheets().add(theme.toExternalForm());
        } else {
            log.warn("theme.css not found on the classpath; running with default JavaFX styling.");
        }

        stage.setTitle(AppConfig.APP_NAME);
        stage.setScene(scene);
        // No explicit setResizable(false) anywhere: the window is resizable by default, and the
        // whole layout (BorderPane sides + FlowPane wrapping + ScrollPane overflow) is built to
        // reflow rather than clip or overlap as it resizes.
        stage.setMinWidth(1000);
        stage.setMinHeight(680);
        stage.show();
    }

    /**
     * Opens the on-disk document library, falling back to a temp directory if the user's normal
     * app-data location can't be created or written (e.g. an unusual permissions setup) - so a rare
     * storage problem degrades to "this session's library won't persist across restarts" rather
     * than preventing the app from starting at all.
     */
    private LibraryStore openLibrary() {
        try {
            return new LibraryStore(LibraryStore.defaultRoot());
        } catch (IOException e) {
            log.warn("Could not open the document library at {}; using a temporary one for this "
                    + "session instead: {}", LibraryStore.defaultRoot(), e.toString());
            try {
                return new LibraryStore(Files.createTempDirectory("statigate-library"));
            } catch (IOException fallbackFailure) {
                // Both the user's own app-data directory AND the OS temp directory are unwritable -
                // a broken environment this application cannot reasonably work around. Fail loudly
                // at startup rather than limping on with no way to persist anything.
                throw new IllegalStateException("Could not initialize any document library", fallbackFailure);
            }
        }
    }

    /**
     * The JavaFX-documented shutdown hook: called once the last window is closed (or {@link
     * Platform#exit()} is invoked). This is where the analysis engine's background executor and
     * model runtime are released, rather than on a per-stage {@code setOnCloseRequest}, since it is
     * guaranteed to run exactly once regardless of how the application is asked to quit.
     */
    @Override
    public void stop() {
        if (engine != null) {
            engine.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
