/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.obligations.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Alignment;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.EventTime;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.FeatureTime;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Finding;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Severity;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.PeriodStarts;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.Due;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.Starts;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.State;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.RetentionSchedules;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Stages;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.DeletionClass;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.DeletionObligation;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.LegalSource;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.RetentionHold;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.RetentionObligation;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.Compliance;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.DeletionClasses;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.LegalSources;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.Obligations;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.ObligationsModelValidator;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.RetentionHolds;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;

/**
 * Duties at the places of CWM: the duties and how a schedule keeps them, which duties apply to an
 * element, what the validator reports, and the XMI of a model with duties.
 */
class ObligationsAcrossCwmTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final String AO_147 = "https://www.gesetze-im-internet.de/ao_1977/__147.html";
    /** An invoice issued on 15 June 2020, a contract that ended on 1 April 2024, both in Berlin. */
    private static final Instant ISSUED = ZonedDateTime.of(2020, 6, 15, 9, 30, 0, 0, BERLIN).toInstant();
    private static final Instant CONTRACT_END = ZonedDateTime.of(2024, 4, 1, 0, 0, 0, 0, BERLIN).toInstant();
    private static final Starts STARTS = start -> Optional.of(start instanceof EventTime ? CONTRACT_END : ISSUED);
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

    private final Model company = Models.create("company");
    private final Package governance = Packages.create(company, "governance");
    private final Package law = Packages.create(company, "law");
    private final Event contractEnded = event("contractEnded");
    private final Event applicationRejected = event("applicationRejected");
    private final ResponsibleParty legal = party("legal", "legal department");
    private final Catalog erp = Catalogs.create("erp");
    private final Schema sales = Schemas.create(erp, "sales");
    private final Table invoices = Tables.create(sales, "invoices");
    private final Column issuedOn = column(invoices, "issued_on", "DATE");
    private final Column customerName = column(invoices, "customer_name", "VARCHAR");
    private final Column total = column(invoices, "total", "DECIMAL");
    private final Table customers = Tables.create(sales, "customers");
    private final Column email = column(customers, "email", "VARCHAR");
    private final Column contractEnd = column(customers, "contract_end", "DATE");
    private final Schema hr = Schemas.create(erp, "hr");
    private final Table applications = Tables.create(hr, "applications");

    // 1 Duties and how a schedule keeps them

    @Test
    void keepAtLeast() {
        RetentionObligation books = Obligations.onRows(Obligations.immutable(
                Obligations.retain(governance, "invoices-8y", "P8Y", endOfYear(), invoices)));
        assertThat(books.getBody().getBody())
                .isEqualTo("keep at least P8Y from issued_on, end of year in Europe/Berlin; immutable");

        RetentionSchedule schedule = RetentionSchedules.rows(governance, "invoice-rows", "P6Y", endOfYear(), invoices);
        assertThat(Compliance.check(invoices, STARTS, NOW).violations()).singleElement().satisfies(v -> {
            assertThat(v.rule()).isEqualTo("O3");
            assertThat(v.message()).isEqualTo("stage delete is due at 2026-12-31T23:00:00Z, before invoices-8y keeps "
                    + "the data until 2028-12-31T23:00:00Z");
        });
        schedule.getStage().get(0).setAfter("P8Y");
        assertThat(Compliance.check(invoices, STARTS, NOW).compliant()).isTrue();
    }

    @Test
    void goneWithin() {
        DeletionObligation privacy = Obligations.onRows(Obligations.delete(governance, "customers-3y", "P3Y",
                PeriodStarts.event(contractEnded, contractEnd), customers));
        assertThat(privacy.getBody().getBody()).isEqualTo("delete within P3Y from event contractEnded (contract_end)");

        RetentionSchedule schedule = RetentionSchedules.rows(governance, "customer-rows", "P5Y",
                PeriodStarts.event(contractEnded, contractEnd), customers);
        assertThat(Compliance.check(customers, STARTS, NOW).violations()).singleElement().satisfies(v -> {
            assertThat(v.rule()).isEqualTo("O4");
            assertThat(v.message()).isEqualTo("stage delete is due at 2029-03-31T22:00:00Z, after customers-3y demands "
                    + "the data gone by 2027-03-31T22:00:00Z");
        });
        schedule.getStage().get(0).setAfter("P3Y");
        assertThat(Compliance.check(customers, STARTS, NOW).compliant()).isTrue();
    }

    @Test
    void restrictThenDelete() {
        Obligations.onRows(Obligations.retain(governance, "invoices-8y", "P8Y", endOfYear(), invoices));
        Obligations.onRows(Obligations.delete(governance, "invoice-privacy-1y", "P1Y", endOfYear(), invoices));

        RetentionSchedule schedule = RetentionSchedules.rows(governance, "invoice-rows", "P8Y", endOfYear(), invoices);
        assertThat(Compliance.check(invoices, STARTS, NOW).violations()).singleElement().satisfies(v -> {
            assertThat(v.rule()).isEqualTo("O5");
            assertThat(v.message()).isEqualTo("invoice-privacy-1y ends at 2021-12-31T23:00:00Z but invoices-8y keeps "
                    + "the data until 2028-12-31T23:00:00Z: the data is restricted by 2021-12-31T23:00:00Z");
        });

        schedule.getStage().clear();
        Stages.restrict(schedule, "restrict", "P1Y");
        Stages.delete(schedule, "delete", "P8Y");
        Compliance.Report report = Compliance.check(invoices, STARTS, NOW);
        assertThat(report.compliant()).isTrue();
        assertThat(report.plan().current()).map(d -> d.step().stage().getName()).contains("restrict");
        assertThat(report.plan().next()).map(Due::at).contains(Instant.parse("2028-12-31T23:00:00Z"));
    }

    @Test
    void aHoldSuspendsTheDelete() {
        RetentionSchedules.rows(governance, "invoice-rows", "P8Y", endOfYear(), invoices);
        RetentionHold audit = RetentionHolds.holder(RetentionHolds.create(governance, "tax-audit-2028",
                "tax audit 2028", Instant.parse("2028-10-01T00:00:00Z"), sales), legal);
        assertThat(audit.getBody().getBody()).isEqualTo("hold since 2028-10-01T00:00:00Z: tax audit 2028");

        Instant january = Instant.parse("2029-01-15T00:00:00Z");
        assertThat(Compliance.check(invoices, STARTS, january).plan().stages()).extracting(Due::state)
                .containsExactly(State.SUSPENDED);
        RetentionHolds.release(audit, Instant.parse("2029-02-01T00:00:00Z"));
        assertThat(Compliance.check(invoices, STARTS, Instant.parse("2029-02-01T00:00:00Z")).plan().stages())
                .extracting(Due::state).containsExactly(State.DUE);
    }

    @Test
    void deletionClassesGiveTheTerm() {
        DeletionClass afterContract = DeletionClasses.onRows(DeletionClasses.create(governance, "contract-end-3y",
                PeriodStarts.event(contractEnded), "P3Y", customers));
        DeletionClass afterRejection = DeletionClasses.onRows(DeletionClasses.create(governance, "rejection-6m",
                PeriodStarts.event(applicationRejected), "P6M", applications));
        assertThat(afterContract.getBody().getBody()).isEqualTo("class: delete P3Y after event contractEnded");

        RetentionSchedule schedule = RetentionSchedules.create(governance, "customer-rows", DataScope.ROW,
                PeriodStarts.event(contractEnded, contractEnd), customers);
        Stages.delete(schedule, "delete", null);
        assertThat(schedule.getBody().getBody()).isEqualTo("from event contractEnded (contract_end): delete");
        assertThat(DeletionClasses.effective(customers, DataScope.ROW)).containsSame(afterContract);
        assertThat(DeletionClasses.effective(applications, DataScope.ROW)).containsSame(afterRejection);
        assertThat(Compliance.check(customers, STARTS, NOW).plan().next()).map(Due::at)
                .contains(Instant.parse("2027-03-31T22:00:00Z"));
    }

    @Test
    void legalSources() {
        RetentionObligation vouchers = Obligations.onRows(Obligations.retain(governance, "vouchers-8y", "P8Y",
                endOfYear(), invoices));
        LegalSource ao = LegalSources.cite(LegalSources.validity(LegalSources.provision(
                LegalSources.create(law, "ao-147", AO_147, LegalSources.STATUTE), "§ 147 (3)", "DE"), "2025-01-01",
                null), vouchers);
        assertThat(LegalSources.of(vouchers)).containsExactly(ao);
        assertThat(LegalSources.inForce(ao, NOW)).isTrue();

        RetentionObligation before = Obligations.onRows(Obligations.retain(governance, "vouchers-10y", "P10Y",
                endOfYear(), invoices));
        LegalSources.cite(LegalSources.validity(LegalSources.provision(
                LegalSources.create(law, "ao-147-until-2024", AO_147, LegalSources.STATUTE), "§ 147 (3)", "DE"), null,
                "2025-01-01"), before);
        assertThat(ObligationsModelValidator.check(company, NOW)).singleElement().satisfies(f -> {
            assertThat(f.rule()).isEqualTo("O8");
            assertThat(f.severity()).isEqualTo(Severity.WARNING);
            assertThat(f.message()).isEqualTo("RetentionObligation 'vouchers-10y': its source ao-147-until-2024 is "
                    + "not in force (valid until 2025-01-01).");
        });
    }

    // 2 Which duties apply

    @Test
    void whichDutiesApply() {
        RetentionObligation books = Obligations.onRows(Obligations.retain(governance, "invoices-8y", "P8Y",
                endOfYear(), invoices));
        DeletionObligation names = Obligations.delete(governance, "customer-name-1y", "P1Y", endOfYear(),
                customerName);
        RetentionHold audit = RetentionHolds.create(governance, "tax-audit-2028", "tax audit 2028",
                Instant.parse("2028-10-01T00:00:00Z"), sales);
        DeletionClass afterContract = DeletionClasses.onRows(DeletionClasses.create(governance, "contract-end-3y",
                PeriodStarts.event(contractEnded), "P3Y", customers));

        assertThat(Compliance.retentions(customerName)).containsExactly(books);
        assertThat(Compliance.deletions(customerName)).containsExactly(names);
        assertThat(Compliance.deletions(total)).isEmpty();
        assertThat(RetentionHolds.of(total)).containsExactly(audit);
        assertThat(RetentionHolds.active(total, NOW)).isEmpty();
        assertThat(DeletionClasses.effective(email, DataScope.ROW)).containsSame(afterContract);
        assertThat(DeletionClasses.effective(email, DataScope.VALUE)).isEmpty();
    }

    // 3 What the validator reports

    @Test
    void o1_aDutyOfRowsOnAColumn() {
        Obligations.onRows(Obligations.retain(governance, "emails-1y", "P1Y", PeriodStarts.valueTime(), email));
        assertThat(errors()).containsExactly("O1");
    }

    @Test
    void o2_aHoldThatEndsBeforeItBegins() {
        RetentionHolds.release(RetentionHolds.create(governance, "tax-audit-2028", "tax audit 2028",
                Instant.parse("2028-10-01T00:00:00Z"), sales), Instant.parse("2028-09-01T00:00:00Z"));
        assertThat(errors()).containsExactly("O2");
    }

    @Test
    void o3_aDeleteBeforeTheMinimum() {
        Obligations.onRows(Obligations.retain(governance, "invoices-8y", "P8Y", endOfYear(), invoices));
        RetentionSchedules.rows(governance, "invoice-rows", "P6Y", endOfYear(), invoices);
        assertThat(errors()).containsExactly("O3");
    }

    @Test
    void o4_aDeleteAfterTheMaximum() {
        Obligations.onRows(Obligations.delete(governance, "customers-3y", "P3Y",
                PeriodStarts.event(contractEnded, contractEnd), customers));
        RetentionSchedules.rows(governance, "customer-rows", "P5Y", PeriodStarts.event(contractEnded, contractEnd),
                customers);
        assertThat(errors()).containsExactly("O4");
    }

    @Test
    void o5_noRestriction() {
        Obligations.onRows(Obligations.retain(governance, "invoices-8y", "P8Y", endOfYear(), invoices));
        Obligations.onRows(Obligations.delete(governance, "invoice-privacy-1y", "P1Y", endOfYear(), invoices));
        RetentionSchedules.rows(governance, "invoice-rows", "P8Y", endOfYear(), invoices);
        assertThat(errors()).containsExactly("O5");
    }

    @Test
    void o6_otherStartsAndApproximatePeriods() {
        Obligations.onRows(Obligations.retain(governance, "invoices-8y", "P8Y", endOfYear(), invoices));
        RetentionSchedule schedule = RetentionSchedules.rows(governance, "invoice-rows", "P8Y",
                PeriodStarts.feature(issuedOn), invoices);
        assertThat(rules(Severity.WARNING)).contains("O6");
        RetentionSchedules.startAt(schedule, endOfYear());
        schedule.getStage().get(0).setAfter("P2922D");
        assertThat(rules(Severity.WARNING)).contains("O6");
        assertThat(errors()).isEmpty();
    }

    @Test
    void o7_aTermFromTheStageAndFromTheClass() {
        DeletionClasses.onRows(DeletionClasses.create(governance, "contract-end-3y", PeriodStarts.event(contractEnded),
                "P3Y", customers));
        RetentionSchedules.rows(governance, "customer-rows", "P3Y", PeriodStarts.event(contractEnded, contractEnd),
                customers);
        assertThat(errors()).containsExactly("O7");
    }

    @Test
    void o8_aSourceNoLongerInForce() {
        RetentionObligation before = Obligations.onRows(Obligations.retain(governance, "vouchers-10y", "P10Y",
                endOfYear(), invoices));
        LegalSources.cite(LegalSources.validity(LegalSources.create(law, "ao-147-until-2024", AO_147,
                LegalSources.STATUTE), null, "2025-01-01"), before);
        assertThat(rules(Severity.WARNING)).containsExactly("O8");
    }

    @Test
    void o9_aBodyEditedByHand() {
        RetentionObligation books = Obligations.onRows(Obligations.retain(governance, "invoices-8y", "P8Y",
                endOfYear(), invoices));
        LegalSources.cite(LegalSources.create(law, "ao-147", AO_147, LegalSources.STATUTE), books);
        books.getBody().setBody("keep at least P1Y");
        assertThat(rules(Severity.WARNING)).containsExactly("O9");
    }

    // 4 In XMI

    /** Lines of the XMI the examples serialize to. */
    private static final String XMI = """
            <ownedElement xsi:type="obligations:RetentionObligation" name="invoices-8y" \
            constrainedElement="_invoices" scope="row" minimum="P8Y" immutable="true">
            <body body="keep at least P8Y from issued_on, end of year in Europe/Berlin; immutable" \
            language="daanse.lifecycle"/>
            <start xsi:type="lifecycle:FeatureTime" alignment="endOfYear" zone="Europe/Berlin" \
            feature="_invoices.issued_on"/>
            </ownedElement>
            <ownedElement xsi:type="obligations:DeletionObligation" name="invoice-privacy-1y" \
            constrainedElement="_invoices" scope="row" maximum="P1Y">
            <body body="delete within P1Y from issued_on, end of year in Europe/Berlin" \
            language="daanse.lifecycle"/>
            <start xsi:type="lifecycle:FeatureTime" alignment="endOfYear" zone="Europe/Berlin" \
            feature="_invoices.issued_on"/>
            </ownedElement>
            <ownedElement xsi:type="lifecycle:RetentionSchedule" name="invoice-rows" \
            constrainedElement="_invoices" scope="row">
            <body body="from issued_on, end of year in Europe/Berlin: restrict after P1Y, delete after P8Y" \
            language="daanse.lifecycle"/>
            <start xsi:type="lifecycle:FeatureTime" alignment="endOfYear" zone="Europe/Berlin" \
            feature="_invoices.issued_on"/>
            <stage xsi:type="lifecycle:Restrict" name="restrict" after="P1Y"/>
            <stage xsi:type="lifecycle:Delete" name="delete" after="P8Y"/>
            </ownedElement>
            <ownedElement xsi:type="obligations:RetentionHold" name="tax-audit-2028" constrainedElement="_sales" \
            since="2028-10-01T00:00:00Z" reason="tax audit 2028" holder="_legal">
            <body body="hold since 2028-10-01T00:00:00Z: tax audit 2028" language="daanse.lifecycle"/>
            </ownedElement>
            <ownedElement xsi:type="obligations:DeletionClass" name="contract-end-3y" \
            constrainedElement="_customers" scope="row" term="P3Y">
            <body body="class: delete P3Y after event contractEnded" language="daanse.lifecycle"/>
            <start xsi:type="lifecycle:EventTime" event="_contractEnded"/>
            </ownedElement>
            <ownedElement xsi:type="lifecycle:RetentionSchedule" name="customer-rows" \
            constrainedElement="_customers" scope="row">
            <body body="from event contractEnded (contract_end): delete" language="daanse.lifecycle"/>
            <start xsi:type="lifecycle:EventTime" event="_contractEnded" feature="_customers.contract_end"/>
            <stage xsi:type="lifecycle:Delete" name="delete"/>
            </ownedElement>
            <ownedElement xsi:type="obligations:LegalSource" name="ao-147" \
            reference="https://www.gesetze-im-internet.de/ao_1977/__147.html" type="statute" \
            modelElement="_invoices-8y" jurisdiction="DE" provision="§ 147 (3)" validFrom="2025-01-01"/>
            <ownedElement xsi:type="obligations:LegalSource" name="gdpr-17" \
            reference="https://eur-lex.europa.eu/eli/reg/2016/679/oj" type="statute" \
            modelElement="_invoice-privacy-1y" jurisdiction="EU" provision="Art. 17 (1) (a)"/>
            <ownedElement xsi:type="relational:Schema" name="sales" constraint="_tax-audit-2028">
            <ownedElement xsi:type="relational:Table" name="invoices" \
            constraint="_invoices-8y _invoice-privacy-1y _invoice-rows">
            <ownedElement xsi:type="relational:Table" name="customers" \
            constraint="_contract-end-3y _customer-rows">
            """;

    @Test
    void theXmiOfTheDocumentIsWhatAModelSerializesTo() throws Exception {
        RetentionObligation books = Obligations.onRows(Obligations.immutable(
                Obligations.retain(governance, "invoices-8y", "P8Y", endOfYear(), invoices)));
        DeletionObligation privacy = Obligations.onRows(Obligations.delete(governance, "invoice-privacy-1y", "P1Y",
                endOfYear(), invoices));
        RetentionSchedule invoiceRows = RetentionSchedules.create(governance, "invoice-rows", DataScope.ROW,
                endOfYear(), invoices);
        Stages.restrict(invoiceRows, "restrict", "P1Y");
        Stages.delete(invoiceRows, "delete", "P8Y");
        RetentionHolds.holder(RetentionHolds.create(governance, "tax-audit-2028", "tax audit 2028",
                Instant.parse("2028-10-01T00:00:00Z"), sales), legal);
        DeletionClasses.onRows(DeletionClasses.create(governance, "contract-end-3y", PeriodStarts.event(contractEnded),
                "P3Y", customers));
        RetentionSchedule customerRows = RetentionSchedules.create(governance, "customer-rows", DataScope.ROW,
                PeriodStarts.event(contractEnded, contractEnd), customers);
        Stages.delete(customerRows, "delete", null);
        LegalSources.cite(LegalSources.validity(LegalSources.provision(
                LegalSources.create(law, "ao-147", AO_147, LegalSources.STATUTE), "§ 147 (3)", "DE"), "2025-01-01",
                null), books);
        LegalSources.cite(LegalSources.provision(LegalSources.create(law, "gdpr-17",
                "https://eur-lex.europa.eu/eli/reg/2016/679/oj", LegalSources.STATUTE), "Art. 17 (1) (a)", "EU"),
                privacy);
        assertThat(ObligationsModelValidator.check(company, NOW)).isEmpty();
        assertThat(Compliance.check(invoices, STARTS, NOW).compliant()).isTrue();

        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
        Resource r = rs.createResource(URI.createURI("company.xmi"));
        r.getContents().add(company);
        r.getContents().add(erp);
        ShortIds.assign(r);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        r.save(out, Map.of(XMLResource.OPTION_ENCODING, "UTF-8"));
        String xmi = out.toString(StandardCharsets.UTF_8).replaceAll(" xmi:id=\"[^\"]*\"", "");
        Files.writeString(Path.of(System.getProperty("user.dir"), "target/obligations-across-cwm.xmi"), xmi,
                StandardCharsets.UTF_8);

        for (String line : XMI.split("\n")) {
            assertThat(xmi).as(line).contains(line.trim());
        }
    }

    private FeatureTime endOfYear() {
        return PeriodStarts.aligned(PeriodStarts.feature(issuedOn), Alignment.END_OF_YEAR, "Europe/Berlin");
    }

    private List<String> errors() {
        return rules(Severity.ERROR);
    }

    private List<String> rules(Severity severity) {
        return ObligationsModelValidator.check(company, NOW).stream().filter(f -> f.severity() == severity)
                .map(Finding::rule).toList();
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

    private Column column(Table table, String name, String type) {
        return Columns.create(table, name, SQLSimpleTypes.ownedIn(erp, type));
    }
}
