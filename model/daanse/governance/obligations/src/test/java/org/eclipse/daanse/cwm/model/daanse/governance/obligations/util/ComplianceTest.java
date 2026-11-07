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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Alignment;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.EventTime;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.FeatureTime;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.PeriodStarts;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.Due;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.Starts;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.State;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.RetentionSchedules;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Stages;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.RetentionObligation;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.Compliance.Violation;
import org.junit.jupiter.api.Test;

/** The duties of an element against its schedules, with the instants of the data at hand. */
class ComplianceTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Instant ISSUED = ZonedDateTime.of(2020, 6, 15, 9, 30, 0, 0, BERLIN).toInstant();
    private static final Instant CONTRACT_END = Instant.parse("2024-03-31T22:00:00Z");
    private static final Starts STARTS = start -> Optional.of(start instanceof EventTime ? CONTRACT_END : ISSUED);
    private final Lab lab = new Lab();

    private FeatureTime endOfYear() {
        return PeriodStarts.aligned(PeriodStarts.feature(lab.issuedOn), Alignment.END_OF_YEAR, "Europe/Berlin");
    }

    private RetentionSchedule invoices(String after) {
        return RetentionSchedules.rows(lab.governance, "invoice-rows", after, endOfYear(), lab.invoice);
    }

    @Test
    void aScheduleThatKeepsItsDuties() {
        Obligations.retain(lab.governance, "books-8y", "P8Y", endOfYear(), lab.invoice).setScope(DataScope.ROW);
        Obligations.delete(lab.governance, "books-10y", "P10Y", endOfYear(), lab.invoice).setScope(DataScope.ROW);
        invoices("P10Y");
        Compliance.Report report = Compliance.check(lab.invoice, STARTS, Instant.parse("2026-11-06T00:00:00Z"));
        assertThat(report.violations()).isEmpty();
        assertThat(report.compliant()).isTrue();
        assertThat(report.plan().next()).map(Due::at)
                .contains(ZonedDateTime.of(2031, 1, 1, 0, 0, 0, 0, BERLIN).toInstant());
    }

    @Test
    void aDestructiveStageBeforeTheMinimumIsO3() {
        Obligations.retain(lab.governance, "books-8y", "P8Y", endOfYear(), lab.invoice).setScope(DataScope.ROW);
        invoices("P5Y");
        Compliance.Report report = Compliance.check(lab.invoice, STARTS, Instant.parse("2026-11-06T00:00:00Z"));
        assertThat(report.violations()).singleElement().satisfies(v -> {
            assertThat(v.rule()).isEqualTo("O3");
            assertThat(v.due()).isEqualTo(ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, BERLIN).toInstant());
            assertThat(v.required()).isEqualTo(ZonedDateTime.of(2029, 1, 1, 0, 0, 0, 0, BERLIN).toInstant());
        });
    }

    @Test
    void noDeleteOrALateOneIsO4() {
        Obligations.delete(lab.governance, "books-3y", "P3Y", endOfYear(), lab.invoice).setScope(DataScope.ROW);
        RetentionSchedule schedule = RetentionSchedules.create(lab.governance, "invoice-rows", DataScope.ROW, endOfYear(),
                lab.invoice);
        Stages.tier(schedule, "cold", "P1Y", "cold", true);
        assertThat(Compliance.check(lab.invoice, STARTS, ISSUED).violations()).extracting(Violation::rule)
                .containsExactly("O4");
        Stages.delete(schedule, "delete", "P5Y");
        assertThat(Compliance.check(lab.invoice, STARTS, ISSUED).violations()).singleElement().satisfies(v -> {
            assertThat(v.rule()).isEqualTo("O4");
            assertThat(v.message()).contains("after books-3y demands the data gone by");
        });
    }

    @Test
    void aLongerRetentionNeedsARestrictionAndTheDeleteAtItsEnd() {
        Obligations.retain(lab.governance, "books-10y", "P10Y", endOfYear(), lab.invoice).setScope(DataScope.ROW);
        Obligations.delete(lab.governance, "privacy-3y", "P3Y", endOfYear(), lab.invoice).setScope(DataScope.ROW);
        RetentionSchedule schedule = RetentionSchedules.create(lab.governance, "invoice-rows", DataScope.ROW, endOfYear(),
                lab.invoice);
        Stages.delete(schedule, "delete", "P10Y");
        assertThat(Compliance.check(lab.invoice, STARTS, ISSUED).violations()).extracting(Violation::rule)
                .containsExactly("O5");
        schedule.getStage().add(0, org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.LifecycleFactory.eINSTANCE
                .createRestrict());
        schedule.getStage().get(0).setName("restrict");
        schedule.getStage().get(0).setAfter("P3Y");
        assertThat(Compliance.check(lab.invoice, STARTS, ISSUED).violations()).isEmpty();
        schedule.getStage().get(1).setAfter("P11Y");
        assertThat(Compliance.check(lab.invoice, STARTS, ISSUED).violations()).singleElement()
                .satisfies(v -> assertThat(v.message()).contains("the data is deleted when books-10y ends"));
    }

    @Test
    void dataThatStaysUnchangedLosesNoValueEither() {
        RetentionObligation books = Obligations.onRows(Obligations.retain(lab.governance, "books-8y", "P8Y",
                endOfYear(), lab.invoice));
        RetentionSchedule values = RetentionSchedules.create(lab.governance, "amount-values", endOfYear(), lab.amount);
        Stages.aggregate(values, "monthly", "P1Y", "P1M",
                org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.AggregateFunction.SUM, null);
        assertThat(Compliance.check(lab.amount, STARTS, ISSUED).violations()).isEmpty();
        Obligations.immutable(books);
        assertThat(Compliance.check(lab.amount, STARTS, ISSUED).violations()).extracting(Violation::rule)
                .containsExactly("O3");
    }

    @Test
    void aHoldSuspendsTheDeleteAndBreaksNoDuty() {
        Obligations.delete(lab.governance, "books-10y", "P10Y", endOfYear(), lab.invoice).setScope(DataScope.ROW);
        invoices("P10Y");
        RetentionHolds.create(lab.governance, "lawsuit", "lawsuit 4711", Instant.parse("2030-06-01T00:00:00Z"), lab.invoice);
        Compliance.Report report = Compliance.check(lab.invoice, STARTS, Instant.parse("2031-02-01T00:00:00Z"));
        assertThat(report.violations()).isEmpty();
        assertThat(report.plan().stages()).extracting(Due::state).containsExactly(State.SUSPENDED);
    }

    @Test
    void theDeletionClassGivesTheTermOfTheLastStage() {
        DeletionClasses.create(lab.governance, "contracts", PeriodStarts.event(lab.contractEnded), "P3Y", lab.customer)
                .setScope(DataScope.ROW);
        Obligations.delete(lab.governance, "customers-3y", "P3Y", PeriodStarts.event(lab.contractEnded, lab.contractEnd),
                lab.customer).setScope(DataScope.ROW);
        RetentionSchedule schedule = RetentionSchedules.create(lab.governance, "customer-rows", DataScope.ROW,
                PeriodStarts.event(lab.contractEnded, lab.contractEnd), lab.customer);
        Stages.delete(schedule, "delete", null);
        assertThat(Compliance.terms(lab.customer).of(new Retention.Step(schedule, schedule.getStage().get(0))))
                .contains("P3Y");
        Compliance.Report report = Compliance.check(lab.customer, STARTS, Instant.parse("2027-04-01T00:00:00Z"));
        assertThat(report.violations()).isEmpty();
        assertThat(report.plan().current()).map(Due::at).contains(Instant.parse("2027-03-31T22:00:00Z"));
    }
}
