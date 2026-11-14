/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.examples.jdbc.roundtrip;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ElementRules;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalRules;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeRules;
import org.eclipse.daanse.cwm.resource.relational.ddl.render.CwmDdlScripts;
import org.eclipse.daanse.cwm.resource.relational.ddl.render.CwmDdlScripts.Script;
import org.eclipse.daanse.cwm.resource.relational.diff.api.CatalogDiff;
import org.eclipse.daanse.cwm.resource.relational.diff.api.CatalogDiffer;
import org.eclipse.daanse.cwm.resource.relational.diff.api.DiffSettings;
import org.eclipse.daanse.cwm.resource.relational.diff.api.Tolerance;
import org.eclipse.daanse.cwm.resource.relational.jdbc.standalone.Connections;
import org.eclipse.daanse.cwm.resource.relational.jdbc.standalone.CwmJdbcExport;
import org.eclipse.daanse.cwm.resource.relational.jdbc.standalone.CwmJdbcExport.Exported;
import org.eclipse.daanse.cwm.resource.relational.jdbc.standalone.CwmJdbcImport;
import org.eclipse.daanse.cwm.resource.relational.jdbc.standalone.ExportSettings;

/**
 * A CWM model held in Java code, {@link ShopModel}, goes into a database and comes back out of it.
 * {@link #roundTrip} does it for one database and one selection, in five steps, each a call of the
 * library: cut a copy of the model to the selection, write its DDL and run it
 * ({@link CwmJdbcExport#write}); write the scripts and the written model as files to look at
 * ({@link CwmDdlScripts}, {@link CwmPackages#save}); read back what the database holds of it
 * ({@link CwmJdbcImport#load}); compare ({@link CatalogDiffer}). {@link #main} runs it three times:
 * PostgreSQL gets everything, DuckDB a part chosen by rules in Java, then the same part chosen by
 * the same rules as text. No OSGi, no Maven plugin: start {@code main} from the IDE.
 */
public final class CwmRoundTrip {

    /**
     * What DuckDB gets of the shop, chosen by rules in Java: the schema {@code sales} and the table
     * {@code stock.product}, without the log table, what is documentation only, the constraints but
     * the primary keys, the trigger and the grants. The include rules do not reach the roles and
     * the user, which the catalog owns, not a schema; DuckDB has none.
     */
    public static final Selection DUCKDB_PART = Selection.builder()
            .include(RelationalRules.inSchema("sales"), RelationalRules.table("stock.product"))
            .exclude(RelationalRules.table("*_log"), RelationalRules.DOCUMENTARY, RelationalRules.CONSTRAINTS,
                    ElementRules.kind(Trigger.class), PrivilegeRules.PRIVILEGES)
            .build();

    /** {@link #DUCKDB_PART} as rule text, one rule each, as the Maven goals take them. */
    public static final List<String> DUCKDB_RULES = List.of(
            "schema:sales           # the schema sales with all it holds",
            "table:stock.product    # and the table product of stock",
            "!table:*_log           # but no log table",
            "!is:documentary        # nothing that is documentation only",
            "!is:constraint         # no unique constraint, check or foreign key; primary keys stay",
            "!kind:Trigger          # no trigger",
            "!is:privilege          # no grant");

    /** How to start the PostgreSQL the first run writes into. */
    public static final String START_POSTGRES = "podman run --rm -d --name cwm-shop -p 5432:5432"
            + " -e POSTGRES_USER=shop -e POSTGRES_PASSWORD=shop postgres:16-alpine";

    /**
     * A database to write into.
     *
     * @param name     names it on the console
     * @param url      its JDBC URL
     * @param user     {@code null} where none is needed
     * @param password {@code null} where none is needed
     */
    public record Target(String name, String url, String user, String password) {
    }

    /**
     * What one round trip did.
     *
     * @param exported what the export wrote: the model cut to the selection, with what the cut left
     *                 out and retained, and the statements that ran
     * @param readBack what the database holds of it, read back
     * @param diff     how the two differ, under the tolerances of the database
     */
    public record Result(Exported exported, Catalog readBack, CatalogDiff diff) {
    }

    private CwmRoundTrip() {
    }

    /**
     * Three round trips. PostgreSQL is found at {@code -Dshop.postgres.url}, {@code .user} and
     * {@code .password}, else at the environment variables {@code SHOP_POSTGRES_URL},
     * {@code _USER} and {@code _PASSWORD}, else at {@code jdbc:postgresql://localhost:5432/shop} as
     * {@code shop} with password {@code shop}: {@link #START_POSTGRES} starts such a database.
     * DuckDB runs in the process, in a file below {@code target}.
     */
    public static void main(String[] args) throws SQLException, IOException {
        Catalog shop = ShopModel.build();
        Path out = Files.createDirectories(Path.of("target", "cwm-roundtrip"));
        ExportSettings settings = ExportSettings.defaults().withDropFirst(true); // so it runs again and again

        // 1. PostgreSQL gets everything: both schemas, roles, user, grants, trigger, the documentary keys
        Target postgres = new Target("postgresql", setting("url", "jdbc:postgresql://localhost:5432/shop"),
                setting("user", "shop"), setting("password", "shop"));
        try {
            roundTrip(shop, postgres, Selection.all(), settings, out.resolve("postgresql"), System.out);
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("08")) { // class 08: no connection
                System.err.println("No PostgreSQL at " + postgres.url() + ". Start one:\n  " + START_POSTGRES
                        + "\nor name another: -Dshop.postgres.url=...");
            }
            throw e;
        }

        // 2. DuckDB, a file in the process, gets a part, chosen by rules in Java
        Target duckdb = new Target("duckdb", "jdbc:duckdb:" + out.resolve("shop.duckdb"), null, null);
        roundTrip(shop, duckdb, DUCKDB_PART, settings, out.resolve("duckdb-java"), System.out);

        // 3. the same part, chosen by the same rules as text
        System.out.println();
        System.out.println("The rules of DuckDB as text:");
        DUCKDB_RULES.forEach(rule -> System.out.println("  " + rule));
        roundTrip(shop, duckdb, CwmJdbcExport.selection(DUCKDB_RULES), settings, out.resolve("duckdb-text"),
                System.out);
    }

    /**
     * One round trip of {@code model} through the database {@code target}, reported on {@code log}:
     *
     * <ol>
     * <li>cut a copy of the model to {@code selection}, and report what the cut left out and what it
     * retained, each with its reason;</li>
     * <li>write the DDL of the cut copy in the dialect of the database and run it, after the drop
     * script with {@link ExportSettings#dropFirst()}, and report how many statements ran;</li>
     * <li>write the scripts and the written model into {@code directory}: {@code <model>-<dialect>.sql},
     * {@code <model>-<dialect>-drop.sql}, {@code written.xmi};</li>
     * <li>read back what the database holds of what was written, by the same selection
     * ({@link Exported#readBack()}), into {@code read.xmi};</li>
     * <li>compare the written model with the one read back, in both directions, under the
     * tolerances of the database ({@link Tolerance#roundTrip(String)}).</li>
     * </ol>
     *
     * @throws SQLException when the database cannot be reached or rejects a statement; a rejected
     *                      statement is named
     */
    public static Result roundTrip(Catalog model, Target target, Selection selection, ExportSettings settings,
            Path directory, PrintStream log) throws SQLException, IOException {
        log.println();
        log.println("== " + target.name() + " " + target.url());
        log.println("rules: " + (selection.includes().isEmpty() && selection.excludes().isEmpty()
                ? "none, everything is selected" : selection));
        try (Connection connection = Connections.connect(target.url(), target.user(), target.password())) {
            // 1. and 2. cut a copy of the model to the selection, write its DDL and run it
            Exported exported = CwmJdbcExport.write(connection, model, selection, settings);
            Selected<Catalog> written = exported.selected();
            log.println("1. cut: " + written.leftOut().size() + " left out, " + written.retained().size()
                    + " retained");
            written.leftOut().forEach(element -> log.println("   left out " + element));
            written.retained().forEach(element -> log.println("   retained " + element));
            log.println("2. ran " + exported.executed().size() + " statements in " + exported.dialect()
                    + ", skipped " + exported.skipped().size() + " of the drop script");

            // 3. the files to look at: the scripts that ran, and the model as written
            List<Script> scripts = new CwmDdlScripts(List.of(exported.dialect()), settings.ddl(), ";")
                    .write(model, selection, directory, model.getName(), true).scripts();
            CwmPackages.save(directory.resolve("written.xmi"), List.of(written.model()));
            log.println("3. wrote " + directory + ": "
                    + String.join(", ", scripts.stream().map(s -> s.file().getFileName().toString()).toList())
                    + ", written.xmi");

            // 4. read back what the database holds of what was written
            Catalog readBack = CwmJdbcImport.load(connection, exported.readBack());
            CwmPackages.save(directory.resolve("read.xmi"), List.of(readBack));
            List<String> schemas = Catalogs.schemas(readBack).stream().map(Schema::getName).toList();
            log.println("4. read back the schemas " + schemas + " into read.xmi");

            // 5. compare, in both directions, letting pass what the database spells its own way
            CatalogDiff diff = CatalogDiffer.create().diff(written.model(), readBack,
                    DiffSettings.defaults().withTolerances(Tolerance.roundTrip(exported.dialect())));
            int differences = diff.lines().size();
            log.println("5. " + (differences == 0 ? "no differences"
                    : differences == 1 ? "1 difference:" : differences + " differences:"));
            diff.lines().forEach(line -> log.println("   " + line));
            return new Result(exported, readBack, diff);
        }
    }

    /**
     * The system property {@code shop.postgres.<name>}, else the environment variable
     * {@code SHOP_POSTGRES_<NAME>}, else {@code fallback}.
     */
    private static String setting(String name, String fallback) {
        String property = System.getProperty("shop.postgres." + name);
        if (property != null) {
            return property;
        }
        String variable = System.getenv("SHOP_POSTGRES_" + name.toUpperCase(Locale.ROOT));
        return variable != null ? variable : fallback;
    }
}
