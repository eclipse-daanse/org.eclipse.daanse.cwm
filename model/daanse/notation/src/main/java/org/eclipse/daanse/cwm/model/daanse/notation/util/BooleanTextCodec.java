/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.notation.util;

import java.util.List;

import org.eclipse.daanse.cwm.model.daanse.notation.BooleanText;

/** Reads and writes a {@link BooleanText}: a word is true or false, a writer writes the first word of each. */
final class BooleanTextCodec implements FormCodec {

    private final List<String> trueWords;
    private final List<String> falseWords;
    private final boolean ignoreCase;

    /**
     * A reader and writer of {@code form}.
     *
     * @throws IllegalArgumentException where a list of words is empty or a word is true and false
     */
    BooleanTextCodec(BooleanText form) {
        this.trueWords = List.copyOf(form.getTrueWords());
        this.falseWords = List.copyOf(form.getFalseWords());
        this.ignoreCase = form.isIgnoreCase();
        if (trueWords.isEmpty() || falseWords.isEmpty()) {
            throw new IllegalArgumentException("a boolean text names at least one true and one false word");
        }
        for (String t : trueWords) {
            if (contains(falseWords, t)) {
                throw new IllegalArgumentException("'" + t + "' is a true and a false word");
            }
        }
    }

    /** Whether {@code words} holds {@code word}, in any case where the form ignores case. */
    boolean contains(List<String> words, String word) {
        return words.stream().anyMatch(w -> ignoreCase ? w.equalsIgnoreCase(word) : w.equals(word));
    }

    @Override
    public Object read(String text, int start, int end) {
        String word = text.substring(start, end);
        if (contains(trueWords, word)) {
            return Boolean.TRUE;
        }
        if (contains(falseWords, word)) {
            return Boolean.FALSE;
        }
        throw new UnfitTextException(text, start, "'" + word + "' is none of the words for true (" + String.join(", ",
                trueWords) + ") and false (" + String.join(", ", falseWords) + ")"
                + (ignoreCase ? ", in any case" : ""));
    }

    @Override
    public String write(Object value) {
        if (value instanceof Boolean b) {
            return b ? trueWords.get(0) : falseWords.get(0);
        }
        throw new IllegalArgumentException(value + " is no truth value");
    }
}
