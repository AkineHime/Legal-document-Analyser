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

package io.statigate.advisory;

import io.statigate.core.Advice;
import io.statigate.core.Citation;
import io.statigate.core.Clause;
import io.statigate.core.RiskFlag;
import io.statigate.core.StatuteRef;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses a local LLM to rephrase the extractive advice into more fluent plain language, under hard
 * guardrails:
 *
 * <ul>
 *   <li>the prompt contains only the clause text, the rule-derived rationale, and the retrieved
 *       statute summaries - nothing else;</li>
 *   <li>the output is rejected if it cites a "Section N" that was not in the prompt, introduces a
 *       number (date, amount, address, period) not present in the source material, runs
 *       substantially longer than the deterministic text, or is empty;</li>
 *   <li>on any rejection the deterministic {@link ExtractiveAdvisoryService} output is used instead.</li>
 * </ul>
 *
 * <p><b>Experimental.</b> Rephrasing quality depends entirely on the local model; small models
 * (&lt;3B) tend to invent specifics and are caught by the guardrail, so most output falls back to
 * the deterministic engine. The extractive backend remains the recommended default.
 */
public final class LlmAdvisoryService implements AdvisoryService {

    private static final Logger log = LoggerFactory.getLogger(LlmAdvisoryService.class);
    private static final Pattern SECTION_REF = Pattern.compile("\\bSection\\s+\\d+[A-Z]?", Pattern.CASE_INSENSITIVE);
    private static final int MAX_NEW_TOKENS = 160;
    private static final int CLAUSE_CHARS = 700;
    private static final int STATUTE_NOTE_CHARS = 180;

    private static final String SYSTEM = """
            Rewrite the note below in plain language for a non-lawyer, staying strictly within the
            given clause text and statute notes. Do NOT add any name, company, address, date,
            amount, time period or section number that is not already written in the material you
            were given. Do not give a verdict. Two or three sentences. End with: This is not legal advice.""";

    private final LlmClient llm;
    private final ExtractiveAdvisoryService fallback;

    public LlmAdvisoryService(LlmClient llm) {
        this.llm = llm;
        this.fallback = new ExtractiveAdvisoryService();
    }

    @Override
    public String backend() {
        return llm.modelId() + (llm.inProcess() ? " (in-process)" : "");
    }

    @Override
    public List<Advice> adviseClause(ClauseAdvisoryInput input) {
        List<Advice> base = fallback.adviseClause(input);
        List<Advice> out = new ArrayList<>(base.size());
        for (Advice deterministic : base) {
            out.add(tryRephrase(input, deterministic));
        }
        return List.copyOf(out);
    }

    private Advice tryRephrase(ClauseAdvisoryInput input, Advice deterministic) {
        Clause clause = input.clause();
        String allowed = allowedSectionText(input);
        String user = """
                Clause text:
                %s

                What to convey:
                %s

                Statute notes (the only law you may mention):
                %s
                """.formatted(
                truncate(clause.text(), CLAUSE_CHARS),
                truncate(deterministic.body(), 500),
                allowed.isBlank() ? "(none)" : allowed);

        String raw;
        try {
            raw = llm.generate(SYSTEM, user, MAX_NEW_TOKENS).strip();
        } catch (RuntimeException e) {
            log.warn("LLM generation failed, using deterministic advice: {}", e.toString());
            return deterministic;
        }

        String source = (clause.text() + " " + deterministic.body() + " " + allowed).toLowerCase();
        if (!isAcceptable(raw, allowed, deterministic.body(), source)) {
            log.debug("LLM output rejected by guardrail for clause {}", clause.id());
            return deterministic;
        }
        return new Advice(deterministic.headline(), raw, deterministic.citations());
    }

    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,./-]*");

    static boolean isAcceptable(String text, String allowedSections, String deterministicBody,
            String sourceLower) {
        if (text.length() < 40 || text.length() > Math.max(700, deterministicBody.length() * 3 / 2 + 200)) {
            return false;
        }
        Matcher sec = SECTION_REF.matcher(text);
        while (sec.find()) {
            if (!allowedSections.toLowerCase().contains(sec.group().toLowerCase())) {
                return false; // invented a section number
            }
        }
        Matcher num = NUMBER.matcher(text);
        while (num.find()) {
            String digits = num.group().replaceAll("[^\\d]", "");
            if (digits.length() >= 2 && !sourceLower.contains(num.group().toLowerCase())
                    && !sourceLower.replaceAll("[^\\d]", " ").contains(digits)) {
                return false; // invented a date, amount, address or period
            }
        }
        return true;
    }

    private static String allowedSectionText(ClauseAdvisoryInput input) {
        StringBuilder sb = new StringBuilder();
        for (StatuteRef s : input.statutes()) {
            sb.append(s.act()).append(", ").append(s.provision()).append(": ")
                    .append(truncate(s.snippet(), STATUTE_NOTE_CHARS)).append('\n');
        }
        return sb.toString().strip();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + " ...";
    }
}
