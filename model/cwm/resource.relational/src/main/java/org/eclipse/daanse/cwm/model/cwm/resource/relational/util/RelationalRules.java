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
package org.eclipse.daanse.cwm.model.cwm.resource.relational.util;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.foundation.keysindexes.KeysindexesPackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ConsequenceRule;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ConsequenceRule.Consequence;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ElementRule;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ElementRules;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.RuleVocabulary;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selections;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalPackage;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLDataType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndexColumn;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.emf.ecore.EClass;

/**
 * The {@link ElementRule}s and {@link ConsequenceRule}s of the relational
 * kinds, for a {@link Selection} of a catalog.
 *
 * <h2>Rules</h2>
 * <table>
 * <caption>Relational rules</caption>
 * <tr><th>Text</th><th>Code</th><th>Matches</th></tr>
 * <tr><td>{@code schema:sales}, {@code schema:shop.sales}</td><td>{@link #inSchema}</td>
 * <td>the schema of that name, in a catalog of that name; what it holds follows by the owner rule</td></tr>
 * <tr><td>{@code table:*_log}, {@code table:sales.orders}</td><td>{@link #table}</td>
 * <td>the table of that name, in a schema and catalog of that name, with the indexes and triggers on it
 * wherever they are owned</td></tr>
 * <tr><td>{@code view:v_*}, {@code view:sales.v_orders}</td><td>{@link #view}</td>
 * <td>the view of that name, in a schema and catalog of that name, with the indexes on it wherever they
 * are owned</td></tr>
 * <tr><td>{@code is:table}, {@code is:view}</td><td>{@link #TABLES}, {@link #VIEWS}</td><td>tables, views;
 * a materialized view is a view</td></tr>
 * <tr><td>{@code is:system}, {@code is:temporary}</td><td>{@link #SYSTEM_TABLES}, {@link #TEMPORARY_TABLES}</td>
 * <td>tables the database keeps for itself ({@code Table.isSystem}), temporary tables
 * ({@code Table.isTemporary})</td></tr>
 * <tr><td>{@code is:materialized}</td><td>{@link #MATERIALIZED}</td>
 * <td>what carries the tagged value {@value #MATERIALIZED_TAG} = {@code true}: views whose rows the
 * database stores</td></tr>
 * <tr><td>{@code is:primaryKey}</td><td>{@link #PRIMARY_KEYS}</td><td>primary keys</td></tr>
 * <tr><td>{@code is:unique}</td><td>{@link #UNIQUES}</td><td>unique constraints that are no primary key</td></tr>
 * <tr><td>{@code is:check}, {@code is:foreignKey}</td><td>{@link #CHECKS}, {@link #FOREIGN_KEYS}</td>
 * <td>check constraints, foreign keys</td></tr>
 * <tr><td>{@code is:constraint}</td><td>{@link #CONSTRAINTS}</td>
 * <td>unique constraints, checks and foreign keys, not primary keys</td></tr>
 * <tr><td>{@code is:index}, {@code is:trigger}, {@code is:procedure}</td><td>{@link #INDEXES},
 * {@link #TRIGGERS}, {@link #PROCEDURES}</td><td>indexes, triggers, procedures and functions</td></tr>
 * <tr><td>{@code is:documentary}</td><td>{@link #DOCUMENTARY}</td>
 * <td>what carries the tagged value {@value #DOCUMENTARY_TAG} = {@code true}</td></tr>
 * </table>
 * <p>
 * The rules on kinds answer for a whole class ({@link ElementRule#matchesAllOf}),
 * so a reader of a database can skip a kind an exclude rule removes. In CWM a
 * primary key is a unique constraint; {@link #UNIQUES} and {@link #CONSTRAINTS}
 * leave it out, so {@code !is:constraint} keeps the primary keys and a reader
 * still asks for them.
 * </p>
 *
 * <h2>Consequences</h2>
 * <p>
 * {@link #CONSEQUENCES} puts these rules in front of {@link ConsequenceRule#CORE}:
 * </p>
 * <ul>
 * <li>A reference to an SQL data type keeps the type in the copy
 * ({@link Consequence#RETAIN}): a column, a parameter or a distinct type
 * cannot be written without it, and catalogs often own their types outside
 * every schema.</li>
 * <li>A foreign key that loses its referenced key or one of its columns is
 * removed.</li>
 * <li>A primary key or unique constraint that loses one of its columns is
 * removed: a key over fewer columns is another key.</li>
 * <li>An index that loses its table or one of its columns is removed. An index
 * names its columns through the index columns it contains
 * ({@code IndexedFeature.feature}, see {@link SQLIndexes#columns}): the index
 * column that loses its column goes, and with it the index.</li>
 * <li>A trigger that loses its table is removed.</li>
 * </ul>
 * <p>
 * A view is not among them. CWM ties a view to its tables only through the
 * text of its query ({@code View.queryExpression}); there is no reference to
 * follow, so a view stays when the tables it reads are left out, and its query
 * may then name a table the copy lacks. Where that matters, model the need as a
 * {@code core::Dependency} from the view to the table and put
 * {@link ConsequenceRule#dependentClients(String)} for its kind in front.
 * </p>
 *
 * @see Selections#copy
 */
public final class RelationalRules {

    /**
     * The tag that marks an element as documentation only: it tells how the
     * data hangs together, a database need not enforce it.
     */
    public static final String DOCUMENTARY_TAG = "daanse.ddl.documentary";

    /**
     * The tag that marks a view as materialized: the database stores its rows
     * and refreshes them on demand. CWM 1.1 has no class of its own for it; a
     * materialized view is a {@code View} with this tagged value {@code true},
     * and read-only.
     */
    public static final String MATERIALIZED_TAG = "materialized";

    /** {@code is:table}. */
    public static final ElementRule TABLES = kindRule(RelationalPackage.Literals.TABLE, "table");

    /** {@code is:view}: views, materialized ones included. */
    public static final ElementRule VIEWS = kindRule(RelationalPackage.Literals.VIEW, "view");

    /** {@code is:system}: tables the database keeps for itself, {@code Table.isSystem}. */
    public static final ElementRule SYSTEM_TABLES = ElementRules.kind(RelationalPackage.Literals.TABLE)
            .and(ElementRule.of("system table", e -> e instanceof Table t && t.isIsSystem()))
            .describedAs("is:system");

    /** {@code is:temporary}: temporary tables, {@code Table.isTemporary}. */
    public static final ElementRule TEMPORARY_TABLES = ElementRules.kind(RelationalPackage.Literals.TABLE)
            .and(ElementRule.of("temporary table", e -> e instanceof Table t && t.isIsTemporary()))
            .describedAs("is:temporary");

    /** {@code is:materialized}: what carries the tagged value {@value #MATERIALIZED_TAG} = {@code true}. */
    public static final ElementRule MATERIALIZED = ElementRules.tagged(MATERIALIZED_TAG, "true")
            .describedAs("is:materialized");

    /** {@code is:primaryKey}. */
    public static final ElementRule PRIMARY_KEYS = kindRule(RelationalPackage.Literals.PRIMARY_KEY, "primaryKey");

    /** {@code is:unique}: unique constraints, without the primary keys, which CWM makes a kind of them. */
    public static final ElementRule UNIQUES = ElementRules.kind(RelationalPackage.Literals.UNIQUE_CONSTRAINT)
            .and(ElementRules.kind(RelationalPackage.Literals.PRIMARY_KEY).negate())
            .describedAs("is:unique");

    /** {@code is:check}. */
    public static final ElementRule CHECKS = kindRule(RelationalPackage.Literals.CHECK_CONSTRAINT, "check");

    /** {@code is:foreignKey}. */
    public static final ElementRule FOREIGN_KEYS = kindRule(RelationalPackage.Literals.FOREIGN_KEY, "foreignKey");

    /** {@code is:constraint}: unique constraints, checks and foreign keys; not primary keys. */
    public static final ElementRule CONSTRAINTS = ElementRules.any(UNIQUES, CHECKS, FOREIGN_KEYS)
            .describedAs("is:constraint");

    /** {@code is:index}. */
    public static final ElementRule INDEXES = kindRule(RelationalPackage.Literals.SQL_INDEX, "index");

    /** {@code is:trigger}. */
    public static final ElementRule TRIGGERS = kindRule(RelationalPackage.Literals.TRIGGER, "trigger");

    /** {@code is:procedure}: procedures and functions. */
    public static final ElementRule PROCEDURES = kindRule(RelationalPackage.Literals.PROCEDURE, "procedure");

    /** {@code is:documentary}: what carries the tagged value {@value #DOCUMENTARY_TAG} = {@code true}. */
    public static final ElementRule DOCUMENTARY = ElementRules.tagged(DOCUMENTARY_TAG, "true")
            .describedAs("is:documentary");

    /** The core words and {@code schema:}, {@code table:}, {@code view:} and the {@code is:} names of this class. */
    public static final RuleVocabulary VOCABULARY = RuleVocabulary.core()
            .withSelector("schema", RelationalRules::inSchema)
            .withSelector("table", RelationalRules::table)
            .withSelector("view", RelationalRules::view)
            .withRule("table", TABLES)
            .withRule("view", VIEWS)
            .withRule("system", SYSTEM_TABLES)
            .withRule("temporary", TEMPORARY_TABLES)
            .withRule("materialized", MATERIALIZED)
            .withRule("primaryKey", PRIMARY_KEYS)
            .withRule("unique", UNIQUES)
            .withRule("check", CHECKS)
            .withRule("foreignKey", FOREIGN_KEYS)
            .withRule("constraint", CONSTRAINTS)
            .withRule("index", INDEXES)
            .withRule("trigger", TRIGGERS)
            .withRule("procedure", PROCEDURES)
            .withRule("documentary", DOCUMENTARY);

    private static final ConsequenceRule SQL_TYPES = ConsequenceRule.of("SQL data type",
            loss -> loss.target() instanceof SQLDataType ? Optional.of(Consequence.RETAIN) : Optional.empty());

    private static final ConsequenceRule FOREIGN_KEY_PARTS = ConsequenceRule.of(
            "foreign key without its key or a column",
            loss -> removeIf(loss.referrer() instanceof ForeignKey
                    && (loss.reference() == KeysindexesPackage.Literals.KEY_RELATIONSHIP__UNIQUE_KEY
                            || loss.reference() == KeysindexesPackage.Literals.KEY_RELATIONSHIP__FEATURE)));

    private static final ConsequenceRule KEY_COLUMNS = ConsequenceRule.of("key without a column",
            loss -> removeIf(loss.referrer() instanceof UniqueConstraint
                    && loss.reference() == KeysindexesPackage.Literals.UNIQUE_KEY__FEATURE));

    private static final ConsequenceRule INDEX_PARTS = ConsequenceRule.of("index without its table or a column",
            loss -> removeIf(loss.referrer() instanceof SQLIndex
                    && (loss.reference() == KeysindexesPackage.Literals.INDEX__SPANNED_CLASS
                            || loss.reference() == KeysindexesPackage.Literals.INDEX__INDEXED_FEATURE)
                    || loss.referrer() instanceof SQLIndexColumn
                            && loss.reference() == KeysindexesPackage.Literals.INDEXED_FEATURE__FEATURE));

    private static final ConsequenceRule TRIGGER_TABLE = ConsequenceRule.of("trigger without its table",
            loss -> removeIf(loss.referrer() instanceof Trigger
                    && loss.reference() == RelationalPackage.Literals.TRIGGER__TABLE));

    /** The consequences listed above, then {@link ConsequenceRule#CORE}. */
    public static final List<ConsequenceRule> CONSEQUENCES = ConsequenceRule.combine(
            List.of(SQL_TYPES, FOREIGN_KEY_PARTS, KEY_COLUMNS, INDEX_PARTS, TRIGGER_TABLE), ConsequenceRule.CORE);

    private RelationalRules() {
    }

    /**
     * Schemas named {@code glob}; {@code catalog.schema} also asks the name of
     * the catalog that holds the schema. Each part is a name glob
     * ({@link ElementRules#named}). Reads {@code schema:<glob>}.
     */
    public static ElementRule inSchema(String glob) {
        Objects.requireNonNull(glob, "glob");
        List<String> parts = parts(glob, 2, "schema", "[catalog.]schema");
        ElementRule schema = ElementRules.kind(RelationalPackage.Literals.SCHEMA)
                .and(ElementRules.named(parts.get(parts.size() - 1)));
        if (parts.size() == 2) {
            schema = schema.and(ElementRules.within(catalogNamed(parts.get(0))));
        }
        return schema.describedAs("schema:" + glob);
    }

    /**
     * Tables named {@code glob}, with the indexes and triggers on them, wherever
     * those are owned; {@code schema.table} and {@code catalog.schema.table}
     * also ask the names of the schema and the catalog that hold the table.
     * Each part is a name glob ({@link ElementRules#named}). Reads
     * {@code table:<glob>}.
     */
    public static ElementRule table(String glob) {
        Objects.requireNonNull(glob, "glob");
        ElementRule tables = namedColumnSet(RelationalPackage.Literals.TABLE, glob, "table");
        ElementRule onTable = ElementRule.of("on a table",
                e -> e instanceof SQLIndex index && index.getSpannedClass() != null
                        && tables.test(index.getSpannedClass())
                        || e instanceof Trigger trigger && trigger.getTable() != null
                                && tables.test(trigger.getTable()));
        return ElementRules.any(tables, onTable).describedAs("table:" + glob);
    }

    /**
     * Views named {@code glob}, materialized ones included, with the indexes on
     * them, wherever those are owned; {@code schema.view} and
     * {@code catalog.schema.view} also ask the names of the schema and the
     * catalog that hold the view. Each part is a name glob
     * ({@link ElementRules#named}). Reads {@code view:<glob>}. A trigger in CWM
     * is on a table only, so there is none on a view to bring along.
     */
    public static ElementRule view(String glob) {
        Objects.requireNonNull(glob, "glob");
        ElementRule views = namedColumnSet(RelationalPackage.Literals.VIEW, glob, "view");
        ElementRule onView = ElementRule.of("on a view",
                e -> e instanceof SQLIndex index && index.getSpannedClass() != null
                        && views.test(index.getSpannedClass()));
        return ElementRules.any(views, onView).describedAs("view:" + glob);
    }

    /** The column sets of {@code kind} named by the last part of {@code glob}, in the schema and catalog named before. */
    private static ElementRule namedColumnSet(EClass kind, String glob, String selector) {
        List<String> parts = parts(glob, 3, selector, "[[catalog.]schema.]" + selector);
        ElementRule named = ElementRules.kind(kind).and(ElementRules.named(parts.get(parts.size() - 1)));
        if (parts.size() >= 2) {
            named = named.and(ElementRules.within(ElementRules.kind(RelationalPackage.Literals.SCHEMA)
                    .and(ElementRules.named(parts.get(parts.size() - 2)))));
        }
        if (parts.size() == 3) {
            named = named.and(ElementRules.within(catalogNamed(parts.get(0))));
        }
        return named;
    }

    private static ElementRule kindRule(EClass eClass, String name) {
        return ElementRules.kind(eClass).describedAs("is:" + name);
    }

    private static ElementRule catalogNamed(String glob) {
        return ElementRules.kind(RelationalPackage.Literals.CATALOG).and(ElementRules.named(glob));
    }

    private static List<String> parts(String glob, int most, String selector, String form) {
        List<String> parts = List.of(glob.split("\\.", -1));
        if (parts.size() > most || parts.stream().anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException(
                    "Rule '" + selector + ":" + glob + "' is no " + form + "; each part a name glob");
        }
        return parts;
    }

    private static Optional<Consequence> removeIf(boolean condition) {
        return condition ? Optional.of(Consequence.REMOVE) : Optional.empty();
    }
}
