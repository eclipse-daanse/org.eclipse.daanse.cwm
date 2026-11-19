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

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.eclipse.daanse.cwm.model.daanse.notation.TemporalForm;
import org.eclipse.daanse.cwm.model.daanse.notation.TemporalText;
import org.eclipse.daanse.cwm.model.daanse.notation.util.DatePattern.Field;
import org.eclipse.daanse.cwm.model.daanse.notation.util.DatePattern.Literal;
import org.eclipse.daanse.cwm.model.daanse.notation.util.DatePattern.Token;

/**
 * Reads and writes a {@link TemporalText}: a named ISO 8601 form or an LDML date pattern.
 *
 * <p>The value is of the kind the text holds: a {@link LocalDate}, {@link YearMonth}, {@link Year} or
 * {@link MonthDay} for a date or part of one; a {@link LocalTime} or, with an offset, an {@link OffsetTime}; a
 * {@link LocalDateTime} for a date and time without a zone; an {@link OffsetDateTime} where the text holds an offset
 * or the form names a fixed one; a {@link ZonedDateTime} where the text holds a zone id or the form names a zone whose
 * offset changes.</p>
 *
 * <p>The fields read: y and u (yy is 2000 to 2099), M and L (numbers, abbreviated and full names), d, D, E (abbreviated
 * and full names), a, h, H, K, k, m, s, S, X and x (1 to 5), Z (1 to 3 and 5) and VV. Other fields of LDML (era,
 * quarter, week, narrow names, zone names) are refused when the reader is made.</p>
 */
final class TemporalTextCodec implements FormCodec {

    private enum Kind {
        LITERAL, YEAR, YEAR2, MONTH, MONTH_NAME, DAY, DAY_OF_YEAR, DAY_NAME, AMPM, HOUR, MINUTE, SECOND, FRACTION,
        OPTIONAL_FRACTION, OFFSET, ZONE_ID
    }

    private static final Set<Kind> NUMERIC = EnumSet.of(Kind.YEAR, Kind.YEAR2, Kind.MONTH, Kind.DAY,
            Kind.DAY_OF_YEAR, Kind.HOUR, Kind.MINUTE, Kind.SECOND, Kind.FRACTION);

    /** What the text holds, and so which value the reader gives. */
    enum Shape {
        LOCAL_DATE, YEAR_MONTH, YEAR, MONTH_DAY, LOCAL_TIME, OFFSET_TIME, LOCAL_DATE_TIME, OFFSET_DATE_TIME,
        ZONED_DATE_TIME
    }

    /**
     * One step of reading and writing. {@code letter} and {@code count} as written in the pattern; for an offset of a
     * named form {@code iso} accepts {@code ±hh} too; {@code names} for a field written as words; {@code fixed} where
     * the next step is a number, so the width is the count.
     */
    private record Step(Kind kind, char letter, int count, String literal, boolean iso, List<String> names,
            List<String> others, boolean fixed, String label) {
    }

    private final List<Step> steps;
    private final boolean lenient;
    private final ZoneId zone;
    private final Shape shape;
    private final boolean offsetInText;
    private final boolean zoneInText;
    private final String formText;

    /**
     * A reader and writer of {@code form}.
     *
     * @throws IllegalArgumentException      where the form is not well-formed: not exactly one of named form and
     *                                       pattern, a pattern that is no LDML, an unknown zone or language, a time
     *                                       without a date with a zone whose offset changes
     * @throws UnsupportedOperationException where a well-formed pattern holds fields this reader does not read
     */
    TemporalTextCodec(TemporalText form) {
        if (form.isSetNamed() == (form.getPattern() != null)) {
            throw new IllegalArgumentException("a temporal text names exactly one of a named form and a pattern");
        }
        this.lenient = form.isLenient();
        this.zone = form.getZone() == null ? null : zone(form.getZone());
        Locale locale = form.getLanguage() == null ? null : language(form.getLanguage());
        List<Step> raw = form.isSetNamed() ? named(form.getNamed()) : pattern(DatePattern.parse(form.getPattern()),
                locale);
        this.formText = form.isSetNamed() ? form.getNamed().getLiteral() : form.getPattern();
        List<Step> fixedSteps = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Step s = raw.get(i);
            boolean fixed = NUMERIC.contains(s.kind()) && i + 1 < raw.size() && NUMERIC.contains(raw.get(i + 1).kind());
            fixedSteps.add(new Step(s.kind(), s.letter(), s.count(), s.literal(), s.iso(), s.names(), s.others(), fixed,
                    s.label()));
        }
        this.steps = List.copyOf(fixedSteps);
        this.offsetInText = has(Kind.OFFSET);
        this.zoneInText = has(Kind.ZONE_ID);
        this.shape = computeShape();
    }

    /** What the text holds, and so the kind of value the reader gives. */
    Shape shape() {
        return shape;
    }

    private Shape computeShape() {
        for (Kind k : Kind.values()) {
            if (k != Kind.LITERAL && steps.stream().filter(s -> s.kind() == k).count() > 1) {
                throw new UnsupportedOperationException(formText + ": the " + label(k) + " is written twice");
            }
        }
        boolean y = has(Kind.YEAR) || has(Kind.YEAR2);
        boolean m = has(Kind.MONTH) || has(Kind.MONTH_NAME);
        boolean d = has(Kind.DAY);
        boolean doy = has(Kind.DAY_OF_YEAR);
        boolean hour = has(Kind.HOUR);
        Shape date;
        if (y && m && d && !doy || y && doy && !m && !d) {
            date = Shape.LOCAL_DATE;
        } else if (y && m && !d && !doy) {
            date = Shape.YEAR_MONTH;
        } else if (y && !m && !d && !doy) {
            date = Shape.YEAR;
        } else if (!y && m && d && !doy) {
            date = Shape.MONTH_DAY;
        } else if (!y && !m && !d && !doy) {
            date = null;
        } else {
            throw new UnsupportedOperationException(formText + ": the fields give no date (a year with a month and a "
                    + "day, or with a day of the year)");
        }
        if (has(Kind.DAY_NAME) && date != Shape.LOCAL_DATE) {
            throw new UnsupportedOperationException(formText + ": the name of the day goes with a whole date");
        }
        if (has(Kind.MINUTE) && !hour || has(Kind.SECOND) && !has(Kind.MINUTE)
                || (has(Kind.FRACTION) || has(Kind.OPTIONAL_FRACTION)) && !has(Kind.SECOND)) {
            throw new UnsupportedOperationException(formText + ": a minute goes with an hour, a second with a minute, "
                    + "a fraction with a second");
        }
        Step hourStep = steps.stream().filter(s -> s.kind() == Kind.HOUR).findFirst().orElse(null);
        boolean twelve = hourStep != null && (hourStep.letter() == 'h' || hourStep.letter() == 'K');
        if (twelve != has(Kind.AMPM)) {
            throw new UnsupportedOperationException(formText + ": an hour 1 to 12 or 0 to 11 goes with AM or PM (a), "
                    + "and a with it");
        }
        if (!hour) {
            if (offsetInText || zoneInText) {
                throw new UnsupportedOperationException(formText + ": a date with an offset or a zone is no value of "
                        + "java.time");
            }
            if (date == null) {
                throw new UnsupportedOperationException(formText + ": the pattern holds neither a date nor a time");
            }
            return date;
        }
        if (date == null) {
            if (zoneInText) {
                throw new UnsupportedOperationException(formText + ": a time without a date holds no zone id");
            }
            if (offsetInText) {
                return Shape.OFFSET_TIME;
            }
            if (zone == null) {
                return Shape.LOCAL_TIME;
            }
            if (fixed(zone)) {
                return Shape.OFFSET_TIME;
            }
            throw new IllegalArgumentException(formText + ": a time without a date cannot take the zone " + zone.getId()
                    + ", whose offset changes; name a fixed offset");
        }
        if (date != Shape.LOCAL_DATE) {
            throw new UnsupportedOperationException(formText + ": a time of day goes with a whole date");
        }
        if (zoneInText) {
            return Shape.ZONED_DATE_TIME;
        }
        if (offsetInText) {
            return Shape.OFFSET_DATE_TIME;
        }
        if (zone == null) {
            return Shape.LOCAL_DATE_TIME;
        }
        return fixed(zone) ? Shape.OFFSET_DATE_TIME : Shape.ZONED_DATE_TIME;
    }

    private boolean has(Kind kind) {
        return steps.stream().anyMatch(s -> s.kind() == kind);
    }

    // ------------------------------------------------------------------ forms

    private static List<Step> named(TemporalForm form) {
        List<Step> date = List.of(num(Kind.YEAR, 'y', 4), lit("-"), num(Kind.MONTH, 'M', 2), lit("-"),
                num(Kind.DAY, 'd', 2));
        List<Step> basicDate = List.of(num(Kind.YEAR, 'y', 4), num(Kind.MONTH, 'M', 2), num(Kind.DAY, 'd', 2));
        List<Step> time = List.of(num(Kind.HOUR, 'H', 2), lit(":"), num(Kind.MINUTE, 'm', 2), lit(":"),
                num(Kind.SECOND, 's', 2), optionalFraction());
        List<Step> basicTime = List.of(num(Kind.HOUR, 'H', 2), num(Kind.MINUTE, 'm', 2), num(Kind.SECOND, 's', 2),
                optionalFraction());
        Step extendedOffset = new Step(Kind.OFFSET, 'X', 3, null, true, null, null, false, "offset");
        Step basicOffset = new Step(Kind.OFFSET, 'X', 2, null, true, null, null, false, "offset");
        List<Step> steps = new ArrayList<>();
        switch (form) {
        case DATE -> steps.addAll(date);
        case BASIC_DATE -> steps.addAll(basicDate);
        case TIME, TIME_WITH_OFFSET -> steps.addAll(time);
        case BASIC_TIME, BASIC_TIME_WITH_OFFSET -> steps.addAll(basicTime);
        case DATE_TIME, DATE_TIME_WITH_OFFSET -> {
            steps.addAll(date);
            steps.add(lit("T"));
            steps.addAll(time);
        }
        case BASIC_DATE_TIME, BASIC_DATE_TIME_WITH_OFFSET -> {
            steps.addAll(basicDate);
            steps.add(lit("T"));
            steps.addAll(basicTime);
        }
        }
        switch (form) {
        case TIME_WITH_OFFSET, DATE_TIME_WITH_OFFSET -> steps.add(extendedOffset);
        case BASIC_TIME_WITH_OFFSET, BASIC_DATE_TIME_WITH_OFFSET -> steps.add(basicOffset);
        default -> {
        }
        }
        return steps;
    }

    private static Step num(Kind kind, char letter, int count) {
        return new Step(kind, letter, count, null, false, null, null, false, label(kind));
    }

    private static Step lit(String text) {
        return new Step(Kind.LITERAL, ' ', 0, text, false, null, null, false, "'" + text + "'");
    }

    private static Step optionalFraction() {
        return new Step(Kind.OPTIONAL_FRACTION, 'S', 9, null, true, null, null, false, "fraction");
    }

    private static List<Step> pattern(DatePattern pattern, Locale locale) {
        List<Step> steps = new ArrayList<>();
        for (Token t : pattern.tokens) {
            if (t instanceof Literal l) {
                steps.add(lit(l.text()));
                continue;
            }
            Field f = (Field) t;
            if (DatePattern.isText(f) && locale == null) {
                throw new IllegalArgumentException(pattern.source + ": " + f.written()
                        + " is written in a language; name one");
            }
            steps.add(switch (f.letter()) {
            case 'y', 'u' -> f.count() == 2 ? num(Kind.YEAR2, f.letter(), 2) : num(Kind.YEAR, f.letter(), f.count());
            case 'M', 'L' -> {
                if (f.count() <= 2) {
                    yield num(Kind.MONTH, f.letter(), f.count());
                }
                if (f.count() == 5) {
                    throw unsupported(pattern, f, "narrow names are one letter for several months");
                }
                boolean standalone = f.letter() == 'L';
                TextStyle shortStyle = standalone ? TextStyle.SHORT_STANDALONE : TextStyle.SHORT;
                TextStyle fullStyle = standalone ? TextStyle.FULL_STANDALONE : TextStyle.FULL;
                List<String> shortNames = new ArrayList<>();
                List<String> fullNames = new ArrayList<>();
                for (Month month : Month.values()) {
                    shortNames.add(month.getDisplayName(shortStyle, locale));
                    fullNames.add(month.getDisplayName(fullStyle, locale));
                }
                yield new Step(Kind.MONTH_NAME, f.letter(), f.count(), null, false,
                        List.copyOf(f.count() == 3 ? shortNames : fullNames),
                        List.copyOf(f.count() == 3 ? fullNames : shortNames), false, "name of the month");
            }
            case 'd' -> num(Kind.DAY, 'd', f.count());
            case 'D' -> num(Kind.DAY_OF_YEAR, 'D', f.count());
            case 'E' -> {
                if (f.count() > 4) {
                    throw unsupported(pattern, f, "narrow and short names are refused, several days share them");
                }
                List<String> shortNames = new ArrayList<>();
                List<String> fullNames = new ArrayList<>();
                for (DayOfWeek day : DayOfWeek.values()) {
                    shortNames.add(day.getDisplayName(TextStyle.SHORT, locale));
                    fullNames.add(day.getDisplayName(TextStyle.FULL, locale));
                }
                yield new Step(Kind.DAY_NAME, 'E', f.count(), null, false,
                        List.copyOf(f.count() == 4 ? fullNames : shortNames),
                        List.copyOf(f.count() == 4 ? shortNames : fullNames), false, "name of the day");
            }
            case 'a' -> {
                if (f.count() > 3) {
                    throw unsupported(pattern, f, "only the abbreviated AM and PM are read");
                }
                DateTimeFormatter marker = DateTimeFormatter.ofPattern("a", locale);
                yield new Step(Kind.AMPM, 'a', f.count(), null, false,
                        List.of(marker.format(LocalTime.of(1, 0)), marker.format(LocalTime.of(13, 0))), null, false,
                        "AM or PM");
            }
            case 'h', 'H', 'K', 'k' -> num(Kind.HOUR, f.letter(), f.count());
            case 'm' -> num(Kind.MINUTE, 'm', f.count());
            case 's' -> num(Kind.SECOND, 's', f.count());
            case 'S' -> num(Kind.FRACTION, 'S', f.count());
            case 'X', 'x' -> new Step(Kind.OFFSET, f.letter(), f.count(), null, false, null, null, false, "offset");
            case 'Z' -> {
                if (f.count() == 4) {
                    throw unsupported(pattern, f, "the localized GMT form is refused");
                }
                yield new Step(Kind.OFFSET, 'Z', f.count(), null, false, null, null, false, "offset");
            }
            case 'V' -> {
                if (f.count() != 2) {
                    throw unsupported(pattern, f, "only VV, the zone id, is read");
                }
                yield new Step(Kind.ZONE_ID, 'V', 2, null, false, null, null, false, "zone id");
            }
            default -> throw unsupported(pattern, f, "this reader does not read the " + DatePattern.name(f.letter()));
            });
        }
        return steps;
    }

    private static UnsupportedOperationException unsupported(DatePattern pattern, Field f, String why) {
        return new UnsupportedOperationException(pattern.source + ": " + f.written() + " at position " + f.position()
                + ": " + why);
    }

    private static String label(Kind kind) {
        return switch (kind) {
        case YEAR, YEAR2 -> "year";
        case MONTH -> "month";
        case MONTH_NAME -> "name of the month";
        case DAY -> "day";
        case DAY_OF_YEAR -> "day of the year";
        case DAY_NAME -> "name of the day";
        case AMPM -> "AM or PM";
        case HOUR -> "hour";
        case MINUTE -> "minute";
        case SECOND -> "second";
        case FRACTION, OPTIONAL_FRACTION -> "fraction";
        case OFFSET -> "offset";
        case ZONE_ID -> "zone id";
        case LITERAL -> "text";
        };
    }

    // ------------------------------------------------------------------ reading

    /** The fields read from a text, with the position of each. */
    private static final class Parsed {
        int year = -1;
        int month = -1;
        int day = -1;
        int dayOfYear = -1;
        int dayOfWeek = -1;
        int ampm = -1;
        int hour = -1;
        char hourLetter;
        int minute;
        int second;
        int nano;
        ZoneOffset offset;
        ZoneId zoneId;
        int dayPos;
        int dayOfWeekPos;
        int hourPos;
        int offsetPos;
    }

    @Override
    public Object read(String text, int start, int end) {
        Parsed p = new Parsed();
        int pos = start;
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            Step next = i + 1 < steps.size() ? steps.get(i + 1) : null;
            pos = switch (s.kind()) {
            case LITERAL -> literal(text, pos, end, s);
            case MONTH_NAME, DAY_NAME, AMPM -> name(text, pos, end, s, next, p);
            case OPTIONAL_FRACTION -> optionalFraction(text, pos, end, p);
            case OFFSET -> offset(text, pos, end, s, p);
            case ZONE_ID -> zoneId(text, pos, end, p);
            default -> number(text, pos, end, s, p);
            };
        }
        if (pos < end) {
            throw new UnfitTextException(text, pos, "after the " + label(steps.get(steps.size() - 1).kind())
                    + " the text goes on with " + FormCodec.found(text, pos, end) + "; " + formText + " ends there");
        }
        return resolve(text, start, p);
    }

    private int literal(String text, int pos, int end, Step s) {
        String lit = s.literal();
        if (pos + lit.length() <= end && text.regionMatches(lenient, pos, lit, 0, lit.length())) {
            return pos + lit.length();
        }
        if (lenient && lit.equals("T") && pos < end && text.charAt(pos) == ' ') {
            return pos + 1;
        }
        throw new UnfitTextException(text, pos, "expected '" + lit + "', found " + FormCodec.found(text, pos, end));
    }

    private int number(String text, int pos, int end, Step s, Parsed p) {
        int max = switch (s.kind()) {
        case YEAR -> 9;
        case DAY_OF_YEAR -> 3;
        case FRACTION -> lenient ? Math.max(9, s.count()) : s.count();
        default -> 2;
        };
        int min = lenient ? 1 : switch (s.kind()) {
        case YEAR2 -> 2;
        case FRACTION -> 1;
        default -> s.count();
        };
        if (s.fixed()) {
            min = s.count();
            max = s.count();
        }
        int digitsEnd = pos;
        while (digitsEnd < end && digitsEnd - pos < max && isDigit(text.charAt(digitsEnd))) {
            digitsEnd++;
        }
        int n = digitsEnd - pos;
        if (n == 0) {
            throw new UnfitTextException(text, pos, "expected the " + s.label() + " as digits, found "
                    + FormCodec.found(text, pos, end));
        }
        if (n < min) {
            throw new UnfitTextException(text, pos, "the " + s.label() + " has " + n + (n == 1 ? " digit" : " digits")
                    + ", " + s.letter() + (s.count() > 1 ? " written " + s.count() + " times" : "")
                    + " reads " + (min == max ? "" : "at least ") + min);
        }
        String digits = text.substring(pos, digitsEnd);
        if (s.kind() == Kind.FRACTION) {
            p.nano = Integer.parseInt((digits + "000000000").substring(0, 9));
            return digitsEnd;
        }
        int value = Integer.parseInt(digits);
        switch (s.kind()) {
        case YEAR -> p.year = value;
        case YEAR2 -> p.year = 2000 + value;
        case MONTH -> p.month = range(text, pos, value, 1, 12, "month");
        case DAY -> {
            p.day = range(text, pos, value, 1, 31, "day");
            p.dayPos = pos;
        }
        case DAY_OF_YEAR -> {
            p.dayOfYear = range(text, pos, value, 1, 366, "day of the year");
            p.dayPos = pos;
        }
        case HOUR -> {
            switch (s.letter()) {
            case 'H' -> range(text, pos, value, 0, 23, "hour (H)");
            case 'k' -> range(text, pos, value, 1, 24, "hour (k)");
            case 'h' -> range(text, pos, value, 1, 12, "hour (h)");
            default -> range(text, pos, value, 0, 11, "hour (K)");
            }
            p.hour = value;
            p.hourLetter = s.letter();
            p.hourPos = pos;
        }
        case MINUTE -> p.minute = range(text, pos, value, 0, 59, "minute");
        case SECOND -> p.second = range(text, pos, value, 0, 59, "second");
        default -> throw new IllegalStateException(s.kind().name());
        }
        return digitsEnd;
    }

    private static int range(String text, int pos, int value, int min, int max, String what) {
        if (value < min || value > max) {
            throw new UnfitTextException(text, pos, value + " is no " + what + " (" + min + " to " + max + ")");
        }
        return value;
    }

    private int name(String text, int pos, int end, Step s, Step next, Parsed p) {
        List<String> names = s.names();
        int index = -1;
        int after = pos;
        if (!lenient) {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                order.add(i);
            }
            order.sort(Comparator.comparingInt((Integer i) -> names.get(i).length()).reversed());
            for (int i : order) {
                String name = names.get(i);
                if (pos + name.length() <= end && text.startsWith(name, pos)) {
                    index = i;
                    after = pos + name.length();
                    break;
                }
            }
        } else {
            int wordEnd = pos;
            while (wordEnd < end && Character.isLetter(text.charAt(wordEnd))) {
                wordEnd++;
            }
            String word = text.substring(pos, wordEnd);
            index = lenientName(word, s, names);
            after = wordEnd;
            boolean dotFollows = next != null && next.kind() == Kind.LITERAL && next.literal().startsWith(".");
            if (index >= 0 && after < end && text.charAt(after) == '.' && !dotFollows) {
                after++;
            }
        }
        if (index < 0) {
            throw new UnfitTextException(text, pos, "expected the " + s.label() + " (" + String.join(", ", names)
                    + "), found " + FormCodec.found(text, pos, end));
        }
        switch (s.kind()) {
        case MONTH_NAME -> p.month = index + 1;
        case DAY_NAME -> {
            p.dayOfWeek = index + 1;
            p.dayOfWeekPos = pos;
        }
        default -> p.ampm = index;
        }
        return after;
    }

    /**
     * The index of {@code word} among the names, in any case and without a trailing dot, among the abbreviated and the
     * full names; else the one name that begins with the word of three or more letters.
     */
    private static int lenientName(String word, Step s, List<String> names) {
        if (word.isEmpty()) {
            return -1;
        }
        List<List<String>> all = new ArrayList<>(List.of(names));
        if (s.others() != null) {
            all.add(s.others());
        }
        for (List<String> list : all) {
            for (int i = 0; i < list.size(); i++) {
                if (bare(list.get(i)).equalsIgnoreCase(word)) {
                    return i;
                }
            }
        }
        if (word.length() < 3) {
            return -1;
        }
        int found = -1;
        for (List<String> list : all) {
            for (int i = 0; i < list.size(); i++) {
                String name = bare(list.get(i));
                if (name.length() >= word.length() && name.regionMatches(true, 0, word, 0, word.length())) {
                    if (found >= 0 && found != i) {
                        return -1;
                    }
                    found = i;
                }
            }
        }
        return found;
    }

    private static String bare(String name) {
        return name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
    }

    private int optionalFraction(String text, int pos, int end, Parsed p) {
        if (pos < end && (text.charAt(pos) == '.' || text.charAt(pos) == ',') && pos + 1 < end
                && isDigit(text.charAt(pos + 1))) {
            int digitsEnd = pos + 1;
            while (digitsEnd < end && isDigit(text.charAt(digitsEnd))) {
                digitsEnd++;
            }
            if (digitsEnd - pos - 1 > 9) {
                throw new UnfitTextException(text, pos + 10, "a fraction of a second has at most 9 digits");
            }
            p.nano = Integer.parseInt((text.substring(pos + 1, digitsEnd) + "000000000").substring(0, 9));
            return digitsEnd;
        }
        return pos;
    }

    private int offset(String text, int pos, int end, Step s, Parsed p) {
        p.offsetPos = pos;
        boolean zAllowed = s.letter() == 'X' || s.letter() == 'Z' && s.count() == 5 || lenient;
        if (pos < end && (text.charAt(pos) == 'Z' || lenient && text.charAt(pos) == 'z')) {
            if (!zAllowed) {
                throw new UnfitTextException(text, pos, s.letter() + " writes a zero offset as digits, not Z");
            }
            p.offset = ZoneOffset.UTC;
            return pos + 1;
        }
        if (pos >= end || text.charAt(pos) != '+' && text.charAt(pos) != '-') {
            throw new UnfitTextException(text, pos, "expected an offset (" + offsetShape(s) + "), found "
                    + FormCodec.found(text, pos, end));
        }
        int sign = text.charAt(pos) == '-' ? -1 : 1;
        int i = pos + 1;
        int hours = twoDigits(text, i, end, "hours of the offset");
        i += 2;
        boolean colon = s.count() == 3 || s.count() == 5;
        boolean minutesRequired = !s.iso() && s.count() >= 2;
        boolean secondsAllowed = !s.iso() && (s.count() == 4 || s.count() == 5);
        if (s.letter() == 'Z' && s.count() <= 3) {
            colon = false;
            minutesRequired = true;
            secondsAllowed = true;
        }
        int minutes = 0;
        int seconds = 0;
        boolean hasColon = i < end && text.charAt(i) == ':';
        if (lenient) {
            colon = hasColon;
            minutesRequired = false;
            secondsAllowed = true;
        }
        if (colon ? hasColon && i + 1 < end && isDigit(text.charAt(i + 1)) : i < end && isDigit(text.charAt(i))) {
            i += colon ? 1 : 0;
            minutes = twoDigits(text, i, end, "minutes of the offset");
            i += 2;
            boolean more = colon ? i + 1 < end && text.charAt(i) == ':' && isDigit(text.charAt(i + 1))
                    : i < end && isDigit(text.charAt(i));
            if (secondsAllowed && more) {
                i += colon ? 1 : 0;
                seconds = twoDigits(text, i, end, "seconds of the offset");
                i += 2;
            }
        } else if (minutesRequired) {
            throw new UnfitTextException(text, i, "expected the minutes of the offset (" + offsetShape(s)
                    + "), found " + FormCodec.found(text, i, end));
        }
        try {
            p.offset = ZoneOffset.ofHoursMinutesSeconds(sign * hours, sign * minutes, sign * seconds);
        } catch (DateTimeException e) {
            throw new UnfitTextException(text, pos, "no offset: " + e.getMessage());
        }
        return i;
    }

    private static String offsetShape(Step s) {
        if (s.iso()) {
            return s.count() == 3 ? "Z or +hh:mm" : "Z or +hhmm";
        }
        String zero = s.letter() == 'X' || s.letter() == 'Z' && s.count() == 5 ? "Z or " : "";
        return zero + switch (s.letter() == 'Z' && s.count() <= 3 ? 2 : s.count()) {
        case 1 -> "+hh or +hhmm";
        case 2 -> "+hhmm";
        case 3 -> "+hh:mm";
        case 4 -> "+hhmm or +hhmmss";
        default -> "+hh:mm or +hh:mm:ss";
        };
    }

    private static int twoDigits(String text, int pos, int end, String what) {
        if (pos + 2 > end || !isDigit(text.charAt(pos)) || !isDigit(text.charAt(pos + 1))) {
            throw new UnfitTextException(text, pos, "expected two digits of the " + what + ", found "
                    + FormCodec.found(text, pos, end));
        }
        return Integer.parseInt(text.substring(pos, pos + 2));
    }

    private int zoneId(String text, int pos, int end, Parsed p) {
        int i = pos;
        while (i < end && (Character.isLetterOrDigit(text.charAt(i)) || "/_+-:.".indexOf(text.charAt(i)) >= 0)) {
            i++;
        }
        if (i == pos) {
            throw new UnfitTextException(text, pos, "expected a zone id, found " + FormCodec.found(text, pos, end));
        }
        try {
            p.zoneId = ZoneId.of(text.substring(pos, i));
        } catch (DateTimeException e) {
            throw new UnfitTextException(text, pos, "'" + text.substring(pos, i) + "' is no zone id");
        }
        return i;
    }

    private Object resolve(String text, int start, Parsed p) {
        LocalDate date = null;
        switch (shape) {
        case YEAR_MONTH:
            return YearMonth.of(p.year, p.month);
        case YEAR:
            return Year.of(p.year);
        case MONTH_DAY:
            if (p.day > Month.of(p.month).maxLength()) {
                throw new UnfitTextException(text, p.dayPos, Month.of(p.month).getDisplayName(TextStyle.FULL,
                        Locale.ENGLISH) + " has no day " + p.day);
            }
            return MonthDay.of(p.month, p.day);
        case LOCAL_TIME, OFFSET_TIME:
            break;
        default:
            date = date(text, p);
        }
        if (shape == Shape.LOCAL_DATE) {
            return date;
        }
        int hour = switch (p.hourLetter) {
        case 'k' -> p.hour % 24;
        case 'h' -> p.hour % 12 + (p.ampm == 1 ? 12 : 0);
        case 'K' -> p.hour + (p.ampm == 1 ? 12 : 0);
        default -> p.hour;
        };
        LocalTime time = LocalTime.of(hour, p.minute, p.second, p.nano);
        switch (shape) {
        case LOCAL_TIME:
            return time;
        case OFFSET_TIME:
            return OffsetTime.of(time, p.offset != null ? p.offset : fixedOffset(zone));
        case LOCAL_DATE_TIME:
            return LocalDateTime.of(date, time);
        case OFFSET_DATE_TIME:
            return OffsetDateTime.of(date, time, p.offset != null ? p.offset : fixedOffset(zone));
        default:
            LocalDateTime local = LocalDateTime.of(date, time);
            ZoneId z = p.zoneId != null ? p.zoneId : zone;
            if (p.offset != null) {
                try {
                    return ZonedDateTime.ofStrict(local, p.offset, z);
                } catch (DateTimeException e) {
                    throw new UnfitTextException(text, p.offsetPos, "the offset " + p.offset + " is not that of "
                            + z.getId() + " at " + local);
                }
            }
            ZonedDateTime zoned = ZonedDateTime.ofLocal(local, z, null);
            if (!lenient && !zoned.toLocalDateTime().equals(local)) {
                throw new UnfitTextException(text, p.hourPos, local + " does not exist in " + z.getId()
                        + ": the clocks moved forward");
            }
            return zoned;
        }
    }

    private LocalDate date(String text, Parsed p) {
        LocalDate date;
        if (p.dayOfYear >= 0) {
            if (p.dayOfYear > Year.of(p.year).length()) {
                throw new UnfitTextException(text, p.dayPos, p.year + " has " + Year.of(p.year).length()
                        + " days, not " + p.dayOfYear);
            }
            date = LocalDate.ofYearDay(p.year, p.dayOfYear);
        } else {
            YearMonth ym = YearMonth.of(p.year, p.month);
            if (p.day > ym.lengthOfMonth()) {
                throw new UnfitTextException(text, p.dayPos, ym.getMonth().getDisplayName(TextStyle.FULL,
                        Locale.ENGLISH) + " " + p.year + " has " + ym.lengthOfMonth() + " days, not " + p.day);
            }
            date = ym.atDay(p.day);
        }
        if (p.dayOfWeek > 0 && !lenient && date.getDayOfWeek().getValue() != p.dayOfWeek) {
            throw new UnfitTextException(text, p.dayOfWeekPos, date + " is a "
                    + date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + ", not a "
                    + DayOfWeek.of(p.dayOfWeek).getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        }
        return date;
    }

    // ------------------------------------------------------------------ writing

    @Override
    public String write(Object value) {
        TemporalAccessor t = adapt(value);
        StringBuilder out = new StringBuilder();
        for (Step s : steps) {
            switch (s.kind()) {
            case LITERAL -> out.append(s.literal());
            case YEAR -> {
                int year = get(t, ChronoField.YEAR, s, value);
                if (year < 1) {
                    throw new IllegalArgumentException("the year " + year + " of " + value + " is not written; years "
                            + "start at 1");
                }
                out.append(pad(year, s.count()));
            }
            case YEAR2 -> {
                int year = get(t, ChronoField.YEAR, s, value);
                if (year < 2000 || year > 2099) {
                    throw new IllegalArgumentException(s.letter() + s.letter() + " writes the years 2000 to 2099, not "
                            + year);
                }
                out.append(pad(year - 2000, 2));
            }
            case MONTH -> out.append(pad(get(t, ChronoField.MONTH_OF_YEAR, s, value), s.count()));
            case MONTH_NAME -> out.append(s.names().get(get(t, ChronoField.MONTH_OF_YEAR, s, value) - 1));
            case DAY -> out.append(pad(get(t, ChronoField.DAY_OF_MONTH, s, value), s.count()));
            case DAY_OF_YEAR -> out.append(pad(get(t, ChronoField.DAY_OF_YEAR, s, value), s.count()));
            case DAY_NAME -> out.append(s.names().get(get(t, ChronoField.DAY_OF_WEEK, s, value) - 1));
            case AMPM -> out.append(s.names().get(get(t, ChronoField.AMPM_OF_DAY, s, value)));
            case HOUR -> out.append(pad(get(t, switch (s.letter()) {
            case 'k' -> ChronoField.CLOCK_HOUR_OF_DAY;
            case 'h' -> ChronoField.CLOCK_HOUR_OF_AMPM;
            case 'K' -> ChronoField.HOUR_OF_AMPM;
            default -> ChronoField.HOUR_OF_DAY;
            }, s, value), s.count()));
            case MINUTE -> out.append(pad(get(t, ChronoField.MINUTE_OF_HOUR, s, value), s.count()));
            case SECOND -> out.append(pad(get(t, ChronoField.SECOND_OF_MINUTE, s, value), s.count()));
            case FRACTION -> {
                String nine = pad(get(t, ChronoField.NANO_OF_SECOND, s, value), 9);
                out.append(s.count() <= 9 ? nine.substring(0, s.count()) : nine + "0".repeat(s.count() - 9));
            }
            case OPTIONAL_FRACTION -> {
                int nano = get(t, ChronoField.NANO_OF_SECOND, s, value);
                if (nano != 0) {
                    out.append('.').append(pad(nano, 9).replaceAll("0+$", ""));
                }
            }
            case OFFSET -> out.append(writeOffset(t.query(TemporalQueries.offset()), s));
            case ZONE_ID -> out.append(t.query(TemporalQueries.zone()).getId());
            }
        }
        return out.toString();
    }

    private static int get(TemporalAccessor t, ChronoField field, Step s, Object value) {
        if (!t.isSupported(field)) {
            throw new IllegalArgumentException(value + " has no " + s.label() + " to write");
        }
        return t.get(field);
    }

    private static String pad(int value, int width) {
        String digits = Integer.toString(value);
        return digits.length() >= width ? digits : "0".repeat(width - digits.length()) + digits;
    }

    /** The value in the shape of the form: in the zone of the form where it names one. */
    private TemporalAccessor adapt(Object value) {
        Object v = value;
        if (v instanceof Instant instant) {
            if (zone != null) {
                v = ZonedDateTime.ofInstant(instant, zone);
            } else if (offsetInText || zoneInText) {
                v = instant.atOffset(ZoneOffset.UTC);
            } else {
                throw new IllegalArgumentException("the instant " + instant + " has no zone to be written as "
                        + formText + "; the form names none");
            }
        }
        if (zone != null) {
            if (v instanceof OffsetDateTime o) {
                v = o.atZoneSameInstant(zone);
            } else if (v instanceof ZonedDateTime z) {
                v = z.withZoneSameInstant(zone);
            } else if (v instanceof OffsetTime o && fixed(zone)) {
                v = o.withOffsetSameInstant(fixedOffset(zone));
            }
        }
        boolean instant = v instanceof OffsetDateTime || v instanceof ZonedDateTime || v instanceof OffsetTime;
        switch (shape) {
        case LOCAL_DATE, YEAR_MONTH, YEAR, MONTH_DAY:
            if (v instanceof TemporalAccessor t && t.isSupported(ChronoField.YEAR) || v instanceof MonthDay) {
                return (TemporalAccessor) v;
            }
            break;
        case LOCAL_TIME, LOCAL_DATE_TIME:
            if (instant) {
                throw new IllegalArgumentException(value + " is an instant; " + formText + " writes a local "
                        + (shape == Shape.LOCAL_TIME ? "time" : "date and time") + " and the form names no zone");
            }
            if (v instanceof LocalTime && shape == Shape.LOCAL_TIME || v instanceof LocalDateTime) {
                return (TemporalAccessor) v;
            }
            break;
        case OFFSET_TIME:
            if (v instanceof OffsetTime) {
                return (TemporalAccessor) v;
            }
            if (v instanceof LocalTime l && zone != null) {
                return l.atOffset(fixedOffset(zone));
            }
            if (v instanceof LocalTime) {
                throw new IllegalArgumentException(value + " is a local time; " + formText + " writes an offset and "
                        + "the form names no zone");
            }
            break;
        case OFFSET_DATE_TIME, ZONED_DATE_TIME:
            if (v instanceof OffsetDateTime o) {
                return shape == Shape.ZONED_DATE_TIME ? o.toZonedDateTime() : o;
            }
            if (v instanceof ZonedDateTime z) {
                return z;
            }
            if (v instanceof LocalDateTime l) {
                if (zone == null) {
                    throw new IllegalArgumentException(value + " is a local date and time; " + formText
                            + " writes an offset or a zone and the form names no zone");
                }
                ZonedDateTime z = ZonedDateTime.ofLocal(l, zone, null);
                if (!z.toLocalDateTime().equals(l)) {
                    throw new IllegalArgumentException(l + " does not exist in " + zone.getId()
                            + ": the clocks moved forward");
                }
                return z;
            }
            break;
        }
        throw new IllegalArgumentException(value + (value == null ? "" : " (" + value.getClass().getSimpleName() + ")")
                + " is no value " + formText + " writes");
    }

    private static String writeOffset(ZoneOffset offset, Step s) {
        int total = offset.getTotalSeconds();
        boolean zAllowed = s.letter() == 'X' || s.letter() == 'Z' && s.count() == 5;
        if (total == 0 && zAllowed) {
            return "Z";
        }
        int abs = Math.abs(total);
        String sign = total < 0 ? "-" : "+";
        String hh = pad(abs / 3600, 2);
        String mm = pad(abs / 60 % 60, 2);
        String ss = pad(abs % 60, 2);
        int count = s.letter() == 'Z' && s.count() <= 3 ? 4 : s.count();
        return sign + switch (count) {
        case 1 -> hh + (abs / 60 % 60 == 0 ? "" : mm);
        case 2 -> hh + mm;
        case 3 -> hh + ":" + mm;
        case 4 -> hh + mm + (abs % 60 == 0 ? "" : ss);
        default -> hh + ":" + mm + (abs % 60 == 0 ? "" : ":" + ss);
        };
    }

    // ------------------------------------------------------------------ zone and language

    /**
     * The zone of a form: an IANA zone or a fixed offset.
     *
     * @throws IllegalArgumentException where it is neither
     */
    static ZoneId zone(String zone) {
        try {
            return ZoneId.of(zone.trim());
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("'" + zone + "' is no zone: neither an IANA zone (Europe/Berlin) nor "
                    + "a fixed offset (+01:00)", e);
        }
    }

    /**
     * The locale of a BCP 47 tag.
     *
     * @throws IllegalArgumentException where the tag is not well-formed
     */
    static Locale language(String tag) {
        try {
            return new Locale.Builder().setLanguageTag(tag).build();
        } catch (java.util.IllformedLocaleException e) {
            throw new IllegalArgumentException("'" + tag + "' is no BCP 47 language tag", e);
        }
    }

    /** Whether a zone has one offset at every instant. */
    static boolean fixed(ZoneId zone) {
        return zone.normalized() instanceof ZoneOffset || zone.getRules().isFixedOffset();
    }

    private static ZoneOffset fixedOffset(ZoneId zone) {
        return zone.normalized() instanceof ZoneOffset o ? o : zone.getRules().getOffset(Instant.EPOCH);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
