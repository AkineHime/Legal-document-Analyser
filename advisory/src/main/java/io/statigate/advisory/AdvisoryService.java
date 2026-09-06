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

import io.statigate.core.Advice;
import java.util.List;

/**
 * Produces grounded, plain-language advisory notes for a clause. Every {@link Advice} an
 * implementation returns must be constructed with citations - the record enforces it - so no
 * implementation can emit ungrounded output.
 */
public interface AdvisoryService {

    List<Advice> adviseClause(ClauseAdvisoryInput input);

    /** Identifier of the backend, e.g. {@code "extractive"} or {@code "jlama:..."}. */
    String backend();
}
