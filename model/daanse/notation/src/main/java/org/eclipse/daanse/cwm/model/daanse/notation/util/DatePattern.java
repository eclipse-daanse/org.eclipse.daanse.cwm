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
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A date pattern of Unicode LDML (UTS #35, part 4, date field symbol table), read into fields and literals.
 *
 * <p>Every ASCII letter outside single quotes is a field; a run of one letter is one field, its count the length of
 * the run. Text in single quotes is literal, two single quotes are one. Every other character is literal. A lone
 * {@code T} is a literal as CSV on the Web writes it ({@code yyyy-MM-ddTHH:mm:ss}); LDML itself would quote it.</p>
 */
final class DatePattern {

    /** A piece of a pattern: a field or literal text. */
    sealed interface Token permits Literal, Field {
        /** Where the piece starts in the pattern, from 0. */
        int position();
    }

    /** Literal text. */
    record Literal(String text, int position) implements Token {
    }

    /** A field: its letter and how many times it is written. */
    record Field(char letter, int count, int position) implements Token {

        /** The field as written, e.g. {@code MMM}. */
        String written() {
            return String.valueOf(letter).repeat(count);
        }
    }

    /** A pattern that is not well-formed: where and why. */
    static final class PatternException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        final int position;
        final String reason;

        PatternException(String pattern, int position, String reason) {
            super("the pattern '" + pattern + "' is not well-formed at position " + position + ": " + reason);
            this.position = position;
            this.reason = reason;
        }
    }

    /** The counts a field letter takes: from {@code min} to {@code max}, and the name of the field. */
    private record Counts(int min, int max, String name) {
    }

    private static final int ANY = Integer.MAX_VALUE;

    /** The date field symbols of LDML with the counts the table defines. */
    private static final Map<Character, Counts> FIELDS = Map.ofEntries(
            Map.entry('G', new Counts(1, 5, "era")),
            Map.entry('y', new Counts(1, ANY, "year")),
            Map.entry('Y', new Counts(1, ANY, "year of the week")),
            Map.entry('u', new Counts(1, ANY, "extended year")),
            Map.entry('U', new Counts(1, 5, "cyclic year name")),
            Map.entry('r', new Counts(1, ANY, "related Gregorian year")),
            Map.entry('Q', new Counts(1, 5, "quarter")),
            Map.entry('q', new Counts(1, 5, "stand-alone quarter")),
            Map.entry('M', new Counts(1, 5, "month")),
            Map.entry('L', new Counts(1, 5, "stand-alone month")),
            Map.entry('w', new Counts(1, 2, "week of the year")),
            Map.entry('W', new Counts(1, 1, "week of the month")),
            Map.entry('d', new Counts(1, 2, "day of the month")),
            Map.entry('D', new Counts(1, 3, "day of the year")),
            Map.entry('F', new Counts(1, 1, "day of the week in the month")),
            Map.entry('g', new Counts(1, ANY, "modified Julian day")),
            Map.entry('E', new Counts(1, 6, "day of the week")),
            Map.entry('e', new Counts(1, 6, "local day of the week")),
            Map.entry('c', new Counts(1, 6, "stand-alone local day of the week")),
            Map.entry('a', new Counts(1, 5, "AM or PM")),
            Map.entry('b', new Counts(1, 5, "day period with noon and midnight")),
            Map.entry('B', new Counts(1, 5, "flexible day period")),
            Map.entry('h', new Counts(1, 2, "hour 1 to 12")),
            Map.entry('H', new Counts(1, 2, "hour 0 to 23")),
            Map.entry('K', new Counts(1, 2, "hour 0 to 11")),
            Map.entry('k', new Counts(1, 2, "hour 1 to 24")),
            Map.entry('m', new Counts(1, 2, "minute")),
            Map.entry('s', new Counts(1, 2, "second")),
            Map.entry('S', new Counts(1, ANY, "fraction of a second")),
            Map.entry('A', new Counts(1, ANY, "milliseconds in the day")),
            Map.entry('z', new Counts(1, 4, "specific zone name")),
            Map.entry('Z', new Counts(1, 5, "zone offset")),
            Map.entry('O', new Counts(1, 4, "localized GMT offset")),
            Map.entry('v', new Counts(1, 4, "generic zone name")),
            Map.entry('V', new Counts(1, 4, "zone id")),
            Map.entry('X', new Counts(1, 5, "zone offset, Z for zero")),
            Map.entry('x', new Counts(1, 5, "zone offset")));

    /** The date formats of CSV on the Web, 6.4.4. */
    private static final String CSVW_DATE = "(yyyy-MM-dd|yyyyMMdd|dd-MM-yyyy|d-M-yyyy|MM-dd-yyyy|M-d-yyyy|dd/MM/yyyy"
            + "|d/M/yyyy|MM/dd/yyyy|M/d/yyyy|dd\\.MM\\.yyyy|d\\.M\\.yyyy|MM\\.dd\\.yyyy|M\\.d\\.yyyy)";
    /** The time formats of CSV on the Web, 6.4.4. */
    private static final String CSVW_TIME = "(HH:mm:ss\\.S+|HH:mm:ss|HHmmss|HH:mm|HHmm)";
    /** A date, a time or both, then an optional zone marker of one to three x or X, possibly after one space. */
    private static final Pattern CSVW = Pattern.compile("(" + CSVW_DATE + "|" + CSVW_TIME + "|" + CSVW_DATE + "[T ]"
            + CSVW_TIME + ")( ?(X{1,3}|x{1,3}))?");

    final String source;
    final List<Token> tokens;

    private DatePattern(String source, List<Token> tokens) {
        this.source = source;
        this.tokens = List.copyOf(tokens);
    }

    /**
     * Reads a pattern.
     *
     * @throws PatternException where a quote does not close, a letter is no field of LDML, or a field is written a
     *                          number of times the field does not take
     */
    static DatePattern parse(String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        if (pattern.isEmpty()) {
            throw new PatternException(pattern, 0, "the pattern is empty");
        }
        List<Token> tokens = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int literalStart = -1;
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '\'') {
                    literalStart = literalStart < 0 ? i : literalStart;
                    literal.append('\'');
                    i += 2;
                    continue;
                }
                int close = i + 1;
                StringBuilder quoted = new StringBuilder();
                while (true) {
                    if (close >= pattern.length()) {
                        throw new PatternException(pattern, i, "the quote is not closed");
                    }
                    if (pattern.charAt(close) == '\'') {
                        if (close + 1 < pattern.length() && pattern.charAt(close + 1) == '\'') {
                            quoted.append('\'');
                            close += 2;
                            continue;
                        }
                        break;
                    }
                    quoted.append(pattern.charAt(close));
                    close++;
                }
                literalStart = literalStart < 0 ? i : literalStart;
                literal.append(quoted);
                i = close + 1;
                continue;
            }
            if (isAsciiLetter(c)) {
                int end = i;
                while (end < pattern.length() && pattern.charAt(end) == c) {
                    end++;
                }
                int count = end - i;
                if (c == 'T' && count == 1) {
                    literalStart = literalStart < 0 ? i : literalStart;
                    literal.append('T');
                    i = end;
                    continue;
                }
                if (c == 'j' || c == 'J' || c == 'C') {
                    throw new PatternException(pattern, i, c + " is a symbol of a skeleton, not a field of a pattern");
                }
                Counts counts = FIELDS.get(c);
                if (counts == null) {
                    throw new PatternException(pattern, i, c + " is no field of LDML; quote literal letters ('" + c
                            + "')");
                }
                if (count < counts.min() || count > counts.max() || c == 'O' && count != 1 && count != 4
                        || c == 'v' && count != 1 && count != 4 || c == 'c' && count == 2) {
                    throw new PatternException(pattern, i, String.valueOf(c).repeat(count) + ": the " + counts.name()
                            + " is not written " + count + " times");
                }
                if (literal.length() > 0) {
                    tokens.add(new Literal(literal.toString(), literalStart));
                    literal.setLength(0);
                    literalStart = -1;
                }
                tokens.add(new Field(c, count, i));
                i = end;
                continue;
            }
            literalStart = literalStart < 0 ? i : literalStart;
            literal.append(c);
            i++;
        }
        if (literal.length() > 0) {
            tokens.add(new Literal(literal.toString(), literalStart));
        }
        return new DatePattern(pattern, tokens);
    }

    /** The name of the field a letter stands for. */
    static String name(char letter) {
        Counts counts = FIELDS.get(letter);
        return counts == null ? String.valueOf(letter) : counts.name();
    }

    /** The fields, in order. */
    List<Field> fields() {
        return tokens.stream().filter(Field.class::isInstance).map(Field.class::cast).toList();
    }

    /** The first field written in the language: names of months, days, day periods, eras, quarters and zones. */
    Field textField() {
        for (Field f : fields()) {
            if (isText(f)) {
                return f;
            }
        }
        return null;
    }

    /** Whether a field is written as words of a language. */
    static boolean isText(Field f) {
        return switch (f.letter()) {
        case 'G', 'U', 'E', 'a', 'b', 'B', 'z', 'v', 'O' -> true;
        case 'M', 'L', 'Q', 'q', 'e', 'c' -> f.count() >= 3;
        case 'Z' -> f.count() == 4;
        case 'V' -> f.count() >= 3;
        default -> false;
        };
    }

    /** Whether the text holds an offset or a zone. */
    boolean hasZoneField() {
        return fields().stream().anyMatch(f -> "zZOvVXx".indexOf(f.letter()) >= 0);
    }

    /** Whether the text holds a time of day: an hour or the milliseconds in the day. */
    boolean hasTimeOfDay() {
        return fields().stream().anyMatch(f -> "hHKkA".indexOf(f.letter()) >= 0);
    }

    /** Whether the text holds a part of a date. */
    boolean hasDate() {
        return fields().stream().anyMatch(f -> "GyYuUrQqMLwWdDFgEec".indexOf(f.letter()) >= 0);
    }

    /**
     * Whether the text is digits only, starting with the year: a number column can hold it as the number of its
     * digits without losing a leading zero ({@code yyyyMMdd}, {@code yyyyMM}, {@code yyyy}).
     */
    boolean digitsOnlyYearFirst() {
        if (tokens.isEmpty() || !(tokens.get(0) instanceof Field first) || first.letter() != 'y' && first.letter() != 'u'
                || first.count() < 4) {
            return false;
        }
        for (Token t : tokens) {
            if (t instanceof Literal || t instanceof Field f && (isText(f) || "yuMLdDHhKkmsS".indexOf(f.letter()) < 0)) {
                return false;
            }
        }
        return true;
    }

    /** Whether the pattern is one of the date, time and date-time formats CSV on the Web obliges every reader to know. */
    boolean isCsvw() {
        StringBuilder shape = new StringBuilder();
        for (Token t : tokens) {
            if (t instanceof Field f) {
                shape.append(f.written());
            } else if (t instanceof Literal l) {
                if (!l.text().chars().allMatch(ch -> "-/.: T".indexOf(ch) >= 0)) {
                    return false;
                }
                shape.append(l.text());
            }
        }
        return CSVW.matcher(shape).matches();
    }

    private static boolean isAsciiLetter(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
    }
}
