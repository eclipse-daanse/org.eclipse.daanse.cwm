/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.recording.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Constraint;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Generalization;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.RelationshipsFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Finding;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Severity;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleRules;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.AbsoluteChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.HistorisationKind;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.HistorisationRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RangeChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RecordingFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.ThrottleRule;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.InternalEObject;
import org.junit.jupiter.api.Test;

/** One positive and at least one negative case per rule of the validator. */
class RecordingModelValidatorTest {

    private final Shop shop = new Shop();

    @Test
    void wellFormedRulesHaveNoFinding() {
        RecordingRules.status(RecordingRules.heartbeat(RecordingRules.minimumInterval(
                RecordingRules.absolute(shop.sensors, "reading-2", 2.0, shop.reading), Duration.ofMinutes(1)),
                Duration.ofHours(1)), shop.quality);
        RecordingRules.changedValue(shop.sensors, "states", shop.state);
        RecordingRules.range(shop.sensors, "level-1pct", 1.0, 0.0, 100.0, shop.level);
        RecordingRules.throttle(shop.sensors, "meter-1m", Duration.ofMinutes(1), shop.meter);
        HistorisationRules.currentFlag(HistorisationRules.validity(HistorisationRules.rows(shop.crm, "versions",
                HistorisationKind.NEW_VERSION, shop.customer), shop.validFrom, shop.validTo), shop.isCurrent);
        HistorisationRules.previousValue(HistorisationRules.values(shop.crm, "city", HistorisationKind.PREVIOUS_VALUE,
                shop.city), shop.previousCity);
        HistorisationRules.history(HistorisationRules.values(shop.crm, "names", HistorisationKind.HISTORY_TABLE,
                shop.name), shop.customerHistory);
        assertThat(RecordingModelValidator.check(shop.model)).isEmpty();
    }

    @Test
    void r1_aRuleGovernsFeaturesClassifiersOrNamespaces() {
        Constraint constraint = CoreFactory.eINSTANCE.createConstraint();
        constraint.setName("positive");
        shop.sensors.getOwnedElement().add(constraint);
        RecordingRules.changedValue(shop.sensors, "on-a-constraint", constraint);
        List<Finding> findings = RecordingModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("R1");
        assertThat(findings.get(0).message()).contains("Constraint", "shop.sensors.positive");
        // a model, a package, a classifier and a data type are namespaces
        constraint.getConstraint().clear();
        RecordingRules.changedValue(shop.model, "everything", shop.model, shop.integer);
        assertThat(RecordingModelValidator.check(shop.model)).isEmpty();
    }

    @Test
    void r1_aRecordingRuleActsOnValues() {
        RecordingRules.changedValue(shop.sensors, "rows", shop.meter).setScope(DataScope.ROW);
        List<Finding> findings = RecordingModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("R1");
        assertThat(findings.get(0).message()).contains("its scope is row");
    }

    @Test
    void r2_oneRecordingRulePerElement() {
        RecordingRules.absolute(shop.sensors, "reading-2", 2.0, shop.reading);
        RecordingRules.throttle(shop.sensors, "reading-1m", Duration.ofMinutes(1), shop.reading);
        List<Finding> findings = RecordingModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("R2");
        assertThat(findings.get(0).element()).isSameAs(shop.reading);
        assertThat(findings.get(0).message()).contains("reading-2", "reading-1m", "combine");
    }

    @Test
    void r2_oneHistorisationRulePerElementAndScope() {
        HistorisationRules.values(shop.crm, "overwrite", HistorisationKind.OVERWRITE, shop.customer);
        HistorisationRules.validity(HistorisationRules.rows(shop.crm, "versions", HistorisationKind.NEW_VERSION,
                shop.customer), shop.validFrom, null);
        assertThat(RecordingModelValidator.check(shop.model)).as("one of each scope").isEmpty();
        HistorisationRules.values(shop.crm, "again", HistorisationKind.OVERWRITE, shop.customer);
        List<Finding> findings = RecordingModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("R2");
        assertThat(findings.get(0).message()).contains("scope value", "overwrite", "again");
    }

    @Test
    void r3_parametersParse() {
        AbsoluteChangeRule negative = RecordingRules.absolute(shop.sensors, "negative", 1.0, shop.reading);
        negative.setDelta("-1");
        RecordingRules.refreshed(negative);
        AbsoluteChangeRule months = RecordingRules.absolute(shop.sensors, "months", 1.0, shop.level);
        months.setMaximumInterval("P1M");
        RecordingRules.refreshed(months);
        AbsoluteChangeRule crossed = RecordingRules.minimumInterval(RecordingRules.heartbeat(
                RecordingRules.absolute(shop.sensors, "crossed", 1.0, shop.gauge), Duration.ofMinutes(1)),
                Duration.ofHours(1));
        RecordingRules.sampling(shop.sensors, "never", 0, shop.quality);
        List<Finding> findings = RecordingModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("R3", "R3", "R3", "R3");
        assertThat(findings.get(0).message()).contains("'-1'");
        assertThat(findings.get(1).message()).contains("'P1M'");
        assertThat(findings.get(2).message()).contains("PT1H is above the maximum interval PT1M");
        assertThat(findings.get(3).message()).contains("every is 0");
        assertThat(crossed.getMinimumInterval()).isEqualTo("PT1H");
    }

    @Test
    void r3_aThrottleHasAMinimumInterval() {
        ThrottleRule rule = RecordingFactory.eINSTANCE.createThrottleRule();
        RecordingRules.refreshed(LifecycleRules.attach(shop.sensors, "unbounded", rule, shop.reading));
        assertThat(rules(RecordingModelValidator.check(shop.model))).containsExactly("R3");
        RecordingRules.minimumInterval(rule, Duration.ofMinutes(5));
        assertThat(RecordingModelValidator.check(shop.model)).isEmpty();
    }

    @Test
    void r3_aRangeIsARange() {
        RangeChangeRule rule = RecordingRules.range(shop.sensors, "level", 150.0, 100.0, 0.0, shop.level);
        List<Finding> findings = RecordingModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("R3", "R3");
        assertThat(findings.get(0).message()).contains("at most 100");
        assertThat(findings.get(1).message()).contains("not below the high end");
        rule.setPercent("1.0");
        rule.setLow("zero");
        RecordingRules.refreshed(rule);
        assertThat(RecordingModelValidator.check(shop.model)).singleElement()
                .satisfies(f -> assertThat(f.message()).contains("no pair of decimal numbers"));
    }

    @Test
    void r4_aDeadbandOnText() {
        RecordingRules.absolute(shop.sensors, "state-1", 1.0, shop.state);
        RecordingRules.swingingDoor(shop.sensors, "level", 0.5, shop.level);
        List<Finding> findings = RecordingModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("R4");
        assertThat(findings.get(0).severity()).isEqualTo(Severity.WARNING);
        assertThat(findings.get(0).message()).contains("shop.sensors.Meter.state", "String");
        assertThat(RecordingModelValidator.validate(shop.model)).isEmpty();
    }

    @Test
    void r5_theStatusFeatureBelongsToTheGovernedClassifier() {
        AbsoluteChangeRule rule = RecordingRules.status(RecordingRules.absolute(shop.sensors, "level", 1.0, shop.level),
                shop.quality);
        assertThat(rules(RecordingModelValidator.check(shop.model))).containsExactly("R5");
        rule.getConstrainedElement().set(0, shop.reading);
        assertThat(RecordingModelValidator.check(shop.model)).isEmpty();
        rule.getConstrainedElement().set(0, shop.sensors);
        assertThat(rules(RecordingModelValidator.check(shop.model))).as("a package has no status feature")
                .containsExactly("R5");
    }

    @Test
    void r5_aFeatureOfAnAncestorCounts() {
        Generalization gaugeIsAMeter = RelationshipsFactory.eINSTANCE.createGeneralization();
        gaugeIsAMeter.setName("Gauge_Meter");
        gaugeIsAMeter.setChild(shop.gauge);
        gaugeIsAMeter.setParent(shop.meter);
        shop.sensors.getOwnedElement().add(gaugeIsAMeter);
        RecordingRules.status(RecordingRules.absolute(shop.sensors, "level", 1.0, shop.level), shop.quality);
        assertThat(RecordingModelValidator.check(shop.model)).isEmpty();
    }

    @Test
    void r6_theKindFitsTheScope() {
        HistorisationRules.previousValue(HistorisationRules.rows(shop.crm, "rows", HistorisationKind.PREVIOUS_VALUE,
                shop.customer), shop.previousCity);
        HistorisationRules.validity(HistorisationRules.values(shop.crm, "values", HistorisationKind.SNAPSHOT,
                shop.city), shop.validFrom, null);
        List<Finding> findings = RecordingModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("R6", "R6");
        assertThat(findings.get(0).message()).contains("previousValue", "its scope is value");
        assertThat(findings.get(1).message()).contains("snapshot", "its scope is row");
    }

    @Test
    void r6_theKindNamesItsAnchors() {
        HistorisationRules.rows(shop.crm, "versions", HistorisationKind.NEW_VERSION, shop.customer);
        HistorisationRules.values(shop.crm, "previous", HistorisationKind.PREVIOUS_VALUE, shop.city);
        HistorisationRules.values(shop.crm, "current", HistorisationKind.VERSION_AND_CURRENT, shop.name);
        HistorisationRules.values(shop.crm, "table", HistorisationKind.HISTORY_TABLE, shop.isCurrent);
        HistorisationRules.values(shop.crm, "version-on-change", HistorisationKind.NEW_VERSION, shop.validTo);
        List<Finding> findings = RecordingModelValidator.check(shop.model);
        assertThat(findings).extracting(Finding::message).containsExactly(
                "HistorisationRule 'versions': newVersion needs validFrom.",
                "HistorisationRule 'previous': previousValue needs previousValue.",
                "HistorisationRule 'current': versionAndCurrent needs currentValue.",
                "HistorisationRule 'table': historyTable needs history.");
    }

    @Test
    void r6_anchorsBelongToTheGovernedClassifier() {
        HistorisationRule rule = HistorisationRules.validity(HistorisationRules.rows(shop.crm, "versions",
                HistorisationKind.NEW_VERSION, shop.customer), shop.takenAt, null);
        List<Finding> findings = RecordingModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("R6");
        assertThat(findings.get(0).message()).contains("shop.sensors.Meter.takenAt", "no feature of shop.crm.Customer");
        HistorisationRules.validity(rule, shop.validFrom, null);
        assertThat(RecordingModelValidator.check(shop.model)).isEmpty();
    }

    @Test
    void r6_theHistoryHasTheGovernedFeatures() {
        HistorisationRules.history(HistorisationRules.values(shop.crm, "cities", HistorisationKind.HISTORY_TABLE,
                shop.city), shop.customerHistory);
        assertThat(RecordingModelValidator.check(shop.model)).isEmpty();
        HistorisationRules.history(HistorisationRules.rows(shop.crm, "rows", HistorisationKind.HISTORY_TABLE,
                shop.customer), shop.customerHistory);
        List<Finding> findings = RecordingModelValidator.check(shop.model);
        assertThat(findings).allSatisfy(f -> {
            assertThat(f.rule()).isEqualTo("R6");
            assertThat(f.severity()).isEqualTo(Severity.WARNING);
        });
        assertThat(findings).extracting(Finding::message).anySatisfy(m -> assertThat(m).contains("no feature validFrom"));
        assertThat(findings).extracting(Finding::message).noneSatisfy(m -> assertThat(m).contains("no feature city"));
    }

    @Test
    void r6_theHistoryIsAnotherClassifier() {
        HistorisationRules.history(HistorisationRules.rows(shop.crm, "rows", HistorisationKind.HISTORY_TABLE,
                shop.customer), shop.customer);
        List<Finding> findings = RecordingModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("R6");
        assertThat(findings.get(0).message()).contains("is shop.crm.Customer itself");
    }

    @Test
    void r7_anElementThatDoesNotResolve() {
        Attribute proxy = CoreFactory.eINSTANCE.createAttribute();
        ((InternalEObject) proxy).eSetProxyURI(URI.createURI("other.xmi#//elsewhere"));
        RecordingRules.changedValue(shop.sensors, "elsewhere", proxy);
        List<Finding> findings = RecordingModelValidator.check(shop.model);
        assertThat(rules(findings)).containsExactly("R7");
        assertThat(findings.get(0).severity()).isEqualTo(Severity.WARNING);
        assertThat(RecordingModelValidator.validate(shop.model)).isEmpty();
    }

    @Test
    void r7_theBodyIsTheTextOfTheRule() {
        AbsoluteChangeRule rule = RecordingRules.absolute(shop.sensors, "reading-2", 2.0, shop.reading);
        rule.getBody().setBody("abs(value - last) >= 1.0");
        HistorisationRule versions = HistorisationRules.validity(HistorisationRules.rows(shop.crm, "versions",
                HistorisationKind.NEW_VERSION, shop.customer), shop.validFrom, null);
        versions.setValidTo(shop.validTo);
        assertThat(rules(RecordingModelValidator.check(shop.model))).containsExactly("R7", "R7");
        RecordingRules.refreshed(rule);
        HistorisationRules.refreshed(versions);
        assertThat(RecordingModelValidator.check(shop.model)).isEmpty();
    }

    private static List<String> rules(List<Finding> findings) {
        return findings.stream().map(Finding::rule).toList();
    }
}
