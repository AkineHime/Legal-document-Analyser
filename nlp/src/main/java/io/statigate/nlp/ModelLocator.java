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

package io.statigate.nlp;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the local directory that holds the exported model assets. Nothing is downloaded - the
 * models are produced once by {@code scripts/export_inlegalbert_onnx.py} and read from disk.
 *
 * <p>Resolution order: explicit constructor path, then {@code -Dstatigate.models=...}, then the
 * {@code STATIGATE_MODELS} environment variable, then {@code ./models}.
 */
public final class ModelLocator {

    public static final String PROP = "statigate.models";
    public static final String ENV = "STATIGATE_MODELS";

    private final Path modelsDir;

    public ModelLocator() {
        this(resolveDefault());
    }

    public ModelLocator(Path modelsDir) {
        this.modelsDir = modelsDir.toAbsolutePath().normalize();
    }

    private static Path resolveDefault() {
        String prop = System.getProperty(PROP);
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop);
        }
        String env = System.getenv(ENV);
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        return Path.of("models");
    }

    public Path modelsDir() {
        return modelsDir;
    }

    public Path inLegalBertDir() {
        return modelsDir.resolve("inlegalbert");
    }

    public Path inLegalBertOnnx() {
        Path int8 = inLegalBertDir().resolve("model.int8.onnx");
        return Files.isRegularFile(int8) ? int8 : inLegalBertDir().resolve("model.fp32.onnx");
    }

    public Path inLegalBertVocab() {
        return inLegalBertDir().resolve("vocab.txt");
    }

    /** True if the InLegalBERT ONNX model and vocabulary are present. */
    public boolean inLegalBertAvailable() {
        return Files.isRegularFile(inLegalBertOnnx()) && Files.isRegularFile(inLegalBertVocab());
    }
}
