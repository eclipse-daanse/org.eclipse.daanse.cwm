/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.transparency.build;

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
 * Programmatic builder for the {@code transparency} metamodel: the transparency assessments of data
 * classes and fields under the Thuringian Transparency Act. A rating names the de-identification
 * policy its data is published under; how long the data is kept is a retention obligation of the
 * model {@code obligations}.
 *
 * <p>The German and English captions of the elements are GenModel details ({@code caption.de},
 * {@code caption.en}). Construction and serialization follow the builder of {@code lifecycle};
 * references into other models are written with the namespace URI of their package.</p>
 */
public final class TransparencyEcoreBuilder {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final String NS_URI =
            "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.governance.transparency";
    private static final String YEAR = "2026";

    private static final String EPL_HEADER = String.join("\n",
            "Copyright (c) " + YEAR + " Contributors to the Eclipse Foundation.",
            "",
            "This program and the accompanying materials are made",
            "available under the terms of the Eclipse Public License 2.0",
            "which is available at https://www.eclipse.org/legal/epl-2.0/",
            "",
            "SPDX-License-Identifier: EPL-2.0");

    private final EcoreFactory ef = EcoreFactory.eINSTANCE;
    private final Refs refs;

    /** The classifiers of the CWM models and of the Daanse models the metamodel refers to. */
    public static final class Refs {
        final EDataType stringType;
        final EDataType timeType;
        final EClass corePackage;
        final EClass modelElement;
        final EClass classifier;
        final EClass structuralFeature;
        final EClass responsibleParty;
        final EClass deidentificationPolicy;

        Refs(EPackage core, EPackage businessinformation, EPackage deidentification) {
            this.stringType = eDataType(core, "String");
            this.timeType = eDataType(core, "Time");
            this.corePackage = eClass(core, "Package");
            this.modelElement = eClass(core, "ModelElement");
            this.classifier = eClass(core, "Classifier");
            this.structuralFeature = eClass(core, "StructuralFeature");
            this.responsibleParty = eClass(businessinformation, "ResponsibleParty");
            this.deidentificationPolicy = eClass(deidentification, "DeidentificationPolicy");
        }
    }

    public TransparencyEcoreBuilder(Refs refs) {
        this.refs = refs;
    }

    /**
     * Builds the metamodel from the Ecores of the models it refers to, next to {@code moduleDir}
     * (module directory {@code model/daanse/governance/transparency}), and writes it to {@code outFile}.
     */
    public static void generate(File moduleDir, File outFile) throws IOException {
        File cwmDir = new File(moduleDir, "../../../cwm").getCanonicalFile();
        File governanceDir = new File(moduleDir, "..").getCanonicalFile();
        ResourceSet rs = newResourceSet();
        EPackage core = loadPackage(rs, ecoreUri(cwmDir, "objectmodel.core/model/core.ecore"));
        loadPackage(rs, ecoreUri(cwmDir, "objectmodel.behavioral/model/behavioral.ecore"));
        EPackage businessinformation = loadPackage(rs,
                ecoreUri(cwmDir, "foundation.businessinformation/model/businessinformation.ecore"));
        loadPackage(rs, ecoreUri(governanceDir, "lifecycle/model/lifecycle.ecore"));
        EPackage deidentification = loadPackage(rs,
                ecoreUri(governanceDir, "deidentification/model/deidentification.ecore"));
        outFile.getParentFile().mkdirs();
        save(new TransparencyEcoreBuilder(new Refs(core, businessinformation, deidentification)).build(), rs,
                URI.createFileURI(outFile.getAbsolutePath()));
    }

    /** Creates the EPackage {@code transparency}. */
    public EPackage build() {
        EPackage p = ef.createEPackage();
        p.setName("transparency");
        p.setNsURI(NS_URI);
        p.setNsPrefix("transparency");
        root(p, "Transparency", "Package of models for transparency obligation assessment according to the Thuringian "
                        + "Transparency Act (ThuerTG). How long published information is kept is a retention "
                        + "obligation of the model obligations, how its personal data is de-identified a policy "
                        + "of the model deidentification; the legal sources of an assessment are "
                        + "obligations::LegalSource documents that name it.",
                "Transparenzpflichten ThuerTG", "Transparency Obligations ThuerTG");


        EEnum publicationMode = doc(enm(p, "PublicationMode"),
                "Publication mode according to ThuerTG: proactive (§5/§6), on request (§9-§15) "
                        + "or excluded (§12/§13)",
                "Veroeffentlichungsart", "Publication Mode");
        doc(literal(publicationMode, "UNEDITED", 0, "UNEDITED"),
                "Unedited",
                null, null);
        doc(literal(publicationMode, "PROACTIVE_MANDATORY", 1, "PROACTIVE_MANDATORY"),
                "Mandatory proactive publication according to §5/§6 ThuerTG",
                null, null);
        doc(literal(publicationMode, "PROACTIVE_OPTIONAL", 2, "PROACTIVE_OPTIONAL"),
                "Voluntary proactive publication",
                null, null);
        doc(literal(publicationMode, "ON_REQUEST", 3, "ON_REQUEST"),
                "Access only on request according to §9-§15 ThuerTG",
                null, null);
        doc(literal(publicationMode, "EXCLUDED", 4, "EXCLUDED"),
                "Excluded from publication according to §12/§13 ThuerTG",
                null, null);

        EEnum transparencyCategory = doc(enm(p, "TransparencyCategory"),
                "Information categories according to §6 para. 3 no. 2 ThuerTG (a-r)",
                "Transparenzkategorie", "Transparency Category");
        doc(literal(transparencyCategory, "UNEDITED", 0, "UNEDITED"),
                "Unedited",
                null, null);
        doc(literal(transparencyCategory, "GESETZE", 1, "GESETZE"),
                "Laws and statutory orders (§6 para. 3 no. 2 lit. a)",
                null, null);
        doc(literal(transparencyCategory, "VERWALTUNGSVORSCHRIFTEN", 2, "VERWALTUNGSVORSCHRIFTEN"),
                "Administrative regulations and general directives (§6 para. 3 no. 2 lit. b)",
                null, null);
        doc(literal(transparencyCategory, "KABINETTSBESCHLUESSE", 3, "KABINETTSBESCHLUESSE"),
                "Cabinet decisions (§6 para. 3 no. 2 lit. c)",
                null, null);
        doc(literal(transparencyCategory, "BERICHTE", 4, "BERICHTE"),
                "Reports and communications (§6 para. 3 no. 2 lit. d)",
                null, null);
        doc(literal(transparencyCategory, "SPONSORING", 5, "SPONSORING"),
                "Sponsoring (§6 para. 3 no. 2 lit. e)",
                null, null);
        doc(literal(transparencyCategory, "BETEILIGUNGEN", 6, "BETEILIGUNGEN"),
                "Corporate participations (§6 para. 3 no. 2 lit. f)",
                null, null);
        doc(literal(transparencyCategory, "TAETIGKEITSBERICHTE", 7, "TAETIGKEITSBERICHTE"),
                "Activity reports (§6 para. 3 no. 2 lit. g)",
                null, null);
        doc(literal(transparencyCategory, "SITZUNGSBESCHLUESSE", 8, "SITZUNGSBESCHLUESSE"),
                "Meeting decisions and minutes (§6 para. 3 no. 2 lit. h)",
                null, null);
        doc(literal(transparencyCategory, "UMWELTINFORMATIONEN", 9, "UMWELTINFORMATIONEN"),
                "Environmental information (§6 para. 3 no. 2 lit. i)",
                null, null);
        doc(literal(transparencyCategory, "STATISTIKEN", 10, "STATISTIKEN"),
                "Statistics and data collections (§6 para. 3 no. 2 lit. j)",
                null, null);
        doc(literal(transparencyCategory, "PLAENE", 11, "PLAENE"),
                "Plans and concepts (§6 para. 3 no. 2 lit. k)",
                null, null);
        doc(literal(transparencyCategory, "VERTRAEGE", 12, "VERTRAEGE"),
                "Public service contracts (§6 para. 3 no. 2 lit. l)",
                null, null);
        doc(literal(transparencyCategory, "ZUWENDUNGEN", 13, "ZUWENDUNGEN"),
                "Grants and subsidies (§6 para. 3 no. 2 lit. m)",
                null, null);
        doc(literal(transparencyCategory, "VERGABEKAMMER", 14, "VERGABEKAMMER"),
                "Public procurement chamber decisions (§6 para. 3 no. 2 lit. n)",
                null, null);
        doc(literal(transparencyCategory, "BEURTEILUNGEN", 15, "BEURTEILUNGEN"),
                "Assessments and evaluations (§6 para. 3 no. 2 lit. o)",
                null, null);
        doc(literal(transparencyCategory, "FINANZHILFEN", 16, "FINANZHILFEN"),
                "Financial aid (§6 para. 3 no. 2 lit. p)",
                null, null);
        doc(literal(transparencyCategory, "GUTACHTEN", 17, "GUTACHTEN"),
                "Expert opinions and studies (§6 para. 3 no. 2 lit. q)",
                null, null);
        doc(literal(transparencyCategory, "SONSTIGE", 18, "SONSTIGE"),
                "Other information of public interest (§6 para. 3 no. 2 lit. r)",
                null, null);

        EEnum exclusionGround = doc(enm(p, "ExclusionGround"),
                "Exclusion grounds for publication according to §12 (public interests) and §13 "
                        + "(private interests) ThuerTG",
                "Ausschlussgrund", "Exclusion Ground");
        doc(literal(exclusionGround, "UNEDITED", 0, "UNEDITED"),
                "Unedited",
                null, null);
        doc(literal(exclusionGround, "NONE", 1, "NONE"),
                "No exclusion ground — unrestricted publication",
                null, null);
        doc(literal(exclusionGround, "PUBLIC_SECURITY", 2, "PUBLIC_SECURITY"),
                "Public security or order (§12 para. 1 no. 1 ThuerTG)",
                null, null);
        doc(literal(exclusionGround, "CONFIDENTIALITY_OBLIGATION", 3, "CONFIDENTIALITY_OBLIGATION"),
                "Statutory confidentiality obligation (§12 para. 1 no. 2 ThuerTG)",
                null, null);
        doc(literal(exclusionGround, "ONGOING_PROCEEDINGS", 4, "ONGOING_PROCEEDINGS"),
                "Ongoing court or administrative proceedings (§12 para. 1 no. 3 ThuerTG)",
                null, null);
        doc(literal(exclusionGround, "SUPERVISORY_TASKS", 5, "SUPERVISORY_TASKS"),
                "Supervisory and control tasks (§12 para. 1 no. 4 ThuerTG)",
                null, null);
        doc(literal(exclusionGround, "FISCAL_INTERESTS", 6, "FISCAL_INTERESTS"),
                "Fiscal interests (§12 para. 1 no. 5 ThuerTG)",
                null, null);
        doc(literal(exclusionGround, "PERSONAL_DATA", 7, "PERSONAL_DATA"),
                "Personal data (§13 para. 1 ThuerTG)",
                null, null);
        doc(literal(exclusionGround, "BUSINESS_SECRET", 8, "BUSINESS_SECRET"),
                "Trade and business secrets (§13 para. 2 ThuerTG)",
                null, null);
        doc(literal(exclusionGround, "DECISION_PREPARATION", 9, "DECISION_PREPARATION"),
                "Decision preparation and policy formulation (§12 para. 1 no. 6 ThuerTG)",
                null, null);

        EEnum dataFormatLevel = doc(enm(p, "DataFormatLevel"),
                "Data format level according to §3 para. 3 and §7 para. 5 ThuerTG: "
                        + "machine-readable, open standards",
                "Datenformat-Stufe", "Data Format Level");
        doc(literal(dataFormatLevel, "UNEDITED", 0, "UNEDITED"),
                "Unedited",
                null, null);
        doc(literal(dataFormatLevel, "MACHINE_READABLE_OPEN", 1, "MACHINE_READABLE_OPEN"),
                "Machine-readable in open format (e.g. CSV, JSON, XML)",
                null, null);
        doc(literal(dataFormatLevel, "MACHINE_READABLE_PROPRIETARY", 2, "MACHINE_READABLE_PROPRIETARY"),
                "Machine-readable in proprietary format (e.g. XLS, DOC)",
                null, null);
        doc(literal(dataFormatLevel, "DIGITAL_NOT_MACHINE_READABLE", 3, "DIGITAL_NOT_MACHINE_READABLE"),
                "Digital but not machine-readable (e.g. scanned PDF)",
                null, null);
        doc(literal(dataFormatLevel, "NOT_DIGITAL", 4, "NOT_DIGITAL"),
                "Not digital (only available in paper form)",
                null, null);

        EClass transparencyAssessment = doc(cls(p, "TransparencyAssessment", false),
                "Transparency assessment of a data domain according to Thuringian Transparency "
                        + "Act (ThuerTG). Extends core::Package; the name comes from core::ModelElement "
                        + "and a description is attached as businessinformation::Description. Its ratings "
                        + "are the typed dataClasses containment feature - keep them there. The inherited "
                        + "Namespace.ownedElement is only for auxiliary owned CWM objects (e.g. a "
                        + "businessinformation::Description).",
                "Transparenzbewertung", "Transparency Assessment");

        EClass transparencyClassRating = doc(cls(p, "TransparencyClassRating", false),
                "Transparency rating of a data class according to Thuringian Transparency Act "
                        + "(ThuerTG): links a CWM Classifier (table, class, view, ...) to its "
                        + "transparency-law metadata.",
                "Transparenz-Klassenbewertung", "Transparency Class Rating");

        EClass transparencyFieldRating = doc(cls(p, "TransparencyFieldRating", false),
                "Transparency rating of a data field according to Thuringian Transparency Act "
                        + "(ThuerTG): links a CWM StructuralFeature (column, attribute, field, association "
                        + "end, ...) to its transparency-law metadata.",
                "Transparenz-Feldbewertung", "Transparency Field Rating");

        // TransparencyAssessment
        transparencyAssessment.getESuperTypes().add(refs.corePackage);
        doc(attr(transparencyAssessment, "validFrom", refs.timeType, 1, 1),
                "Validity start date of the transparency assessment",
                "Gueltig ab", "Valid From");
        doc(attr(transparencyAssessment, "lastReview", refs.timeType, 0, 1),
                "Date of the last review",
                "Letzte Ueberpruefung", "Last Review");
        doc(oppositeRole(ref(transparencyAssessment, "contactResponsible", refs.responsibleParty, 1, 1),
                "transparencyResponsibleFor"),
                "Responsible contact person for this transparency assessment",
                "Verantwortlicher", "Responsible Contact");
        doc(cont(transparencyAssessment, "dataClasses", transparencyClassRating, 0, -1),
                "Assessed transparency data classes within this assessment",
                "Datenklassen", "Data Classes");

        // TransparencyClassRating
        transparencyClassRating.getESuperTypes().add(refs.modelElement);
        doc(oppositeRole(ref(transparencyClassRating, "dataClass", refs.classifier, 1, 1), "transparencyClassRating"),
                "Rated CWM Classifier",
                "Datenklasse", "Data Class");
        doc(attr(transparencyClassRating, "publicationMode", publicationMode, 0, 1),
                "Publication mode: proactive, on request or excluded",
                "Veroeffentlichungsart", "Publication Mode");
        doc(attr(transparencyClassRating, "transparencyCategory", transparencyCategory, 0, 1),
                "Information category according to §6 para. 3 no. 2 ThuerTG",
                "Transparenzkategorie", "Transparency Category");
        doc(attr(transparencyClassRating, "exclusionGround", exclusionGround, 0, 1),
                "Exclusion ground according to §12/§13 ThuerTG",
                "Ausschlussgrund", "Exclusion Ground");
        doc(attr(transparencyClassRating, "dataFormatLevel", dataFormatLevel, 0, 1),
                "Data format level according to §3 para. 3 and §7 para. 5 ThuerTG",
                "Datenformat-Stufe", "Data Format Level");
        doc(attr(transparencyClassRating, "legalReference", refs.stringType, 0, 1),
                "Specific legal reference (e.g. ThuerTG §6 para. 3 no. 2 lit. a)",
                "Paragraphen-Bezug", "Legal Reference");
        doc(attr(transparencyClassRating, "publicationNote", refs.stringType, 0, 1),
                "Free-text note regarding publication",
                "Veroeffentlichungshinweis", "Publication Note");
        doc(attr(transparencyClassRating, "lastReview", refs.timeType, 0, 1),
                "Date of the last review",
                "Letzte Ueberpruefung", "Last Review");
        doc(oppositeRole(ref(transparencyClassRating, "policy", refs.deidentificationPolicy, 0, 1),
                "transparencyRating"),
                "How the data of the class is de-identified before it is published: a policy of the model "
                        + "deidentification that governs the data class.",
                "Deidentifizierung", "De-identification");
        doc(cont(transparencyClassRating, "dataFields", transparencyFieldRating, 0, -1),
                "Transparency ratings of the data fields belonging to this data class",
                "Datenfeld-Bewertungen", "Field Ratings");

        // TransparencyFieldRating
        transparencyFieldRating.getESuperTypes().add(refs.modelElement);
        doc(oppositeRole(ref(transparencyFieldRating, "dataField", refs.structuralFeature, 1, 1),
                "transparencyFieldRating"),
                "Rated CWM StructuralFeature",
                "Datenfeld", "Data Field");
        doc(attr(transparencyFieldRating, "publicationMode", publicationMode, 0, 1),
                "Publication mode of the data field",
                "Veroeffentlichungsart", "Publication Mode");
        doc(attr(transparencyFieldRating, "exclusionGround", exclusionGround, 0, 1),
                "Exclusion ground of the data field according to §12/§13 ThuerTG",
                "Ausschlussgrund", "Exclusion Ground");

        return p;
    }

    /** The annotations of the root package: documentation, captions, GenModel settings, delegates. */
    private void root(EPackage p, String prefix, String doc, String de, String en) {
        doc(p, doc, de, en);
        EAnnotation genModel = p.getEAnnotation(GENMODEL);
        genModel.getDetails().put("modelName", prefix);
        genModel.getDetails().put("copyrightText", EPL_HEADER);
        genModel.getDetails().put("oSGiCompatible", "true");
        genModel.getDetails().put("basePackage", "org.eclipse.daanse.cwm.model.daanse.governance");
        genModel.getDetails().put("resource", "XMI");
        genModel.getDetails().put("prefix", prefix);
        genModel.getDetails().put("literalsInterface", "true");
        genModel.getDetails().put("loadInitialization", "false");
        delegates(p);
    }

    /** A subpackage of {@code parent} with its own namespace and delegates. */
    private EPackage subpackage(EPackage parent, String name, String nsUri, String nsPrefix) {
        EPackage sub = ef.createEPackage();
        sub.setName(name);
        sub.setNsURI(nsUri);
        sub.setNsPrefix(nsPrefix);
        delegates(sub);
        parent.getESubpackages().add(sub);
        return sub;
    }

    /** The OCL delegates and hidden opposites every package of the Daanse models declares. */
    private void delegates(EPackage p) {
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
    }

    /** Documentation and the German and English captions, each where given. */
    private <T extends EModelElement> T doc(T el, String doc, String de, String en) {
        EAnnotation a = el.getEAnnotation(GENMODEL);
        if (a == null) {
            a = ef.createEAnnotation();
            a.setSource(GENMODEL);
            el.getEAnnotations().add(a);
        }
        if (doc != null) {
            a.getDetails().put("documentation", doc);
        }
        if (de != null) {
            a.getDetails().put("caption.de", de);
        }
        if (en != null) {
            a.getDetails().put("caption.en", en);
        }
        return el;
    }

    /** Further details under {@code source}, given as key and value in turn. */
    private void annotate(EModelElement el, String source, String... keysAndValues) {
        EAnnotation a = ef.createEAnnotation();
        a.setSource(source);
        for (int i = 0; i < keysAndValues.length; i += 2) {
            a.getDetails().put(keysAndValues[i], keysAndValues[i + 1]);
        }
        el.getEAnnotations().add(a);
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
    private EReference oppositeRole(EReference r, String roleName) {
        EAnnotation a1 = ef.createEAnnotation();
        a1.setSource(EMOF);
        a1.getDetails().put("Property.oppositeRoleName", roleName);
        r.getEAnnotations().add(a1);
        EAnnotation a2 = ef.createEAnnotation();
        a2.setSource(EMOF + "#Property.oppositeRoleName");
        a2.getDetails().put("body", roleName);
        r.getEAnnotations().add(a2);
        return r;
    }

    private EClass cls(EPackage pkg, String name, boolean isAbstract) {
        EClass c = ef.createEClass();
        c.setName(name);
        c.setAbstract(isAbstract);
        pkg.getEClassifiers().add(c);
        return c;
    }

    private EEnum enm(EPackage pkg, String name) {
        EEnum e = ef.createEEnum();
        e.setName(name);
        pkg.getEClassifiers().add(e);
        return e;
    }

    private EEnumLiteral literal(EEnum e, String name, int value, String literal) {
        EEnumLiteral l = ef.createEEnumLiteral();
        l.setName(name);
        l.setValue(value);
        l.setLiteral(literal);
        e.getELiterals().add(l);
        return l;
    }

    private EAttribute attr(EClass owner, String name, EClassifier type, int lower, int upper) {
        EAttribute a = ef.createEAttribute();
        a.setName(name);
        a.setEType(type);
        a.setLowerBound(lower);
        a.setUpperBound(upper);
        owner.getEStructuralFeatures().add(a);
        return a;
    }

    private EReference ref(EClass owner, String name, EClassifier type, int lower, int upper) {
        EReference r = ef.createEReference();
        r.setName(name);
        r.setEType(type);
        r.setLowerBound(lower);
        r.setUpperBound(upper);
        if (upper != 1) {
            r.setOrdered(false);
        }
        owner.getEStructuralFeatures().add(r);
        return r;
    }

    private EReference cont(EClass owner, String name, EClassifier type, int lower, int upper) {
        EReference r = ref(owner, name, type, lower, upper);
        r.setContainment(true);
        r.setResolveProxies(false);
        return r;
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
                + "* Copyright (c) " + YEAR + " Contributors to the Eclipse Foundation.\n"
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
