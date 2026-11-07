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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.BehavioralFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Event;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.AggregateFunction;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Alignment;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.LifecycleFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.junit.jupiter.api.Test;

/** Which schedule applies to an element, the helpers that create schedules, and the text of a schedule. */
class LifecycleRulesTest {

    private final Shop shop = new Shop();

    @Test
    void theNearestScheduleWinsAlongFeatureOwnerAndNamespaces() {
        RetentionSchedule onModel = RetentionSchedules.values(shop.governance, "keep-10y", "P10Y", shop.model);
        RetentionSchedule onPackage = RetentionSchedules.values(shop.governance, "keep-5y", "P5Y", shop.sensors);
        RetentionSchedule onClass = RetentionSchedules.values(shop.governance, "keep-1y", "P1Y", shop.meter);
        RetentionSchedule onFeature = RetentionSchedules.values(shop.governance, "keep-90d", "P90D", shop.reading);

        assertThat(RetentionSchedules.effective(shop.reading, DataScope.VALUE)).containsSame(onFeature);
        assertThat(RetentionSchedules.effective(shop.state, DataScope.VALUE)).containsSame(onClass);
        assertThat(RetentionSchedules.effective(shop.level, DataScope.VALUE)).containsSame(onPackage);
        assertThat(RetentionSchedules.effective(shop.integer, DataScope.VALUE)).as("a data type of the model")
                .containsSame(onModel);
        assertThat(LifecycleRules.all(shop.reading, RetentionSchedule.class))
                .containsExactly(onFeature, onClass, onPackage, onModel);
    }

    @Test
    void theNearestScheduleOfAScopeIsFoundOnItsOwn() {
        RetentionSchedule values = RetentionSchedules.values(shop.governance, "keep-90d", "P90D", shop.meter);
        RetentionSchedule rows = RetentionSchedules.rows(shop.governance, "rows-1y", "P1Y", PeriodStarts.valueTime(),
                shop.sensors);

        assertThat(LifecycleRules.nearest(shop.reading, RetentionSchedule.class, DataScope.VALUE)).containsSame(values);
        assertThat(LifecycleRules.nearest(shop.reading, RetentionSchedule.class, DataScope.ROW)).containsSame(rows);
        assertThat(LifecycleRules.nearest(shop.reading, RetentionSchedule.class)).containsSame(values);
        assertThat(LifecycleRules.nearest(shop.gauge, RetentionSchedule.class, DataScope.VALUE)).isEmpty();
        assertThat(Retention.effective(shop.reading)).isEqualTo(new Retention.Effective(values, rows));
    }

    @Test
    void withoutSchedulesNothingApplies() {
        assertThat(RetentionSchedules.effective(shop.reading, DataScope.VALUE)).isEmpty();
        assertThat(LifecycleRules.rulesOf(shop.reading)).isEmpty();
        assertThat(Retention.effective(shop.reading).isEmpty()).isTrue();
    }

    @Test
    void ofTwoSchedulesOfAScopeOnOneElementTheFirstCounts() {
        RetentionSchedule first = RetentionSchedules.values(shop.governance, "keep-90d", "P90D", shop.reading);
        RetentionSchedules.values(shop.governance, "keep-1y", "P1Y", shop.reading);
        assertThat(LifecycleRules.rulesOf(shop.reading)).hasSize(2);
        assertThat(RetentionSchedules.effective(shop.reading, DataScope.VALUE)).containsSame(first);
    }

    @Test
    void helpersOwnNameConstrainAndWriteTheBody() {
        RetentionSchedule schedule = RetentionSchedules.values(shop.governance, "keep-90d", "P90D", shop.reading,
                shop.level);
        assertThat(schedule.getName()).isEqualTo("keep-90d");
        assertThat(schedule.getNamespace()).isSameAs(shop.governance);
        assertThat(schedule.getConstrainedElement()).containsExactly(shop.reading, shop.level);
        assertThat(shop.reading.getConstraint()).containsExactly(schedule);
        assertThat(schedule.getScope()).isEqualTo(DataScope.VALUE);
        assertThat(schedule.getStage()).singleElement().satisfies(s -> {
            assertThat(s.getName()).isEqualTo("delete");
            assertThat(s.getAfter()).isEqualTo("P90D");
        });
        assertThat(schedule.getBody().getLanguage()).isEqualTo(LifecycleRules.LANGUAGE);
        assertThat(schedule.getBody().getBody()).isEqualTo("from the time of the value: delete after P90D");
        assertThat(LifecycleRules.hasBody(schedule, "from the time of the value: delete after P90D")).isTrue();
    }

    @Test
    void aScheduleConstrainsAtLeastOneElement() {
        assertThatThrownBy(() -> RetentionSchedules.keeping(shop.governance, "keep", 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LifecycleRules.attach(null, "keep", LifecycleFactory.eINSTANCE.createRetentionSchedule(),
                shop.reading)).isInstanceOf(NullPointerException.class).hasMessageContaining("owner");
        assertThatThrownBy(() -> RetentionSchedules.values(shop.governance, "keep", "P1Q", shop.reading))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theTextOfASchedule() {
        Event contractEnded = BehavioralFactory.eINSTANCE.createEvent();
        contractEnded.setName("contractEnded");
        ResponsibleParty records = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
        records.setName("records-office");
        RetentionSchedule schedule = RetentionSchedules.create(shop.governance, "meters",
                PeriodStarts.offset(PeriodStarts.aligned(PeriodStarts.event(contractEnded, shop.takenAt),
                        Alignment.END_OF_YEAR, "Europe/Berlin"), "P1M"),
                shop.meter);
        Stages.tier(schedule, "cold", "P30D", "cold", true);
        Stages.aggregate(schedule, "daily", "P1Y", "P1D", AggregateFunction.AVERAGE, shop.gauge);
        Stages.restrict(schedule, "restrict", "P6Y");
        Stages.review(schedule, "review", "P10Y", records);
        Stages.notify(schedule, "notify", "P10Y", records);
        Stages.transfer(schedule, "archive", "P10Y", shop.model);
        Stages.from(Stages.delete(schedule, "delete", "P30D"), PeriodStarts.feature(shop.takenAt));
        Limits.count(schedule, 1000, shop.state);
        Limits.size(schedule, 1_000_000);
        RetentionSchedules.maxDelete(RetentionSchedules.cleanupEvery(schedule, Duration.ofDays(1)), 500);
        assertThat(schedule.getBody().getBody()).isEqualTo("from event contractEnded (takenAt), end of year in "
                + "Europe/Berlin, + P1M: tier cold compressed after P30D, aggregate per P1D as average into Gauge after "
                + "P1Y, restrict after P6Y, review by records-office after P10Y, notify records-office after P10Y, "
                + "transfer to shop after P10Y, delete after P30D from takenAt; keep the newest 1000 per state; keep at "
                + "most 1000000 bytes; cleanup every P1D; at most 500 deletions per cleanup");
        assertThat(LifecycleRules.path(shop.takenAt)).isEqualTo("shop.sensors.Meter.takenAt");
        RetentionSchedule count = RetentionSchedules.keeping(shop.governance, "last-1000", 1000, shop.gauge);
        assertThat(count.getBody().getBody()).isEqualTo("keep the newest 1000");
    }
}
