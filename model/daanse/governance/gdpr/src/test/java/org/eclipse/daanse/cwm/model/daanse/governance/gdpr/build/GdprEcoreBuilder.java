/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.gdpr.build;

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
 * Programmatic builder for the {@code gdpr} metamodel: the records of processing activities and the
 * data protection impact assessments of the GDPR, in the subpackages {@code processing} and
 * {@code assessment}. A data erasure is a deletion obligation of the model {@code obligations}, a
 * transmission names a policy of the model {@code deidentification}, and the legal sources of a
 * processing are {@code obligations::LegalSource} documents that name it.
 *
 * <p>The German and English captions of the elements are GenModel details ({@code caption.de},
 * {@code caption.en}). Construction and serialization follow the builder of {@code lifecycle};
 * references into other models are written with the namespace URI of their package.</p>
 */
public final class GdprEcoreBuilder {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final String NS_URI =
            "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.gdpr";
    private static final String YEAR = "2024";

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
        final EDataType integerType;
        final EDataType floatType;
        final EDataType booleanType;
        final EDataType timeType;
        final EClass corePackage;
        final EClass modelElement;
        final EClass classifier;
        final EClass structuralFeature;
        final EClass responsibleParty;
        final EClass softwareSystem;
        final EClass deployedComponent;
        final EClass changeRequest;
        final EClass deletionObligation;
        final EClass deidentificationPolicy;

        Refs(EPackage core, EPackage businessinformation, EPackage softwaredeployment, EPackage warehouseoperation,
                EPackage obligations, EPackage deidentification) {
            this.stringType = eDataType(core, "String");
            this.integerType = eDataType(core, "Integer");
            this.floatType = eDataType(core, "Float");
            this.booleanType = eDataType(core, "Boolean");
            this.timeType = eDataType(core, "Time");
            this.corePackage = eClass(core, "Package");
            this.modelElement = eClass(core, "ModelElement");
            this.classifier = eClass(core, "Classifier");
            this.structuralFeature = eClass(core, "StructuralFeature");
            this.responsibleParty = eClass(businessinformation, "ResponsibleParty");
            this.softwareSystem = eClass(softwaredeployment, "SoftwareSystem");
            this.deployedComponent = eClass(softwaredeployment, "DeployedComponent");
            this.changeRequest = eClass(warehouseoperation, "ChangeRequest");
            this.deletionObligation = eClass(obligations, "DeletionObligation");
            this.deidentificationPolicy = eClass(deidentification, "DeidentificationPolicy");
        }
    }

    public GdprEcoreBuilder(Refs refs) {
        this.refs = refs;
    }

    /**
     * Builds the metamodel from the Ecores of the models it refers to, next to {@code moduleDir}
     * (module directory {@code model/daanse/governance/gdpr}), and writes it to {@code outFile}.
     */
    public static void generate(File moduleDir, File outFile) throws IOException {
        File cwmDir = new File(moduleDir, "../../../cwm").getCanonicalFile();
        File governanceDir = new File(moduleDir, "..").getCanonicalFile();
        ResourceSet rs = newResourceSet();
        EPackage core = loadPackage(rs, ecoreUri(cwmDir, "objectmodel.core/model/core.ecore"));
        loadPackage(rs, ecoreUri(cwmDir, "objectmodel.behavioral/model/behavioral.ecore"));
        EPackage businessinformation = loadPackage(rs,
                ecoreUri(cwmDir, "foundation.businessinformation/model/businessinformation.ecore"));
        EPackage softwaredeployment = loadPackage(rs,
                ecoreUri(cwmDir, "foundation.softwaredeployment/model/softwaredeployment.ecore"));
        EPackage warehouseoperation = loadPackage(rs,
                ecoreUri(cwmDir, "management.warehouseoperation/model/warehouseoperation.ecore"));
        loadPackage(rs, ecoreUri(governanceDir, "lifecycle/model/lifecycle.ecore"));
        EPackage obligations = loadPackage(rs, ecoreUri(governanceDir, "obligations/model/obligations.ecore"));
        EPackage deidentification = loadPackage(rs,
                ecoreUri(governanceDir, "deidentification/model/deidentification.ecore"));
        outFile.getParentFile().mkdirs();
        save(new GdprEcoreBuilder(new Refs(core, businessinformation, softwaredeployment, warehouseoperation,
                obligations,
                deidentification)).build(), rs, URI.createFileURI(outFile.getAbsolutePath()));
    }

    /** Creates the EPackage {@code gdpr}. */
    public EPackage build() {
        EPackage p = ef.createEPackage();
        p.setName("gdpr");
        p.setNsURI(NS_URI);
        p.setNsPrefix("gdpr");
        root(p, "Gdpr", "Data protection after the GDPR: the records of processing activities (Art. 30) with "
                        + "their processings, transmissions and erasures in the subpackage processing; the data "
                        + "protection impact assessments (Art. 35) and the privacy ratings of data classes and "
                        + "fields in the subpackage assessment. A data erasure is a deletion obligation of the "
                        + "model obligations, a transmission names the de-identification policy of the model "
                        + "deidentification; the legal sources of a processing are obligations::LegalSource "
                        + "documents that name it.",
                "Datenschutz DSGVO", "Data Protection GDPR");
        // the root holds no classifier, only the two subpackages: no package class, so no resource of its own
        p.getEAnnotation(GENMODEL).getDetails().removeKey("resource");

        EPackage processing = doc(subpackage(p, "processing", NS_URI + "/processing", "gdprprocessing"),
                "Records of processing activities and data processings according to Art. 30 GDPR.",
                "Verarbeitung", "Processing");
        EPackage assessment = doc(subpackage(p, "assessment", NS_URI + "/assessment", "gdprassessment"),
                "Data Protection Impact Assessment (DPIA) according to Art. 35 GDPR including "
                        + "threshold analysis, risks and damage categories.",
                "Folgenabschaetzung", "Impact Assessment");

        EClass dataProcessingRecord = doc(cls(processing, "DataProcessingRecord", false),
                "Record of processing activities per Art. 30 GDPR. Extends core::Package: the "
                        + "data processings live in the inherited Namespace.ownedElement (a "
                        + "DataProcessingRecord owns its DataProcessings the way a relational Schema owns "
                        + "its Tables). Good owned objects: DataProcessing instances - the "
                        + "OnlyProcessingsInRecord / RecordHoldsProcessings constraints require at least "
                        + "one and permit nothing else. Auxiliary businessinformation::Description "
                        + "instances describing the record may also be owned here.",
                "Verarbeitungsverzeichnis", "Processing Record");

        EClass dataProcessing = doc(cls(processing, "DataProcessing", false),
                "Any operation or set of operations which is performed on personal data or on "
                        + "sets of personal data, whether or not by automated means, such as collection, "
                        + "recording, organisation, structuring, storage, adaptation or alteration, "
                        + "retrieval, consultation, use, disclosure by transmission, dissemination or "
                        + "otherwise making available, alignment or combination, restriction, erasure or "
                        + "destruction (according to Art. 4 No. 2 GDPR). Extends core::Package. Its "
                        + "structural parts (dataProcessingLawfulness, dataTransmissions, dataErasures) "
                        + "are typed containment features, not free ownedElement members - keep them there "
                        + "so their per-type cardinality and typed access stay enforced. The inherited "
                        + "Namespace.ownedElement is only for auxiliary owned CWM objects (e.g. a "
                        + "businessinformation::Description describing this processing).",
                "Datenverarbeitung", "Data Processing");

        EClass dataProcessingLawfulness = doc(cls(processing, "DataProcessingLawfulness", false),
                null,
                "Erfassungsberechtigung", "Processing Lawfulness");

        EClass dataTransmission = doc(cls(processing, "DataTransmission", false),
                null,
                "Datenweitergabe", "Data Transmission");

        EClass dataErasure = doc(cls(processing, "DataErasure", false),
                "The erasure of personal data a data processing promises (Art. 17 and Art. 30 (1) (f) "
                        + "GDPR): a deletion obligation of the model obligations. It governs the data classes "
                        + "and fields to erase in constrainedElement, counted from its start, within its "
                        + "maximum; its legal sources are obligations::LegalSource documents that name it. It "
                        + "is contained in its data processing.",
                "Datenlöschung", "Data Erasure");

        EEnum dataProcessingType = doc(enm(processing, "DataProcessingType"),
                null,
                "Datenverarbeitungs-Art", "Data Processing Type");
        doc(literal(dataProcessingType, "UNEDITED", 0, "UNEDITED"),
                null,
                "UNBEARBEITET", "UNEDITED");
        doc(literal(dataProcessingType, "COLLECTION", 1, "COLLECTION"),
                null,
                "Erheben", "Collection");
        doc(literal(dataProcessingType, "RECORDING", 2, "RECORDING"),
                null,
                "Erfassen", "Recording");
        doc(literal(dataProcessingType, "ORGANISATION", 3, "ORGANISATION"),
                null,
                "Organisation", "Organisation");
        doc(literal(dataProcessingType, "STRUCTURING", 4, "STRUCTURING"),
                null,
                "Ordnen", "Structuring");
        doc(literal(dataProcessingType, "STORAGE", 5, "STORAGE"),
                null,
                "Speicherung", "Storage");
        doc(literal(dataProcessingType, "ADAPTATION", 6, "ADAPTATION"),
                null,
                "Anpassung", "Adaptation");
        doc(literal(dataProcessingType, "ALTERATION", 7, "ALTERATION"),
                null,
                "Veränderung", "Alteration");
        doc(literal(dataProcessingType, "RETRIEVAL", 8, "RETRIEVAL"),
                null,
                "Auslesen", "Retrieval");
        doc(literal(dataProcessingType, "CONSULTATION", 9, "CONSULTATION"),
                null,
                "Abfragen", "Consultation");
        doc(literal(dataProcessingType, "USE", 10, "USE"),
                null,
                "Verwendung", "Use");
        doc(literal(dataProcessingType, "TRANSMISSION", 11, "TRANSMISSION"),
                null,
                "Übermittlung", "Transmission");
        doc(literal(dataProcessingType, "DISSEMINATION", 12, "DISSEMINATION"),
                null,
                "Verbreitung", "Dissemination");
        doc(literal(dataProcessingType, "MAKING_AVAILABLE", 13, "MAKING_AVAILABLE"),
                null,
                "Bereitstellung", "Making Available");
        doc(literal(dataProcessingType, "ALIGNMENT", 14, "ALIGNMENT"),
                null,
                "Abgleich", "Alignment");
        doc(literal(dataProcessingType, "COMBINATION", 15, "COMBINATION"),
                null,
                "Verknüpfung", "Combination");
        doc(literal(dataProcessingType, "RESTRICTION", 16, "RESTRICTION"),
                null,
                "Einschränkung", "Restriction");
        doc(literal(dataProcessingType, "ERASURE", 17, "ERASURE"),
                null,
                "Löschung", "Erasure");
        doc(literal(dataProcessingType, "DESTRUCTION", 18, "DESTRUCTION"),
                null,
                "Vernichtung", "Destruction");

        EEnum dataProcessingLawfulnessType = doc(enm(processing, "DataProcessingLawfulnessType"),
                null,
                "Erfassungsberechtigungs-Art", "Processing Lawfulness Type");
        doc(literal(dataProcessingLawfulnessType, "UNEDITED", 0, "UNEDITED"),
                null,
                "UNBEARBEITET", "UNEDITED");
        doc(literal(dataProcessingLawfulnessType, "CONSENT", 1, "CONSENT"),
                "The data subject has given written consent to the collection, processing and/or "
                        + "use of personal data (Art. 6(1)(a) GDPR in conjunction with Art. 7 GDPR and "
                        + "Recital 32).",
                "EINWILLIGUNG", "CONSENT");
        doc(literal(dataProcessingLawfulnessType, "CONTRACTUAL_REQUIREMENT", 2, "CONTRACTUAL_REQUIREMENT"),
                "Processing is necessary for the performance of a contract to which the data "
                        + "subject is party or in order to take steps at the request of the data subject "
                        + "prior to entering into a contract (Art. 6(1)(b) GDPR).",
                "VERTRAGLICHES_ERFORDERNIS", "CONTRACTUAL_REQUIREMENT");
        doc(literal(dataProcessingLawfulnessType, "LEGAL_OBLIGATION", 3, "LEGAL_OBLIGATION"),
                "Processing is necessary for compliance with a legal obligation to which the "
                        + "controller is subject (Art. 6(1)(c) GDPR).",
                "RECHTLICHE_VERPFLICHTUNG", "LEGAL_OBLIGATION");
        doc(literal(dataProcessingLawfulnessType, "VITAL_INTERESTS", 4, "VITAL_INTERESTS"),
                "Processing is necessary in order to protect the vital interests of the data "
                        + "subject or of another natural person, i.e. all essential interests in the area "
                        + "of health protection; e.g. processing for humanitarian purposes including the "
                        + "monitoring of epidemics and their spread or in humanitarian emergencies, in "
                        + "particular in situations of natural and man-made disasters (Art. 6(1)(d) GDPR "
                        + "in conjunction with Recitals 46 and 112).",
                "LEBENSWICHTIGE_INTERESSEN", "VITAL_INTERESTS");
        doc(literal(dataProcessingLawfulnessType, "PUBLIC_INTERESTS_OR_AUTHORITY", 5, "PUBLIC_INTERESTS_OR_AUTHORITY"),
                "Processing is necessary for the performance of a task carried out in the public "
                        + "interest or in the exercise of official authority, for example for the "
                        + "international exchange of data between competition, tax or customs authorities, "
                        + "between financial supervisory authorities or between services responsible for "
                        + "social security or public health matters, such as in the case of contact "
                        + "tracing for communicable diseases or to reduce and/or eliminate doping in sport "
                        + "(Art. 6(1)(e) GDPR in conjunction with Recital 112).",
                "OEFFENTLICHE_INTERESSEN_ODER_GEWALT", "PUBLIC_INTERESTS_OR_AUTHORITY");
        doc(literal(dataProcessingLawfulnessType, "LEGITIMATE_INTERESTS_AFTER_WEIGHING", 6,
                "LEGITIMATE_INTERESTS_AFTER_WEIGHING"),
                "The legitimate interest requires a bilateral balancing of the interests of both "
                        + "the controller and the data subjects to assess lawfulness. If there are less "
                        + "intrusive means, the legitimate interest usually (always?) appears as not "
                        + "legitimate and therefore as not given (Art. 6(1)(f) GDPR in conjunction with "
                        + "Recital 47).",
                "BERECHTIGTE_INTERESSEN_NACH_ABWAEGUNG", "LEGITIMATE_INTERESTS_AFTER_WEIGHING");

        EEnum dataProcessingLawfulnessTypeSpecialCategories = doc(enm(processing,
                "DataProcessingLawfulnessTypeSpecialCategories"),
                null,
                "Erfassungsberechtigungs-Art für besondere Kategorien", "Lawfulness Type for Special Categories");
        doc(literal(dataProcessingLawfulnessTypeSpecialCategories, "UNEDITED", 0, "UNEDITED"),
                null,
                "UNBEARBEITET", "UNEDITED");
        doc(literal(dataProcessingLawfulnessTypeSpecialCategories, "CONSENT", 1, "CONSENT"),
                "The data subject has given explicit consent to the processing of those personal "
                        + "data for one or more specified purposes (Art. 9(2)(a) GDPR).",
                "EINWILLIGUNG", "CONSENT");
        doc(literal(dataProcessingLawfulnessTypeSpecialCategories, "PUBLISHED_DATA", 2, "PUBLISHED_DATA"),
                "Processing relates to personal data which are manifestly made public by the "
                        + "data subject (Art. 9(2)(e) GDPR).",
                "VEROEFFENTLICHTE_DATEN", "PUBLISHED_DATA");
        doc(literal(dataProcessingLawfulnessTypeSpecialCategories, "EMPLOYMENT_SOCIAL_SECURITY", 3,
                "EMPLOYMENT_SOCIAL_SECURITY"),
                "Processing is necessary for the purposes of carrying out the obligations and "
                        + "exercising specific rights of the controller or of the data subject in the "
                        + "field of employment and social security and social protection law in so far as "
                        + "it is authorised by Union or Member State law or a collective agreement "
                        + "pursuant to Member State law providing for appropriate safeguards for the "
                        + "fundamental rights and the interests of the data subject (Art. 9(2)(b) GDPR).",
                "ARBEITSRECHT_SOZIALE_SICHERHEIT", "EMPLOYMENT_SOCIAL_SECURITY");
        doc(literal(dataProcessingLawfulnessTypeSpecialCategories, "PUBLIC_EMPLOYMENT_CIVIL_SERVICE_LAW", 4,
                "PUBLIC_EMPLOYMENT_CIVIL_SERVICE_LAW"),
                "Processing is necessary to take account of the rights and obligations of public "
                        + "bodies in the field of civil service and employment law (Section 16(2)(4) "
                        + "ThuerDSG).",
                "OEFFENTLICHES_DIENST_ARBEITSRECHT", "PUBLIC_EMPLOYMENT_CIVIL_SERVICE_LAW");
        doc(literal(dataProcessingLawfulnessTypeSpecialCategories, "VITAL_INTERESTS", 5, "VITAL_INTERESTS"),
                "Processing is necessary to protect the vital interests of the data subject or "
                        + "of another natural person where the data subject is physically or legally "
                        + "incapable of giving consent (Art. 9(2)(c) GDPR).",
                "LEBENSWICHTIGE_INTERESSEN", "VITAL_INTERESTS");
        doc(literal(dataProcessingLawfulnessTypeSpecialCategories, "PUBLIC_HEALTH", 6, "PUBLIC_HEALTH"),
                "Processing is necessary for reasons of public interest in the area of public "
                        + "health, such as protecting against serious cross-border threats to health or "
                        + "ensuring high standards of quality and safety of health care and of medicinal "
                        + "products or medical devices, on the basis of Union or Member State law which "
                        + "provides for suitable and specific measures to safeguard the rights and "
                        + "freedoms of the data subject, in particular professional secrecy (Art. 9(2)(i) "
                        + "GDPR).",
                "OEFFENTLICHE_GESUNDHEIT", "PUBLIC_HEALTH");
        doc(literal(dataProcessingLawfulnessTypeSpecialCategories, "HEALTH_CARE", 7, "HEALTH_CARE"),
                "Processing is necessary for the purposes of preventive or occupational "
                        + "medicine, for the assessment of the working capacity of the employee, medical "
                        + "diagnosis, the provision of health or social care or treatment or the "
                        + "management of health or social care systems and services on the basis of Union "
                        + "or Member State law or pursuant to contract with a health professional and "
                        + "subject to the conditions and safeguards referred to in paragraph 3 (Art. "
                        + "9(2)(h) GDPR). Processing is necessary for the purposes of preventive medicine, "
                        + "medical diagnosis, health care or treatment or the management of health care "
                        + "services, and the processing of such data is carried out by medical personnel "
                        + "subject to professional secrecy or by other persons subject to a corresponding "
                        + "obligation of confidentiality (Section 16(2)(5) ThuerDSG).",
                "GESUNDHEITSVORSORGE", "HEALTH_CARE");
        doc(literal(dataProcessingLawfulnessTypeSpecialCategories, "SIGNIFICANT_PUBLIC_INTEREST", 8,
                "SIGNIFICANT_PUBLIC_INTEREST"),
                "Processing is necessary for reasons of substantial public interest, on the "
                        + "basis of Union or Member State law which shall be proportionate to the aim "
                        + "pursued, respect the essence of the right to data protection and provide for "
                        + "suitable and specific measures to safeguard the fundamental rights and the "
                        + "interests of the data subject (Art. 9(2)(g) GDPR). Processing is necessary for "
                        + "reasons of substantial public interest, in particular where there are factual "
                        + "indications that the functionality of the administration is substantially "
                        + "endangered (Section 16(2)(2) ThuerDSG).",
                "ERHEBLICHES_OEFFENTLICHES_INTERESSE", "SIGNIFICANT_PUBLIC_INTEREST");
        doc(literal(dataProcessingLawfulnessTypeSpecialCategories, "RESEARCH_STATISTICS_ARCHIVING", 9,
                "RESEARCH_STATISTICS_ARCHIVING"),
                "Processing is necessary for archiving purposes in the public interest, "
                        + "scientific or historical research purposes or statistical purposes in "
                        + "accordance with Article 89(1) based on Union or Member State law which shall be "
                        + "proportionate to the aim pursued, respect the essence of the right to data "
                        + "protection and provide for suitable and specific measures to safeguard the "
                        + "fundamental rights and the interests of the data subject (Art. 9(2)(j) GDPR). "
                        + "Processing is necessary for conducting scientific or historical research, the "
                        + "scientific interest in conducting the research project substantially outweighs "
                        + "the interest of the data subject in the exclusion of processing, and the "
                        + "purpose of the research cannot be achieved by other means or only with "
                        + "disproportionate effort (Section 16(2)(3) ThuerDSG).",
                "FORSCHUNG_STATISTIK_ARCHIVIERUNG", "RESEARCH_STATISTICS_ARCHIVING");
        doc(literal(dataProcessingLawfulnessTypeSpecialCategories, "DANGER_PREVENTION_PUBLIC_SAFETY", 10,
                "DANGER_PREVENTION_PUBLIC_SAFETY"),
                "Processing is necessary to prevent significant disadvantages to the common good "
                        + "or dangers to public safety (Section 16(2)(1) ThuerDSG).",
                "GEFAHRENABWEHR_OEFFENTLICHE_SICHERHEIT", "DANGER_PREVENTION_PUBLIC_SAFETY");
        doc(literal(dataProcessingLawfulnessTypeSpecialCategories, "NON_PROFIT_ORGANISATION", 11,
                "NON_PROFIT_ORGANISATION"),
                "Processing is carried out in the course of its legitimate activities with "
                        + "appropriate safeguards by a foundation, association or any other not-for-profit "
                        + "body with a political, philosophical, religious or trade union aim and on "
                        + "condition that the processing relates solely to the members or to former "
                        + "members of the body or to persons who have regular contact with it in "
                        + "connection with its purposes and that the personal data are not disclosed "
                        + "outside that body without the consent of the data subjects (Art. 9(2)(d) GDPR).",
                "GEMEINNUETZIGE_ORGANISATION", "NON_PROFIT_ORGANISATION");
        doc(literal(dataProcessingLawfulnessTypeSpecialCategories, "JUDICIAL_PROCEEDINGS", 12, "JUDICIAL_PROCEEDINGS"),
                "Processing is necessary for the establishment, exercise or defence of legal "
                        + "claims or whenever courts are acting in their judicial capacity (Art. 9(2)(f) "
                        + "GDPR).",
                "JUSTIZIELLE_VERFAHREN", "JUDICIAL_PROCEEDINGS");

        EEnum personalReferenceType = doc(enm(processing, "PersonalReferenceType"),
                null,
                "Personenbezug-Art", "Personal Reference Type");
        doc(literal(personalReferenceType, "UNEDITED", 0, "UNEDITED"),
                null,
                "UNBEARBEITET", "UNEDITED");
        doc(literal(personalReferenceType, "DIRECT", 1, "DIRECT"),
                "Through the assignment of data, a direct reference to the person can be "
                        + "established (Art. 4 No. 1 GDPR).",
                "DIREKT", "DIRECT");
        doc(literal(personalReferenceType, "INDIRECT", 2, "INDIRECT"),
                "Identification of a person is only possible with the help of additional "
                        + "knowledge, as the reference alone is not sufficient for unambiguous "
                        + "identification (Art. 4 No. 1 GDPR).",
                "INDIREKT", "INDIRECT");

        EClass dPIARequirementAnalysis = doc(cls(assessment, "DPIARequirementAnalysis", false),
                null,
                "Schwellenwert-Analyse", "Threshold Analysis");

        EClass riskyProcessing = doc(cls(assessment, "RiskyProcessing", false),
                null,
                "Riskante Verarbeitung", "Risky Processing");

        EClass dataProtectionImpactAssessment = doc(cls(assessment, "DataProtectionImpactAssessment", false),
                "Data Protection Impact Assessment (DPIA) per Art. 35 GDPR. Extends "
                        + "core::Package. Its parts (responsibilities, risks) are typed containment "
                        + "features - keep them there for typed access and their [1..*] cardinality. The "
                        + "inherited Namespace.ownedElement is only for auxiliary owned CWM objects (e.g. "
                        + "a businessinformation::Description).",
                "Datenschutz-Folgenabschätzung", "Data Protection Impact Assessment");

        EClass responsibilityDPIA = doc(cls(assessment, "ResponsibilityDPIA", false),
                null,
                "Zuständigkeit DSFA", "DPIA Responsibility");

        EClass risk = doc(cls(assessment, "Risk", false),
                null,
                "Risiko", "Risk");

        EEnum riskAssessmentKind = doc(enm(assessment, "RiskAssessmentKind"),
                "Whether a risk assessment reflects the inherent risk (before measures) or the "
                        + "residual risk (after measures).",
                "Bewertungsart", "Assessment Kind");
        doc(literal(riskAssessmentKind, "INHERENT", 0, "INHERENT"),
                "Inherent risk, before any measures.",
                "Inhaerent", "Inherent");
        doc(literal(riskAssessmentKind, "RESIDUAL", 1, "RESIDUAL"),
                "Residual risk, after the applied measures.",
                "Residual", "Residual");

        EClass riskAssessment = doc(cls(assessment, "RiskAssessment", false),
                null,
                "Risikobewertung", "Risk Assessment");

        EClass damageCategory = doc(cls(assessment, "DamageCategory", false),
                null,
                "Schadenskategorie", "Damage Category");

        EClass damageExtent = doc(cls(assessment, "DamageExtent", false),
                null,
                "Schadensausprägung", "Damage Extent");

        EEnum occurrenceProbability = doc(enm(assessment, "OccurrenceProbability"),
                null,
                "Eintrittswahrscheinlichkeit", "Occurrence Probability");
        doc(literal(occurrenceProbability, "UNEDITED", 0, "UNEDITED"),
                null,
                "UNBEARBEITET", "UNEDITED");
        doc(literal(occurrenceProbability, "LOW", 1, "LOW"),
                "The incident will probably not occur within the next 6 years, occurred more "
                        + "than 6 years ago or has not occurred yet.",
                "GERING", "LOW");
        doc(literal(occurrenceProbability, "MEDIUM", 2, "MEDIUM"),
                "The incident will probably occur within the next 4 to 6 years or has occurred "
                        + "during the past 4 to 6 years.",
                "MITTEL", "MEDIUM");
        doc(literal(occurrenceProbability, "HIGH", 3, "HIGH"),
                "The incident will probably occur within the next 1 to 3 years or has occurred "
                        + "during the past 1 to 3 years.",
                "HOCH", "HIGH");
        doc(literal(occurrenceProbability, "VERY_HIGH", 4, "VERY_HIGH"),
                "The incident will probably occur within the next year or has occurred during "
                        + "the past year.",
                "SEHR_HOCH", "VERY_HIGH");

        EClass privacyClassRating = doc(cls(assessment, "PrivacyClassRating", false),
                "Privacy rating of a data class: links a CWM Classifier (table, class, view, "
                        + "...) to its GDPR privacy level.",
                "Datenklasse-Datenschutzbewertung", "Privacy Class Rating");

        EClass privacyFieldRating = doc(cls(assessment, "PrivacyFieldRating", false),
                "Privacy rating of a data field: links a CWM StructuralFeature (column, "
                        + "attribute, field, ...) to its GDPR personal-data category according to Art. 4 / "
                        + "Art. 9 GDPR.",
                "Datenfeld-Datenschutzbewertung", "Privacy Field Rating");

        EEnum privacyLevel = doc(enm(assessment, "PrivacyLevel"),
                null,
                "Datenschutz-Level", "Privacy Level");
        doc(literal(privacyLevel, "UNEDITED", 0, "UNKNOWN"),
                null,
                "UNBEARBEITET", "UNEDITED");
        doc(literal(privacyLevel, "NONE", 1, "NONE"),
                null,
                "KEINE", "NONE");
        doc(literal(privacyLevel, "LOW", 2, "LOW"),
                null,
                "GERING", "LOW");
        doc(literal(privacyLevel, "MEDIUM", 3, "MEDIUM"),
                null,
                "MITTEL", "MEDIUM");
        doc(literal(privacyLevel, "HIGH", 4, "HIGH"),
                null,
                "HOCH", "HIGH");

        EEnum personalDataCategory = doc(enm(assessment, "PersonalDataCategory"),
                null,
                "Personendaten-Kategorie", "Personal Data Category");
        doc(literal(personalDataCategory, "UNEDITED", 0, "UNEDITED"),
                null,
                "UNBEARBEITET", "UNEDITED");
        doc(literal(personalDataCategory, "NO_PERSONAL_DATA", 1, "NO_PERSONAL_DATA"),
                "Information that does not relate to identified or identifiable natural persons "
                        + "within the meaning of Art. 4 No. 1 GDPR, or where potential identification is "
                        + "not possible.",
                "KEINE_PERSONENBEZOGENEN_DATEN", "NO_PERSONAL_DATA");
        doc(literal(personalDataCategory, "PERSONAL_DATA_WITHOUT_SPECIAL_CATEGORY", 2,
                "PERSONAL_DATA_WITHOUT_SPECIAL_CATEGORY"),
                "Personal data that does not belong to any special category pursuant to Art. "
                        + "9(1), 10 GDPR. Personal data comprises all information relating to an "
                        + "identified or identifiable natural person, i.e. information that can directly "
                        + "or indirectly, in particular by reference to an identifier such as a name, an "
                        + "identification number, location data, an online identifier, or to one or more "
                        + "factors specific to the physical, physiological, genetic, mental, economic, "
                        + "cultural or social identity of that natural person, lead to the identification "
                        + "of a natural person (Art. 4 No. 1 GDPR).",
                "PERSONENBEZOGENE_DATEN_OHNE_BESONDERE_KATEGORIE", "PERSONAL_DATA_WITHOUT_SPECIAL_CATEGORY");
        doc(literal(personalDataCategory, "ETHNIC_ORIGIN", 3, "ETHNIC_ORIGIN"),
                "Information about characteristics that allow conclusions about belonging to a "
                        + "race or ethnicity, in particular information about a person's physical "
                        + "appearance, such as skin colour, hair type, eye and nose shape.",
                "ETHNISCHE_HERKUNFT", "ETHNIC_ORIGIN");
        doc(literal(personalDataCategory, "RELIGIOUS_PHILOSOPHICAL_BELIEFS", 4, "RELIGIOUS_PHILOSOPHICAL_BELIEFS"),
                "Information about a person's religious or philosophical beliefs. In addition to "
                        + "a person's inner attitude, this also includes external activities through which "
                        + "they express their religious or philosophical conviction, including membership "
                        + "in a religious community or the wearing of a kippah or headscarf.",
                "RELIGIOESES_PHILOSOPHISCHES_WELTBILD", "RELIGIOUS_PHILOSOPHICAL_BELIEFS");
        doc(literal(personalDataCategory, "TRADE_UNION_MEMBERSHIP", 5, "TRADE_UNION_MEMBERSHIP"),
                "Information about membership in a trade union and its orientation.",
                "MITGLIEDSCHAFT_GEWERKSCHAFT", "TRADE_UNION_MEMBERSHIP");
        doc(literal(personalDataCategory, "GENETIC_DATA", 6, "GENETIC_DATA"),
                "Personal data relating to the inherited or acquired genetic characteristics of "
                        + "a natural person which give unique information about the physiology or the "
                        + "health of that natural person and which result, in particular, from an analysis "
                        + "of a biological sample from the natural person in question.",
                "GENETISCHE_DATEN", "GENETIC_DATA");
        doc(literal(personalDataCategory, "BIOMETRIC_DATA", 7, "BIOMETRIC_DATA"),
                "Personal data resulting from specific technical processing relating to the "
                        + "physical, physiological or behavioural characteristics of a natural person, "
                        + "which allow or confirm the unique identification of that natural person, such "
                        + "as facial images or dactyloscopic data.",
                "BIOMETRISCHE_DATEN", "BIOMETRIC_DATA");
        doc(literal(personalDataCategory, "HEALTH_DATA", 8, "HEALTH_DATA"),
                "Personal data related to the physical or mental health of a natural person, "
                        + "including the provision of health care services, which reveal information about "
                        + "his or her health status.",
                "GESUNDHEITSDATEN", "HEALTH_DATA");
        doc(literal(personalDataCategory, "SEXUAL_LIFE_ORIENTATION", 9, "SEXUAL_LIFE_ORIENTATION"),
                "Information about heterosexuality, homosexuality, bisexuality, frequency or "
                        + "type of sexual activity of the data subject.",
                "SEXUALLEBEN_SEXUELLE_ORIENTIERUNG", "SEXUAL_LIFE_ORIENTATION");
        doc(literal(personalDataCategory, "CRIMINAL_CONVICTIONS_OFFENSES", 10, "CRIMINAL_CONVICTIONS_OFFENSES"),
                "In addition to criminal offences, this also includes information about "
                        + "administrative offences. Furthermore, this includes data on binding official "
                        + "decisions in preparatory proceedings aimed at establishing a criminal offence, "
                        + "in particular data on measures in criminal investigation proceedings.",
                "STRAFTATEN_VERURTEILUNGEN", "CRIMINAL_CONVICTIONS_OFFENSES");

        // DataProcessingRecord
        dataProcessingRecord.getESuperTypes().add(refs.corePackage);
        constrain(dataProcessingRecord, "RecordHoldsProcessings",
                "self.ownedElement->select(e | e.oclIsKindOf(DataProcessing))->notEmpty()");
        constrain(dataProcessingRecord, "OnlyProcessingsInRecord",
                "self.ownedElement->forAll(e | e.oclIsKindOf(DataProcessing))");
        doc(oppositeRole(ref(dataProcessingRecord, "contactControllerGlobal", refs.responsibleParty, 1, 1),
                "gdprGlobalControllerOf"),
                null,
                "Kontaktdaten global Verantwortliche:r", "Contact Information Global Controller");
        doc(oppositeRole(ref(dataProcessingRecord, "contactControllerInternal", refs.responsibleParty, 1, 1),
                "gdprInternalControllerOf"),
                null,
                "Kontaktdaten des intern Verantwortliche:r", "Contact Information Internal Controller");
        doc(oppositeRole(ref(dataProcessingRecord, "contactJointResponsibilities", refs.responsibleParty, 0, -1),
                "gdprJointControllerOf"),
                null,
                "Kontaktdaten gemeinsam Verantwortliche", "Contact Information Joint Controllers");
        doc(oppositeRole(ref(dataProcessingRecord, "contactPrivacyOfficer", refs.responsibleParty, 1, 1),
                "gdprPrivacyOfficerOf"),
                null,
                "Kontaktdaten Datenschutzbeauftragte:r", "Contact Information Data Protection Officer");
        doc(oppositeRole(ref(dataProcessingRecord, "contactCommissionedProcessor", refs.responsibleParty, 0, 1),
                "gdprProcessorOf"),
                null,
                "Kontaktdaten Auftragsverarbeitende:r", "Contact Information Processor");

        // DataProcessing
        dataProcessing.getESuperTypes().add(refs.corePackage);
        doc(oppositeRole(ref(dataProcessing, "dataClasses", refs.classifier, 0, -1), "gdprDataProcessing"),
                null,
                "Datenklassen", "Data Classes");
        doc(attr(dataProcessing, "personalReferenceTypes", personalReferenceType, 1, -1),
                null,
                "Personenbezugsarten", "Personal Reference Types");
        doc(attr(dataProcessing, "dataProcessingTypes", dataProcessingType, 1, -1),
                "Data processing types used in the specific data process",
                "Verarbeitungsarten", "Processing Types");
        doc(attr(dataProcessing, "bpmnProcesses", refs.stringType, 0, -1),
                "reference to related bpmn process model/process step descriptions",
                "BPMN-Prozesse", "BPMN Processes");
        doc(oppositeRole(ref(dataProcessing, "softwareSystems", refs.softwareSystem, 0, -1), "gdprDataProcessing"),
                null,
                "Software-Systeme", "Software Systems");
        doc(oppositeRole(ref(dataProcessing, "softwareModules", refs.deployedComponent, 0, -1), "gdprDataProcessing"),
                null,
                "Software-Module", "Software Modules");
        doc(attr(dataProcessing, "otherEnvironmentHardware", refs.stringType, 0, 1),
                null,
                "Sonstige Umgebung und Hardware", "Other Environment and Hardware");
        doc(attr(dataProcessing, "dataProcessingPurpose", refs.stringType, 1, 1),
                "Purpose of data processing in accordance with Article 30 Paragraph 1 Sentence 2 "
                        + "Letter b) DSGVO",
                "Zweck der Datenverarbeitung", "Purpose of Data Processing");
        doc(cont(dataProcessing, "dataProcessingLawfulness", dataProcessingLawfulness, 1, -1),
                null,
                "Erfassungsberechtigungen", "Processing Lawfulness");
        doc(cont(dataProcessing, "dataTransmissions", dataTransmission, 0, -1),
                null,
                "Datenweitergaben", "Data Transmissions");
        doc(cont(dataProcessing, "dataErasures", dataErasure, 1, -1),
                null,
                "Datenlöschungen", "Data Erasures");
        doc(attr(dataProcessing, "validFrom", refs.timeType, 1, 1),
                null,
                "Gültigkeit von", "Valid From");
        doc(attr(dataProcessing, "validTo", refs.timeType, 0, 1),
                null,
                "Gültigkeit bis", "Valid To");
        doc(oppositeRole(ref(dataProcessing, "dpiaRequirementAnalysis", dPIARequirementAnalysis, 1, 1),
                "gdprDataProcessing"),
                null,
                "Schwellenwert-Analyse", "Threshold Analysis");

        // DataProcessingLawfulness
        dataProcessingLawfulness.getESuperTypes().add(refs.modelElement);
        doc(attr(dataProcessingLawfulness, "dataProcessingLawfulnessTypes", dataProcessingLawfulnessType, 0, 1),
                null,
                "Art der Erfassungsberechtigung", "Lawfulness Type");
        doc(attr(dataProcessingLawfulness, "dataProcessingLawfulnessTypesSpecialCategories",
                dataProcessingLawfulnessTypeSpecialCategories, 0, 1),
                null,
                "Erfassungsberechtigungs-Art für besondere Kategorien", "Lawfulness Type for Special Categories");
        doc(attr(dataProcessingLawfulness, "dataProcessingLawfulnessAnnotation", refs.stringType, 1, 1),
                null,
                "Begründung zur Erfassungsberechtigung", "Lawfulness Annotation");

        // DataTransmission
        dataTransmission.getESuperTypes().add(refs.modelElement);
        doc(oppositeRole(ref(dataTransmission, "dataClasses", refs.classifier, 0, -1), "gdprDataTransmission"),
                null,
                "Datenklassen", "Data Classes");
        doc(oppositeRole(ref(dataTransmission, "dataFields", refs.structuralFeature, 0, -1), "gdprDataTransmission"),
                null,
                "Datenfelder", "Data Fields");
        doc(oppositeRole(ref(dataTransmission, "contactRecipient", refs.responsibleParty, 1, 1), "gdprRecipientOf"),
                null,
                "Kontaktdaten Empfänger", "Contact Information Recipient");
        doc(oppositeRole(ref(dataTransmission, "policy", refs.deidentificationPolicy, 0, 1), "gdprTransmission"),
                "How the transmitted data is de-identified for the recipient: the policy of the model "
                        + "deidentification, whose audience is the recipient.",
                "Deidentifizierung", "De-identification");
        doc(attr(dataTransmission, "guarantee", refs.stringType, 0, 1),
                null,
                "Garantie", "Guarantee");
        doc(attr(dataTransmission, "adequacyDecision", refs.stringType, 0, 1),
                null,
                "Angemessenheitsbeschluss", "Adequacy Decision");

        // DataErasure
        dataErasure.getESuperTypes().add(refs.deletionObligation);

        // DPIARequirementAnalysis
        dPIARequirementAnalysis.getESuperTypes().add(refs.modelElement);
        doc(oppositeRole(ref(dPIARequirementAnalysis, "riskyProcessings", riskyProcessing, 1, -1),
                "gdprRequirementAnalysis"),
                null,
                "Riskante Verarbeitungen", "Risky Processings");
        doc(attr(dPIARequirementAnalysis, "otherHighRisk", refs.stringType, 0, 1),
                null,
                "Sonstiges hohes Risiko", "Other High Risk");
        doc(attr(dPIARequirementAnalysis, "explanation", refs.stringType, 1, 1),
                null,
                "Begründung", "Explanation");
        doc(attr(dPIARequirementAnalysis, "dpiaRequired", refs.booleanType, 1, 1),
                null,
                "Erfordernis DSFA", "DPIA Required");

        // RiskyProcessing
        riskyProcessing.getESuperTypes().add(refs.modelElement);
        doc(attr(riskyProcessing, "score", refs.integerType, 1, 1),
                null,
                "Punktzahl", "Score");

        // DataProtectionImpactAssessment
        dataProtectionImpactAssessment.getESuperTypes().add(refs.corePackage);
        doc(oppositeRole(ref(dataProtectionImpactAssessment, "dataProcessings", dataProcessing, 1, -1),
                "gdprImpactAssessment"),
                null,
                "Datenverarbeitungen", "Data Processings");
        doc(cont(dataProtectionImpactAssessment, "responsibilities", responsibilityDPIA, 1, -1),
                null,
                "Zuständigkeiten", "Responsibilities");
        doc(attr(dataProtectionImpactAssessment, "affectedParties", refs.stringType, 1, 1),
                null,
                "Betroffene", "Affected Parties");
        doc(cont(dataProtectionImpactAssessment, "risks", risk, 1, -1),
                null,
                "Risiken", "Risks");

        // ResponsibilityDPIA
        responsibilityDPIA.getESuperTypes().add(refs.modelElement);
        doc(oppositeRole(ref(responsibilityDPIA, "contact", refs.responsibleParty, 1, 1), "gdprDpiaResponsibility"),
                null,
                "Kontakt", "Contact");
        doc(attr(responsibilityDPIA, "role", refs.stringType, 1, 1),
                null,
                "Rolle", "Role");

        // Risk
        risk.getESuperTypes().add(refs.modelElement);
        constrain(risk, "ExactlyOneInherentAssessment",
                "self.assessment->select(a | a.kind = "
                        + "assessment::RiskAssessmentKind::INHERENT)->size() = 1");
        constrain(risk, "ResidualAssessmentNamesMeasures",
                "self.assessment->forAll(a | a.kind = assessment::RiskAssessmentKind::RESIDUAL "
                        + "implies a.appliedMeasure->notEmpty())");
        doc(attr(risk, "incidentDescription", refs.stringType, 1, 1),
                null,
                "Ereignisbeschreibung", "Incident Description");
        doc(attr(risk, "riskSource", refs.stringType, 1, 1),
                null,
                "Risikoquelle", "Risk Source");
        doc(attr(risk, "damageDescription", refs.stringType, 1, 1),
                null,
                "Schadensbeschreibung", "Damage Description");
        doc(cont(risk, "assessment", riskAssessment, 1, -1),
                "The risk assessments over time: one inherent assessment (before measures) and "
                        + "any number of residual assessments (after measures).",
                "Risikobewertungen", "Risk Assessments");

        // RiskAssessment
        riskAssessment.getESuperTypes().add(refs.modelElement);
        doc(attr(riskAssessment, "kind", riskAssessmentKind, 1, 1),
                "Whether this is the inherent assessment (before measures) or a residual "
                        + "assessment (after measures).",
                "Art", "Kind");
        doc(attr(riskAssessment, "assessedAt", refs.timeType, 0, 1),
                "When this assessment was made.",
                "Bewertet am", "Assessed At");
        doc(oppositeRole(ref(riskAssessment, "appliedMeasure", refs.changeRequest, 0, -1), "gdprRiskAssessment"),
                "The measures whose effect this residual assessment presupposes, each a "
                        + "warehouseoperation::ChangeRequest (a tracked, lifecycle-bearing countermeasure "
                        + "affecting model elements). No containment.",
                "Angewandte Massnahmen", "Applied Measures");
        doc(cont(riskAssessment, "damageExtents", damageExtent, 1, -1),
                null,
                "Schadensausprägungen", "Damage Extents");
        doc(attr(riskAssessment, "occurrenceProbability", occurrenceProbability, 1, 1),
                null,
                "Eintrittswahrscheinlichkeit", "Occurrence Probability");

        // DamageCategory
        damageCategory.getESuperTypes().add(refs.modelElement);

        // DamageExtent
        damageExtent.getESuperTypes().add(refs.modelElement);
        doc(oppositeRole(ref(damageExtent, "damageCategory", damageCategory, 1, 1), "gdprDamageExtent"),
                null,
                "Schadenskategorie", "Damage Category");

        // PrivacyClassRating
        privacyClassRating.getESuperTypes().add(refs.modelElement);
        doc(oppositeRole(ref(privacyClassRating, "dataClass", refs.classifier, 1, 1), "gdprPrivacyClassRating"),
                "Classified data class",
                "Datenklasse", "Data Class");
        doc(attr(privacyClassRating, "privacyLevel", privacyLevel, 1, 1),
                null,
                "Datenschutz-Level", "Privacy Level");

        // PrivacyFieldRating
        privacyFieldRating.getESuperTypes().add(refs.modelElement);
        doc(oppositeRole(ref(privacyFieldRating, "dataField", refs.structuralFeature, 1, 1), "gdprPrivacyFieldRating"),
                "Classified data field",
                "Datenfeld", "Data Field");
        doc(attr(privacyFieldRating, "personalDataCategory", personalDataCategory, 1, 1),
                null,
                "Kategorie der personenbezogenen Daten", "Personal Data Category");

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
