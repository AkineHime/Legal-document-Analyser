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

import io.statigate.core.Sentence;
import io.statigate.core.SourceSpan;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.Span;

/**
 * Sentence segmentation backed by an Apache OpenNLP maximum-entropy model.
 *
 * <p>The model ({@value #MODEL_RESOURCE}) is bundled on the classpath, so this works offline with
 * no download at runtime. The detector is not thread-safe; construct one per thread or synchronize.
 */
public final class OpenNlpSentenceSplitter implements SentenceSplitter {

    static final String MODEL_RESOURCE = "/models/opennlp-en-ud-ewt-sentence-1.3-2.5.4.bin";

    private final SentenceDetectorME detector;

    public OpenNlpSentenceSplitter() {
        this.detector = new SentenceDetectorME(loadModel());
    }

    private static SentenceModel loadModel() {
        try (InputStream in = OpenNlpSentenceSplitter.class.getResourceAsStream(MODEL_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Bundled sentence model not found: " + MODEL_RESOURCE);
            }
            return new SentenceModel(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load sentence model " + MODEL_RESOURCE, e);
        }
    }

    @Override
    public List<Sentence> split(String cleanText) {
        if (cleanText == null || cleanText.isBlank()) {
            return List.of();
        }
        Span[] spans = detector.sentPosDetect(cleanText);
        List<Sentence> sentences = new ArrayList<>(spans.length);
        for (int i = 0; i < spans.length; i++) {
            Span s = spans[i];
            String text = cleanText.substring(s.getStart(), s.getEnd());
            if (text.isBlank()) {
                continue;
            }
            sentences.add(new Sentence(sentences.size(), text,
                    new SourceSpan(s.getStart(), s.getEnd(), 0)));
        }
        return List.copyOf(sentences);
    }
}
