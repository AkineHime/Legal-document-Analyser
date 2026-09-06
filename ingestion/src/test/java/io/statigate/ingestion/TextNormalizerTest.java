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

package io.statigate.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    @Test
    void undoesHyphenatedLineBreaks() {
        String raw = "The indem-\nnity clause is one-sided.";
        assertEquals("The indemnity clause is one-sided.", TextNormalizer.normalize(raw));
    }

    @Test
    void joinsWrappedLinesButKeepsParagraphBreaks() {
        String raw = "This Agreement is made\nbetween the parties.\n\nGoverning law is India.";
        String out = TextNormalizer.normalize(raw);
        assertTrue(out.contains("This Agreement is made between the parties."));
        assertTrue(out.contains("\n\nGoverning law is India."));
    }

    @Test
    void normalizesCarriageReturnsAndCollapsesSpaces() {
        String raw = "Clause 1:\r\n\r\n\r\nPayment    of   fees.";
        String out = TextNormalizer.normalize(raw);
        assertFalse(out.contains("\r"));
        assertTrue(out.contains("Payment of fees."));
        assertFalse(out.contains("\n\n\n"));
    }

    @Test
    void handlesEmptyInput() {
        assertEquals("", TextNormalizer.normalize(null));
        assertEquals("", TextNormalizer.normalize(""));
    }
}
