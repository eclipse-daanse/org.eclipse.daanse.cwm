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
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.BooleanExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Expression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ProcedureExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Namespaces;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndexColumn;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLParameter;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ActionOrientationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ProcedureType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ReferentialRuleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalRules;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.UniqueConstraints;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Views;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.Privilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.PrivilegeFactory;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.ProcedurePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeRules;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeStereotypes;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlStatement;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.PrivilegeSqlGeneratorImpl;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.DialectProfile;
import org.eclipse.daanse.cwm.resource.relational.diff.api.CatalogDiff;
import org.eclipse.daanse.cwm.resource.relational.diff.api.CatalogDiffer;
import org.eclipse.daanse.cwm.resource.relational.diff.api.DiffSettings;
import org.eclipse.daanse.cwm.resource.relational.diff.api.Tolerance;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.internal.CwmLoaderImpl;
import org.eclipse.daanse.cwm.resource.relational.load.privilege.internal.PrivilegeLoaderImpl;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.jdbc.api.DatabaseService;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.impl.DatabaseServiceImpl;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A model written as DDL into each database and read back is, compared with
 * {@link Tolerance#roundTrip(String)} of that database, the model that was written: tables with
 * typed, sized, defaulted and commented columns, a primary key, a unique constraint, a check, a
 * foreign key with a delete rule, an index, a view and, where the database runs the test's trigger
 * body, a trigger. What a database spells its own way is what the tolerances of its round trip let
 * pass, nothing else; these round trips are where {@link Tolerance#roundTrip(String)} has its sets
 * from.
 */
class CatalogRoundTripParameterizedTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;
    private static final String VIEW_COMMENT = "Whom to write to";
    private static final String VIEW_COLUMN_COMMENT = "The address";
    /** The role of PostgreSQL that is granted one of two overloads. */
    private static final String OVER_ROLE = "rt_pg_over";
    private static final DatabaseService DB_SERVICE = new DatabaseServiceImpl();

    /**
     * The databases where the trigger of the test calls a procedure of its own that holds its body,
     * and whose metadata report that body apart from the procedure's {@code CREATE} statement. Their
     * metadata report the trigger's action as the call (MySQL, MariaDB) or as the body of the
     * procedure (PostgreSQL); read back, the trigger holds the body that was written. SQL Server
     * reports a trigger and a procedure only as their whole {@code CREATE} statements, Oracle keeps
     * the body in the trigger itself.
     */
    private static final Set<DialectProfile> TRIGGER_BODY_IN_A_PROCEDURE = EnumSet.of(DialectProfile.POSTGRES,
            DialectProfile.MARIADB, DialectProfile.MYSQL);

    static Stream<DialectProfile> dialects() {
        return Stream.of(DialectProfile.values());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void whatIsReadBackIsWhatWasWritten(DialectProfile profile) throws Exception {
        ActiveDatabase db = activateOrSkip(profile);
        try (Connection c = db.dataSource().getConnection()) {
            Dialect dialect = db.dialect();
            Catalog written = model(profile, dialect);
            Schema schema = Catalogs.schemas(written).get(0);
            try {
                executeAll(c, profile.create(dialect, schema));
                if (profile == DialectProfile.POSTGRES) {
                    overloadsWithAGrantOnOne(c, written, dialect);
                }

                profile.prepareForMetadata(c);
                MetaInfo info = profile.snapshot(DB_SERVICE, c, dialect);
                List<String> rules = new ArrayList<>(List.of("schema:" + schema.getName()));
                if (profile == DialectProfile.POSTGRES) {
                    rules.add("name:" + OVER_ROLE);
                }
                Catalog readBack = new CwmLoaderImpl(List.of(new PrivilegeLoaderImpl())).load(info,
                        JdbcToCwmConfig.defaults().withCatalogName(written.getName())
                                .withSelection(Selection.parse(PrivilegeRules.VOCABULARY, rules)));

                DiffSettings settings = DiffSettings.defaults()
                        .withTolerances(Tolerance.roundTrip(profile.providerId()));
                CatalogDiff diff = CatalogDiffer.create().diff(written, readBack, settings);

                assertThat(diff.lines()).as("%s differs from what was written", profile).isEmpty();
                if (profile.commentsOnViews()) {
                    // the differ compares the comments of tables and their columns, not those of views
                    View view = Schemas.findView(Catalogs.schemas(readBack).get(0), "V_CUSTOMERS").orElseThrow();
                    assertThat(comment(view)).as("%s: the comment on the view", profile).contains(VIEW_COMMENT);
                    assertThat(ColumnSets.findColumn(view, "EMAIL").flatMap(column -> comment(column)))
                            .as("%s: the comment on a column of the view", profile).contains(VIEW_COLUMN_COMMENT);
                }
                if (TRIGGER_BODY_IN_A_PROCEDURE.contains(profile)) {
                    // the text of a body does not count in the comparison above
                    assertThat(Catalogs.schemas(readBack).get(0).getOwnedElement())
                            .filteredOn(Trigger.class::isInstance)
                            .map(e -> ((Trigger) e).getActionStatement().getBody().strip())
                            .as("%s: the body of the trigger, not the call of its procedure", profile)
                            .containsExactly(profile.triggerBody());
                }
            } finally {
                profile.cleanup(c, schema, dialect);
                if (profile == DialectProfile.POSTGRES) {
                    try (Statement s = c.createStatement()) {
                        s.execute("DROP ROLE IF EXISTS \"" + OVER_ROLE + "\"");
                    }
                }
            }
        }
    }

    /**
     * Creates the overloads {@code fn_over(v integer)} and {@code fn_over(v text)} of the model, which
     * the DDL writes no routines for, and writes its role with the grant on the second one.
     */
    private static void overloadsWithAGrantOnOne(Connection c, Catalog written, Dialect dialect) throws Exception {
        String schema = Catalogs.schemas(written).get(0).getName();
        try (Statement s = c.createStatement()) {
            s.execute("CREATE FUNCTION \"" + schema + "\".fn_over(v integer) RETURNS integer LANGUAGE sql"
                    + " AS 'SELECT 1'");
            s.execute("CREATE FUNCTION \"" + schema + "\".fn_over(v text) RETURNS integer LANGUAGE sql"
                    + " AS 'SELECT 2'");
        }
        PrivilegeSqlGeneratorImpl privileges = new PrivilegeSqlGeneratorImpl(dialect);
        List<ResponsibleParty> roles = Namespaces.ownedElements(written, ResponsibleParty.class);
        executeAll(c, DdlStatement.sql(privileges.createRoleStatements(roles)));
        executeAll(c, DdlStatement.sql(privileges.grantStatements(roles.stream()
                .flatMap(r -> Namespaces.ownedElements(r, Privilege.class).stream()).toList())));
    }

    /**
     * {@code rt.<schema>}: {@code CUSTOMERS(ID PK, EMAIL VARCHAR(100) NOT NULL UNIQUE, NAME
     * VARCHAR(50), STATUS VARCHAR(16) DEFAULT 'NEW', AMOUNT DECIMAL(10,2), CHECK ID > 0)}, commented,
     * with an index on {@code NAME}, descending where the database keeps the order, and, where the dialect has partial indexes, one on {@code STATUS}
     * over the rows that have one, {@code ORDERS(ID PK, CUSTOMER_ID → CUSTOMERS ON DELETE CASCADE)}
     * without an index on {@code CUSTOMER_ID}, for which some databases make one, the view
     * {@code V_CUSTOMERS}, where the dialect writes them the materialized view {@code MV_CUSTOMERS},
     * and, where the profile has one, a trigger on {@code ORDERS}; on PostgreSQL
     * also the overloads {@code fn_over(v integer)} and {@code fn_over(v text)} and the role
     * {@code rt_pg_over} with {@code EXECUTE} on the second one.
     */
    private static Catalog model(DialectProfile profile, Dialect dialect) {
        Catalog catalog = tables(profile, dialect);
        if (profile == DialectProfile.POSTGRES) {
            Schema schema = Catalogs.schemas(catalog).get(0);
            SQLSimpleType integer = SQLSimpleTypes.create(catalog, "integer", Types.INTEGER);
            SQLSimpleType text = SQLSimpleTypes.create(catalog, "text", Types.VARCHAR);
            overload(schema, integer, "SELECT 1");
            Procedure byText = overload(schema, text, "SELECT 2");
            Stereotype databaseRole = PrivilegeStereotypes.create(PrivilegeStereotypes.DATABASE_ROLE);
            catalog.getOwnedElement().add(databaseRole);
            ResponsibleParty role = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
            role.setName(OVER_ROLE);
            catalog.getOwnedElement().add(role);
            databaseRole.getExtendedElement().add(role);
            ProcedurePrivilege execute = PrivilegeFactory.eINSTANCE.createProcedurePrivilege();
            execute.setProcedure(byText);
            execute.setGrantable(false);
            role.getOwnedElement().add(execute);
        }
        return catalog;
    }

    /** A function {@code fn_over} in {@code schema} with the parameter {@code v} of the type {@code type}. */
    private static Procedure overload(Schema schema, SQLSimpleType type, String body) {
        Procedure function = RF.createProcedure();
        function.setName("fn_over");
        function.setType(ProcedureType.FUNCTION);
        SQLParameter v = RF.createSQLParameter();
        v.setName("v");
        v.setKind(ParameterDirectionKind.PDK_IN);
        v.setType(type);
        function.getParameter().add(v);
        ProcedureExpression expression = CoreFactory.eINSTANCE.createProcedureExpression();
        expression.setLanguage("SQL");
        expression.setBody(body);
        function.setBody(expression);
        schema.getOwnedElement().add(function);
        return function;
    }

    /** The tables, the view and the trigger of {@link #model}. */
    private static Catalog tables(DialectProfile profile, Dialect dialect) {
        Catalog catalog = Catalogs.create("rt");
        Schema schema = RF.createSchema();
        schema.setName(profile.schemaName());
        catalog.getOwnedElement().add(schema);
        SQLSimpleType integer = SQLSimpleTypes.create(catalog, "INTEGER", Types.INTEGER);
        SQLSimpleType varchar = SQLSimpleTypes.create(catalog, "VARCHAR", Types.VARCHAR);
        SQLSimpleType decimal = SQLSimpleTypes.create(catalog, "DECIMAL", Types.DECIMAL);

        Table customers = Tables.create(schema, "CUSTOMERS");
        Column id = Columns.create(customers, "ID", integer, NullableType.COLUMN_NO_NULLS);
        Column email = Columns.create(customers, "EMAIL", varchar, NullableType.COLUMN_NO_NULLS, 100, 0, 0);
        Column name = Columns.create(customers, "NAME", varchar, NullableType.COLUMN_NULLABLE, 50, 0, 0);
        Column status = Columns.create(customers, "STATUS", varchar, NullableType.COLUMN_NULLABLE, 16, 0, 0);
        Expression statusDefault = CoreFactory.eINSTANCE.createExpression();
        statusDefault.setLanguage("SQL");
        statusDefault.setBody("'NEW'");
        status.setInitialValue(statusDefault);
        Columns.create(customers, "AMOUNT", decimal, NullableType.COLUMN_NULLABLE, 0, 10, 2);
        PrimaryKey customersKey = PrimaryKeys.create(customers, "PK_CUSTOMERS", id);
        UniqueConstraints.create(customers, "UQ_CUSTOMERS_EMAIL", email);
        CheckConstraint check = RF.createCheckConstraint();
        check.setName("CK_CUSTOMERS_ID");
        BooleanExpression condition = CoreFactory.eINSTANCE.createBooleanExpression();
        condition.setLanguage("SQL");
        condition.setBody(dialect.quoteIdentifier("ID") + " > 0");
        check.setBody(condition);
        customers.getOwnedElement().add(check);
        Descriptions.describe(customers, Descriptions.JDBC_REMARKS, null, "Who orders");
        Descriptions.describe(email, Descriptions.JDBC_REMARKS, null, "Where the invoice goes");

        Table orders = Tables.create(schema, "ORDERS");
        Column orderId = Columns.create(orders, "ID", integer, NullableType.COLUMN_NO_NULLS);
        Column customerId = Columns.create(orders, "CUSTOMER_ID", integer, NullableType.COLUMN_NO_NULLS);
        PrimaryKeys.create(orders, "PK_ORDERS", orderId);
        ForeignKey fk = ForeignKeys.create(orders, "FK_ORDERS_CUSTOMERS", customersKey, customerId);
        fk.setDeleteRule(ReferentialRuleType.IMPORTED_KEY_CASCADE);
        // no index on the foreign key's column: H2, MySQL and MariaDB make one of their own for it
        SQLIndex byName = SQLIndexes.index("IX_CUSTOMERS_NAME", false, customers, name);
        // descending where the database keeps the order
        ((SQLIndexColumn) byName.getIndexedFeature().get(0))
                .setIsAscending(!dialect.ddlGenerator().supportsDescendingIndexColumns());
        schema.getOwnedElement().add(byName);
        if (dialect.ddlGenerator().supportsFilteredIndexes()) {
            SQLIndex open = SQLIndexes.index("IX_CUSTOMERS_STATUS", false, customers, status);
            open.setFilterCondition(dialect.quoteIdentifier("STATUS") + " IS NOT NULL");
            schema.getOwnedElement().add(open);
        }

        View view = Views.create(schema, "V_CUSTOMERS", "SELECT " + dialect.quoteIdentifier("ID") + ", "
                + dialect.quoteIdentifier("EMAIL") + " FROM " + dialect.quoteIdentifier(schema.getName(), "CUSTOMERS"));
        if (profile.commentsOnViews()) {
            Column viewEmail = Columns.create(view, "EMAIL", varchar, NullableType.COLUMN_NO_NULLS, 100, 0, 0);
            Descriptions.describe(view, Descriptions.JDBC_REMARKS, null, VIEW_COMMENT);
            Descriptions.describe(viewEmail, Descriptions.JDBC_REMARKS, null, VIEW_COLUMN_COMMENT);
        }

        if (dialect.ddlGenerator().supportsMaterializedViews()) {
            View materialized = Views.create(schema, "MV_CUSTOMERS", "SELECT " + dialect.quoteIdentifier("ID")
                    + ", " + dialect.quoteIdentifier("NAME") + " FROM "
                    + dialect.quoteIdentifier(schema.getName(), "CUSTOMERS"));
            TaggedValues.set(materialized, RelationalRules.MATERIALIZED_TAG, "true");
        }

        if (profile.supportsTriggers()) {
            Trigger trigger = RF.createTrigger();
            trigger.setName("TRG_ORDERS_AUDIT");
            trigger.setTable(orders);
            trigger.setConditionTiming(profile.triggerTiming());
            trigger.setEventManipulation(profile.triggerEvent());
            // SQL Server has statement triggers only
            trigger.setActionOrientation(profile == DialectProfile.MSSQL ? ActionOrientationType.STATEMENT
                    : ActionOrientationType.ROW);
            ProcedureExpression body = CoreFactory.eINSTANCE.createProcedureExpression();
            body.setLanguage("SQL");
            body.setBody(profile.triggerBody());
            trigger.setActionStatement(body);
            schema.getOwnedElement().add(trigger);
        }
        return catalog;
    }

    /** The comment the read back {@code element} carries. */
    private static Optional<String> comment(ModelElement element) {
        return Descriptions.find(element, Descriptions.JDBC_REMARKS).map(Description::getBody);
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
