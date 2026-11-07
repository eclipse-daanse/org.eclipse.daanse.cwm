/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.recording.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.analysis.olap.Cube;
import org.eclipse.daanse.cwm.model.cwm.analysis.olap.Measure;
import org.eclipse.daanse.cwm.model.cwm.analysis.olap.OlapFactory;
import org.eclipse.daanse.cwm.model.cwm.management.warehouseoperation.Measurement;
import org.eclipse.daanse.cwm.model.cwm.management.warehouseoperation.WarehouseoperationFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.DataTypes;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Models;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ShortIds;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.instance.DataSlot;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.instance.Extent;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.instance.InstanceFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.record.Field;
import org.eclipse.daanse.cwm.model.cwm.resource.record.RecordDef;
import org.eclipse.daanse.cwm.model.cwm.resource.record.RecordFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.record.RecordFile;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Finding;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Severity;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.AbsoluteChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.ChangedValueRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.HistorisationKind;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.HistorisationRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.LateValueHandling;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RangeChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RecordingRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RelativeChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.SamplingRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.SwingingDoorRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.ThrottleRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.util.HistorisationRules;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.util.Recording;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.util.Recording.Decision;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.util.Recording.Sample;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.util.RecordingModelValidator;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.util.RecordingRules;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;

/**
 * Recording rules at the places of CWM: how a rule decides, how the history of a class or a column
 * is kept, which rule applies where, where a rule does not reach, what the validator reports, and
 * the XMI of a model with rules.
 */
class RecordingAcrossCwmTest {

    private static final CoreFactory CORE = CoreFactory.eINSTANCE;
    private static final Instant T0 = Instant.parse("2026-11-06T08:00:00Z");

    private final Model plant = Models.create("plant");
    private final Package governance = Packages.create(plant, "governance");
    private final Package classes = Packages.create(plant, "classes");
    private final DataType number = DataTypes.create(classes, "Double");
    private final DataType text = DataTypes.create(classes, "String");
    private final Class sensor = cls(classes, "Sensor");
    private final Attribute temperature = attr(sensor, "temperature", number);
    private final Attribute humidity = attr(sensor, "humidity", number);
    private final Attribute state = attr(sensor, "state", text);
    private final Attribute quality = attr(sensor, "quality", text);

    // 1 How a recording rule decides

    @Test
    void absoluteChange() {
        AbsoluteChangeRule rule = RecordingRules.absolute(governance, "temperature-0.5", 0.5, temperature);
        Recording.State s = new Recording.State();
        assertThat(List.of(
                Recording.accepts(rule, s, 20.0, at(0)),
                Recording.accepts(rule, s, 20.3, at(1)),
                Recording.accepts(rule, s, 20.6, at(2)),
                Recording.accepts(rule, s, 20.9, at(3))))
                .containsExactly(true, false, true, false);
        assertThat(rule.getBody().getBody()).isEqualTo("abs(value - last) >= 0.5");
    }

    @Test
    void relativeChange() {
        RelativeChangeRule rule = RecordingRules.relative(governance, "humidity-5pct", 5.0, humidity);
        Recording.State s = new Recording.State();
        assertThat(List.of(
                Recording.accepts(rule, s, 100, at(0)),
                Recording.accepts(rule, s, 104, at(1)),
                Recording.accepts(rule, s, 105, at(2)),
                Recording.accepts(rule, s, 99, at(3))))
                .containsExactly(true, false, true, true);
        Recording.State zero = new Recording.State();
        assertThat(List.of(
                Recording.accepts(rule, zero, 0, at(0)),
                Recording.accepts(rule, zero, 0, at(1)),
                Recording.accepts(rule, zero, 0.1, at(2))))
                .containsExactly(true, false, true);
        assertThat(rule.getBody().getBody()).isEqualTo("abs(value - last) >= 5.0 % of abs(last)");
    }

    @Test
    void changeInPercentOfTheRange() {
        RangeChangeRule rule = RecordingRules.range(governance, "humidity-2pct-of-range", 2.0, 0.0, 100.0, humidity);
        Recording.State s = new Recording.State();
        assertThat(List.of(
                Recording.accepts(rule, s, 40.0, at(0)),
                Recording.accepts(rule, s, 41.5, at(1)),
                Recording.accepts(rule, s, 42.0, at(2)),
                Recording.accepts(rule, s, 40.5, at(3))))
                .containsExactly(true, false, true, false);
        assertThat(rule.getBody().getBody()).isEqualTo("abs(value - last) >= 2.0 % of (100.0 - 0.0)");
    }

    @Test
    void sampling() {
        SamplingRule rule = RecordingRules.sampling(governance, "every-3rd", 3, temperature);
        Recording.State s = new Recording.State();
        List<Boolean> recorded = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            recorded.add(Recording.accepts(rule, s, i + 1, at(i)));
        }
        assertThat(recorded).containsExactly(true, false, false, true, false, false, true);
        assertThat(rule.getBody().getBody()).isEqualTo("one value out of every 3");
    }

    @Test
    void throttle() {
        ThrottleRule rule = RecordingRules.throttle(governance, "at-most-every-10m", Duration.ofMinutes(10), temperature);
        Recording.State s = new Recording.State();
        assertThat(List.of(
                Recording.accepts(rule, s, 20.0, at(0)),
                Recording.accepts(rule, s, 25.0, at(5)),
                Recording.accepts(rule, s, 21.0, at(10)),
                Recording.accepts(rule, s, 22.0, at(12)),
                Recording.accepts(rule, s, 23.0, at(9))))
                .containsExactly(true, false, true, false, false);
        assertThat(rule.getBody().getBody()).isEqualTo("time - lastTime >= PT10M");
    }

    @Test
    void changedValue() {
        ChangedValueRule rule = RecordingRules.changedValue(governance, "state-changes", state);
        Recording.State s = new Recording.State();
        assertThat(List.of(
                Recording.accepts(rule, s, "open", at(0)),
                Recording.accepts(rule, s, "open", at(1)),
                Recording.accepts(rule, s, "closed", at(2)),
                Recording.accepts(rule, s, "closed", at(3))))
                .containsExactly(true, false, true, false);
        assertThat(rule.getBody().getBody()).isEqualTo("value <> last");
    }

    @Test
    void heartbeatMinimumIntervalAndStatusInOneRule() {
        AbsoluteChangeRule rule = RecordingRules.status(RecordingRules.heartbeat(RecordingRules.minimumInterval(
                RecordingRules.absolute(governance, "temperature-0.5", 0.5, temperature), Duration.ofMinutes(5)),
                Duration.ofHours(1)), quality);
        Recording.State s = new Recording.State();
        assertThat(List.of(
                Recording.accepts(rule, s, 20.0, "good", at(0)),
                Recording.accepts(rule, s, 25.0, "good", at(2)),
                Recording.accepts(rule, s, 25.0, "good", at(5)),
                Recording.accepts(rule, s, 25.2, "good", at(30)),
                Recording.accepts(rule, s, 25.2, "uncertain", at(40)),
                Recording.accepts(rule, s, 25.3, "uncertain", at(100))))
                .containsExactly(true, false, true, false, true, true);
        assertThat(rule.getBody().getBody()).isEqualTo("time - lastTime >= PT5M and ((abs(value - last) >= 0.5) "
                + "or time - lastTime >= PT1H or quality changed)");
    }

    @Test
    void lateValues() {
        AbsoluteChangeRule rule = RecordingRules.absolute(governance, "humidity-2", 2.0, humidity);
        assertThat(late(rule)).containsExactly(true, false, true, false);
        RecordingRules.lateValues(rule, LateValueHandling.DROP);
        assertThat(late(rule)).containsExactly(true, false, false, true);
        assertThat(rule.getBody().getBody()).isEqualTo("abs(value - last) >= 2.0; late values: drop");
        RecordingRules.lateValues(rule, LateValueHandling.INSERT);
        assertThat(late(rule)).containsExactly(true, true, true, true);
    }

    @Test
    void swingingDoor() {
        SwingingDoorRule rule = RecordingRules.swingingDoor(governance, "temperature-door", 0.5, temperature);
        Recording.State s = new Recording.State();
        double[] values = { 20.0, 20.2, 20.4, 20.6, 20.8, 23.0, 23.0, 23.0 };
        List<Decision> decisions = new ArrayList<>();
        List<Sample> recorded = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            Decision d = Recording.decide(rule, s, values[i], null, at(i));
            decisions.add(d);
            s.released().ifPresent(recorded::add);
            if (d.recordsValue()) {
                recorded.add(new Sample(values[i], null, at(i)));
            }
        }
        Recording.flush(s).ifPresent(recorded::add);
        assertThat(decisions).containsExactly(Decision.RECORD, Decision.HOLD, Decision.HOLD, Decision.HOLD,
                Decision.HOLD, Decision.RECORD_HELD_THEN_HOLD, Decision.RECORD_HELD_THEN_HOLD, Decision.HOLD);
        assertThat(recorded).extracting(Sample::value).containsExactly(20.0, 20.8, 23.0, 23.0);
        assertThat(recorded).extracting(Sample::timestamp).containsExactly(at(0), at(4), at(5), at(7));
        assertThat(rule.getBody().getBody()).isEqualTo("swinging door within 0.5");
    }

    // 2 How the history is kept

    @Test
    void aDimensionWithHistory() {
        Customers c = new Customers();
        HistorisationRule versions = HistorisationRules.currentFlag(HistorisationRules.validity(
                HistorisationRules.rows(governance, "customer-versions", HistorisationKind.NEW_VERSION, c.customers),
                c.validFrom, c.validTo), c.isCurrent);
        HistorisationRule name = HistorisationRules.values(governance, "name-overwrite", HistorisationKind.OVERWRITE,
                c.name);
        HistorisationRule city = HistorisationRules.previousValue(HistorisationRules.values(governance,
                "city-previous", HistorisationKind.PREVIOUS_VALUE, c.city), c.previousCity);
        HistorisationRule segment = HistorisationRules.currentValue(HistorisationRules.values(governance,
                "segment-current", HistorisationKind.VERSION_AND_CURRENT, c.segment), c.currentSegment);

        assertThat(versions.getBody().getBody())
                .isEqualTo("rows: newVersion; valid from valid_from to valid_to; current is_current");
        assertThat(name.getBody().getBody()).isEqualTo("values: overwrite");
        assertThat(city.getBody().getBody()).isEqualTo("values: previousValue; previous in previous_city");
        assertThat(segment.getBody().getBody()).isEqualTo("values: versionAndCurrent; current value in current_segment");
        assertThat(HistorisationRules.effective(c.city, DataScope.ROW)).containsSame(versions);
        assertThat(HistorisationRules.effective(c.city, DataScope.VALUE)).containsSame(city);
        assertThat(HistorisationRules.effective(c.validFrom, DataScope.VALUE)).isEmpty();
        assertThat(RecordingModelValidator.check(plant)).isEmpty();
    }

    @Test
    void aHistoryTable() {
        Catalog crm = Catalogs.create("crm");
        Schema core = Schemas.create(crm, "core");
        Table contracts = Tables.create(core, "contracts");
        Columns.create(contracts, "id", SQLSimpleTypes.ownedIn(crm, "INTEGER"));
        Columns.create(contracts, "status", SQLSimpleTypes.ownedIn(crm, "VARCHAR"));
        Table history = Tables.create(core, "contracts_history");
        Columns.create(history, "id", SQLSimpleTypes.ownedIn(crm, "INTEGER"));
        Columns.create(history, "status", SQLSimpleTypes.ownedIn(crm, "VARCHAR"));
        Column changedAt = Columns.create(history, "changed_at", SQLSimpleTypes.ownedIn(crm, "TIMESTAMP"));

        HistorisationRule rule = HistorisationRules.history(HistorisationRules.rows(governance, "contract-history",
                HistorisationKind.HISTORY_TABLE, contracts), history);

        assertThat(rule.getBody().getBody()).isEqualTo("rows: historyTable; history in contracts_history");
        assertThat(changedAt.getOwner()).isSameAs(history);
        assertThat(RecordingModelValidator.check(crm)).isEmpty();
        assertThat(RecordingModelValidator.check(plant)).isEmpty();
    }

    @Test
    void snapshots() {
        Catalog stock = Catalogs.create("stock");
        Table levels = Tables.create(Schemas.create(stock, "inventory"), "stock_levels");
        Column snapshotDate = Columns.create(levels, "snapshot_date", SQLSimpleTypes.ownedIn(stock, "DATE"));
        Columns.create(levels, "quantity", SQLSimpleTypes.ownedIn(stock, "INTEGER"));

        HistorisationRule rule = HistorisationRules.validity(HistorisationRules.rows(governance, "daily-stock",
                HistorisationKind.SNAPSHOT, levels), snapshotDate, null);

        assertThat(rule.getBody().getBody()).isEqualTo("rows: snapshot; valid from snapshot_date");
        assertThat(RecordingModelValidator.check(plant)).isEmpty();
    }

    // 3 Which rule applies

    @Test
    void objectModel() {
        ThrottleRule everyMinute = RecordingRules.throttle(governance, "sensor-1m", Duration.ofMinutes(1), sensor);
        AbsoluteChangeRule deadband = RecordingRules.absolute(governance, "temperature-0.5", 0.5, temperature);

        assertThat(RecordingRules.effective(temperature)).containsSame(deadband);
        assertThat(RecordingRules.effective(humidity)).containsSame(everyMinute);
        assertThat(temperature.getConstraint()).containsExactly(deadband);
        assertThat(RecordingModelValidator.validate(plant)).isEmpty();
    }

    @Test
    void relational() {
        Catalog shop = Catalogs.create("shop");
        Schema sales = Schemas.create(shop, "sales");
        Table orders = Tables.create(sales, "orders");
        Column amount = Columns.create(orders, "amount", SQLSimpleTypes.ownedIn(shop, "DECIMAL"));
        Column status = Columns.create(orders, "status", SQLSimpleTypes.ownedIn(shop, "VARCHAR"));
        Table customers = Tables.create(sales, "customers");
        Column name = Columns.create(customers, "name", SQLSimpleTypes.ownedIn(shop, "VARCHAR"));

        Model rulesModel = Models.create("data-governance");
        Package rules = Packages.create(rulesModel, "sales");
        AbsoluteChangeRule amountHistory = RecordingRules.absolute(rules, "amount-1", 1.0, amount);
        ChangedValueRule statusHistory = RecordingRules.changedValue(rules, "status-changes", status);

        assertThat(RecordingRules.effective(amount)).containsSame(amountHistory);
        assertThat(RecordingRules.effective(status)).containsSame(statusHistory);
        assertThat(RecordingRules.effective(name)).isEmpty();
        assertThat(RecordingModelValidator.check(rulesModel)).isEmpty();
    }

    @Test
    void recordFiles() {
        RecordFile file = RecordFactory.eINSTANCE.createRecordFile();
        file.setName("orders.csv");
        plant.getOwnedElement().add(file);
        RecordDef row = RecordFactory.eINSTANCE.createRecordDef();
        row.setName("OrderRecord");
        file.getOwnedElement().add(row);
        file.getRecord().add(row);
        Field total = field(row, "total", number);
        Field customer = field(row, "customer", text);

        RelativeChangeRule totals = RecordingRules.relative(governance, "total-2pct", 2.0, total);

        assertThat(RecordingRules.effective(total)).containsSame(totals);
        assertThat(RecordingRules.effective(customer)).isEmpty();
        assertThat(RecordingModelValidator.validate(plant)).isEmpty();
    }

    @Test
    void olap() {
        org.eclipse.daanse.cwm.model.cwm.analysis.olap.Schema sales = OlapFactory.eINSTANCE.createSchema();
        sales.setName("sales");
        plant.getOwnedElement().add(sales);
        Cube cube = OlapFactory.eINSTANCE.createCube();
        cube.setName("Sales");
        sales.getCube().add(cube);
        Measure revenue = measure(cube, "revenue", number);
        Measure stock = measure(cube, "stockLevel", number);

        RelativeChangeRule snapshots = RecordingRules.relative(governance, "stock-5pct", 5.0, stock);

        assertThat(RecordingRules.effective(stock)).containsSame(snapshots);
        assertThat(RecordingRules.effective(revenue)).isEmpty();
        assertThat(RecordingModelValidator.check(plant)).isEmpty();
    }

    @Test
    void instances() {
        Extent november = InstanceFactory.eINSTANCE.createExtent();
        november.setName("readings-2026-11");
        plant.getOwnedElement().add(november);
        org.eclipse.daanse.cwm.model.cwm.objectmodel.instance.Object reading = InstanceFactory.eINSTANCE.createObject();
        reading.setName("reading-1");
        reading.setClassifier(sensor);
        november.getOwnedElement().add(reading);
        DataSlot value = InstanceFactory.eINSTANCE.createDataSlot();
        value.setFeature(temperature);
        value.setDataValue("20.5");
        value.setObject(reading);

        AbsoluteChangeRule deadband = RecordingRules.absolute(governance, "temperature-0.5", 0.5, temperature);

        assertThat(RecordingRules.effective(value)).isEmpty();
        assertThat(RecordingRules.effective(value.getFeature())).containsSame(deadband);
    }

    // 4 Where a rule does not reach

    @Test
    void aRuleOnATypeDoesNotReachTheFeaturesOfThatType() {
        RecordingRules.absolute(governance, "all-doubles", 0.1, number);
        assertThat(RecordingRules.effective(temperature)).isEmpty();
    }

    @Test
    void aRecordingRuleNeedsAnElementThatHoldsValues() {
        Measurement rowCount = WarehouseoperationFactory.eINSTANCE.createMeasurement();
        rowCount.setName("orders-row-count");
        plant.getOwnedElement().add(rowCount);
        RecordingRules.throttle(governance, "observe-15m", Duration.ofMinutes(15), rowCount);
        assertThat(rules(RecordingModelValidator.check(plant))).containsExactly("R1");
    }

    // 5 What the validator reports

    @Test
    void twoRecordingRulesOnOneElement() {
        RecordingRules.absolute(governance, "temperature-0.5", 0.5, temperature);
        RecordingRules.throttle(governance, "temperature-1m", Duration.ofMinutes(1), temperature);
        assertThat(RecordingModelValidator.check(plant)).singleElement().satisfies(f -> {
            assertThat(f.rule()).isEqualTo("R2");
            assertThat(f.severity()).isEqualTo(Severity.ERROR);
            assertThat(f.element()).isSameAs(temperature);
        });
    }

    @Test
    void aMinimumIntervalAboveTheHeartbeat() {
        RecordingRules.minimumInterval(RecordingRules.heartbeat(
                RecordingRules.absolute(governance, "temperature-0.5", 0.5, temperature), Duration.ofMinutes(10)),
                Duration.ofHours(1));
        assertThat(rules(RecordingModelValidator.check(plant))).containsExactly("R3");
    }

    @Test
    void aDeadbandOnText() {
        RecordingRules.absolute(governance, "state-1", 1.0, state);
        List<Finding> findings = RecordingModelValidator.check(plant);
        assertThat(rules(findings)).containsExactly("R4");
        assertThat(findings.get(0).severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void aStatusFeatureOfAnotherClass() {
        Class pump = cls(classes, "Pump");
        Attribute pressure = attr(pump, "pressure", number);
        RecordingRules.status(RecordingRules.absolute(governance, "pressure-0.1", 0.1, pressure), quality);
        assertThat(rules(RecordingModelValidator.check(plant))).containsExactly("R5");
    }

    @Test
    void versionsWithoutValidity() {
        Customers c = new Customers();
        HistorisationRules.rows(governance, "customer-versions", HistorisationKind.NEW_VERSION, c.customers);
        assertThat(rules(RecordingModelValidator.check(plant))).containsExactly("R6");
    }

    @Test
    void aBodyEditedByHand() {
        RecordingRule rule = RecordingRules.absolute(governance, "temperature-0.5", 0.5, temperature);
        rule.getBody().setBody("abs(value - last) >= 1.0");
        assertThat(rules(RecordingModelValidator.check(plant))).containsExactly("R7");
    }

    // 6 In XMI

    /** Lines of the XMI the examples serialize to. */
    private static final String XMI = """
            <ownedElement xsi:type="recording:AbsoluteChangeRule" name="temperature-0.5" \
            constrainedElement="_Sensor.temperature" minimumInterval="PT5M" maximumInterval="PT1H" \
            statusFeature="_Sensor.quality" delta="0.5">
            <body body="time - lastTime >= PT5M and ((abs(value - last) >= 0.5) or time - lastTime >= PT1H or \
            quality changed)" \
            language="daanse.lifecycle"/>
            </ownedElement>
            <ownedElement xsi:type="recording:HistorisationRule" name="customer-versions" \
            constrainedElement="_customers" scope="row" kind="newVersion" validFrom="_customers.valid_from" \
            validTo="_customers.valid_to" currentFlag="_customers.is_current">
            <body body="rows: newVersion; valid from valid_from to valid_to; current is_current" \
            language="daanse.lifecycle"/>
            </ownedElement>
            <ownedElement xsi:type="recording:HistorisationRule" name="city-previous" \
            constrainedElement="_customers.city" kind="previousValue" previousValue="_customers.previous_city">
            <body body="values: previousValue; previous in previous_city" language="daanse.lifecycle"/>
            </ownedElement>
            <feature xsi:type="core:Attribute" name="temperature" constraint="_temperature-0.5" type="_Double"/>
            <ownedElement xsi:type="relational:Table" name="customers" constraint="_customer-versions">
            <feature xsi:type="relational:Column" name="city" constraint="_city-previous" \
            type="_CHARACTER_VARYING"/>
            """;

    @Test
    void theXmiOfTheDocumentIsWhatAModelSerializesTo() throws Exception {
        RecordingRules.status(RecordingRules.heartbeat(RecordingRules.minimumInterval(
                RecordingRules.absolute(governance, "temperature-0.5", 0.5, temperature), Duration.ofMinutes(5)),
                Duration.ofHours(1)), quality);
        Customers c = new Customers();
        HistorisationRules.currentFlag(HistorisationRules.validity(
                HistorisationRules.rows(governance, "customer-versions", HistorisationKind.NEW_VERSION, c.customers),
                c.validFrom, c.validTo), c.isCurrent);
        HistorisationRules.previousValue(HistorisationRules.values(governance, "city-previous",
                HistorisationKind.PREVIOUS_VALUE, c.city), c.previousCity);

        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
        Resource r = rs.createResource(URI.createURI("plant.xmi"));
        r.getContents().add(plant);
        r.getContents().add(c.crm);
        ShortIds.assign(r);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        r.save(out, Map.of(XMLResource.OPTION_ENCODING, "UTF-8"));
        String xmi = out.toString(StandardCharsets.UTF_8).replaceAll(" xmi:id=\"[^\"]*\"", "");
        Files.writeString(Path.of(System.getProperty("user.dir"), "target/recording-across-cwm.xmi"), xmi,
                StandardCharsets.UTF_8);

        for (String line : XMI.split("\n")) {
            assertThat(xmi).as(line).contains(line.trim());
        }
    }

    /** The table {@code customers} of a dimension with history, in schema {@code core} of catalog {@code crm}. */
    private static final class Customers {
        final Catalog crm = Catalogs.create("crm");
        final Table customers = Tables.create(Schemas.create(crm, "core"), "customers");
        final Column name = column("name", "VARCHAR");
        final Column city = column("city", "VARCHAR");
        final Column segment = column("segment", "VARCHAR");
        final Column validFrom = column("valid_from", "TIMESTAMP");
        final Column validTo = column("valid_to", "TIMESTAMP");
        final Column isCurrent = column("is_current", "BOOLEAN");
        final Column previousCity = column("previous_city", "VARCHAR");
        final Column currentSegment = column("current_segment", "VARCHAR");

        private Column column(String columnName, String type) {
            return Columns.create(customers, columnName, SQLSimpleTypes.ownedIn(crm, type));
        }
    }

    private List<Boolean> late(AbsoluteChangeRule rule) {
        Recording.State s = new Recording.State();
        return List.of(
                Recording.accepts(rule, s, 50, at(10)),
                Recording.accepts(rule, s, 51, at(5)),
                Recording.accepts(rule, s, 55, at(6)),
                Recording.accepts(rule, s, 56, at(12)));
    }

    private static List<String> rules(List<Finding> findings) {
        return findings.stream().map(Finding::rule).toList();
    }

    private static Instant at(int minutes) {
        return T0.plus(Duration.ofMinutes(minutes));
    }

    private static Class cls(Package pkg, String name) {
        Class c = CORE.createClass();
        c.setName(name);
        pkg.getOwnedElement().add(c);
        return c;
    }

    private static Attribute attr(Class owner, String name, Classifier type) {
        Attribute a = CORE.createAttribute();
        a.setName(name);
        a.setType(type);
        owner.getFeature().add(a);
        return a;
    }

    private static Field field(RecordDef owner, String name, Classifier type) {
        Field f = RecordFactory.eINSTANCE.createField();
        f.setName(name);
        f.setType(type);
        owner.getFeature().add(f);
        return f;
    }

    private static Measure measure(Cube cube, String name, Classifier type) {
        Measure m = OlapFactory.eINSTANCE.createMeasure();
        m.setName(name);
        m.setType(type);
        cube.getFeature().add(m);
        return m;
    }
}
