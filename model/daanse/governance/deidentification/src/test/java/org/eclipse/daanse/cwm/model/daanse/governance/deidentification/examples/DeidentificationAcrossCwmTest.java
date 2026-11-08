/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.deidentification.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.daanse.cwm.model.daanse.governance.deidentification.AttributeRole.IDENTIFYING;
import static org.eclipse.daanse.cwm.model.daanse.governance.deidentification.AttributeRole.QUASI_IDENTIFYING;
import static org.eclipse.daanse.cwm.model.daanse.governance.deidentification.AttributeRole.SENSITIVE;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.analysis.olap.Cube;
import org.eclipse.daanse.cwm.model.cwm.analysis.olap.Measure;
import org.eclipse.daanse.cwm.model.cwm.analysis.olap.OlapFactory;
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
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Consistency;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationPolicy;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DisclosureControl;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DisclosureMethod;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DiversityVariant;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Outcome;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.PseudonymMethod;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.PseudonymizationDomain;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util.DeidentificationModelValidator;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util.DeidentificationStages;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util.DisclosureControls;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util.Policies;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util.PrivacyModels;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util.PseudonymizationDomains;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util.Treatments;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.EventTime;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Finding;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Severity;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.PeriodStarts;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.Due;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.Starts;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.Suspension;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.RetentionSchedules;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.DeletionObligation;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.Compliance;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.Compliance.Violation;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.Obligations;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;

/**
 * De-identification at the places of CWM: policies feature by feature, privacy models, a
 * pseudonymization domain and the end of its linkage, the stages in a schedule beside a deletion
 * obligation, disclosure control of a cube, which policy applies, what the validator reports, and
 * the XMI of a model with policies and stages.
 */
class DeidentificationAcrossCwmTest {

    /** A record collected on 3 February 2025, a study that ended on 30 June 2026. */
    private static final Instant COLLECTED = Instant.parse("2025-02-03T00:00:00Z");
    private static final Instant STUDY_END = Instant.parse("2026-06-30T00:00:00Z");
    private static final Starts STARTS = start -> Optional.of(start instanceof EventTime ? STUDY_END : COLLECTED);
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

    private final Model hospital = Models.create("hospital");
    private final Package governance = Packages.create(hospital, "governance");
    private final Event studyEnded = event("studyEnded");
    private final ResponsibleParty trustCentre = party("trust-centre",
            "trusted third party that holds the pseudonyms of the study");
    private final ResponsibleParty researchPartner = party("research-partner",
            "university clinic that analyses the study data");
    private final ResponsibleParty statisticsOffice = party("statistics-office",
            "state statistics office that publishes counts");
    private final Catalog his = Catalogs.create("his");
    private final Schema study = Schemas.create(his, "study");
    private final Table records = Tables.create(study, "records");
    private final Column insuranceNo = column(records, "insurance_no", "VARCHAR");
    private final Column name = column(records, "name", "VARCHAR");
    private final Column birthDate = column(records, "birth_date", "DATE");
    private final Column zip = column(records, "zip", "VARCHAR");
    private final Column sex = column(records, "sex", "VARCHAR");
    private final Column diagnosis = column(records, "diagnosis", "VARCHAR");
    private final Column collectedOn = column(records, "collected_on", "DATE");
    private final Schema trust = Schemas.create(his, "trust");
    private final Table pseudonyms = Tables.create(trust, "pseudonyms");

    // 1 Policies and stages

    @Test
    void aPolicyFeatureByFeature() {
        DeidentificationPolicy research = research(domain());
        assertThat(research.getBody().getBody()).isEqualTo("pseudonymous for research-partner; insurance_no "
                + "pseudonymized by counter global in study-pseudonyms, name removed, birth_date generalized to month, "
                + "zip unchanged, sex unchanged, diagnosis unchanged, collected_on shifted by up to P14D perRow");
        assertThat(DeidentificationModelValidator.check(hospital)).extracting(Finding::rule).containsExactly("D5");
    }

    @Test
    void anAnonymousResultAndItsPrivacyModels() {
        DeidentificationPolicy statistics = statistics();
        assertThat(statistics.getBody().getBody()).isEqualTo("anonymous for statistics-office; 5-anonymity, distinct "
                + "2-diversity of diagnosis; suppression up to 0.02; insurance_no removed, name removed, birth_date "
                + "generalized to year, zip truncated to 3, sex unchanged, diagnosis unchanged, collected_on "
                + "generalized to quarter");
        assertThat(DeidentificationModelValidator.check(hospital)).isEmpty();
    }

    @Test
    void aDomainAndTheEndOfItsLinkage() {
        PseudonymizationDomain domain = domain();
        RetentionSchedule keys = keys(domain);
        assertThat(keys.getBody().getBody()).isEqualTo("from event studyEnded: destroy linkage study-pseudonyms "
                + "after P10Y");
        assertThat(PseudonymizationDomains.destroyedBy(domain)).singleElement()
                .satisfies(d -> assertThat(d.getName()).isEqualTo("destroy-linkage"));
        assertThat(Retention.plan(domain, STARTS, NOW, Suspension.NONE).next()).map(Due::at)
                .contains(Instant.parse("2036-06-30T00:00:00Z"));
        assertThat(DeidentificationModelValidator.check(hospital)).isEmpty();
    }

    @Test
    void stagesBesideADeletionObligation() {
        DeidentificationPolicy research = research(domain());
        DeidentificationPolicy statistics = statistics();
        RetentionSchedule schedule = rows(research, statistics);
        assertThat(schedule.getBody().getBody()).isEqualTo("from collected_on: pseudonymize records-research after "
                + "P6M, anonymize records-statistics after P10Y");
        DeletionObligation consent = Obligations.onRows(Obligations.anonymizationSatisfies(Obligations.delete(
                governance, "records-10y", "P10Y", PeriodStarts.feature(collectedOn), records)));

        Compliance.Report report = Compliance.check(records, STARTS, NOW);
        assertThat(report.compliant()).isTrue();
        assertThat(report.plan().current()).map(d -> d.step().stage().getName()).contains("pseudonymize");
        assertThat(report.plan().next()).map(Due::at).contains(Instant.parse("2035-02-03T00:00:00Z"));

        consent.setAnonymizationSatisfies(false);
        assertThat(Compliance.check(records, STARTS, NOW).violations()).extracting(Violation::rule, Violation::message)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("O4", "stage anonymize does not delete the "
                        + "data, and records-10y is not satisfied by making it anonymous"));
        assertThat(LifecycleModelValidator.check(hospital)).isEmpty();
    }

    @Test
    void disclosureControlOfACube() {
        org.eclipse.daanse.cwm.model.cwm.analysis.olap.Schema reporting = OlapFactory.eINSTANCE.createSchema();
        reporting.setName("reporting");
        hospital.getOwnedElement().add(reporting);
        Cube admissions = OlapFactory.eINSTANCE.createCube();
        admissions.setName("admissions");
        reporting.getCube().add(admissions);
        Measure cases = measure(admissions, "cases");
        Measure costs = measure(admissions, "costs");

        DisclosureControl cells = DisclosureControls.minimumCellCount(DisclosureControls.create(governance,
                "admissions-cells", DisclosureMethod.CELL_KEY, admissions), 3);
        DisclosureControl dominance = DisclosureControls.dominance(DisclosureControls.create(governance,
                "costs-dominance", DisclosureMethod.SUPPRESSION, costs), 2, 85);
        assertThat(cells.getBody().getBody()).isEqualTo("at least 3 per cell; cellKey");
        assertThat(dominance.getBody().getBody()).isEqualTo("(2, 85) dominance; suppression");
        assertThat(DisclosureControls.effective(cases)).containsSame(cells);
        assertThat(DisclosureControls.effective(costs)).containsSame(dominance);
        assertThat(DeidentificationModelValidator.check(hospital)).isEmpty();
    }

    // 2 Which policy applies

    @Test
    void whichPolicyApplies() {
        DeidentificationPolicy research = research(domain());
        DeidentificationPolicy statistics = statistics();
        assertThat(Policies.of(records)).containsExactly(research, statistics);
        assertThat(Policies.treatmentOf(statistics, zip)).map(Treatments::render).contains("zip truncated to 3");
        assertThat(Policies.treatmentOf(research, zip)).map(Treatments::render).contains("zip unchanged");
        assertThat(Policies.of(pseudonyms)).isEmpty();
    }

    // 3 What the validator reports

    @Test
    void d1_aFeatureWithoutTreatment() {
        DeidentificationPolicy statistics = statistics();
        statistics.getTreatment().removeIf(t -> t.getFeature() == collectedOn);
        Policies.refreshed(statistics);
        assertThat(rules(Severity.WARNING)).containsExactly("D1");
    }

    @Test
    void d2_anIdentifierKeptInAnAnonymousResult() {
        DeidentificationPolicy statistics = statistics();
        statistics.getTreatment().removeIf(t -> t.getFeature() == name);
        Treatments.unchanged(statistics, name, IDENTIFYING);
        assertThat(rules(Severity.ERROR)).containsExactly("D2");
    }

    @Test
    void d3_aSensitiveFeatureThatIsNot() {
        PrivacyModels.lDiversity(statistics(), 2, DiversityVariant.DISTINCT, zip);
        assertThat(rules(Severity.ERROR)).containsExactly("D3");
    }

    @Test
    void d4_aGlobalPseudonymWithoutDomain() {
        DeidentificationPolicy research = Policies.create(governance, "records-research", Outcome.PSEUDONYMOUS,
                records);
        Treatments.pseudonymize(research, insuranceNo, IDENTIFYING, PseudonymMethod.HASH, Consistency.GLOBAL, null);
        assertThat(rules(Severity.ERROR)).containsExactly("D4");
    }

    @Test
    void d5_aMappingKeptWithoutEnd() {
        domain();
        assertThat(rules(Severity.WARNING)).containsExactly("D5");
    }

    @Test
    void d6_anonymizingByAPseudonymousPolicy() {
        PseudonymizationDomain domain = domain();
        keys(domain);
        DeidentificationPolicy research = research(domain);
        RetentionSchedule schedule = RetentionSchedules.create(governance, "record-rows", DataScope.ROW,
                PeriodStarts.feature(collectedOn), records);
        DeidentificationStages.anonymize(schedule, "anonymize", "P10Y", research);
        assertThat(rules(Severity.ERROR)).containsExactly("D6");
    }

    @Test
    void d7_anAudienceWithoutResponsibility() {
        ResponsibleParty someone = party("someone", null);
        Policies.audience(statistics(), someone);
        assertThat(rules(Severity.WARNING)).containsExactly("D7");
    }

    @Test
    void d8_aControlOnASchemaThatSaysNothing() {
        DisclosureControls.create(governance, "study-cells", DisclosureMethod.SUPPRESSION, study);
        assertThat(DeidentificationModelValidator.check(hospital)).extracting(Finding::severity, Finding::rule)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(Severity.WARNING, "D8"),
                        org.assertj.core.groups.Tuple.tuple(Severity.ERROR, "D8"));
    }

    @Test
    void d9_aBodyEditedByHand() {
        statistics().getBody().setBody("anonymous");
        assertThat(rules(Severity.WARNING)).containsExactly("D9");
    }

    // 4 In XMI

    /** Lines of the XMI the examples serialize to. */
    private static final String XMI = """
            <ownedElement xsi:type="deid:PseudonymizationDomain" name="study-pseudonyms" \
            constraint="_pseudonym-keys" keyHolder="_trust-centre" mapping="_pseudonyms"/>
            <ownedElement xsi:type="lifecycle:RetentionSchedule" name="pseudonym-keys" \
            constrainedElement="_study-pseudonyms">
            <body body="from event studyEnded: destroy linkage study-pseudonyms after P10Y" \
            language="daanse.lifecycle"/>
            <start xsi:type="lifecycle:EventTime" event="_studyEnded"/>
            <stage xsi:type="deid:DestroyLinkage" name="destroy-linkage" after="P10Y" \
            domain="_study-pseudonyms"/>
            </ownedElement>
            <ownedElement xsi:type="deid:DeidentificationPolicy" name="records-research" \
            constrainedElement="_records" outcome="pseudonymous" audience="_research-partner">
            <treatment xsi:type="deid:Pseudonymization" feature="_records.insurance_no" role="identifying" \
            domain="_study-pseudonyms"/>
            <treatment xsi:type="deid:Removal" feature="_records.name" role="identifying"/>
            <treatment xsi:type="deid:Generalization" feature="_records.birth_date" role="quasiIdentifying" \
            granularity="month"/>
            <treatment xsi:type="deid:Unchanged" feature="_records.zip" role="quasiIdentifying"/>
            <treatment xsi:type="deid:Unchanged" feature="_records.sex" role="quasiIdentifying"/>
            <treatment xsi:type="deid:Unchanged" feature="_records.diagnosis" role="sensitive"/>
            <treatment xsi:type="deid:DateShift" feature="_records.collected_on" role="quasiIdentifying" \
            maximum="P14D" consistency="perRow"/>
            </ownedElement>
            <ownedElement xsi:type="deid:DeidentificationPolicy" name="records-statistics" \
            constrainedElement="_records" outcome="anonymous" audience="_statistics-office" \
            suppressionLimit="0.02">
            <treatment xsi:type="deid:Truncation" feature="_records.zip" role="quasiIdentifying" keep="3"/>
            <model xsi:type="deid:KAnonymity" k="5"/>
            <model xsi:type="deid:LDiversity" l="2" sensitive="_records.diagnosis"/>
            </ownedElement>
            <ownedElement xsi:type="lifecycle:RetentionSchedule" name="record-rows" \
            constrainedElement="_records" scope="row">
            <body body="from collected_on: pseudonymize records-research after P6M, anonymize records-statistics \
            after P10Y" \
            language="daanse.lifecycle"/>
            <start xsi:type="lifecycle:FeatureTime" feature="_records.collected_on"/>
            <stage xsi:type="deid:Pseudonymize" name="pseudonymize" after="P6M" policy="_records-research"/>
            <stage xsi:type="deid:Anonymize" name="anonymize" after="P10Y" policy="_records-statistics"/>
            </ownedElement>
            <ownedElement xsi:type="relational:Table" name="records" \
            constraint="_records-research _records-statistics _record-rows">
            """;

    @Test
    void theXmiOfTheDocumentIsWhatAModelSerializesTo() throws Exception {
        PseudonymizationDomain domain = domain();
        keys(domain);
        DeidentificationPolicy research = research(domain);
        DeidentificationPolicy statistics = statistics();
        rows(research, statistics);
        assertThat(DeidentificationModelValidator.check(hospital)).isEmpty();

        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
        Resource r = rs.createResource(URI.createURI("hospital.xmi"));
        r.getContents().add(hospital);
        r.getContents().add(his);
        ShortIds.assign(r);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        r.save(out, Map.of(XMLResource.OPTION_ENCODING, "UTF-8"));
        String xmi = out.toString(StandardCharsets.UTF_8).replaceAll(" xmi:id=\"[^\"]*\"", "");
        Files.writeString(Path.of(System.getProperty("user.dir"), "target/deidentification-across-cwm.xmi"), xmi,
                StandardCharsets.UTF_8);

        for (String line : XMI.split("\n")) {
            assertThat(xmi).as(line).contains(line.trim());
        }
    }

    private PseudonymizationDomain domain() {
        return PseudonymizationDomains.mapping(PseudonymizationDomains.create(governance, "study-pseudonyms",
                trustCentre), pseudonyms);
    }

    private RetentionSchedule keys(PseudonymizationDomain domain) {
        RetentionSchedule keys = RetentionSchedules.create(governance, "pseudonym-keys",
                PeriodStarts.event(studyEnded), domain);
        DeidentificationStages.destroyLinkage(keys, "destroy-linkage", "P10Y", domain);
        return keys;
    }

    private DeidentificationPolicy research(PseudonymizationDomain domain) {
        DeidentificationPolicy research = Policies.audience(Policies.create(governance, "records-research",
                Outcome.PSEUDONYMOUS, records), researchPartner);
        Treatments.pseudonymize(research, insuranceNo, IDENTIFYING, PseudonymMethod.COUNTER, Consistency.GLOBAL,
                domain);
        Treatments.remove(research, name, IDENTIFYING);
        Treatments.generalizeTo(research, birthDate, QUASI_IDENTIFYING, "month");
        Treatments.unchanged(research, zip, QUASI_IDENTIFYING);
        Treatments.unchanged(research, sex, QUASI_IDENTIFYING);
        Treatments.unchanged(research, diagnosis, SENSITIVE);
        Treatments.shiftDates(research, collectedOn, QUASI_IDENTIFYING, "P14D", Consistency.PER_ROW);
        return research;
    }

    private DeidentificationPolicy statistics() {
        DeidentificationPolicy statistics = Policies.suppressionLimit(Policies.audience(Policies.create(governance,
                "records-statistics", Outcome.ANONYMOUS, records), statisticsOffice), 0.02);
        Treatments.remove(statistics, insuranceNo, IDENTIFYING);
        Treatments.remove(statistics, name, IDENTIFYING);
        Treatments.generalizeTo(statistics, birthDate, QUASI_IDENTIFYING, "year");
        Treatments.truncate(statistics, zip, QUASI_IDENTIFYING, 3);
        Treatments.unchanged(statistics, sex, QUASI_IDENTIFYING);
        Treatments.unchanged(statistics, diagnosis, SENSITIVE);
        Treatments.generalizeTo(statistics, collectedOn, QUASI_IDENTIFYING, "quarter");
        PrivacyModels.kAnonymity(statistics, 5);
        PrivacyModels.lDiversity(statistics, 2, DiversityVariant.DISTINCT, diagnosis);
        return statistics;
    }

    private RetentionSchedule rows(DeidentificationPolicy research, DeidentificationPolicy statistics) {
        RetentionSchedule schedule = RetentionSchedules.create(governance, "record-rows", DataScope.ROW,
                PeriodStarts.feature(collectedOn), records);
        DeidentificationStages.pseudonymize(schedule, "pseudonymize", "P6M", research);
        DeidentificationStages.anonymize(schedule, "anonymize", "P10Y", statistics);
        return schedule;
    }

    private List<String> rules(Severity severity) {
        return DeidentificationModelValidator.check(hospital).stream().filter(f -> f.severity() == severity)
                .map(Finding::rule).toList();
    }

    private Event event(String eventName) {
        Event event = BehavioralFactory.eINSTANCE.createEvent();
        event.setName(eventName);
        governance.getOwnedElement().add(event);
        return event;
    }

    private ResponsibleParty party(String partyName, String responsibility) {
        ResponsibleParty party = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
        party.setName(partyName);
        party.setResponsibility(responsibility);
        governance.getOwnedElement().add(party);
        return party;
    }

    private Column column(Table table, String columnName, String type) {
        return Columns.create(table, columnName, SQLSimpleTypes.ownedIn(his, type));
    }

    private static Measure measure(Cube cube, String measureName) {
        Measure m = OlapFactory.eINSTANCE.createMeasure();
        m.setName(measureName);
        cube.getFeature().add(m);
        return m;
    }
}
