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

/**
 * Fixed, compile-time application configuration. Not user-editable settings (see
 * {@code engine.EngineSettings} for those) - just the constants a handful of classes would
 * otherwise each hardcode their own copy of.
 */
public final class AppConfig {

    public static final String APP_NAME = "Statigate";
    public static final String TAGLINE = "Insight";
    public static final String VOLUME_LABEL = "VOL. I · v0.1";

    /** Upload size ceiling enforced before a file ever reaches the pipeline. */
    public static final long MAX_UPLOAD_BYTES = 25L * 1024 * 1024;

    /** How many characters of a clause/snippet a card shows before offering "Show more". */
    public static final int SNIPPET_PREVIEW_CHARS = 260;

    private AppConfig() {
    }
}
