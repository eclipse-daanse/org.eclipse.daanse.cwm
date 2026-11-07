/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.recording.build;

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
 * Programmatic builder for the {@code recording} metamodel: which value changes of a feature are
 * recorded, and how the history of a class or an attribute is kept.
 *
 * <p>Every rule is a {@code lifecycle::LifecycleRule}, so a {@code core::Constraint} on the
 * elements it governs. Construction and serialization follow the builder of {@code lifecycle};
 * references into {@code lifecycle} and {@code objectmodel/core} are written with the namespace
 * URI of their package, as the code generator resolves them from the dependencies.</p>
 */
public final class RecordingEcoreBuilder {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final String NS_URI =
            "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.governance.recording";

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

    /** The classifiers of {@code objectmodel/core} and {@code lifecycle} the metamodel refers to. */
    public static final class Refs {
        final EClass structuralFeature;
        final EClass classifier;
        final EDataType floatType;
        final EDataType integerType;
        final EClass lifecycleRule;
        final EDataType duration;

        Refs(EPackage core, EPackage lifecycle) {
            this.structuralFeature = eClass(core, "StructuralFeature");
            this.classifier = eClass(core, "Classifier");
            this.floatType = eDataType(core, "Float");
            this.integerType = eDataType(core, "Integer");
            this.lifecycleRule = eClass(lifecycle, "LifecycleRule");
            this.duration = eDataType(lifecycle, "Duration");
        }
    }

    public RecordingEcoreBuilder(Refs refs) {
        this.refs = refs;
    }

    /**
     * Builds the metamodel from the CWM Ecores and the {@code lifecycle} Ecore next to
     * {@code moduleDir} (module directory {@code model/daanse/governance/recording}) and writes it
     * to {@code outFile}.
     */
    public static void generate(File moduleDir, File outFile) throws IOException {
        File cwmDir = new File(moduleDir, "../../../cwm").getCanonicalFile();
        File lifecycleDir = new File(moduleDir, "../lifecycle").getCanonicalFile();
        ResourceSet rs = newResourceSet();
        EPackage core = loadPackage(rs, ecoreUri(cwmDir, "objectmodel.core/model/core.ecore"));
        EPackage lifecycle = loadPackage(rs, ecoreUri(lifecycleDir, "model/lifecycle.ecore"));
        outFile.getParentFile().mkdirs();
        save(new RecordingEcoreBuilder(new Refs(core, lifecycle)).build(), rs,
                URI.createFileURI(outFile.getAbsolutePath()));
    }

    /** Creates the EPackage {@code recording}: 9 classes (1 abstract), 3 enumerations. */
    public EPackage build() {
        EPackage p = ef.createEPackage();
        p.setName("recording");
        p.setNsURI(NS_URI);
        p.setNsPrefix("recording");
        EAnnotation pa = annotate(p,
                "Capture and historisation rules for any CWM element. A recording rule decides which "
                        + "value changes of a feature are recorded: a change of at least a delta, a percent "
                        + "of the last value or a percent of the measurement range, one value out of every "
                        + "n, at most one value per minimum interval, changed values only, swinging-door "
                        + "compression; every rule may add a minimum interval, a heartbeat that records "
                        + "regardless after a maximum interval, a status feature whose changes record, the "
                        + "handling of values older than the last recorded one, and the interpolation "
                        + "between recorded values. A historisation rule decides how the history of a class "
                        + "or of an attribute is kept: overwritten, as new versions with their validity, in "
                        + "a column of the previous value, in a history table. Every rule is a "
                        + "lifecycle::LifecycleRule, a core::Constraint on the elements it governs; of the "
                        + "rules of a kind the nearest one along feature, owner and namespaces applies.");
        pa.getDetails().put("modelName", "Recording");
        pa.getDetails().put("copyrightText", EPL_HEADER);
        pa.getDetails().put("oSGiCompatible", "true");
        pa.getDetails().put("basePackage", "org.eclipse.daanse.cwm.model.daanse.governance");
        pa.getDetails().put("resource", "XMI");
        pa.getDetails().put("prefix", "Recording");

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

        EEnum lateValues = enm(p, "LateValueHandling",
                "What happens to a value older than the last recorded one. compare (the default): it is "
                        + "judged like any other value — recorded, it becomes the last recorded value — and a "
                        + "minimum interval drops it; drop: it is never recorded; insert: it is always "
                        + "recorded, and the last recorded value stays the newest one.",
                "compare", "drop", "insert");
        EEnum interpolation = enm(p, "Interpolation",
                "How a value between two recorded values is read: linear (the default, OPC UA sloped) "
                        + "between the two, or stepped — the earlier value holds until the next.",
                "linear", "stepped");
        EEnum kind = enm(p, "HistorisationKind",
                "How the history of a class or an attribute is kept, after the slowly changing "
                        + "dimensions of a data warehouse. overwrite: no history, the new value replaces the "
                        + "old one (type 1). newVersion: a change makes a new row with its validity (type 2); "
                        + "of the values of a feature, each value is a version valid from its own time, the "
                        + "series of a feature. "
                        + "previousValue: the previous value is kept in a column of its own (type 3). "
                        + "historyTable: the current row stays, the history goes to a table of its own "
                        + "(type 4). versionAndCurrent: new versions, and the current value in a column of "
                        + "every version (type 6). snapshot: the whole state of the rows at regular times. "
                        + "previousValue and versionAndCurrent act on the values of an attribute, snapshot on "
                        + "rows. A value that may not change at all is a frozen feature of CWM (type 0).",
                "overwrite", "newVersion", "previousValue", "historyTable", "versionAndCurrent", "snapshot");

        EClass recordingRule = cls(p, "RecordingRule", true,
                "Decides which value changes of a feature are recorded. Every rule compares against "
                        + "the last recorded value; the first value is always recorded. In this order: a "
                        + "late value is handled by lateValues; a value sooner than minimumInterval after "
                        + "the last recorded one is dropped; a value is recorded once the last one is older "
                        + "than maximumInterval, or when the status feature changed; else the criterion of "
                        + "the rule decides. A value that is no number passes a rule that compares numbers. "
                        + "At most one recording rule applies to an element, and it acts on values.");
        recordingRule.getESuperTypes().add(refs.lifecycleRule);
        attr(recordingRule, "minimumInterval", refs.duration, 0, 1,
                "The shortest time between two recorded values, even if the value changes: a value "
                        + "that arrives sooner after the last recorded one is not recorded. Unset: no minimum.");
        attr(recordingRule, "maximumInterval", refs.duration, 0, 1,
                "Heartbeat: a value is recorded regardless of the rule once the last recorded value "
                        + "is older than this, so that an unchanged value still shows up at least once per "
                        + "interval. Unset: no heartbeat.");
        attr(recordingRule, "lateValues", lateValues, 1, 1,
                "What happens to a value older than the last recorded one.");
        attr(recordingRule, "interpolation", interpolation, 1, 1,
                "How a value between two recorded values is read; also what swinging-door compression "
                        + "and an aggregation of the values assume.");
        EReference status = ref(recordingRule, "statusFeature", refs.structuralFeature, 0, 1,
                "A feature of the same classifier that holds the status or quality of the value: a "
                        + "change of the status records the value even if the value itself did not change "
                        + "(OPC UA data change trigger status-value).");
        oppositeRole(status, "recordingStatusRule");
        constrain(recordingRule, "ValuesOnly", "self.scope = lifecycle::DataScope::value");

        EClass absolute = cls(p, "AbsoluteChangeRule", false,
                "Records a value when it differs from the last recorded value by at least delta "
                        + "(a dead band, OPC UA ExceptionDeviation as absolute value): |value - last| >= delta.");
        absolute.getESuperTypes().add(recordingRule);
        attr(absolute, "delta", refs.floatType, 1, 1,
                "The smallest absolute change that is recorded, a non-negative decimal number in "
                        + "the unit of the value.");

        EClass relative = cls(p, "RelativeChangeRule", false,
                "Records a value when it differs from the last recorded value by at least percent "
                        + "percent of the last value: |value - last| / |last| * 100 >= percent. Against a "
                        + "last value of zero every change is recorded.");
        relative.getESuperTypes().add(recordingRule);
        attr(relative, "percent", refs.floatType, 1, 1,
                "The smallest relative change that is recorded, in percent of the last recorded "
                        + "value, a non-negative decimal number.");

        EClass range = cls(p, "RangeChangeRule", false,
                "Records a value when it differs from the last recorded value by at least percent "
                        + "percent of the measurement range: |value - last| >= percent / 100 * (high - low) "
                        + "(OPC UA percent dead band of the engineering-unit range).");
        range.getESuperTypes().add(recordingRule);
        attr(range, "percent", refs.floatType, 1, 1,
                "The smallest change that is recorded, in percent of the range, a decimal number from "
                        + "0 to 100.");
        attr(range, "low", refs.floatType, 1, 1,
                "The low end of the measurement range, in the unit of the value.");
        attr(range, "high", refs.floatType, 1, 1,
                "The high end of the measurement range, in the unit of the value; above low.");

        EClass sampling = cls(p, "SamplingRule", false,
                "Records one value out of every n values, however much they change: the first, the "
                        + "n+1st, the 2n+1st and so on.");
        sampling.getESuperTypes().add(recordingRule);
        attr(sampling, "every", refs.integerType, 1, 1,
                "n: one value out of every n is recorded; 1 records every value.");

        EClass throttle = cls(p, "ThrottleRule", false,
                "Records every value, at most one per minimumInterval: a value that arrives sooner than "
                        + "minimumInterval after the last recorded one is not recorded, and neither is a "
                        + "value older than the last recorded one. With maximumInterval equal to "
                        + "minimumInterval it samples periodically.");
        throttle.getESuperTypes().add(recordingRule);
        constrain(throttle, "HasMinimumInterval", "not self.minimumInterval.oclIsUndefined()");

        EClass changedValue = cls(p, "ChangedValueRule", false,
                "Records a value only when it differs from the last recorded value; numbers are "
                        + "compared by their value.");
        changedValue.getESuperTypes().add(recordingRule);

        EClass swingingDoor = cls(p, "SwingingDoorRule", false,
                "Swinging-door compression: a value is held until a later value can no longer be "
                        + "reached by a straight line from the last recorded value within deviation of every "
                        + "value in between; then the held value is recorded and the doors swing anew from "
                        + "it. What is recorded draws the curve within deviation.");
        swingingDoor.getESuperTypes().add(recordingRule);
        attr(swingingDoor, "deviation", refs.floatType, 1, 1,
                "How far the curve of the recorded values may lie from a value, a non-negative decimal "
                        + "number in the unit of the value (the precision of the instrument).");

        EClass historisation = cls(p, "HistorisationRule", false,
                "Decides how the history of the governed elements is kept. On a classifier with scope "
                        + "row: how its rows keep their history — new versions with their validity, a "
                        + "history table, snapshots. On an attribute (scope value, also as the default of a "
                        + "classifier or namespace for its features): what a change of that attribute does — "
                        + "overwrite, make a new version, keep the previous value or the current value in a "
                        + "column of its own. The anchors name the features and the classifier that hold the "
                        + "history.");
        historisation.getESuperTypes().add(refs.lifecycleRule);
        attr(historisation, "kind", kind, 1, 1, "How the history is kept.");
        EReference validFrom = ref(historisation, "validFrom", refs.structuralFeature, 0, 1,
                "The feature that holds the time from which a version is valid, or the time of a "
                        + "snapshot.");
        oppositeRole(validFrom, "recordingValidFromRule");
        EReference validTo = ref(historisation, "validTo", refs.structuralFeature, 0, 1,
                "The feature that holds the time to which a version is valid; unset: the next version "
                        + "ends it.");
        oppositeRole(validTo, "recordingValidToRule");
        EReference currentFlag = ref(historisation, "currentFlag", refs.structuralFeature, 0, 1,
                "The feature that marks the current version.");
        oppositeRole(currentFlag, "recordingCurrentFlagRule");
        EReference previousValue = ref(historisation, "previousValue", refs.structuralFeature, 0, 1,
                "The feature that holds the previous value of the governed attribute.");
        oppositeRole(previousValue, "recordingPreviousValueRule");
        EReference currentValue = ref(historisation, "currentValue", refs.structuralFeature, 0, 1,
                "The feature that holds the current value of the governed attribute in every version.");
        oppositeRole(currentValue, "recordingCurrentValueRule");
        EReference history = ref(historisation, "history", refs.classifier, 0, 1,
                "The classifier that holds the history rows.");
        oppositeRole(history, "recordingHistoryRule");
        constrain(historisation, "KindHasItsAnchors",
                "((self.kind = HistorisationKind::snapshot or (self.kind = HistorisationKind::newVersion "
                        + "and self.scope = lifecycle::DataScope::row)) implies not self.validFrom.oclIsUndefined()) "
                        + "and (self.kind = HistorisationKind::previousValue implies not self.previousValue.oclIsUndefined()) "
                        + "and (self.kind = HistorisationKind::versionAndCurrent implies not self.currentValue.oclIsUndefined()) "
                        + "and (self.kind = HistorisationKind::historyTable implies not self.history.oclIsUndefined())");

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
     * Loads an ecore; its resource URI becomes the nsURI, so that references serialize as
     * {@code <nsURI>#//Class}.
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
