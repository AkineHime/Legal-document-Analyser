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

package io.statigate.grounding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The loaded local corpus of Indian statute provisions used for grounding. Read once from a bundled
 * JSON resource; no database, no network.
 */
public final class StatuteCorpus {

    private static final Logger log = LoggerFactory.getLogger(StatuteCorpus.class);
    private static final String DEFAULT_RESOURCE = "/statutes/indian_statutes.json";

    private final List<StatuteProvision> provisions;

    public StatuteCorpus(List<StatuteProvision> provisions) {
        this.provisions = List.copyOf(provisions == null ? List.of() : provisions);
    }

    public static StatuteCorpus loadDefault() {
        return loadResource(DEFAULT_RESOURCE);
    }

    public static StatuteCorpus loadResource(String resourcePath) {
        try (InputStream in = StatuteCorpus.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("statute corpus resource not found: " + resourcePath);
            }
            JsonNode root = new ObjectMapper().readTree(in);
            List<StatuteProvision> list = new ArrayList<>();
            for (JsonNode p : root.path("provisions")) {
                list.add(new StatuteProvision(
                        p.path("corpus_id").asText(),
                        p.path("act").asText(),
                        p.path("provision").asText(),
                        p.path("heading").asText(),
                        p.path("text").asText(),
                        stringList(p.path("topics")),
                        stringList(p.path("clause_types"))));
            }
            log.info("Loaded {} statute provisions from {}", list.size(), resourcePath);
            return new StatuteCorpus(list);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read statute corpus " + resourcePath, e);
        }
    }

    private static List<String> stringList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(n -> out.add(n.asText()));
        }
        return out;
    }

    public static StatuteCorpus empty() {
        return new StatuteCorpus(List.of());
    }

    public List<StatuteProvision> provisions() {
        return provisions;
    }

    public boolean isEmpty() {
        return provisions.isEmpty();
    }

    public int size() {
        return provisions.size();
    }
}
