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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OnnxRuntimeProbeTest {

    @Test
    void nativeLibrariesLoadOnThisMachine() {
        OnnxProbeResult r = new OnnxRuntimeProbe().probe();
        assertTrue(r.nativeLoaded(), () -> "ONNX Runtime natives failed to load: " + r.detail());
        assertFalse(r.ortVersion().isBlank());
        assertFalse(r.providers().isEmpty(), "expected at least the CPU execution provider");
    }

    @Test
    void archLabelMapping() {
        assertEquals("win-x64", OnnxRuntimeProbe.nativeArchLabel("Windows 11", "amd64"));
        assertEquals("linux-x64", OnnxRuntimeProbe.nativeArchLabel("Linux", "x86_64"));
        assertEquals("osx-aarch64", OnnxRuntimeProbe.nativeArchLabel("Mac OS X", "aarch64"));
    }
}
