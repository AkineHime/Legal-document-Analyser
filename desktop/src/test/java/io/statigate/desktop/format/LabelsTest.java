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

package io.statigate.desktop.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.statigate.core.Clause;
import io.statigate.core.ClauseType;
import io.statigate.core.EntityType;
import io.statigate.core.Severity;
import org.junit.jupiter.api.Test;

class LabelsTest {

    @Test
    void clauseTypeHumanizesKnownConstants() {
        assertEquals("Indemnity", Labels.clauseType(ClauseType.INDEMNITY));
        assertEquals("Non compete", Labels.clauseType(ClauseType.NON_COMPETE));
        assertEquals("Intellectual property", Labels.clauseType(ClauseType.INTELLECTUAL_PROPERTY));
    }

    @Test
    void clauseTypeHandlesUnclassifiedAndNullGracefully() {
        assertEquals("Unclassified", Labels.clauseType(Clause.UNCLASSIFIED));
        assertEquals("Unclassified", Labels.clauseType(null));
        assertEquals("Unclassified", Labels.clauseType(""));
    }

    @Test
    void clauseTypeDegradesGracefullyForAnUnknownFutureType() {
        // ClauseType is deliberately an open string set; a future model may emit something new.
        assertEquals("Some brand new clause", Labels.clauseType("SOME_BRAND_NEW_CLAUSE"));
    }

    @Test
    void entityTypeHumanizesKnownConstants() {
        assertEquals("Effective date", Labels.entityType(EntityType.EFFECTIVE_DATE));
        assertEquals("Monetary value", Labels.entityType(EntityType.MONETARY_VALUE));
        assertEquals("Other", Labels.entityType(null));
    }

    @Test
    void riskCategoryHandlesHyphenAndUnderscoreForms() {
        assertEquals("One sided indemnity", Labels.riskCategory("one-sided-indemnity"));
        assertEquals("Auto renewal", Labels.riskCategory("auto_renewal"));
        assertEquals("Flagged clause", Labels.riskCategory(null));
    }

    @Test
    void severityLabelsAreTitleCase() {
        assertEquals("Low", Labels.severity(Severity.LOW));
        assertEquals("Medium", Labels.severity(Severity.MEDIUM));
        assertEquals("High", Labels.severity(Severity.HIGH));
        assertEquals("Unrated", Labels.severity(null));
    }

    @Test
    void groundingMethodMapsKnownBackends() {
        assertEquals("semantic match", Labels.groundingMethod("inlegalbert"));
        assertEquals("keyword match", Labels.groundingMethod("bm25"));
        assertEquals("combined match", Labels.groundingMethod("hybrid"));
        assertEquals("matched", Labels.groundingMethod(null));
    }
}
