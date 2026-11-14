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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ElementRule;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selections;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.UnmatchedRulesException;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlStatement;
import org.eclipse.daanse.cwm.resource.relational.ddl.render.CwmDdlRenderer;
import org.eclipse.daanse.cwm.resource.relational.ddl.render.CwmDdlScripts.Kind;
import org.eclipse.daanse.cwm.resource.relational.ddl.render.DdlScript;
import org.eclipse.daanse.cwm.resource.relational.ddl.render.Dialects;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;

/**
 * Writes a CWM relational model into a live database: cuts a copy of it to a selection, writes its
 * DDL in the dialect of the database and runs it over JDBC. The way back is {@link CwmJdbcImport};
 * the DDL is that of {@code ddl.render}, statement for statement what {@code cwm-ddl:generate}
 * writes into its script files for the same model, selection and settings.
 *
 * <pre>{@code
 * Exported done = CwmJdbcExport.write("jdbc:postgresql://localhost:5432/shop", "owner", "secret", null,
 *         getClass().getClassLoader(), model, CwmJdbcExport.selection(List.of("schema:sales", "is:role")),
 *         ExportSettings.defaults().withDropFirst(true));
 * done.selected().leftOut();   // what the selection left out, each with the rule or consequence
 * done.executed();             // the statements that ran, in their order
 * Catalog back = CwmJdbcImport.load("jdbc:postgresql://localhost:5432/shop", "owner", "secret", null,
 *         getClass().getClassLoader(), done.readBack());       // what the database holds of it
 * }</pre>
 *
 * <p><b>Dialect.</b> The statements are written in the dialect {@link Dialects#forConnection} finds
 * for the database, or in the one {@link ExportSettings#dialect()} names.</p>
 *
 * <p><b>Drop first.</b> With {@link ExportSettings#dropFirst()} the drop script of the same model runs
 * before the create script, written with {@code IF EXISTS}, so an export runs again and again. Some
 * of its statements fail where what they drop is not there, {@code IF EXISTS} or not: a
 * {@code REVOKE} and a {@code DROP ROLE} have no such clause, nor has {@code DROP CONSTRAINT} or
 * {@code DROP INDEX} in MySQL, an {@code ALTER TABLE} that drops a constraint fails without its
 * table, and H2 fails a {@code DROP … IF EXISTS} without its schema. So the database is asked first
 * what it holds of the model: the schemas the model is written into, with their tables and views
 * and the keys, constraints, foreign keys, indexes and triggers on them, read in one go as the
 * import reads them; the routines through the JDBC metadata; the roles and users through the
 * metadata provider of the database product. A statement runs only where the database holds the
 * element it is written for, a constraint, index or trigger under the name the statement gives it;
 * the others are {@linkplain Exported#skipped() skipped}, as a constraint the model gained since
 * the last export. Each statement is kept with the element it is written for, so this is decided
 * by the element, not by the text. No failure of a statement that runs is passed over. Where the
 * database does not report its roles and users, the statements on them run as written. A
 * constraint, index or trigger the database holds under another name than the model's is not
 * dropped on its own: it goes with its table, which the drop script drops after it.</p>
 *
 * <p><b>Transaction.</b> Where the database runs DDL in transactions, as its metadata say
 * ({@link DatabaseMetaData#supportsDataDefinitionAndDataManipulationTransactions()} without
 * {@link DatabaseMetaData#dataDefinitionCausesTransactionCommit()} or
 * {@link DatabaseMetaData#dataDefinitionIgnoredInTransactions()}), both scripts run in one
 * transaction, which a failure rolls back: the database is as before. Elsewhere each statement is
 * committed as it runs, and a failure leaves what ran before it. The auto-commit state of the
 * connection is restored afterwards; a transaction the caller has open on it is committed with the
 * export. A connection the caller passes stays open.</p>
 *
 * <p><b>Failure.</b> A statement the database rejects ends the export with a
 * {@link StatementFailedException}: the statement, its position in its script, the element it is
 * written for and what remains, with the database's {@link SQLException} as cause.</p>
 */
public final class CwmJdbcExport {

    /**
     * What one export did.
     *
     * @param selected the model as written, the copy cut to the selection, with what the cut left
     *                 out and retained ({@code Selections.copy})
     * @param dialect  the id of the dialect the statements are written in
     * @param executed the statements that ran, those of the drop script first, in their order
     * @param skipped  the statements of the drop script that did not run, as the database did not
     *                 hold what they work on
     * @param readBack how {@link CwmJdbcImport#load(Connection, JdbcToCwmConfig)} reads back what
     *                 was written, so the catalog read compares with {@code selected().model()}
     *                 element for element: the catalog under the name of the model, the comments
     *                 as descriptions of the type they were written from, and the rules the model
     *                 was cut by. A database holds more than any model written into it: the
     *                 schemas it keeps for itself ({@code public}, {@code information_schema},
     *                 {@code main}), its login users and predefined roles, and what other models
     *                 put there, all of which rules without an include rule, such as
     *                 {@link Selection#all()}, reach. So the selection read back also leaves out
     *                 every schema and every role or user the written model does not hold, by
     *                 name. Within the schemas and parties it holds, the database is read as the
     *                 rules select: a table there beyond the written ones is read and shows as a
     *                 difference.
     */
    public record Exported(Selected<Catalog> selected, String dialect, List<DdlStatement> executed,
            List<DdlStatement> skipped, JdbcToCwmConfig readBack) {

        public Exported {
            Objects.requireNonNull(selected, "selected");
            Objects.requireNonNull(dialect, "dialect");
            Objects.requireNonNull(readBack, "readBack");
            executed = List.copyOf(executed);
            skipped = List.copyOf(skipped);
        }
    }

    private CwmJdbcExport() {
    }

    /**
     * Writes {@code model} into the database at {@code url}, as
     * {@link #write(Connection, Catalog, Selection, ExportSettings)} does: connects as
     * {@link Connections#connect(String, String, String, String, ClassLoader)} does, writes, and
     * closes the connection.
     */
    public static Exported write(String url, String user, String password, String driverClass, ClassLoader loader,
            Catalog model, Selection selection, ExportSettings settings) throws SQLException {
        try (Connection connection = Connections.connect(url, user, password, driverClass, loader)) {
            return write(connection, model, selection, settings);
        }
    }

    /**
     * Writes {@code model} into the database behind {@code dataSource}, as
     * {@link #write(Connection, Catalog, Selection, ExportSettings)} does, on a connection of its own,
     * which is closed afterwards.
     */
    public static Exported write(DataSource dataSource, Catalog model, Selection selection, ExportSettings settings)
            throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return write(connection, model, selection, settings);
        }
    }

    /**
     * Writes what {@code selection} selects of {@code model} into the database behind
     * {@code connection}: cuts a copy of the model to the selection with the consequences of the
     * relational, business information and privilege models ({@link CwmDdlRenderer#select}), writes
     * its create script in the dialect of the database and runs it, with
     * {@link ExportSettings#dropFirst()} after the drop script. {@code model} is not changed; the
     * connection stays open.
     *
     * @throws UnmatchedRulesException  when an include rule matches nothing in {@code model}; nothing
     *                                  ran
     * @throws IllegalArgumentException when no dialect is known for the database, naming the known
     *                                  ones; or as {@link CwmDdlRenderer#scripts}; nothing ran
     * @throws IllegalStateException    as {@link CwmDdlRenderer#scripts}, among them an
     *                                  {@code UnwritableException} with every case where the
     *                                  settings are strict ({@link DdlSettings#strict()}) and the
     *                                  dialect cannot write what the model holds; nothing ran
     * @throws StatementFailedException when the database rejects a statement
     * @throws SQLException             when the metadata cannot be read or the transaction not
     *                                  committed
     */
    public static Exported write(Connection connection, Catalog model, Selection selection, ExportSettings settings)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(settings, "settings");
        String dialect = settings.dialect() != null ? settings.dialect() : Dialects.forConnection(connection);
        Selected<Catalog> cut = CwmDdlRenderer.select(model, selection);
        DdlScript script = script(dialect, settings.ddl(), cut.model());
        List<DdlStatement> create = script.create();
        // the drop script with IF EXISTS; written once more only where the settings leave it out, as
        // each writing names what the dialect cannot write
        List<DdlStatement> drop = !settings.dropFirst() ? List.of()
                : settings.ddl().ifNotExists() ? script.drop()
                        : script(dialect, settings.ddl().withIfNotExists(true), cut.model()).drop();

        List<Step> steps = new ArrayList<>();
        List<DdlStatement> skipped = new ArrayList<>();
        if (!drop.isEmpty()) {
            DatabaseContents contents = DatabaseContents.of(connection, Dialects.require(dialect).dialect(),
                    settings.ddl(), cut.model());
            for (int i = 0; i < drop.size(); i++) {
                if (contents.holdsWhatIsDropped(drop.get(i).element())) {
                    steps.add(new Step(Kind.DROP, i + 1, drop.get(i)));
                } else {
                    skipped.add(drop.get(i));
                }
            }
        }
        for (int i = 0; i < create.size(); i++) {
            steps.add(new Step(Kind.CREATE, i + 1, create.get(i)));
        }
        List<DdlStatement> executed = run(connection, steps);
        return new Exported(cut, dialect, executed, skipped,
                readBack(cut.model(), selection, settings.ddl().commentType()));
    }

    /**
     * The words a selection of a model to write is written in, as on the reading side:
     * {@link CwmJdbcImport#vocabulary()}.
     */
    public static Selection selection(List<String> rules) {
        return CwmJdbcImport.selection(rules);
    }

    /**
     * {@link Exported#readBack()} of the model {@code written}, cut by {@code selection}, with the
     * comments written from descriptions of {@code commentType}.
     */
    private static JdbcToCwmConfig readBack(Catalog written, Selection selection, String commentType) {
        Set<String> schemas = names(Selections.list(written, Selection.all(), Schema.class));
        Set<String> parties = names(Selections.list(written, Selection.all(), ResponsibleParty.class));
        ElementRule notWritten = ElementRule.of("schema or party not written",
                e -> e instanceof Schema schema && !schemas.contains(schema.getName())
                        || e instanceof ResponsibleParty party && !parties.contains(party.getName()));
        Selection read = Selection.builder().include(selection.includes().toArray(ElementRule[]::new))
                .exclude(selection.excludes().toArray(ElementRule[]::new)).exclude(notWritten).build();
        return JdbcToCwmConfig.defaults().withCatalogName(written.getName()).withSelection(read)
                .withCommentType(commentType);
    }

    /** The names of {@code elements}, {@code null} for an element without one. */
    private static Set<String> names(List<? extends ModelElement> elements) {
        Set<String> names = new HashSet<>();
        elements.forEach(e -> names.add(e.getName()));
        return names;
    }

    private static DdlScript script(String dialect, DdlSettings settings, Catalog model) {
        return new CwmDdlRenderer(List.of(Dialects.require(dialect)), settings).scripts(model).get(0);
    }

    /** One statement to run, and where it stands in its script, from 1. */
    private record Step(Kind script, int position, DdlStatement statement) {
    }

    /**
     * Runs {@code steps} in their order: in one transaction where the database runs DDL in
     * transactions, otherwise each committed on its own.
     */
    private static List<DdlStatement> run(Connection connection, List<Step> steps) throws SQLException {
        boolean transactional = ddlInTransactions(connection.getMetaData());
        boolean autoCommit = connection.getAutoCommit();
        List<DdlStatement> executed = new ArrayList<>();
        connection.setAutoCommit(!transactional);
        try (Statement statement = connection.createStatement()) {
            for (Step step : steps) {
                try {
                    statement.execute(step.statement().sql());
                } catch (SQLException e) {
                    boolean rolledBack = transactional && rollback(connection, e);
                    throw new StatementFailedException(step.script(), step.position(), step.statement(), rolledBack,
                            executed, e);
                }
                executed.add(step.statement());
            }
            if (transactional) {
                connection.commit();
            }
        } catch (StatementFailedException e) {
            restore(connection, autoCommit, e);
            throw e;
        } catch (SQLException | RuntimeException e) {
            if (transactional) {
                rollback(connection, e);
            }
            restore(connection, autoCommit, e);
            throw e;
        }
        restore(connection, autoCommit, null);
        return executed;
    }

    /**
     * Whether the database runs DDL in a transaction that a rollback undoes, as its metadata say: it
     * has transactions for DDL and DML together, and DDL neither commits the transaction nor is
     * ignored in it.
     */
    static boolean ddlInTransactions(DatabaseMetaData metaData) throws SQLException {
        return metaData.supportsTransactions() && metaData.supportsDataDefinitionAndDataManipulationTransactions()
                && !metaData.dataDefinitionCausesTransactionCommit() && !metaData.dataDefinitionIgnoredInTransactions();
    }

    /** Rolls back; {@code false}, with the reason added to {@code failure}, where that fails. */
    private static boolean rollback(Connection connection, Exception failure) {
        try {
            connection.rollback();
            return true;
        } catch (SQLException e) {
            failure.addSuppressed(e);
            return false;
        }
    }

    private static void restore(Connection connection, boolean autoCommit, Exception failure) throws SQLException {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException e) {
            if (failure == null) {
                throw e;
            }
            failure.addSuppressed(e);
        }
    }
}
