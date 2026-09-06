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

package io.statigate.advisory;

/**
 * A local, in-process text-generation model.
 *
 * <p>Contract: implementations run entirely within this JVM process with no network calls at
 * generation time. The intended implementation is JLama (pure-JVM inference).
 */
public interface LlmClient extends AutoCloseable {

    /**
     * @param system  system instruction
     * @param user    user message
     * @param maxNewTokens generation budget
     * @return the model's completion (text only)
     */
    String generate(String system, String user, int maxNewTokens);

    /** Short identifier of the backing model, e.g. {@code "jlama:Qwen2.5-0.5B-Instruct"}. */
    String modelId();

    /** True if this client runs fully in-process (JLama). */
    boolean inProcess();

    @Override
    default void close() {
    }
}
