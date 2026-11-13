/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.resource.relational.diff.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.SqlGenAssertions.executeAll;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.BooleanExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.UniqueConstraints;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Views;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.DialectProfile;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangePlanner;
import org.eclipse.daanse.cwm.resource.relational.diff.api.SchemaDiff;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.jdbc.api.DatabaseService;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.StructureInfo;
import org.eclipse.daanse.sql.jdbc.impl.DatabaseServiceImpl;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Migrations that run on every database: a table the migration adds gets its unique constraint,
 * check, index and foreign key, also where the dialect declares them in CREATE TABLE; a column that an
 * unchanged view reads can be dropped, and tables that refer to each other can be dropped.
 */
class DiffMigrationOrderParameterizedTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;
    private static final DatabaseService DB_SERVICE = new DatabaseServiceImpl();

    static Stream<DialectProfile> dialects() {
        return Stream.of(DialectProfile.values());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void anAddedTableGetsItsUniqueConstraintCheckIndexAndForeignKey(DialectProfile profile) throws Exception {
        ActiveDatabase db = activateOrSkip(profile);
        try (Connection c = db.dataSource().getConnection()) {
            Dialect dialect = db.dialect();
            String schemaName = profile.schemaName();
            Schema oldSchema = customers(schemaName, false);
            Schema newSchema = customers(schemaName, false);
            orders(newSchema, dialect);
            try {
                executeAll(c, profile.create(dialect, oldSchema));

                SchemaDiff diff = new SchemaDifferImpl().diff(oldSchema, newSchema);
                executeAll(c, new MigrationEmitterImpl(new DdlGeneratorFactoryImpl())
                        .emit(ChangePlanner.create().plan(diff), dialect));

                profile.prepareForMetadata(c);
                MetaInfo info = profile.snapshot(DB_SERVICE, c, dialect);
                StructureInfo si = info.structureInfo();
                assertThat(si.uniqueConstraints()).as("unique constraints").anySatisfy(uc -> {
                    assertThat(is(uc.table(), schemaName, "ORDERS")).isTrue();
                    assertThat(names(uc.columns())).containsExactly("CODE");
                });
                assertThat(si.checkConstraints()).as("checks")
                        .anySatisfy(ck -> assertThat(is(ck.table(), schemaName, "ORDERS")).isTrue());
                assertThat(si.importedKeys()).as("foreign keys").anySatisfy(fk -> assertThat(
                        fk.foreignKeyColumn().table().filter(t -> is(t, schemaName, "ORDERS"))).isPresent());
                assertThat(info.indexInfos()).as("indexes")
                        .filteredOn(ii -> is(ii.tableReference(), schemaName, "ORDERS"))
                        .flatMap(ii -> ii.indexInfoItems())
                        .anySatisfy(item -> assertThat(item.indexName()).hasValueSatisfying(
                                name -> assertThat(name).isEqualToIgnoringCase("IX_ORDERS_QTY")));
            } finally {
                profile.cleanup(c, newSchema, dialect);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void aColumnAnUnchangedViewReadsAndTablesThatReferToEachOtherCanGo(DialectProfile profile) throws Exception {
        ActiveDatabase db = activateOrSkip(profile);
        try (Connection c = db.dataSource().getConnection()) {
            Dialect dialect = db.dialect();
            String schemaName = profile.schemaName();
            Schema oldSchema = customers(schemaName, true);
            view(oldSchema, dialect);
            parentAndChild(oldSchema);
            Schema newSchema = customers(schemaName, false);
            view(newSchema, dialect);
            try {
                executeAll(c, profile.create(dialect, oldSchema));

                SchemaDiff diff = new SchemaDifferImpl().diff(oldSchema, newSchema);
                executeAll(c, new MigrationEmitterImpl(new DdlGeneratorFactoryImpl())
                        .emit(ChangePlanner.create().plan(diff), dialect));

                try (Statement s = c.createStatement();
                        ResultSet rs = s.executeQuery("SELECT * FROM "
                                + dialect.quoteIdentifier(schemaName, "V_CUSTOMERS"))) {
                    assertThat(rs.getMetaData().getColumnCount()).isEqualTo(2);
                }
            } finally {
                profile.cleanup(c, oldSchema, dialect);
                profile.cleanup(c, newSchema, dialect);
            }
        }
    }

    /** {@code CUSTOMERS(ID PK, NAME[, NICK])}. */
    private static Schema customers(String schemaName, boolean withNick) {
        Schema schema = RF.createSchema();
        schema.setName(schemaName);
        Table customers = table(schema, "CUSTOMERS");
        Column id = column(customers, "ID", integer(), true);
        column(customers, "NAME", varchar(), false);
        if (withNick) {
            column(customers, "NICK", varchar(), false);
        }
        PrimaryKeys.create(customers, "PK_CUSTOMERS", id);
        return schema;
    }

    /** {@code ORDERS(ID PK, CUSTOMER_ID → CUSTOMERS, CODE UNIQUE, QTY CHECK)} with an index on QTY. */
    private static void orders(Schema schema, Dialect dialect) {
        Table customers = schema.getOwnedElement().stream().filter(Table.class::isInstance).map(Table.class::cast)
                .filter(t -> "CUSTOMERS".equals(t.getName())).findFirst().orElseThrow();
        PrimaryKey customerKey = customers.getOwnedElement().stream().filter(PrimaryKey.class::isInstance)
                .map(PrimaryKey.class::cast).findFirst().orElseThrow();
        Table orders = table(schema, "ORDERS");
        Column id = column(orders, "ID", integer(), true);
        Column customerId = column(orders, "CUSTOMER_ID", integer(), true);
        Column code = column(orders, "CODE", integer(), true);
        Column qty = column(orders, "QTY", integer(), true);
        PrimaryKeys.create(orders, "PK_ORDERS", id);
        ForeignKeys.create(orders, "FK_ORDERS_CUSTOMERS", customerKey, customerId);
        UniqueConstraints.create(orders, "UQ_ORDERS_CODE", code);
        CheckConstraint check = RF.createCheckConstraint();
        check.setName("CK_ORDERS_QTY");
        BooleanExpression body = CoreFactory.eINSTANCE.createBooleanExpression();
        body.setLanguage("SQL");
        body.setBody(dialect.quoteIdentifier("QTY") + " > 0");
        check.setBody(body);
        orders.getOwnedElement().add(check);
        schema.getOwnedElement().add(SQLIndexes.index("IX_ORDERS_QTY", false, orders, qty));
    }

    /** {@code V_CUSTOMERS}: every column of CUSTOMERS. */
    private static void view(Schema schema, Dialect dialect) {
        Views.create(schema, "V_CUSTOMERS", "SELECT * FROM " + dialect.quoteIdentifier(schema.getName(), "CUSTOMERS"));
    }

    /** {@code PARENT(ID PK)} and {@code CHILD(ID, PARENT_ID → PARENT)}, the referenced one first. */
    private static void parentAndChild(Schema schema) {
        Table parent = table(schema, "PARENT");
        PrimaryKey parentKey = PrimaryKeys.create(parent, "PK_PARENT", column(parent, "ID", integer(), true));
        Table child = table(schema, "CHILD");
        column(child, "ID", integer(), true);
        ForeignKeys.create(child, "FK_CHILD_PARENT", parentKey, column(child, "PARENT_ID", integer(), false));
    }

    private static Table table(Schema schema, String name) {
        Table table = RF.createTable();
        table.setName(name);
        schema.getOwnedElement().add(table);
        return table;
    }

    private static Column column(Table table, String name, SQLSimpleType type, boolean notNull) {
        Column column = RF.createColumn();
        column.setName(name);
        column.setType(type);
        column.setIsNullable(notNull ? NullableType.COLUMN_NO_NULLS : NullableType.COLUMN_NULLABLE);
        table.getFeature().add(column);
        return column;
    }

    private static SQLSimpleType integer() {
        SQLSimpleType type = RF.createSQLSimpleType();
        type.setName("INTEGER");
        type.setTypeNumber(Types.INTEGER);
        return type;
    }

    private static SQLSimpleType varchar() {
        SQLSimpleType type = RF.createSQLSimpleType();
        type.setName("CHARACTER VARYING");
        type.setTypeNumber(Types.VARCHAR);
        type.setCharacterMaximumLength(40);
        return type;
    }

    private static boolean is(TableReference table, String schema, String name) {
        return table.name().equalsIgnoreCase(name)
                && table.schema().map(SchemaReference::name).map(s -> s.equalsIgnoreCase(schema)).orElse(true);
    }

    private static List<String> names(List<ColumnReference> columns) {
        return columns.stream().map(c -> c.name().toUpperCase(Locale.ROOT)).toList();
    }

    private static ActiveDatabase activateOrSkip(DialectProfile profile) {
        try {
            return profile.activate();
        } catch (RuntimeException e) {
            Assumptions.assumeTrue(false, "Database '" + profile + "' unavailable (no Docker?): " + e.getMessage());
            throw new AssertionError("unreachable");
        }
    }
}
