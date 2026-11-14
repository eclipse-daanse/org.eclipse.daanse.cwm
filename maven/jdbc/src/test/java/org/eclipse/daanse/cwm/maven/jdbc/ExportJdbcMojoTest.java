/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.maven.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.daanse.cwm.maven.jdbc.ImportJdbcMojoTest.set;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.BooleanExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.DeferrabilityType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The goal against in-memory H2 databases, the way a build runs it: a model built in code is saved
 * as XMI, the Mojo is instantiated, its parameters set as a POM or the command line sets them, and
 * executed; then the database is asked what it holds and the log what the goal reported.
 */
class ExportJdbcMojoTest {

    @TempDir
    Path dir;

    /**
     * The orders alone: the customers are not included, so the foreign key to them goes with them,
     * and the index is excluded; the SQL type the columns need stays. The log says so, each with its
     * reason.
     */
    @Test
    void writesWhatTheRulesChoose_andReportsWhatTheyLeftOutAndRetained() throws Exception {
        String url = "jdbc:h2:mem:export_part;DB_CLOSE_DELAY=-1";
        ExportJdbcMojo mojo = mojo(url, shop("shop"));
        set(mojo, "selection", List.of("table:SHOP.ORDERS", " !is:index "));
        Log log = log(mojo);

        mojo.execute();

        assertThat(tables(url, "SHOP")).containsOnlyKeys("ORDERS");
        assertThat(tables(url, "OTHER")).isEmpty();
        assertThat(log.info).contains("cwm-jdbc: the selection leaves out 6 and retains 1 elements",
                "cwm-jdbc:   left out Table shop.SHOP.CUSTOMER (not included)",
                "cwm-jdbc:   left out SQLIndex shop.SHOP.IX_ORDERS_CUSTOMER (is:index)",
                "cwm-jdbc:   left out ForeignKey shop.SHOP.ORDERS.FK_ORDERS_CUSTOMER (foreign key without its key or"
                        + " a column (uniqueKey: shop.SHOP.CUSTOMER.PK_CUSTOMER))",
                "cwm-jdbc:   left out Schema shop.OTHER (not included)",
                "cwm-jdbc:   retained SQLSimpleType shop.INTEGER (needed by shop.SHOP.ORDERS.ID.type)")
                .anyMatch(l -> l.startsWith("cwm-jdbc: wrote catalog shop of ") && l.endsWith(" into " + url
                        + " in the dialect h2: 3 statements ran"));
    }

    @Test
    void dropFirstWritesAgainAndAgain_andSaysWhatWasSkipped() throws Exception {
        String url = "jdbc:h2:mem:export_again;DB_CLOSE_DELAY=-1";
        ExportJdbcMojo mojo = mojo(url, shop("shop"));
        set(mojo, "dropFirst", true);
        Log first = log(mojo);
        mojo.execute();
        Log again = log(mojo);
        mojo.execute();

        assertThat(first.info).anyMatch(l -> l.matches("cwm-jdbc: wrote catalog shop .* \\d+ statements ran, "
                + "[1-9]\\d* of the drop script skipped"));
        assertThat(first.debug).anyMatch(l -> l.startsWith("cwm-jdbc: skipped, as the database does not hold it: "
                + "DROP TABLE"));
        assertThat(again.info).anyMatch(l -> l.endsWith(" statements ran, 0 of the drop script skipped"));
        assertThat(tables(url, "SHOP")).containsOnlyKeys("CUSTOMER", "ORDERS", "ORDERS_OLD");
        assertThat(tables(url, "SHOP").get("CUSTOMER")).as("the comment, a description of the comment type")
                .isEqualTo("Who orders");
    }

    @Test
    void aRejectedStatementFailsTheBuild_namingTheStatementItsPositionAndElement() throws Exception {
        String url = "jdbc:h2:mem:export_rejected;DB_CLOSE_DELAY=-1";
        Catalog shop = shop("shop");
        CheckConstraint check = Tables.checkConstraints(table(shop, "SHOP", "ORDERS")).get(0);
        check.getBody().setBody("\"QTY\" >>> 0");
        ExportJdbcMojo mojo = mojo(url, shop);

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageMatching("(?s)cwm-jdbc: writing .*shop\\.xmi into " + url.replace(".", "\\.")
                        + ": statement \\d+ of the CREATE script, for CheckConstraint shop\\.SHOP\\.ORDERS"
                        + "\\.CK_ORDERS_QTY, failed: ALTER TABLE .*>>>.*not rolled back, the \\d+ statements before"
                        + " it remain\\)");
    }

    @Test
    void theSettingsOfTheDdl() throws Exception {
        String url = "jdbc:h2:mem:export_settings;DB_CLOSE_DELAY=-1";
        ExportJdbcMojo mojo = mojo(url, shop("shop"));
        set(mojo, "selection", List.of("schema:SHOP"));
        set(mojo, "includeSchema", false);
        set(mojo, "createSchema", false);
        set(mojo, "commentType", "other");
        set(mojo, "dialect", "H2");

        mojo.execute();

        assertThat(tables(url, "PUBLIC")).as("into the connection's schema, without the comment")
                .containsEntry("CUSTOMER", null).containsKeys("ORDERS", "ORDERS_OLD");
        assertThat(tables(url, "SHOP")).isEmpty();

        set(mojo, "dialect", "db2");
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class).hasMessageContaining("db2")
                .hasMessageContaining("postgresql, mysql");
    }

    /**
     * H2 checks every foreign key at once: a deferrable key is written not deferrable, and the build
     * log says so; strict, the goal fails before anything runs, naming the case.
     */
    @Test
    void strictTheGoalFailsBeforeAnythingRunsWhereTheDialectCannotWriteWhatTheModelHolds() throws Exception {
        String url = "jdbc:h2:mem:export_strict;DB_CLOSE_DELAY=-1";
        Catalog shop = shop("shop");
        shop.eAllContents().forEachRemaining(e -> {
            if (e instanceof ForeignKey fk) {
                fk.setDeferrability(DeferrabilityType.INITIALLY_IMMEDIATE);
            }
        });
        ExportJdbcMojo mojo = mojo(url, shop);
        set(mojo, "selection", List.of("schema:SHOP"));
        set(mojo, "strict", true);

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("foreign key shop.SHOP.ORDERS.FK_ORDERS_CUSTOMER: ")
                .hasMessageContaining("cannot write DEFERRABLE INITIALLY IMMEDIATE");
        assertThat(tables(url, "SHOP")).as("nothing ran").isEmpty();

        set(mojo, "strict", false);
        mojo.execute();

        assertThat(tables(url, "SHOP")).containsKeys("CUSTOMER", "ORDERS");
    }

    @Test
    void aRuleThatMatchesNothingOrIsNoRuleFailsTheGoal() throws Exception {
        ExportJdbcMojo mojo = mojo("jdbc:h2:mem:export_rules;DB_CLOSE_DELAY=-1", shop("shop"));
        set(mojo, "selection", List.of("schema:SHOP", "schema:shop"));

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("matches nothing").hasMessageContaining("schema:shop");
        assertThat(tables("jdbc:h2:mem:export_rules;DB_CLOSE_DELAY=-1", "SHOP")).as("nothing ran").isEmpty();

        set(mojo, "selection", List.of("schemas:SHOP"));
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Unknown selector 'schemas'");
    }

    @Test
    void theCatalogOfTheFile_byNameWhereItHoldsSeveral() throws Exception {
        String url = "jdbc:h2:mem:export_catalogs;DB_CLOSE_DELAY=-1";
        Path file = dir.resolve("two.xmi");
        CwmPackages.save(file, List.of(shop("shop"), other("archive")));
        ExportJdbcMojo mojo = mojo(url, shop("unused"));
        set(mojo, "modelFile", file.toFile());

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("holds the catalogs shop, archive; name one in catalogName");

        set(mojo, "catalogName", "archive");
        mojo.execute();
        assertThat(tables(url, "ARCHIVE")).containsOnlyKeys("T");

        set(mojo, "catalogName", "missing");
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("holds no catalog missing, only shop, archive");

        set(mojo, "modelFile", dir.resolve("absent.xmi").toFile());
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("modelFile does not exist");
    }

    @Test
    void credentialsFromASettingsServer_anUnreachableDatabaseFails_skipDoesNothing() throws Exception {
        Server server = new Server();
        server.setId("dev-db");
        server.setUsername("sa");
        server.setPassword("");
        Settings settings = new Settings();
        settings.addServer(server);
        String url = "jdbc:h2:mem:export_login;DB_CLOSE_DELAY=-1";
        ExportJdbcMojo mojo = mojo(url, shop("shop"));
        set(mojo, "user", null);
        set(mojo, "password", null);
        set(mojo, "settings", settings);
        set(mojo, "serverId", "dev-db");
        set(mojo, "selection", List.of("schema:OTHER"));

        mojo.execute();
        assertThat(tables(url, "OTHER")).containsOnlyKeys("T");

        set(mojo, "url", "jdbc:h2:file:" + dir.resolve("absent/db") + ";IFEXISTS=TRUE");
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoExecutionException.class).hasMessageContaining("absent");

        set(mojo, "skip", true);
        Log log = log(mojo);
        mojo.execute();
        assertThat(log.info).containsExactly("cwm-jdbc: skipped");
    }

    // --- helpers -----------------------------------------------------------------------------------

    /** The goal for {@code model}, saved as {@code shop.xmi}, into the database at {@code url}. */
    private ExportJdbcMojo mojo(String url, Catalog model) throws ReflectiveOperationException {
        Path file = dir.resolve("shop.xmi");
        CwmPackages.save(file, List.of(model));
        ExportJdbcMojo mojo = new ExportJdbcMojo();
        set(mojo, "url", url);
        set(mojo, "user", "sa");
        set(mojo, "password", "");
        set(mojo, "modelFile", file.toFile());
        set(mojo, "includeSchema", true);
        set(mojo, "createSchema", true);
        set(mojo, "ifNotExists", true);
        set(mojo, "commentType", Descriptions.JDBC_REMARKS);
        return mojo;
    }

    /**
     * The catalog {@code name}: schema {@code SHOP} with {@code CUSTOMER} (commented) and
     * {@code ORDERS}, which has a foreign key to the customer, an index over it and a check, and
     * {@code ORDERS_OLD}; schema {@code OTHER} with {@code T}.
     */
    private static Catalog shop(String name) {
        Catalog shop = Catalogs.create(name);
        SQLSimpleType integer = SQLSimpleTypes.create(shop, "INTEGER", Types.INTEGER);
        SQLSimpleType varchar = SQLSimpleTypes.create(shop, "VARCHAR", Types.VARCHAR);
        Schema schema = Schemas.create(shop, "SHOP");
        Table customer = Tables.create(schema, "CUSTOMER");
        Column customerId = Columns.create(customer, "ID", integer, NullableType.COLUMN_NO_NULLS);
        Columns.create(customer, "NAME", varchar, NullableType.COLUMN_NO_NULLS, 100, 0, 0);
        PrimaryKey customerKey = PrimaryKeys.create(customer, "PK_CUSTOMER", customerId);
        Descriptions.describe(customer, Descriptions.JDBC_REMARKS, null, "Who orders");
        Table orders = Tables.create(schema, "ORDERS");
        Column orderId = Columns.create(orders, "ID", integer, NullableType.COLUMN_NO_NULLS);
        Column orderCustomer = Columns.create(orders, "CUSTOMER_ID", integer, NullableType.COLUMN_NO_NULLS);
        Columns.create(orders, "QTY", integer, NullableType.COLUMN_NO_NULLS);
        PrimaryKeys.create(orders, "PK_ORDERS", orderId);
        ForeignKeys.create(orders, "FK_ORDERS_CUSTOMER", customerKey, orderCustomer);
        schema.getOwnedElement().add(SQLIndexes.index("IX_ORDERS_CUSTOMER", false, orders, orderCustomer));
        CheckConstraint check = RelationalFactory.eINSTANCE.createCheckConstraint();
        check.setName("CK_ORDERS_QTY");
        BooleanExpression condition = CoreFactory.eINSTANCE.createBooleanExpression();
        condition.setLanguage("SQL");
        condition.setBody("\"QTY\" > 0");
        check.setBody(condition);
        orders.getOwnedElement().add(check);
        Columns.create(Tables.create(schema, "ORDERS_OLD"), "ID", integer, NullableType.COLUMN_NULLABLE);
        Columns.create(Tables.create(Schemas.create(shop, "OTHER"), "T"), "ID", integer,
                NullableType.COLUMN_NULLABLE);
        return shop;
    }

    /** The catalog {@code name}: schema {@code ARCHIVE} with {@code T}. */
    private static Catalog other(String name) {
        Catalog other = Catalogs.create(name);
        SQLSimpleType integer = SQLSimpleTypes.create(other, "INTEGER", Types.INTEGER);
        Columns.create(Tables.create(Schemas.create(other, "ARCHIVE"), "T"), "ID", integer,
                NullableType.COLUMN_NULLABLE);
        return other;
    }

    private static Table table(Catalog catalog, String schema, String name) {
        return Schemas.findTable(Catalogs.findSchema(catalog, schema).orElseThrow(), name).orElseThrow();
    }

    /** The tables of {@code schema} with their comments, in the order the database lists them. */
    private static Map<String, String> tables(String url, String schema) throws SQLException {
        Map<String, String> tables = new LinkedHashMap<>();
        try (Connection c = DriverManager.getConnection(url, "sa", "");
                ResultSet rs = c.getMetaData().getTables(null, schema, "%", new String[] { "TABLE" })) {
            while (rs.next()) {
                String remarks = rs.getString("REMARKS");
                tables.put(rs.getString("TABLE_NAME"), remarks == null || remarks.isEmpty() ? null : remarks);
            }
        }
        return tables;
    }

    private static Log log(ExportJdbcMojo mojo) {
        Log log = new Log();
        mojo.setLog(log);
        return log;
    }

    /** The messages of the goal, by level. */
    private static final class Log extends SystemStreamLog {

        final List<String> info = new ArrayList<>();
        final List<String> debug = new ArrayList<>();

        @Override
        public void info(CharSequence content) {
            info.add(content.toString());
        }

        @Override
        public void debug(CharSequence content) {
            debug.add(content.toString());
        }

        @Override
        public boolean isDebugEnabled() {
            return true;
        }
    }
}
