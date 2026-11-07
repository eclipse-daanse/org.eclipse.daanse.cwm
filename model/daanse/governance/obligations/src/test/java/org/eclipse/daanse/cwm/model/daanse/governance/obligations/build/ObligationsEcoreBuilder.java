/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.obligations.build;

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
 * Programmatic builder for the {@code obligations} metamodel: the legal duties on the data of any
 * CWM element — to retain it for a minimum period, to delete it within a maximum period — with
 * their legal sources, the holds that suspend deletion, and the deletion classes that give kinds of
 * data a start and a standard term.
 *
 * <p>Every duty is a {@code lifecycle::LifecycleRule}, so a {@code core::Constraint} on the elements
 * it governs; a legal source is a {@code businessinformation::Document} that names what it rules.
 * Construction and serialization follow the builder of {@code lifecycle}; references into other
 * models are written with the namespace URI of their package.</p>
 */
public final class ObligationsEcoreBuilder {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final String NS_URI =
            "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.governance.obligations";

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

    /** The classifiers of the CWM models and of {@code lifecycle} the metamodel refers to. */
    public static final class Refs {
        final EClass document;
        final EClass responsibleParty;
        final EDataType stringType;
        final EDataType booleanType;
        final EDataType timeType;
        final EClass lifecycleRule;
        final EClass periodStart;
        final EDataType period;

        Refs(EPackage core, EPackage businessinformation, EPackage lifecycle) {
            this.document = eClass(businessinformation, "Document");
            this.responsibleParty = eClass(businessinformation, "ResponsibleParty");
            this.stringType = eDataType(core, "String");
            this.booleanType = eDataType(core, "Boolean");
            this.timeType = eDataType(core, "Time");
            this.lifecycleRule = eClass(lifecycle, "LifecycleRule");
            this.periodStart = eClass(lifecycle, "PeriodStart");
            this.period = eDataType(lifecycle, "Period");
        }
    }

    public ObligationsEcoreBuilder(Refs refs) {
        this.refs = refs;
    }

    /**
     * Builds the metamodel from the CWM Ecores and the {@code lifecycle} Ecore next to
     * {@code moduleDir} (module directory {@code model/daanse/governance/obligations}) and writes it
     * to {@code outFile}.
     */
    public static void generate(File moduleDir, File outFile) throws IOException {
        File cwmDir = new File(moduleDir, "../../../cwm").getCanonicalFile();
        File lifecycleDir = new File(moduleDir, "../lifecycle").getCanonicalFile();
        ResourceSet rs = newResourceSet();
        EPackage core = loadPackage(rs, ecoreUri(cwmDir, "objectmodel.core/model/core.ecore"));
        loadPackage(rs, ecoreUri(cwmDir, "objectmodel.behavioral/model/behavioral.ecore"));
        EPackage businessinformation = loadPackage(rs,
                ecoreUri(cwmDir, "foundation.businessinformation/model/businessinformation.ecore"));
        EPackage lifecycle = loadPackage(rs, ecoreUri(lifecycleDir, "model/lifecycle.ecore"));
        outFile.getParentFile().mkdirs();
        save(new ObligationsEcoreBuilder(new Refs(core, businessinformation, lifecycle)).build(), rs,
                URI.createFileURI(outFile.getAbsolutePath()));
    }

    /** Creates the EPackage {@code obligations}: 6 classes (1 abstract). */
    public EPackage build() {
        EPackage p = ef.createEPackage();
        p.setName("obligations");
        p.setNsURI(NS_URI);
        p.setNsPrefix("obligations");
        EAnnotation pa = annotate(p,
                "Legal duties on the data of any CWM element. A retention obligation demands that data is "
                        + "kept for a minimum period, a deletion obligation that it is deleted within a maximum "
                        + "period, each counted from a start and resting on legal sources. Of the obligations "
                        + "along the path of an element all apply, the strictest wins: the longest minimum, the "
                        + "shortest maximum. A retention hold suspends every destructive stage of the schedules "
                        + "of the data it governs while it lasts. A deletion class gives a kind of data an "
                        + "abstract start and a standard term, from which a retention schedule may take the term "
                        + "of its last stage. A legal source is a businessinformation::Document that names the "
                        + "duties it gives. Every duty, hold and class is a lifecycle::LifecycleRule, a "
                        + "core::Constraint on the elements it governs.");
        pa.getDetails().put("modelName", "Obligations");
        pa.getDetails().put("copyrightText", EPL_HEADER);
        pa.getDetails().put("oSGiCompatible", "true");
        pa.getDetails().put("basePackage", "org.eclipse.daanse.cwm.model.daanse.governance");
        pa.getDetails().put("resource", "XMI");
        pa.getDetails().put("prefix", "Obligations");

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

        EClass legalSource = cls(p, "LegalSource", false,
                "The legal ground of duties on data: a statute, a contract, an internal policy. A document "
                        + "(reference: where it is found, type: statute, contract or policy) that names the "
                        + "obligations, holds and deletion classes it gives in modelElement; provision is the "
                        + "part it is (a paragraph, an article, a clause), jurisdiction where it applies, "
                        + "validFrom and validTo when — a change of the law is a new source.");
        legalSource.getESuperTypes().add(refs.document);
        attr(legalSource, "jurisdiction", refs.stringType, 0, 1,
                "Where the source applies: a country, a state, the European Union (DE, DE-TH, EU).");
        attr(legalSource, "provision", refs.stringType, 0, 1,
                "The part of the source that gives the duty: § 147 (4), Art. 17, clause 12.");
        attr(legalSource, "validFrom", refs.timeType, 0, 1,
                "From when the source applies, an ISO-8601 date or instant.");
        attr(legalSource, "validTo", refs.timeType, 0, 1,
                "Until when the source applies, an ISO-8601 date or instant; unset: still in force.");

        EClass obligation = cls(p, "Obligation", true,
                "A legal duty on the data of the governed elements, counted from start. Of the obligations "
                        + "of a kind along the path of an element all apply, and the strictest wins.");
        obligation.getESuperTypes().add(refs.lifecycleRule);
        cont(obligation, "start", refs.periodStart, 1, 1, "Where the period of the duty starts.");

        EClass retention = cls(p, "RetentionObligation", false,
                "The data must be kept for at least minimum from the start: no stage that summarizes, "
                        + "hands over or deletes it may come before. immutable: it must also stay unchanged "
                        + "(write once, read many).");
        retention.getESuperTypes().add(obligation);
        attr(retention, "minimum", refs.period, 1, 1, "How long the data is kept at least.");
        attr(retention, "immutable", refs.booleanType, 1, 1, "Whether the data must stay unchanged while kept.");

        EClass deletion = cls(p, "DeletionObligation", false,
                "The data must be gone within maximum from the start: a terminal stage — the delete — "
                        + "must come by then. anonymizationSatisfies: making the data anonymous fulfils the duty "
                        + "as well, as the source says. Where a retention obligation demands a longer minimum, "
                        + "the data is restricted at the latest at the maximum and deleted at the minimum.");
        deletion.getESuperTypes().add(obligation);
        attr(deletion, "maximum", refs.period, 1, 1, "Within how long the data is gone.");
        attr(deletion, "anonymizationSatisfies", refs.booleanType, 1, 1,
                "Whether a stage that makes the data anonymous fulfils the duty.");

        EClass hold = cls(p, "RetentionHold", false,
                "A hold on the data of the governed elements — for a lawsuit, an audit, an investigation: "
                        + "from since until until (unset: until released) every destructive stage of their "
                        + "schedules is suspended. All holds that reach an element apply.");
        hold.getESuperTypes().add(refs.lifecycleRule);
        attr(hold, "since", refs.timeType, 1, 1, "From when the hold applies, an ISO-8601 instant.");
        attr(hold, "until", refs.timeType, 0, 1,
                "When the hold was released, an ISO-8601 instant; unset: it still applies.");
        attr(hold, "reason", refs.stringType, 1, 1, "Why the data is held.");
        EReference holder = ref(hold, "holder", refs.responsibleParty, 0, 1, "Who ordered the hold.");
        oppositeRole(holder, "obligationsHold");
        constrain(hold, "UntilAfterSince", "self.until.oclIsUndefined() or self.until > self.since");

        EClass deletionClass = cls(p, "DeletionClass", false,
                "A class of deletion: an abstract start — the time of a value, or a business event without "
                        + "the feature that holds its time — and a standard term. It governs the kinds of data "
                        + "that are deleted alike; a retention schedule on such data may leave the period of its "
                        + "last stage unset and take the term of the class, its start being the class's event "
                        + "bound to the feature of the data.");
        deletionClass.getESuperTypes().add(refs.lifecycleRule);
        cont(deletionClass, "start", refs.periodStart, 1, 1, "The abstract start of the term.");
        attr(deletionClass, "term", refs.period, 1, 1, "The standard term after the start.");
        constrain(deletionClass, "AbstractStart", "self.start.oclIsKindOf(lifecycle::ValueTime) or "
                + "(self.start.oclIsKindOf(lifecycle::EventTime) and "
                + "self.start.oclAsType(lifecycle::EventTime).feature.oclIsUndefined())");

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
