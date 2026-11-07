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

import java.time.Duration;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Constraint;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Generalization;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.RelationshipsFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.AggregateFunction;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Alignment;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.LifecycleFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Transfer;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Finding;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Severity;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.InternalEObject;
import org.junit.jupiter.api.Test;

/** One positive and at least one negative case per rule of the validator. */
class LifecycleModelValidatorTest {

    private final Shop shop = new Shop();

    @Test
    void wellFormedSchedulesHaveNoFinding() {
        RetentionSchedule meters = RetentionSchedules.create(shop.governance, "meters",
                PeriodStarts.aligned(PeriodStarts.feature(shop.takenAt), Alignment.END_OF_YEAR, "Europe/Berlin"),
                shop.meter);
        Stages.tier(meters, "cold", "P30D", "cold", true);
        Stages.restrict(meters, "restrict", "P6Y");
        Stages.delete(meters, "delete", "P10Y");
        Limits.count(meters, 10, shop.state);
        RetentionSchedules.cleanupEvery(meters, Duration.ofDays(1));
        RetentionSchedules.keeping(shop.governance, "keep-1000", 1000, shop.model);
        RetentionSchedules.rows(shop.governance, "gauges", "P1Y", PeriodStarts.valueTime(), shop.gauge);
        assertThat(LifecycleModelValidator.check(shop.model)).isEmpty();
    }

    @Test
    void l1_aScheduleGovernsData() {
        RetentionSchedule readings = RetentionSchedules.values(shop.governance, "readings", "P1Y", shop.reading);
        RetentionSchedule onAStage = RetentionSchedules.values(shop.governance, "on-a-stage", "P1Y",
                readings.getStage().get(0));
        List<Finding> findings = LifecycleModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("L1");
        assertThat(findings.get(0).message()).contains("not the Delete", "of a rule");
        onAStage.getConstrainedElement().set(0, shop.gauge);
        RetentionSchedules.rows(shop.governance, "rows", "P1Y", PeriodStarts.valueTime(), shop.level);
        findings = LifecycleModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("L1");
        assertThat(findings.get(0).message()).contains("a schedule of rows governs classifiers or namespaces");
    }

    @Test
    void l2_oneSchedulePerElementAndScope() {
        RetentionSchedules.values(shop.governance, "keep-90d", "P90D", shop.meter);
        RetentionSchedules.rows(shop.governance, "rows-1y", "P1Y", PeriodStarts.valueTime(), shop.meter);
        assertThat(LifecycleModelValidator.check(shop.model)).as("one of each scope").isEmpty();
        RetentionSchedules.values(shop.governance, "keep-1y", "P1Y", shop.meter);
        List<Finding> findings = LifecycleModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("L2");
        assertThat(findings.get(0).message()).contains("shop.sensors.Meter", "schedules of values", "keep-90d, keep-1y");
    }

    @Test
    void l3_parametersParse() {
        RetentionSchedule empty = LifecycleRules.attach(shop.governance, "empty",
                LifecycleFactory.eINSTANCE.createRetentionSchedule(), shop.meter);
        RetentionSchedules.refreshed(empty);
        RetentionSchedule startless = RetentionSchedules.keeping(shop.governance, "startless", 0, shop.gauge);
        Stages.delete(startless, "delete", "P1Y");
        RetentionSchedule literals = RetentionSchedules.values(shop.governance, "literals", "P1Y", shop.reading);
        literals.getStage().get(0).setAfter("P1Q");
        literals.getStart().setAlignment(Alignment.END_OF_YEAR);
        literals.setCleanupInterval("P1M");
        RetentionSchedules.refreshed(literals);
        List<Finding> findings = LifecycleModelValidator.check(shop.model);
        assertThat(findings).extracting(Finding::rule).containsOnly("L3");
        assertThat(findings).extracting(Finding::message).anySatisfy(m -> assertThat(m).contains("neither a stage nor a limit"))
                .anySatisfy(m -> assertThat(m).contains("stages without a start"))
                .anySatisfy(m -> assertThat(m).contains("keeps 0"))
                .anySatisfy(m -> assertThat(m).contains("'P1Q' is no ISO-8601 period"))
                .anySatisfy(m -> assertThat(m).contains("without a zone"))
                .anySatisfy(m -> assertThat(m).contains("cleanupInterval 'P1M'"));
    }

    @Test
    void l3_everyStageButTheLastHasAPeriod() {
        RetentionSchedule schedule = RetentionSchedules.create(shop.governance, "readings", PeriodStarts.valueTime(),
                shop.reading);
        Stages.tier(schedule, "cold", null, "cold", false);
        Stages.delete(schedule, "delete", null);
        List<Finding> findings = LifecycleModelValidator.check(shop.model);
        assertThat(findings).extracting(Finding::rule, Finding::severity).containsExactly(
                org.assertj.core.groups.Tuple.tuple("L3", Severity.ERROR),
                org.assertj.core.groups.Tuple.tuple("L3", Severity.WARNING));
        assertThat(findings.get(0).message()).contains("stage cold has no period");
        assertThat(findings.get(1).message()).contains("unless a deletion class gives its term");
    }

    @Test
    void l3_aZoneIsATimeZone() {
        RetentionSchedule schedule = RetentionSchedules.values(shop.governance, "keep", "P1Y", shop.reading);
        schedule.getStart().setZone("Mars/Olympus");
        assertThat(rules(LifecycleModelValidator.check(shop.model))).containsExactly("L3");
        schedule.getStart().setZone("Europe/Berlin");
        assertThat(LifecycleModelValidator.check(shop.model)).isEmpty();
    }

    @Test
    void l4_featuresBelongToTheGovernedClassifier() {
        RetentionSchedule schedule = RetentionSchedules.create(shop.governance, "gauges",
                PeriodStarts.feature(shop.takenAt), shop.gauge);
        Stages.delete(schedule, "delete", "P1Y");
        Limits.count(schedule, 10, shop.state);
        List<Finding> findings = LifecycleModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("L4", "L4");
        assertThat(findings.get(0).message()).contains("time feature shop.sensors.Meter.takenAt", "no feature of shop.sensors.Gauge");
        assertThat(findings.get(1).message()).contains("key shop.sensors.Meter.state");
        schedule.getConstrainedElement().set(0, shop.reading);
        assertThat(LifecycleModelValidator.check(shop.model)).as("the owner of the governed feature").isEmpty();
        schedule.getConstrainedElement().set(0, shop.sensors);
        assertThat(rules(LifecycleModelValidator.check(shop.model))).as("a package has no features")
                .containsExactly("L4", "L4");
    }

    @Test
    void l4_aFeatureOfAnAncestorCounts() {
        Generalization gaugeIsAMeter = RelationshipsFactory.eINSTANCE.createGeneralization();
        gaugeIsAMeter.setName("Gauge_Meter");
        gaugeIsAMeter.setChild(shop.gauge);
        gaugeIsAMeter.setParent(shop.meter);
        shop.sensors.getOwnedElement().add(gaugeIsAMeter);
        RetentionSchedule schedule = RetentionSchedules.create(shop.governance, "gauges",
                PeriodStarts.feature(shop.takenAt), shop.gauge);
        Stages.delete(schedule, "delete", "P1Y");
        assertThat(LifecycleModelValidator.check(shop.model)).isEmpty();
    }

    @Test
    void l5_theTerminalStageIsTheLastAndTheStagesInOrder() {
        RetentionSchedule late = RetentionSchedules.values(shop.governance, "late", "P1Y", shop.reading);
        Stages.restrict(late, "restrict", "P2Y");
        RetentionSchedule twice = RetentionSchedules.values(shop.governance, "twice", "P1Y", shop.level);
        Stages.delete(twice, "again", "P2Y");
        RetentionSchedule disorder = RetentionSchedules.create(shop.governance, "disorder", PeriodStarts.valueTime(),
                shop.state);
        Stages.restrict(disorder, "restrict", "P2Y");
        Stages.tier(disorder, "cold", "P1Y", "cold", false);
        List<Finding> findings = LifecycleModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("L5", "L5", "L5");
        assertThat(findings.get(0).message()).contains("terminal stage delete is not the last");
        assertThat(findings.get(1).message()).contains("2 terminal stages");
        assertThat(findings.get(2).message()).contains("stage cold (P1Y) comes after stage restrict (P2Y)");
    }

    @Test
    void l5_stagesOfOtherStartsAreNotOrdered() {
        RetentionSchedule schedule = RetentionSchedules.create(shop.governance, "mixed", PeriodStarts.valueTime(),
                shop.meter);
        Stages.restrict(schedule, "restrict", "P2Y");
        Stages.from(Stages.delete(schedule, "delete", "P1Y"), PeriodStarts.feature(shop.takenAt));
        assertThat(LifecycleModelValidator.check(shop.model)).isEmpty();
    }

    @Test
    void l6_aStageOfValuesAfterTheEndOfTheRows() {
        RetentionSchedule values = RetentionSchedules.create(shop.governance, "readings", PeriodStarts.valueTime(),
                shop.reading);
        Stages.tier(values, "cold", "P30D", "cold", false);
        Stages.delete(values, "delete", "P5Y");
        RetentionSchedules.rows(shop.governance, "meters", "P1Y", PeriodStarts.valueTime(), shop.meter);
        List<Finding> findings = LifecycleModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("L6");
        assertThat(findings.get(0).severity()).isEqualTo(Severity.WARNING);
        assertThat(findings.get(0).message()).contains("stage delete", "meters");
        assertThat(LifecycleModelValidator.validate(shop.model)).isEmpty();
    }

    @Test
    void l7_aTransferLeaves() {
        RetentionSchedule schedule = RetentionSchedules.create(shop.governance, "archive", PeriodStarts.valueTime(),
                shop.meter);
        Stages.transfer(schedule, "archive", "P1Y", shop.sensors);
        assertThat(rules(LifecycleModelValidator.check(shop.model))).containsExactly("L7");
        Package archive = CoreFactory.eINSTANCE.createPackage();
        archive.setName("archive");
        shop.model.getOwnedElement().add(archive);
        ((Transfer) schedule.getStage().get(0)).setTarget(archive);
        RetentionSchedules.refreshed(schedule);
        assertThat(LifecycleModelValidator.check(shop.model)).isEmpty();
    }

    @Test
    void l8_anAggregationTargetWithoutFeatures() {
        Class daily = CoreFactory.eINSTANCE.createClass();
        daily.setName("DailyReading");
        shop.sensors.getOwnedElement().add(daily);
        RetentionSchedule schedule = RetentionSchedules.create(shop.governance, "readings", PeriodStarts.valueTime(),
                shop.reading);
        Stages.aggregate(schedule, "daily", "P30D", "P1D", AggregateFunction.AVERAGE, daily);
        List<Finding> findings = LifecycleModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("L8");
        assertThat(findings.get(0).severity()).isEqualTo(Severity.WARNING);
        Attribute value = CoreFactory.eINSTANCE.createAttribute();
        value.setName("average");
        daily.getFeature().add(value);
        assertThat(LifecycleModelValidator.check(shop.model)).isEmpty();
    }

    @Test
    void l9_anElementThatDoesNotResolveAndABodyEditedByHand() {
        Attribute proxy = CoreFactory.eINSTANCE.createAttribute();
        ((InternalEObject) proxy).eSetProxyURI(URI.createURI("other.xmi#//elsewhere"));
        RetentionSchedules.values(shop.governance, "elsewhere", "P1Y", proxy);
        RetentionSchedule edited = RetentionSchedules.values(shop.governance, "edited", "P90D", shop.reading);
        edited.getStage().get(0).setAfter("P30D");
        List<Finding> findings = LifecycleModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("L9", "L9");
        assertThat(findings).allSatisfy(f -> assertThat(f.severity()).isEqualTo(Severity.WARNING));
        assertThat(findings.get(1).message()).contains("delete after P30D");
        RetentionSchedules.refreshed(edited);
        assertThat(rules(LifecycleModelValidator.check(shop.model))).containsExactly("L9");
    }

    @Test
    void l9_aReferenceThatDoesNotResolve() {
        Attribute proxy = CoreFactory.eINSTANCE.createAttribute();
        ((InternalEObject) proxy).eSetProxyURI(URI.createURI("other.xmi#//takenAt"));
        RetentionSchedules.create(shop.governance, "elsewhere", PeriodStarts.feature(proxy), shop.meter);
        Stages.delete((RetentionSchedule) shop.governance.getOwnedElement().get(0), "delete", "P1Y");
        List<Finding> findings = LifecycleModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("L9");
        assertThat(findings.get(0).message()).contains("FeatureTime", "other.xmi#//takenAt");
    }

    @Test
    void l10_aScheduleGovernsData() {
        Constraint positive = CoreFactory.eINSTANCE.createConstraint();
        positive.setName("positive");
        shop.sensors.getOwnedElement().add(positive);
        RetentionSchedules.values(shop.governance, "on-a-rule", "P1Y", positive);
        RetentionSchedule own = RetentionSchedules.values(shop.governance, "own-namespace", "P1Y", shop.governance);
        List<Finding> findings = LifecycleModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("L10", "L10");
        assertThat(findings.get(0).message()).contains("not the rule shop.sensors.positive");
        assertThat(findings.get(1).message()).contains("the namespace that owns it");
        own.getConstrainedElement().set(0, shop.model);
        assertThat(rules(LifecycleModelValidator.check(shop.model))).as("the model around it is data")
                .containsExactly("L10");
    }

    private static List<String> rules(List<Finding> findings) {
        return findings.stream().map(Finding::rule).toList();
    }
}
