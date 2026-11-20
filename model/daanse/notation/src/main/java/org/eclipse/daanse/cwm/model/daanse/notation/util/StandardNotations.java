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
import java.util.function.Function;

import org.eclipse.daanse.cwm.emf.standard.StandardSets;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.DataTypes;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Namespaces;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.daanse.notation.Form;
import org.eclipse.daanse.cwm.model.daanse.notation.Notation;
import org.eclipse.daanse.cwm.model.daanse.notation.NotationPackage;
import org.eclipse.daanse.cwm.model.daanse.notation.TemporalForm;
import org.eclipse.daanse.cwm.model.daanse.units.SystemOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.util.SI;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.xmi.XMIResource;

/**
 * The usual notations as a standard set other model files import: for each a {@code core::DataType} and the
 * {@link Notation} that names it, so that an attribute of a model file typed by the data type is written in its form
 * ({@link Notations#of}: an element's own notation, else that of its data type). Each data type says the Java class of
 * its values in the tagged value {@value #INSTANCE_CLASS_NAME}.
 *
 * <p>The notations are the ISO 8601 date, time, date and time without and with an offset, the German date
 * {@code dd.MM.yyyy}, seconds and milliseconds since the Unix epoch, days since the spreadsheet epoch, a number as
 * written in English ({@code 1,234.5}) and in German ({@code 1.234,5}), and the truth words {@code true}/{@code false},
 * {@code 1}/{@code 0}, {@code yes}/{@code no} and {@code ja}/{@code nein}. A count since an epoch refers to its unit in
 * the standard set of the SI ({@link SI#SET_URI}).</p>
 *
 * <p>The set is the file {@value #SET_FILE} beside this class under the URI {@value #SET_URI}:
 * {@link #standardSet()} builds it, {@link #load(ResourceSet)} reads it and the SI from the jars, and a model file
 * types an attribute as {@code href="<SET_URI>#<id>"}, the id the UUID stated beside the data type here.</p>
 */
public final class StandardNotations {

    /** The name of the package of the set. */
    public static final String NAME = "Notations";

    /** The tag of the tagged value that names the Java class of a data type's values. */
    public static final String INSTANCE_CLASS_NAME = "daanse.ecore.instanceClassName";

    /** The URI of the standard set, under which a model file refers to its data types and notations. */
    public static final String SET_URI =
            "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.notation/notations.xmi";

    /** The file of the standard set, beside this class in the jar. */
    public static final String SET_FILE = "notations.xmi";

    /** A date, ISO 8601: {@code 2026-11-19}. */
    public static final String ISO_DATE = "IsoDate";
    /** A time of day, ISO 8601: {@code 10:15:30}. */
    public static final String ISO_TIME = "IsoTime";
    /** A date and time without an offset, ISO 8601: {@code 2026-11-19T10:15:30}. */
    public static final String ISO_DATE_TIME = "IsoDateTime";
    /** A date and time with an offset, ISO 8601: {@code 2026-11-19T10:15:30+01:00}. */
    public static final String ISO_DATE_TIME_WITH_OFFSET = "IsoDateTimeWithOffset";
    /** A date as written in German: {@code 19.11.2026}. */
    public static final String GERMAN_DATE = "GermanDate";
    /** Seconds since 1970-01-01T00:00:00Z. */
    public static final String UNIX_SECONDS = "UnixSeconds";
    /** Milliseconds since 1970-01-01T00:00:00Z. */
    public static final String UNIX_MILLISECONDS = "UnixMilliseconds";
    /** Days since 1899-12-30, the serial date of a spreadsheet. */
    public static final String SPREADSHEET_DAYS = "SpreadsheetDays";
    /** A number as written in English: {@code 1,234.5}. */
    public static final String ENGLISH_NUMBER = "EnglishNumber";
    /** A number as written in German: {@code 1.234,5}. */
    public static final String GERMAN_NUMBER = "GermanNumber";
    /** {@code true} or {@code false}, in any case. */
    public static final String TRUE_FALSE = "TrueFalse";
    /** {@code 1} or {@code 0}. */
    public static final String ONE_ZERO = "OneZero";
    /** {@code yes} or {@code no}, in any case. */
    public static final String YES_NO = "YesNo";
    /** {@code ja} or {@code nein}, in any case. */
    public static final String JA_NEIN = "JaNein";

    /**
     * One notation of the set: the name of its data type, the Java class of the values, its form; the ids of the data
     * type and of its tagged value, then of the notation and of its form.
     */
    private record Entry(String name, String javaClass, Function<Notation, Form> form, String typeId, String tagId,
            String notationId, String formId) {
    }

    private StandardNotations() {
    }

    /** The standard set, built in memory with its ids beside the SI loaded from the jar of {@code units}. */
    public static XMIResource standardSet() {
        XMIResource set = StandardSets.create(SET_URI);
        SystemOfUnits si = SI.load(set.getResourceSet());
        Package notations = CoreFactory.eINSTANCE.createPackage();
        notations.setName(NAME);
        set.getContents().add(notations);
        StandardSets.id(notations, "_LdwI0OUIEfGl4IEUgfF4xA");
        for (Entry e : entries(si)) {
            DataType type = StandardSets.id(DataTypes.create(notations, e.name()), e.typeId());
            StandardSets.id(TaggedValues.set(type, INSTANCE_CLASS_NAME, e.javaClass()), e.tagId());
            Notation notation = StandardSets.id(Notations.create(notations, e.name() + "Notation", type),
                    e.notationId());
            StandardSets.id(e.form().apply(notation), e.formId());
        }
        return set;
    }

    /**
     * Maps the standard set in {@code resourceSet} to its file in the jar, the SI it refers to as well: a model file
     * there that refers to its data types and notations reads them from the jar ({@link StandardSets#map}).
     */
    public static void map(ResourceSet resourceSet) {
        SI.map(resourceSet);
        StandardSets.map(resourceSet, SET_URI, StandardNotations.class.getResource(SET_FILE), CorePackage.eINSTANCE,
                NotationPackage.eINSTANCE);
    }

    /**
     * The package of the standard set in {@code resourceSet}, mapped there and read from the jar where it is not read
     * yet.
     */
    public static Package load(ResourceSet resourceSet) {
        map(resourceSet);
        return StandardSets.load(resourceSet, SET_URI, Package.class);
    }

    /** The data type of {@code standard} named {@code name}, one of the constants of this class. */
    public static Optional<DataType> type(Package standard, String name) {
        Objects.requireNonNull(standard, "standard");
        return Namespaces.findOwnedByName(standard, DataType.class, Objects.requireNonNull(name, "name"));
    }

    /** The entries of the set; the units of a count are those of {@code si}. */
    private static List<Entry> entries(SystemOfUnits si) {
        return List.of(
                new Entry(ISO_DATE, "java.time.LocalDate", n -> Notations.temporalText(n, TemporalForm.DATE),
                        "_LdwI0eUIEfGl4IEUgfF4xA", "_LdwI0uUIEfGl4IEUgfF4xA",
                        "_LdwI0-UIEfGl4IEUgfF4xA", "_LdwI1OUIEfGl4IEUgfF4xA"),
                new Entry(ISO_TIME, "java.time.LocalTime", n -> Notations.temporalText(n, TemporalForm.TIME),
                        "_LdwI1eUIEfGl4IEUgfF4xA", "_LdwI1uUIEfGl4IEUgfF4xA",
                        "_LdwI1-UIEfGl4IEUgfF4xA", "_LdwI2OUIEfGl4IEUgfF4xA"),
                new Entry(ISO_DATE_TIME, "java.time.LocalDateTime",
                        n -> Notations.temporalText(n, TemporalForm.DATE_TIME),
                        "_LdwI2eUIEfGl4IEUgfF4xA", "_LdwI2uUIEfGl4IEUgfF4xA",
                        "_LdwI2-UIEfGl4IEUgfF4xA", "_LdwI3OUIEfGl4IEUgfF4xA"),
                new Entry(ISO_DATE_TIME_WITH_OFFSET, "java.time.OffsetDateTime",
                        n -> Notations.temporalText(n, TemporalForm.DATE_TIME_WITH_OFFSET),
                        "_LdwI3eUIEfGl4IEUgfF4xA", "_LdwI3uUIEfGl4IEUgfF4xA",
                        "_LdwI3-UIEfGl4IEUgfF4xA", "_LdwI4OUIEfGl4IEUgfF4xA"),
                new Entry(GERMAN_DATE, "java.time.LocalDate", n -> Notations.temporalText(n, "dd.MM.yyyy"),
                        "_LdwI4eUIEfGl4IEUgfF4xA", "_LdwI4uUIEfGl4IEUgfF4xA",
                        "_LdwI4-UIEfGl4IEUgfF4xA", "_LdwI5OUIEfGl4IEUgfF4xA"),
                new Entry(UNIX_SECONDS, "java.time.Instant",
                        n -> Notations.temporalNumber(n, Notations.UNIX_EPOCH, SI.unit(si, SI.SECOND)),
                        "_LdwI5eUIEfGl4IEUgfF4xA", "_LdwI5uUIEfGl4IEUgfF4xA",
                        "_LdwI5-UIEfGl4IEUgfF4xA", "_LdwI6OUIEfGl4IEUgfF4xA"),
                new Entry(UNIX_MILLISECONDS, "java.time.Instant",
                        n -> Notations.temporalNumber(n, Notations.UNIX_EPOCH, SI.unit(si, SI.MILLISECOND)),
                        "_LdwI6eUIEfGl4IEUgfF4xA", "_LdwI6uUIEfGl4IEUgfF4xA",
                        "_LdwI6-UIEfGl4IEUgfF4xA", "_LdwI7OUIEfGl4IEUgfF4xA"),
                new Entry(SPREADSHEET_DAYS, "java.time.LocalDateTime",
                        n -> Notations.temporalNumber(n, Notations.SPREADSHEET_EPOCH, SI.unit(si, SI.DAY)),
                        "_LdwI7eUIEfGl4IEUgfF4xA", "_LdwI7uUIEfGl4IEUgfF4xA",
                        "_LdwI7-UIEfGl4IEUgfF4xA", "_LdwI8OUIEfGl4IEUgfF4xA"),
                new Entry(ENGLISH_NUMBER, "java.math.BigDecimal", n -> Notations.numberText(n, ".", ","),
                        "_LdwI8eUIEfGl4IEUgfF4xA", "_LdwI8uUIEfGl4IEUgfF4xA",
                        "_LdwI8-UIEfGl4IEUgfF4xA", "_LdwI9OUIEfGl4IEUgfF4xA"),
                new Entry(GERMAN_NUMBER, "java.math.BigDecimal", n -> Notations.numberText(n, ",", "."),
                        "_LdwI9eUIEfGl4IEUgfF4xA", "_LdwI9uUIEfGl4IEUgfF4xA",
                        "_LdwI9-UIEfGl4IEUgfF4xA", "_LdwI-OUIEfGl4IEUgfF4xA"),
                new Entry(TRUE_FALSE, "java.lang.Boolean",
                        n -> Notations.ignoreCase(Notations.booleanText(n, "true", "false")),
                        "_LdwI-eUIEfGl4IEUgfF4xA", "_LdwI-uUIEfGl4IEUgfF4xA",
                        "_LdwI--UIEfGl4IEUgfF4xA", "_LdwI_OUIEfGl4IEUgfF4xA"),
                new Entry(ONE_ZERO, "java.lang.Boolean", n -> Notations.booleanText(n, "1", "0"),
                        "_LdwI_eUIEfGl4IEUgfF4xA", "_LdwI_uUIEfGl4IEUgfF4xA",
                        "_LdwI_-UIEfGl4IEUgfF4xA", "_LdwJAOUIEfGl4IEUgfF4xA"),
                new Entry(YES_NO, "java.lang.Boolean", n -> Notations.ignoreCase(Notations.booleanText(n, "yes", "no")),
                        "_LdwJAeUIEfGl4IEUgfF4xA", "_LdwJAuUIEfGl4IEUgfF4xA",
                        "_LdwJA-UIEfGl4IEUgfF4xA", "_LdwJBOUIEfGl4IEUgfF4xA"),
                new Entry(JA_NEIN, "java.lang.Boolean",
                        n -> Notations.ignoreCase(Notations.booleanText(n, "ja", "nein")),
                        "_LdwJBeUIEfGl4IEUgfF4xA", "_LdwJBuUIEfGl4IEUgfF4xA",
                        "_LdwJB-UIEfGl4IEUgfF4xA", "_LdwJCOUIEfGl4IEUgfF4xA"));
    }
}
