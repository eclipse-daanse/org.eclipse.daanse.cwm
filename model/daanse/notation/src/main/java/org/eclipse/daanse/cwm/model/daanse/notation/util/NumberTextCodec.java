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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.cwm.model.daanse.notation.NumberText;

/**
 * Reads and writes a {@link NumberText}. A number reads as a {@link BigDecimal}, exactly as written; NaN, INF and
 * -INF read as the {@link Double} values. A writer rounds half to even to the fraction digits of the pattern.
 *
 * <p>Without a pattern a text is, as in CSV on the Web: an optional sign, a digit, digits and group characters, an
 * optional decimal character with digits, an optional exponent (E, an optional sign, digits). A group character
 * neither ends the whole part nor follows another one. A writer writes the plain number, its whole part in groups of
 * three where the notation names a group character.</p>
 *
 * <p>Read strictly, the groups of a whole part written with group characters are the usual ones (a first group of
 * one to three digits, then groups of three) or, with a pattern, those the pattern states; {@code 12.5} with the
 * decimal character {@code ,} and the group character {@code .} does not fit. Read leniently, a group character may
 * stand between any two digits, as CSV on the Web reads it, and {@code 12.5} reads as 125. A text without group
 * characters fits either way.</p>
 */
final class NumberTextCodec implements FormCodec {

    /** The usual grouping without a pattern: groups of three. */
    private static final int USUAL_GROUP = 3;

    private final char decimal;
    private final Character group;
    private final NumberPattern pattern;
    private final boolean bare;
    private final boolean lenient;

    /**
     * A reader and writer of {@code form}.
     *
     * @throws IllegalArgumentException      where a character is not one character, the two are equal, or the pattern
     *                                       is not well-formed or scales the value
     * @throws UnsupportedOperationException where the pattern holds symbols this reader does not read
     */
    NumberTextCodec(NumberText form) {
        this.decimal = oneCharacter(form.getDecimalChar(), "decimal character");
        this.group = form.getGroupChar() == null ? null : oneCharacter(form.getGroupChar(), "group character");
        if (group != null && group == decimal) {
            throw new IllegalArgumentException("the decimal and the group character are both '" + decimal + "'");
        }
        this.bare = form.isBare();
        this.lenient = form.isLenient();
        if (form.getPattern() == null) {
            this.pattern = null;
        } else {
            this.pattern = NumberPattern.parse(form.getPattern(), decimal, group);
            if (pattern.scalePosition >= 0) {
                throw new IllegalArgumentException("the number pattern '" + form.getPattern() + "' scales the value by "
                        + "the sign at position " + pattern.scalePosition + "; a scale is a unit, quote the sign");
            }
            if (!pattern.unsupported.isEmpty()) {
                throw new UnsupportedOperationException("the number pattern '" + form.getPattern() + "' holds "
                        + String.join(", ", pattern.unsupported) + ", which this reader does not read");
            }
        }
    }

    static char oneCharacter(String s, String what) {
        if (s == null || s.codePointCount(0, s.length()) != 1 || s.length() != 1) {
            throw new IllegalArgumentException("the " + what + " '" + s + "' is not one character");
        }
        char c = s.charAt(0);
        if (c >= '0' && c <= '9' || c == '+' || c == '-' || c == 'E') {
            throw new IllegalArgumentException("the " + what + " '" + s + "' is a digit, a sign or E");
        }
        return c;
    }

    // ------------------------------------------------------------------ reading

    @Override
    public Object read(String text, int start, int end) {
        Object special = special(text.substring(start, end));
        if (special != null) {
            return special;
        }
        if (bare) {
            return pattern == null ? plain(text, start, end) : patterned(text, start, end, false);
        }
        if (pattern != null && (!pattern.prefix.isEmpty() || !pattern.suffix.isEmpty()
                || pattern.negativePrefix != null)) {
            try {
                return patterned(text, start, end, false);
            } catch (UnfitTextException first) {
                // the signs around the number are not the pattern's: stripped, the number read without them
            }
        }
        int s = start;
        int e = end;
        while (s < e && strippable(text.charAt(s))) {
            s++;
        }
        while (e > s && strippable(text.charAt(e - 1))) {
            e--;
        }
        special = special(text.substring(s, e));
        if (special != null) {
            return special;
        }
        return pattern == null ? plain(text, s, e) : patterned(text, s, e, true);
    }

    /** A sign a number that is not bare may carry around it: white space, a currency sign, %, ‰, ‱, a letter. */
    static boolean strippable(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.getType(c) == Character.CURRENCY_SYMBOL
                || c == '%' || c == '‰' || c == '‱' || Character.isLetter(c);
    }

    private static Object special(String s) {
        return switch (s) {
        case "NaN" -> Double.NaN;
        case "INF" -> Double.POSITIVE_INFINITY;
        case "-INF" -> Double.NEGATIVE_INFINITY;
        default -> null;
        };
    }

    /** The grammar of CSV on the Web, without a pattern. */
    private BigDecimal plain(String text, int start, int end) {
        int i = start;
        StringBuilder number = new StringBuilder();
        if (i < end && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
            number.append(text.charAt(i));
            i++;
        }
        if (i >= end || !digit(text.charAt(i))) {
            throw new UnfitTextException(text, i, "expected a digit, found " + FormCodec.found(text, i, end));
        }
        List<int[]> groups = new ArrayList<>();
        int groupStart = i;
        int groupSize = 0;
        boolean lastWasGroup = false;
        while (i < end && (digit(text.charAt(i)) || group != null && text.charAt(i) == group)) {
            char c = text.charAt(i);
            if (!digit(c)) {
                if (lastWasGroup) {
                    throw new UnfitTextException(text, i, "two group characters in a row");
                }
                groups.add(new int[] { groupStart, groupSize });
                groupStart = i + 1;
                groupSize = 0;
                lastWasGroup = true;
            } else {
                number.append(c);
                groupSize++;
                lastWasGroup = false;
            }
            i++;
        }
        if (lastWasGroup) {
            throw new UnfitTextException(text, i - 1, "the group character ends the whole part");
        }
        if (!groups.isEmpty() && !lenient) {
            groups.add(new int[] { groupStart, groupSize });
            checkGroups(text, groups, USUAL_GROUP, USUAL_GROUP, "the usual grouping");
        }
        if (i < end && text.charAt(i) == decimal) {
            int f = i + 1;
            while (f < end && digit(text.charAt(f))) {
                f++;
            }
            if (f == i + 1) {
                throw new UnfitTextException(text, f, "expected digits after the decimal character, found "
                        + FormCodec.found(text, f, end));
            }
            number.append('.').append(text, i + 1, f);
            i = f;
        }
        i = exponent(text, i, end, number, 1);
        if (i < end) {
            throw new UnfitTextException(text, i, "after the number the text goes on with " + FormCodec.found(text, i,
                    end));
        }
        return new BigDecimal(number.toString());
    }

    /** Reads an exponent where one stands at {@code i}; {@code min} digits at least. */
    private static int exponent(String text, int i, int end, StringBuilder number, int min) {
        if (i < end && text.charAt(i) == 'E') {
            int e = i + 1;
            StringBuilder exp = new StringBuilder("E");
            if (e < end && (text.charAt(e) == '+' || text.charAt(e) == '-')) {
                exp.append(text.charAt(e));
                e++;
            }
            int digits = e;
            while (e < end && digit(text.charAt(e))) {
                e++;
            }
            if (e - digits < min) {
                throw new UnfitTextException(text, digits, "expected " + min + (min == 1 ? " digit" : " digits")
                        + " of the exponent, found " + FormCodec.found(text, digits, end));
            }
            number.append(exp).append(text, digits, e);
            return e;
        }
        return i;
    }

    /**
     * The number by the pattern; {@code withoutAffixes} reads the number alone, with an optional minus, as a number
     * that is not bare does once the signs around it are stripped.
     */
    private BigDecimal patterned(String text, int start, int end, boolean withoutAffixes) {
        NumberPattern p = pattern;
        String posPrefix = withoutAffixes ? "" : p.prefix;
        String posSuffix = withoutAffixes ? "" : p.suffix;
        String negPrefix = p.negativePrefix != null && !withoutAffixes ? p.negativePrefix : "-" + posPrefix;
        String negSuffix = p.negativePrefix != null && !withoutAffixes ? p.negativeSuffix : posSuffix;
        boolean negative;
        String prefix;
        String suffix;
        boolean negFits = fits(text, start, end, negPrefix, negSuffix);
        boolean posFits = fits(text, start, end, posPrefix, posSuffix);
        if (negFits && (!posFits || negPrefix.length() + negSuffix.length() > posPrefix.length() + posSuffix.length())) {
            negative = true;
            prefix = negPrefix;
            suffix = negSuffix;
        } else if (posFits) {
            negative = false;
            prefix = posPrefix;
            suffix = posSuffix;
        } else {
            boolean prefixFits = text.startsWith(posPrefix, start) || text.startsWith(negPrefix, start);
            int at = prefixFits ? Math.max(start, end - posSuffix.length()) : start;
            String expected = prefixFits ? posSuffix : posPrefix;
            throw new UnfitTextException(text, at, "expected '" + expected + "', found " + FormCodec.found(text, at,
                    end));
        }
        int i = start + prefix.length();
        int bodyEnd = end - suffix.length();
        StringBuilder number = new StringBuilder(negative ? "-" : "");
        int digitsStart = i;
        int digits = 0;
        int groupStart = i;
        int groupSize = 0;
        List<int[]> groups = new ArrayList<>();
        int lastGroupAt = -1;
        while (i < bodyEnd && (digit(text.charAt(i)) || group != null && text.charAt(i) == group)) {
            if (digit(text.charAt(i))) {
                number.append(text.charAt(i));
                digits++;
                groupSize++;
            } else {
                if (!p.grouping() && !lenient) {
                    throw new UnfitTextException(text, i, "a group character, but the pattern groups no digits");
                }
                if (groupSize == 0) {
                    throw new UnfitTextException(text, i, i == digitsStart ? "the group character starts the number"
                            : "two group characters in a row");
                }
                groups.add(new int[] { groupStart, groupSize });
                groupStart = i + 1;
                groupSize = 0;
                lastGroupAt = i;
            }
            i++;
        }
        if (lastGroupAt >= 0 && groupSize == 0) {
            throw new UnfitTextException(text, lastGroupAt, "the group character ends the whole part");
        }
        if (!groups.isEmpty() && !lenient) {
            groups.add(new int[] { groupStart, groupSize });
            checkGroups(text, groups, p.primaryGroup, p.secondaryGroup, "the grouping of the pattern");
        }
        if (digits < p.minInteger) {
            throw new UnfitTextException(text, digitsStart, "the whole part has " + digits + " digits, the pattern "
                    + "writes at least " + p.minInteger);
        }
        int fraction = 0;
        if (i < bodyEnd && text.charAt(i) == decimal) {
            if (p.maxFraction == 0) {
                throw new UnfitTextException(text, i, "a decimal character, but the pattern writes no fraction");
            }
            int f = i + 1;
            while (f < bodyEnd && digit(text.charAt(f))) {
                f++;
            }
            fraction = f - i - 1;
            if (fraction < Math.max(1, p.minFraction) || fraction > p.maxFraction) {
                throw new UnfitTextException(text, i + 1, "the fraction has " + fraction + " digits, the pattern "
                        + "writes " + (p.minFraction == p.maxFraction ? "" : p.minFraction + " to ") + p.maxFraction);
            }
            number.append('.').append(text, i + 1, f);
            i = f;
        } else if (p.minFraction > 0) {
            throw new UnfitTextException(text, i, "expected the decimal character and " + p.minFraction
                    + " digits of the fraction, found " + FormCodec.found(text, i, end));
        }
        if (digits == 0 && fraction == 0) {
            throw new UnfitTextException(text, digitsStart, "expected a number, found " + FormCodec.found(text,
                    digitsStart, end));
        }
        if (p.exponent) {
            if (i >= bodyEnd || text.charAt(i) != 'E') {
                throw new UnfitTextException(text, i, "expected the exponent (E), found " + FormCodec.found(text, i,
                        end));
            }
            i = exponent(text, i, bodyEnd, number, p.minExponent);
        }
        if (i < bodyEnd) {
            throw new UnfitTextException(text, i, "after the number the text goes on with " + FormCodec.found(text, i,
                    end) + (suffix.isEmpty() ? "" : "; expected '" + suffix + "'"));
        }
        return new BigDecimal(number.toString());
    }

    /**
     * Checks the groups of a whole part written with group characters, each given by where its first digit stands and
     * how many digits it has, from left to right: the first has one to {@code secondary} digits, those in between
     * {@code secondary}, the last {@code primary}.
     */
    private static void checkGroups(String text, List<int[]> groups, int primary, int secondary, String grouping) {
        int[] first = groups.get(0);
        if (first[1] > secondary) {
            throw new UnfitTextException(text, first[0], "the first group has " + digits(first[1]) + ", more than "
                    + secondary + " (" + grouping + ")");
        }
        for (int g = 1; g < groups.size() - 1; g++) {
            int[] between = groups.get(g);
            if (between[1] != secondary) {
                throw new UnfitTextException(text, between[0], "a group has " + digits(between[1]) + ", not "
                        + secondary + " (" + grouping + ")");
            }
        }
        int[] last = groups.get(groups.size() - 1);
        if (last[1] != primary) {
            throw new UnfitTextException(text, last[0], "the last group has " + digits(last[1]) + ", not " + primary
                    + " (" + grouping + ")");
        }
    }

    private static String digits(int n) {
        return n + (n == 1 ? " digit" : " digits");
    }

    private static boolean fits(String text, int start, int end, String prefix, String suffix) {
        return end - start >= prefix.length() + suffix.length() && text.startsWith(prefix, start)
                && text.startsWith(suffix, end - suffix.length());
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }

    // ------------------------------------------------------------------ writing

    @Override
    public String write(Object value) {
        if (value instanceof Double d && (d.isNaN() || d.isInfinite())) {
            return d.isNaN() ? "NaN" : d > 0 ? "INF" : "-INF";
        }
        if (value instanceof Float f && (f.isNaN() || f.isInfinite())) {
            return f.isNaN() ? "NaN" : f > 0 ? "INF" : "-INF";
        }
        BigDecimal v = decimal(value);
        return pattern == null ? plainText(v) : patternText(v);
    }

    /** A number as a decimal: exactly for integers and decimals, by its shortest text for a float or double. */
    static BigDecimal decimal(Object value) {
        return switch (value) {
        case BigDecimal b -> b;
        case BigInteger b -> new BigDecimal(b);
        case Long l -> BigDecimal.valueOf(l);
        case Integer n -> BigDecimal.valueOf(n);
        case Short n -> BigDecimal.valueOf(n);
        case Byte n -> BigDecimal.valueOf(n);
        case Double d -> new BigDecimal(Double.toString(d));
        case Float f -> new BigDecimal(Float.toString(f));
        case null -> throw new IllegalArgumentException("no number to write");
        default -> throw new IllegalArgumentException(value + " (" + value.getClass().getSimpleName()
                + ") is no number");
        };
    }

    private String plainText(BigDecimal v) {
        String s = v.toPlainString();
        boolean negative = s.startsWith("-");
        String abs = negative ? s.substring(1) : s;
        int dot = abs.indexOf('.');
        String whole = dot < 0 ? abs : abs.substring(0, dot);
        String fraction = dot < 0 ? "" : abs.substring(dot + 1);
        return (negative ? "-" : "") + grouped(whole, 3, 3) + (fraction.isEmpty() ? "" : decimal + fraction);
    }

    private String grouped(String whole, int primary, int secondary) {
        if (group == null || primary <= 0 || whole.length() <= primary) {
            return whole;
        }
        StringBuilder out = new StringBuilder(whole.substring(whole.length() - primary));
        int i = whole.length() - primary;
        while (i > 0) {
            int from = Math.max(0, i - secondary);
            out.insert(0, group).insert(0, whole.substring(from, i));
            i = from;
        }
        return out.toString();
    }

    private String patternText(BigDecimal value) {
        NumberPattern p = pattern;
        BigDecimal abs = value.abs();
        String whole;
        String fraction;
        String exponentText = "";
        boolean zero;
        if (p.exponent) {
            int e = 0;
            BigDecimal mantissa = BigDecimal.ZERO;
            if (abs.signum() != 0) {
                int lead = abs.precision() - abs.scale() - 1;
                e = lead - (p.minInteger - 1);
                mantissa = abs.movePointLeft(e).setScale(p.maxFraction, RoundingMode.HALF_EVEN);
                if (mantissa.compareTo(BigDecimal.TEN.pow(p.minInteger)) >= 0) {
                    e++;
                    mantissa = abs.movePointLeft(e).setScale(p.maxFraction, RoundingMode.HALF_EVEN);
                }
            }
            zero = mantissa.signum() == 0;
            String[] parts = split(trimFraction(mantissa.setScale(p.maxFraction, RoundingMode.HALF_EVEN), p.minFraction));
            whole = parts[0];
            fraction = parts[1];
            String digits = Integer.toString(Math.abs(e));
            exponentText = "E" + (e < 0 ? "-" : p.exponentPlus ? "+" : "")
                    + "0".repeat(Math.max(0, p.minExponent - digits.length())) + digits;
        } else {
            BigDecimal rounded = abs.setScale(p.maxFraction, RoundingMode.HALF_EVEN);
            zero = rounded.signum() == 0;
            String[] parts = split(trimFraction(rounded, p.minFraction));
            whole = parts[0];
            fraction = parts[1];
        }
        if (whole.equals("0") && p.minInteger == 0 && !fraction.isEmpty()) {
            whole = "";
        }
        if (whole.length() < p.minInteger) {
            whole = "0".repeat(p.minInteger - whole.length()) + whole;
        }
        if (p.grouping()) {
            whole = grouped(whole, p.primaryGroup, p.secondaryGroup);
        }
        boolean negative = value.signum() < 0 && !zero;
        String prefix = negative ? (p.negativePrefix != null ? p.negativePrefix : "-" + p.prefix) : p.prefix;
        String suffix = negative ? (p.negativePrefix != null ? p.negativeSuffix : p.suffix) : p.suffix;
        return prefix + whole + (fraction.isEmpty() ? "" : decimal + fraction) + exponentText + suffix;
    }

    /** Drops trailing zeros of the fraction down to {@code min} digits. */
    private static BigDecimal trimFraction(BigDecimal v, int min) {
        BigDecimal t = v.stripTrailingZeros();
        return t.scale() < min ? t.setScale(min) : t;
    }

    /** The whole part and the fraction of a non-negative decimal, as digits. */
    private static String[] split(BigDecimal v) {
        String s = v.toPlainString();
        int dot = s.indexOf('.');
        return dot < 0 ? new String[] { s, "" } : new String[] { s.substring(0, dot), s.substring(dot + 1) };
    }
}
