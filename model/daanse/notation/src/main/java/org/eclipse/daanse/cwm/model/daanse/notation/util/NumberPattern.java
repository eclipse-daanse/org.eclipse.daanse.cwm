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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A number pattern of Unicode LDML (UTS #35, part 3, number patterns), written as CSV on the Web writes it: with the
 * decimal and group character of the notation itself, not LDML's placeholders.
 *
 * <p>A pattern is a prefix, a number and a suffix, and optionally {@code ;} and a negative subpattern whose prefix and
 * suffix are those of a negative number. The number: {@code #} a digit where needed, {@code 0} a digit always, the
 * group character between groups of the whole part, the decimal character before the fraction, {@code E} and an
 * optional {@code +} before the digits of the exponent. Text in single quotes is literal. The number runs from its
 * first digit symbol to its last; what stands around it is prefix and suffix.</p>
 */
final class NumberPattern {

    /** A pattern that is not well-formed: where and why. */
    static final class PatternException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        final int position;
        final String reason;

        PatternException(String pattern, int position, String reason) {
            super("the number pattern '" + pattern + "' is not well-formed at position " + position + ": " + reason);
            this.position = position;
            this.reason = reason;
        }
    }

    final String source;
    final String prefix;
    final String suffix;
    /** The prefix and suffix of a negative number, {@code null} without a negative subpattern. */
    final String negativePrefix;
    final String negativeSuffix;
    final int minInteger;
    final boolean integerHasHash;
    final int primaryGroup;
    final int secondaryGroup;
    final int minFraction;
    final int maxFraction;
    final boolean exponent;
    final boolean exponentPlus;
    final int minExponent;
    /** Where an unquoted percent or per-mille sign stands, -1 where none: LDML scales the value by it. */
    final int scalePosition;
    /** Symbols of LDML this reader does not read ({@code @}, {@code *}, {@code ¤}), empty where none. */
    final List<String> unsupported;
    /** Whether the pattern holds only the symbols CSV on the Web obliges every reader to know. */
    final boolean csvw;

    private NumberPattern(String source, Part positive, Part negative, boolean csvw) {
        this.source = source;
        this.prefix = positive.prefix;
        this.suffix = positive.suffix;
        this.negativePrefix = negative == null ? null : negative.prefix;
        this.negativeSuffix = negative == null ? null : negative.suffix;
        this.minInteger = positive.minInteger;
        this.integerHasHash = positive.integerHasHash;
        this.primaryGroup = positive.primaryGroup;
        this.secondaryGroup = positive.secondaryGroup;
        this.minFraction = positive.minFraction;
        this.maxFraction = positive.maxFraction;
        this.exponent = positive.exponent;
        this.exponentPlus = positive.exponentPlus;
        this.minExponent = positive.minExponent;
        this.scalePosition = positive.scalePosition >= 0 ? positive.scalePosition
                : negative == null ? -1 : negative.scalePosition;
        List<String> u = new ArrayList<>(positive.unsupported);
        if (negative != null) {
            negative.unsupported.stream().filter(x -> !u.contains(x)).forEach(u::add);
        }
        this.unsupported = List.copyOf(u);
        this.csvw = csvw;
    }

    /** Whether the whole part is written in groups. */
    boolean grouping() {
        return primaryGroup > 0;
    }

    /**
     * Reads a pattern written with {@code decimalChar} and {@code groupChar} ({@code null}: no grouping).
     *
     * @throws PatternException where the pattern is not well-formed
     */
    static NumberPattern parse(String pattern, char decimalChar, Character groupChar) {
        Objects.requireNonNull(pattern, "pattern");
        if (pattern.isEmpty()) {
            throw new PatternException(pattern, 0, "the pattern is empty");
        }
        List<Integer> separators = new ArrayList<>();
        boolean quoted = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            } else if (c == ';' && !quoted) {
                separators.add(i);
            }
        }
        if (quoted) {
            throw new PatternException(pattern, pattern.lastIndexOf('\''), "the quote is not closed");
        }
        if (separators.size() > 1) {
            throw new PatternException(pattern, separators.get(1), "a pattern has at most one negative subpattern");
        }
        int split = separators.isEmpty() ? pattern.length() : separators.get(0);
        Part positive = new Part(pattern, 0, split, decimalChar, groupChar);
        Part negative = separators.isEmpty() ? null : new Part(pattern, split + 1, pattern.length(), decimalChar, groupChar);
        boolean csvw = separators.isEmpty() && pattern.chars().allMatch(c -> c == '0' || c == '#' || c == decimalChar
                || groupChar != null && c == groupChar || c == 'E' || c == '+' || c == '%' || c == '‰');
        return new NumberPattern(pattern, positive, negative, csvw);
    }

    /** One subpattern: prefix, number, suffix. */
    private static final class Part {
        String prefix;
        String suffix;
        int minInteger;
        boolean integerHasHash;
        int primaryGroup;
        int secondaryGroup;
        int minFraction;
        int maxFraction;
        boolean exponent;
        boolean exponentPlus;
        int minExponent;
        int scalePosition = -1;
        final List<String> unsupported = new ArrayList<>();

        Part(String pattern, int from, int to, char d, Character g) {
            int first = -1;
            int last = -1;
            boolean q = false;
            for (int i = from; i < to; i++) {
                char c = pattern.charAt(i);
                if (c == '\'') {
                    q = !q;
                    continue;
                }
                if (q) {
                    continue;
                }
                boolean digit = c == '0' || c == '#' || c == '@';
                boolean leadingDecimal = c == d && i + 1 < to && (pattern.charAt(i + 1) == '0'
                        || pattern.charAt(i + 1) == '#');
                if (first < 0 && (digit || leadingDecimal)) {
                    first = i;
                }
                if (digit) {
                    last = i;
                }
            }
            if (first < 0) {
                throw new PatternException(pattern, from, "the pattern has no digit (0 or #)");
            }
            if (last + 1 < to && pattern.charAt(last + 1) == 'E') {
                throw new PatternException(pattern, last + 1, "E needs at least one 0");
            }
            prefix = affix(pattern, from, first);
            suffix = affix(pattern, last + 1, to);
            number(pattern, first, last + 1, d, g);
        }

        private String affix(String pattern, int from, int to) {
            StringBuilder out = new StringBuilder();
            int i = from;
            while (i < to) {
                char c = pattern.charAt(i);
                if (c == '\'') {
                    if (i + 1 < to && pattern.charAt(i + 1) == '\'') {
                        out.append('\'');
                        i += 2;
                        continue;
                    }
                    int close = pattern.indexOf('\'', i + 1);
                    while (close + 1 < to && pattern.charAt(close + 1) == '\'') {
                        close = pattern.indexOf('\'', close + 2);
                    }
                    out.append(pattern.substring(i + 1, close).replace("''", "'"));
                    i = close + 1;
                    continue;
                }
                if ((c == '%' || c == '‰') && scalePosition < 0) {
                    scalePosition = i;
                } else if (c == '¤') {
                    unsupported.add("¤ (the currency sign of a locale)");
                } else if (c == '*') {
                    unsupported.add("* (padding)");
                }
                out.append(c);
                i++;
            }
            return out.toString();
        }

        private void number(String pattern, int from, int to, char d, Character g) {
            boolean fraction = false;
            boolean zeroSeen = false;
            boolean hashInFraction = false;
            int digitsSinceGroup = 0;
            int lastGroupSize = -1;
            boolean grouped = false;
            int i = from;
            for (; i < to; i++) {
                char c = pattern.charAt(i);
                if (c == '\'') {
                    throw new PatternException(pattern, i, "quoted text inside the number");
                }
                if (c == 'E') {
                    break;
                }
                if (c == '@') {
                    if (!unsupported.contains("@ (significant digits)")) {
                        unsupported.add("@ (significant digits)");
                    }
                    c = '0';
                }
                if (!fraction) {
                    if (c == '#') {
                        if (zeroSeen) {
                            throw new PatternException(pattern, i, "# after 0 in the whole part");
                        }
                        integerHasHash = true;
                        digitsSinceGroup++;
                    } else if (c == '0') {
                        zeroSeen = true;
                        minInteger++;
                        digitsSinceGroup++;
                    } else if (g != null && c == g) {
                        if (grouped) {
                            lastGroupSize = digitsSinceGroup;
                        }
                        grouped = true;
                        digitsSinceGroup = 0;
                    } else if (c == d) {
                        fraction = true;
                    } else {
                        throw new PatternException(pattern, i, "'" + c + "' in the number"
                                + (c == ',' || c == '.' ? ": it is neither the decimal nor the group character of the "
                                        + "notation" : ""));
                    }
                } else {
                    if (c == '0') {
                        if (hashInFraction) {
                            throw new PatternException(pattern, i, "0 after # in the fraction");
                        }
                        minFraction++;
                        maxFraction++;
                    } else if (c == '#') {
                        hashInFraction = true;
                        maxFraction++;
                    } else {
                        throw new PatternException(pattern, i, "'" + c + "' in the fraction");
                    }
                }
            }
            if (grouped) {
                if (digitsSinceGroup == 0) {
                    throw new PatternException(pattern, i - 1, "the group character ends the whole part");
                }
                primaryGroup = digitsSinceGroup;
                secondaryGroup = lastGroupSize > 0 ? lastGroupSize : digitsSinceGroup;
            }
            if (i < to) {
                exponent = true;
                i++;
                if (i < to && pattern.charAt(i) == '+') {
                    exponentPlus = true;
                    i++;
                }
                for (; i < to; i++) {
                    if (pattern.charAt(i) != '0') {
                        throw new PatternException(pattern, i, "the exponent is written with 0 only");
                    }
                    minExponent++;
                }
                if (minExponent == 0) {
                    throw new PatternException(pattern, to - 1, "E needs at least one 0");
                }
                if (grouped) {
                    throw new PatternException(pattern, from, "a number with an exponent is not grouped");
                }
                if (integerHasHash) {
                    unsupported.add("# in the whole part of a number with an exponent (engineering notation)");
                }
            }
        }
    }
}
