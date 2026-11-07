/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Alignment;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.FeatureTime;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Stage;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.Due;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.State;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.Starts;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.Step;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.Suspension;
import org.junit.jupiter.api.Test;

/** When stages are due, which one is in force, a hold, and the schedules of values and rows together. */
class RetentionTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private final Shop shop = new Shop();

    @Test
    void aPeriodThatBeginsWithTheEndOfTheYear() {
        FeatureTime documentDate = PeriodStarts.aligned(PeriodStarts.feature(shop.takenAt), Alignment.END_OF_YEAR,
                "Europe/Berlin");
        RetentionSchedule books = RetentionSchedules.create(shop.governance, "books-10y", documentDate, shop.meter);
        Stage delete = Stages.delete(books, "delete", "P10Y");
        Instant june2020 = ZonedDateTime.of(2020, 6, 15, 9, 30, 0, 0, BERLIN).toInstant();
        Instant newYear2021 = ZonedDateTime.of(2021, 1, 1, 0, 0, 0, 0, BERLIN).toInstant();
        assertThat(PeriodStarts.begin(documentDate, june2020).toInstant()).isEqualTo(newYear2021);
        assertThat(Retention.dueAt(delete, documentDate, june2020))
                .contains(ZonedDateTime.of(2031, 1, 1, 0, 0, 0, 0, BERLIN).toInstant());
        Instant lastMinuteOf2020 = ZonedDateTime.of(2020, 12, 31, 23, 59, 0, 0, BERLIN).toInstant();
        assertThat(PeriodStarts.begin(documentDate, lastMinuteOf2020).toInstant()).isEqualTo(newYear2021);
        assertThat(books.getBody().getBody()).isEqualTo("from takenAt, end of year in Europe/Berlin: delete after P10Y");
    }

    @Test
    void theEndOfAMonthAndOfAQuarterAndAnOffset() {
        Instant may10 = ZonedDateTime.of(2026, 5, 10, 12, 0, 0, 0, BERLIN).toInstant();
        assertThat(PeriodStarts.begin(PeriodStarts.aligned(PeriodStarts.valueTime(), Alignment.END_OF_QUARTER,
                "Europe/Berlin"), may10)).isEqualTo(ZonedDateTime.of(2026, 7, 1, 0, 0, 0, 0, BERLIN));
        assertThat(PeriodStarts.begin(PeriodStarts.offset(PeriodStarts.aligned(PeriodStarts.valueTime(),
                Alignment.END_OF_MONTH, "Europe/Berlin"), "P1M"), may10))
                .isEqualTo(ZonedDateTime.of(2026, 7, 1, 0, 0, 0, 0, BERLIN));
        assertThat(PeriodStarts.begin(PeriodStarts.valueTime(), may10).toInstant()).as("unaligned").isEqualTo(may10);
    }

    @Test
    void aPlanSaysWhichStageIsInForce() {
        RetentionSchedule schedule = RetentionSchedules.create(shop.governance, "readings", PeriodStarts.valueTime(),
                shop.reading);
        Stage cold = Stages.tier(schedule, "cold", "P30D", "cold", true);
        Stage restrict = Stages.restrict(schedule, "restrict", "P1Y");
        Stage delete = Stages.delete(schedule, "delete", "P2Y");

        Retention.Plan plan = Retention.plan(shop.reading, Starts.at(T0), T0.plus(java.time.Duration.ofDays(400)),
                Suspension.NONE);
        assertThat(plan.stages()).extracting(d -> d.step().stage()).containsExactly(cold, restrict, delete);
        assertThat(plan.stages()).extracting(Due::state).containsExactly(State.PAST, State.DUE, State.PENDING);
        assertThat(plan.current()).map(d -> d.step().stage()).contains(restrict);
        assertThat(plan.next()).map(Due::at).contains(Instant.parse("2028-01-01T00:00:00Z"));

        Retention.Plan held = Retention.plan(shop.reading, Starts.at(T0), Instant.parse("2029-01-01T00:00:00Z"),
                Suspension.between(Instant.parse("2027-06-01T00:00:00Z"), null));
        assertThat(held.stages()).extracting(Due::state).containsExactly(State.PAST, State.DUE, State.SUSPENDED);
        Retention.Plan released = Retention.plan(shop.reading, Starts.at(T0), Instant.parse("2029-01-01T00:00:00Z"),
                Suspension.between(Instant.parse("2027-06-01T00:00:00Z"), Instant.parse("2028-06-01T00:00:00Z")));
        assertThat(released.current()).map(d -> d.step().stage()).contains(delete);
    }

    @Test
    void anUnknownStartLeavesEveryStagePending() {
        RetentionSchedules.values(shop.governance, "readings", "P90D", shop.reading);
        Retention.Plan plan = Retention.plan(shop.reading, start -> Optional.empty(), T0, Suspension.NONE);
        assertThat(plan.stages()).singleElement().satisfies(d -> {
            assertThat(d.at()).isNull();
            assertThat(d.state()).isEqualTo(State.PENDING);
        });
        assertThat(plan.next()).isEmpty();
    }

    @Test
    void theLastStageOfTheRowsEndsTheValues() {
        RetentionSchedule values = RetentionSchedules.create(shop.governance, "reading-history", PeriodStarts.valueTime(),
                shop.reading);
        Stage tier = Stages.tier(values, "cold", "P30D", "cold", false);
        Stage valuesDelete = Stages.delete(values, "delete", "P5Y");
        RetentionSchedule rows = RetentionSchedules.rows(shop.governance, "meters-1y", "P1Y", PeriodStarts.valueTime(),
                shop.meter);
        Stage rowsDelete = rows.getStage().get(0);

        Retention.Effective effective = Retention.effective(shop.reading);
        assertThat(effective).isEqualTo(new Retention.Effective(values, rows));
        assertThat(Retention.timeline(effective)).extracting(Step::stage).containsExactly(tier, rowsDelete, valuesDelete);
        assertThat(Retention.unreachable(effective)).extracting(Step::stage).containsExactly(valuesDelete);

        Retention.Plan plan = Retention.plan(effective, Starts.at(T0), Instant.parse("2032-01-01T00:00:00Z"),
                Suspension.NONE);
        assertThat(plan.stages()).extracting(Due::state).containsExactly(State.PAST, State.DUE, State.UNREACHABLE);
        assertThat(plan.current()).map(d -> d.step().stage()).contains(rowsDelete);
        assertThat(rows.getScope()).isEqualTo(DataScope.ROW);
        Retention.Plan early = Retention.plan(effective, Starts.at(T0), T0, Suspension.NONE);
        assertThat(early.stages()).extracting(Due::state).as("unreachable whatever the time")
                .containsExactly(State.PENDING, State.PENDING, State.UNREACHABLE);
        assertThat(early.next()).map(d -> d.step().stage()).contains(tier);
    }

    @Test
    void aTermFromElsewhere() {
        RetentionSchedule schedule = RetentionSchedules.create(shop.governance, "readings", PeriodStarts.valueTime(),
                shop.reading);
        Stage delete = Stages.delete(schedule, "delete", null);
        assertThat(Retention.plan(shop.reading, Starts.at(T0), T0, Suspension.NONE).stages()).singleElement()
                .satisfies(d -> assertThat(d.at()).as("no period: never due").isNull());
        Retention.Terms classTerm = step -> Optional.of(step.stage().getAfter() != null ? step.stage().getAfter() : "P3Y");
        Retention.Plan plan = Retention.plan(Retention.effective(shop.reading), Starts.at(T0),
                Instant.parse("2029-01-01T00:00:00Z"), Suspension.NONE, classTerm);
        assertThat(plan.current()).map(d -> d.step().stage()).contains(delete);
        assertThat(plan.current()).map(Due::at).contains(Instant.parse("2029-01-01T00:00:00Z"));
    }

    @Test
    void stagesOfDifferentStartsKeepTheOrderOfTheirSchedules() {
        RetentionSchedule values = RetentionSchedules.values(shop.governance, "readings", "P5Y", shop.reading);
        RetentionSchedule rows = RetentionSchedules.rows(shop.governance, "meters", "P1Y", PeriodStarts.feature(shop.takenAt),
                shop.meter);
        List<Step> timeline = Retention.timeline(Retention.effective(shop.reading));
        assertThat(timeline).extracting(Step::schedule).containsExactly(values, rows);
        assertThat(Retention.unreachable(Retention.effective(shop.reading))).as("not comparable").isEmpty();
    }
}
