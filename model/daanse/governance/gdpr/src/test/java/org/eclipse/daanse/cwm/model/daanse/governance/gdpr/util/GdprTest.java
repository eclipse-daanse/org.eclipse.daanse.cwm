/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.gdpr.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.BehavioralFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Event;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Models;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ShortIds;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.AttributeRole;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationPolicy;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Outcome;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util.Policies;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util.Treatments;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.assessment.PersonalDataCategory;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.assessment.PrivacyLevel;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataErasure;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataProcessing;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataProcessingLawfulnessType;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataProcessingRecord;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataProcessingType;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataTransmission;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.PersonalReferenceType;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Finding;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Severity;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.PeriodStarts;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.RetentionSchedules;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.Compliance;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.LegalSources;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.ObligationsModelValidator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;

/** A record of processing activities on a CRM: the helpers, the validator, and an erasure kept by a schedule. */
class GdprTest {

    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

    private final Model company = Models.create("company");
    private final Package governance = Packages.create(company, "governance");
    private final Event contractEnded = event("contractEnded");
    private final ResponsibleParty controller = party("controller", "the company");
    private final ResponsibleParty office = party("office", "data protection office");
    private final ResponsibleParty officer = party("officer", "data protection officer");
    private final ResponsibleParty statistics = party("statistics", "state statistics office");
    private final Catalog crm = Catalogs.create(company, "crm");
    private final Schema core = Schemas.create(crm, "core");
    private final Table customers = Tables.create(core, "customers");
    private final Column email = Columns.create(customers, "email", SQLSimpleTypes.ownedIn(crm, "VARCHAR"));
    private final Column health = Columns.create(customers, "health_note", SQLSimpleTypes.ownedIn(crm, "VARCHAR"));
    private final Column contractEnd = Columns.create(customers, "contract_end", SQLSimpleTypes.ownedIn(crm, "DATE"));
    private final Table orders = Tables.create(core, "orders");

    private final DataProcessingRecord record = DataProcessingRecords.create(governance, "record", controller, office,
            officer);
    private final DataProcessing care = DataProcessings.kinds(DataProcessings.create(record, "customer-care",
            "care for customers", "2025-01-01", customers), List.of(DataProcessingType.COLLECTION),
            List.of(PersonalReferenceType.DIRECT));

    @Test
    void aProcessingWithItsLawfulnessErasureAndTransmission() {
        DataProcessings.lawful(care, "contract", DataProcessingLawfulnessType.CONTRACTUAL_REQUIREMENT,
                "the customer contract");
        DataErasure erasure = erasure3y();
        DeidentificationPolicy policy = Policies.audience(Policies.create(governance, "counts", Outcome.ANONYMOUS,
                customers), statistics);
        DataTransmission transmission = DataTransmissions.policy(DataTransmissions.create(care, "to-statistics",
                statistics, customers), policy);

        assertThat(DataProcessingRecords.processings(record)).containsExactly(care);
        assertThat(erasure.getBody().getBody()).isEqualTo("delete within P3Y from event contractEnded (contract_end)");
        assertThat(DataErasures.processing(erasure)).isSameAs(care);
        assertThat(Compliance.deletions(customers)).containsExactly(erasure);
        assertThat(transmission.getPolicy()).isSameAs(policy);
        assertThat(care.getDataProcessingLawfulness()).singleElement()
                .satisfies(l -> assertThat(l.getDataProcessingLawfulnessAnnotation())
                        .isEqualTo("the customer contract"));
    }

    @Test
    void anErasureIsKeptByASchedule() {
        erasure3y();
        RetentionSchedule schedule = RetentionSchedules.rows(governance, "customer-rows", "P5Y",
                PeriodStarts.event(contractEnded, contractEnd), customers);
        assertThat(ObligationsModelValidator.validate(company)).containsExactly("O4 DataErasure 'customers-3y': stage "
                + "delete of customer-rows deletes after P5Y, not within P3Y (company.crm.core.customers).");
        assertThat(Compliance.check(customers, start -> Optional.of(NOW), NOW).compliant()).isFalse();

        schedule.getStage().get(0).setAfter("P3Y");
        RetentionSchedules.refreshed(schedule);
        assertThat(ObligationsModelValidator.validate(company)).isEmpty();
        assertThat(Compliance.check(customers, start -> Optional.of(NOW), NOW).compliant()).isTrue();
    }

    @Test
    void g1_anErasureGovernsWhatItsProcessingProcesses() {
        erasure3y();
        DataErasure emails = DataErasures.create(care, "email-1y", "P1Y",
                PeriodStarts.event(contractEnded, contractEnd), email);
        assertThat(rules(Severity.ERROR)).isEmpty();
        emails.getConstrainedElement().add(orders);
        assertThat(errors()).containsExactly("erasure 'email-1y' governs company.crm.core.orders, which its "
                + "processing 'customer-care' does not process.");
    }

    @Test
    void g2_aSpecialCategoryIsNoInsensitiveFeature() {
        PrivacyRatings.rate(governance, "health-note", health, PersonalDataCategory.HEALTH_DATA);
        PrivacyRatings.rate(governance, "customers", customers, PrivacyLevel.HIGH);
        DeidentificationPolicy policy = Policies.create(governance, "research", Outcome.PSEUDONYMOUS, customers);
        Treatments.unchanged(policy, health, AttributeRole.SENSITIVE);
        assertThat(rules(Severity.WARNING)).doesNotContain("G2");
        policy.getTreatment().clear();
        Treatments.unchanged(policy, health, AttributeRole.INSENSITIVE);
        assertThat(rules(Severity.WARNING)).contains("G2");
        assertThat(PrivacyRatings.special(PersonalDataCategory.HEALTH_DATA)).isTrue();
        assertThat(PrivacyRatings.special(PersonalDataCategory.PERSONAL_DATA_WITHOUT_SPECIAL_CATEGORY)).isFalse();
    }

    @Test
    void g3_thePolicyOfATransmissionIsForItsRecipient() {
        DeidentificationPolicy policy = Policies.create(governance, "counts", Outcome.ANONYMOUS, customers);
        DataTransmission transmission = DataTransmissions.policy(DataTransmissions.create(care, "to-statistics",
                statistics, customers), policy);
        assertThat(rules(Severity.ERROR)).isEmpty();
        Policies.audience(policy, office);
        assertThat(errors()).containsExactly("transmission 'to-statistics' goes to statistics, but its policy "
                + "'counts' is for office.");
        Policies.audience(policy, transmission.getContactRecipient());
        assertThat(rules(Severity.ERROR)).isEmpty();
    }

    @Test
    void g4_aProcessingRestsOnALegalSource() {
        assertThat(rules(Severity.WARNING)).containsExactly("G4");
        LegalSources.cite(LegalSources.provision(LegalSources.create(governance, "gdpr-6",
                "https://eur-lex.europa.eu/eli/reg/2016/679/oj", LegalSources.STATUTE), "Art. 6 (1) (b)", "EU"), care);
        assertThat(rules(Severity.WARNING)).isEmpty();
    }

    private DataErasure erasure3y() {
        DataErasure erasure = DataErasures.create(care, "customers-3y", "P3Y",
                PeriodStarts.event(contractEnded, contractEnd), customers);
        erasure.setScope(DataScope.ROW);
        return erasure;
    }

    private List<String> rules(Severity severity) {
        return GdprModelValidator.check(company).stream().filter(f -> f.severity() == severity).map(Finding::rule)
                .toList();
    }

    private List<String> errors() {
        return GdprModelValidator.check(company).stream().filter(f -> f.severity() == Severity.ERROR)
                .map(Finding::message).toList();
    }

    private Event event(String name) {
        Event event = BehavioralFactory.eINSTANCE.createEvent();
        event.setName(name);
        governance.getOwnedElement().add(event);
        return event;
    }

    private ResponsibleParty party(String name, String responsibility) {
        ResponsibleParty party = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
        party.setName(name);
        party.setResponsibility(responsibility);
        governance.getOwnedElement().add(party);
        return party;
    }

    /** Lines of the XMI {@link #inXmi()} writes: a processing with its lawfulness, erasure and transmission. */
    private static final String XMI = """
            <ownedElement xsi:type="gdprprocessing:DataProcessingRecord" name="record" \
            contactControllerGlobal="_controller" contactControllerInternal="_office" \
            contactPrivacyOfficer="_officer">
            <ownedElement xsi:type="gdprprocessing:DataProcessing" name="customer-care" dataClasses="_customers" \
            dataProcessingPurpose="care for customers" validFrom="2025-01-01">
            <personalReferenceTypes>DIRECT</personalReferenceTypes>
            <dataProcessingTypes>COLLECTION</dataProcessingTypes>
            <dataProcessingLawfulness name="contract" dataProcessingLawfulnessTypes="CONTRACTUAL_REQUIREMENT" \
            dataProcessingLawfulnessAnnotation="the customer contract"/>
            <dataTransmissions name="to-statistics" dataClasses="_customers" contactRecipient="_statistics" \
            policy="_counts"/>
            <dataErasures name="customers-3y" constrainedElement="_customers" scope="row" maximum="P3Y">
            <body body="delete within P3Y from event contractEnded (contract_end)" language="daanse.lifecycle"/>
            <start xsi:type="lifecycle:EventTime" event="_contractEnded" feature="_customers.contract_end"/>
            <ownedElement xsi:type="obligations:LegalSource" name="gdpr-6" \
            reference="https://eur-lex.europa.eu/eli/reg/2016/679/oj" type="statute" \
            modelElement="_customer-care" jurisdiction="EU" provision="Art. 6 (1) (b)"/>
            <ownedElement xsi:type="deid:DeidentificationPolicy" name="counts" constrainedElement="_customers" \
            outcome="anonymous" audience="_statistics">
            <body body="anonymous for statistics" language="daanse.deidentification"/>
            <ownedElement xsi:type="relational:Table" name="customers" \
            constraint="_customer-care.customers-3y _counts">
            """;

    @Test
    void inXmi() throws IOException {
        DataProcessings.lawful(care, "contract", DataProcessingLawfulnessType.CONTRACTUAL_REQUIREMENT,
                "the customer contract");
        LegalSources.cite(LegalSources.provision(LegalSources.create(governance, "gdpr-6",
                "https://eur-lex.europa.eu/eli/reg/2016/679/oj", LegalSources.STATUTE), "Art. 6 (1) (b)", "EU"), care);
        erasure3y();
        DataTransmissions.policy(DataTransmissions.create(care, "to-statistics", statistics, customers),
                Policies.audience(Policies.create(governance, "counts", Outcome.ANONYMOUS, customers), statistics));
        assertThat(GdprModelValidator.check(company)).isEmpty();

        String xmi = xmi(company, "gdpr.xmi");
        for (String line : XMI.split("\n")) {
            assertThat(xmi).as(line).contains(line.trim());
        }
    }

    /** {@code root} as XMI with short ids, written to {@code target/<file>} as well. */
    private static String xmi(EObject root, String file) throws IOException {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
        Resource r = rs.createResource(URI.createURI(file));
        r.getContents().add(root);
        ShortIds.assign(r);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        r.save(out, Map.of(XMLResource.OPTION_ENCODING, "UTF-8"));
        String xmi = out.toString(StandardCharsets.UTF_8).replaceAll(" xmi:id=\"[^\"]*\"", "");
        Files.writeString(Path.of(System.getProperty("user.dir"), "target", file), xmi, StandardCharsets.UTF_8);
        return xmi;
    }
}
