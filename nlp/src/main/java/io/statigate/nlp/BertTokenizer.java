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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A self-contained BERT WordPiece tokenizer (the {@code bert-base-uncased} scheme that InLegalBERT
 * uses): lowercase, strip accents, split on whitespace and punctuation, then greedy longest-match
 * WordPiece against {@code vocab.txt}.
 *
 * <p>Pure JVM, no native code, no network - the vocabulary is the only input. Not thread-safe for
 * mutation but {@link #encode} is stateless and safe to call concurrently.
 */
public final class BertTokenizer {

    private static final String CLS = "[CLS]";
    private static final String SEP = "[SEP]";
    private static final String PAD = "[PAD]";
    private static final String UNK = "[UNK]";
    private static final int MAX_CHARS_PER_WORD = 100;

    private final Map<String, Integer> vocab;
    private final boolean lowercase;
    private final boolean stripAccents;
    private final int clsId;
    private final int sepId;
    private final int padId;
    private final int unkId;

    private BertTokenizer(Map<String, Integer> vocab, boolean lowercase, boolean stripAccents) {
        this.vocab = vocab;
        this.lowercase = lowercase;
        this.stripAccents = stripAccents;
        this.clsId = require(CLS);
        this.sepId = require(SEP);
        this.padId = vocab.getOrDefault(PAD, 0);
        this.unkId = require(UNK);
    }

    public static BertTokenizer fromVocabFile(Path vocabTxt, boolean lowercase, boolean stripAccents) {
        try {
            List<String> lines = Files.readAllLines(vocabTxt, StandardCharsets.UTF_8);
            Map<String, Integer> vocab = new HashMap<>(lines.size() * 4 / 3);
            for (int i = 0; i < lines.size(); i++) {
                String tok = lines.get(i);
                if (!tok.isEmpty()) {
                    vocab.putIfAbsent(tok, i);
                }
            }
            if (vocab.size() < 100) {
                throw new IllegalStateException("vocab.txt looks empty: " + vocabTxt);
            }
            return new BertTokenizer(vocab, lowercase, stripAccents);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read vocab file " + vocabTxt, e);
        }
    }

    private int require(String token) {
        Integer id = vocab.get(token);
        if (id == null) {
            throw new IllegalStateException("vocab is missing required special token " + token);
        }
        return id;
    }

    public int padId() {
        return padId;
    }

    /**
     * Encodes a single text as {@code [CLS] ... [SEP]}, truncated to {@code maxLen} total tokens.
     */
    public Encoding encode(String text, int maxLen) {
        int budget = Math.max(2, maxLen);
        List<Long> ids = new ArrayList<>(Math.min(budget, 64));
        ids.add((long) clsId);
        outer:
        for (String word : basicTokenize(text)) {
            for (String piece : wordPiece(normalize(word))) {
                if (ids.size() >= budget - 1) {
                    break outer;
                }
                ids.add((long) vocab.getOrDefault(piece, unkId));
            }
        }
        ids.add((long) sepId);

        int n = ids.size();
        long[] inputIds = new long[n];
        long[] attentionMask = new long[n];
        long[] tokenTypeIds = new long[n];
        for (int i = 0; i < n; i++) {
            inputIds[i] = ids.get(i);
            attentionMask[i] = 1L;
        }
        return new Encoding(inputIds, attentionMask, tokenTypeIds);
    }

    private String normalize(String token) {
        String t = token;
        if (lowercase) {
            t = t.toLowerCase();
        }
        if (stripAccents) {
            t = Normalizer.normalize(t, Normalizer.Form.NFD)
                    .replaceAll("\\p{Mn}+", "");
        }
        return t;
    }

    /** Whitespace + punctuation splitting, dropping control characters. */
    static List<String> basicTokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (isControl(cp)) {
                continue;
            }
            if (Character.isWhitespace(cp)) {
                flush(cur, tokens);
            } else if (isPunctuation(cp)) {
                flush(cur, tokens);
                tokens.add(new String(Character.toChars(cp)));
            } else {
                cur.appendCodePoint(cp);
            }
        }
        flush(cur, tokens);
        return tokens;
    }

    private static void flush(StringBuilder cur, List<String> tokens) {
        if (cur.length() > 0) {
            tokens.add(cur.toString());
            cur.setLength(0);
        }
    }

    private List<String> wordPiece(String token) {
        List<String> pieces = new ArrayList<>();
        if (token.isEmpty()) {
            return pieces;
        }
        if (token.length() > MAX_CHARS_PER_WORD) {
            pieces.add(UNK);
            return pieces;
        }
        int start = 0;
        while (start < token.length()) {
            int end = token.length();
            String match = null;
            while (start < end) {
                String sub = (start > 0 ? "##" : "") + token.substring(start, end);
                if (vocab.containsKey(sub)) {
                    match = sub;
                    break;
                }
                end--;
            }
            if (match == null) {
                return List.of(UNK); // whole token is OOV
            }
            pieces.add(match);
            start = end;
        }
        return pieces;
    }

    private static boolean isControl(int cp) {
        if (cp == '\t' || cp == '\n' || cp == '\r') {
            return false;
        }
        int type = Character.getType(cp);
        return type == Character.CONTROL || type == Character.FORMAT
                || type == Character.SURROGATE || type == Character.PRIVATE_USE
                || type == Character.UNASSIGNED;
    }

    private static boolean isPunctuation(int cp) {
        if ((cp >= 33 && cp <= 47) || (cp >= 58 && cp <= 64)
                || (cp >= 91 && cp <= 96) || (cp >= 123 && cp <= 126)) {
            return true;
        }
        int type = Character.getType(cp);
        return switch (type) {
            case Character.DASH_PUNCTUATION, Character.START_PUNCTUATION, Character.END_PUNCTUATION,
                 Character.CONNECTOR_PUNCTUATION, Character.OTHER_PUNCTUATION,
                 Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION -> true;
            default -> false;
        };
    }

    /** Model input tensors for one sequence. */
    public record Encoding(long[] inputIds, long[] attentionMask, long[] tokenTypeIds) {
        public int length() {
            return inputIds.length;
        }
    }
}
