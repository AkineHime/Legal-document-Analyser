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

package io.statigate.desktop.state;

/** The one thing the whole UI shell needs to know to decide what to show. */
public enum AnalysisPhase {
    /** Nothing has happened yet, or the last result/error was dismissed. Show the drop zone. */
    IDLE,
    /** The pipeline is running on a background thread. Show progress. */
    ANALYZING,
    /** A report is ready. Show it. */
    COMPLETE,
    /** Validation or analysis failed. Show the reason and let the user try again. */
    FAILED
}
