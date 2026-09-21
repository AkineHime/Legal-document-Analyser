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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;

/**
 * Starts the JavaFX toolkit at most once per test JVM, for the handful of tests that exercise real
 * {@code javafx.concurrent.Task}/{@code Platform.runLater} behavior rather than pure logic classes.
 *
 * <p>Most of this module's tests deliberately avoid needing this at all (see the {@code format},
 * {@code validation}, and {@code state} packages) - reach for it only when a test genuinely needs a
 * running toolkit. On an environment with no display (a headless CI runner with no virtual
 * framebuffer), toolkit startup itself can fail; {@link #available()} reports that so such tests can
 * skip themselves via {@code Assumptions.assumeTrue} instead of failing the whole build.
 */
public final class FxTestSupport {

    private static final AtomicBoolean ATTEMPTED = new AtomicBoolean(false);
    private static volatile boolean available;

    public static synchronized boolean available() {
        if (ATTEMPTED.compareAndSet(false, true)) {
            CountDownLatch latch = new CountDownLatch(1);
            try {
                Platform.startup(latch::countDown);
                available = latch.await(10, TimeUnit.SECONDS);
            } catch (IllegalStateException alreadyStarted) {
                available = true; // another test class in this JVM already started it
            } catch (Throwable t) {
                available = false;
            }
        }
        return available;
    }

    private FxTestSupport() {
    }
}
