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

package io.statigate.desktop.validation;

/**
 * A user-facing, already-friendly reason a candidate file was rejected before analysis. Deliberately
 * unchecked: validation happens on the FX Application Thread in response to a user action (a chosen
 * or dropped file), where a checked exception would only add boilerplate.
 */
public final class FileValidationException extends RuntimeException {

    public FileValidationException(String message) {
        super(message);
    }
}
