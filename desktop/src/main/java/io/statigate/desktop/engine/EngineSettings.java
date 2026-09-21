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

package io.statigate.desktop.engine;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * User-adjustable engine configuration, mirroring the CLI's {@code --models} and {@code --threads}
 * flags. Immutable and compared by value so {@link AnalysisEngine} can tell whether a change
 * actually requires rebuilding the (expensive) model runtime.
 *
 * @param modelsDir explicit model asset directory, or {@code null} to use
 *                  {@link io.statigate.nlp.ModelLocator}'s own default resolution
 *                  ({@code -Dstatigate.models}, then {@code STATIGATE_MODELS}, then {@code ./models})
 * @param threads   ONNX Runtime intra-op thread count
 * @param useGpu    attempt NVIDIA CUDA acceleration for the encoder instead of CPU. Only takes
 *                  effect if this build was compiled with the {@code gpu} Maven profile and the
 *                  machine has a matching CUDA/cuDNN install; otherwise it is silently ignored
 *                  (logged, not thrown - see {@code OnnxTextEncoder}) and CPU is used as normal.
 */
public record EngineSettings(Path modelsDir, int threads, boolean useGpu) {

    public EngineSettings {
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be > 0, was " + threads);
        }
    }

    public static EngineSettings defaults() {
        return new EngineSettings(defaultModelsDir(),
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2), false);
    }

    /**
     * This module's own README says to run {@code mvn javafx:run} from inside {@code desktop/},
     * which makes the process's working directory {@code desktop/} - one level below the
     * repository root that {@code scripts/export_inlegalbert_onnx.py} actually exports the model
     * to. {@link io.statigate.nlp.ModelLocator}'s own default ({@code ./models}, relative to the
     * working directory) is correct for the CLI, which runs from the repository root, so it is
     * left alone; this only adds a sibling-directory fallback for when the desktop app specifically
     * is launched the way its README documents, without hardcoding either layout as "the" one.
     */
    private static Path defaultModelsDir() {
        if (Files.isRegularFile(Path.of("models", "inlegalbert", "model.int8.onnx"))) {
            return null; // ModelLocator's own "./models" default already finds it
        }
        Path sibling = Path.of("..", "models");
        if (Files.isRegularFile(sibling.resolve("inlegalbert").resolve("model.int8.onnx"))) {
            return sibling;
        }
        return null; // not exported anywhere findable - fall back to model-free, as before
    }
}
