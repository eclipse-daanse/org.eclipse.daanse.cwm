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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.daanse.cwm.model.daanse.notation.PatternText;

/**
 * Reads and writes a {@link PatternText}: the value is the text, the whole of which matches the regular expression.
 * The expression is compiled by {@link java.util.regex}, which reads the common syntax of ECMAScript alike.
 */
final class PatternTextCodec implements FormCodec {

    private final Pattern regex;

    /**
     * A reader and writer of {@code form}.
     *
     * @throws IllegalArgumentException where the regular expression does not compile
     */
    PatternTextCodec(PatternText form) {
        this.regex = compile(form.getPattern());
    }

    /**
     * The compiled expression.
     *
     * @throws IllegalArgumentException where it does not compile
     */
    static Pattern compile(String expression) {
        if (expression == null) {
            throw new IllegalArgumentException("the pattern text names no regular expression");
        }
        try {
            return Pattern.compile(expression);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("the regular expression '" + expression + "' does not compile at "
                    + "position " + e.getIndex() + ": " + e.getDescription(), e);
        }
    }

    @Override
    public Object read(String text, int start, int end) {
        String value = text.substring(start, end);
        if (regex.matcher(value).matches()) {
            return value;
        }
        int fits = 0;
        for (int k = 1; k <= value.length(); k++) {
            Matcher m = regex.matcher(value.substring(0, k));
            if (m.matches() || m.hitEnd()) {
                fits = k;
            } else {
                break;
            }
        }
        String reason = fits == value.length() ? "the text ends before it matches " + regex.pattern()
                : "from here the text does not match " + regex.pattern();
        throw new UnfitTextException(text, start + fits, reason);
    }

    @Override
    public String write(Object value) {
        if (!(value instanceof CharSequence cs)) {
            throw new IllegalArgumentException(value + " is no text");
        }
        String s = cs.toString();
        if (!regex.matcher(s).matches()) {
            throw new IllegalArgumentException("'" + s + "' does not match " + regex.pattern());
        }
        return s;
    }
}
