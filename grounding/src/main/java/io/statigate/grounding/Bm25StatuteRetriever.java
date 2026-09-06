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

import java.util.List;

/** BM25 provision scorer over heading + summary + topic tags. */
final class Bm25StatuteRetriever implements ProvisionScorer {

    private final Bm25Index index;

    Bm25StatuteRetriever(StatuteCorpus corpus) {
        this.index = new Bm25Index(
                corpus.provisions().stream().map(StatuteProvision::searchText).toList());
    }

    @Override
    public double[] scoreAll(String clauseText) {
        return index.scores(clauseText);
    }

    @Override
    public String name() {
        return "bm25";
    }
}
