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

import io.statigate.core.StatuteRef;
import java.util.List;

/**
 * One provision of an Indian statute in the local grounding corpus.
 *
 * @param corpusId    stable id, e.g. {@code "ica-1872-s27"}
 * @param act         act title, e.g. {@code "Indian Contract Act, 1872"}
 * @param provision   provision label, e.g. {@code "Section 27"}
 * @param heading     marginal heading
 * @param text        condensed editorial summary of the provision (not a verbatim quotation)
 * @param topics      free-text topic tags
 * @param clauseTypes clause types this provision commonly bears on
 */
public record StatuteProvision(
        String corpusId,
        String act,
        String provision,
        String heading,
        String text,
        List<String> topics,
        List<String> clauseTypes) {

    public StatuteProvision {
        topics = List.copyOf(topics == null ? List.of() : topics);
        clauseTypes = List.copyOf(clauseTypes == null ? List.of() : clauseTypes);
    }

    /** The searchable text: heading + body + topic tags. */
    public String searchText() {
        return heading + ". " + text + " " + String.join(" ", topics);
    }

    public StatuteRef toRef() {
        return new StatuteRef(act, provision, text, corpusId);
    }
}
