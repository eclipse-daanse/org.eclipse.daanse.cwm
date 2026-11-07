/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.analysis.olap.Cube;
import org.eclipse.daanse.cwm.model.cwm.analysis.olap.Measure;
import org.eclipse.daanse.cwm.model.cwm.analysis.olap.OlapFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.softwaredeployment.DataManager;
import org.eclipse.daanse.cwm.model.cwm.foundation.softwaredeployment.SoftwaredeploymentFactory;
import org.eclipse.daanse.cwm.model.cwm.management.warehouseoperation.Measurement;
import org.eclipse.daanse.cwm.model.cwm.management.warehouseoperation.WarehouseoperationFactory;
import org.eclipse.daanse.cwm.model.cwm.management.warehouseprocess.ProcessPackage;
import org.eclipse.daanse.cwm.model.cwm.management.warehouseprocess.WarehouseActivity;
import org.eclipse.daanse.cwm.model.cwm.management.warehouseprocess.WarehouseprocessFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.BehavioralFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Event;
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
import org.eclipse.daanse.cwm.model.cwm.resource.multidimensional.DimensionedObject;
import org.eclipse.daanse.cwm.model.cwm.resource.multidimensional.MultidimensionalFactory;
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
import org.eclipse.daanse.cwm.model.cwm.resource.xml.ElementType;
import org.eclipse.daanse.cwm.model.cwm.resource.xml.XmlFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.AggregateFunction;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Alignment;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.FeatureTime;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Stage;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Durations;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Finding;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator.Severity;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleRules;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Limits;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.PeriodStarts;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Periods;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.Due;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.Starts;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.State;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.Step;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Retention.Suspension;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.RetentionSchedules;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Stages;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;

/**
 * Retention schedules at the places of CWM: how a schedule decides, which schedule applies where,
 * where a schedule does not reach, what the validator reports, and how schedules look in XMI.
 */
class SchedulesAcrossCwmTest {

    private static final CoreFactory CORE = CoreFactory.eINSTANCE;
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final Model plant = Models.create("plant");
    private final Package governance = Packages.create(plant, "governance");
    private final Package classes = Packages.create(plant, "classes");
    private final DataType number = DataTypes.create(classes, "Double");
    private final DataType text = DataTypes.create(classes, "String");
    private final DataType timestamp = DataTypes.create(classes, "Timestamp");
    private final Class sensor = cls(classes, "Sensor");
    private final Attribute temperature = attr(sensor, "temperature", number);
    private final Attribute humidity = attr(sensor, "humidity", number);
    private final Attribute takenAt = attr(sensor, "takenAt", timestamp);

    // 1 How a schedule decides

    @Test
    void deleteAfterAPeriod() {
        RetentionSchedule schedule = RetentionSchedules.values(governance, "temperature-90d", "P90D", temperature);
        assertThat(schedule.getBody().getBody()).isEqualTo("from the time of the value: delete after P90D");
        assertThat(state(schedule, temperature, T0, T0.plus(Duration.ofDays(89)))).containsExactly(State.PENDING);
        assertThat(state(schedule, temperature, T0, T0.plus(Duration.ofDays(90)))).containsExactly(State.DUE);
    }

    @Test
    void stagesOneAfterAnother() {
        RetentionSchedule schedule = RetentionSchedules.create(governance, "sensor-history", PeriodStarts.valueTime(),
                sensor);
        Stages.tier(schedule, "cold", "P30D", "cold", true);
        Stages.aggregate(schedule, "hourly", "P1Y", "PT1H", AggregateFunction.AVERAGE, null);
        Stages.delete(schedule, "delete", "P5Y");
        assertThat(schedule.getBody().getBody()).isEqualTo("from the time of the value: tier cold compressed after "
                + "P30D, aggregate per PT1H as average after P1Y, delete after P5Y");
        Retention.Plan plan = Retention.plan(temperature, Starts.at(T0), Instant.parse("2028-01-01T00:00:00Z"),
                Suspension.NONE);
        assertThat(plan.stages()).extracting(Due::state).containsExactly(State.PAST, State.DUE, State.PENDING);
        assertThat(plan.next()).map(Due::at).contains(Instant.parse("2031-01-01T00:00:00Z"));
    }

    @Test
    void whereThePeriodsStart() {
        Class invoice = cls(classes, "Invoice");
        Attribute issuedOn = attr(invoice, "issuedOn", timestamp);
        Class contract = cls(classes, "Contract");
        Attribute endedOn = attr(contract, "endedOn", timestamp);
        Event contractEnded = BehavioralFactory.eINSTANCE.createEvent();
        contractEnded.setName("contractEnded");
        governance.getOwnedElement().add(contractEnded);

        FeatureTime endOfYear = PeriodStarts.aligned(PeriodStarts.feature(issuedOn), Alignment.END_OF_YEAR,
                "Europe/Berlin");
        RetentionSchedule invoices = RetentionSchedules.rows(governance, "invoices-10y", "P10Y", endOfYear, invoice);
        RetentionSchedule contracts = RetentionSchedules.rows(governance, "contracts-3y", "P3Y",
                PeriodStarts.event(contractEnded, endedOn), contract);

        assertThat(invoices.getBody().getBody())
                .isEqualTo("from issuedOn, end of year in Europe/Berlin: delete after P10Y");
        assertThat(contracts.getBody().getBody()).isEqualTo("from event contractEnded (endedOn): delete after P3Y");
        Instant issued = ZonedDateTime.of(2020, 6, 15, 9, 30, 0, 0, BERLIN).toInstant();
        assertThat(Retention.dueAt(invoices.getStage().get(0), endOfYear, issued))
                .contains(ZonedDateTime.of(2031, 1, 1, 0, 0, 0, 0, BERLIN).toInstant());
        assertThat(LifecycleModelValidator.check(plant)).isEmpty();
    }

    @Test
    void limits() {
        Class reading = cls(classes, "Reading");
        Attribute sensorId = attr(reading, "sensorId", text);
        RetentionSchedule lastThousand = RetentionSchedules.keeping(governance, "last-1000", 1000, humidity);
        RetentionSchedule perSensor = RetentionSchedules.rows(governance, "readings-1y", "P1Y", PeriodStarts.valueTime(),
                reading);
        Limits.count(perSensor, 100, sensorId);
        Limits.size(perSensor, 50_000_000);
        assertThat(lastThousand.getBody().getBody()).isEqualTo("keep the newest 1000");
        assertThat(perSensor.getBody().getBody()).isEqualTo("from the time of the value: delete after P1Y; keep the "
                + "newest 100 per sensorId; keep at most 50000000 bytes");
        assertThat(LifecycleModelValidator.check(plant)).isEmpty();
    }

    @Test
    void valuesAndRowsTogether() {
        RetentionSchedule values = RetentionSchedules.create(governance, "temperature-history", PeriodStarts.valueTime(),
                temperature);
        Stages.tier(values, "cold", "P30D", "cold", false);
        Stage valuesDelete = Stages.delete(values, "delete", "P5Y");
        RetentionSchedule rows = RetentionSchedules.rows(governance, "sensor-rows-1y", "P1Y", PeriodStarts.valueTime(),
                sensor);

        Retention.Effective effective = Retention.effective(temperature);
        assertThat(effective).isEqualTo(new Retention.Effective(values, rows));
        assertThat(Retention.timeline(effective)).extracting(s -> s.stage().getName())
                .containsExactly("cold", "delete", "delete");
        assertThat(Retention.timeline(effective)).extracting(Step::schedule).containsExactly(values, rows, values);
        assertThat(Retention.unreachable(effective)).extracting(Step::stage).containsExactly(valuesDelete);
        assertThat(LifecycleModelValidator.check(plant)).singleElement().satisfies(f -> {
            assertThat(f.rule()).isEqualTo("L6");
            assertThat(f.severity()).isEqualTo(Severity.WARNING);
        });
    }

    @Test
    void periodsAndDurations() {
        assertThat(Periods.isExact("P90D")).isTrue();
        assertThat(Periods.isExact("P10Y")).isFalse();
        assertThat(Periods.toDuration("P2W")).isEqualTo(Duration.ofDays(14));
        assertThat(Periods.compare("P1Y", "P12M").exact()).isTrue();
        assertThat(Periods.compare("P1Y", "P365D").exact()).isFalse();
        assertThat(Durations.format(Duration.ofHours(36))).isEqualTo("P1DT12H");
        assertThat(Durations.isValid("P1M")).isFalse();
        assertThat(Durations.largestUnit(Duration.ofMinutes(90))).isEqualTo(new Durations.Amount(90, ChronoUnit.MINUTES));
    }

    // 2 Which schedule applies

    @Test
    void objectModel() {
        RetentionSchedule plantDefault = RetentionSchedules.values(governance, "plant-1y", "P1Y", plant);
        RetentionSchedule sensors = RetentionSchedules.values(governance, "sensor-90d", "P90D", sensor);
        RetentionSchedule temperatures = RetentionSchedules.values(governance, "temperature-30d", "P30D", temperature);

        assertThat(RetentionSchedules.effective(temperature, DataScope.VALUE)).containsSame(temperatures);
        assertThat(RetentionSchedules.effective(humidity, DataScope.VALUE)).containsSame(sensors);
        assertThat(RetentionSchedules.effective(number, DataScope.VALUE)).containsSame(plantDefault);
        assertThat(temperature.getConstraint()).containsExactly(temperatures);
        assertThat(LifecycleModelValidator.validate(plant)).isEmpty();
    }

    @Test
    void relational() {
        Catalog shop = Catalogs.create("shop");
        Schema sales = Schemas.create(shop, "sales");
        Table orders = Tables.create(sales, "orders");
        Column amount = Columns.create(orders, "amount", SQLSimpleTypes.ownedIn(shop, "DECIMAL"));
        Column status = Columns.create(orders, "status", SQLSimpleTypes.ownedIn(shop, "VARCHAR"));
        Column createdAt = Columns.create(orders, "created_at", SQLSimpleTypes.ownedIn(shop, "TIMESTAMP"));
        Table customers = Tables.create(sales, "customers");
        Column name = Columns.create(customers, "name", SQLSimpleTypes.ownedIn(shop, "VARCHAR"));

        Model rulesModel = Models.create("data-governance");
        Package rules = Packages.create(rulesModel, "sales");
        RetentionSchedule schemaDefault = RetentionSchedules.values(rules, "sales-5y", "P5Y", sales);
        RetentionSchedule orderRows = RetentionSchedules.cleanupEvery(RetentionSchedules.rows(rules, "orders-2y", "P2Y",
                PeriodStarts.feature(createdAt), orders), Duration.ofDays(1));
        RetentionSchedule amountHistory = RetentionSchedules.values(rules, "amount-history-1y", "P1Y", amount);

        assertThat(Retention.effective(amount)).isEqualTo(new Retention.Effective(amountHistory, orderRows));
        assertThat(Retention.effective(status)).isEqualTo(new Retention.Effective(schemaDefault, orderRows));
        assertThat(Retention.effective(name)).isEqualTo(new Retention.Effective(schemaDefault, null));
        assertThat(orderRows.getBody().getBody()).isEqualTo("from created_at: delete after P2Y; cleanup every P1D");
        assertThat(LifecycleModelValidator.check(rulesModel)).isEmpty();
    }

    @Test
    void recordFiles() {
        RecordFile file = RecordFactory.eINSTANCE.createRecordFile();
        file.setName("orders.csv");
        plant.getOwnedElement().add(file);
        RecordDef row = record(file, "OrderRecord");
        RecordDef trailer = record(file, "TrailerRecord");
        Field orderedAt = field(row, "orderedAt", timestamp);
        Field total = field(row, "total", number);
        Field count = field(trailer, "count", number);

        RetentionSchedule deliveries = RetentionSchedules.rows(governance, "deliveries-30d", "P30D",
                PeriodStarts.valueTime(), file);
        RetentionSchedule orders = RetentionSchedules.rows(governance, "order-rows-1y", "P1Y",
                PeriodStarts.feature(orderedAt), row);

        assertThat(RetentionSchedules.effective(total, DataScope.ROW)).containsSame(orders);
        assertThat(RetentionSchedules.effective(count, DataScope.ROW)).containsSame(deliveries);
        assertThat(LifecycleModelValidator.validate(plant)).isEmpty();
    }

    @Test
    void xml() {
        org.eclipse.daanse.cwm.model.cwm.resource.xml.Schema schema = XmlFactory.eINSTANCE.createSchema();
        schema.setName("orders.xsd");
        plant.getOwnedElement().add(schema);
        ElementType order = XmlFactory.eINSTANCE.createElementType();
        order.setName("order");
        schema.getOwnedElement().add(order);
        org.eclipse.daanse.cwm.model.cwm.resource.xml.Attribute placed = XmlFactory.eINSTANCE.createAttribute();
        placed.setName("placed");
        placed.setType(text);
        order.getFeature().add(placed);

        RetentionSchedule documents = RetentionSchedules.rows(governance, "order-documents-1y", "P1Y",
                PeriodStarts.feature(placed), order);

        assertThat(RetentionSchedules.effective(placed, DataScope.ROW)).containsSame(documents);
        assertThat(LifecycleModelValidator.validate(plant)).isEmpty();
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

        RetentionSchedule cubes = RetentionSchedules.values(governance, "cubes-3y", "P3Y", sales);
        RetentionSchedule daily = RetentionSchedules.create(governance, "stock-daily", PeriodStarts.valueTime(), stock);
        Stages.aggregate(daily, "daily", "P90D", "P1D", AggregateFunction.LAST, null);
        Stages.delete(daily, "delete", "P3Y");

        assertThat(cube.getNamespace()).as("a cube is held in Schema.cube, not in ownedElement").isNull();
        assertThat(LifecycleRules.next(cube)).isSameAs(sales);
        assertThat(RetentionSchedules.effective(revenue, DataScope.VALUE)).containsSame(cubes);
        assertThat(RetentionSchedules.effective(stock, DataScope.VALUE)).containsSame(daily);
        assertThat(LifecycleModelValidator.check(plant)).isEmpty();
    }

    @Test
    void multidimensional() {
        org.eclipse.daanse.cwm.model.cwm.resource.multidimensional.Schema planning =
                MultidimensionalFactory.eINSTANCE.createSchema();
        planning.setName("planning");
        plant.getOwnedElement().add(planning);
        DimensionedObject budget = MultidimensionalFactory.eINSTANCE.createDimensionedObject();
        budget.setName("budget");
        budget.setType(number);
        planning.getDimensionedObject().add(budget);

        RetentionSchedule versions = RetentionSchedules.keeping(governance, "last-12-versions", 12, planning);

        assertThat(RetentionSchedules.effective(budget, DataScope.VALUE)).containsSame(versions);
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

        RetentionSchedule month = RetentionSchedules.rows(governance, "readings-90d", "P90D", PeriodStarts.valueTime(),
                november);
        RetentionSchedule history = RetentionSchedules.values(governance, "temperature-30d", "P30D", temperature);

        assertThat(RetentionSchedules.effective(value, DataScope.ROW)).containsSame(month);
        assertThat(RetentionSchedules.effective(value.getFeature(), DataScope.VALUE)).containsSame(history);
    }

    @Test
    void warehouseOperationAndProcess() {
        Measurement rowCount = WarehouseoperationFactory.eINSTANCE.createMeasurement();
        rowCount.setName("orders-row-count");
        plant.getOwnedElement().add(rowCount);
        ProcessPackage processes = WarehouseprocessFactory.eINSTANCE.createProcessPackage();
        processes.setName("processes");
        plant.getOwnedElement().add(processes);
        WarehouseActivity nightly = WarehouseprocessFactory.eINSTANCE.createWarehouseActivity();
        nightly.setName("nightly-load");
        processes.getOwnedElement().add(nightly);

        RetentionSchedule observations = RetentionSchedules.values(governance, "observations-30d", "P30D", rowCount);
        RetentionSchedule runs = RetentionSchedules.keeping(governance, "last-100-runs", 100, nightly);

        assertThat(RetentionSchedules.effective(rowCount, DataScope.VALUE)).containsSame(observations);
        assertThat(RetentionSchedules.effective(nightly, DataScope.VALUE)).containsSame(runs);
        assertThat(LifecycleModelValidator.validate(plant)).isEmpty();
    }

    // 3 Where a schedule does not reach

    @Test
    void aScheduleOnATypeDoesNotReachTheFeaturesOfThatType() {
        RetentionSchedules.values(governance, "all-doubles", "P1D", number);
        assertThat(RetentionSchedules.effective(temperature, DataScope.VALUE)).isEmpty();
    }

    @Test
    void aScheduleOnADataManagerDoesNotReachItsDataPackage() {
        Catalog shop = Catalogs.create("shop");
        Table orders = Tables.create(Schemas.create(shop, "sales"), "orders");
        DataManager database = SoftwaredeploymentFactory.eINSTANCE.createDataManager();
        database.setName("postgres-prod");
        database.getDataPackage().add(shop);
        plant.getOwnedElement().add(database);
        RetentionSchedules.values(governance, "database-1y", "P1Y", database);
        assertThat(RetentionSchedules.effective(orders, DataScope.VALUE)).isEmpty();
    }

    // 4 What the validator reports

    @Test
    void aScheduleOnAStage() {
        RetentionSchedule schedule = RetentionSchedules.values(governance, "temperature-30d", "P30D", temperature);
        RetentionSchedules.values(governance, "on-a-stage", "P1Y", schedule.getStage().get(0));
        assertThat(rules(LifecycleModelValidator.check(plant))).containsExactly("L1");
    }

    @Test
    void twoSchedulesOfValuesOnOneElement() {
        RetentionSchedules.values(governance, "temperature-30d", "P30D", temperature);
        RetentionSchedules.values(governance, "temperature-1y", "P1Y", temperature);
        assertThat(LifecycleModelValidator.check(plant)).singleElement().satisfies(f -> {
            assertThat(f.rule()).isEqualTo("L2");
            assertThat(f.severity()).isEqualTo(Severity.ERROR);
            assertThat(f.element()).isSameAs(temperature);
        });
    }

    @Test
    void anAlignmentWithoutZone() {
        RetentionSchedule schedule = RetentionSchedules.values(governance, "temperature-1y", "P1Y", temperature);
        schedule.getStart().setAlignment(Alignment.END_OF_YEAR);
        RetentionSchedules.refreshed(schedule);
        assertThat(rules(LifecycleModelValidator.check(plant))).containsExactly("L3");
    }

    @Test
    void aTimeFeatureOfAnotherTable() {
        Catalog shop = Catalogs.create("shop");
        Schema sales = Schemas.create(shop, "sales");
        Table orders = Tables.create(sales, "orders");
        Table invoices = Tables.create(sales, "invoices");
        Column issuedAt = Columns.create(invoices, "issued_at", SQLSimpleTypes.ownedIn(shop, "TIMESTAMP"));
        RetentionSchedules.rows(governance, "orders-2y", "P2Y", PeriodStarts.feature(issuedAt), orders);
        assertThat(rules(LifecycleModelValidator.check(plant))).containsExactly("L4");
    }

    @Test
    void stagesOutOfOrder() {
        RetentionSchedule schedule = RetentionSchedules.values(governance, "temperature-1y", "P1Y", temperature);
        Stages.tier(schedule, "cold", "P2Y", "cold", false);
        assertThat(rules(LifecycleModelValidator.check(plant))).containsExactly("L5");
    }

    @Test
    void aTransferToWhereTheDataIs() {
        RetentionSchedule schedule = RetentionSchedules.create(governance, "sensor-archive", PeriodStarts.valueTime(),
                sensor);
        Stages.transfer(schedule, "archive", "P1Y", classes);
        assertThat(rules(LifecycleModelValidator.check(plant))).containsExactly("L7");
    }

    @Test
    void aBodyEditedByHand() {
        RetentionSchedule schedule = RetentionSchedules.values(governance, "temperature-30d", "P30D", temperature);
        schedule.getBody().setBody("from the time of the value: delete after P90D");
        assertThat(rules(LifecycleModelValidator.check(plant))).containsExactly("L9");
    }

    @Test
    void aScheduleOnItsOwnNamespace() {
        RetentionSchedules.values(governance, "everything", "P1Y", governance);
        assertThat(rules(LifecycleModelValidator.check(plant))).containsExactly("L10");
    }

    // 6 Using the schedules

    @Test
    void aPurgeJobAsksThePlan() {
        RetentionSchedule schedule = RetentionSchedules.create(governance, "sensor-history", PeriodStarts.feature(takenAt),
                sensor);
        Stages.restrict(schedule, "restrict", "P1Y");
        Stages.delete(schedule, "delete", "P2Y");
        Instant measured = Instant.parse("2026-03-01T10:00:00Z");
        Starts starts = start -> Optional.of(measured);
        Retention.Plan plan = Retention.plan(temperature, starts, Instant.parse("2027-06-01T00:00:00Z"), Suspension.NONE);
        assertThat(plan.current()).map(d -> d.step().stage().getName()).contains("restrict");
        assertThat(plan.next()).map(Due::at).contains(Instant.parse("2028-03-01T10:00:00Z"));
        Retention.Plan held = Retention.plan(temperature, starts, Instant.parse("2028-06-01T00:00:00Z"),
                Suspension.between(Instant.parse("2028-01-01T00:00:00Z"), null));
        assertThat(held.stages()).extracting(Due::state).containsExactly(State.DUE, State.SUSPENDED);
    }

    private static List<State> state(RetentionSchedule schedule, Attribute element, Instant start, Instant now) {
        return Retention.plan(element, Starts.at(start), now, Suspension.NONE).stages().stream().map(Due::state).toList();
    }

    private static List<String> rules(List<Finding> findings) {
        return findings.stream().map(Finding::rule).toList();
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

    private static RecordDef record(RecordFile file, String name) {
        RecordDef row = RecordFactory.eINSTANCE.createRecordDef();
        row.setName(name);
        file.getOwnedElement().add(row);
        file.getRecord().add(row);
        return row;
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

    // In XMI

    /** Lines of the XMI {@link #inXmi()} writes: the schedules, and the elements that name them. */
    private static final String XMI = """
            <ownedElement xsi:type="relational:Table" name="invoices" constraint="_invoices-10y">
            <feature xsi:type="relational:Column" name="issued_on" type="_DATE"/>
            <feature xsi:type="relational:Column" name="note" constraint="_note-90d" type="_CHARACTER_VARYING"/>
            <ownedElement xsi:type="lifecycle:RetentionSchedule" name="invoices-10y" \
            constrainedElement="_invoices" scope="row">
            <body body="from issued_on, end of year in Europe/Berlin: restrict after P8Y, delete after P10Y" \
            language="daanse.lifecycle"/>
            <start xsi:type="lifecycle:FeatureTime" alignment="endOfYear" zone="Europe/Berlin" \
            feature="_invoices.issued_on"/>
            <stage xsi:type="lifecycle:Restrict" name="restrict" after="P8Y"/>
            <stage xsi:type="lifecycle:Delete" name="delete" after="P10Y"/>
            <ownedElement xsi:type="lifecycle:RetentionSchedule" name="note-90d" \
            constrainedElement="_invoices.note">
            <body body="from the time of the value: delete after P90D" language="daanse.lifecycle"/>
            <start xsi:type="lifecycle:ValueTime"/>
            <stage xsi:type="lifecycle:Delete" name="delete" after="P90D"/>
            """;

    @Test
    void inXmi() throws IOException {
        Model erp = Models.create("erp");
        Catalog finance = Catalogs.create(erp, "finance");
        Table invoices = Tables.create(Schemas.create(finance, "ledger"), "invoices");
        Column issuedOn = Columns.create(invoices, "issued_on", SQLSimpleTypes.ownedIn(finance, "DATE"));
        Column note = Columns.create(invoices, "note", SQLSimpleTypes.ownedIn(finance, "VARCHAR"));
        Package rules = Packages.create(erp, "rules");
        RetentionSchedule books = RetentionSchedules.create(rules, "invoices-10y", DataScope.ROW,
                PeriodStarts.aligned(PeriodStarts.feature(issuedOn), Alignment.END_OF_YEAR, "Europe/Berlin"),
                invoices);
        Stages.restrict(books, "restrict", "P8Y");
        Stages.delete(books, "delete", "P10Y");
        RetentionSchedules.values(rules, "note-90d", "P90D", note);
        assertThat(LifecycleModelValidator.check(erp)).isEmpty();

        String xmi = xmi(erp, "schedules.xmi");
        for (String line : XMI.split("\n")) {
            assertThat(xmi).as(line).contains(line.trim());
        }
    }

    /** {@code root} as XMI with short ids, written to {@code target/<file>} as well. */
    static String xmi(EObject root, String file) throws IOException {
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
