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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationPackage;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.BusinessInformationRules;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ElementRule;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ElementRules;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalNames;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalRules;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.Privilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.ProcedurePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeRules;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.eclipse.daanse.sql.jdbc.api.schema.DatabasePrincipal;
import org.eclipse.daanse.sql.jdbc.metadata.MetadataProviders;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * What a database holds of a model, asked once before anything is dropped. A statement of a drop
 * script runs only where the database holds the element the statement is written for:
 *
 * <ul>
 * <li>{@code DROP SCHEMA}: the schema;</li>
 * <li>{@code DROP TABLE}, {@code DROP VIEW}, {@code DROP MATERIALIZED VIEW}: the table, or the view
 * that is materialized as the statement drops it or plain as it drops it;</li>
 * <li>what drops a unique constraint, a check, a foreign key, an index or a trigger with its
 * procedure: that element on its table, under the name the statement gives it;</li>
 * <li>{@code DROP FUNCTION}, {@code DROP PROCEDURE}: a routine of its name;</li>
 * <li>{@code DROP ROLE}, {@code DROP USER}: the role, the user;</li>
 * <li>{@code REVOKE}: the grantee and what the privilege is on, the table, the routine or the role,
 * as {@code REVOKE} has no {@code IF EXISTS}.</li>
 * </ul>
 *
 * <p>
 * The schemas the statements are written into are read in one go, as the import reads a database
 * ({@link CwmJdbcImport#load(Connection, JdbcToCwmConfig)}): their tables and views with the keys,
 * unique constraints, checks, foreign keys, indexes and triggers, without comments, routines,
 * roles or privileges, and no other schema. The routines a {@code REVOKE} is on are asked for
 * through the JDBC metadata, the roles and users through the metadata provider of the database
 * product.
 * </p>
 *
 * <p>
 * <b>Names.</b> Names compare exactly, as the generator quotes every name. An element is looked for
 * on its table under the name its statement gives it: its own, or the one {@link RelationalNames}
 * gives an element without one, by which the import also names what the database reports without
 * a name. A primary key has no statement of its own: it is declared in {@code CREATE TABLE} and goes
 * with its table, so the name a database gives it itself (MariaDB and MySQL {@code PRIMARY}, DuckDB
 * {@code <table>_<columns>_pkey}) does not count; nor do the names DuckDB gives the unique
 * constraints, checks and foreign keys it declares in {@code CREATE TABLE}, as they go with their
 * table too. An element the database holds under another name than its statement's, as one another
 * tool created, one renamed since, or one an earlier model gave another default name, is not
 * dropped by that statement: the statement would name something the database does not have and is
 * skipped. The element remains until its table is dropped, which the same drop script does before
 * the create script runs. A foreign key that remains this way keeps referring to its table until
 * then; where the script drops that table first and without {@code CASCADE}, the database rejects
 * the {@code DROP TABLE}.
 * </p>
 *
 * <p>
 * Without schema names ({@link DdlSettings#includeSchema()} off) a table is looked for in the
 * connection's current schema. {@code PUBLIC}, the grantee every SQL database knows, is always
 * there. Where the metadata provider of the database does not report its roles and users, the
 * statements on them run as written.
 * </p>
 */
final class DatabaseContents {

    private final Connection connection;
    private final DatabaseMetaData metaData;
    private final DdlSettings settings;
    /** The dialect the statements are written in. */
    private final Dialect dialect;
    /** Whether the database keeps its schemas in the catalog slot of JDBC, as MySQL and MariaDB do. */
    private final boolean schemasAreCatalogs;
    /** The names of the roles and users; {@code null} where the database does not report them. */
    private final Set<String> parties;
    /** The schemas the statements are written into, as the database holds them, by name. */
    private final Map<Optional<String>, Schema> schemas = new HashMap<>();
    /** Per schema the names of its routines; {@code null} for a driver that lists none. */
    private final Map<Optional<String>, Set<String>> routines = new HashMap<>();
    /** Per model, the names its statements give its keys, constraints, indexes and triggers. */
    private final Map<EObject, Map<ModelElement, String>> names = new IdentityHashMap<>();

    private DatabaseContents(Connection connection, Dialect dialect, DdlSettings settings, Set<String> parties)
            throws SQLException {
        this.connection = connection;
        this.metaData = connection.getMetaData();
        this.dialect = dialect;
        this.settings = settings;
        this.schemasAreCatalogs = !metaData.supportsSchemasInTableDefinitions()
                && metaData.supportsCatalogsInTableDefinitions();
        this.parties = parties;
    }

    /**
     * What the database behind {@code connection} holds of {@code model}, whose drop script is
     * written in {@code dialect} with {@code settings}: reads the schemas the script is written into
     * and the roles and users at once, the routines when a statement needs them.
     */
    static DatabaseContents of(Connection connection, Dialect dialect, DdlSettings settings, Catalog model)
            throws SQLException {
        Optional<MetadataProvider> provider = MetadataProviders.forConnection(connection);
        Set<String> parties = null;
        if (provider.isPresent()) {
            parties = provider.get().getAllPrincipals(connection)
                    .map(all -> {
                        Set<String> names = new HashSet<>();
                        all.stream().map(DatabasePrincipal::name).filter(Objects::nonNull).forEach(names::add);
                        return names;
                    }).orElse(null);
        }
        DatabaseContents contents = new DatabaseContents(connection, dialect, settings, parties);
        contents.read(model);
        return contents;
    }

    /**
     * Reads the schemas the statements of {@code model} are written into, with what they hold, as
     * the import reads them.
     */
    private void read(Catalog model) throws SQLException {
        Set<Optional<String>> written = new HashSet<>();
        for (Schema schema : Catalogs.schemas(model)) {
            written.add(schemaOf(schema));
        }
        ElementRule elsewhere = ElementRule.of("a schema the model is not written into",
                e -> e instanceof Schema schema && !written.contains(key(schema.getName())));
        Selection read = Selection.builder().exclude(elsewhere, RelationalRules.PROCEDURES,
                BusinessInformationRules.COMMENTS, PrivilegeRules.PRIVILEGES,
                ElementRules.kind(BusinessinformationPackage.Literals.RESPONSIBLE_PARTY)).build();
        Catalog database = CwmJdbcImport.load(connection, JdbcToCwmConfig.defaults().withSelection(read));
        for (Schema schema : Catalogs.schemas(database)) {
            schemas.put(key(schema.getName()), schema);
        }
    }

    /** Whether the database holds what a statement written for {@code element} works on. */
    boolean holdsWhatIsDropped(ModelElement element) throws SQLException {
        if (element instanceof Schema schema) {
            return !named(schema) || schemas.containsKey(key(schema.getName()));
        }
        if (element instanceof NamedColumnSet table) {
            return held(table).isPresent();
        }
        if (element instanceof ResponsibleParty party) {
            return party(party);
        }
        if (element instanceof Procedure routine) {
            return routine(routine);
        }
        if (element instanceof Privilege privilege) {
            return privilege.getNamespace() instanceof ResponsibleParty grantee && party(grantee)
                    && target(privilege);
        }
        Optional<NamedColumnSet> table = tableOf(element);
        if (table.isEmpty()) {
            return true;
        }
        Optional<NamedColumnSet> held = held(table.get());
        return held.isPresent() && holds(held.get(), element);
    }

    /**
     * Whether {@code held}, the table as the database holds it, holds {@code element} of the model's
     * table under the name its statement gives it; a primary key under any name, as it has no
     * statement of its own.
     */
    private boolean holds(NamedColumnSet held, ModelElement element) {
        if (!(held instanceof Table table)) {
            return !(element instanceof UniqueConstraint || element instanceof CheckConstraint
                    || element instanceof ForeignKey || element instanceof SQLIndex || element instanceof Trigger);
        }
        String name = name(element);
        return switch (element) {
        case PrimaryKey key -> Tables.findPrimaryKey(table).isPresent();
        case UniqueConstraint unique -> Tables.uniqueConstraints(table).stream()
                .anyMatch(u -> !(u instanceof PrimaryKey) && name.equals(u.getName()));
        case CheckConstraint check -> Tables.checkConstraints(table).stream().anyMatch(c -> name.equals(c.getName()));
        case ForeignKey key -> Tables.foreignKeys(table).stream().anyMatch(f -> name.equals(f.getName()));
        case SQLIndex index -> SQLIndexes.spanning(table).stream().anyMatch(i -> name.equals(i.getName()));
        case Trigger trigger -> table.getTrigger().stream().anyMatch(t -> name.equals(t.getName()));
        default -> true;
        };
    }

    /** The name the statements give {@code element}: its own, or the one {@link RelationalNames} gives it. */
    private String name(ModelElement element) {
        String name = names.computeIfAbsent(EcoreUtil.getRootContainer(element), RelationalNames::in).get(element);
        return name != null ? name : element.getName();
    }

    private boolean target(Privilege privilege) throws SQLException {
        if (privilege instanceof TablePrivilege on) {
            return on.getTable() != null && held(on.getTable()).isPresent();
        }
        if (privilege instanceof ProcedurePrivilege on) {
            return on.getProcedure() != null && routine(on.getProcedure());
        }
        if (privilege instanceof RolePrivilege on) {
            return on.getRole() != null && party(on.getRole());
        }
        return true;
    }

    /** The table or view a key, constraint, index, trigger or comment belongs to. */
    private static Optional<NamedColumnSet> tableOf(ModelElement element) {
        if (element instanceof Description description) {
            return description.getModelElement().stream().map(DatabaseContents::tableOf).flatMap(Optional::stream)
                    .findFirst();
        }
        Optional<NamedColumnSet> table = RelationalNames.tableOf(element);
        if (table.isPresent()) {
            return table;
        }
        for (EObject o = element; o != null; o = o.eContainer()) {
            if (o instanceof NamedColumnSet ncs) {
                return Optional.of(ncs);
            }
        }
        return Optional.empty();
    }

    private boolean party(ResponsibleParty party) {
        String name = party.getName();
        return parties == null || "PUBLIC".equalsIgnoreCase(name) || parties.contains(name);
    }

    /**
     * The table or view of that name and kind the database holds where the statements name it; a view
     * materialized where the statements write it materialized, as the dialect does for a view the model
     * says is one, otherwise a plain one.
     */
    private Optional<NamedColumnSet> held(NamedColumnSet table) throws SQLException {
        Schema schema = schemas.get(schemaOf(table.getNamespace()));
        if (schema == null || table.getName() == null) {
            return Optional.empty();
        }
        Class<? extends NamedColumnSet> kind = table instanceof View ? View.class : Table.class;
        Optional<NamedColumnSet> held = Schemas.findOwnedByName(schema, kind, table.getName())
                .map(NamedColumnSet.class::cast);
        if (table instanceof View view) {
            boolean materialized = RelationalRules.MATERIALIZED.test(view)
                    && dialect.ddlGenerator().supportsMaterializedViews();
            return held.filter(h -> RelationalRules.MATERIALIZED.test(h) == materialized);
        }
        return held;
    }

    /** Whether the routine is there; {@code true} where the driver lists no routines at all. */
    private boolean routine(Procedure procedure) throws SQLException {
        Optional<String> schema = schemaOf(procedure.getNamespace());
        if (!routines.containsKey(schema)) {
            Set<String> found = new HashSet<>();
            boolean listed = false;
            try (ResultSet rs = schemasAreCatalogs ? metaData.getProcedures(schema.orElse(null), null, "%")
                    : metaData.getProcedures(null, pattern(schema), "%")) {
                listed = true;
                while (rs.next()) {
                    if (in(rs, schema)) {
                        found.add(rs.getString(3));
                    }
                }
            } catch (SQLFeatureNotSupportedException notListed) {
                // the driver lists no procedures; the functions may tell
            }
            try (ResultSet rs = schemasAreCatalogs ? metaData.getFunctions(schema.orElse(null), null, "%")
                    : metaData.getFunctions(null, pattern(schema), "%")) {
                listed = true;
                while (rs.next()) {
                    if (in(rs, schema)) {
                        found.add(rs.getString(3));
                    }
                }
            } catch (SQLFeatureNotSupportedException notListed) {
                // the driver lists no functions; the procedures may have told
            }
            routines.put(schema, listed ? found : null);
        }
        Set<String> found = routines.get(schema);
        return found == null || found.contains(procedure.getName());
    }

    /**
     * The schema the statements name {@code namespace} by: its own name with schema names, otherwise
     * the connection's current schema; empty where there is none.
     */
    private Optional<String> schemaOf(Namespace namespace) throws SQLException {
        if (settings.includeSchema() && namespace instanceof Schema schema && named(schema)) {
            return Optional.of(schema.getName());
        }
        return key(schemasAreCatalogs ? connection.getCatalog() : connection.getSchema());
    }

    /** A schema by its name; empty for a schema without one. */
    private static Optional<String> key(String schemaName) {
        return Optional.ofNullable(schemaName).filter(n -> !n.isBlank());
    }

    /**
     * Whether the row of a metadata result lies in {@code schema}: its catalog or schema column, the
     * first or the second of every such result; any row where the connection names no current schema.
     */
    private boolean in(ResultSet rs, Optional<String> schema) throws SQLException {
        return schema.isEmpty() || schema.get().equals(rs.getString(schemasAreCatalogs ? 1 : 2));
    }

    /** {@code schema} as a search pattern of exactly that name. */
    private String pattern(Optional<String> schema) throws SQLException {
        if (schema.isEmpty()) {
            return null;
        }
        String escape = metaData.getSearchStringEscape();
        if (escape == null || escape.isEmpty()) {
            return schema.get();
        }
        return schema.get().replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%");
    }

    private static boolean named(Schema schema) {
        return schema.getName() != null && !schema.getName().isBlank();
    }
}
