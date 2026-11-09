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

import org.eclipse.daanse.cwm.model.daanse.dcat.util.DcatValidator;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A valid and a violating case for each of the 13 OCL invariants. */
class DcatConstraintsTest {

    private static final DcatFactory DF = DcatFactory.eINSTANCE;

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
        service.getEndpointUrl().add("https://api.example.org/hr");
        assertThat(severity((v, d) -> v.validateDataService_EndpointUrlNotEmpty(service, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        service.getEndpointUrl().add("");
        assertThat(severity((v, d) -> v.validateDataService_EndpointUrlNotEmpty(service, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
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
