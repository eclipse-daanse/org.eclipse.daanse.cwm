/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.criticality.build;

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
 * Programmatic builder for the {@code criticality} metamodel: how critical a CWM model object or its
 * content is, rated per dimension, with the measures that reduce it, the evidence, the changes that
 * affect it and the regimes and sectors it is assessed under.
 *
 * <p>The German and English captions of the elements are GenModel details ({@code caption.de},
 * {@code caption.en}). Construction and serialization follow the builder of {@code lifecycle};
 * references into other models are written with the namespace URI of their package.</p>
 */
public final class CriticalityEcoreBuilder {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final String NS_URI =
            "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.governance.criticality";
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
        final EDataType booleanType;
        final EDataType timeType;
        final EClass corePackage;
        final EClass element;
        final EClass modelElement;
        final EClass measurement;
        final EClass changeRequest;

        Refs(EPackage core, EPackage warehouseoperation) {
            this.stringType = eDataType(core, "String");
            this.booleanType = eDataType(core, "Boolean");
            this.timeType = eDataType(core, "Time");
            this.corePackage = eClass(core, "Package");
            this.element = eClass(core, "Element");
            this.modelElement = eClass(core, "ModelElement");
            this.measurement = eClass(warehouseoperation, "Measurement");
            this.changeRequest = eClass(warehouseoperation, "ChangeRequest");
        }
    }

    public CriticalityEcoreBuilder(Refs refs) {
        this.refs = refs;
    }

    /**
     * Builds the metamodel from the Ecores of the models it refers to, next to {@code moduleDir}
     * (module directory {@code model/daanse/governance/criticality}), and writes it to {@code outFile}.
     */
    public static void generate(File moduleDir, File outFile) throws IOException {
        File cwmDir = new File(moduleDir, "../../../cwm").getCanonicalFile();
        File governanceDir = new File(moduleDir, "..").getCanonicalFile();
        ResourceSet rs = newResourceSet();
        EPackage core = loadPackage(rs, ecoreUri(cwmDir, "objectmodel.core/model/core.ecore"));
        EPackage warehouseoperation = loadPackage(rs,
                ecoreUri(cwmDir, "management.warehouseoperation/model/warehouseoperation.ecore"));
        outFile.getParentFile().mkdirs();
        save(new CriticalityEcoreBuilder(new Refs(core, warehouseoperation)).build(), rs,
                URI.createFileURI(outFile.getAbsolutePath()));
    }

    /** Creates the EPackage {@code criticality}. */
    public EPackage build() {
        EPackage p = ef.createEPackage();
        p.setName("criticality");
        p.setNsURI(NS_URI);
        p.setNsPrefix("criticality");
        root(p, "Criticality", "KRITIS criticality overlay: rates how critical a CWM model object or its content"
                        + " is - classes, attributes, references, associations, warehouse processes, "
                        + "systems and changes - for banks, public authorities and network operators under "
                        + "NIS2, DORA, the BSI Act, the BSI-KritisV and the KRITIS umbrella act. A single "
                        + "generic rating attaches via element -> core::ModelElement to arbitrary objects, "
                        + "exactly like warehouseoperation::Measurement in the CWM core. Supersedes the "
                        + "empty critis placeholder package.",
                null, null);


        EEnum criticalityLevel = doc(enm(p, "CriticalityLevel"),
                "Protection need per BSI IT-Grundschutz: normal, high, very high.",
                "Kritikalitaetsstufe", "Criticality Level");
        doc(literal(criticalityLevel, "UNASSESSED", 0, "UNASSESSED"),
                "Not yet assessed.",
                "Unbewertet", "Not Assessed");
        doc(literal(criticalityLevel, "NORMAL", 1, "NORMAL"),
                "Impact is limited and manageable.",
                "Normal", "Normal");
        doc(literal(criticalityLevel, "HIGH", 2, "HIGH"),
                "Impact can be considerable.",
                "Hoch", "High");
        doc(literal(criticalityLevel, "VERY_HIGH", 3, "VERY_HIGH"),
                "Impact can be existentially threatening, catastrophic.",
                "Sehr hoch", "Very High");

        EEnum criticalityDimension = doc(enm(p, "CriticalityDimension"),
                "Rating axis. The first three are the BSI protection goals; the rest extend them "
                        + "with resilience, dependency and regulatory aspects from NIS2, DORA and the "
                        + "KRITIS umbrella act.",
                "Kritikalitaetsdimension", "Criticality Dimension");
        doc(literal(criticalityDimension, "CONFIDENTIALITY", 0, "CONFIDENTIALITY"),
                "Protection against unauthorised disclosure.",
                "Vertraulichkeit", "Confidentiality");
        doc(literal(criticalityDimension, "INTEGRITY", 1, "INTEGRITY"),
                "Protection against unauthorised modification.",
                "Integritaet", "Integrity");
        doc(literal(criticalityDimension, "AVAILABILITY", 2, "AVAILABILITY"),
                "Protection against outage.",
                "Verfuegbarkeit", "Availability");
        doc(literal(criticalityDimension, "RECOVERY", 3, "RECOVERY"),
                "Criticality of recoverability after an outage (KRITIS umbrella act).",
                "Wiederanlauf", "Recovery");
        doc(literal(criticalityDimension, "DEPENDENCY", 4, "DEPENDENCY"),
                "Criticality from dependencies and cascade effects (DORA, KRITIS umbrella act).",
                "Abhaengigkeit", "Dependency");
        doc(literal(criticalityDimension, "PUBLIC_SAFETY", 5, "PUBLIC_SAFETY"),
                "Sensitivity for public order and safety.",
                "Oeffentliche Sicherheit", "Public Safety");
        doc(literal(criticalityDimension, "MARKET_STABILITY", 6, "MARKET_STABILITY"),
                "Sensitivity for financial-market and systemic stability (DORA).",
                "Marktstabilitaet", "Market Stability");
        doc(literal(criticalityDimension, "DISCLOSURE", 7, "DISCLOSURE"),
                "Risk from disclosure or reconnaissance (e.g. installation descriptions).",
                "Offenlegung", "Disclosure Risk");
        doc(literal(criticalityDimension, "CHANGE", 8, "CHANGE"),
                "Criticality of major changes (DORA major change).",
                "Aenderung", "Change Risk");

        EEnum classification = doc(enm(p, "Classification"),
                "German classification level (Verschlusssache) per SUeG/VSA - a confidentiality "
                        + "axis separate from impact level. An emergency plan can be highly available AND "
                        + "classified; the two are different things. English names with the German "
                        + "equivalent in the documentation.",
                "VS-Einstufung", "Classification Level");
        doc(literal(classification, "UNCLASSIFIED", 0, "UNCLASSIFIED"),
                "Not classified (offen).",
                "Offen", "Unclassified");
        doc(literal(classification, "RESTRICTED", 1, "RESTRICTED"),
                "Restricted (VS - Nur fuer den Dienstgebrauch, VS-NfD).",
                "VS-NfD", "Restricted");
        doc(literal(classification, "CONFIDENTIAL", 2, "CONFIDENTIAL"),
                "Confidential (VS - Vertraulich).",
                "VS-Vertraulich", "Confidential");
        doc(literal(classification, "SECRET", 3, "SECRET"),
                "Secret (Geheim).",
                "Geheim", "Secret");
        doc(literal(classification, "TOP_SECRET", 4, "TOP_SECRET"),
                "Top secret (Streng geheim).",
                "Streng geheim", "Top Secret");

        EEnum legalRegime = doc(enm(p, "LegalRegime"),
                "Regulatory regime under which an assessment stands.",
                "Regulierungsregime", "Legal Regime");
        doc(literal(legalRegime, "NIS2", 0, "NIS2"),
                "NIS 2 Directive (EU) 2022/2555.",
                "NIS 2", "NIS 2");
        doc(literal(legalRegime, "DORA", 1, "DORA"),
                "DORA, Regulation (EU) 2022/2554.",
                "DORA", "DORA");
        doc(literal(legalRegime, "BSIG", 2, "BSIG"),
                "BSI Act (BSIG, 2025 revision).",
                "BSIG", "BSI Act");
        doc(literal(legalRegime, "BSI_KRITISV", 3, "BSI_KRITISV"),
                "BSI Critis Regulation (BSI-KritisV, transitional).",
                "BSI-KritisV", "BSI-KritisV");
        doc(literal(legalRegime, "KRITIS_UMBRELLA_ACT", 4, "KRITIS_UMBRELLA_ACT"),
                "KRITIS umbrella act (KRITIS-Dachgesetz, 2026).",
                "KRITIS-Dachgesetz", "KRITIS Umbrella Act");
        doc(literal(legalRegime, "CER", 5, "CER"),
                "CER Directive (EU) 2022/2557.",
                "CER", "CER");
        doc(literal(legalRegime, "SECTOR_SPECIFIC", 6, "SECTOR_SPECIFIC"),
                "Sector-specific regime.",
                "Sektorspezifisch", "Sector-Specific");

        EEnum sector = doc(enm(p, "Sector"),
                "KRITIS sector per BSI-KritisV / KRITIS umbrella act.",
                "KRITIS-Sektor", "KRITIS Sector");
        doc(literal(sector, "ENERGY", 0, "ENERGY"),
                "Energy.",
                "Energie", "Energy");
        doc(literal(sector, "WATER", 1, "WATER"),
                "Water and waste water.",
                "Wasser", "Water");
        doc(literal(sector, "FOOD", 2, "FOOD"),
                "Food.",
                "Ernaehrung", "Food");
        doc(literal(sector, "IT_TELECOM", 3, "IT_TELECOM"),
                "Information technology and telecommunications.",
                "IT und TK", "IT and Telecom");
        doc(literal(sector, "HEALTH", 4, "HEALTH"),
                "Health.",
                "Gesundheit", "Health");
        doc(literal(sector, "FINANCE_INSURANCE", 5, "FINANCE_INSURANCE"),
                "Finance and insurance.",
                "Finanz- und Versicherungswesen", "Finance and Insurance");
        doc(literal(sector, "TRANSPORT", 6, "TRANSPORT"),
                "Transport and traffic.",
                "Transport und Verkehr", "Transport");
        doc(literal(sector, "WASTE", 7, "WASTE"),
                "Municipal waste disposal.",
                "Siedlungsabfall", "Waste");
        doc(literal(sector, "PUBLIC_ADMINISTRATION", 8, "PUBLIC_ADMINISTRATION"),
                "State and public administration.",
                "Staat und Verwaltung", "Public Administration");
        doc(literal(sector, "SPACE", 9, "SPACE"),
                "Space.",
                "Weltraum", "Space");
        doc(literal(sector, "OTHER", 10, "OTHER"),
                "Other sector.",
                "Sonstige", "Other");

        EEnum entityCategory = doc(enm(p, "EntityCategory"),
                "Regulatory category of the entity per NIS2 / BSIG.",
                "Einrichtungskategorie", "Entity Category");
        doc(literal(entityCategory, "ESSENTIAL", 0, "ESSENTIAL"),
                "Essential entity (NIS2).",
                "Wesentlich", "Essential");
        doc(literal(entityCategory, "IMPORTANT", 1, "IMPORTANT"),
                "Important entity (NIS2).",
                "Wichtig", "Important");
        doc(literal(entityCategory, "CRITICAL_INSTALLATION", 2, "CRITICAL_INSTALLATION"),
                "Operator of a critical installation (BSIG).",
                "Kritische Anlage", "Critical Installation");
        doc(literal(entityCategory, "NOT_REGULATED", 3, "NOT_REGULATED"),
                "Not regulated.",
                "Nicht reguliert", "Not Regulated");

        EEnum measureRole = doc(enm(p, "MeasureRole"),
                "Functional role of an assigned measure.",
                "Massnahmenrolle", "Measure Role");
        doc(literal(measureRole, "PREVENTIVE", 0, "PREVENTIVE"),
                "Preventive.",
                "Praeventiv", "Preventive");
        doc(literal(measureRole, "DETECTIVE", 1, "DETECTIVE"),
                "Detective.",
                "Detektiv", "Detective");
        doc(literal(measureRole, "CORRECTIVE", 2, "CORRECTIVE"),
                "Corrective.",
                "Korrektiv", "Corrective");
        doc(literal(measureRole, "RECOVERY", 3, "RECOVERY"),
                "Recovery.",
                "Wiederanlauf", "Recovery");
        doc(literal(measureRole, "COMPENSATING", 4, "COMPENSATING"),
                "Compensating.",
                "Kompensierend", "Compensating");

        EEnum evidenceKind = doc(enm(p, "EvidenceKind"),
                "Kind of compliance evidence (NIS2, BSIG, KRITIS umbrella act).",
                "Nachweisart", "Evidence Kind");
        doc(literal(evidenceKind, "AUDIT", 0, "AUDIT"),
                "Audit.",
                "Audit", "Audit");
        doc(literal(evidenceKind, "CERTIFICATE", 1, "CERTIFICATE"),
                "Certificate.",
                "Zertifikat", "Certificate");
        doc(literal(evidenceKind, "ASSURANCE_AUDIT", 2, "ASSURANCE_AUDIT"),
                "Assurance audit (formerly Section 8a BSIG).",
                "Nachweispruefung", "Assurance Audit");
        doc(literal(evidenceKind, "RESILIENCE_PLAN", 3, "RESILIENCE_PLAN"),
                "Resilience plan (KRITIS umbrella act).",
                "Resilienzplan", "Resilience Plan");
        doc(literal(evidenceKind, "TEST", 4, "TEST"),
                "Test or exercise.",
                "Test", "Test");
        doc(literal(evidenceKind, "RISK_ANALYSIS", 5, "RISK_ANALYSIS"),
                "Risk analysis.",
                "Risikoanalyse", "Risk Analysis");

        EClass criticalityAssessment = doc(cls(p, "CriticalityAssessment", false),
                "A versioned criticality assessment context for a domain, operator or service. "
                        + "Extends core::Package: its content lives in the inherited "
                        + "Namespace.ownedElement. Good owned objects (enforced by the "
                        + "OnlyGovernanceContent constraint): CriticalityRating, ChangeAssessment, "
                        + "RegimeProfile, SectorProfile, EquivalenceMapping - plus the "
                        + "warehouseoperation::Measurement and warehouseoperation::ChangeRequest instances "
                        + "the ratings reference (they get their home here, so cross-resource proxies "
                        + "resolve). Versioning via validFrom/validTo represents the transition from the "
                        + "BSI-KritisV to the KRITIS umbrella act.",
                "Kritikalitaetsbewertung", "Criticality Assessment");

        EClass criticalityRating = doc(cls(p, "CriticalityRating", false),
                "The central, generic rating. It attaches via element to one or more arbitrary "
                        + "CWM model objects - class, attribute, association, warehouse process, system - "
                        + "exactly like warehouseoperation::Measurement. overallLevel follows the BSI "
                        + "maximum principle over the dimension scores.",
                "Element-Kritikalitaetsbewertung", "Element Criticality Rating");

        EClass dimensionScore = doc(cls(p, "DimensionScore", false),
                "A single score for exactly one criticality dimension. Extends core::Element "
                        + "(not a namespace member, just a value carrier).",
                "Dimensionsbewertung", "Dimension Score");

        EClass measureAssignment = doc(cls(p, "MeasureAssignment", false),
                "Context-specific assignment of a reusable TOM measure to a rating. The same "
                        + "measure can play different roles and have different effect in different "
                        + "contexts, so the context sits here and not on the measure itself.",
                "Massnahmenzuordnung", "Measure Assignment");

        EClass changeAssessment = doc(cls(p, "ChangeAssessment", false),
                "Assessment of the criticality risk arising from a major change (DORA major "
                        + "change). changeElement points generically at the changed model objects - "
                        + "canonically a warehouseoperation::ChangeRequest, but deliberately "
                        + "core::ModelElement so this package does not inherit the warehouse hull.",
                "Aenderungsbewertung", "Change Assessment");

        EClass regimeProfile = doc(cls(p, "RegimeProfile", false),
                "View of a regulatory regime on the assessment. Several profiles allow looking "
                        + "at the same object under DORA, BSIG and NIS2 at once.",
                "Regime-Profil", "Regime Profile");

        EClass sectorProfile = doc(cls(p, "SectorProfile", false),
                "Sector and operator classification of the assessment.",
                "Sektor-Profil", "Sector Profile");

        EClass equivalenceMapping = doc(cls(p, "EquivalenceMapping", false),
                "Declares when one regime is satisfied by satisfying another (DORA/BSIG "
                        + "offsetting). Prevents duplicated obligations and contradictory dashboards for "
                        + "banks and certain digital operators.",
                "Gleichwertigkeitsabbildung", "Equivalence Mapping");

        EClass evidence = doc(cls(p, "Evidence", false),
                "A compliance evidence item: audit, certificate, assurance audit, resilience "
                        + "plan, test or risk analysis.",
                "Nachweis", "Evidence");

        // CriticalityAssessment
        criticalityAssessment.getESuperTypes().add(refs.corePackage);
        constrain(criticalityAssessment, "OnlyGovernanceContent",
                "self.ownedElement->forAll(e | e.oclIsKindOf(CriticalityRating) or "
                        + "e.oclIsKindOf(ChangeAssessment) or e.oclIsKindOf(RegimeProfile) or "
                        + "e.oclIsKindOf(SectorProfile) or e.oclIsKindOf(EquivalenceMapping) or "
                        + "e.oclIsKindOf(warehouseoperation::Measurement) or "
                        + "e.oclIsKindOf(warehouseoperation::ChangeRequest))");
        annotate(criticalityAssessment,
                "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.governance.criticality/constraints",
                "OnlyGovernanceContent.name", "OnlyGovernanceContent",
                "OnlyGovernanceContent.description",
                        "A criticality assessment contains only ratings, change assessments and profiles.",
                "OnlyGovernanceContent.level", "error");
        doc(attr(criticalityAssessment, "validFrom", refs.timeType, 1, 1),
                "Valid from.",
                "Gueltig ab", "Valid From");
        doc(attr(criticalityAssessment, "validTo", refs.timeType, 0, 1),
                "Valid to.",
                "Gueltig bis", "Valid To");

        // CriticalityRating
        criticalityRating.getESuperTypes().add(refs.modelElement);
        doc(oppositeRole(ref(criticalityRating, "element", refs.modelElement, 1, -1), "criticalityRating"),
                "The rated model objects. No containment; the opposite end is not a feature "
                        + "because core::ModelElement belongs to the CWM core.",
                "Bewertetes Element", "Rated Element");
        doc(attr(criticalityRating, "overallLevel", criticalityLevel, 1, 1),
                "Overall criticality (the maximum over the dimensions).",
                "Gesamtstufe", "Overall Level");
        doc(attr(criticalityRating, "classification", classification, 0, 1),
                "Classification level (Verschlusssache), if applicable.",
                "VS-Einstufung", "Classification");
        doc(attr(criticalityRating, "rationale", refs.stringType, 1, 1),
                "Rationale of the rating.",
                "Begruendung", "Rationale");
        doc(cont(criticalityRating, "dimensionScore", dimensionScore, 1, -1),
                "The per-dimension scores.",
                "Dimensionsbewertungen", "Dimension Scores");
        doc(cont(criticalityRating, "measureAssignment", measureAssignment, 0, -1),
                "Assigned measures with context.",
                "Massnahmenzuordnungen", "Measure Assignments");
        doc(cont(criticalityRating, "evidence", evidence, 0, -1),
                "Evidence for this rating.",
                "Nachweise", "Evidence");

        // DimensionScore
        dimensionScore.getESuperTypes().add(refs.element);
        doc(attr(dimensionScore, "dimension", criticalityDimension, 1, 1),
                "The rated dimension.",
                "Dimension", "Dimension");
        doc(attr(dimensionScore, "level", criticalityLevel, 1, 1),
                "The score of this dimension.",
                "Stufe", "Level");
        doc(attr(dimensionScore, "justification", refs.stringType, 0, 1),
                "Justification of this dimension.",
                "Begruendung", "Justification");
        doc(oppositeRole(ref(dimensionScore, "measurement", refs.measurement, 0, 1), "criticalityDimensionScore"),
                "Optional link to a warehouseoperation::Measurement carrying the raw value, its "
                        + "type (measured/estimated/planned) and the creation/effective dates. The "
                        + "Measurement lives as an ownedElement in the CriticalityAssessment namespace; "
                        + "dimension and level here are the typed criticality view of it. No containment.",
                "Messung", "Measurement");

        // MeasureAssignment
        measureAssignment.getESuperTypes().add(refs.modelElement);
        doc(oppositeRole(ref(measureAssignment, "measure", refs.changeRequest, 1, 1), "criticalityMeasureAssignment"),
                "The assigned measure as a warehouseoperation::ChangeRequest - a tracked "
                        + "countermeasure with its own lifecycle (status/completed/dates) affecting model "
                        + "elements. Its implementation status therefore lives on the ChangeRequest "
                        + "itself, not here. No containment; the opposite end is not a feature.",
                "Massnahme", "Measure");
        doc(attr(measureAssignment, "role", measureRole, 1, 1),
                "Functional role in context.",
                "Rolle", "Role");
        doc(attr(measureAssignment, "effectiveness", criticalityLevel, 0, 1),
                "Residual criticality after the measure.",
                "Wirksamkeit", "Effectiveness");
        doc(attr(measureAssignment, "reviewDate", refs.timeType, 0, 1),
                "Next review date.",
                "Ueberpruefung", "Review Date");

        // ChangeAssessment
        changeAssessment.getESuperTypes().add(refs.modelElement);
        doc(attr(changeAssessment, "majorChange", refs.booleanType, 1, 1),
                "Whether this is a major change in the sense of DORA.",
                "Wesentliche Aenderung", "Major Change");
        doc(attr(changeAssessment, "changeRiskLevel", criticalityLevel, 1, 1),
                "Criticality of the change.",
                "Aenderungsrisiko", "Change Risk Level");
        doc(oppositeRole(ref(changeAssessment, "changeElement", refs.modelElement, 1, -1),
                "criticalityChangeAssessment"),
                "The changed model objects (canonically a warehouseoperation::ChangeRequest). No "
                        + "containment; the opposite end is not a feature.",
                "Geaendertes Element", "Changed Element");
        doc(oppositeRole(ref(changeAssessment, "affectedRating", criticalityRating, 0, -1),
                "criticalityChangeAssessment"),
                "The ratings affected by the change. No containment.",
                "Betroffene Bewertungen", "Affected Ratings");

        // RegimeProfile
        regimeProfile.getESuperTypes().add(refs.modelElement);
        doc(attr(regimeProfile, "regime", legalRegime, 1, 1),
                "The regime.",
                "Regime", "Regime");
        doc(attr(regimeProfile, "legalReference", refs.stringType, 0, 1),
                "Legal reference.",
                "Rechtsgrundlage", "Legal Reference");
        doc(attr(regimeProfile, "equivalentTo", refs.stringType, 0, 1),
                "Free-text equivalence; formally via EquivalenceMapping.",
                "Gleichwertig zu", "Equivalent To");

        // SectorProfile
        sectorProfile.getESuperTypes().add(refs.modelElement);
        doc(attr(sectorProfile, "sector", sector, 1, 1),
                "The KRITIS sector.",
                "Sektor", "Sector");
        doc(attr(sectorProfile, "subsector", refs.stringType, 0, 1),
                "Subsector or installation category.",
                "Teilsektor", "Subsector");
        doc(attr(sectorProfile, "category", entityCategory, 1, 1),
                "Regulatory category.",
                "Kategorie", "Category");

        // EquivalenceMapping
        equivalenceMapping.getESuperTypes().add(refs.modelElement);
        doc(attr(equivalenceMapping, "fromRegime", legalRegime, 1, 1),
                "Source regime.",
                "Von Regime", "From Regime");
        doc(attr(equivalenceMapping, "toRegime", legalRegime, 1, 1),
                "Target regime.",
                "Zu Regime", "To Regime");
        doc(attr(equivalenceMapping, "condition", refs.stringType, 1, 1),
                "Condition of the offsetting.",
                "Bedingung", "Condition");

        // Evidence
        evidence.getESuperTypes().add(refs.modelElement);
        doc(attr(evidence, "kind", evidenceKind, 1, 1),
                "Kind of evidence.",
                "Art", "Kind");
        doc(attr(evidence, "reference", refs.stringType, 1, 1),
                "Reference or file number.",
                "Fundstelle", "Reference");
        doc(attr(evidence, "issuedDate", refs.timeType, 0, 1),
                "Issue date.",
                "Ausstellungsdatum", "Issued Date");

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
