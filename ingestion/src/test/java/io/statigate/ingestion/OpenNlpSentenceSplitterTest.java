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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.statigate.core.Sentence;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenNlpSentenceSplitterTest {

    private final SentenceSplitter splitter = new OpenNlpSentenceSplitter();

    @Test
    void splitsAKnownParagraphIntoThreeSentences() {
        String text = "This Service Agreement is entered into between Acme Technologies "
                + "Private Limited and Beta Ventures LLP. The Company shall pay fees of "
                + "INR 5,00,000 per month. This Agreement shall be governed by the laws of India.";
        List<Sentence> sentences = splitter.split(text);
        assertEquals(3, sentences.size());
    }

    @Test
    void sentenceSpansSliceBackToOriginalText() {
        String text = "Clause one is short. Clause two is also short. Clause three ends here.";
        List<Sentence> sentences = splitter.split(text);
        for (Sentence s : sentences) {
            assertEquals(s.text(), text.substring(s.span().start(), s.span().end()));
        }
        assertTrue(sentences.size() >= 3);
    }

    @Test
    void emptyTextYieldsNoSentences() {
        assertTrue(splitter.split("").isEmpty());
        assertTrue(splitter.split("   ").isEmpty());
    }
}
