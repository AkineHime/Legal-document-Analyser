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

package io.statigate.extraction;

import java.util.List;

/**
 * Outcome of {@link OnnxRuntimeProbe}: proof that the ONNX Runtime native libraries resolved and
 * loaded on this machine's OS/architecture.
 *
 * @param nativeLoaded    true if the native environment was created and a session-options handle allocated
 * @param ortVersion      ONNX Runtime version string, or {@code "unknown"}
 * @param osName          {@code os.name} of the running JVM
 * @param osArch          {@code os.arch} of the running JVM
 * @param nativeArchLabel the ONNX Runtime native classifier expected for this platform, e.g. {@code win-x64}
 * @param providers       execution providers available in this build, e.g. {@code [CPU]}
 * @param detail          human-readable summary line
 */
public record OnnxProbeResult(
        boolean nativeLoaded,
        String ortVersion,
        String osName,
        String osArch,
        String nativeArchLabel,
        List<String> providers,
        String detail) {

    public OnnxProbeResult {
        providers = List.copyOf(providers == null ? List.of() : providers);
    }
}
