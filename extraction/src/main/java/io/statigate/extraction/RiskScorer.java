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

import io.statigate.core.Citation;
import io.statigate.core.Clause;
import io.statigate.core.ClauseType;
import io.statigate.core.RiskFlag;
import io.statigate.core.Severity;
import io.statigate.core.SourceSpan;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Explainable risk detection. Each flag names a category, gives a plain-language rationale, and
 * cites the exact clause span it is based on (statute grounding is added later by the grounding
 * module). Framing is advisory: a flag means "worth a closer look", not a legal conclusion.
 *
 * <p>Two rule families: per-clause rules keyed on the classified clause type, and document-level
 * rules that flag conventional protections that appear to be missing entirely.
 */
public final class RiskScorer {

    private static final Pattern UNCAPPED = Pattern.compile(
            "without any (?:monetary )?(?:cap|limit)|uncapped|no (?:monetary )?cap|"
                    + "regardless of the (?:cause|amount)|to the fullest extent", Pattern.CASE_INSENSITIVE);
    private static final Pattern MUTUAL_INDEMNITY = Pattern.compile(
            "each party|mutually|reciprocal", Pattern.CASE_INSENSITIVE);
    private static final Pattern FOR_CONVENIENCE = Pattern.compile(
            "for convenience|without cause|at any time (?:on|upon)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ONLY_MATERIAL_BREACH = Pattern.compile(
            "only for material breach|terminate only", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTO_RENEW = Pattern.compile(
            "automatically renew|renew automatically|shall renew for", Pattern.CASE_INSENSITIVE);
    private static final Pattern MONTHLY_INTEREST = Pattern.compile(
            "interest (?:at|of) (\\d+(?:\\.\\d+)?)\\s*(?:%|per\\s?cent|percent)\\s*(?:per|a)\\s*month",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LOW_CAP = Pattern.compile(
            "shall not exceed[^.]{0,60}?(?:one|1)\\s+month|not exceed the fees paid|"
                    + "limited to the (?:total )?fees", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOTICE_DAYS = Pattern.compile(
            "(\\d{1,3})\\s*(?:\\(\\d{1,3}\\)\\s*)?days", Pattern.CASE_INSENSITIVE);

    /** Per-clause flags plus whole-document flags for conventional protections that seem to be missing. */
    public record Assessment(List<RiskFlag> clauseFlags, List<RiskFlag> documentFlags) {
    }

    public Assessment score(List<Clause> classifiedClauses, String fullText) {
        List<RiskFlag> clauseFlags = new ArrayList<>();
        List<RiskFlag> documentFlags = new ArrayList<>();
        Set<String> present = new HashSet<>();
        for (Clause c : classifiedClauses) {
            present.add(c.type());
        }
        for (Clause c : classifiedClauses) {
            perClause(c, clauseFlags);
        }
        documentLevel(present, classifiedClauses, documentFlags);
        return new Assessment(List.copyOf(clauseFlags), List.copyOf(documentFlags));
    }

    private void perClause(Clause c, List<RiskFlag> flags) {
        String t = c.text();
        Citation cite = Citation.documentOnly(c.span(), t);
        switch (c.type()) {
            case ClauseType.INDEMNITY -> {
                if (UNCAPPED.matcher(t).find()) {
                    flags.add(new RiskFlag(Severity.HIGH, "uncapped-indemnity",
                            "This indemnity is expressed without a monetary cap, so potential exposure "
                                    + "is open-ended. A liability cap or a carve-out is worth negotiating.",
                            cite));
                } else if (!MUTUAL_INDEMNITY.matcher(t).find()) {
                    flags.add(new RiskFlag(Severity.MEDIUM, "one-sided-indemnity",
                            "The indemnity appears to run in one direction only. Consider whether it "
                                    + "should be mutual or limited to third-party claims.",
                            cite));
                }
            }
            case ClauseType.LIMITATION_OF_LIABILITY -> {
                if (LOW_CAP.matcher(t).find()) {
                    flags.add(new RiskFlag(Severity.MEDIUM, "low-liability-cap",
                            "The liability cap looks low relative to typical contract value (about one "
                                    + "month's fees). Check it is proportionate to the risk you carry.",
                            cite));
                }
            }
            case ClauseType.TERMINATION -> {
                if (FOR_CONVENIENCE.matcher(t).find() && ONLY_MATERIAL_BREACH.matcher(t).find()) {
                    flags.add(new RiskFlag(Severity.MEDIUM, "asymmetric-termination",
                            "One party may terminate for convenience while the other may terminate only "
                                    + "for material breach. Consider a reciprocal right or a longer notice period.",
                            cite));
                }
            }
            case ClauseType.RENEWAL -> {
                if (AUTO_RENEW.matcher(t).find()) {
                    int days = maxNoticeDays(t);
                    if (days >= 60) {
                        flags.add(new RiskFlag(Severity.MEDIUM, "auto-renewal-long-notice",
                                "The contract renews automatically and the window to opt out is long ("
                                        + days + " days before term end). Diarise the non-renewal deadline.",
                                cite));
                    } else {
                        flags.add(new RiskFlag(Severity.LOW, "auto-renewal",
                                "The contract renews automatically unless notice is given. Track the "
                                        + "renewal date so a renewal is a choice, not an accident.",
                                cite));
                    }
                }
            }
            case ClauseType.PAYMENT -> {
                Matcher m = MONTHLY_INTEREST.matcher(t);
                if (m.find()) {
                    double perMonth = Double.parseDouble(m.group(1));
                    double perAnnum = perMonth * 12;
                    if (perAnnum >= 18) {
                        flags.add(new RiskFlag(Severity.MEDIUM, "high-default-interest",
                                String.format("Default interest of %.2g%% per month is about %.0f%% per "
                                        + "year. Very high rates can be challenged as a penalty under "
                                        + "Indian contract law; consider negotiating it down.",
                                        perMonth, perAnnum),
                                cite));
                    }
                }
            }
            case ClauseType.NON_COMPETE -> flags.add(new RiskFlag(Severity.MEDIUM, "non-compete-restraint",
                    "Post-term non-compete restraints are frequently unenforceable in India as a "
                            + "restraint of trade. Its practical effect may be limited; take advice before "
                            + "relying on or agreeing to it.",
                    cite));
            case ClauseType.ARBITRATION -> {
                if (Pattern.compile("outside india|foreign seat|seat .* (?:singapore|london|dubai|new york)",
                        Pattern.CASE_INSENSITIVE).matcher(t).find()) {
                    flags.add(new RiskFlag(Severity.MEDIUM, "foreign-arbitration-seat",
                            "A seat of arbitration outside India changes which courts supervise the "
                                    + "arbitration and how an award is enforced. Confirm this is intended.",
                            cite));
                }
            }
            default -> { /* no per-clause rule */ }
        }
    }

    private void documentLevel(Set<String> present, List<Clause> clauses, List<RiskFlag> flags) {
        if (clauses.isEmpty()) {
            return;
        }
        SourceSpan whole = clauses.get(0).span();
        String firstText = clauses.get(0).text();
        if (!present.contains(ClauseType.LIMITATION_OF_LIABILITY)) {
            flags.add(new RiskFlag(Severity.MEDIUM, "no-liability-cap",
                    "No limitation-of-liability clause was identified. Without one, liability for a "
                            + "breach is potentially unlimited for both sides.",
                    Citation.documentOnly(whole, firstText)));
        }
        if (!present.contains(ClauseType.CONFIDENTIALITY)) {
            flags.add(new RiskFlag(Severity.LOW, "no-confidentiality-clause",
                    "No confidentiality clause was identified. If either side will share commercially "
                            + "sensitive information, one is worth adding.",
                    Citation.documentOnly(whole, firstText)));
        }
        if (!present.contains(ClauseType.GOVERNING_LAW)) {
            flags.add(new RiskFlag(Severity.MEDIUM, "no-governing-law",
                    "No governing-law clause was identified. Absent one, which law applies can itself "
                            + "become a dispute.",
                    Citation.documentOnly(whole, firstText)));
        }
        if (!present.contains(ClauseType.FORCE_MAJEURE)) {
            flags.add(new RiskFlag(Severity.LOW, "no-force-majeure",
                    "No force-majeure clause was identified. Without one, a party may still be liable "
                            + "for non-performance caused by events outside its control.",
                    Citation.documentOnly(whole, firstText)));
        }
    }

    private static int maxNoticeDays(String text) {
        Matcher m = NOTICE_DAYS.matcher(text);
        int max = 0;
        while (m.find()) {
            try {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return max;
    }
}
