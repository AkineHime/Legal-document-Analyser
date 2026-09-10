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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Exercised only when a JLama model is present under {@code ../models/llm/<name>} and the run opts
 * in with {@code -Dstatigate.test.jlama=true}. JLama's FFM path also needs {@code --enable-preview}
 * on Java 21, which is not on by default, so this is normally skipped. End-to-end verification of
 * the {@code --llm jlama} pipeline is done through the CLI (see README).
 *
 * <pre>mvn -pl advisory -am test -Dtest=JlamaLlmClientTest -Dsurefire.failIfNoSpecifiedTests=false \
 *   -Dstatigate.test.jlama=true \
 *   -Dsurefire.argLine="--enable-preview --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED"</pre>
 */
class JlamaLlmClientTest {

    private static Path modelDir() {
        Path llm = Path.of("..", "models", "llm").toAbsolutePath().normalize();
        if (!Files.isDirectory(llm)) {
            return null;
        }
        try (var s = Files.list(llm)) {
            return s.filter(Files::isDirectory)
                    .filter(d -> Files.isRegularFile(d.resolve("config.json")))
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    static boolean jlamaRunnable() {
        return Boolean.getBoolean("statigate.test.jlama") && modelDir() != null;
    }

    @Test
    @EnabledIf("jlamaRunnable")
    void loadsAndGeneratesGroundedText() {
        try (var client = new JlamaLlmClient(modelDir())) {
            assertTrue(client.inProcess());
            String out = client.generate(
                    "Rewrite in one plain sentence. Do not add facts. End with: This is not legal advice.",
                    "Note: an indemnity without a monetary cap leaves exposure open-ended.",
                    64);
            assertFalse(out.isBlank());
            assertFalse(out.matches("(?s).*\\bSection\\s+\\d+.*"), "must not invent a section number");
        }
    }
}
