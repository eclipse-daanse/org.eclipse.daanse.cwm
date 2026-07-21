/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.load.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.DatatypesFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.QueryExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.BooleanExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Expression;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndexColumn;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ReferentialRuleType;

/**
 * Shared CWM fixture and JDBC helpers for the round-trip tests
 * (CUSTOMERS/ORDERS with PK, unique constraint, CHECK, FK, index, view and a
 * defaulted column). Dialect-specific differences are expressed via
 * {@link Options}; the tests keep only their dialect-specific parts.
 */
final class RoundTripFixture {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;
    private static final CoreFactory CF = CoreFactory.eINSTANCE;
    private static final DatatypesFactory DF = DatatypesFactory.eINSTANCE;

    private RoundTripFixture() {
    }

    /**
     * Dialect-specific fixture variations.
     *
     * @param jsonbMeta        add a PG-native {@code jsonb} META column
     * @param nonUniqueIndexOnPk add a NON-unique index on the CUSTOMERS PK
     *                         column — must survive loading (only the PK's own
     *                         unique backing index is skipped). Not allowed on
     *                         Oracle (ORA-01408: column list already indexed).
     * @param checkName        name of the CHECK constraint on CUSTOMERS
     * @param checkBody        SQL body of that CHECK constraint
     */
    record Options(boolean jsonbMeta, boolean nonUniqueIndexOnPk, String checkName, String checkBody) {

        static Options standard() {
            return new Options(false, false, "CK_CUSTOMERS_EMAIL_LEN", "LENGTH(\"EMAIL\") > 3");
        }

        static Options pg() {
            return new Options(true, true, "CK_CUSTOMERS_EMAIL_LEN", "LENGTH(\"EMAIL\") > 3");
        }

        static Options oracle() {
            // Oracle has no LENGTH-on-VARCHAR2 gotchas worth exercising; the
            // ID > 0 check keeps the body dialect-neutral.
            return new Options(false, false, "CK_CUSTOMERS_ID_POS", "\"ID\" > 0");
        }
    }

    static SQLSimpleType type(String name, int jdbc, long max, long prec, long scale) {
        SQLSimpleType t = RF.createSQLSimpleType();
        t.setName(name);
        t.setTypeNumber(jdbc);
        if (max > 0)
            t.setCharacterMaximumLength(max);
        if (prec > 0)
            t.setNumericPrecision(prec);
        if (scale > 0)
            t.setNumericScale(scale);
        return t;
    }

    static Column col(String name, SQLSimpleType type, boolean notNull) {
        Column c = RF.createColumn();
        c.setName(name);
        c.setType(type);
        c.setIsNullable(notNull ? NullableType.COLUMN_NO_NULLS : NullableType.COLUMN_NULLABLE);
        return c;
    }

    static Schema build(String schemaName, Options o) {
        Schema schema = RF.createSchema();
        schema.setName(schemaName);

        Table customers = RF.createTable();
        customers.setName("CUSTOMERS");
        Column cId = col("ID", type("INTEGER", Types.INTEGER, 0, 0, 0), true);
        Column cEmail = col("EMAIL", type("CHARACTER VARYING", Types.VARCHAR, 100, 0, 0), true);
        Column cName = col("NAME", type("CHARACTER VARYING", Types.VARCHAR, 50, 0, 0), false);
        // Defaulted column — verifies columnDefault round-trip.
        Column cStatus = col("STATUS", type("CHARACTER VARYING", Types.VARCHAR, 16, 0, 0), false);
        Expression statusDefault = CF.createExpression();
        statusDefault.setLanguage("SQL");
        statusDefault.setBody("'NEW'");
        cStatus.setInitialValue(statusDefault);
        customers.getFeature().add(cId);
        customers.getFeature().add(cEmail);
        customers.getFeature().add(cName);
        customers.getFeature().add(cStatus);
        if (o.jsonbMeta()) {
            // PG-native type — verifies typeName preservation through the round-trip.
            customers.getFeature().add(col("META", type("jsonb", Types.OTHER, 0, 0, 0), false));
        }
        schema.getOwnedElement().add(customers);

        PrimaryKey customersPk = RF.createPrimaryKey();
        customersPk.setName("PK_CUSTOMERS");
        customersPk.getFeature().add(cId);
        customers.getOwnedElement().add(customersPk);

        UniqueConstraint uc = RF.createUniqueConstraint();
        uc.setName("UC_CUSTOMERS_EMAIL");
        uc.getFeature().add(cEmail);
        customers.getOwnedElement().add(uc);

        CheckConstraint cc = RF.createCheckConstraint();
        cc.setName(o.checkName());
        BooleanExpression be = CF.createBooleanExpression();
        be.setBody(o.checkBody());
        be.setLanguage("SQL");
        cc.setBody(be);
        customers.getOwnedElement().add(cc);

        addIndex(schema, customers, "IDX_CUSTOMERS_NAME", false, cName);
        if (o.nonUniqueIndexOnPk()) {
            // A deliberately NON-unique index on the PK column — the loader
            // must keep it (it only skips the PK's own unique backing index).
            addIndex(schema, customers, "IDX_CUSTOMERS_ID_EXTRA", false, cId);
        }

        Table orders = RF.createTable();
        orders.setName("ORDERS");
        Column oId = col("ID", type("INTEGER", Types.INTEGER, 0, 0, 0), true);
        Column oCustomerId = col("CUSTOMER_ID", type("INTEGER", Types.INTEGER, 0, 0, 0), true);
        Column oTotal = col("TOTAL", type("DECIMAL", Types.DECIMAL, 0, 10, 2), false);
        orders.getFeature().add(oId);
        orders.getFeature().add(oCustomerId);
        orders.getFeature().add(oTotal);
        schema.getOwnedElement().add(orders);

        PrimaryKey ordersPk = RF.createPrimaryKey();
        ordersPk.setName("PK_ORDERS");
        ordersPk.getFeature().add(oId);
        orders.getOwnedElement().add(ordersPk);

        ForeignKey fk = RF.createForeignKey();
        fk.setName("FK_ORDERS_CUSTOMERS");
        fk.getFeature().add(oCustomerId);
        fk.setUniqueKey(customersPk);
        orders.getOwnedElement().add(fk);
        fk.setDeleteRule(ReferentialRuleType.IMPORTED_KEY_CASCADE);
        // Oracle FKs don't accept ON UPDATE — the emitter drops the clause there.
        fk.setUpdateRule(ReferentialRuleType.IMPORTED_KEY_NO_ACTION);

        View view = RF.createView();
        view.setName("CUSTOMER_ORDERS");
        QueryExpression qe = DF.createQueryExpression();
        qe.setLanguage("SQL");
        qe.setBody("SELECT C.\"NAME\", O.\"TOTAL\" FROM \"" + schemaName + "\".\"CUSTOMERS\" C " + "JOIN \""
                + schemaName + "\".\"ORDERS\" O ON O.\"CUSTOMER_ID\" = C.\"ID\"");
        view.setQueryExpression(qe);
        schema.getOwnedElement().add(view);

        return schema;
    }

    private static void addIndex(Schema schema, Table table, String name, boolean unique, Column... cols) {
        SQLIndex idx = RF.createSQLIndex();
        idx.setName(name);
        idx.setIsUnique(unique);
        idx.setSpannedClass(table);
        for (Column c : cols) {
            SQLIndexColumn ic = RF.createSQLIndexColumn();
            ic.setFeature(c);
            idx.getIndexedFeature().add(ic);
        }
        schema.getOwnedElement().add(idx);
    }

    static void executeAll(Connection connection, List<String> sql) throws SQLException {
        try (Statement s = connection.createStatement()) {
            for (String stmt : sql) {
                s.execute(stmt);
            }
        }
    }

    /** Best-effort statement execution for cleanup — each failure is ignored. */
    static void executeIgnoring(Connection connection, String... sql) {
        for (String stmt : sql) {
            try (Statement s = connection.createStatement()) {
                s.execute(stmt);
            } catch (SQLException ignored) {
                // already gone
            }
        }
    }

    static int jdbcType(Column col) {
        if (col.getType() instanceof SQLSimpleType s) {
            return (int) s.getTypeNumber();
        }
        return Types.OTHER;
    }
}
