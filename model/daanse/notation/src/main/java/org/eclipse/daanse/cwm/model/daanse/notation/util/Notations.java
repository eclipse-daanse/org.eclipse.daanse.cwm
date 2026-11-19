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
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.daanse.notation.BooleanText;
import org.eclipse.daanse.cwm.model.daanse.notation.NumberText;
import org.eclipse.daanse.cwm.model.daanse.notation.PatternText;
import org.eclipse.daanse.cwm.model.daanse.notation.Notation;
import org.eclipse.daanse.cwm.model.daanse.notation.NotationFactory;
import org.eclipse.daanse.cwm.model.daanse.notation.NotationPackage;
import org.eclipse.daanse.cwm.model.daanse.notation.TemporalForm;
import org.eclipse.daanse.cwm.model.daanse.notation.TemporalNumber;
import org.eclipse.daanse.cwm.model.daanse.notation.TemporalText;
import org.eclipse.daanse.cwm.model.daanse.notation.Trim;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Creates notations and their forms, and finds the notation of an element.
 *
 * <p>A notation is owned by the namespace it is created in (a package of notations beside the model)
 * and names its elements; it is not owned by them, because a data type may own nothing (CWM 1.1, C-3-2). Each form is
 * made by one helper that hangs it on its notation: {@link #temporalText(Notation, TemporalForm)} and
 * {@link #temporalText(Notation, String)}, {@link #temporalNumber}, {@link #numberText},
 * {@link #booleanText}, {@link #patternText}. The notation of an element is its own, else that of the data
 * type that types it ({@link #of}); they are found backwards through {@link InverseReferences}.</p>
 */
public final class Notations {

    /** The instant Unix time counts from. */
    public static final String UNIX_EPOCH = "1970-01-01T00:00:00Z";
    /** The local day spreadsheet serial dates count from: day 1 is 1899-12-31, day 61 is 1900-03-01. */
    public static final String SPREADSHEET_EPOCH = "1899-12-30";

    private static final NotationFactory F = NotationFactory.eINSTANCE;

    private Notations() {
    }

    /** A notation owned by {@code owner} of the values of {@code elements}, without a form yet. */
    public static Notation create(Namespace owner, String name, ModelElement... elements) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(elements, "elements");
        if (elements.length == 0) {
            throw new IllegalArgumentException("a notation names at least one element");
        }
        Notation r = F.createNotation();
        r.setName(Objects.requireNonNull(name, "name"));
        for (ModelElement e : elements) {
            r.getModelElement().add(Objects.requireNonNull(e, "element"));
        }
        owner.getOwnedElement().add(r);
        return r;
    }

    /** The texts that mean no value; the first is the one a writer writes. */
    public static Notation missing(Notation notation, String... words) {
        for (String w : words) {
            notation.getMissingWords().add(Objects.requireNonNull(w, "word"));
        }
        return notation;
    }

    /** Which white space a reader removes before it reads. */
    public static Notation trim(Notation notation, Trim trim) {
        notation.setTrim(Objects.requireNonNull(trim, "trim"));
        return notation;
    }

    /** A date, a time or both in a named ISO 8601 form, the form of {@code notation}. */
    public static TemporalText temporalText(Notation notation, TemporalForm named) {
        TemporalText t = F.createTemporalText();
        t.setNamed(Objects.requireNonNull(named, "named"));
        notation.setForm(t);
        return t;
    }

    /** A date, a time or both by an LDML date pattern ({@code dd.MM.yyyy}), the form of {@code notation}. */
    public static TemporalText temporalText(Notation notation, String pattern) {
        TemporalText t = F.createTemporalText();
        t.setPattern(Objects.requireNonNull(pattern, "pattern"));
        notation.setForm(t);
        return t;
    }

    /** The zone of a text that holds none: an IANA zone or a fixed offset. */
    public static TemporalText zone(TemporalText form, String zone) {
        form.setZone(Objects.requireNonNull(zone, "zone"));
        return form;
    }

    /** The language of names of months and days, a BCP 47 tag. */
    public static TemporalText language(TemporalText form, String language) {
        form.setLanguage(Objects.requireNonNull(language, "language"));
        return form;
    }

    /** Reads texts that are close to the form. */
    public static TemporalText lenient(TemporalText form) {
        form.setLenient(true);
        return form;
    }

    /**
     * A count of {@code unit}, a unit of time, since {@code epoch}, the form of {@code notation}: Unix seconds
     * are {@code temporalNumber(r, UNIX_EPOCH, second)}, spreadsheet serial dates
     * {@code temporalNumber(r, SPREADSHEET_EPOCH, day)}.
     */
    public static TemporalNumber temporalNumber(Notation notation, String epoch, Unit unit) {
        TemporalNumber n = F.createTemporalNumber();
        n.setEpoch(Objects.requireNonNull(epoch, "epoch"));
        n.setUnit(Objects.requireNonNull(unit, "unit"));
        notation.setForm(n);
        return n;
    }

    /** The zone of a local epoch counted in days or longer units. */
    public static TemporalNumber zone(TemporalNumber form, String zone) {
        form.setZone(Objects.requireNonNull(zone, "zone"));
        return form;
    }

    /** A number with {@code decimalChar} and no grouping, the form of {@code notation}. */
    public static NumberText numberText(Notation notation, String decimalChar) {
        NumberText n = F.createNumberText();
        n.setDecimalChar(Objects.requireNonNull(decimalChar, "decimalChar"));
        notation.setForm(n);
        return n;
    }

    /** A number with {@code decimalChar} and groups of digits divided by {@code groupChar}. */
    public static NumberText numberText(Notation notation, String decimalChar, String groupChar) {
        NumberText n = numberText(notation, decimalChar);
        n.setGroupChar(Objects.requireNonNull(groupChar, "groupChar"));
        return n;
    }

    /** An LDML number pattern written with the decimal and group character of {@code form}. */
    public static NumberText pattern(NumberText form, String pattern) {
        form.setPattern(Objects.requireNonNull(pattern, "pattern"));
        return form;
    }

    /** Strips white space, currency, percent and per-mille signs and letters around the number on reading. */
    public static NumberText withSigns(NumberText form) {
        form.setBare(false);
        return form;
    }

    /** Reads a group character between any two digits of the whole part, whatever groups the pattern states. */
    public static NumberText lenient(NumberText form) {
        form.setLenient(true);
        return form;
    }

    /** A truth value written as {@code trueWord} or {@code falseWord}, the form of {@code notation}. */
    public static BooleanText booleanText(Notation notation, String trueWord, String falseWord) {
        BooleanText b = F.createBooleanText();
        b.getTrueWords().add(Objects.requireNonNull(trueWord, "trueWord"));
        b.getFalseWords().add(Objects.requireNonNull(falseWord, "falseWord"));
        notation.setForm(b);
        return b;
    }

    /** More words a reader takes for true and for false; the writer keeps writing the first of each. */
    public static BooleanText also(BooleanText form, List<String> trueWords, List<String> falseWords) {
        trueWords.forEach(w -> form.getTrueWords().add(Objects.requireNonNull(w, "word")));
        falseWords.forEach(w -> form.getFalseWords().add(Objects.requireNonNull(w, "word")));
        return form;
    }

    /** Reads the words in any case. */
    public static BooleanText ignoreCase(BooleanText form) {
        form.setIgnoreCase(true);
        return form;
    }

    /** Text the whole of which matches {@code regex} (ECMAScript syntax), the form of {@code notation}. */
    public static PatternText patternText(Notation notation, String regex) {
        PatternText p = F.createPatternText();
        p.setPattern(Objects.requireNonNull(regex, "regex"));
        notation.setForm(p);
        return p;
    }

    /** The notations that name {@code element}; more than one is an error of the model. */
    public static List<Notation> own(ModelElement element) {
        return InverseReferences.referencingList(element,
                NotationPackage.Literals.NOTATION__MODEL_ELEMENT, Notation.class);
    }

    /**
     * The notation of {@code element}: its own, else the one of the data type that types it (an attribute, a
     * parameter).
     */
    public static Optional<Notation> of(ModelElement element) {
        Objects.requireNonNull(element, "element");
        List<Notation> own = own(element);
        if (!own.isEmpty()) {
            return Optional.of(own.get(0));
        }
        return typeOf(element).flatMap(type -> own(type).stream().findFirst());
    }

    /** The data type that types {@code element}, if it has one: of an attribute or a parameter. */
    public static Optional<DataType> typeOf(ModelElement element) {
        EStructuralFeature type = element.eClass().getEStructuralFeature("type");
        if (type == null || type.isMany()) {
            return Optional.empty();
        }
        return element.eGet(type) instanceof DataType d ? Optional.of(d) : Optional.empty();
    }
}
