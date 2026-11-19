/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.notation.build;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

/**
 * Programmatic builder for the {@code notation} metamodel: how a value of an attribute is written where it is
 * stored or exchanged, the lexical form beside the type.
 *
 * <p>The shape follows W3C "CSV on the Web" (the datatype format of a column and the parsing of cells: date and time
 * patterns of Unicode LDML, number formats with decimal and group characters, the true and false words of a boolean, a
 * regular expression for any other type, the words that mean no value, trimming), with three additions it lacks: a
 * zone for values written without one, time as a number of units since an epoch, and month and day names in a
 * language.</p>
 *
 * <p>Every reference into CWM and units is one-way with an EMOF {@code Property.oppositeRoleName} (prefix
 * {@code notation}); construction and serialization follow {@code model/daanse/units/.../UnitsEcoreBuilder}.</p>
 */
public final class NotationEcoreBuilder {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    /** The namespace URI of the package. */
    public static final String NS_URI = "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.notation";

    private static final String EPL_HEADER = String.join("\n",
            "Copyright (c) 2026 Contributors to the Eclipse Foundation.",
            "",
            "This program and the accompanying materials are made",
            "available under the terms of the Eclipse Public License 2.0",
            "which is available at https://www.eclipse.org/legal/epl-2.0/",
            "",
            "SPDX-License-Identifier: EPL-2.0");

    private final EcoreFactory ef = EcoreFactory.eINSTANCE;
    private final Refs refs;

    /** The classifiers of {@code objectmodel/core} and {@code units} the metamodel refers to. */
    public static final class Refs {
        final EClass element;
        final EClass modelElement;
        final EDataType stringType;
        final EDataType booleanType;
        final EClass unit;

        Refs(EPackage core, EPackage units) {
            this.element = eClass(core, "Element");
            this.modelElement = eClass(core, "ModelElement");
            this.stringType = eDataType(core, "String");
            this.booleanType = eDataType(core, "Boolean");
            this.unit = eClass(units, "Unit");
        }
    }

    public NotationEcoreBuilder(Refs refs) {
        this.refs = refs;
    }

    /**
     * Builds the metamodel from the CWM and units Ecores next to {@code moduleDir} (module directory
     * {@code model/daanse/notation}) and writes it to {@code outFile}.
     */
    public static void generate(File moduleDir, File outFile) throws IOException {
        File cwmDir = new File(moduleDir, "../../cwm").getCanonicalFile();
        File unitsDir = new File(moduleDir, "../units").getCanonicalFile();
        ResourceSet rs = newResourceSet();
        EPackage core = loadPackage(rs, ecoreUri(cwmDir, "objectmodel.core/model/core.ecore"));
        EPackage units = loadPackage(rs, ecoreUri(unitsDir, "model/units.ecore"));
        outFile.getParentFile().mkdirs();
        save(new NotationEcoreBuilder(new Refs(core, units)).build(), rs,
                URI.createFileURI(outFile.getAbsolutePath()));
    }

    /** Creates the EPackage {@code notation}: 7 classes (1 abstract), 2 enumerations. */
    public EPackage build() {
        EPackage p = ef.createEPackage();
        p.setName("notation");
        p.setNsURI(NS_URI);
        p.setNsPrefix("notation");
        EAnnotation pa = annotate(p,
                "How a value of an attribute is written where it is stored or exchanged: the lexical form beside the "
                        + "type. A Notation names the elements it holds for (attributes such as columns, record "
                        + "fields, measures and resources, parameters, or data types whose attributes all are written "
                        + "so; an element's own notation comes before that of its type) and holds at most one "
                        + "form, the words that mean no value and how a text is trimmed. The class of the form says "
                        + "the kind of value: TemporalText (an ISO 8601 form or a Unicode LDML date pattern, with a "
                        + "zone for texts without one and a language for names of months and days), TemporalNumber "
                        + "(a count of a unit of time since an epoch), NumberText (decimal and group characters, an "
                        + "LDML number pattern), BooleanText (the true and false words) and PatternText (a regular "
                        + "expression for any other type). The shape follows W3C CSV on the Web, the parsing of "
                        + "cells. It is not the display format of a value (an OLAP format string), and it holds no "
                        + "scale: a percent or a thousand is a unit (units::ScaledUnit). Non-containment references "
                        + "are hidden association ends (Property.oppositeRoleName, prefix notation).");
        pa.getDetails().put("modelName", "Notation");
        pa.getDetails().put("copyrightText", EPL_HEADER);
        pa.getDetails().put("oSGiCompatible", "true");
        pa.getDetails().put("basePackage", "org.eclipse.daanse.cwm.model.daanse");
        pa.getDetails().put("resource", "XMI");
        pa.getDetails().put("prefix", "Notation");

        EAnnotation delegates = ef.createEAnnotation();
        delegates.setSource(ECORE);
        delegates.getDetails().put("invocationDelegates", ECORE_OCL);
        delegates.getDetails().put("settingDelegates", ECORE_OCL);
        delegates.getDetails().put("validationDelegates", ECORE_OCL);
        p.getEAnnotations().add(delegates);
        EAnnotation ho = ef.createEAnnotation();
        ho.setSource(ECORE_OCL);
        ho.getDetails().put("hiddenOpposites", "true");
        p.getEAnnotations().add(ho);

        EClass notation = cls(p, "Notation", false,
                "States how the values of the elements it names are written where they are stored or exchanged: "
                        + "an attribute (a column, a record field, a measure, a resource), a parameter, or a data "
                        + "type whose attributes all are written so. An element's own notation comes before "
                        + "that of its type. A reader trims a text first, then reads a missing word as no value, "
                        + "then reads the rest by the form; a writer writes no value as the first missing word.");
        notation.getESuperTypes().add(refs.modelElement);
        EClass form = cls(p, "Form", true,
                "The lexical form of a value; the subclass says the kind of value it writes: a time as text or as "
                        + "a number, a number, a truth value, or text of any other type.");
        form.getESuperTypes().add(refs.element);
        EClass temporalText = cls(p, "TemporalText", false,
                "A date, a time of day or both, written as text: by a named ISO 8601 form or by a date pattern of "
                        + "Unicode LDML (yyyy-MM-dd, dd.MM.yyyy HH:mm, MMM yyyy). A text without an offset or zone "
                        + "takes the zone of the form, where it names one; names of months and days are read in "
                        + "its language.");
        temporalText.getESuperTypes().add(form);
        EClass temporalNumber = cls(p, "TemporalNumber", false,
                "A date, a time or an instant written as a number: the count of a unit of time since an epoch. "
                        + "Seconds or milliseconds since 1970-01-01T00:00:00Z are an instant; days since a local "
                        + "date (1899-12-30 for spreadsheet serial dates) are a local date and time, which a zone "
                        + "makes an instant.");
        temporalNumber.getESuperTypes().add(form);
        EClass numberText = cls(p, "NumberText", false,
                "A number written as text, with a decimal character, a group character and an optional number "
                        + "pattern of Unicode LDML (#,##0.00). Without a pattern a text is an optional sign, digits "
                        + "in the usual groups (a first group of one to three digits, then groups of three), an "
                        + "optional fraction and an optional exponent, or one of NaN, INF and -INF.");
        numberText.getESuperTypes().add(form);
        EClass booleanText = cls(p, "BooleanText", false,
                "A truth value written as one of the true words or one of the false words; the first of each is "
                        + "the word a writer writes.");
        booleanText.getESuperTypes().add(form);
        EClass patternText = cls(p, "PatternText", false,
                "A value of any other type written as text the whole of which matches a regular expression "
                        + "(a postcode, a code of letters and digits).");
        patternText.getESuperTypes().add(form);

        EEnum trim = enm(p, "Trim",
                "Which white space a reader removes from a text before it reads it: at both ends, at the start, at "
                        + "the end, or none (as the trim of CSV on the Web: true, start, end, false).",
                "both", "start", "end", "none");
        EEnum temporalForm = enm(p, "TemporalForm",
                "A form of ISO 8601: a calendar date, a time of day or both, without or with an offset from UTC, "
                        + "in the extended form (with - and :) or the basic form (digits only). Seconds are "
                        + "written, a fraction of a second where it is not zero; an offset is Z or +hh:mm "
                        + "(extended), Z or +hhmm (basic).",
                "date", "basicDate", "time", "basicTime", "timeWithOffset", "basicTimeWithOffset", "dateTime",
                "basicDateTime", "dateTimeWithOffset", "basicDateTimeWithOffset");

        EReference elements = ref(notation, "modelElement", refs.modelElement, 1, -1,
                "The elements whose values are written so: attributes, parameters, data types.");
        oppositeRole(elements, "notationNotation");
        cont(notation, "form", form, 0, 1, "The form of the values; without one a value is the text itself.");
        attr(notation, "missingWords", refs.stringType, 0, -1,
                "The texts that mean no value, compared after trimming and exactly; the first is the one a writer "
                        + "writes for no value. The empty text is a missing word only where it is named.");
        attr(notation, "trim", trim, 1, 1, "Which white space a reader removes before it reads a text.");
        constrain(notation, "NoMissingWordIsTrueOrFalse",
                "self.form.oclIsUndefined() or not self.form.oclIsKindOf(BooleanText) or "
                        + "(self.missingWords->excludesAll(self.form.oclAsType(BooleanText).trueWords) and "
                        + "self.missingWords->excludesAll(self.form.oclAsType(BooleanText).falseWords))");

        EAttribute named = attr(temporalText, "named", temporalForm, 0, 1,
                "The ISO 8601 form of the text; exactly one of named and pattern is given.");
        named.setUnsettable(true);
        attr(temporalText, "pattern", refs.stringType, 0, 1,
                "A date pattern of Unicode LDML (UTS #35): yyyy year, MM month, MMM and MMMM its names, dd day, "
                        + "EEE the name of the day, HH hour, mm minute, ss second, S fraction, X and x offset, VV "
                        + "zone; text in single quotes. A lone T between a date and a time is a literal, as CSV on "
                        + "the Web writes it.");
        attr(temporalText, "zone", refs.stringType, 0, 1,
                "The zone of a date and time whose text holds none: an IANA zone (Europe/Berlin) or a fixed offset "
                        + "(+01:00, Z). Where the text holds an offset or a zone, the text's wins.");
        attr(temporalText, "language", refs.stringType, 0, 1,
                "The language of names of months, days and day periods, a BCP 47 tag (de, en-GB).");
        attr(temporalText, "lenient", refs.booleanType, 1, 1,
                "Reads texts that are close: numbers with fewer digits than the pattern writes, names and "
                        + "literals in any case, abbreviated names with or without their dot, a name by three or "
                        + "more of its first letters where that is one name, a local time in the gap of a change "
                        + "of offset moved forward. Values out of range stay errors.")
                .setDefaultValueLiteral("false");

        attr(temporalNumber, "epoch", refs.stringType, 1, 1,
                "Where the count starts, in ISO 8601: an instant (1970-01-01T00:00:00Z) or, without an offset, a "
                        + "local date or date and time (1899-12-30).");
        EReference unit = ref(temporalNumber, "unit", refs.unit, 1, 1,
                "The unit of the count, a unit of time: second, millisecond, day.");
        oppositeRole(unit, "notationTemporalNumber");
        attr(temporalNumber, "zone", refs.stringType, 0, 1,
                "The zone of a local epoch counted in days or longer units: it makes the local date and time an "
                        + "instant.");
        constrain(temporalNumber, "UnitOfTime",
                "self.unit.oclIsUndefined() or self.unit.dimension.oclIsUndefined() or "
                        + "(self.unit.dimension.time = 1 and self.unit.dimension.length = 0 and "
                        + "self.unit.dimension.mass = 0 and self.unit.dimension.electricCurrent = 0 and "
                        + "self.unit.dimension.thermodynamicTemperature = 0 and "
                        + "self.unit.dimension.amountOfSubstance = 0 and self.unit.dimension.luminousIntensity = 0)");

        attr(numberText, "decimalChar", refs.stringType, 1, 1,
                "The character between the whole part and the fraction, one character.")
                .setDefaultValueLiteral(".");
        attr(numberText, "groupChar", refs.stringType, 0, 1,
                "The character between groups of digits of the whole part, one character other than the decimal "
                        + "character; without one a text groups no digits.");
        attr(numberText, "pattern", refs.stringType, 0, 1,
                "A number pattern of Unicode LDML written with this decimal and group character: 0 a digit, # a "
                        + "digit where needed, E an exponent, text in single quotes, a negative subpattern after ;. "
                        + "No percent or per-mille sign outside quotes: a scale is a unit.");
        attr(numberText, "bare", refs.booleanType, 1, 1,
                "The text is the number alone. Where false, white space, currency signs, percent and per-mille "
                        + "signs and letters around the number are stripped on reading.")
                .setDefaultValueLiteral("true");
        attr(numberText, "lenient", refs.booleanType, 1, 1,
                "Reads a group character between any two digits of the whole part, as CSV on the Web reads it, "
                        + "whatever groups the pattern states. Where false, the groups are those of the pattern or, "
                        + "without one, the usual ones: a first group of one to three digits, then groups of three. "
                        + "A text without group characters fits either way; a writer writes the proper groups.")
                .setDefaultValueLiteral("false");
        constrain(numberText, "DecimalCharIsOneCharacter", "self.decimalChar.size() = 1");
        constrain(numberText, "GroupCharIsOneCharacter", "self.groupChar.oclIsUndefined() or self.groupChar.size() = 1");
        constrain(numberText, "DecimalAndGroupDiffer",
                "self.groupChar.oclIsUndefined() or self.groupChar <> self.decimalChar");

        attr(booleanText, "trueWords", refs.stringType, 1, -1, "The words for true; the first is written.");
        attr(booleanText, "falseWords", refs.stringType, 1, -1, "The words for false; the first is written.");
        attr(booleanText, "ignoreCase", refs.booleanType, 1, 1, "Reads the words in any case.")
                .setDefaultValueLiteral("false");
        constrain(booleanText, "NoWordTrueAndFalse", "self.trueWords->excludesAll(self.falseWords)");

        attr(patternText, "pattern", refs.stringType, 1, 1,
                "A regular expression in the syntax of ECMAScript the whole text matches.");
        return p;
    }

    /** Adds an OCL invariant: Ecore constraints list plus the OCL expression of the same name. */
    private void constrain(EClass c, String name, String ocl) {
        EAnnotation constraints = c.getEAnnotation(ECORE);
        if (constraints == null) {
            constraints = ef.createEAnnotation();
            constraints.setSource(ECORE);
            c.getEAnnotations().add(0, constraints);
        }
        String names = constraints.getDetails().get("constraints");
        constraints.getDetails().put("constraints", names == null ? name : names + " " + name);
        EAnnotation expressions = c.getEAnnotation(ECORE_OCL);
        if (expressions == null) {
            expressions = ef.createEAnnotation();
            expressions.setSource(ECORE_OCL);
            c.getEAnnotations().add(1, expressions);
        }
        expressions.getDetails().put(name, ocl);
    }

    /** {@code Property.oppositeRoleName} pair (convention for hidden association ends). */
    private void oppositeRole(EReference r, String roleName) {
        EAnnotation a1 = ef.createEAnnotation();
        a1.setSource(EMOF);
        a1.getDetails().put("Property.oppositeRoleName", roleName);
        r.getEAnnotations().add(a1);
        EAnnotation a2 = ef.createEAnnotation();
        a2.setSource(EMOF + "#Property.oppositeRoleName");
        a2.getDetails().put("body", roleName);
        r.getEAnnotations().add(a2);
    }

    private EClass cls(EPackage pkg, String name, boolean isAbstract, String doc) {
        EClass c = ef.createEClass();
        c.setName(name);
        c.setAbstract(isAbstract);
        pkg.getEClassifiers().add(c);
        annotate(c, doc);
        return c;
    }

    private EEnum enm(EPackage pkg, String name, String doc, String... literals) {
        EEnum e = ef.createEEnum();
        e.setName(name);
        int value = 0;
        for (String literal : literals) {
            EEnumLiteral l = ef.createEEnumLiteral();
            l.setName(literal);
            l.setValue(value++);
            e.getELiterals().add(l);
        }
        pkg.getEClassifiers().add(e);
        annotate(e, doc);
        return e;
    }

    private EAttribute attr(EClass owner, String name, EClassifier type, int lower, int upper, String doc) {
        EAttribute a = ef.createEAttribute();
        a.setName(name);
        a.setEType(type);
        a.setLowerBound(lower);
        a.setUpperBound(upper);
        owner.getEStructuralFeatures().add(a);
        annotate(a, doc);
        return a;
    }

    private EReference ref(EClass owner, String name, EClassifier type, int lower, int upper, String doc) {
        EReference r = ef.createEReference();
        r.setName(name);
        r.setEType(type);
        r.setLowerBound(lower);
        r.setUpperBound(upper);
        if (upper != 1) {
            r.setOrdered(false);
        }
        owner.getEStructuralFeatures().add(r);
        annotate(r, doc);
        return r;
    }

    /** A containment reference. */
    private EReference cont(EClass owner, String name, EClassifier type, int lower, int upper, String doc) {
        EReference r = ef.createEReference();
        r.setName(name);
        r.setEType(type);
        r.setLowerBound(lower);
        r.setUpperBound(upper);
        r.setContainment(true);
        owner.getEStructuralFeatures().add(r);
        annotate(r, doc);
        return r;
    }

    private EAnnotation annotate(EModelElement el, String doc) {
        EAnnotation a = ef.createEAnnotation();
        a.setSource(GENMODEL);
        a.getDetails().put("documentation", doc);
        el.getEAnnotations().add(a);
        return a;
    }

    /** A resource set reading {@code .ecore} files. */
    public static ResourceSet newResourceSet() {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
        return rs;
    }

    /**
     * Loads an ecore; its resource URI becomes the nsURI, so that references serialize as {@code <nsURI>#//Class}.
     */
    private static EPackage loadPackage(ResourceSet rs, URI ecoreUri) {
        Resource res = rs.getResource(ecoreUri, true);
        EPackage pkg = (EPackage) res.getContents().get(0);
        res.setURI(URI.createURI(pkg.getNsURI()));
        rs.getPackageRegistry().put(pkg.getNsURI(), pkg);
        return pkg;
    }

    private static URI ecoreUri(File dir, String path) {
        File f = new File(dir, path);
        if (!f.isFile()) {
            throw new IllegalStateException("ecore not found: " + f);
        }
        return URI.createFileURI(f.getAbsolutePath());
    }

    private static EClass eClass(EPackage pkg, String name) {
        if (pkg.getEClassifier(name) instanceof EClass c) {
            return c;
        }
        throw new IllegalStateException("EClass not found: " + name + " in " + pkg.getName());
    }

    private static EDataType eDataType(EPackage pkg, String name) {
        if (pkg.getEClassifier(name) instanceof EDataType d) {
            return d;
        }
        throw new IllegalStateException("EDataType not found: " + name + " in " + pkg.getName());
    }

    /** Writes the package as UTF-8 with the EPL header after the XML declaration. */
    public static void save(EPackage pkg, ResourceSet rs, URI outUri) throws IOException {
        Resource out = rs.createResource(outUri);
        out.getContents().add(pkg);
        Map<Object, Object> opts = new HashMap<>();
        opts.put(XMLResource.OPTION_ENCODING, "UTF-8");
        opts.put(XMLResource.OPTION_LINE_WIDTH, Integer.valueOf(96));
        out.save(opts);
        Path file = Path.of(outUri.toFileString());
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String comment = "<!--\n"
                + "/*********************************************************************\n"
                + "* Copyright (c) 2026 Contributors to the Eclipse Foundation.\n"
                + "*\n"
                + "* This program and the accompanying materials are made\n"
                + "* available under the terms of the Eclipse Public License 2.0\n"
                + "* which is available at https://www.eclipse.org/legal/epl-2.0/\n"
                + "*\n"
                + "* SPDX-License-Identifier: EPL-2.0\n"
                + "**********************************************************************/\n"
                + "-->\n";
        int nl = content.indexOf('\n');
        Files.writeString(file, content.substring(0, nl + 1) + comment + content.substring(nl + 1),
                StandardCharsets.UTF_8);
    }
}
