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

import java.util.HashMap;
import java.util.function.BiPredicate;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Contact;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResourceLocator;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.daanse.dcat.util.DcatValidator;
import org.eclipse.daanse.cwm.model.daanse.dcat.util.DcatVocabularies;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A valid and a violating case for each of the 23 OCL invariants. The EMF validator reports every
 * violated invariant as an error; the level of a rule ({@code error} or {@code warning}) is in its
 * constraint metadata, checked here for the warnings.
 */
class DcatConstraintsTest {

    private static final DcatFactory DF = DcatFactory.eINSTANCE;
    private static final BusinessinformationFactory BF = BusinessinformationFactory.eINSTANCE;
    private static final String CONSTRAINTS_NS =
            "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.dcat/constraints";

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void singlePublisher() {
        Dataset dataset = DF.createDataset();
        dataset.getAttribution().add(attribution(AgentRole.PUBLISHER));
        dataset.getAttribution().add(attribution(AgentRole.CONTACT_POINT));
        assertThat(severity((v, d) -> v.validateCatalogedResource_SinglePublisher(dataset, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        dataset.getAttribution().add(attribution(AgentRole.PUBLISHER));
        assertThat(severity((v, d) -> v.validateCatalogedResource_SinglePublisher(dataset, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void spatialResolutionPositive() {
        Dataset dataset = DF.createDataset();
        Distribution distribution = DF.createDistribution();
        assertThat(severity((v, d) -> v.validateDataset_SpatialResolutionPositive(dataset, d, new HashMap<>())))
                .as("unset").isEqualTo(Diagnostic.OK);
        dataset.setSpatialResolutionInMeters("2.5");
        distribution.setSpatialResolutionInMeters("10");
        assertThat(severity((v, d) -> v.validateDataset_SpatialResolutionPositive(dataset, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        assertThat(severity(
                (v, d) -> v.validateDistribution_SpatialResolutionPositive(distribution, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        dataset.setSpatialResolutionInMeters("0");
        distribution.setSpatialResolutionInMeters("-1");
        assertThat(severity((v, d) -> v.validateDataset_SpatialResolutionPositive(dataset, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
        assertThat(severity(
                (v, d) -> v.validateDistribution_SpatialResolutionPositive(distribution, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void seriesHasNoDistribution() {
        DatasetSeries series = DF.createDatasetSeries();
        assertThat(severity((v, d) -> v.validateDatasetSeries_SeriesHasNoDistribution(series, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        series.getDistribution().add(DF.createDistribution());
        assertThat(severity((v, d) -> v.validateDatasetSeries_SeriesHasNoDistribution(series, d, new HashMap<>())))
                .isNotEqualTo(Diagnostic.OK);
        assertThat(level(DcatPackage.Literals.DATASET_SERIES, "SeriesHasNoDistribution")).isEqualTo("warning");
    }

    @Test
    void recordTopicIsListed() {
        Catalog catalog = DF.createCatalog();
        Dataset listed = DF.createDataset();
        Dataset owned = DF.createDataset();
        Dataset elsewhere = DF.createDataset();
        catalog.getResource().add(listed);
        catalog.getOwnedElement().add(owned);
        catalog.getRecord().add(record(listed));
        catalog.getRecord().add(record(owned));
        assertThat(severity((v, d) -> v.validateCatalog_RecordTopicIsListed(catalog, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        catalog.getRecord().add(record(elsewhere));
        assertThat(severity((v, d) -> v.validateCatalog_RecordTopicIsListed(catalog, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void notOwnMember() {
        Catalog catalog = DF.createCatalog();
        catalog.getResource().add(DF.createCatalog());
        assertThat(severity((v, d) -> v.validateCatalog_NotOwnMember(catalog, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        catalog.getResource().add(catalog);
        assertThat(severity((v, d) -> v.validateCatalog_NotOwnMember(catalog, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void endpointUrlNotEmpty() {
        DataService service = DF.createDataService();
        service.setEndpointUrl("https://api.example.org/hr");
        assertThat(severity((v, d) -> v.validateDataService_EndpointUrlNotEmpty(service, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        service.setEndpointUrl("");
        assertThat(severity((v, d) -> v.validateDataService_EndpointUrlNotEmpty(service, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void oneAccessService() {
        Portal portal = DF.createPortal();
        portal.getOwnedElement().add(DF.createCatalog());
        assertThat(severity((v, d) -> v.validatePortal_OneAccessService(portal, d, new HashMap<>())))
                .as("no access service").isEqualTo(Diagnostic.ERROR);
        portal.getOwnedElement().add(DF.createDataService());
        assertThat(severity((v, d) -> v.validatePortal_OneAccessService(portal, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        portal.getOwnedElement().add(DF.createDataService());
        assertThat(severity((v, d) -> v.validatePortal_OneAccessService(portal, d, new HashMap<>())))
                .as("two access services").isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void accessServiceServesCatalogs() {
        Portal portal = DF.createPortal();
        DataService api = DF.createDataService();
        Catalog own = DF.createCatalog();
        Catalog takenOver = DF.createCatalog();
        portal.getOwnedElement().add(api);
        portal.getOwnedElement().add(own);
        portal.getImportedElement().add(takenOver);
        api.getServesDataset().add(own);
        api.getServesDataset().add(takenOver);
        assertThat(severity((v, d) -> v.validatePortal_AccessServiceServesCatalogs(portal, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        api.getServesDataset().remove(own);
        assertThat(severity((v, d) -> v.validatePortal_AccessServiceServesCatalogs(portal, d, new HashMap<>())))
                .as("an own catalog not served").isEqualTo(Diagnostic.ERROR);
        api.getServesDataset().add(own);
        api.getServesDataset().add(DF.createCatalog());
        assertThat(severity((v, d) -> v.validatePortal_AccessServiceServesCatalogs(portal, d, new HashMap<>())))
                .as("a catalog of nobody's portal").isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void takenOverNotOwned() {
        Portal portal = DF.createPortal();
        Catalog own = DF.createCatalog();
        Catalog takenOver = DF.createCatalog();
        portal.getOwnedElement().add(own);
        portal.getImportedElement().add(takenOver);
        assertThat(severity((v, d) -> v.validatePortal_TakenOverNotOwned(portal, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        portal.getImportedElement().add(own);
        assertThat(severity((v, d) -> v.validatePortal_TakenOverNotOwned(portal, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void oneProtocol() {
        DataService service = DF.createDataService();
        service.getConformsToIri().add("http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/core");
        service.getConformsToIri().add("http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/geojson");
        service.getConformsToIri().add(DcatVocabularies.PROFILE_DCAT_AP_DE);
        assertThat(severity((v, d) -> v.validateDataService_OneProtocol(service, d, new HashMap<>())))
                .as("two conformance classes of one protocol and a profile").isEqualTo(Diagnostic.OK);
        service.getConformsToIri().add(DcatVocabularies.PROTOCOL_ODATA);
        assertThat(severity((v, d) -> v.validateDataService_OneProtocol(service, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void accessUrlUnderEndpoint() {
        DataService service = DF.createDataService();
        service.setEndpointUrl("https://geo.utility.example/ogc/assets");
        Distribution distribution = DF.createDistribution();
        distribution.getAccessUrl().add("https://geo.utility.example/ogc/assets/collections/pools");
        assertThat(severity((v, d) -> v.validateDistribution_AccessUrlUnderEndpoint(distribution, d, new HashMap<>())))
                .as("no service").isEqualTo(Diagnostic.OK);
        distribution.setAccessService(service);
        assertThat(severity((v, d) -> v.validateDistribution_AccessUrlUnderEndpoint(distribution, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        distribution.getAccessUrl().add("https://opendata.utility.example/dataset/pools");
        assertThat(severity((v, d) -> v.validateDistribution_AccessUrlUnderEndpoint(distribution, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void servedDatasetHasDistribution() {
        DataService service = DF.createDataService();
        Dataset pools = DF.createDataset();
        Catalog catalog = DF.createCatalog();
        service.getServesDataset().add(pools);
        service.getServesDataset().add(catalog);
        assertThat(severity((v, d) -> v.validateDataService_ServedDatasetHasDistribution(service, d, new HashMap<>())))
                .as("a dataset without a distribution with this service").isNotEqualTo(Diagnostic.OK);
        Distribution geojson = DF.createDistribution();
        geojson.setAccessService(service);
        pools.getDistribution().add(geojson);
        assertThat(severity((v, d) -> v.validateDataService_ServedDatasetHasDistribution(service, d, new HashMap<>())))
                .as("a catalog needs none").isEqualTo(Diagnostic.OK);
        assertThat(level(DcatPackage.Literals.DATA_SERVICE, "ServedDatasetHasDistribution")).isEqualTo("warning");
    }

    @Test
    void distributionServiceServesDataset() {
        DataService service = DF.createDataService();
        Dataset pools = DF.createDataset();
        Distribution geojson = DF.createDistribution();
        geojson.setAccessService(service);
        pools.getDistribution().add(geojson);
        pools.getDistribution().add(DF.createDistribution());
        assertThat(severity((v, d) -> v.validateDataset_DistributionServiceServesDataset(pools, d, new HashMap<>())))
                .isNotEqualTo(Diagnostic.OK);
        service.getServesDataset().add(pools);
        assertThat(severity((v, d) -> v.validateDataset_DistributionServiceServesDataset(pools, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        assertThat(level(DcatPackage.Literals.DATASET, "DistributionServiceServesDataset")).isEqualTo("warning");
    }

    @Test
    void sourceNotCircular() {
        Dataset monthly = DF.createDataset();
        Dataset daily = DF.createDataset();
        Dataset events = DF.createDataset();
        monthly.getSource().add(daily);
        daily.getSource().add(events);
        assertThat(severity((v, d) -> v.validateDataset_SourceNotCircular(monthly, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        events.getSource().add(monthly);
        assertThat(severity((v, d) -> v.validateDataset_SourceNotCircular(monthly, d, new HashMap<>())))
                .as("a cycle over two datasets").isEqualTo(Diagnostic.ERROR);
        Dataset self = DF.createDataset();
        self.getSource().add(self);
        assertThat(severity((v, d) -> v.validateDataset_SourceNotCircular(self, d, new HashMap<>())))
                .as("its own source").isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void generatedHasSource() {
        Dataset monthly = DF.createDataset();
        assertThat(severity((v, d) -> v.validateDataset_GeneratedHasSource(monthly, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        monthly.getWasGeneratedBy().add(CoreFactory.eINSTANCE.createPackage());
        assertThat(severity((v, d) -> v.validateDataset_GeneratedHasSource(monthly, d, new HashMap<>())))
                .isNotEqualTo(Diagnostic.OK);
        monthly.getSource().add(DF.createDataset());
        assertThat(severity((v, d) -> v.validateDataset_GeneratedHasSource(monthly, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        assertThat(level(DcatPackage.Literals.DATASET, "GeneratedHasSource")).isEqualTo("warning");
    }

    @Test
    void agentHasOneLocator() {
        ResponsibleParty utility = BF.createResponsibleParty();
        Attribution publisher = attribution(AgentRole.PUBLISHER);
        publisher.setAgent(utility);
        Contact office = BF.createContact();
        office.getUrl().add(locator("https://www.utility.example"));
        utility.getContact().add(office);
        Contact desk = BF.createContact();
        desk.getEmail().add(BF.createEmail());
        utility.getContact().add(desk);
        assertThat(severity((v, d) -> v.validateAttribution_AgentHasOneLocator(publisher, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        desk.getUrl().add(locator("https://www.utility.example/contact"));
        assertThat(severity((v, d) -> v.validateAttribution_AgentHasOneLocator(publisher, d, new HashMap<>())))
                .as("a second locator in another contact").isNotEqualTo(Diagnostic.OK);
        assertThat(level(DcatPackage.Literals.ATTRIBUTION, "AgentHasOneLocator")).isEqualTo("warning");
    }

    @Test
    void byteSizeNonNegative() {
        Distribution distribution = DF.createDistribution();
        distribution.setByteSize(42L);
        assertThat(severity((v, d) -> v.validateDistribution_ByteSizeNonNegative(distribution, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        distribution.setByteSize(-1L);
        assertThat(severity((v, d) -> v.validateDistribution_ByteSizeNonNegative(distribution, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void otherRoleNeedsIri() {
        Attribution other = attribution(AgentRole.OTHER);
        assertThat(severity((v, d) -> v.validateAttribution_OtherRoleNeedsIri(other, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
        other.setRoleIri("http://www.w3.org/ns/prov#Agent");
        assertThat(severity((v, d) -> v.validateAttribution_OtherRoleNeedsIri(other, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        Attribution publisher = attribution(AgentRole.PUBLISHER);
        assertThat(severity((v, d) -> v.validateAttribution_OtherRoleNeedsIri(publisher, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
    }

    @Test
    void relationshipHasRole() {
        Relationship relationship = DF.createRelationship();
        assertThat(severity((v, d) -> v.validateRelationship_RelationshipHasRole(relationship, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
        relationship.getRoleIri().add("http://www.iana.org/assignments/relation/describedby");
        assertThat(severity((v, d) -> v.validateRelationship_RelationshipHasRole(relationship, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
    }

    @Test
    void startBeforeEnd() {
        PeriodOfTime period = DF.createPeriodOfTime();
        period.setStartDate("2024-01-01");
        assertThat(severity((v, d) -> v.validatePeriodOfTime_StartBeforeEnd(period, d, new HashMap<>())))
                .as("open end").isEqualTo(Diagnostic.OK);
        period.setEndDate("2025-12-31");
        assertThat(severity((v, d) -> v.validatePeriodOfTime_StartBeforeEnd(period, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        period.setEndDate("2023-12-31");
        assertThat(severity((v, d) -> v.validatePeriodOfTime_StartBeforeEnd(period, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void locationNotEmpty() {
        Location location = DF.createLocation();
        assertThat(severity((v, d) -> v.validateLocation_LocationNotEmpty(location, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
        location.setBboxWkt("POLYGON((11.5 50.9, 11.7 50.9, 11.7 51.0, 11.5 51.0, 11.5 50.9))");
        assertThat(severity((v, d) -> v.validateLocation_LocationNotEmpty(location, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
    }

    @Test
    void checksumValueNotEmpty() {
        Checksum checksum = DF.createChecksum();
        checksum.setAlgorithm(ChecksumAlgorithm.SHA256);
        checksum.setChecksumValue("");
        assertThat(severity((v, d) -> v.validateChecksum_ChecksumValueNotEmpty(checksum, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
        checksum.setChecksumValue("ab12");
        assertThat(severity((v, d) -> v.validateChecksum_ChecksumValueNotEmpty(checksum, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
    }

    private static Attribution attribution(AgentRole role) {
        Attribution a = DF.createAttribution();
        a.setRole(role);
        return a;
    }

    private static ResourceLocator locator(String url) {
        ResourceLocator locator = BF.createResourceLocator();
        locator.setUrl(url);
        return locator;
    }

    /** The level of the constraint {@code name} of {@code eClass}: {@code error} or {@code warning}. */
    private static String level(EClass eClass, String name) {
        return eClass.getEAnnotation(CONSTRAINTS_NS).getDetails().get(name + ".level");
    }

    private static CatalogRecord record(CatalogedResource topic) {
        CatalogRecord r = DF.createCatalogRecord();
        r.setPrimaryTopic(topic);
        return r;
    }

    private static int severity(BiPredicate<DcatValidator, BasicDiagnostic> check) {
        BasicDiagnostic diagnostic = new BasicDiagnostic();
        check.test(DcatValidator.INSTANCE, diagnostic);
        return diagnostic.getSeverity();
    }
}
