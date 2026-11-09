/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.dcat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.analysis.businessnomenclature.BusinessnomenclatureFactory;
import org.eclipse.daanse.cwm.model.cwm.analysis.businessnomenclature.Concept;
import org.eclipse.daanse.cwm.model.cwm.analysis.businessnomenclature.Taxonomy;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.daanse.dcat.util.Catalogs;
import org.eclipse.daanse.cwm.model.daanse.dcat.util.Datasets;
import org.eclipse.daanse.cwm.model.daanse.dcat.util.DcatVocabularies;
import org.eclipse.daanse.cwm.model.daanse.dcat.util.Distributions;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The shape of the {@code dcat} metamodel: hidden opposites with {@code Property.oppositeRoleName}
 * pairs, the annotations of every constraint, the catalog as dataset and package, the XMI round trip
 * of a small catalog next to the CWM elements it publishes, and the way back through
 * {@code InverseReferences} (dataset, inCatalog, seriesMember, next, dataService,
 * servedDistribution).
 */
class DcatModelTest {

    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final String ECORE_ANN = "http://www.eclipse.org/emf/2002/Ecore";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final String CONSTRAINTS_NS =
            "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.dcat/constraints";

    private static final DcatFactory DF = DcatFactory.eINSTANCE;
    private static final CoreFactory CF = CoreFactory.eINSTANCE;
    private static final BusinessinformationFactory BF = BusinessinformationFactory.eINSTANCE;
    private static final BusinessnomenclatureFactory NF = BusinessnomenclatureFactory.eINSTANCE;

    // ------------------------------------------------------------------
    // Conventions, checked against the checked-in .ecore
    // ------------------------------------------------------------------

    @Test
    void packageDeclaresHiddenOppositesAndRoleNamePairs() throws Exception {
        EPackage pkg = loadCheckedInEcore();

        EAnnotation ocl = pkg.getEAnnotation(ECORE_OCL);
        assertThat(ocl).isNotNull();
        assertThat(ocl.getDetails().get("hiddenOpposites")).isEqualTo("true");
        EAnnotation delegates = pkg.getEAnnotation(ECORE_ANN);
        assertThat(delegates).isNotNull();
        assertThat(delegates.getDetails().get("validationDelegates")).isNotBlank();

        int nonContainment = 0;
        int containment = 0;
        for (var classifier : pkg.getEClassifiers()) {
            if (!(classifier instanceof EClass eClass)) {
                continue;
            }
            for (EReference ref : eClass.getEReferences()) {
                assertThat(ref.getEOpposite()).as("%s.%s has no eOpposite", eClass.getName(),
                        ref.getName()).isNull();
                if (ref.isContainment()) {
                    containment++;
                    continue;
                }
                nonContainment++;
                EAnnotation emof = ref.getEAnnotation(EMOF);
                assertThat(emof).as("%s.%s has a Property.oppositeRoleName", eClass.getName(), ref.getName())
                        .isNotNull();
                String roleName = emof.getDetails().get("Property.oppositeRoleName");
                assertThat(roleName).isNotBlank();
                EAnnotation body = ref.getEAnnotation(EMOF + "#Property.oppositeRoleName");
                assertThat(body).isNotNull();
                assertThat(body.getDetails().get("body")).isEqualTo(roleName);
            }
        }
        // 23 hidden association ends, 7 containments (attribution, qualifiedRelation,
        // distribution, spatial, temporal, checksum, record).
        assertThat(nonContainment).isEqualTo(23);
        assertThat(containment).isEqualTo(7);
    }

    @Test
    void constraintAnnotationTriplesAreConsistent() throws Exception {
        EPackage pkg = loadCheckedInEcore();
        int constraints = 0;
        for (var classifier : pkg.getEClassifiers()) {
            if (!(classifier instanceof EClass eClass)) {
                continue;
            }
            EAnnotation ecoreAnn = eClass.getEAnnotation(ECORE_ANN);
            String declared = ecoreAnn == null ? null : ecoreAnn.getDetails().get("constraints");
            if (declared == null) {
                continue;
            }
            EAnnotation ocl = eClass.getEAnnotation(ECORE_OCL);
            EAnnotation meta = eClass.getEAnnotation(CONSTRAINTS_NS);
            assertThat(ocl).as("%s has OCL bodies", eClass.getName()).isNotNull();
            assertThat(meta).as("%s has constraint metadata", eClass.getName()).isNotNull();
            for (String name : declared.split(" ")) {
                constraints++;
                assertThat(ocl.getDetails().get(name))
                        .as("OCL body of %s.%s", eClass.getName(), name).isNotBlank();
                assertThat(meta.getDetails().get(name + ".name")).isEqualTo(name);
                assertThat(meta.getDetails().get(name + ".description")).isNotBlank();
                assertThat(meta.getDetails().get(name + ".level")).isIn("error", "warning");
            }
        }
        assertThat(constraints).isEqualTo(13);
    }

    // ------------------------------------------------------------------
    // The catalog is a dataset and a package
    // ------------------------------------------------------------------

    @Test
    void catalogIsDatasetAndPackage() {
        Catalog catalog = DF.createCatalog();
        assertThat(catalog).isInstanceOf(Dataset.class);
        assertThat(catalog).isInstanceOf(org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package.class);

        // As a package the catalog owns objects without another home in ownedElement.
        ResponsibleParty party = BF.createResponsibleParty();
        party.setName("Statistikamt");
        catalog.getOwnedElement().add(party);
        assertThat(party.getNamespace()).isSameAs(catalog);
    }

    // ------------------------------------------------------------------
    // Round trip and the way back
    // ------------------------------------------------------------------

    @Test
    void miniModelRoundTripsAndBackNavigates(@TempDir Path tmp) throws Exception {
        // The CWM side: a model that already exists (package HR with the class EMPLOYEE).
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package hr = CF.createPackage();
        hr.setName("HR");
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class employee = CF.createClass();
        employee.setName("EMPLOYEE");
        hr.getOwnedElement().add(employee);

        // The catalog side: the catalog owns its supporting objects itself (as a package).
        Catalog catalog = DF.createCatalog();
        catalog.setName("Open Data Catalog");
        catalog.setHomepage("https://data.example.org");

        ResponsibleParty office = BF.createResponsibleParty();
        office.setName("Statistikamt");
        office.setResponsibility("publisher");
        catalog.getOwnedElement().add(office);

        Taxonomy themes = NF.createTaxonomy();
        themes.setName("themes");
        Concept government = NF.createConcept();
        government.setName("GOVE");
        themes.getOwnedElement().add(government);
        catalog.getOwnedElement().add(themes);
        catalog.getThemeTaxonomy().add(themes);
        catalog.getThemeTaxonomyIri().add(DcatVocabularies.EU_DATA_THEME);

        // The dataset lives next to the model it publishes (a root of its own).
        Dataset employees = DF.createDataset();
        // dcterms:title is the inherited name (core::ModelElement).
        employees.setName("employees");
        employees.setDescription("All employees as published dataset");
        employees.getSubject().add(employee);
        employees.getThemeIri().add(DcatVocabularies.themeIri("GOVE"));
        employees.getTheme().add(government);
        employees.getKeyword().add("hr");
        employees.setAccrualPeriodicityIri(DcatVocabularies.frequencyIri("DAILY"));
        employees.setAccessRightsIri(DcatVocabularies.ACCESS_RIGHT_PUBLIC);

        Attribution publisher = DF.createAttribution();
        publisher.setAgent(office);
        publisher.setRole(AgentRole.PUBLISHER);
        employees.getAttribution().add(publisher);
        Attribution contact = DF.createAttribution();
        contact.setAgent(office);
        contact.setRole(AgentRole.CONTACT_POINT);
        employees.getAttribution().add(contact);

        Distribution csv = DF.createDistribution();
        csv.setName("employees-csv");
        csv.getAccessUrl().add("https://data.example.org/employees");
        csv.getDownloadUrl().add("https://data.example.org/employees.csv");
        csv.setMediaType("text/csv");
        csv.setByteSize(42L);
        Checksum sum = DF.createChecksum();
        sum.setAlgorithm(ChecksumAlgorithm.SHA256);
        sum.setChecksumValue("ab12");
        csv.setChecksum(sum);
        employees.getDistribution().add(csv);

        PeriodOfTime coverage = DF.createPeriodOfTime();
        coverage.setStartDate("2024-01-01");
        coverage.setEndDate("2025-12-31");
        employees.getTemporal().add(coverage);

        DataService api = DF.createDataService();
        api.setName("hr-api");
        api.getEndpointUrl().add("https://api.example.org/hr");
        api.getServesDataset().add(employees);
        csv.getAccessService().add(api);
        catalog.getOwnedElement().add(api);

        // A series with two ordered members.
        DatasetSeries series = DF.createDatasetSeries();
        series.setName("employees-by-year");
        Dataset d2024 = DF.createDataset();
        d2024.setName("employees-2024");
        Dataset d2025 = DF.createDataset();
        d2025.setName("employees-2025");
        d2024.getInSeries().add(series);
        d2025.getInSeries().add(series);
        d2025.setPrev(d2024);
        series.setFirst(d2024);
        series.setLast(d2025);
        catalog.getOwnedElement().add(series);
        catalog.getOwnedElement().add(d2024);
        catalog.getOwnedElement().add(d2025);

        // Membership and record.
        catalog.getResource().add(employees);
        catalog.getResource().add(api);
        catalog.getResource().add(series);
        catalog.getResource().add(d2024);
        catalog.getResource().add(d2025);
        CatalogRecord record = DF.createCatalogRecord();
        record.setName("employees-record");
        record.setIssued("2026-08-12");
        record.setPrimaryTopic(employees);
        catalog.getRecord().add(record);

        // Model and dataset in one resource, the catalog in the other.
        ResourceSet out = resourceSet();
        Resource cwmRes = out.createResource(URI.createFileURI(tmp.resolve("hr.xmi").toString()));
        cwmRes.getContents().add(hr);
        cwmRes.getContents().add(employees);
        Resource catalogRes = out.createResource(URI.createFileURI(tmp.resolve("catalog.xmi").toString()));
        catalogRes.getContents().add(catalog);
        cwmRes.save(Map.of());
        catalogRes.save(Map.of());

        // Load afresh and resolve the proxies.
        ResourceSet in = resourceSet();
        Resource loadedCatalogRes =
                in.getResource(URI.createFileURI(tmp.resolve("catalog.xmi").toString()), true);
        EcoreUtil.resolveAll(in);
        Map<EObject, ?> unresolved = EcoreUtil.UnresolvedProxyCrossReferencer.find(in);
        assertThat(unresolved).isEmpty();

        Catalog loadedCatalog = (Catalog) loadedCatalogRes.getContents().get(0);
        Dataset loadedEmployees = Catalogs.datasetStream(loadedCatalog)
                .filter(d -> "employees".equals(d.getName())).findFirst().orElseThrow();
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement loadedEmployee =
                loadedEmployees.getSubject().get(0);
        assertThat(loadedEmployee.getName()).isEqualTo("EMPLOYEE");
        Distribution loadedCsv = loadedEmployees.getDistribution().get(0);
        assertThat(loadedCsv.getChecksum().getAlgorithm()).isEqualTo(ChecksumAlgorithm.SHA256);
        assertThat(Distributions.datasetOf(loadedCsv)).contains(loadedEmployees);
        assertThat(Datasets.publisherOf(loadedEmployees).orElseThrow().getName())
                .isEqualTo("Statistikamt");
        assertThat(Datasets.contactPointsOf(loadedEmployees)).hasSize(1);
        assertThat(Catalogs.recordFor(loadedCatalog, loadedEmployees)).isPresent();
        // The datasets, services and catalogs of a catalog are filters, not features.
        assertThat(Catalogs.datasets(loadedCatalog)).hasSize(4);
        assertThat(Catalogs.services(loadedCatalog)).hasSize(1);
        assertThat(Catalogs.catalogs(loadedCatalog)).isEmpty();

        // Hidden opposites: without an index only a scan of the whole model ...
        assertThat(InverseReferences.isIndexed(loadedEmployee)).isFalse();
        // ... with InverseReferences every hidden end is answered from the index.
        InverseReferences.install(in);
        assertThat(InverseReferences.isIndexed(loadedEmployee)).isTrue();
        assertThat(Datasets.datasetsFor(loadedEmployee)).containsExactly(loadedEmployees);
        assertThat(Catalogs.catalogsListing(loadedEmployees)).containsExactly(loadedCatalog);
        DataService loadedApi = Catalogs.services(loadedCatalog).get(0);
        assertThat(Datasets.servicesServing(loadedEmployees)).containsExactly(loadedApi);
        assertThat(Distributions.distributionsServedBy(loadedApi)).containsExactly(loadedCsv);

        DatasetSeries loadedSeries = (DatasetSeries) Catalogs.datasetStream(loadedCatalog)
                .filter(DatasetSeries.class::isInstance).findFirst().orElseThrow();
        assertThat(Datasets.seriesMembers(loadedSeries)).hasSize(2);
        List<Dataset> ordered = Datasets.seriesMembersInOrder(loadedSeries);
        assertThat(ordered).extracting(Dataset::getName)
                .containsExactly("employees-2024", "employees-2025");
        assertThat(Datasets.next(ordered.get(0))).contains(ordered.get(1));
    }

    @Test
    void containmentTreeHasSingleContainers() {
        Catalog catalog = DF.createCatalog();
        Dataset dataset = DF.createDataset();
        Distribution dist = DF.createDistribution();
        dataset.getDistribution().add(dist);
        catalog.getOwnedElement().add(dataset);
        CatalogRecord record = DF.createCatalogRecord();
        record.setPrimaryTopic(dataset);
        catalog.getRecord().add(record);

        // Moving a contained child removes it from its old container.
        Dataset other = DF.createDataset();
        other.getDistribution().add(dist);
        assertThat(dataset.getDistribution()).isEmpty();
        assertThat(dist.eContainer()).isSameAs(other);

        List<EObject> seen = new ArrayList<>();
        catalog.eAllContents().forEachRemaining(seen::add);
        assertThat(seen).doesNotHaveDuplicates();
        seen.forEach(o -> assertThat(o.eContainer()).isNotNull());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static EPackage loadCheckedInEcore() {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
        File ecore = new File(System.getProperty("user.dir"), "model/dcat.ecore");
        Resource res = rs.getResource(URI.createFileURI(ecore.getAbsolutePath()), true);
        return (EPackage) res.getContents().get(0);
    }

    private static ResourceSet resourceSet() {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
        return rs;
    }
}
