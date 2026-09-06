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

package io.statigate.core;

/**
 * The contract-oriented entity types this system aims to extract. Kept as string constants rather
 * than an enum so that model-driven extraction can emit additional types without a code change;
 * these are the ones the advisory layer knows how to reason about.
 */
public final class EntityType {

    public static final String PARTY = "PARTY";
    public static final String EFFECTIVE_DATE = "EFFECTIVE_DATE";
    public static final String TERM_DURATION = "TERM_DURATION";
    public static final String MONETARY_VALUE = "MONETARY_VALUE";
    public static final String GOVERNING_LAW = "GOVERNING_LAW";
    public static final String JURISDICTION = "JURISDICTION";
    public static final String NOTICE_PERIOD = "NOTICE_PERIOD";

    private EntityType() {
    }
}
