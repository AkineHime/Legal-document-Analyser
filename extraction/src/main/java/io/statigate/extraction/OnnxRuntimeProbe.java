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

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtProvider;
import ai.onnxruntime.OrtSession;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies that the ONNX Runtime Java API can load its native libraries on this machine.
 *
 * <p>This is the single most environment-sensitive dependency in the project (a JNI library with
 * per-OS/arch native binaries), and the module that proves the core research claim: a legal-domain
 * transformer running natively in the JVM. Getting it to resolve is Milestone 0's gate before any
 * model work in Milestone 2.
 */
public final class OnnxRuntimeProbe {

    private static final Logger log = LoggerFactory.getLogger(OnnxRuntimeProbe.class);

    public OnnxProbeResult probe() {
        String osName = System.getProperty("os.name", "unknown");
        String osArch = System.getProperty("os.arch", "unknown");
        String archLabel = nativeArchLabel(osName, osArch);

        try {
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            String version = ortVersion(env);
            List<String> providers = availableProviders();

            // Allocating session options exercises the native handle path end to end.
            try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
                opts.setInterOpNumThreads(1);
                opts.setIntraOpNumThreads(1);
            }

            String detail = "ONNX Runtime %s native libraries loaded for %s (os.name=%s, os.arch=%s); providers=%s"
                    .formatted(version, archLabel, osName, osArch, providers);
            log.info(detail);
            // Touch env so it is not flagged unused; the singleton stays open for the JVM lifetime.
            assert env != null;
            return new OnnxProbeResult(true, version, osName, osArch, archLabel, providers, detail);
        } catch (OrtException | RuntimeException | LinkageError e) {
            String detail = "ONNX Runtime native libraries FAILED to load for %s (os.name=%s, os.arch=%s): %s"
                    .formatted(archLabel, osName, osArch, e);
            log.error(detail, e);
            return new OnnxProbeResult(false, "unknown", osName, osArch, archLabel, List.of(), detail);
        }
    }

    private static String ortVersion(OrtEnvironment env) {
        // ONNX Runtime has exposed the version differently across releases (static getVersion(),
        // instance getVersion(), or only via toString()). Try each without assuming.
        for (Object target : new Object[] {null, env}) {
            try {
                Object v = OrtEnvironment.class.getMethod("getVersion").invoke(target);
                if (v != null && !v.toString().isBlank()) {
                    return v.toString();
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // try the next form
            }
        }
        var m = java.util.regex.Pattern.compile("version=([0-9][^,)\\s]*)").matcher(String.valueOf(env));
        if (m.find()) {
            return m.group(1);
        }
        Package p = OrtEnvironment.class.getPackage();
        String impl = p == null ? null : p.getImplementationVersion();
        return impl == null ? "unknown" : impl;
    }

    private static List<String> availableProviders() {
        Set<String> names = new TreeSet<>();
        try {
            for (OrtProvider provider : OrtEnvironment.getAvailableProviders()) {
                names.add(provider.name());
            }
        } catch (RuntimeException e) {
            log.warn("Could not enumerate ONNX Runtime providers: {}", e.toString());
        }
        return List.copyOf(names);
    }

    static String nativeArchLabel(String osName, String osArch) {
        String os = osName.toLowerCase(Locale.ROOT);
        String arch = osArch.toLowerCase(Locale.ROOT);
        String osPart;
        if (os.contains("win")) {
            osPart = "win";
        } else if (os.contains("mac") || os.contains("darwin") || os.contains("osx")) {
            osPart = "osx";
        } else {
            osPart = "linux";
        }
        String archPart = switch (arch) {
            case "amd64", "x86_64", "x64" -> "x64";
            case "aarch64", "arm64" -> "aarch64";
            case "x86", "i386", "i486", "i586", "i686" -> "x86";
            default -> arch;
        };
        // ONNX Runtime uses osx-x64 / osx-aarch64, win-x64, linux-x64, linux-aarch64.
        if (osPart.equals("win") && archPart.equals("aarch64")) {
            return "win-arm64";
        }
        return osPart + "-" + archPart;
    }
}
