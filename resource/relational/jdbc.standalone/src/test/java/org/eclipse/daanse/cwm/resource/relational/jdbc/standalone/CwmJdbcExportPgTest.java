/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.jdbc.standalone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Namespaces;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ProcedureType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalNames;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalRules;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Views;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.PrivilegeFactory;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.ProcedurePrivilege;
import org.eclipse.daanse.cwm.resource.relational.ddl.render.CwmDdlScripts.Kind;
import org.eclipse.daanse.cwm.resource.relational.ddl.render.Dialects;
import org.eclipse.daanse.cwm.resource.relational.diff.api.CatalogDiffer;
import org.eclipse.daanse.cwm.resource.relational.diff.api.DiffSettings;
import org.eclipse.daanse.cwm.resource.relational.diff.api.Tolerance;
import org.eclipse.daanse.cwm.resource.relational.jdbc.standalone.CwmJdbcExport.Exported;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.postgresql.PostgresDatabaseProvider;
import org.junit.jupiter.api.Test;

/**
 * PostgreSQL in a container: it runs DDL in transactions and keeps roles and privileges, and its
 * metadata report them. The names take a prefix of their own per test, as the roles of the server
 * are shared by all its databases.
 */
class CwmJdbcExportPgTest {

    private static final ActiveDatabase DATABASE = new PostgresDatabaseProvider().activate("cwm_export");

    /**
     * Everything, roles, the user and privileges included, goes in and comes back the same; with
     * {@code dropFirst} again and again, also when the tables went and the roles and the user stayed.
     */
    @Test
    void everythingGoesInAndComesBackTheSame_dropFirstAgainAndAgain() throws Exception {
        try (Connection c = DATABASE.dataSource().getConnection()) {
            Catalog model = ShopModels.shop("rt_", true);
            ExportSettings settings = ExportSettings.defaults().withDropFirst(true);

            Exported first = CwmJdbcExport.write(c, model, Selection.all(), settings);
            Exported again = CwmJdbcExport.write(c, model, Selection.all(), settings);

            assertThat(first.dialect()).isEqualTo("postgresql");
            assertThat(CwmJdbcExportTest.sql(first.executed())).as("nothing was there to drop")
                    .noneMatch(s -> s.startsWith("DROP") || s.startsWith("REVOKE"));
            assertThat(CwmJdbcExportTest.sql(first.skipped())).anyMatch(s -> s.startsWith("REVOKE"))
                    .anyMatch(s -> s.startsWith("DROP ROLE")).anyMatch(s -> s.startsWith("DROP TRIGGER"))
                    .anyMatch(s -> s.startsWith("ALTER TABLE")).anyMatch(s -> s.startsWith("DROP FUNCTION")
                            && s.contains("\"orders_of\"(INTEGER)"));
            assertThat(CwmJdbcExportTest.sql(first.executed()))
                    .anyMatch(s -> s.startsWith("CREATE FUNCTION \"rt_sales\".\"orders_of\""))
                    .anyMatch(s -> s.startsWith("GRANT EXECUTE ON FUNCTION \"rt_sales\".\"orders_of\""));
            assertThat(again.skipped()).as("the second time everything is there").isEmpty();
            assertThat(CwmJdbcExportTest.sql(again.executed())).anyMatch(s -> s.startsWith("REVOKE"))
                    .anyMatch(s -> s.startsWith("DROP ROLE")).anyMatch(s -> s.startsWith("CREATE ROLE"))
                    .anyMatch(s -> s.startsWith("DROP FUNCTION"));

            Catalog written = again.selected().model();
            Catalog readBack = CwmJdbcImport.load(c, again.readBack());
            assertThat(CatalogDiffer.create().diff(written, readBack,
                    DiffSettings.defaults().withTolerances(Tolerance.roundTrip(again.dialect()))).lines()).isEmpty();
            assertThat(Catalogs.schemas(readBack)).as("not public, the schema PostgreSQL keeps for itself")
                    .extracting(s -> s.getName()).containsExactlyInAnyOrder("rt_sales", "rt_stock");
            assertThat(Namespaces.ownedElements(readBack, ResponsibleParty.class))
                    .as("not the login user, nor the predefined roles").extracting(p -> p.getName())
                    .containsExactlyInAnyOrder("rt_reader", "rt_writer", "rt_clerk");
            assertThat(CwmJdbcExportTest.sql(first.executed())).as("the user, created without credentials")
                    .anyMatch(s -> s.contains("CREATE USER \"rt_clerk\""));
            assertThat(Namespaces.findOwnedByName(readBack, ResponsibleParty.class, "rt_writer").orElseThrow()
                    .getOwnedElement()).hasSize(3);

            // the schemas go, the roles stay: the REVOKEs on the tables cannot run, the rest does
            try (Statement s = c.createStatement()) {
                s.execute("DROP SCHEMA rt_sales CASCADE");
                s.execute("DROP SCHEMA rt_stock CASCADE");
            }
            Exported afterwards = CwmJdbcExport.write(c, model, Selection.all(), settings);

            assertThat(CwmJdbcExportTest.sql(afterwards.skipped())).isNotEmpty()
                    .allMatch(s -> !s.startsWith("DROP ROLE"));
            assertThat(CwmJdbcExportTest.sql(afterwards.executed())).anyMatch(s -> s.startsWith("DROP ROLE"))
                    .anyMatch(s -> s.startsWith("REVOKE") && s.contains("rt_reader") && !s.contains(" ON "));
            assertThat(CatalogDiffer.create().diff(written, CwmJdbcImport.load(c, afterwards.readBack()),
                    DiffSettings.defaults().withTolerances(Tolerance.roundTrip("postgresql"))).lines()).isEmpty();
        }
    }

    /**
     * The model gained a check without a name, a unique constraint, an index, a foreign key and a
     * trigger since it was last exported. With {@code dropFirst} it goes in again: the statements
     * that would drop what the database does not hold yet are skipped, under the names they give
     * it, the trigger's function with the trigger, and what comes back is the model.
     */
    @Test
    void whatTheModelGainedSinceTheLastExportIsNotDropped() throws Exception {
        try (Connection c = DATABASE.dataSource().getConnection()) {
            Catalog model = ShopModels.shop("gain_", true);
            ExportSettings settings = ExportSettings.defaults().withDropFirst(true);
            CwmJdbcExport.write(c, model, Selection.all(), settings);

            List<String> gained = ShopModels.gain(model, "gain_", true);
            Exported again = CwmJdbcExport.write(c, model, Selection.all(), settings);

            assertThat(again.skipped()).extracting(s -> RelationalNames.of(s.element()))
                    .containsOnly(gained.toArray(String[]::new));
            assertThat(CwmJdbcExportTest.sql(again.skipped())).filteredOn(s -> s.startsWith("DROP FUNCTION"))
                    .singleElement().asString().contains("trg_customer_name_fn");
            assertThat(CatalogDiffer.create().diff(again.selected().model(), CwmJdbcImport.load(c, again.readBack()),
                    DiffSettings.defaults().withTolerances(Tolerance.roundTrip("postgresql"))).lines()).isEmpty();
            assertThat(CwmJdbcExport.write(c, model, Selection.all(), settings).skipped()).as("now they are there")
                    .isEmpty();
        }
    }

    /**
     * A materialized view goes in as one and, with {@code dropFirst}, is dropped as one the next time.
     * The statement that drops a plain view of its name does not run on it, as it is not what that
     * statement drops.
     */
    @Test
    void aMaterializedViewIsDroppedAsOne() throws Exception {
        try (Connection c = DATABASE.dataSource().getConnection()) {
            Catalog model = ShopModels.shop("mv_", false);
            View totals = Views.create(Catalogs.findSchema(model, "mv_sales").orElseThrow(), "mv_quantities",
                    "SELECT \"customer_id\", SUM(\"qty\") AS \"qty\" FROM \"mv_sales\".\"orders\""
                            + " GROUP BY \"customer_id\"");
            TaggedValues.set(totals, RelationalRules.MATERIALIZED_TAG, "true");
            ExportSettings settings = ExportSettings.defaults().withDropFirst(true);

            Exported first = CwmJdbcExport.write(c, model, Selection.all(), settings);
            Exported again = CwmJdbcExport.write(c, model, Selection.all(), settings);

            assertThat(CwmJdbcExportTest.sql(first.executed()))
                    .anyMatch(s -> s.startsWith("CREATE MATERIALIZED VIEW") && s.contains("mv_quantities"));
            assertThat(CwmJdbcExportTest.sql(first.skipped()))
                    .anyMatch(s -> s.startsWith("DROP MATERIALIZED VIEW") && s.contains("mv_quantities"));
            assertThat(again.skipped()).isEmpty();
            assertThat(CwmJdbcExportTest.sql(again.executed()))
                    .anyMatch(s -> s.startsWith("DROP MATERIALIZED VIEW") && s.contains("mv_quantities"));
            assertThat(CatalogDiffer.create().diff(again.selected().model(), CwmJdbcImport.load(c, again.readBack()),
                    DiffSettings.defaults().withTolerances(Tolerance.roundTrip("postgresql"))).lines()).isEmpty();

            TaggedValues.set(totals, RelationalRules.MATERIALIZED_TAG, "false");
            assertThat(DatabaseContents.of(c, Dialects.require("postgresql").dialect(), settings.ddl(), model)
                    .holdsWhatIsDropped(totals)).as("the database holds it materialized, not plain").isFalse();
        }
    }

    /**
     * A failure at the end, at a grant on a routine the database does not have, rolls everything
     * back: neither the schemas nor the roles created before it remain.
     */
    @Test
    void aFailureRollsBackEverything() throws Exception {
        try (Connection c = DATABASE.dataSource().getConnection()) {
            Catalog model = ShopModels.shop("tx_", false);
            Procedure missing = RelationalFactory.eINSTANCE.createProcedure();
            missing.setName("no_such_function");
            missing.setType(ProcedureType.FUNCTION);
            Catalogs.findSchema(model, "tx_sales").orElseThrow().getOwnedElement().add(missing);
            ProcedurePrivilege execute = PrivilegeFactory.eINSTANCE.createProcedurePrivilege();
            execute.setProcedure(missing);
            Namespaces.findOwnedByName(model, ResponsibleParty.class, "tx_reader").orElseThrow().getOwnedElement()
                    .add(execute);

            assertThat(CwmJdbcExport.ddlInTransactions(c.getMetaData())).isTrue();
            assertThatThrownBy(() -> CwmJdbcExport.write(c, model, Selection.all(),
                    ExportSettings.defaults().withDropFirst(true)))
                    .isInstanceOfSatisfying(StatementFailedException.class, e -> {
                        assertThat(e.script()).isEqualTo(Kind.CREATE);
                        assertThat(e.element()).isEqualTo("ProcedurePrivilege shop.tx_reader");
                        assertThat(e.statement().sql()).startsWith("GRANT EXECUTE").contains("no_such_function");
                        assertThat(e.rolledBack()).isTrue();
                        assertThat(e.executed()).isNotEmpty();
                        assertThat(e.getMessage()).contains("rolled back, the database is as before");
                    });
            assertThat(c.getAutoCommit()).as("the auto-commit state is restored").isTrue();
            assertThat(names(c, "SELECT nspname FROM pg_namespace WHERE nspname LIKE 'tx\\_%'")).isEmpty();
            assertThat(names(c, "SELECT rolname FROM pg_roles WHERE rolname LIKE 'tx\\_%'")).isEmpty();
        }
    }

    private static List<String> names(Connection c, String query) throws SQLException {
        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }
}
