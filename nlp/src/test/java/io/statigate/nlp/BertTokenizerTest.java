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

package io.statigate.nlp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BertTokenizerTest {

    @Test
    void basicTokenizeSplitsPunctuationAndWhitespace() {
        List<String> toks = BertTokenizer.basicTokenize("Fees of INR 5,00,000 (Rupees Five Lakh).");
        assertTrue(toks.contains("("));
        assertTrue(toks.contains(")"));
        assertTrue(toks.contains(","));
        assertTrue(toks.contains("."));
        assertTrue(toks.contains("INR"));
    }

    @Test
    void basicTokenizeSplitsOnTabAndSpace() {
        assertEquals(List.of("a", "b", "c"), BertTokenizer.basicTokenize("a b\tc"));
    }

    @Test
    void basicTokenizeKeepsHyphenatedTokenAsSplitPieces() {
        // WordPiece punctuation-splitting keeps "-" as its own token (BERT convention).
        assertEquals(List.of("non", "-", "compete"), BertTokenizer.basicTokenize("non-compete"));
    }
}
