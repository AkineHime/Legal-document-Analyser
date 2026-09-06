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

package io.statigate.advisory.jlama;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.model.functions.Generator;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.SafeTensorSupport;
import com.github.tjake.jlama.safetensors.prompt.PromptContext;
import io.statigate.advisory.LlmClient;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link LlmClient} backed by JLama - a pure-JVM LLM inference engine. Runs the model in this
 * process on CPU; no daemon, no socket.
 *
 * <p>Requires Java 21+ launched with {@code --enable-preview --add-modules jdk.incubator.vector
 * --enable-native-access=ALL-UNNAMED}. Construction fails fast with that guidance if the runtime is
 * not set up for it.
 */
public final class JlamaLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(JlamaLlmClient.class);
    private static final float TEMPERATURE = 0.2f;

    private final AbstractModel model;
    private final String modelId;

    /** Loads a model from a local directory (already downloaded). */
    public JlamaLlmClient(Path modelDir) {
        if (!Files.isDirectory(modelDir)) {
            throw new IllegalArgumentException("JLama model directory not found: " + modelDir
                    + " (run scripts/download_jlama_model.* first)");
        }
        try {
            this.model = ModelSupport.loadModel(modelDir.toFile(), DType.F32, DType.I8);
            this.modelId = "jlama:" + modelDir.getFileName();
            log.info("Loaded JLama model {} ({})", modelId, model.getClass().getSimpleName());
        } catch (UnsupportedOperationException | LinkageError e) {
            throw new IllegalStateException(
                    "JLama failed to initialize. Launch the JVM with: --enable-preview "
                            + "--add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED",
                    e);
        }
    }

    /** One-time: fetch a model from Hugging Face into {@code targetDir}. Network call - setup only. */
    public static Path download(String hfOwnerName, Path targetDir) {
        try {
            Files.createDirectories(targetDir);
            File dir = SafeTensorSupport.maybeDownloadModel(targetDir.toString(), hfOwnerName);
            return dir.toPath();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to download " + hfOwnerName, e);
        }
    }

    @Override
    public String generate(String system, String user, int maxNewTokens) {
        PromptContext ctx = model.promptSupport()
                .map(ps -> ps.builder()
                        .addSystemMessage(system)
                        .addUserMessage(user)
                        .build())
                .orElseGet(() -> PromptContext.of(system + "\n\n" + user));
        Generator.Response resp = model.generate(
                UUID.randomUUID(), ctx, TEMPERATURE, maxNewTokens, (tok, t) -> { });
        return resp.responseText == null ? "" : resp.responseText.strip();
    }

    @Override
    public String modelId() {
        return modelId;
    }

    @Override
    public boolean inProcess() {
        return true;
    }

    @Override
    public void close() {
        try {
            model.close();
        } catch (Exception e) {
            log.warn("Error closing JLama model: {}", e.toString());
        }
    }
}
