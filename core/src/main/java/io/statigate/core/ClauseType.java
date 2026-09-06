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

import java.util.List;

/**
 * The clause types the analysis pipeline recognizes in Indian commercial contracts. String
 * constants (not an enum) so a model can emit a type outside this set without a code change; these
 * are the ones the risk and advisory layers reason about.
 */
public final class ClauseType {

    public static final String INDEMNITY = "INDEMNITY";
    public static final String LIMITATION_OF_LIABILITY = "LIMITATION_OF_LIABILITY";
    public static final String TERMINATION = "TERMINATION";
    public static final String RENEWAL = "RENEWAL";
    public static final String PAYMENT = "PAYMENT";
    public static final String CONFIDENTIALITY = "CONFIDENTIALITY";
    public static final String GOVERNING_LAW = "GOVERNING_LAW";
    public static final String DISPUTE_RESOLUTION = "DISPUTE_RESOLUTION";
    public static final String ARBITRATION = "ARBITRATION";
    public static final String JURISDICTION = "JURISDICTION";
    public static final String FORCE_MAJEURE = "FORCE_MAJEURE";
    public static final String INTELLECTUAL_PROPERTY = "INTELLECTUAL_PROPERTY";
    public static final String DATA_PROTECTION = "DATA_PROTECTION";
    public static final String NON_COMPETE = "NON_COMPETE";
    public static final String NON_SOLICITATION = "NON_SOLICITATION";
    public static final String WARRANTY = "WARRANTY";
    public static final String ASSIGNMENT = "ASSIGNMENT";
    public static final String NOTICES = "NOTICES";
    public static final String ENTIRE_AGREEMENT = "ENTIRE_AGREEMENT";
    public static final String AMENDMENT = "AMENDMENT";
    public static final String SEVERABILITY = "SEVERABILITY";
    public static final String SCOPE_OF_WORK = "SCOPE_OF_WORK";
    public static final String PARTIES = "PARTIES";

    public static final List<String> ALL = List.of(
            INDEMNITY, LIMITATION_OF_LIABILITY, TERMINATION, RENEWAL, PAYMENT, CONFIDENTIALITY,
            GOVERNING_LAW, DISPUTE_RESOLUTION, ARBITRATION, JURISDICTION, FORCE_MAJEURE,
            INTELLECTUAL_PROPERTY, DATA_PROTECTION, NON_COMPETE, NON_SOLICITATION, WARRANTY,
            ASSIGNMENT, NOTICES, ENTIRE_AGREEMENT, AMENDMENT, SEVERABILITY, SCOPE_OF_WORK, PARTIES);

    private ClauseType() {
    }
}
