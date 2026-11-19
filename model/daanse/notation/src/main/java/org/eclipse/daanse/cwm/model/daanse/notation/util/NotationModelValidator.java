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
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.daanse.notation.BooleanText;
import org.eclipse.daanse.cwm.model.daanse.notation.Form;
import org.eclipse.daanse.cwm.model.daanse.notation.NumberText;
import org.eclipse.daanse.cwm.model.daanse.notation.PatternText;
import org.eclipse.daanse.cwm.model.daanse.notation.Notation;
import org.eclipse.daanse.cwm.model.daanse.notation.TemporalForm;
import org.eclipse.daanse.cwm.model.daanse.notation.TemporalNumber;
import org.eclipse.daanse.cwm.model.daanse.notation.TemporalText;
import org.eclipse.daanse.cwm.model.daanse.notation.util.TypeKinds.Info;
import org.eclipse.daanse.cwm.model.daanse.notation.util.TypeKinds.Kind;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.eclipse.daanse.cwm.model.daanse.validation.Finding;
import org.eclipse.daanse.cwm.model.daanse.validation.Findings;
import org.eclipse.daanse.cwm.model.daanse.validation.Rule;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Checks notations and their forms beyond what the metamodel enforces. Errors stop a reader (the
 * {@link Codec} refuses the form), warnings do not.
 *
 * <ul>
 * <li>{@code NT1} where a notation attaches: an attribute (a column, a record field, a measure, a resource), a
 * parameter or a data type; never a class, a package or an association end; and no data type owns one (CWM 1.1,
 * C-3-2).</li>
 * <li>{@code NT2} an element carries at most one notation.</li>
 * <li>{@code NT3} a temporal text names exactly one of a named form and a pattern.</li>
 * <li>{@code NT4} a date pattern is well-formed LDML: quotes close, every unquoted letter is a field of LDML, written a
 * number of times the field takes.</li>
 * <li>{@code NT5} (warning) a date or number pattern lies outside the set CSV on the Web obliges every reader to know:
 * not every reader can read it.</li>
 * <li>{@code NT6} a pattern with fields written in words (names of months and days, AM and PM, eras, zone names)
 * names a language, and a language is a well-formed BCP 47 tag.</li>
 * <li>{@code NT7} a time of day meant as an instant (the type of the element holds an instant) has a zone in its text
 * or names a zone.</li>
 * <li>{@code NT8} (warning) a time of day with neither a zone in its text nor a zone named, where the type does not
 * say it is local: it is read as a local time.</li>
 * <li>{@code NT9} the unit of a temporal number is a unit of time (dimension T1) that counts from zero.</li>
 * <li>{@code NT10} the decimal and the group character are one character each, neither a digit, a sign nor E, and
 * differ.</li>
 * <li>{@code NT11} no word is true and false, and no missing word is a true or a false word (in any case where the
 * words are read in any case).</li>
 * <li>{@code NT12} the form fits the type of the element where the type is known: a stored type (an SQL type of a
 * column) holds what the form writes (text, or a number for a temporal number); the type of a value holds either
 * what is written or the value (a temporal text and a temporal number a time, a number text a number, a boolean text
 * a truth value).</li>
 * <li>{@code NT13} a zone is an IANA zone or a fixed offset, an epoch an ISO 8601 date or date and time, and a time
 * without a date names a fixed offset, not a zone whose offset changes.</li>
 * <li>{@code NT14} a temporal number names a zone only where its unit is a day or longer and its epoch is local.</li>
 * <li>{@code NT15} a number pattern is well-formed LDML and holds no percent or per-mille sign outside quotes: such a
 * sign scales the value, and a scale is a unit.</li>
 * <li>{@code NT16} the regular expression of a pattern text compiles.</li>
 * <li>{@code NT17} (warning) a reference that does not resolve.</li>
 * <li>{@code NT18} (warning) a regular expression holds constructs ECMAScript does not know.</li>
 * </ul>
 */
public final class NotationModelValidator {

    /** The module the rules of this validator belong to. */
    public static final String MODULE = "notation";

    public static final Rule NT1 = Rule.error(MODULE, "NT1",
            "A notation names attributes, parameters or data types, never an association end, a class or a "
                    + "package, and no data type owns one.");

    public static final Rule NT2 = Rule.error(MODULE, "NT2", "An element carries at most one notation.");

    public static final Rule NT3 = Rule.error(MODULE, "NT3",
            "A temporal text names exactly one of a named form and a pattern.");

    public static final Rule NT4 = Rule.error(MODULE, "NT4",
            "A date pattern is well-formed LDML: quotes close, every unquoted letter is a field written a number of "
                    + "times it takes.");

    public static final Rule NT5 = Rule.warning(MODULE, "NT5",
            "A date or number pattern lies within the set CSV on the Web obliges every reader to know.");

    public static final Rule NT6 = Rule.error(MODULE, "NT6",
            "A pattern with fields written in words names a language, a well-formed BCP 47 tag.");

    public static final Rule NT7 = Rule.error(MODULE, "NT7",
            "A time of day meant as an instant has a zone in its text or names a zone.");

    public static final Rule NT8 = Rule.warning(MODULE, "NT8",
            "A time of day without a zone in its text or named is read as a local time.");

    public static final Rule NT9 = Rule.error(MODULE, "NT9",
            "The unit of a temporal number is a unit of time that counts from zero.");

    public static final Rule NT10 = Rule.error(MODULE, "NT10",
            "The decimal and the group character are one character each, neither a digit, a sign nor E, and differ.");

    public static final Rule NT11 = Rule.error(MODULE, "NT11",
            "No word is true and false, and no missing word is a true or a false word.");

    public static final Rule NT12 = Rule.error(MODULE, "NT12", "The form fits the type of the element.");

    public static final Rule NT13 = Rule.error(MODULE, "NT13",
            "A zone is an IANA zone or a fixed offset, an epoch an ISO 8601 date or date and time, and a time without "
                    + "a date names a fixed offset.");

    public static final Rule NT14 = Rule.error(MODULE, "NT14",
            "A temporal number names a zone only where its unit is a day or longer and its epoch is local.");

    public static final Rule NT15 = Rule.error(MODULE, "NT15",
            "A number pattern is well-formed LDML and holds no percent or per-mille sign outside quotes.");

    public static final Rule NT16 = Rule.error(MODULE, "NT16", "The regular expression of a pattern text compiles.");

    public static final Rule NT17 = Rule.warning(MODULE, "NT17", "A reference resolves.");

    public static final Rule NT18 = Rule.warning(MODULE, "NT18",
            "A regular expression holds only what ECMAScript knows.");

    /** Every rule of this validator, in the order of their codes. */
    public static final List<Rule> RULES = List.of(NT1, NT2, NT3, NT4, NT5, NT6, NT7, NT8, NT9, NT10, NT11, NT12, NT13,
            NT14, NT15, NT16, NT17, NT18);

    private static final String BEHAVIORAL = "http://www.omg.org/spec/CWM/1.1/objectmodel/behavioral";
    private static final Set<TemporalForm> WITH_OFFSET = Set.of(TemporalForm.TIME_WITH_OFFSET,
            TemporalForm.BASIC_TIME_WITH_OFFSET, TemporalForm.DATE_TIME_WITH_OFFSET,
            TemporalForm.BASIC_DATE_TIME_WITH_OFFSET);
    private static final Set<TemporalForm> WITH_TIME = Set.of(TemporalForm.TIME, TemporalForm.BASIC_TIME,
            TemporalForm.TIME_WITH_OFFSET, TemporalForm.BASIC_TIME_WITH_OFFSET, TemporalForm.DATE_TIME,
            TemporalForm.BASIC_DATE_TIME, TemporalForm.DATE_TIME_WITH_OFFSET, TemporalForm.BASIC_DATE_TIME_WITH_OFFSET);
    private static final Set<TemporalForm> TIME_ONLY = Set.of(TemporalForm.TIME, TemporalForm.BASIC_TIME,
            TemporalForm.TIME_WITH_OFFSET, TemporalForm.BASIC_TIME_WITH_OFFSET);
    /** Constructs of java.util.regex that ECMAScript does not know. */
    private static final Pattern JAVA_ONLY = Pattern.compile(
            "\\\\[AZzGQhHRX]|\\(\\?>|\\(\\?[a-zA-Z-]+\\)|[*+?}]\\+|&&|\\\\p\\{(java|Is|In)");

    private NotationModelValidator() {
    }

    /** All findings for the notations in {@code root} and below. */
    public static List<Finding> check(EObject root) {
        List<Finding> findings = new ArrayList<>();
        List<EObject> all = new ArrayList<>(List.of(root));
        root.eAllContents().forEachRemaining(all::add);
        Map<EObject, List<Notation>> byElement = new LinkedHashMap<>();
        for (EObject o : all) {
            if (o instanceof Notation r) {
                notation(r, findings);
                for (ModelElement e : r.getModelElement()) {
                    byElement.computeIfAbsent(e, k -> new ArrayList<>()).add(r);
                }
            }
        }
        for (Map.Entry<EObject, List<Notation>> e : byElement.entrySet()) {
            if (e.getValue().size() > 1) {
                findings.add(NT2.at(e.getKey(), path(e.getKey()) + " has " + e.getValue().size()
                        + " notations: " + String.join(", ", e.getValue().stream()
                                .map(NotationModelValidator::path).toList()) + "; an element has one"));
            }
        }
        return findings;
    }

    /** The errors of {@link #check(EObject)} as {@code "rule message"} lines. */
    public static List<String> validate(EObject root) {
        return Findings.errorLines(check(root));
    }

    private static void notation(Notation r, List<Finding> findings) {
        if (r.getNamespace() instanceof DataType) {
            findings.add(NT1.at(r, path(r) + " is owned by a data type, which owns nothing (CWM 1.1, C-3-2)"));
        }
        Form form = r.getForm();
        switch (form) {
        case TemporalText t -> temporalText(r, t, findings);
        case TemporalNumber n -> temporalNumber(r, n, findings);
        case NumberText n -> numberText(r, n, findings);
        case BooleanText b -> booleanText(r, b, findings);
        case PatternText p -> patternText(r, p, findings);
        case null, default -> {
        }
        }
        boolean readAsLocal = r.getModelElement().isEmpty();
        for (ModelElement e : r.getModelElement()) {
            if (e.eIsProxy()) {
                findings.add(NT17.at(r, path(r) + ": an element does not resolve"));
                continue;
            }
            boolean typed = e instanceof Attribute || isParameter(e);
            if (!typed && !(e instanceof DataType)) {
                findings.add(NT1.at(r, path(r) + " names " + e.eClass().getName() + " " + path(e)
                        + "; a notation names attributes, parameters and data types"));
                continue;
            }
            Classifier type = typed ? typeOf(e) : (DataType) e;
            Info info = type == null || type.eIsProxy() ? UNKNOWN : TypeKinds.of(type);
            if (fits(r, e, form, info, findings)) {
                instant(r, e, form, info, findings);
                readAsLocal |= !info.instant() && !info.localTime();
            }
        }
        if (readAsLocal && form instanceof TemporalText t) {
            TemporalShape shape = shape(t);
            if (shape != null && shape.timeOfDay() && !shape.zoneInText() && t.getZone() == null) {
                findings.add(NT8.at(t, path(r) + ": the time of day has no zone in its text and the form names none; "
                        + "it is read as a local time"));
            }
        }
    }

    private static final Info UNKNOWN = new Info(Kind.UNKNOWN, false, false, false, null);

    // ------------------------------------------------------------------ temporal text

    /** What a temporal text holds, as far as the rules on zones are concerned; {@code null} where it is not known. */
    private record TemporalShape(boolean timeOfDay, boolean date, boolean zoneInText) {
    }

    private static TemporalShape shape(TemporalText t) {
        if (t.isSetNamed() && t.getPattern() == null) {
            TemporalForm f = t.getNamed();
            return new TemporalShape(WITH_TIME.contains(f), !TIME_ONLY.contains(f), WITH_OFFSET.contains(f));
        }
        if (!t.isSetNamed() && t.getPattern() != null) {
            try {
                DatePattern p = DatePattern.parse(t.getPattern());
                return new TemporalShape(p.hasTimeOfDay(), p.hasDate(), p.hasZoneField());
            } catch (DatePattern.PatternException e) {
                return null;
            }
        }
        return null;
    }

    private static void temporalText(Notation r, TemporalText t, List<Finding> findings) {
        if (t.isSetNamed() == (t.getPattern() != null)) {
            findings.add(NT3.at(t, path(r) + (t.isSetNamed() ? " names the form " + t.getNamed().getLiteral()
                    + " and the pattern '" + t.getPattern() + "'" : " names neither a form nor a pattern")
                    + "; a temporal text names exactly one"));
        }
        if (t.getPattern() != null) {
            try {
                DatePattern p = DatePattern.parse(t.getPattern());
                if (!p.isCsvw()) {
                    findings.add(NT5.at(t, path(r) + ": the date pattern '" + t.getPattern() + "' is not one CSV on "
                            + "the Web obliges every reader to know; not every reader can read it"));
                }
                DatePattern.Field text = p.textField();
                if (text != null && t.getLanguage() == null) {
                    findings.add(NT6.at(t, path(r) + ": the pattern '" + t.getPattern() + "' writes " + text.written()
                            + " (" + DatePattern.name(text.letter()) + ") in words but names no language"));
                }
            } catch (DatePattern.PatternException e) {
                findings.add(NT4.at(t, path(r) + ": the date pattern '" + t.getPattern() + "' is not well-formed at "
                        + "position " + e.position + ": " + e.reason));
            }
        }
        if (t.getLanguage() != null) {
            try {
                TemporalTextCodec.language(t.getLanguage());
            } catch (IllegalArgumentException e) {
                findings.add(NT6.at(t, path(r) + ": '" + t.getLanguage() + "' is no BCP 47 language tag"));
            }
        }
        if (t.getZone() != null) {
            ZoneId zone = zone(r, t, t.getZone(), findings);
            TemporalShape shape = shape(t);
            if (zone != null && shape != null && shape.timeOfDay() && !shape.date() && !shape.zoneInText()
                    && !TemporalTextCodec.fixed(zone)) {
                findings.add(NT13.at(t, path(r) + ": a time without a date cannot take the zone " + zone.getId()
                        + ", whose offset changes; name a fixed offset"));
            }
        }
    }

    private static ZoneId zone(Notation r, EObject at, String zone, List<Finding> findings) {
        try {
            return TemporalTextCodec.zone(zone);
        } catch (IllegalArgumentException e) {
            findings.add(NT13.at(at, path(r) + ": '" + zone + "' is no zone: neither an IANA zone (Europe/Berlin) nor "
                    + "a fixed offset (+01:00)"));
            return null;
        }
    }

    // ------------------------------------------------------------------ temporal number

    private static void temporalNumber(Notation r, TemporalNumber n, List<Finding> findings) {
        TemporalNumberCodec.Epoch epoch = null;
        try {
            epoch = TemporalNumberCodec.epoch(n.getEpoch());
        } catch (IllegalArgumentException e) {
            findings.add(NT13.at(n, path(r) + ": " + e.getMessage()));
        }
        BigDecimal seconds = null;
        Unit unit = n.getUnit();
        if (unit != null && unit.eIsProxy()) {
            findings.add(NT17.at(n, path(r) + ": the unit does not resolve"));
        } else if (unit != null) {
            try {
                seconds = TemporalNumberCodec.secondsPerUnit(unit);
            } catch (IllegalArgumentException e) {
                findings.add(NT9.at(n, path(r) + ": the unit " + e.getMessage()));
            }
        }
        if (n.getZone() != null) {
            zone(r, n, n.getZone(), findings);
            if (epoch != null && epoch.instant() != null) {
                findings.add(NT14.at(n, path(r) + " counts from the instant " + n.getEpoch() + " and names the zone "
                        + n.getZone() + "; a count from an instant takes no zone"));
            } else if (seconds != null && seconds.compareTo(TemporalNumberCodec.DAY) < 0) {
                findings.add(NT14.at(n, path(r) + " counts " + unit.getSymbol() + " and names the zone " + n.getZone()
                        + "; a zone stands with a unit of a day or longer"));
            }
        }
    }

    // ------------------------------------------------------------------ number text

    private static void numberText(Notation r, NumberText n, List<Finding> findings) {
        Character decimal = character(r, n, n.getDecimalChar(), "decimal character", findings);
        Character group = n.getGroupChar() == null ? null : character(r, n, n.getGroupChar(), "group character",
                findings);
        if (decimal != null && group != null && decimal.equals(group)) {
            findings.add(NT10.at(n, path(r) + ": the decimal and the group character are both '" + decimal + "'"));
            return;
        }
        if (n.getPattern() == null || decimal == null || n.getGroupChar() != null && group == null) {
            return;
        }
        try {
            NumberPattern p = NumberPattern.parse(n.getPattern(), decimal, group);
            if (p.scalePosition >= 0) {
                findings.add(NT15.at(n, path(r) + ": the number pattern '" + n.getPattern() + "' scales the value by "
                        + "the sign at position " + p.scalePosition + "; a scale is a unit (units::ScaledUnit), quote "
                        + "the sign to write it as text"));
            }
            if (!p.csvw) {
                findings.add(NT5.at(n, path(r) + ": the number pattern '" + n.getPattern() + "' holds more than the "
                        + "symbols CSV on the Web obliges every reader to know (0, #, the decimal and group character, "
                        + "E, +); not every reader can read it"));
            }
        } catch (NumberPattern.PatternException e) {
            findings.add(NT15.at(n, path(r) + ": the number pattern '" + n.getPattern() + "' is not well-formed at "
                    + "position " + e.position + ": " + e.reason));
        }
    }

    private static Character character(Notation r, NumberText n, String s, String what,
            List<Finding> findings) {
        try {
            return NumberTextCodec.oneCharacter(s, what);
        } catch (IllegalArgumentException e) {
            findings.add(NT10.at(n, path(r) + ": " + e.getMessage()));
            return null;
        }
    }

    // ------------------------------------------------------------------ boolean text

    private static void booleanText(Notation r, BooleanText b, List<Finding> findings) {
        for (String t : b.getTrueWords()) {
            for (String f : b.getFalseWords()) {
                if (same(t, f, b.isIgnoreCase())) {
                    findings.add(NT11.at(b, path(r) + ": '" + t + "' is a true and a false word"));
                }
            }
        }
        for (String m : r.getMissingWords()) {
            for (String w : b.getTrueWords()) {
                if (same(m, w, b.isIgnoreCase())) {
                    findings.add(NT11.at(r, path(r) + ": the missing word '" + m + "' is a true word"));
                }
            }
            for (String w : b.getFalseWords()) {
                if (same(m, w, b.isIgnoreCase())) {
                    findings.add(NT11.at(r, path(r) + ": the missing word '" + m + "' is a false word"));
                }
            }
        }
    }

    private static boolean same(String a, String b, boolean ignoreCase) {
        return ignoreCase ? a.equalsIgnoreCase(b) : a.equals(b);
    }

    // ------------------------------------------------------------------ pattern text

    private static void patternText(Notation r, PatternText p, List<Finding> findings) {
        if (p.getPattern() == null) {
            return;
        }
        try {
            PatternTextCodec.compile(p.getPattern());
        } catch (IllegalArgumentException e) {
            findings.add(NT16.at(p, path(r) + ": " + e.getMessage()));
            return;
        }
        Matcher m = JAVA_ONLY.matcher(p.getPattern());
        if (m.find()) {
            findings.add(NT18.at(p, path(r) + ": the regular expression '" + p.getPattern() + "' holds '" + m.group()
                    + "' at position " + m.start() + ", which ECMAScript does not know"));
        }
    }

    // ------------------------------------------------------------------ fit to the type, zones by the type

    /** NT12: whether the form fits what the type holds; a type that is not known fits. */
    private static boolean fits(Notation r, ModelElement e, Form form, Info type, List<Finding> findings) {
        if (form == null || type.kind() == Kind.UNKNOWN) {
            return true;
        }
        Set<Kind> stored = switch (form) {
        case TemporalText t -> digitsOnly(t) ? Set.of(Kind.TEXT, Kind.NUMBER) : Set.of(Kind.TEXT);
        case TemporalNumber n -> Set.of(Kind.NUMBER);
        case BooleanText b -> wholeNumbers(b) ? Set.of(Kind.TEXT, Kind.NUMBER) : Set.of(Kind.TEXT);
        default -> Set.of(Kind.TEXT);
        };
        Kind value = switch (form) {
        case TemporalText t -> Kind.TEMPORAL;
        case TemporalNumber n -> Kind.TEMPORAL;
        case NumberText n -> Kind.NUMBER;
        case BooleanText b -> Kind.BOOLEAN;
        default -> Kind.TEXT;
        };
        boolean fits = stored.contains(type.kind()) || !type.storage() && type.kind() == value;
        if (!fits) {
            String formName = form.eClass().getName();
            String storedAs = String.join(" or ", stored.stream().sorted().map(NotationModelValidator::what)
                    .toList());
            findings.add(NT12.at(r, path(r) + ": " + path(e) + " is of type " + type.name() + " ("
                    + type.kind().name().toLowerCase(java.util.Locale.ROOT) + "); a " + words(formName)
                    + (type.storage() ? " is stored as " + storedAs : " writes " + what(value) + " as " + storedAs)));
            return false;
        }
        return true;
    }

    /** NT7 where the type holds an instant and the form says no zone. */
    private static void instant(Notation r, ModelElement e, Form form, Info type, List<Finding> findings) {
        if (form instanceof TemporalText t) {
            TemporalShape shape = shape(t);
            if (shape != null && shape.timeOfDay() && !shape.zoneInText() && t.getZone() == null && type.instant()) {
                findings.add(NT7.at(t, path(r) + ": " + path(e) + " of type " + type.name() + " holds an instant, but "
                        + "the text holds no zone and the form names none"));
            }
        } else if (form instanceof TemporalNumber n && type.instant() && n.getZone() == null) {
            try {
                if (TemporalNumberCodec.epoch(n.getEpoch()).local() != null) {
                    findings.add(NT7.at(n, path(r) + ": " + path(e) + " of type " + type.name() + " holds an instant, "
                            + "but the count is from the local epoch " + n.getEpoch() + " and the form names no zone"));
                }
            } catch (IllegalArgumentException ignored) {
                // NT13 says it
            }
        }
    }

    /** Whether a temporal text is digits only, starting with the year, so a number column can hold it. */
    private static boolean digitsOnly(TemporalText t) {
        if (t.isSetNamed() && t.getPattern() == null) {
            return t.getNamed() == TemporalForm.BASIC_DATE;
        }
        try {
            return t.getPattern() != null && DatePattern.parse(t.getPattern()).digitsOnlyYearFirst();
        } catch (DatePattern.PatternException e) {
            return false;
        }
    }

    /** Whether every word of a boolean text is a whole number, so a number column can hold it (1 and 0). */
    private static boolean wholeNumbers(BooleanText b) {
        return !b.getTrueWords().isEmpty() && !b.getFalseWords().isEmpty()
                && b.getTrueWords().stream().allMatch(w -> w.matches("-?[0-9]+"))
                && b.getFalseWords().stream().allMatch(w -> w.matches("-?[0-9]+"));
    }

    /** A kind of value in words: text, a number, a time, a truth value. */
    private static String what(Kind kind) {
        return switch (kind) {
        case TEXT -> "text";
        case NUMBER -> "a number";
        case TEMPORAL -> "a time";
        case BOOLEAN -> "a truth value";
        default -> kind.name().toLowerCase(java.util.Locale.ROOT);
        };
    }

    private static String words(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isParameter(EObject o) {
        List<EClass> types = new ArrayList<>(List.of(o.eClass()));
        types.addAll(o.eClass().getEAllSuperTypes());
        for (EClass c : types) {
            if ("Parameter".equals(c.getName()) && c.getEPackage() != null
                    && BEHAVIORAL.equals(c.getEPackage().getNsURI())) {
                return true;
            }
        }
        return false;
    }

    private static Classifier typeOf(EObject e) {
        EStructuralFeature type = e.eClass().getEStructuralFeature("type");
        return type != null && !type.isMany() && e.eGet(type) instanceof Classifier c ? c : null;
    }

    /** The names from the root down to {@code element}, joined by dots. */
    static String path(EObject element) {
        if (element == null) {
            return "";
        }
        Deque<String> names = new ArrayDeque<>();
        for (EObject o = element; o != null; o = o.eContainer()) {
            if (o instanceof ModelElement me && me.getName() != null) {
                names.addFirst(me.getName());
            }
        }
        return String.join(".", names);
    }
}
