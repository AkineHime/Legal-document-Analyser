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

package io.statigate.extraction;

import io.statigate.core.Document;
import io.statigate.core.Entity;
import io.statigate.core.EntityType;
import io.statigate.core.SourceSpan;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based extraction of the contract entity set (parties, dates, money, governing law,
 * jurisdiction, notice periods). Deterministic and explainable: every entity maps to an exact
 * span and the rule that found it, which matters for a system whose output must be auditable.
 */
public final class EntityExtractor {

    private record Rule(String type, Pattern pattern, int group) {
    }

    private static final String MONTHS =
            "(?:January|February|March|April|May|June|July|August|September|October|November|December)";

    private static final List<Rule> RULES = List.of(
            // Defined parties: a quoted short name near a corporate-form word, e.g. ("Service Provider")
            new Rule(EntityType.PARTY, Pattern.compile(
                    "\\(\"([A-Z][A-Za-z .&'-]{1,60}?)\"\\)"), 1),
            new Rule(EntityType.PARTY, Pattern.compile(
                    "\\b([A-Z][A-Za-z.&'-]+(?:\\s+[A-Z][A-Za-z.&'-]+){0,5}\\s+"
                            + "(?:Private\\s+Limited|Pvt\\.?\\s*Ltd\\.?|Limited|Ltd\\.?|LLP|LLC|Inc\\.?|"
                            + "Corporation|Company|Partnership|Trust|Foundation|Society))\\b"), 1),
            // Money: INR / Rs / rupees / ₹ amounts (Indian digit grouping included)
            new Rule(EntityType.MONETARY_VALUE, Pattern.compile(
                    "(?:(?:INR|Rs\\.?|₹)\\s?[0-9][0-9,]*(?:\\.[0-9]+)?"
                            + "|Rupees\\s+[A-Za-z ]+?(?:\\s+only|\\s+lakh[s]?|\\s+crore[s]?)?)"), 0),
            // Dates: "1 April 2025" and 01/04/2025
            new Rule(EntityType.EFFECTIVE_DATE, Pattern.compile(
                    "\\b(?:[0-3]?\\d(?:st|nd|rd|th)?\\s+" + MONTHS + "\\s+\\d{4}"
                            + "|" + MONTHS + "\\s+[0-3]?\\d,\\s+\\d{4}"
                            + "|[0-3]?\\d[/-][01]?\\d[/-]\\d{2,4})\\b"), 0),
            new Rule(EntityType.GOVERNING_LAW, Pattern.compile(
                    "governed by (?:and construed in accordance with )?the laws (?:of|in force in) "
                            + "([A-Z][A-Za-z ]+?)(?=[.,;]| and| whose)", Pattern.CASE_INSENSITIVE), 1),
            new Rule(EntityType.JURISDICTION, Pattern.compile(
                    "(?:courts?|tribunals?)\\s+(?:at|of|in|within)\\s+([A-Z][A-Za-z ]+?)\\s+shall have "
                            + "(?:exclusive\\s+)?jurisdiction", Pattern.CASE_INSENSITIVE), 1),
            new Rule(EntityType.JURISDICTION, Pattern.compile(
                    "seat (?:and venue )?of arbitration shall be ([A-Z][A-Za-z ]+?)(?=[.,; ]|and)",
                    Pattern.CASE_INSENSITIVE), 1),
            new Rule(EntityType.NOTICE_PERIOD, Pattern.compile(
                    "(?:(?:at least\\s+)?(?:\\d{1,3}|thirty|sixty|ninety|fifteen|forty-five|one hundred and eighty)"
                            + "\\s*(?:\\(\\d{1,3}\\)\\s*)?days?[’'\\s]*(?:prior\\s+)?(?:written\\s+)?notice)",
                    Pattern.CASE_INSENSITIVE), 0));

    private static final Set<String> NOT_A_PARTY = Set.of(
            "agreement", "the agreement", "this agreement", "effective date", "term", "services",
            "deliverables", "confidential information", "parties", "party", "company", "client",
            "service provider", "vendor", "buyer", "seller", "consultant", "employee", "employer");

    public List<Entity> extract(Document document) {
        String text = document.cleanText();
        List<Entity> entities = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(text);
            while (m.find()) {
                int start = rule.group() == 0 ? m.start() : m.start(rule.group());
                int end = rule.group() == 0 ? m.end() : m.end(rule.group());
                if (start < 0 || end <= start) {
                    continue;
                }
                String value = text.substring(start, end).strip();
                if (value.isBlank() || value.length() > 120) {
                    continue;
                }
                if (rule.type().equals(EntityType.PARTY)) {
                    value = value.replaceFirst(
                            "^(?i)(for|by|between|name|title|director|designated partner|"
                                    + "authorised signatory|per|witness)\\s+", "").strip();
                    // drop trailing signatory noise like "... LLP Name R. Iyer"
                    value = value.replaceFirst("(?i)\\s+(name|title|for)\\s+.*$", "").strip();
                    if (NOT_A_PARTY.contains(value.toLowerCase()) || value.split("\\s+").length > 8
                            || !value.matches(".*\\p{Ll}.*")) {
                        continue;
                    }
                }
                String key = rule.type() + "\u0000" + value.toLowerCase();
                if (!seen.add(key)) {
                    continue;
                }
                entities.add(new Entity(rule.type(), value,
                        new SourceSpan(start, end, document.pageOf(start)), 1.0));
            }
        }
        return List.copyOf(entities);
    }
}
