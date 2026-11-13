/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns the globs of {@link ElementRules} into patterns. The pattern stays
 * inside this package: callers write globs, never regular expressions.
 */
final class Globs {

    private static final String ANY_SEGMENTS = "**";

    private Globs() {
    }

    /** A name glob: {@code *} is any run of characters, {@code ?} one character. */
    static Pattern name(String glob) {
        return Pattern.compile(translate(glob, ".*", "."), Pattern.DOTALL);
    }

    /**
     * A dotted path glob: {@code *} and {@code ?} stay within a segment, a
     * segment {@code **} is any number of segments, none included. A
     * {@code **} inside a longer segment counts as {@code *}.
     */
    static Pattern path(String glob) {
        List<String> segments = new ArrayList<>();
        for (String segment : glob.split("\\.", -1)) {
            boolean repeatsAny = ANY_SEGMENTS.equals(segment) && !segments.isEmpty()
                    && ANY_SEGMENTS.equals(segments.get(segments.size() - 1));
            if (!repeatsAny) {
                segments.add(segment);
            }
        }
        StringBuilder regex = new StringBuilder();
        boolean dotBeforeNext = false;
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            boolean last = i == segments.size() - 1;
            if (ANY_SEGMENTS.equals(segment)) {
                if (dotBeforeNext) {
                    // after a segment: nothing, or a dot and whatever follows up to the next dot
                    regex.append("(?:\\..*)?");
                } else {
                    // at the start: nothing, or segments that end in a dot (or everything, when alone)
                    regex.append(last ? ".*" : "(?:.*\\.)?");
                }
            } else {
                if (dotBeforeNext) {
                    regex.append("\\.");
                }
                regex.append(translate(segment, "[^.]*", "[^.]"));
                dotBeforeNext = true;
            }
        }
        return Pattern.compile(regex.toString(), Pattern.DOTALL);
    }

    private static String translate(String glob, String star, String question) {
        StringBuilder regex = new StringBuilder();
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*' || c == '?') {
                flush(literal, regex);
                regex.append(c == '*' ? star : question);
            } else {
                literal.append(c);
            }
        }
        flush(literal, regex);
        return regex.toString();
    }

    private static void flush(StringBuilder literal, StringBuilder regex) {
        if (!literal.isEmpty()) {
            regex.append(Pattern.quote(literal.toString()));
            literal.setLength(0);
        }
    }
}
