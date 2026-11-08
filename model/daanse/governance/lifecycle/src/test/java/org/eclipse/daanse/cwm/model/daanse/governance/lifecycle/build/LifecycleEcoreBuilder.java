/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.build;

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
 * Programmatic builder for the {@code lifecycle} metamodel: the base of the lifecycle rules of any
 * CWM element and the retention schedules.
 *
 * <p>A rule is a {@code core::Constraint}: CWM already attaches a rule to any number of model
 * elements through {@code Constraint.constrainedElement}, whose opposite
 * {@code ModelElement.constraint} lets every element list its rules without an inverse index.
 * The inherited mandatory {@code body} carries the rule as text in the language
 * {@code daanse.lifecycle}; the typed attributes of the concrete rule are the truth. A retention
 * schedule contains what is its own — the start of its periods, its stages, its limits; every
 * other reference is one-way with an opposite role name.</p>
 *
 * <p>Construction and serialization follow {@code model/daanse/mapping.relational/.../OrmEcoreBuilder}.</p>
 */
public final class LifecycleEcoreBuilder {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final String NS_URI =
            "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.governance.lifecycle";

    private static final String EPL_HEADER = String.join("\n",
            "Copyright (c) 2026 Contributors to the Eclipse Foundation.",
            "",
            "This program and the accompanying materials are made",
            "available under the terms of the Eclipse Public License 2.0",
            "which is available at https://www.eclipse.org/legal/epl-2.0/",
            "",
            "SPDX-License-Identifier: EPL-2.0");

    private final EcoreFactory ef = EcoreFactory.eINSTANCE;
    private final CwmRefs cwm;

    /** The classifiers of {@code objectmodel/core}, {@code behavioral} and {@code businessinformation} the metamodel refers to. */
    public static final class CwmRefs {
        final EClass element;
        final EClass modelElement;
        final EClass namespace;
        final EClass classifier;
        final EClass constraint;
        final EClass structuralFeature;
        final EClass event;
        final EClass responsibleParty;
        final EDataType stringType;
        final EDataType integerType;
        final EDataType booleanType;

        CwmRefs(EPackage core, EPackage behavioral, EPackage businessinformation) {
            this.element = eClass(core, "Element");
            this.modelElement = eClass(core, "ModelElement");
            this.namespace = eClass(core, "Namespace");
            this.classifier = eClass(core, "Classifier");
            this.constraint = eClass(core, "Constraint");
            this.structuralFeature = eClass(core, "StructuralFeature");
            this.event = eClass(behavioral, "Event");
            this.responsibleParty = eClass(businessinformation, "ResponsibleParty");
            this.stringType = eDataType(core, "String");
            this.integerType = eDataType(core, "Integer");
            this.booleanType = eDataType(core, "Boolean");
        }
    }

    public LifecycleEcoreBuilder(CwmRefs cwm) {
        this.cwm = cwm;
    }

    /**
     * Builds the metamodel from the CWM Ecores next to {@code moduleDir} (module directory
     * {@code model/daanse/governance/lifecycle}) and writes it to {@code outFile}.
     */
    public static void generate(File moduleDir, File outFile) throws IOException {
        File cwmDir = new File(moduleDir, "../../../cwm").getCanonicalFile();
        ResourceSet rs = newResourceSet();
        EPackage core = loadPackage(rs, ecoreUri(cwmDir, "objectmodel.core/model/core.ecore"));
        EPackage behavioral = loadPackage(rs, ecoreUri(cwmDir, "objectmodel.behavioral/model/behavioral.ecore"));
        EPackage businessinformation = loadPackage(rs,
                ecoreUri(cwmDir, "foundation.businessinformation/model/businessinformation.ecore"));
        outFile.getParentFile().mkdirs();
        save(new LifecycleEcoreBuilder(new CwmRefs(core, behavioral, businessinformation)).build(), rs,
                URI.createFileURI(outFile.getAbsolutePath()));
    }

    /**
     * Creates the EPackage {@code lifecycle}: 19 classes (6 abstract), 3 enumerations, 2 data
     * types.
     */
    public EPackage build() {
        EPackage p = ef.createEPackage();
        p.setName("lifecycle");
        p.setNsURI(NS_URI);
        p.setNsPrefix("lifecycle");
        EAnnotation pa = annotate(p,
                "The base of the lifecycle models of a CWM element, and its retention schedules. A "
                        + "lifecycle rule is a core::Constraint that names the elements it governs in "
                        + "constrainedElement, and every element finds its rules in ModelElement.constraint. A "
                        + "rule on a feature applies to that feature, a rule on a classifier to its features or "
                        + "rows, a rule on a namespace to everything in it; of the rules of a kind and scope the "
                        + "nearest one wins. The scope says what a rule acts on: each value of a feature, one "
                        + "series per feature, or each row of a classifier. The inherited body holds the rule as "
                        + "text in the language daanse.lifecycle, the typed attributes are the truth. A retention "
                        + "schedule says what happens to data as it ages: from the start of its periods — the "
                        + "time of a value, the value of a time feature, the time of a business event, aligned to "
                        + "the end of a month, quarter or year and offset — its stages follow one another (tier, "
                        + "restrict, review, notify, aggregate, transfer, delete), and its limits keep the newest "
                        + "values or bytes. Which value changes are recorded is the model recording; obligations "
                        + "and de-identification build on this model.");
        pa.getDetails().put("modelName", "Lifecycle");
        pa.getDetails().put("copyrightText", EPL_HEADER);
        pa.getDetails().put("oSGiCompatible", "true");
        pa.getDetails().put("basePackage", "org.eclipse.daanse.cwm.model.daanse.governance");
        pa.getDetails().put("resource", "XMI");
        pa.getDetails().put("prefix", "Lifecycle");

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

        EDataType duration = ef.createEDataType();
        duration.setName("Duration");
        duration.setInstanceClassName("java.lang.String");
        p.getEClassifiers().add(duration);
        annotate(duration, "An exact amount of time as an ISO-8601 duration literal in the grammar of "
                + "java.time.Duration: days of 24 hours, hours, minutes and seconds (P90D, PT10M, PT1H30M, "
                + "P1DT12H); months and years are not durations of fixed length and are not allowed. For "
                + "intervals of recording and cleanup. Backed by a string so that a model loaded dynamically "
                + "from this ecore reads it too.");

        EDataType period = ef.createEDataType();
        period.setName("Period");
        period.setInstanceClassName("java.lang.String");
        p.getEClassifiers().add(period);
        annotate(period, "An amount of calendar time as an ISO-8601 period literal: years, months, weeks "
                + "and days, and an optional time part (P10Y, P6M, P2W, P90D, P1Y6M, P1DT12H). Years and "
                + "months are calendar units of varying length, added in the time zone of the start; weeks, "
                + "days of 24 hours and the time part are exact. For the stages and terms of retention. "
                + "Backed by a string.");

        EEnum dataScope = enm(p, "DataScope",
                "What a rule acts on. value: each value of a feature, one series of values per feature "
                        + "(the history of a column, of a resource of a digital twin); a value rule on a "
                        + "classifier or namespace is the default for each of its features. row: each row of "
                        + "a classifier (the records of a table, of a record file); a row rule names "
                        + "classifiers or namespaces, never a feature.",
                "value", "row");
        EEnum alignment = enm(p, "Alignment",
                "To which boundary the start of a period moves before it runs: none, or the end of the "
                        + "month, the quarter or the year of the start — the first instant of the next month, "
                        + "quarter or year in the time zone of the start (a retention period that begins with "
                        + "the end of the calendar year).",
                "none", "endOfMonth", "endOfQuarter", "endOfYear");
        EEnum function = enm(p, "AggregateFunction",
                "How the values of an interval are summarized when they are aggregated.",
                "average", "minimum", "maximum", "sum", "count", "first", "last");

        EClass lifecycleRule = cls(p, "LifecycleRule", true,
                "A rule about the data of the elements it constrains: a core::Constraint whose "
                        + "constrainedElement names the governed elements. A rule on a feature applies to "
                        + "that feature, on a classifier to its features or rows, on a namespace to "
                        + "everything in it; of the rules of a kind and scope the nearest one wins (the "
                        + "element itself, then the owner of a feature, then the namespaces outward). The "
                        + "inherited body is the rule as text in the language daanse.lifecycle; the typed "
                        + "attributes of the concrete rule are the truth.");
        lifecycleRule.getESuperTypes().add(cwm.constraint);
        attr(lifecycleRule, "scope", dataScope, 1, 1,
                "What the rule acts on: each value of the governed features (the default) or each row "
                        + "of the governed classifiers.");
        constrain(lifecycleRule, "ConstrainsAnElement", "self.constrainedElement->notEmpty()");
        constrain(lifecycleRule, "RowsNotOnFeature", "self.scope = DataScope::row implies "
                + "self.constrainedElement->forAll(e | not e.oclIsKindOf(core::StructuralFeature))");

        EClass periodStart = cls(p, "PeriodStart", true,
                "Where the periods of a schedule or a stage start: the subclass says from which time — "
                        + "of the value, of a feature, of a business event. The start moves to the end of "
                        + "the month, quarter or year (alignment, in zone) and then by offset; from there the "
                        + "after of each stage runs.");
        periodStart.getESuperTypes().add(cwm.element);
        attr(periodStart, "alignment", alignment, 1, 1,
                "To which boundary the start moves; none (the default) keeps it.");
        attr(periodStart, "offset", period, 0, 1,
                "A period added to the aligned start.");
        attr(periodStart, "zone", cwm.stringType, 0, 1,
                "The IANA time zone in which the start is aligned and calendar periods are added "
                        + "(Europe/Berlin); required with an alignment. Unset: UTC.");
        constrain(periodStart, "AlignmentNeedsZone",
                "self.alignment = Alignment::none or (not self.zone.oclIsUndefined() and self.zone <> '')");

        EClass valueTime = cls(p, "ValueTime", false,
                "The periods start at the time of each value: when it was recorded, or when the row was "
                        + "written.");
        valueTime.getESuperTypes().add(periodStart);

        EClass featureTime = cls(p, "FeatureTime", false,
                "The periods start at the time a feature holds: the date column of a row, the time field "
                        + "of a record.");
        featureTime.getESuperTypes().add(periodStart);
        EReference featureTimeFeature = ref(featureTime, "feature", cwm.structuralFeature, 1, 1,
                "The feature that holds the start time; a feature of the governed classifier or of the "
                        + "owner of the governed feature.");
        oppositeRole(featureTimeFeature, "lifecycleTimeOf");

        EClass eventTime = cls(p, "EventTime", false,
                "The periods start at a business event: the end of a contract, the closing of an account, "
                        + "the withdrawal of a consent. Where the time of the event is held per row, feature "
                        + "names it.");
        eventTime.getESuperTypes().add(periodStart);
        EReference event = ref(eventTime, "event", cwm.event, 1, 1,
                "The event the periods start with.");
        oppositeRole(event, "lifecycleEventStart");
        EReference eventFeature = ref(eventTime, "feature", cwm.structuralFeature, 0, 1,
                "The feature that holds the time of the event for each row; unset where the start is "
                        + "abstract, as in a deletion class.");
        oppositeRole(eventFeature, "lifecycleEventTimeOf");

        EClass stage = cls(p, "Stage", true,
                "A step in the life of data, due after a period from the start: a stage moves, "
                        + "restricts, reviews, announces, summarizes, hands over or deletes. A stage is named "
                        + "and can be described like any model element.");
        stage.getESuperTypes().add(cwm.modelElement);
        attr(stage, "after", period, 0, 1,
                "How long after the start the stage is due; unset only where another rule gives it, as "
                        + "a deletion class gives the term of the last stage.");
        cont(stage, "start", periodStart, 0, 1,
                "A start of the stage's own, where it differs from the start of the schedule.");

        EClass destructive = cls(p, "DestructiveStage", true,
                "A stage after which data is no longer as it was: summarized, handed over, deleted. A "
                        + "retention hold suspends it.");
        destructive.getESuperTypes().add(stage);
        EClass terminal = cls(p, "TerminalStage", true,
                "A stage after which the data is gone: it ends the life of the data and fulfils a duty to "
                        + "delete. A schedule has at most one, as its last stage.");
        terminal.getESuperTypes().add(destructive);

        EClass delete = cls(p, "Delete", false, "The data is deleted.");
        delete.getESuperTypes().add(terminal);

        EClass aggregate = cls(p, "Aggregate", false,
                "The values are summarized per interval and the single values are given up: per day an "
                        + "average, a sum, a count. The summary goes to target where given.");
        aggregate.getESuperTypes().add(destructive);
        attr(aggregate, "interval", period, 1, 1, "The interval the values are summarized over.");
        attr(aggregate, "function", function, 1, 1, "How the values of an interval are summarized.");
        EReference aggregateTarget = ref(aggregate, "target", cwm.classifier, 0, 1,
                "The classifier that holds the summaries: the table of daily values.");
        oppositeRole(aggregateTarget, "lifecycleAggregateTarget");

        EClass transfer = cls(p, "Transfer", false,
                "The data is handed over to another place and leaves this one: an archive, another "
                        + "schema, a data manager.");
        transfer.getESuperTypes().add(destructive);
        EReference transferTarget = ref(transfer, "target", cwm.namespace, 1, 1,
                "The namespace the data goes to: a schema, a package, a data manager.");
        oppositeRole(transferTarget, "lifecycleTransferTarget");

        EClass tier = cls(p, "Tier", false,
                "The data moves to another tier of storage and stays as it is: warm, cold, archive.");
        tier.getESuperTypes().add(stage);
        attr(tier, "tier", cwm.stringType, 1, 1, "The name of the tier: warm, cold, archive.");
        attr(tier, "compressed", cwm.booleanType, 1, 1, "Whether the data is compressed in the tier.");

        EClass restrict = cls(p, "Restrict", false,
                "The data is kept but no longer processed except for what the restriction allows: kept "
                        + "for a legal claim, blocked for everything else.");
        restrict.getESuperTypes().add(stage);

        EClass review = cls(p, "Review", false,
                "A party reviews the data before the next stage: the disposition review of a records "
                        + "manager.");
        review.getESuperTypes().add(stage);
        EReference reviewer = ref(review, "reviewer", cwm.responsibleParty, 1, 1, "Who reviews.");
        oppositeRole(reviewer, "lifecycleReviewStage");

        EClass notify = cls(p, "Notify", false, "A party is told that the next stage is near.");
        notify.getESuperTypes().add(stage);
        EReference party = ref(notify, "party", cwm.responsibleParty, 1, 1, "Who is told.");
        oppositeRole(party, "lifecycleNotifyStage");

        EClass limit = cls(p, "Limit", true,
                "A bound on how much data is kept, whatever its age; the oldest data beyond it goes.");
        limit.getESuperTypes().add(cwm.element);
        EClass countLimit = cls(p, "CountLimit", false,
                "Keeps the newest keep values, or rows; with key, the newest keep per value of the key "
                        + "features (the newest ten versions per customer).");
        countLimit.getESuperTypes().add(limit);
        attr(countLimit, "keep", cwm.integerType, 1, 1, "How many of the newest are kept; at least 1.");
        EReference key = ref(countLimit, "key", cwm.structuralFeature, 0, -1,
                "The features whose values group what is counted; empty: everything governed counts "
                        + "together.");
        oppositeRole(key, "lifecycleCountLimitKey");
        EClass sizeLimit = cls(p, "SizeLimit", false, "Keeps the newest data up to bytes in size.");
        sizeLimit.getESuperTypes().add(limit);
        attr(sizeLimit, "bytes", cwm.integerType, 1, 1, "The size kept, in bytes; above 0.");

        EClass schedule = cls(p, "RetentionSchedule", false,
                "Decides what happens to the data of the governed elements as it ages: from the start of "
                        + "its periods the stages follow one another in order, each due after its period; the "
                        + "limits bound how much is kept. A cleanup runs every cleanupInterval and deletes at "
                        + "most maxDelete per run. Of the schedules of a scope the nearest one applies; a "
                        + "schedule of rows and one of values of an element apply together, and the last stage "
                        + "of the rows ends the values too.");
        schedule.getESuperTypes().add(lifecycleRule);
        cont(schedule, "start", periodStart, 0, 1,
                "Where the periods of the stages start, unless a stage has a start of its own.");
        EReference stages = cont(schedule, "stage", stage, 0, -1, "The stages, in the order they follow.");
        stages.setOrdered(true);
        EReference limits = cont(schedule, "limit", limit, 0, -1, "The limits, all of which hold.");
        limits.setOrdered(false);
        attr(schedule, "cleanupInterval", duration, 0, 1,
                "How often the cleanup runs; unset leaves the cadence to the runtime.");
        EAttribute maxDelete = attr(schedule, "maxDelete", cwm.integerType, 0, 1,
                "Safety cap on what a single cleanup run deletes; unset deletes without limit.");
        maxDelete.setUnsettable(true);
        constrain(schedule, "StageOrLimit", "self.stage->notEmpty() or self.limit->notEmpty()");
        constrain(schedule, "StagesHaveAStart", "self.stage->notEmpty() implies "
                + "(not self.start.oclIsUndefined() or self.stage->forAll(s | not s.start.oclIsUndefined()))");
        constrain(schedule, "AtMostOneTerminalStage",
                "self.stage->select(s | s.oclIsKindOf(TerminalStage))->size() <= 1");

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

    private EReference cont(EClass owner, String name, EClassifier type, int lower, int upper, String doc) {
        EReference r = ef.createEReference();
        r.setName(name);
        r.setEType(type);
        r.setLowerBound(lower);
        r.setUpperBound(upper);
        r.setContainment(true);
        r.setResolveProxies(false);
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
     * Loads a CWM ecore; its resource URI becomes the nsURI, so that references serialize as
     * {@code <nsURI>#//Class}.
     */
    private static EPackage loadPackage(ResourceSet rs, URI ecoreUri) {
        Resource res = rs.getResource(ecoreUri, true);
        EPackage pkg = (EPackage) res.getContents().get(0);
        res.setURI(URI.createURI(pkg.getNsURI()));
        rs.getPackageRegistry().put(pkg.getNsURI(), pkg);
        return pkg;
    }

    private static URI ecoreUri(File cwmDir, String path) {
        File f = new File(cwmDir, path);
        if (!f.isFile()) {
            throw new IllegalStateException("CWM ecore not found: " + f);
        }
        return URI.createFileURI(f.getAbsolutePath());
    }

    private static EClass eClass(EPackage pkg, String name) {
        if (pkg.getEClassifier(name) instanceof EClass c) {
            return c;
        }
        throw new IllegalStateException("CWM EClass not found: " + name + " in " + pkg.getName());
    }

    private static EDataType eDataType(EPackage pkg, String name) {
        if (pkg.getEClassifier(name) instanceof EDataType d) {
            return d;
        }
        throw new IllegalStateException("CWM EDataType not found: " + name + " in " + pkg.getName());
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
