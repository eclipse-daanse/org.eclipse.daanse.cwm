/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.ddl.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ConsequenceRule;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selections;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.UnmatchedRulesException;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeRules;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlGenerator;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlStatement;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.PrivilegeSqlGenerator;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.UnwritableException;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Unwritables;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.PrivilegeSqlGeneratorImpl;
import org.eclipse.daanse.cwm.resource.relational.ddl.render.Dialects.NamedDialect;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * The DDL of a CWM model per SQL dialect, backed by the CWM relational DDL generator
 * ({@code org.eclipse.daanse.cwm.resource.relational.ddl}) and the Daanse JDBC SQL dialects.
 *
 * <p>A model is written as it is: its relational schemas in one script, then the roles of its
 * parties and their grants ({@link #scripts}). To write a part of it, {@link #select} cuts a copy
 * to a {@link Selection}, with the consequences of the relational, business information and
 * privilege models, and the scripts of that copy are written:</p>
 *
 * <pre>{@code
 * Selected<Catalog> cut = CwmDdlRenderer.select(catalog, Selection.parse(PrivilegeRules.VOCABULARY,
 *         "schema:sales", "!is:privilege"));
 * List<DdlScript> scripts = new CwmDdlRenderer(Dialects.of(List.of("postgresql"))).scripts(cut.model());
 * }</pre>
 *
 * <p>For a page about one element, {@link #render} gives the statements written for it and for
 * what belongs to it, {@link #renderElementOnly} those for the element alone. Each statement is
 * taken by the element the generator wrote it for, not by its text.</p>
 */
public final class CwmDdlRenderer {

    private record Gen(String name, DdlGenerator structure, PrivilegeSqlGenerator privileges) {
    }

    private final List<Gen> generators = new ArrayList<>();
    private final boolean strict;

    public CwmDdlRenderer(List<NamedDialect> dialects) {
        this(dialects, DdlSettings.defaults());
    }

    /** Renders with {@code settings}: schema-qualified names, {@code IF [NOT] EXISTS}, {@code CASCADE}. */
    public CwmDdlRenderer(List<NamedDialect> dialects, DdlSettings settings) {
        this.strict = settings.strict();
        DdlGeneratorFactoryImpl factory = new DdlGeneratorFactoryImpl();
        for (NamedDialect nd : dialects) {
            generators.add(new Gen(nd.name(), factory.create(nd.dialect(), settings),
                    new PrivilegeSqlGeneratorImpl(nd.dialect(), settings)));
        }
    }

    /**
     * The consequences a cut for DDL follows: those of the privilege model, then those of the
     * relational and business information models ({@link PrivilegeRules#CONSEQUENCES}).
     */
    public static List<ConsequenceRule> consequences() {
        return PrivilegeRules.CONSEQUENCES;
    }

    /**
     * A copy of {@code model} cut to {@code selection} with the {@link #consequences()}, and what
     * it leaves out and retains; {@code model} is not touched.
     *
     * @throws UnmatchedRulesException when an include rule of {@code selection} matches nothing in
     *                                 {@code model}, naming the rule
     */
    public static <T extends ModelElement> Selected<T> select(T model, Selection selection) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(selection, "selection");
        selection.requireIncludesMatch(model);
        return Selections.copy(model, selection, consequences());
    }

    /**
     * The scripts of everything {@code model} holds, one per dialect, also when one has no
     * statement. {@code CREATE}: the relational schemas in the model (see {@link DdlGenerator} for
     * their order), then {@code CREATE ROLE} for the parties marked {@code databaseRole}, then
     * {@code CREATE USER} for those marked {@code databaseUser}, without credentials, where the
     * dialect has users, then {@code GRANT} for the privileges and memberships the parties own.
     * {@code DROP}: the exact reverse, so {@code REVOKE} comes before {@code DROP USER} and
     * {@code DROP ROLE}, and all before the tables go.
     *
     * @throws IllegalArgumentException as {@link DdlGenerator#create}
     * @throws IllegalStateException    as {@link DdlGenerator#create}
     * @throws UnwritableException      where the settings are strict and the model holds what a
     *                                  dialect cannot write: one for all scripts, with every case
     *                                  of every dialect
     */
    public List<DdlScript> scripts(ModelElement model) {
        Objects.requireNonNull(model, "model");
        List<Schema> schemas = Selections.list(model, Selection.all(), Schema.class);
        Grants grants = Grants.in(model);
        List<DdlScript> out = new ArrayList<>();
        // each generation reports its own cases; strict, they fail together
        Unwritables found = new Unwritables(strict, false);
        for (Gen g : generators) {
            List<DdlStatement> create = new ArrayList<>(found.collect(() -> g.structure().create(schemas)));
            create.addAll(g.privileges().createRoleStatements(grants.roles()));
            create.addAll(found.collect(() -> g.privileges().createUserStatements(grants.users())));
            create.addAll(g.privileges().grantStatements(grants.privileges()));
            List<DdlStatement> drop = new ArrayList<>(g.privileges().revokeStatements(grants.privileges()));
            drop.addAll(found.collect(() -> g.privileges().dropUserStatements(grants.users())));
            drop.addAll(g.privileges().dropRoleStatements(grants.roles()));
            drop.addAll(found.collect(() -> g.structure().drop(schemas)));
            out.add(new DdlScript(g.name(), create, drop));
        }
        found.finish();
        return out;
    }

    /**
     * The statements for the element alone — the table without its indexes, the schema without
     * its tables — so a page can show what the element itself is before showing everything that
     * hangs off it. Nothing for a catalog, which has no statement of its own.
     */
    public List<DialectDdl> renderElementOnly(EObject object) {
        if (!(object instanceof ModelElement element) || object instanceof Catalog) {
            return List.of();
        }
        return perDialect(element, statement -> statement.element() == element);
    }

    /**
     * The statements for the element and for what belongs to it: a catalog's whole
     * {@link #scripts CREATE script}, roles and grants included; a schema's script; a table with its
     * keys and constraints, the indexes on it, its triggers and the comments on it and its columns;
     * a view, index, key, constraint or trigger with its own statements. Nothing for an element
     * without DDL of its own, such as a column type.
     */
    public List<DialectDdl> render(EObject object) {
        if (!(object instanceof ModelElement element)) {
            return List.of();
        }
        if (object instanceof Catalog catalog) {
            List<DialectDdl> out = new ArrayList<>();
            for (DdlScript script : scripts(catalog)) {
                if (!script.create().isEmpty()) {
                    out.add(new DialectDdl(script.dialect(), script.createSql()));
                }
            }
            return out;
        }
        return perDialect(element, statement -> belongsTo(statement.element(), element));
    }

    /** The statements of the script of {@code element}'s schema that {@code wanted} takes, per dialect. */
    private List<DialectDdl> perDialect(ModelElement element, Predicate<DdlStatement> wanted) {
        Schema schema = schemaOf(element);
        if (schema == null) {
            return List.of();
        }
        List<DialectDdl> out = new ArrayList<>();
        for (Gen g : generators) {
            List<String> statements = g.structure().create(List.of(schema)).stream().filter(wanted)
                    .map(DdlStatement::sql).toList();
            if (!statements.isEmpty()) {
                out.add(new DialectDdl(g.name(), statements));
            }
        }
        return out;
    }

    /**
     * Whether a statement written for {@code written} belongs to {@code element}: it is the element
     * or lies in it, or it is an index on it, a trigger on it or a comment on it.
     */
    private static boolean belongsTo(ModelElement written, ModelElement element) {
        if (written == null) {
            return false;
        }
        if (written == element || EcoreUtil.isAncestor(element, written)) {
            return true;
        }
        if (written instanceof SQLIndex index && index.getSpannedClass() != null) {
            return belongsTo(index.getSpannedClass(), element);
        }
        if (written instanceof Trigger trigger && trigger.getTable() != null) {
            return belongsTo(trigger.getTable(), element);
        }
        if (written instanceof Description description) {
            return description.getModelElement().stream().anyMatch(described -> belongsTo(described, element));
        }
        return false;
    }

    private static Schema schemaOf(EObject o) {
        for (EObject e = o; e != null; e = e.eContainer()) {
            if (e instanceof Schema s) {
                return s;
            }
        }
        return null;
    }
}
