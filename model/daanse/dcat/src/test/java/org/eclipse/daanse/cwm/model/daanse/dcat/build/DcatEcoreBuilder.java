/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.dcat.build;

import java.io.IOException;
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
 * Programmatic builder for the {@code dcat} metamodel: W3C DCAT 3 (Data Catalog Vocabulary, REC
 * 2024) as a CWM-anchored Daanse extension.
 *
 * <p>Content already modelled in CWM (a {@code relational::Schema}, single tables or classes) is
 * <em>referenced</em> as the datasets of a catalog, not copied: {@code Dataset.subject} points at any
 * {@code core::ModelElement}. Agents (publisher, creator, contact point) are
 * {@code businessinformation::ResponsibleParty}s; themes point at {@code businessnomenclature::Concept}s
 * next to EU vocabulary IRIs; a data service may point at its deployed component
 * ({@code softwaredeployment::DataProvider}).</p>
 *
 * <p>Naming: DCAT property names literally in camelCase; vocabulary resources flattened to strings
 * carry the suffix {@code Iri} ({@code themeIri}, {@code licenseIri}); plain web addresses keep
 * {@code Url} or the DCAT name ({@code accessUrl}, {@code endpointUrl}, {@code landingPage}). The
 * DCAT 3 inverses (dcat:seriesMember, dcat:inCatalog, ...) are the {@code Property.oppositeRoleName}
 * annotations of the hidden reverse directions.</p>
 */
public final class DcatEcoreBuilder {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String ECORE_ANN = "http://www.eclipse.org/emf/2002/Ecore";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final String BASE_NS =
            "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.dcat";
    private static final String CONSTRAINTS_NS = BASE_NS + "/constraints";

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

    /** The classifiers of the CWM Ecores the metamodel refers to. */
    public static final class CwmRefs {
        final EClass modelElement;
        final EClass element;
        final EClass cwmPackage;
        final EClass responsibleParty;
        final EClass concept;
        final EClass taxonomy;
        final EClass dataProvider;
        final EDataType stringType;
        final EDataType timeType;
        final EDataType integerType;
        final EDataType floatType;

        CwmRefs(EClass modelElement, EClass element, EClass cwmPackage, EClass responsibleParty,
                EClass concept, EClass taxonomy, EClass dataProvider, EDataType stringType,
                EDataType timeType, EDataType integerType, EDataType floatType) {
            this.modelElement = modelElement;
            this.element = element;
            this.cwmPackage = cwmPackage;
            this.responsibleParty = responsibleParty;
            this.concept = concept;
            this.taxonomy = taxonomy;
            this.dataProvider = dataProvider;
            this.stringType = stringType;
            this.timeType = timeType;
            this.integerType = integerType;
            this.floatType = floatType;
        }
    }

    public DcatEcoreBuilder(CwmRefs cwm) {
        this.cwm = cwm;
    }

    /**
     * Builds the package from the CWM Ecores next to {@code moduleDir} (module directory
     * {@code model/daanse/dcat}) and writes it to {@code outFile}.
     */
    public static void generate(java.io.File moduleDir, java.io.File outFile) throws IOException {
        java.io.File cwmDir = new java.io.File(moduleDir, "../../cwm").getCanonicalFile();
        ResourceSet rs = newResourceSet();
        CwmRefs refs = loadCwmRefs(rs, ecoreUri(cwmDir, "objectmodel.core/model/core.ecore"),
                ecoreUri(cwmDir, "foundation.businessinformation/model/businessinformation.ecore"),
                ecoreUri(cwmDir, "analysis.businessnomenclature/model/businessnomenclature.ecore"),
                ecoreUri(cwmDir, "foundation.softwaredeployment/model/softwaredeployment.ecore"));
        outFile.getParentFile().mkdirs();
        save(new DcatEcoreBuilder(refs).build(), rs, URI.createFileURI(outFile.getAbsolutePath()));
    }

    private static URI ecoreUri(java.io.File cwmDir, String path) {
        java.io.File f = new java.io.File(cwmDir, path);
        if (!f.isFile()) {
            throw new IllegalStateException("CWM ecore not found: " + f);
        }
        return URI.createFileURI(f.getAbsolutePath());
    }

    /**
     * Loads the CWM Ecores and resolves the classifiers the metamodel refers to. The resource URI
     * becomes the nsURI, so that references serialize as {@code <nsURI>#//Class}.
     */
    public static CwmRefs loadCwmRefs(ResourceSet rs, URI coreEcoreUri, URI businessInformationEcoreUri,
            URI businessNomenclatureEcoreUri, URI softwareDeploymentEcoreUri) {
        EPackage core = loadPackage(rs, coreEcoreUri);
        EPackage businessInformation = loadPackage(rs, businessInformationEcoreUri);
        EPackage businessNomenclature = loadPackage(rs, businessNomenclatureEcoreUri);
        EPackage softwareDeployment = loadPackage(rs, softwareDeploymentEcoreUri);

        return new CwmRefs(
                eClass(core, "ModelElement"),
                eClass(core, "Element"),
                eClass(core, "Package"),
                eClass(businessInformation, "ResponsibleParty"),
                eClass(businessNomenclature, "Concept"),
                eClass(businessNomenclature, "Taxonomy"),
                eClass(softwareDeployment, "DataProvider"),
                eDataType(core, "String"),
                eDataType(core, "Time"),
                eDataType(core, "Integer"),
                eDataType(core, "Float"));
    }

    private static EPackage loadPackage(ResourceSet rs, URI ecoreUri) {
        Resource res = rs.getResource(ecoreUri, true);
        EPackage pkg = (EPackage) res.getContents().get(0);
        res.setURI(URI.createURI(pkg.getNsURI()));
        registerRecursive(rs, pkg);
        return pkg;
    }

    private static void registerRecursive(ResourceSet rs, EPackage pkg) {
        rs.getPackageRegistry().put(pkg.getNsURI(), pkg);
        for (EPackage sub : pkg.getESubpackages()) {
            registerRecursive(rs, sub);
        }
    }

    private static EClass eClass(EPackage pkg, String name) {
        EClassifier c = pkg.getEClassifier(name);
        if (!(c instanceof EClass)) {
            throw new IllegalStateException("CWM EClass not found: " + name + " in " + pkg.getName());
        }
        return (EClass) c;
    }

    private static EDataType eDataType(EPackage pkg, String name) {
        EClassifier c = pkg.getEClassifier(name);
        if (!(c instanceof EDataType)) {
            throw new IllegalStateException("CWM EDataType not found: " + name + " in " + pkg.getName());
        }
        return (EDataType) c;
    }


    public static void save(EPackage pkg, ResourceSet rs, URI outUri) throws IOException {
        Resource out = rs.createResource(outUri);
        out.getContents().add(pkg);
        Map<Object, Object> opts = new HashMap<>();
        opts.put(XMLResource.OPTION_ENCODING, "UTF-8");
        opts.put(XMLResource.OPTION_LINE_WIDTH, Integer.valueOf(96));
        out.save(opts);
        injectLicenseHeader(outUri);
    }

    /** Inserts the EPL-2.0 XML comment header after the XML declaration. */
    private static void injectLicenseHeader(URI outUri) throws IOException {
        String path = outUri.toFileString();
        if (path == null) {
            return;
        }
        java.nio.file.Path file = java.nio.file.Path.of(path);
        String content = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
        if (content.contains("<!--\n/****")) {
            return;
        }
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
        if (nl < 0) {
            return;
        }
        String result = content.substring(0, nl + 1) + comment + content.substring(nl + 1);
        java.nio.file.Files.writeString(file, result, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static ResourceSet newResourceSet() {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
        return rs;
    }


    /** Creates the EPackage {@code dcat}: 14 classes, 2 enumerations, no subpackages. */
    public EPackage build() {
        EPackage p = ef.createEPackage();
        p.setName("dcat");
        p.setNsURI(BASE_NS);
        p.setNsPrefix("dcat");
        EAnnotation pa = annotate(p,
                "W3C DCAT 3 (Data Catalog Vocabulary, REC 2024) als CWM-verankerte "
                        + "Daanse-Erweiterung: Kataloge beschreiben Datensaetze, deren Inhalt bereits als "
                        + "CWM-Modell vorliegt — Dataset.subject referenziert beliebige core::ModelElements "
                        + "(relational::Schema, json::ObjectType, einzelne Tabellen), Distributionen "
                        + "beschreiben die physischen Zugriffsformen, DataServices die APIs. Agenten sind "
                        + "businessinformation::ResponsibleParty, Themen verweisen auf "
                        + "businessnomenclature::Concept und/oder EU-Vokabular-IRIs. Kontrollierte "
                        + "Vokabulare (data-theme, frequency, access-right, ...) sind bewusst als "
                        + "IRI-Strings verflacht (Suffix Iri). DCAT-Inverse (seriesMember, inCatalog, "
                        + "nextVersion, ...) sind die Property.oppositeRoleName-Annotationen der "
                        + "versteckten Gegenrichtungen — Rueckwaertsnavigation ueber InverseReferences.",
                "W3C DCAT 3 (Data Catalog Vocabulary, REC 2024) as a CWM-anchored Daanse extension: "
                        + "catalogs describe datasets whose content already exists as a CWM model — "
                        + "Dataset.subject references arbitrary core::ModelElements (relational::Schema, "
                        + "json::ObjectType, single tables), distributions describe the physical access "
                        + "forms, data services the APIs. Agents are businessinformation::ResponsibleParty, "
                        + "themes point at businessnomenclature::Concept and/or EU vocabulary IRIs. "
                        + "Controlled vocabularies (data-theme, frequency, access-right, ...) are "
                        + "deliberately flattened to IRI strings (suffix Iri). DCAT inverses "
                        + "(seriesMember, inCatalog, nextVersion, ...) are the Property.oppositeRoleName "
                        + "annotations of the hidden reverse directions — backward navigation via "
                        + "InverseReferences.",
                "Datenkatalog (DCAT)", "Data Catalog (DCAT)");
        pa.getDetails().put("basePackage", "org.eclipse.daanse.cwm.model.daanse");
        pa.getDetails().put("prefix", "Dcat");
        pa.getDetails().put("oSGiCompatible", "true");
        pa.getDetails().put("literalsInterface", "true");
        pa.getDetails().put("loadInitialization", "false");
        pa.getDetails().put("copyrightText", EPL_HEADER);

        // OCL delegates for the declared constraints.
        EAnnotation vd = ef.createEAnnotation();
        vd.setSource(ECORE_ANN);
        vd.getDetails().put("invocationDelegates", ECORE_OCL);
        vd.getDetails().put("settingDelegates", ECORE_OCL);
        vd.getDetails().put("validationDelegates", ECORE_OCL);
        p.getEAnnotations().add(vd);

        // Non-containment references are hidden association ends.
        EAnnotation ho = ef.createEAnnotation();
        ho.setSource(ECORE_OCL);
        ho.getDetails().put("hiddenOpposites", "true");
        p.getEAnnotations().add(ho);

        // -- Enums --------------------------------------------------------

        EEnum agentRole = enm(p, "AgentRole",
                "Rolle einer ResponsibleParty gegenueber einer katalogisierten Ressource. Deckt "
                        + "dcterms:publisher/creator/contributor, dcat:contactPoint, dcterms:rightsHolder "
                        + "sowie prov:qualifiedAttribution+dcat:hadRole ab; ORIGINATOR/CUSTODIAN "
                        + "(geodcatap) und MAINTAINER liegen fuer kommende Profile bereits im Basis-Enum "
                        + "(EEnums sind nicht nachtraeglich erweiterbar). OTHER + Attribution.roleIri ist "
                        + "das offene Ende fuer beliebige prov-Rollen.",
                "Role of a ResponsibleParty towards a cataloged resource. Covers "
                        + "dcterms:publisher/creator/contributor, dcat:contactPoint, dcterms:rightsHolder "
                        + "and prov:qualifiedAttribution+dcat:hadRole; ORIGINATOR/CUSTODIAN (geodcatap) "
                        + "and MAINTAINER are already in the base enum for future profiles (EEnums cannot "
                        + "be extended later). OTHER + Attribution.roleIri is the open end for arbitrary "
                        + "prov roles.",
                "Agentenrolle", "Agent Role",
                "PUBLISHER", "CREATOR", "CONTRIBUTOR", "CONTACT_POINT", "RIGHTS_HOLDER",
                "ORIGINATOR", "CUSTODIAN", "MAINTAINER", "OTHER");

        EEnum checksumAlgorithm = enm(p, "ChecksumAlgorithm",
                "Pruefsummen-Algorithmus nach SPDX (spdx:ChecksumAlgorithm), wie von DCAT 3 fuer "
                        + "spdx:Checksum referenziert.",
                "Checksum algorithm per SPDX (spdx:ChecksumAlgorithm) as referenced by DCAT 3 for "
                        + "spdx:Checksum.",
                "Pruefsummen-Algorithmus", "Checksum Algorithm",
                "SHA1", "SHA224", "SHA256", "SHA384", "SHA512", "SHA3_256", "SHA3_384", "SHA3_512",
                "MD5", "BLAKE2B_256", "BLAKE2B_384", "BLAKE2B_512", "BLAKE3", "ADLER32");

        // -- Classes first (they refer to each other) ----------------------

        EClass dcatElement = cls(p, "DcatElement", true,
                "Abstrakte lokale Wurzel der Katalogwelt (Muster SqlElement): die von "
                        + "CatalogedResource, Distribution und CatalogRecord geteilten Metadaten — "
                        + "Beschreibung, Lebenszyklusdaten, Lizenz und Rechte. Der Titel "
                        + "(dcterms:title) ist der geerbte name (core::ModelElement.name); description "
                        + "traegt die Primaersprache; weitere Sprachfassungen haengen als "
                        + "businessinformation::Description (type=title|description, language gesetzt) "
                        + "am Element, weitere Dokumentationsseiten als businessinformation::Document.",
                "Abstract local root of the catalog world (SqlElement pattern): the metadata shared "
                        + "by CatalogedResource, Distribution and CatalogRecord — description, "
                        + "lifecycle dates, license and rights. The title (dcterms:title) is the "
                        + "inherited name (core::ModelElement.name); description carries the primary "
                        + "language; further language versions attach as "
                        + "businessinformation::Description (type=title|description, language set), "
                        + "further documentation pages as businessinformation::Document.",
                "DCAT-Element", "DCAT Element");
        dcatElement.getESuperTypes().add(cwm.modelElement);

        EClass catalogedResource = cls(p, "CatalogedResource", true,
                "dcat:Resource — eine katalogisierte Ressource (Dataset, DataService, Katalog). "
                        + "Traegt Identifikation, Vokabular-IRIs, Schlagworte/Themen, Attributionen, "
                        + "qualifizierte Relationen sowie die DCAT-3-Versions- und Ordnungsbezuege. Die "
                        + "Gegenrichtungen (inCatalog, nextVersion, seriesMember, ...) sind versteckte "
                        + "Rollen — Navigation ueber InverseReferences.",
                "dcat:Resource — a cataloged resource (dataset, data service, catalog). Carries "
                        + "identification, vocabulary IRIs, keywords/themes, attributions, qualified "
                        + "relations and the DCAT 3 versioning and ordering links. Reverse directions "
                        + "(inCatalog, nextVersion, seriesMember, ...) are hidden roles — navigate via "
                        + "InverseReferences.",
                "Katalogisierte Ressource", "Cataloged Resource");
        catalogedResource.getESuperTypes().add(dcatElement);

        EClass dataset = cls(p, "Dataset", false,
                "dcat:Dataset — eine als Einheit publizierte Datensammlung. Der Kern der "
                        + "CWM-Verankerung: subject referenziert die bereits modellierten CWM-Elemente "
                        + "(relational::Schema, eine einzelne Table, json::ObjectType, calc::Sheet, ...), "
                        + "die dieser Datensatz publiziert — \"diese Klasse publiziere ich als Datensatz "
                        + "mit dieser Beschreibung\". Physische Zugriffsformen sind eigene "
                        + "Distribution-Kinder.",
                "dcat:Dataset — a collection of data published as a unit. The core of the CWM "
                        + "anchoring: subject references the already-modeled CWM elements "
                        + "(relational::Schema, a single Table, json::ObjectType, calc::Sheet, ...) this "
                        + "dataset publishes — \"I publish this class as a dataset with this "
                        + "description\". Physical access forms are owned Distribution children.",
                "Datensatz", "Dataset");
        dataset.getESuperTypes().add(catalogedResource);

        EClass datasetSeries = cls(p, "DatasetSeries", false,
                "dcat:DatasetSeries (neu in DCAT 3) — ein Datensatz, der andere Datensaetze als "
                        + "Serie buendelt (Zeitreihen, Kartenwerke). Keine eigenen Merkmale; Mitglieder "
                        + "zeigen per inSeries hierher (Gegenrolle seriesMember), ihre Ordnung ueber "
                        + "first/prev/last. Eine Serie soll selbst keine Distributionen tragen "
                        + "(SeriesHasNoDistribution).",
                "dcat:DatasetSeries (new in DCAT 3) — a dataset that groups other datasets into a "
                        + "series (time series, map series). No own features; members point here via "
                        + "inSeries (reverse role seriesMember), their order via first/prev/last. A "
                        + "series should not carry distributions itself (SeriesHasNoDistribution).",
                "Datensatzserie", "Dataset Series");
        datasetSeries.getESuperTypes().add(dataset);

        EClass catalog = cls(p, "Catalog", false,
                "dcat:Catalog — eine kuratierte Sammlung von Metadaten ueber Ressourcen. DCAT-treu "
                        + "ein Dataset und zugleich core::Package (Vorbild core::Subsystem): als Package "
                        + "kann der Katalog heimatlose Datensaetze und Traegerobjekte (ResponsibleParties, "
                        + "Taxonomien) als ownedElement besitzen; die kanonische Mitgliedschaft ist aber "
                        + "die Referenz resource — Datensaetze leben in der Regel bei den Ressourcenmodellen, "
                        + "die sie beschreiben. dataset/service/catalog-Teilmengen sind ableitbar "
                        + "(Util-Filter), keine eigenen Features.",
                "dcat:Catalog — a curated collection of metadata about resources. Faithful to DCAT it "
                        + "is a Dataset and at the same time a core::Package (precedent core::Subsystem): "
                        + "as a Package the catalog may own homeless datasets and support objects "
                        + "(ResponsibleParties, taxonomies) as ownedElement; canonical membership however "
                        + "is the resource reference — datasets usually live next to the resource models "
                        + "they describe. The dataset/service/catalog subsets are derivable (util "
                        + "filters), not separate features.",
                "Katalog", "Catalog");
        catalog.getESuperTypes().add(dataset);
        catalog.getESuperTypes().add(cwm.cwmPackage);

        EClass dataService = cls(p, "DataService", false,
                "dcat:DataService — ein Dienst/API, der Zugriff auf Datensaetze bietet. "
                        + "endpointUrl/endpointDescription sind eigene Merkmale (CWM SoftwareDeployment "
                        + "kennt keine URL); provider verlinkt optional die deployte Komponente "
                        + "(softwaredeployment::DataProvider) fuer die Betriebssicht.",
                "dcat:DataService — a service/API offering access to datasets. "
                        + "endpointUrl/endpointDescription are own features (CWM SoftwareDeployment has "
                        + "no URL anywhere); provider optionally links the deployed component "
                        + "(softwaredeployment::DataProvider) for the operations view.",
                "Datendienst", "Data Service");
        dataService.getESuperTypes().add(catalogedResource);

        EClass distribution = cls(p, "Distribution", false,
                "dcat:Distribution — eine konkrete Zugriffsform eines Datensatzes (Datei-Download, "
                        + "API-Sicht, Medientyp, Groesse, Pruefsumme). Existiert nur als Kind seines "
                        + "Datensatzes (Dataset.distribution); dcat:isDistributionOf ist der Container.",
                "dcat:Distribution — a concrete access form of a dataset (file download, API view, "
                        + "media type, size, checksum). Exists only as a child of its dataset "
                        + "(Dataset.distribution); dcat:isDistributionOf is the container.",
                "Distribution", "Distribution");
        distribution.getESuperTypes().add(dcatElement);

        EClass catalogRecord = cls(p, "CatalogRecord", false,
                "dcat:CatalogRecord — der Katalogeintrag als eigenes Objekt: Metadaten ueber die "
                        + "Registrierung (Listungsdatum = issued, Aenderungsdatum, Metadatenprofil = "
                        + "conformsToIri), getrennt von den Metadaten der Ressource selbst. Kind des "
                        + "Katalogs (Catalog.record).",
                "dcat:CatalogRecord — the catalog entry as its own object: metadata about the "
                        + "registration (listing date = issued, modification date, metadata profile = "
                        + "conformsToIri), separate from the metadata of the resource itself. Child of "
                        + "the catalog (Catalog.record).",
                "Katalogeintrag", "Catalog Record");
        catalogRecord.getESuperTypes().add(dcatElement);

        EClass attribution = cls(p, "Attribution", false,
                "Zustaendigkeit einer ResponsibleParty fuer eine Ressource in genau einer Rolle — "
                        + "vereinigt dcterms:publisher/creator/contributor, dcat:contactPoint, "
                        + "dcterms:rightsHolder und prov:qualifiedAttribution+dcat:hadRole in einer "
                        + "Form (agent, role). Kontaktdaten (E-Mail, Telefon, URL) bringt die "
                        + "ResponsibleParty ueber businessinformation::Contact mit.",
                "Responsibility of a ResponsibleParty for a resource in exactly one role — unifies "
                        + "dcterms:publisher/creator/contributor, dcat:contactPoint, "
                        + "dcterms:rightsHolder and prov:qualifiedAttribution+dcat:hadRole in one shape "
                        + "(agent, role). Contact data (email, telephone, URL) comes with the "
                        + "ResponsibleParty via businessinformation::Contact.",
                "Attribution", "Attribution");
        attribution.getESuperTypes().add(cwm.modelElement);

        EClass relationship = cls(p, "Relationship", false,
                "dcat:Relationship — qualifizierte Beziehung zu anderen Ressourcen, wenn keine "
                        + "dcterms-Property passt: Ziel(e) plus Rolle als IRI (ISO 19115, DataCite, IANA "
                        + "Link Relations) und/oder businessnomenclature::Concept.",
                "dcat:Relationship — qualified relation to other resources when no dcterms property "
                        + "fits: target(s) plus role as IRI (ISO 19115, DataCite, IANA link relations) "
                        + "and/or businessnomenclature::Concept.",
                "Qualifizierte Beziehung", "Qualified Relationship");
        relationship.getESuperTypes().add(cwm.modelElement);

        EClass periodOfTime = cls(p, "PeriodOfTime", false,
                "dcterms:PeriodOfTime — zeitliche Abdeckung eines Datensatzes; offene Intervalle "
                        + "(nur Anfang oder nur Ende) sind erlaubt.",
                "dcterms:PeriodOfTime — temporal coverage of a dataset; open intervals (start only "
                        + "or end only) are allowed.",
                "Zeitraum", "Period of Time");
        periodOfTime.getESuperTypes().add(cwm.element);

        EClass location = cls(p, "Location", false,
                "dcterms:Location — raeumliche Abdeckung: Gazetteer-IRI (Geonames, EU-Orte) und/oder "
                        + "Geometrie, Begrenzungsrahmen und Zentroid als WKT-Literale (dcat:bbox, "
                        + "dcat:centroid, locn:geometry).",
                "dcterms:Location — spatial coverage: gazetteer IRI (Geonames, EU places) and/or "
                        + "geometry, bounding box and centroid as WKT literals (dcat:bbox, "
                        + "dcat:centroid, locn:geometry).",
                "Ort", "Location");
        location.getESuperTypes().add(cwm.element);

        EClass checksum = cls(p, "Checksum", false,
                "spdx:Checksum (neu in DCAT 3) — Integritaetspruefsumme einer Distribution, bezogen "
                        + "auf deren downloadUrl.",
                "spdx:Checksum (new in DCAT 3) — integrity checksum of a distribution, related to "
                        + "its downloadUrl.",
                "Pruefsumme", "Checksum");
        checksum.getESuperTypes().add(cwm.element);

        // -- DcatElement: features ----------------------------------------

        attr(dcatElement, "description", cwm.stringType, 0, 1,
                "dcterms:description in der Primaersprache; weitere Sprachen als "
                        + "businessinformation::Description (type=description).",
                "dcterms:description in the primary language; further languages as "
                        + "businessinformation::Description (type=description).");
        attr(dcatElement, "issued", cwm.timeType, 0, 1,
                "dcterms:issued — Veroeffentlichungsdatum (ISO 8601); beim CatalogRecord das "
                        + "Listungsdatum.",
                "dcterms:issued — release date (ISO 8601); for a CatalogRecord the listing date.");
        attr(dcatElement, "modified", cwm.timeType, 0, 1,
                "dcterms:modified — Datum der letzten Aenderung (ISO 8601).",
                "dcterms:modified — date of last change (ISO 8601).");
        attr(dcatElement, "licenseIri", cwm.stringType, 0, 1,
                "dcterms:license als IRI (z. B. EU-Lizenzvokabular, dcat-ap.de/def/licenses). CWM "
                        + "kennt keine Lizenzverankerung; Volltexte haengen als "
                        + "businessinformation::Document am Element.",
                "dcterms:license as IRI (e.g. EU license vocabulary, dcat-ap.de/def/licenses). CWM "
                        + "has no licensing anchor; full texts attach as businessinformation::Document.");
        attr(dcatElement, "accessRightsIri", cwm.stringType, 0, 1,
                "dcterms:accessRights als IRI (EU access-right: PUBLIC/RESTRICTED/NON_PUBLIC).",
                "dcterms:accessRights as IRI (EU access-right: PUBLIC/RESTRICTED/NON_PUBLIC).");
        attr(dcatElement, "rights", cwm.stringType, 0, 1,
                "dcterms:rights — Rechtehinweis als Freitext oder IRI eines RightsStatement.",
                "dcterms:rights — rights statement as free text or IRI of a RightsStatement.");
        attr(dcatElement, "policyIri", cwm.stringType, 0, -1,
                "odrl:hasPolicy — IRIs von ODRL-Nutzungsrichtlinien.",
                "odrl:hasPolicy — IRIs of ODRL usage policies.");
        attr(dcatElement, "conformsToIri", cwm.stringType, 0, -1,
                "dcterms:conformsTo — IRIs der Standards/Schemata/Profile, denen der Inhalt folgt; "
                        + "beim CatalogRecord das Metadatenprofil des Eintrags.",
                "dcterms:conformsTo — IRIs of the standards/schemata/profiles the content follows; "
                        + "for a CatalogRecord the metadata profile of the entry.");

        // -- CatalogedResource: features ----------------------------------

        attr(catalogedResource, "identifier", cwm.stringType, 0, -1,
                "dcterms:identifier — Hauptbezeichner (URN, DOI, Portal-ID, ...).",
                "dcterms:identifier — primary identifiers (URN, DOI, portal id, ...).");
        attr(catalogedResource, "otherIdentifier", cwm.stringType, 0, -1,
                "adms:identifier, verflacht auf die Notation — weitere Bezeichner aus anderen "
                        + "Systemen.",
                "adms:identifier flattened to the notation — further identifiers from other systems.");
        attr(catalogedResource, "language", cwm.stringType, 0, -1,
                "dcterms:language — EU-Sprach-IRI oder ISO-639-Code des Inhalts.",
                "dcterms:language — EU language IRI or ISO 639 code of the content.");
        attr(catalogedResource, "typeIri", cwm.stringType, 0, -1,
                "dcterms:type — Genre/Art der Ressource als IRI (z. B. EU dataset-type).",
                "dcterms:type — genre/kind of the resource as IRI (e.g. EU dataset-type).");
        attr(catalogedResource, "statusIri", cwm.stringType, 0, 1,
                "adms:status als IRI (EU distribution-status bzw. ADMS-Statusliste).",
                "adms:status as IRI (EU distribution-status or ADMS status list).");
        attr(catalogedResource, "version", cwm.stringType, 0, 1,
                "dcat:version (neu in DCAT 3) — Versionsbezeichner dieser Ressource.",
                "dcat:version (new in DCAT 3) — version designator of this resource.");
        attr(catalogedResource, "versionNotes", cwm.stringType, 0, -1,
                "adms:versionNotes — Aenderungsnotizen zu dieser Version.",
                "adms:versionNotes — change notes for this version.");
        attr(catalogedResource, "keyword", cwm.stringType, 0, -1,
                "dcat:keyword — freie Schlagworte.",
                "dcat:keyword — free-text keywords.");
        attr(catalogedResource, "landingPage", cwm.stringType, 0, -1,
                "dcat:landingPage — Webseite(n) mit Zugang zur Ressource; weitere foaf:pages als "
                        + "businessinformation::Document.",
                "dcat:landingPage — web page(s) giving access to the resource; further foaf:pages as "
                        + "businessinformation::Document.");
        attr(catalogedResource, "themeIri", cwm.stringType, 0, -1,
                "dcat:theme als IRI des EU-data-theme-Vokabulars (bzw. eines anderen "
                        + "skos:ConceptScheme) — die Form, die Exporter und Portale brauchen.",
                "dcat:theme as IRI of the EU data-theme vocabulary (or another skos:ConceptScheme) — "
                        + "the form exporters and portals need.");
        EReference theme = ref(catalogedResource, "theme", cwm.concept, 0, -1,
                "dcat:theme als repo-interne SKOS-Verankerung: businessnomenclature::Concept einer "
                        + "lokalen Taxonomie.",
                "dcat:theme as in-repo SKOS anchor: businessnomenclature::Concept of a local "
                        + "taxonomy.");
        oppositeRole(theme, "themedResource");
        cont(catalogedResource, "attribution", attribution, 0, -1,
                "Zustaendigkeiten: publisher/creator/contactPoint/... als (agent, role)-Paare.",
                "Responsibilities: publisher/creator/contactPoint/... as (agent, role) pairs.");
        cont(catalogedResource, "qualifiedRelation", relationship, 0, -1,
                "dcat:qualifiedRelation — qualifizierte Beziehungen zu anderen Ressourcen.",
                "dcat:qualifiedRelation — qualified relations to other resources.");
        EReference relation = ref(catalogedResource, "relation", cwm.modelElement, 0, -1,
                "dcterms:relation — generischer Bezug auf verwandte Elemente.",
                "dcterms:relation — generic link to related elements.");
        oppositeRole(relation, "relatedResource");
        EReference hasPart = ref(catalogedResource, "hasPart", catalogedResource, 0, -1,
                "dcterms:hasPart — enthaltene Teilressourcen (lose Kollektion, kein Containment).",
                "dcterms:hasPart — contained part resources (loose collection, no containment).");
        oppositeRole(hasPart, "isPartOf");
        EReference isReferencedBy = ref(catalogedResource, "isReferencedBy", cwm.modelElement, 0, -1,
                "dcterms:isReferencedBy — Publikationen/Elemente, die diese Ressource zitieren "
                        + "(Datenzitation).",
                "dcterms:isReferencedBy — publications/elements citing this resource (data "
                        + "citation).");
        oppositeRole(isReferencedBy, "references");
        EReference previousVersion = ref(catalogedResource, "previousVersion", catalogedResource, 0, 1,
                "dcat:previousVersion (neu in DCAT 3) — der unmittelbare Vorgaenger in der "
                        + "Versionslinie (nicht die Serienposition, das ist prev).",
                "dcat:previousVersion (new in DCAT 3) — the immediate predecessor in the version "
                        + "lineage (not the series position, that is prev).");
        oppositeRole(previousVersion, "nextVersion");
        EReference hasVersion = ref(catalogedResource, "hasVersion", catalogedResource, 0, -1,
                "dcat:hasVersion (neu in DCAT 3) — Versionen dieser Ressource.",
                "dcat:hasVersion (new in DCAT 3) — versions of this resource.");
        oppositeRole(hasVersion, "isVersionOf");
        EReference hasCurrentVersion = ref(catalogedResource, "hasCurrentVersion", catalogedResource, 0, 1,
                "dcat:hasCurrentVersion (neu in DCAT 3) — die aktuelle Version.",
                "dcat:hasCurrentVersion (new in DCAT 3) — the current version.");
        oppositeRole(hasCurrentVersion, "isCurrentVersionOf");
        EReference replaces = ref(catalogedResource, "replaces", catalogedResource, 0, -1,
                "dcterms:replaces — Ressourcen, die diese abloest.",
                "dcterms:replaces — resources this one supersedes.");
        oppositeRole(replaces, "isReplacedBy");
        EReference first = ref(catalogedResource, "first", catalogedResource, 0, 1,
                "dcat:first — erstes Mitglied der geordneten Kollektion/Serie.",
                "dcat:first — first member of the ordered collection/series.");
        oppositeRole(first, "isFirstOf");
        EReference last = ref(catalogedResource, "last", catalogedResource, 0, 1,
                "dcat:last — letztes Mitglied der geordneten Kollektion/Serie.",
                "dcat:last — last member of the ordered collection/series.");
        oppositeRole(last, "isLastOf");
        EReference prev = ref(catalogedResource, "prev", catalogedResource, 0, 1,
                "dcat:prev — Vorgaenger in der geordneten Kollektion/Serie (Position, nicht "
                        + "Versionslinie).",
                "dcat:prev — predecessor in the ordered collection/series (position, not version "
                        + "lineage).");
        oppositeRole(prev, "next");

        // -- Dataset: features --------------------------------------------

        EReference subject = ref(dataset, "subject", cwm.modelElement, 0, -1,
                "Der CWM-Anker: die bereits modellierten Elemente, die dieser Datensatz publiziert "
                        + "(relational::Schema, eine Table, json::ObjectType, calc::Sheet, ...). "
                        + "Rueckrichtung dataset via InverseReferences: \"als welche Datensaetze ist "
                        + "dieses Element publiziert?\".",
                "The CWM anchor: the already-modeled elements this dataset publishes "
                        + "(relational::Schema, a Table, json::ObjectType, calc::Sheet, ...). Reverse "
                        + "role dataset via InverseReferences: \"as which datasets is this element "
                        + "published?\".");
        oppositeRole(subject, "dataset");
        cont(dataset, "distribution", distribution, 0, -1,
                "dcat:distribution — die physischen Zugriffsformen; dcat:isDistributionOf ist der "
                        + "Container.",
                "dcat:distribution — the physical access forms; dcat:isDistributionOf is the "
                        + "container.");
        attr(dataset, "accrualPeriodicityIri", cwm.stringType, 0, 1,
                "dcterms:accrualPeriodicity — Aktualisierungsfrequenz als IRI des "
                        + "EU-frequency-Vokabulars.",
                "dcterms:accrualPeriodicity — update frequency as IRI of the EU frequency "
                        + "vocabulary.");
        EReference inSeries = ref(dataset, "inSeries", datasetSeries, 0, -1,
                "dcat:inSeries (neu in DCAT 3) — Serien, zu denen dieser Datensatz gehoert; "
                        + "Gegenrolle seriesMember.",
                "dcat:inSeries (new in DCAT 3) — series this dataset belongs to; reverse role "
                        + "seriesMember.");
        oppositeRole(inSeries, "seriesMember");
        cont(dataset, "spatial", location, 0, -1,
                "dcterms:spatial — raeumliche Abdeckung.",
                "dcterms:spatial — spatial coverage.");
        attr(dataset, "spatialResolutionInMeters", cwm.floatType, 0, 1,
                "dcat:spatialResolutionInMeters — kleinster aufgeloester Abstand in Metern.",
                "dcat:spatialResolutionInMeters — minimum resolvable distance in meters.");
        cont(dataset, "temporal", periodOfTime, 0, -1,
                "dcterms:temporal — zeitliche Abdeckung.",
                "dcterms:temporal — temporal coverage.");
        attr(dataset, "temporalResolution", cwm.stringType, 0, 1,
                "dcat:temporalResolution — kleinster aufgeloester Zeitabstand als "
                        + "xsd:duration-Literal (core::Time ist ein Zeitpunkt, keine Dauer).",
                "dcat:temporalResolution — minimum resolvable time period as an xsd:duration "
                        + "literal (core::Time is an instant, not a duration).");
        EReference wasGeneratedBy = ref(dataset, "wasGeneratedBy", cwm.modelElement, 0, -1,
                "prov:wasGeneratedBy — die erzeugende Aktivitaet. Bewusst generisch typisiert; "
                        + "kanonische Ziele sind transformation::TransformationActivity und "
                        + "warehouseoperation::TransformationExecution (so bleibt deren Modul-Huelle "
                        + "aus dem pom der Konsumenten).",
                "prov:wasGeneratedBy — the generating activity. Deliberately generically typed; "
                        + "canonical targets are transformation::TransformationActivity and "
                        + "warehouseoperation::TransformationExecution (keeping their module hull out "
                        + "of consumers' poms).");
        oppositeRole(wasGeneratedBy, "generated");

        // -- Catalog: features --------------------------------------------

        attr(catalog, "homepage", cwm.stringType, 0, 1,
                "foaf:homepage — die Startseite des Katalogs.",
                "foaf:homepage — the catalog's home page.");
        EReference themeTaxonomy = ref(catalog, "themeTaxonomy", cwm.taxonomy, 0, -1,
                "dcat:themeTaxonomy als repo-interne Verankerung: businessnomenclature::Taxonomy, "
                        + "deren Concepts die Themen liefern.",
                "dcat:themeTaxonomy as in-repo anchor: businessnomenclature::Taxonomy whose "
                        + "concepts provide the themes.");
        oppositeRole(themeTaxonomy, "catalog");
        attr(catalog, "themeTaxonomyIri", cwm.stringType, 0, -1,
                "dcat:themeTaxonomy als IRI eines externen skos:ConceptScheme (z. B. EU "
                        + "data-theme).",
                "dcat:themeTaxonomy as IRI of an external skos:ConceptScheme (e.g. EU data-theme).");
        EReference resource = ref(catalog, "resource", catalogedResource, 0, -1,
                "dcat:resource — die kanonische Mitgliedschaft: der Katalog listet Ressourcen, die "
                        + "strukturell bei ihren Ressourcenmodellen leben; Gegenrolle inCatalog. "
                        + "dataset/service/catalog-Teilmengen per Util-Filter.",
                "dcat:resource — canonical membership: the catalog lists resources that structurally "
                        + "live next to their resource models; reverse role inCatalog. The "
                        + "dataset/service/catalog subsets via util filters.");
        oppositeRole(resource, "inCatalog");
        cont(catalog, "record", catalogRecord, 0, -1,
                "dcat:record — die Katalogeintraege; Eintraege sind an den Lebenszyklus des "
                        + "Katalogs gebunden (Containment).",
                "dcat:record — the catalog records; records are bound to the catalog's lifecycle "
                        + "(containment).");

        // -- DataService: features ----------------------------------------

        attr(dataService, "endpointUrl", cwm.stringType, 1, -1,
                "dcat:endpointURL — Wurzel-Endpunkt(e) des Dienstes.",
                "dcat:endpointURL — root endpoint(s) of the service.");
        attr(dataService, "endpointDescription", cwm.stringType, 0, -1,
                "dcat:endpointDescription — maschinenlesbare Beschreibung (OpenAPI, OGC "
                        + "GetCapabilities, SPARQL Service Description, WSDL) als URL oder inline.",
                "dcat:endpointDescription — machine-readable description (OpenAPI, OGC "
                        + "GetCapabilities, SPARQL service description, WSDL) as URL or inline.");
        EReference servesDataset = ref(dataService, "servesDataset", dataset, 0, -1,
                "dcat:servesDataset — die Datensaetze, die dieser Dienst ausliefert; Gegenrolle "
                        + "dataService.",
                "dcat:servesDataset — the datasets this service serves; reverse role dataService.");
        oppositeRole(servesDataset, "dataService");
        EReference provider = ref(dataService, "provider", cwm.dataProvider, 0, 1,
                "Optionale Bruecke zur Betriebssicht: die deployte Zugriffskomponente "
                        + "(softwaredeployment::DataProvider mit ProviderConnections).",
                "Optional bridge to the operations view: the deployed access component "
                        + "(softwaredeployment::DataProvider with its ProviderConnections).");
        oppositeRole(provider, "catalogedService");

        // -- Distribution: features ---------------------------------------

        attr(distribution, "accessUrl", cwm.stringType, 1, -1,
                "dcat:accessURL — Zugangspunkt(e): Downloadseite, API-Einstieg, Formular. Bei "
                        + "direktem Dateizugriff zusaetzlich downloadUrl setzen.",
                "dcat:accessURL — access point(s): download page, API entry, form. For direct file "
                        + "access also set downloadUrl.");
        attr(distribution, "downloadUrl", cwm.stringType, 0, -1,
                "dcat:downloadURL — direkte Download-Adresse(n) der Datei.",
                "dcat:downloadURL — direct download address(es) of the file.");
        attr(distribution, "mediaType", cwm.stringType, 0, 1,
                "dcat:mediaType — IANA-Medientyp (z. B. text/csv).",
                "dcat:mediaType — IANA media type (e.g. text/csv).");
        attr(distribution, "formatIri", cwm.stringType, 0, 1,
                "dcterms:format — Dateiformat als IRI (EU file-type), wenn kein IANA-Medientyp "
                        + "passt.",
                "dcterms:format — file format as IRI (EU file-type) when no IANA media type fits.");
        attr(distribution, "compressFormat", cwm.stringType, 0, 1,
                "dcat:compressFormat — Medientyp der Kompression (z. B. application/gzip).",
                "dcat:compressFormat — media type of the compression (e.g. application/gzip).");
        attr(distribution, "packageFormat", cwm.stringType, 0, 1,
                "dcat:packageFormat — Medientyp der Paketierung (z. B. application/zip), wenn "
                        + "mehrere Dateien gebuendelt sind.",
                "dcat:packageFormat — media type of the packaging (e.g. application/zip) when "
                        + "several files are bundled.");
        attr(distribution, "byteSize", cwm.integerType, 0, 1,
                "dcat:byteSize — Groesse in Bytes (core::Integer = long).",
                "dcat:byteSize — size in bytes (core::Integer = long).");
        attr(distribution, "spatialResolutionInMeters", cwm.floatType, 0, 1,
                "dcat:spatialResolutionInMeters — kleinster aufgeloester Abstand in Metern.",
                "dcat:spatialResolutionInMeters — minimum resolvable distance in meters.");
        attr(distribution, "temporalResolution", cwm.stringType, 0, 1,
                "dcat:temporalResolution — kleinster aufgeloester Zeitabstand als "
                        + "xsd:duration-Literal.",
                "dcat:temporalResolution — minimum resolvable time period as an xsd:duration "
                        + "literal.");
        cont1(distribution, "checksum", checksum,
                "spdx:checksum (neu in DCAT 3) — Pruefsumme, bezogen auf downloadUrl.",
                "spdx:checksum (new in DCAT 3) — checksum, related to downloadUrl.");
        EReference accessService = ref(distribution, "accessService", dataService, 0, -1,
                "dcat:accessService — Dienste, die diese Distribution ausliefern; Gegenrolle "
                        + "servedDistribution.",
                "dcat:accessService — services serving this distribution; reverse role "
                        + "servedDistribution.");
        oppositeRole(accessService, "servedDistribution");

        // -- CatalogRecord: features --------------------------------------

        EReference primaryTopic = ref(catalogRecord, "primaryTopic", catalogedResource, 1, 1,
                "foaf:primaryTopic — die eine Ressource, die dieser Eintrag beschreibt; Gegenrolle "
                        + "isPrimaryTopicOf.",
                "foaf:primaryTopic — the single resource this record describes; reverse role "
                        + "isPrimaryTopicOf.");
        oppositeRole(primaryTopic, "isPrimaryTopicOf");

        // -- Attribution: features ----------------------------------------

        EReference agent = ref(attribution, "agent", cwm.responsibleParty, 1, 1,
                "Die zustaendige businessinformation::ResponsibleParty (Person, Rolle oder "
                        + "Organisation); Gegenrolle attribution.",
                "The responsible businessinformation::ResponsibleParty (person, role or "
                        + "organization); reverse role attribution.");
        oppositeRole(agent, "attribution");
        attr(attribution, "role", agentRole, 1, 1,
                "Die Rolle des Agenten gegenueber der Ressource.",
                "The agent's role towards the resource.");
        attr(attribution, "roleIri", cwm.stringType, 0, 1,
                "Praezisierende Rollen-IRI (prov/ISO 19115); Pflicht bei role=OTHER.",
                "Refining role IRI (prov/ISO 19115); mandatory for role=OTHER.");

        // -- Relationship: features ---------------------------------------

        EReference relTarget = ref(relationship, "relation", cwm.modelElement, 1, -1,
                "dcterms:relation — Ziel(e) der qualifizierten Beziehung.",
                "dcterms:relation — target(s) of the qualified relationship.");
        oppositeRole(relTarget, "qualifiedRelationOf");
        attr(relationship, "roleIri", cwm.stringType, 0, -1,
                "dcat:hadRole als IRI (ISO 19115, DataCite, IANA Link Relations).",
                "dcat:hadRole as IRI (ISO 19115, DataCite, IANA link relations).");
        EReference relRole = ref(relationship, "role", cwm.concept, 0, -1,
                "dcat:hadRole als repo-interne Verankerung: businessnomenclature::Concept.",
                "dcat:hadRole as in-repo anchor: businessnomenclature::Concept.");
        oppositeRole(relRole, "relationshipRole");

        // -- PeriodOfTime / Location / Checksum: features ------------------

        attr(periodOfTime, "startDate", cwm.timeType, 0, 1,
                "dcat:startDate — Beginn des Zeitraums (ISO 8601).",
                "dcat:startDate — start of the period (ISO 8601).");
        attr(periodOfTime, "endDate", cwm.timeType, 0, 1,
                "dcat:endDate — Ende des Zeitraums (ISO 8601).",
                "dcat:endDate — end of the period (ISO 8601).");

        attr(location, "iri", cwm.stringType, 0, 1,
                "Gazetteer-IRI des Ortes (Geonames, EU Countries/Places, ATU).",
                "Gazetteer IRI of the place (Geonames, EU countries/places, ATU).");
        attr(location, "geometryWkt", cwm.stringType, 0, 1,
                "locn:geometry als WKT-Literal.",
                "locn:geometry as WKT literal.");
        attr(location, "bboxWkt", cwm.stringType, 0, 1,
                "dcat:bbox — Begrenzungsrahmen als WKT-Literal.",
                "dcat:bbox — bounding box as WKT literal.");
        attr(location, "centroidWkt", cwm.stringType, 0, 1,
                "dcat:centroid — Zentroid als WKT-Literal (POINT).",
                "dcat:centroid — centroid as WKT literal (POINT).");

        attr(checksum, "algorithm", checksumAlgorithm, 1, 1,
                "spdx:algorithm — der Pruefsummen-Algorithmus.",
                "spdx:algorithm — the checksum algorithm.");
        attr(checksum, "checksumValue", cwm.stringType, 1, 1,
                "spdx:checksumValue — Hexwert in Kleinbuchstaben.",
                "spdx:checksumValue — lowercase hex value.");

        // -- OCL constraints ----------------------------------------------

        constrain(catalogedResource, "SinglePublisher",
                "self.attribution->select(a | a.role = AgentRole::PUBLISHER)->size() <= 1",
                "A resource has at most one publisher attribution.", "error");
        constrain(dataset, "SpatialResolutionPositive",
                "self.spatialResolutionInMeters <> null "
                        + "implies self.spatialResolutionInMeters.toReal() > 0",
                "The spatial resolution, when given, is positive (core::Float is a decimal "
                        + "literal).", "error");
        constrain(datasetSeries, "SeriesHasNoDistribution",
                "self.distribution->isEmpty()",
                "A dataset series should not carry distributions itself (DCAT 3 SHOULD).", "warning");
        constrain(catalog, "RecordTopicIsListed",
                "self.record->forAll(r | self.resource->includes(r.primaryTopic) "
                        + "or self.ownedElement->includes(r.primaryTopic))",
                "Every catalog record describes a resource the catalog lists or owns.", "error");
        constrain(catalog, "NotOwnMember",
                "not self.resource->includes(self)",
                "A catalog does not list itself as a member.", "error");
        constrain(dataService, "EndpointUrlNotEmpty",
                "self.endpointUrl->forAll(u | u.size() > 0)",
                "Endpoint URLs are non-empty strings.", "error");
        constrain(distribution, "ByteSizeNonNegative",
                "self.byteSize >= 0",
                "The byte size is not negative (core::Integer is a primitive long; unset = 0).",
                "error");
        constrain(distribution, "SpatialResolutionPositive",
                "self.spatialResolutionInMeters <> null "
                        + "implies self.spatialResolutionInMeters.toReal() > 0",
                "The spatial resolution, when given, is positive (core::Float is a decimal "
                        + "literal).", "error");
        constrain(attribution, "OtherRoleNeedsIri",
                "self.role = AgentRole::OTHER implies self.roleIri <> null",
                "The open role OTHER requires a refining role IRI.", "error");
        constrain(relationship, "RelationshipHasRole",
                "self.roleIri->notEmpty() or self.role->notEmpty()",
                "A qualified relationship names at least one role (IRI or concept).", "error");
        constrain(periodOfTime, "StartBeforeEnd",
                "(self.startDate <> null and self.endDate <> null) "
                        + "implies self.startDate <= self.endDate",
                "When both bounds are given, the period starts no later than it ends "
                        + "(lexical comparison of ISO 8601 values).", "error");
        constrain(location, "LocationNotEmpty",
                "self.iri <> null or self.geometryWkt <> null or self.bboxWkt <> null "
                        + "or self.centroidWkt <> null",
                "A location carries at least one of gazetteer IRI, geometry, bounding box or "
                        + "centroid.", "error");
        constrain(checksum, "ChecksumValueNotEmpty",
                "self.checksumValue.size() > 0",
                "The checksum value is a non-empty hex string.", "error");

        return p;
    }

    // -- Helpers -----------------------------------------------------------

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

    /** An OCL invariant: Ecore constraints list, OCL body and Daanse metadata (name, description, level). */
    private void constrain(EClass c, String name, String ocl, String descriptionEn, String level) {
        EAnnotation ce = c.getEAnnotation(ECORE_ANN);
        if (ce == null) {
            ce = ef.createEAnnotation();
            ce.setSource(ECORE_ANN);
            c.getEAnnotations().add(ce);
        }
        String existing = ce.getDetails().get("constraints");
        ce.getDetails().put("constraints", existing == null ? name : existing + " " + name);
        EAnnotation oe = c.getEAnnotation(ECORE_OCL);
        if (oe == null) {
            oe = ef.createEAnnotation();
            oe.setSource(ECORE_OCL);
            c.getEAnnotations().add(oe);
        }
        oe.getDetails().put(name, ocl);
        EAnnotation me = c.getEAnnotation(CONSTRAINTS_NS);
        if (me == null) {
            me = ef.createEAnnotation();
            me.setSource(CONSTRAINTS_NS);
            c.getEAnnotations().add(me);
        }
        me.getDetails().put(name + ".name", name);
        me.getDetails().put(name + ".description", descriptionEn);
        me.getDetails().put(name + ".level", level);
    }

    private EClass cls(EPackage pkg, String name, boolean isAbstract, String de, String en, String capDe,
            String capEn) {
        EClass c = ef.createEClass();
        c.setName(name);
        c.setAbstract(isAbstract);
        pkg.getEClassifiers().add(c);
        annotate(c, de, en, capDe, capEn);
        return c;
    }

    private EAttribute attr(EClass owner, String name, EClassifier type, int lower, int upper,
            String de, String en) {
        EAttribute a = ef.createEAttribute();
        a.setName(name);
        a.setEType(type);
        a.setLowerBound(lower);
        a.setUpperBound(upper);
        if (upper != 1) {
            a.setOrdered(false);
        }
        owner.getEStructuralFeatures().add(a);
        annotate(a, de, en, null, null);
        return a;
    }

    private EReference ref(EClass owner, String name, EClassifier type, int lower, int upper,
            String de, String en) {
        EReference r = ef.createEReference();
        r.setName(name);
        r.setEType(type);
        r.setLowerBound(lower);
        r.setUpperBound(upper);
        r.setContainment(false);
        if (upper != 1) {
            r.setOrdered(false);
        }
        owner.getEStructuralFeatures().add(r);
        annotate(r, de, en, null, null);
        return r;
    }

    /** A containment reference of many values (unordered; no opposite role name: the inverse is the container). */
    private EReference cont(EClass owner, String name, EClassifier type, int lower, int upper,
            String de, String en) {
        EReference r = ref(owner, name, type, lower, upper, de, en);
        r.setContainment(true);
        return r;
    }

    /** A single-valued containment reference. */
    private EReference cont1(EClass owner, String name, EClassifier type, String de, String en) {
        EReference r = ref(owner, name, type, 0, 1, de, en);
        r.setContainment(true);
        return r;
    }

    private EEnum enm(EPackage pkg, String name, String de, String en, String capDe, String capEn,
            String... literals) {
        EEnum e = ef.createEEnum();
        e.setName(name);
        int value = 0;
        for (String lit : literals) {
            EEnumLiteral l = ef.createEEnumLiteral();
            l.setName(lit);
            l.setLiteral(lit);
            l.setValue(value++);
            e.getELiterals().add(l);
        }
        pkg.getEClassifiers().add(e);
        annotate(e, de, en, capDe, capEn);
        return e;
    }

    private EAnnotation annotate(EModelElement el, String de, String en, String capDe, String capEn) {
        EAnnotation a = ef.createEAnnotation();
        a.setSource(GENMODEL);
        if (en != null) {
            a.getDetails().put("documentation", en);
        }
        if (de != null) {
            a.getDetails().put("documentation.de", de);
        }
        if (capDe != null) {
            a.getDetails().put("caption.de", capDe);
        }
        if (capEn != null) {
            a.getDetails().put("caption.en", capEn);
        }
        el.getEAnnotations().add(a);
        return a;
    }
}
