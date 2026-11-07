/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.obligations.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Constraint;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Alignment;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.FeatureTime;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Finding;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Severity;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.PeriodStarts;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.RetentionSchedules;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Stages;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.DeletionObligation;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.LegalSource;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.Obligation;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.RetentionHold;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.RetentionObligation;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;

/** One positive and at least one negative case per rule of the validator. */
class ObligationsModelValidatorTest {

    private final Lab lab = new Lab();
    private final LegalSource ao = LegalSources.validity(LegalSources.create(lab.law, "ao-147",
            "https://www.gesetze-im-internet.de/ao_1977/__147.html", LegalSources.STATUTE), "2025-01-01", null);

    private FeatureTime endOfYear() {
        return PeriodStarts.aligned(PeriodStarts.feature(lab.issuedOn), Alignment.END_OF_YEAR, "Europe/Berlin");
    }

    private <O extends Obligation> O rows(O obligation) {
        obligation.setScope(DataScope.ROW);
        LegalSources.cite(ao, obligation);
        return obligation;
    }

    private RetentionObligation keep(String minimum) {
        return rows(Obligations.retain(lab.governance, "keep-" + minimum, minimum, endOfYear(), lab.invoice));
    }

    private DeletionObligation delete(String maximum) {
        return rows(Obligations.delete(lab.governance, "delete-" + maximum, maximum, endOfYear(), lab.invoice));
    }

    private RetentionSchedule schedule(String... stages) {
        RetentionSchedule schedule = RetentionSchedules.create(lab.governance, "invoice-rows", DataScope.ROW, endOfYear(),
                lab.invoice);
        for (String stage : stages) {
            String[] kind = stage.split(" ");
            if (kind[0].equals("restrict")) {
                Stages.restrict(schedule, "restrict", kind[1]);
            } else {
                Stages.delete(schedule, "delete", kind[1]);
            }
        }
        return schedule;
    }

    @Test
    void schedulesThatKeepTheirDutiesHaveNoFinding() {
        keep("P8Y");
        delete("P10Y");
        schedule("delete P10Y");
        RetentionHolds.create(lab.governance, "audit", "tax audit", Instant.parse("2026-11-01T00:00:00Z"), lab.invoice);
        DeletionClasses.create(lab.governance, "readings", PeriodStarts.valueTime(), "P90D", lab.reading);
        assertThat(ObligationsModelValidator.check(lab.model)).isEmpty();
    }

    @Test
    void o1_aDutyGovernsData() {
        Constraint positive = CoreFactory.eINSTANCE.createConstraint();
        positive.setName("positive");
        lab.data.getOwnedElement().add(positive);
        rows(Obligations.retain(lab.governance, "on-a-rule", "P1Y", PeriodStarts.valueTime(), lab.invoice))
                .getConstrainedElement().add(positive);
        DeletionObligation onAFeature = Obligations.delete(lab.governance, "rows-of-a-feature", "P1Y",
                PeriodStarts.valueTime(), lab.amount);
        rows(onAFeature);
        List<Finding> findings = ObligationsModelValidator.check(lab.model);
        assertThat(findings).extracting(Finding::rule).containsExactly("O1", "O1", "O4");
        assertThat(findings.get(0).message()).contains("not the Constraint lab.data.positive");
        assertThat(findings.get(1).message()).contains("a rule of rows governs classifiers or namespaces");
    }

    @Test
    void o2_literalsParseAndAHoldEndsAfterItBegins() {
        keep("P8Y").setMinimum("P8Q");
        RetentionHold hold = RetentionHolds.create(lab.governance, "audit", "tax audit",
                Instant.parse("2026-11-01T00:00:00Z"), lab.invoice);
        hold.setUntil("2026-10-01T00:00:00Z");
        RetentionHolds.refreshed(hold);
        DeletionClasses.create(lab.governance, "bound", PeriodStarts.event(lab.contractEnded, lab.contractEnd), "P3Y",
                lab.customer);
        List<Finding> findings = ObligationsModelValidator.check(lab.model);
        assertThat(findings).filteredOn(f -> f.rule().equals("O2")).extracting(Finding::message)
                .anySatisfy(m -> assertThat(m).contains("'P8Q' is no ISO-8601 period"))
                .anySatisfy(m -> assertThat(m).contains("not after it begins"))
                .anySatisfy(m -> assertThat(m).contains("the start of a deletion class is abstract"));
    }

    @Test
    void o3_noDestructiveStageBeforeTheMinimum() {
        keep("P8Y");
        RetentionSchedule early = schedule("delete P5Y");
        List<Finding> findings = ObligationsModelValidator.check(lab.model);
        assertThat(findings).extracting(Finding::rule).containsExactly("O3");
        assertThat(findings.get(0).message()).contains("stage delete of invoice-rows is due after P5Y",
                "before the data is kept for P8Y");
        EcoreUtil.delete(early);
        schedule("delete P8Y");
        assertThat(ObligationsModelValidator.check(lab.model)).isEmpty();
    }

    @Test
    void o4_aDeleteByTheMaximum() {
        delete("P3Y");
        RetentionSchedule none = schedule();
        Stages.tier(none, "cold", "P1Y", "cold", false);
        assertThat(ObligationsModelValidator.check(lab.model)).extracting(Finding::message)
                .singleElement().satisfies(m -> assertThat(m).contains("no stage deletes the data within P3Y"));
        Stages.delete(none, "delete", "P5Y");
        assertThat(ObligationsModelValidator.check(lab.model)).extracting(Finding::message)
                .singleElement().satisfies(m -> assertThat(m).contains("deletes after P5Y, not within P3Y"));
        none.getStage().get(1).setAfter("P3Y");
        RetentionSchedules.refreshed(none);
        assertThat(ObligationsModelValidator.check(lab.model)).isEmpty();
    }

    @Test
    void o5_aLongerRetentionRestrictsAndDeletesAtItsEnd() {
        keep("P10Y");
        delete("P3Y");
        RetentionSchedule schedule = schedule("delete P10Y");
        assertThat(ObligationsModelValidator.check(lab.model)).extracting(Finding::rule).containsExactly("O5");
        EcoreUtil.delete(schedule);
        RetentionSchedule restricted = schedule("restrict P3Y", "delete P10Y");
        assertThat(ObligationsModelValidator.check(lab.model)).isEmpty();
        EcoreUtil.delete(restricted);
        schedule("restrict P3Y", "delete P11Y");
        assertThat(ObligationsModelValidator.check(lab.model)).extracting(Finding::message).singleElement()
                .satisfies(m -> assertThat(m).contains("deletes after P11Y, not when keep-P10Y ends after P10Y"));
    }

    @Test
    void o6_otherStartsAreLeftToInstants() {
        rows(Obligations.retain(lab.governance, "keep-8y", "P8Y", PeriodStarts.valueTime(), lab.invoice));
        schedule("delete P10Y");
        List<Finding> findings = ObligationsModelValidator.check(lab.model);
        assertThat(findings).extracting(Finding::rule).containsExactly("O6");
        assertThat(findings.get(0).severity()).isEqualTo(Severity.WARNING);
        assertThat(ObligationsModelValidator.validate(lab.model)).isEmpty();
    }

    @Test
    void o6_aComparisonOnlyApproximate() {
        keep("P8Y");
        schedule("delete P2922D");
        assertThat(ObligationsModelValidator.check(lab.model)).extracting(Finding::rule).containsExactly("O6");
    }

    @Test
    void o7_theTermComesFromTheStageOrFromTheClass() {
        RetentionSchedule both = RetentionSchedules.create(lab.governance, "customer-rows", DataScope.ROW,
                PeriodStarts.event(lab.contractEnded, lab.contractEnd), lab.customer);
        Stages.delete(both, "delete", "P3Y");
        DeletionClasses.create(lab.governance, "contracts", PeriodStarts.event(lab.contractEnded), "P3Y", lab.customer)
                .setScope(DataScope.ROW);
        assertThat(ObligationsModelValidator.check(lab.model)).extracting(Finding::message).singleElement()
                .satisfies(m -> assertThat(m).contains("one of them gives the term"));
        both.getStage().get(0).setAfter(null);
        RetentionSchedules.refreshed(both);
        assertThat(ObligationsModelValidator.check(lab.model)).isEmpty();
        RetentionSchedules.startAt(both, PeriodStarts.valueTime());
        assertThat(ObligationsModelValidator.check(lab.model)).extracting(Finding::message).singleElement()
                .satisfies(m -> assertThat(m).contains("takes its term from contracts, but starts from the time of the value"));
    }

    @Test
    void aDutyOnAPackageIsJudgedAtTheClassesInIt() {
        DeletionObligation all = rows(Obligations.delete(lab.governance, "data-10y", "P10Y", PeriodStarts.valueTime(),
                lab.data));
        Stages.delete(RetentionSchedules.create(lab.governance, "invoice-rows", DataScope.ROW, PeriodStarts.valueTime(),
                lab.invoice), "delete", "P10Y");
        assertThat(ObligationsModelValidator.check(lab.model)).singleElement().satisfies(f -> {
            assertThat(f.rule()).isEqualTo("O4");
            assertThat(f.element()).isSameAs(all);
            assertThat(f.message()).isEqualTo("DeletionObligation 'data-10y': no stage deletes the data within P10Y "
                    + "(lab.data.Customer, lab.data.Reading).");
        });
        RetentionSchedules.rows(lab.governance, "data-rows", "P10Y", PeriodStarts.valueTime(), lab.data);
        assertThat(ObligationsModelValidator.check(lab.model)).isEmpty();
    }

    @Test
    void o6_aLongerRetentionThatStartsElsewhere() {
        rows(Obligations.retain(lab.governance, "keep-10y", "P10Y", PeriodStarts.valueTime(), lab.invoice));
        delete("P3Y");
        schedule("restrict P3Y", "delete P10Y");
        List<Finding> findings = ObligationsModelValidator.check(lab.model);
        assertThat(findings).extracting(Finding::severity).containsOnly(Severity.WARNING);
        assertThat(findings).extracting(Finding::rule).contains("O6");
    }

    @Test
    void o6_aRestrictionThatStartsElsewhere() {
        keep("P10Y");
        delete("P3Y");
        RetentionSchedule schedule = schedule();
        Stages.from(Stages.restrict(schedule, "restrict", "P3Y"), PeriodStarts.valueTime());
        Stages.delete(schedule, "delete", "P10Y");
        List<Finding> findings = ObligationsModelValidator.check(lab.model);
        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.severity()).isEqualTo(Severity.WARNING);
            assertThat(f.message()).isEqualTo("DeletionObligation 'delete-P3Y': stage restrict of invoice-rows starts "
                    + "elsewhere; compare them with instants (lab.data.Invoice).");
        });
    }

    @Test
    void o3_dataThatStaysUnchangedKeepsItsValuesToo() {
        RetentionObligation books = keep("P8Y");
        RetentionSchedule values = RetentionSchedules.create(lab.governance, "amount-values", endOfYear(), lab.amount);
        Stages.aggregate(values, "monthly", "P1Y", "P1M",
                org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.AggregateFunction.SUM, null);
        assertThat(ObligationsModelValidator.check(lab.model)).isEmpty();
        Obligations.immutable(books);
        assertThat(ObligationsModelValidator.check(lab.model)).extracting(Finding::message).containsExactly(
                "RetentionObligation 'keep-P8Y': stage monthly of amount-values is due after P1Y, before the data is "
                        + "kept for P8Y (lab.data.Invoice.amount).");
    }

    @Test
    void o7_theClassBindsAlignedAndToAFeature() {
        DeletionClasses.onRows(DeletionClasses.create(lab.governance, "contracts", PeriodStarts.aligned(
                PeriodStarts.event(lab.contractEnded), Alignment.END_OF_YEAR, "Europe/Berlin"), "P3Y", lab.customer));
        RetentionSchedule schedule = RetentionSchedules.create(lab.governance, "customer-rows", DataScope.ROW,
                PeriodStarts.event(lab.contractEnded, lab.contractEnd), lab.customer);
        Stages.delete(schedule, "delete", null);
        assertThat(ObligationsModelValidator.check(lab.model)).extracting(Finding::message).containsExactly(
                "RetentionSchedule 'customer-rows': stage delete takes its term from contracts, but starts from event "
                        + "contractEnded (contractEnd), not from event contractEnded, end of year in Europe/Berlin "
                        + "(lab.data.Customer).");
        RetentionSchedules.startAt(schedule, PeriodStarts.aligned(PeriodStarts.event(lab.contractEnded),
                Alignment.END_OF_YEAR, "Europe/Berlin"));
        assertThat(ObligationsModelValidator.check(lab.model)).extracting(Finding::message).containsExactly(
                "RetentionSchedule 'customer-rows': stage delete takes its term from contracts, but names no feature "
                        + "that holds the time of event contractEnded, end of year in Europe/Berlin (lab.data.Customer).");
        RetentionSchedules.startAt(schedule, PeriodStarts.aligned(PeriodStarts.event(lab.contractEnded,
                lab.contractEnd), Alignment.END_OF_YEAR, "Europe/Berlin"));
        assertThat(ObligationsModelValidator.check(lab.model)).isEmpty();
    }

    @Test
    void o7_theClassOfEveryElementOfTheData() {
        RetentionSchedule schedule = RetentionSchedules.create(lab.governance, "data-rows", DataScope.ROW,
                PeriodStarts.valueTime(), lab.data);
        Stages.delete(schedule, "delete", null);
        DeletionClasses.onRows(DeletionClasses.create(lab.governance, "kept", PeriodStarts.valueTime(), "P1Y",
                lab.invoice, lab.customer));
        assertThat(ObligationsModelValidator.check(lab.model)).extracting(Finding::message).containsExactly(
                "RetentionSchedule 'data-rows': stage delete has no period, and no deletion class gives one "
                        + "(lab.data.Reading).");
    }

    @Test
    void o7_noTermAtAll() {
        RetentionSchedule schedule = RetentionSchedules.create(lab.governance, "reading-rows", DataScope.ROW,
                PeriodStarts.valueTime(), lab.reading);
        Stages.delete(schedule, "delete", null);
        assertThat(ObligationsModelValidator.check(lab.model)).extracting(Finding::rule).containsExactly("O7");
    }

    @Test
    void o8_aDutyRestsOnASourceInForce() {
        Obligations.retain(lab.governance, "unfounded", "P8Y", endOfYear(), lab.invoice).setScope(DataScope.ROW);
        List<Finding> findings = ObligationsModelValidator.check(lab.model);
        assertThat(findings).extracting(Finding::rule).containsExactly("O8");
        assertThat(findings.get(0).severity()).isEqualTo(Severity.WARNING);
        RetentionObligation old = keep("P10Y");
        LegalSource superseded = LegalSources.validity(LegalSources.create(lab.law, "ao-147-old",
                "https://www.gesetze-im-internet.de/ao_1977/__147.html", LegalSources.STATUTE), "1977-03-16", "2025-01-01");
        LegalSources.cite(superseded, old);
        assertThat(ObligationsModelValidator.check(lab.model)).extracting(Finding::message)
                .anySatisfy(m -> assertThat(m).contains("its source ao-147-old is not in force"));
    }

    @Test
    void o9_anElementThatDoesNotResolveAndABodyEditedByHand() {
        Attribute proxy = CoreFactory.eINSTANCE.createAttribute();
        ((InternalEObject) proxy).eSetProxyURI(URI.createURI("other.xmi#//elsewhere"));
        rows(Obligations.retain(lab.governance, "elsewhere", "P8Y", PeriodStarts.valueTime(), proxy)).setScope(DataScope.VALUE);
        RetentionObligation edited = keep("P8Y");
        edited.getBody().setBody("keep at least P1Y");
        List<Finding> findings = ObligationsModelValidator.check(lab.model);
        assertThat(findings).extracting(Finding::rule).containsExactly("O9", "O9");
        assertThat(findings).allSatisfy(f -> assertThat(f.severity()).isEqualTo(Severity.WARNING));
    }
}
