/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.deidentification.build;

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
 * Programmatic builder for the {@code deidentification} metamodel: policies that de-identify the
 * rows of CWM classifiers feature by feature and name the privacy models the result meets,
 * pseudonymization domains with their key holder, the lifecycle stages that pseudonymize, anonymize
 * and destroy the linkage of data, and disclosure control for aggregates.
 *
 * <p>A policy and a disclosure control are {@code core::Constraint}s on the elements they govern;
 * the stages extend the stages of {@code lifecycle}. Construction and serialization follow the
 * builder of {@code lifecycle}; references into other models are written with the namespace URI of
 * their package.</p>
 */
public final class DeidentificationEcoreBuilder {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final String NS_URI =
            "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.governance.deidentification";

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
        final EClass constraint;
        final EClass element;
        final EClass modelElement;
        final EClass structuralFeature;
        final EClass classifier;
        final EClass responsibleParty;
        final EDataType stringType;
        final EDataType integerType;
        final EDataType floatType;
        final EClass destructiveStage;
        final EClass terminalStage;
        final EDataType period;

        Refs(EPackage core, EPackage businessinformation, EPackage lifecycle) {
            this.constraint = eClass(core, "Constraint");
            this.element = eClass(core, "Element");
            this.modelElement = eClass(core, "ModelElement");
            this.structuralFeature = eClass(core, "StructuralFeature");
            this.classifier = eClass(core, "Classifier");
            this.responsibleParty = eClass(businessinformation, "ResponsibleParty");
            this.stringType = eDataType(core, "String");
            this.integerType = eDataType(core, "Integer");
            this.floatType = eDataType(core, "Float");
            this.destructiveStage = eClass(lifecycle, "DestructiveStage");
            this.terminalStage = eClass(lifecycle, "TerminalStage");
            this.period = eDataType(lifecycle, "Period");
        }
    }

    public DeidentificationEcoreBuilder(Refs refs) {
        this.refs = refs;
    }

    /**
     * Builds the metamodel from the CWM Ecores and the {@code lifecycle} Ecore next to
     * {@code moduleDir} (module directory {@code model/daanse/governance/deidentification}) and
     * writes it to {@code outFile}.
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
        save(new DeidentificationEcoreBuilder(new Refs(core, businessinformation, lifecycle)).build(), rs,
                URI.createFileURI(outFile.getAbsolutePath()));
    }

    /** Creates the EPackage {@code deidentification}: 23 classes (2 abstract), 6 enumerations. */
    public EPackage build() {
        EPackage p = ef.createEPackage();
        p.setName("deidentification");
        p.setNsURI(NS_URI);
        p.setNsPrefix("deid");
        EAnnotation pa = annotate(p,
                "De-identification of the data of CWM classifiers. A de-identification policy is a "
                        + "core::Constraint on the classifiers whose rows it de-identifies: one treatment per "
                        + "feature, each with the role of the feature, the privacy models the result meets, and "
                        + "the outcome it claims — pseudonymous or anonymous — for an audience. A "
                        + "pseudonymization domain is where pseudonyms are consistent and who holds the means to "
                        + "reverse them. The stages Pseudonymize, Anonymize and DestroyLinkage put a policy or a "
                        + "domain into a retention schedule of the lifecycle model. A disclosure control "
                        + "protects the aggregates of cubes and measures.");
        pa.getDetails().put("modelName", "Deidentification");
        pa.getDetails().put("copyrightText", EPL_HEADER);
        pa.getDetails().put("oSGiCompatible", "true");
        pa.getDetails().put("basePackage", "org.eclipse.daanse.cwm.model.daanse.governance");
        pa.getDetails().put("resource", "XMI");
        pa.getDetails().put("prefix", "Deidentification");

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

        EEnum attributeRole = enm(p, "AttributeRole",
                "The part a feature plays in de-identification. identifying: it names a person on its own "
                        + "(a name, an account number). quasiIdentifying: combined with others it may single out "
                        + "a person (birth date, postcode, sex). sensitive: what must not be learned about a "
                        + "person (a diagnosis, an income). insensitive: none of these.",
                "identifying", "quasiIdentifying", "sensitive", "insensitive");
        EEnum pseudonymMethod = enm(p, "PseudonymMethod",
                "How a pseudonym is made: a counter, a random value (both kept in a mapping), a hash, a "
                        + "hash with a secret key, an encryption that the key holder can reverse.",
                "counter", "random", "hash", "keyedHash", "encryption");
        EEnum consistency = enm(p, "Consistency",
                "Within what the same value is treated alike: in every data set (global), within one data "
                        + "set (perDataset), within one row (perRow: the dates of a record shift alike, so that "
                        + "the intervals between them stay), anew for every occurrence (perOccurrence).",
                "global", "perDataset", "perRow", "perOccurrence");
        EEnum outcome = enm(p, "Outcome",
                "What a policy claims for its result. pseudonymous: the data can be linked back to a person "
                        + "with additional information kept separately — it is still personal data. anonymous: "
                        + "it cannot, by the means reasonably likely to be used.",
                "pseudonymous", "anonymous");
        EEnum diversityVariant = enm(p, "DiversityVariant",
                "What well-represented means for l-diversity: l distinct values, an entropy of at least "
                        + "log l, or recursive (c, l)-diversity.",
                "distinct", "entropy", "recursive");
        EEnum disclosureMethod = enm(p, "DisclosureMethod",
                "How a sensitive cell of an aggregate is protected: suppressed, perturbed by the cell-key "
                        + "method, or rounded.",
                "suppression", "cellKey", "rounding");

        EClass policy = cls(p, "DeidentificationPolicy", false,
                "How the rows of the governed classifiers are de-identified: one treatment per feature, the "
                        + "privacy models the result meets, and the outcome claimed — pseudonymous or anonymous. "
                        + "audience: for whom the outcome holds, since anonymity depends on what a recipient can "
                        + "know. suppressionLimit: the share of rows that may be left out to meet the models, a "
                        + "decimal number between 0 and 1. The body holds the policy as text.");
        policy.getESuperTypes().add(refs.constraint);
        attr(policy, "outcome", outcome, 1, 1, "The outcome the policy claims; always written.")
                .setUnsettable(true);
        EReference audience = ref(policy, "audience", refs.responsibleParty, 0, 1,
                "For whom the outcome holds: the recipient of the de-identified data.");
        oppositeRole(audience, "deidAudienceOf");
        attr(policy, "suppressionLimit", refs.floatType, 0, 1,
                "The share of rows that may be suppressed, between 0 and 1.");
        EClass treatment = cls(p, "Treatment", true,
                "What happens to one feature of the rows: the feature, the role it plays, and in the "
                        + "subclass the technique.");
        EClass privacyModel = cls(p, "PrivacyModel", true, "A formal property the de-identified rows meet.");
        cont(policy, "treatment", treatment, 0, -1, "One treatment per feature of the governed classifiers.");
        cont(policy, "model", privacyModel, 0, -1, "The privacy models the result meets.");
        constrain(policy, "ConstrainsClassifiers", "self.constrainedElement->notEmpty() and "
                + "self.constrainedElement->forAll(e | e.oclIsKindOf(core::Classifier))");
        constrain(policy, "OneTreatmentPerFeature", "self.treatment->isUnique(t | t.feature)");

        treatment.getESuperTypes().add(refs.element);
        privacyModel.getESuperTypes().add(refs.element);
        EReference treated = ref(treatment, "feature", refs.structuralFeature, 1, 1, "The feature treated.");
        oppositeRole(treated, "deidTreatment");
        attr(treatment, "role", attributeRole, 1, 1, "The role of the feature; always written.").setUnsettable(true);

        technique(p, treatment, "Unchanged",
                "The feature is kept as it is: the explicit choice for a feature that needs no treatment, or "
                        + "whose value the result must keep, as the sensitive feature of l-diversity.");
        technique(p, treatment, "Removal", "The feature is removed from every row.");
        EClass masking = technique(p, treatment, "Masking",
                "Characters of the value are replaced by a mask; pattern shows which: X is masked, # kept "
                        + "(XXXX-XXXX-XXXX-#### keeps the last four digits).");
        attr(masking, "pattern", refs.stringType, 1, 1, "The mask: X masks a character, # keeps it.");
        EClass truncation = technique(p, treatment, "Truncation",
                "Only the first keep characters of the value remain: a postcode of five digits cut to three.");
        attr(truncation, "keep", refs.integerType, 1, 1, "How many characters remain.");
        EClass rounding = technique(p, treatment, "Rounding",
                "The value is rounded to a multiple of to: an income to 1000, an age to 5.");
        attr(rounding, "to", refs.floatType, 1, 1, "The multiple the value is rounded to.");
        EClass coding = technique(p, treatment, "TopBottomCoding",
                "Values below low become low, values above high become high: the rare extremes that single "
                        + "out a person.");
        attr(coding, "low", refs.floatType, 0, 1, "The lowest value kept; lower ones become it.");
        attr(coding, "high", refs.floatType, 0, 1, "The highest value kept; higher ones become it.");
        EClass noise = technique(p, treatment, "NoiseAddition",
                "Random noise is added to the value, drawn from distribution (laplace, gaussian, uniform) "
                        + "with scale.");
        attr(noise, "scale", refs.floatType, 1, 1, "The scale of the noise.");
        attr(noise, "distribution", refs.stringType, 1, 1, "The distribution the noise is drawn from.");
        EClass dateShift = technique(p, treatment, "DateShift",
                "A date is moved by a random period of at most maximum, the same shift within consistency: "
                        + "perRow keeps the intervals between the dates of a record, a shift per data set reveals "
                        + "every date once one is known, a shift per occurrence loses the intervals.");
        attr(dateShift, "maximum", refs.period, 1, 1, "The largest shift, a Period literal.");
        attr(dateShift, "consistency", consistency, 1, 1, "Within what the shift is the same.");
        EClass generalization = technique(p, treatment, "Generalization",
                "The value is replaced by a coarser one: a number by an interval of width, a date or a code "
                        + "by its granularity (year, month, the first digits). The hierarchy of the coarser "
                        + "values is not modelled.");
        attr(generalization, "width", refs.floatType, 0, 1, "The width of the intervals a number falls into.");
        attr(generalization, "granularity", refs.stringType, 0, 1,
                "The coarser unit of a date or a code: year, month, the first three digits.");
        constrain(generalization, "WidthOrGranularity",
                "not self.width.oclIsUndefined() or not self.granularity.oclIsUndefined()");
        EClass pseudonymization = technique(p, treatment, "Pseudonymization",
                "The value is replaced by a pseudonym made by method, the same value giving the same "
                        + "pseudonym within consistency, in the domain whose key or mapping links pseudonym and "
                        + "value.");
        attr(pseudonymization, "method", pseudonymMethod, 1, 1, "How the pseudonym is made.");
        attr(pseudonymization, "consistency", consistency, 1, 1, "Within what a value keeps its pseudonym.");

        EClass domain = cls(p, "PseudonymizationDomain", false,
                "Where pseudonyms are consistent and who can reverse them: the key holder — a trusted third "
                        + "party, a trust centre, the controller — and the classifier that maps pseudonyms to "
                        + "values, where there is one. A retention schedule on the domain that ends in "
                        + "DestroyLinkage says when the link is destroyed.");
        domain.getESuperTypes().add(refs.modelElement);
        EReference keyHolder = ref(domain, "keyHolder", refs.responsibleParty, 1, 1,
                "Who holds the key or the mapping.");
        oppositeRole(keyHolder, "deidKeyHolderOf");
        EReference mapping = ref(domain, "mapping", refs.classifier, 0, 1,
                "The classifier whose rows map pseudonyms to values.");
        oppositeRole(mapping, "deidDomainOfMapping");
        EReference inDomain = ref(pseudonymization, "domain", domain, 0, 1, "The domain of the pseudonyms.");
        oppositeRole(inDomain, "deidPseudonymization");

        EClass kAnonymity = model(p, privacyModel, "KAnonymity",
                "Every combination of quasi-identifying values is shared by at least k rows.");
        attr(kAnonymity, "k", refs.integerType, 1, 1, "The least number of rows alike.");
        EClass lDiversity = model(p, privacyModel, "LDiversity",
                "Every group of rows alike in the quasi-identifiers holds at least l well-represented values "
                        + "of the sensitive feature, as variant defines.");
        attr(lDiversity, "l", refs.integerType, 1, 1, "The least number of well-represented values.");
        attr(lDiversity, "variant", diversityVariant, 1, 1, "What well-represented means.");
        EReference lSensitive = ref(lDiversity, "sensitive", refs.structuralFeature, 1, 1,
                "The sensitive feature.");
        oppositeRole(lSensitive, "deidLDiversity");
        EClass tCloseness = model(p, privacyModel, "TCloseness",
                "In every group of rows alike in the quasi-identifiers, the distribution of the sensitive "
                        + "feature is within t of its distribution over all rows.");
        attr(tCloseness, "t", refs.floatType, 1, 1, "The largest distance of the distributions, between 0 and 1.");
        EReference tSensitive = ref(tCloseness, "sensitive", refs.structuralFeature, 1, 1,
                "The sensitive feature.");
        oppositeRole(tSensitive, "deidTCloseness");
        EClass differentialPrivacy = model(p, privacyModel, "DifferentialPrivacy",
                "The result changes little with or without any one person: the privacy budget epsilon, and "
                        + "delta, the probability that the bound fails.");
        attr(differentialPrivacy, "epsilon", refs.floatType, 1, 1, "The privacy budget.");
        attr(differentialPrivacy, "delta", refs.floatType, 0, 1, "The probability that the bound fails.");

        EClass pseudonymize = cls(p, "Pseudonymize", false,
                "A stage that pseudonymizes the data after its period, by the policy. The data stays "
                        + "personal: the periods of the schedule go on, and a hold suspends the stage.");
        pseudonymize.getESuperTypes().add(refs.destructiveStage);
        EReference pPolicy = ref(pseudonymize, "policy", policy, 1, 1, "The policy the stage applies.");
        oppositeRole(pPolicy, "deidPseudonymizeStage");
        EClass anonymize = cls(p, "Anonymize", false,
                "A terminal stage that makes the data anonymous after its period, by the policy, whose "
                        + "outcome is anonymous. It fulfils a deletion obligation that lets anonymization "
                        + "satisfy it.");
        anonymize.getESuperTypes().add(refs.terminalStage);
        EReference aPolicy = ref(anonymize, "policy", policy, 1, 1, "The policy the stage applies.");
        oppositeRole(aPolicy, "deidAnonymizeStage");
        constrain(anonymize, "PolicyIsAnonymous",
                "self.policy.oclIsUndefined() or self.policy.outcome = Outcome::anonymous");
        EClass destroyLinkage = cls(p, "DestroyLinkage", false,
                "A terminal stage that destroys the link of a pseudonymization domain after its period — its "
                        + "key or its mapping: the pseudonyms of the domain can no longer be reversed.");
        destroyLinkage.getESuperTypes().add(refs.terminalStage);
        EReference dDomain = ref(destroyLinkage, "domain", domain, 1, 1, "The domain whose link is destroyed.");
        oppositeRole(dDomain, "deidDestroyLinkage");

        EClass disclosure = cls(p, "DisclosureControl", false,
                "Protection of the aggregates of the governed cubes or measures against disclosing a person "
                        + "or a business: a cell of fewer than minimumCellCount contributors, or one that a few "
                        + "contributors dominate, is protected by method. The body holds the control as text.");
        disclosure.getESuperTypes().add(refs.constraint);
        EAttribute minimumCellCount = attr(disclosure, "minimumCellCount", refs.integerType, 0, 1,
                "The least number of contributors of a cell that is shown as it is.");
        minimumCellCount.setUnsettable(true);
        EClass dominance = cls(p, "DominanceRule", false,
                "A cell is sensitive when its n largest contributors make up more than k percent of its "
                        + "total: the (n, k) rule.");
        dominance.getESuperTypes().add(refs.element);
        attr(dominance, "n", refs.integerType, 1, 1, "How many of the largest contributors count.");
        attr(dominance, "k", refs.floatType, 1, 1, "The share in percent they may make up.");
        cont(disclosure, "dominance", dominance, 0, 1, "The dominance rule, where there is one.");
        attr(disclosure, "method", disclosureMethod, 1, 1, "How a sensitive cell is protected.");
        constrain(disclosure, "SaysSomething", "self.minimumCellCount > 0 or not self.dominance.oclIsUndefined()");

        return p;
    }

    private EClass technique(EPackage p, EClass treatment, String name, String doc) {
        EClass c = cls(p, name, false, doc);
        c.getESuperTypes().add(treatment);
        return c;
    }

    private EClass model(EPackage p, EClass privacyModel, String name, String doc) {
        EClass c = cls(p, name, false, doc);
        c.getESuperTypes().add(privacyModel);
        return c;
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
        if (upper != 1) {
            r.setOrdered(false);
        }
        r.setContainment(true);
        r.setResolveProxies(false);
        owner.getEStructuralFeatures().add(r);
        annotate(r, doc);
        return r;
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
