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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * The names primary keys, unique constraints, check constraints, foreign keys,
 * indexes and triggers go by in a database, also where the model leaves one
 * without a name. Reading a database, writing DDL and writing a migration all
 * name by this one rule, so a drop finds what a create made.
 *
 * <p>
 * An element with a name keeps it. One without gets
 * {@code <prefix>_<table>}, the table being the one it belongs to:
 * </p>
 * <table>
 * <caption>Default names</caption>
 * <tr><th>Element</th><th>Prefix</th><th>Its table</th></tr>
 * <tr><td>primary key</td><td>{@code pk}</td><td>the table that owns it</td></tr>
 * <tr><td>unique constraint</td><td>{@code uc}</td><td>the table that owns it</td></tr>
 * <tr><td>check constraint</td><td>{@code ck}</td><td>the table that owns it, else the table of the
 * first column it constrains</td></tr>
 * <tr><td>foreign key</td><td>{@code fk}</td><td>the table that owns it</td></tr>
 * <tr><td>index</td><td>{@code idx}</td><td>the table or view it spans</td></tr>
 * <tr><td>trigger</td><td>{@code trg}</td><td>its table</td></tr>
 * </table>
 * <p>
 * Where that name is taken, {@code _2}, {@code _3}, … is appended, the first
 * one that is free. Taken, regardless of case, are the names of the tables and
 * views of the schema, the names of the elements above that belong to a table
 * of the schema, and the default names given before; databases keep these
 * names in one namespace per schema (PostgreSQL and Oracle) or per table, so a
 * name free in the schema is free in either. The elements are named in the
 * order of the containment tree. An element of a table without a schema shares
 * the namespace of its model.
 * </p>
 * <p>
 * A default name thus depends on the other names of its schema: name the
 * element in the model to fix its name for good. An element that belongs to no
 * table keeps what it has.
 * </p>
 */
public final class RelationalNames {

    /**
     * The tag that keeps the specific name of a routine as the database it was read from gives it:
     * the name that tells the routine from others of its name in its schema (overloads), in
     * PostgreSQL its name and its oid, {@code f_total_16384}. A privilege on a routine names the
     * overload it is on by it. The name is the database's own and holds only there: the DDL
     * generator does not write it, a comparison of two models does not compare it, and the same
     * routine written into another database gets another one. A reader sets it only where it differs
     * from the routine's name.
     */
    public static final String SPECIFIC_NAME_TAG = "daanse.jdbc.specificName";

    private RelationalNames() {
    }

    /**
     * The specific name of {@code routine}: its tagged value {@link #SPECIFIC_NAME_TAG}, otherwise its
     * name, as a database gives a routine whose name is unique in its schema.
     */
    public static String specificName(Procedure routine) {
        return TaggedValues.value(routine, SPECIFIC_NAME_TAG).filter(n -> !n.isBlank()).orElse(routine.getName());
    }

    /**
     * The name {@code element} goes by: its own, or for a key, constraint,
     * index or trigger without one, the default name of the rule above, found
     * in the whole model {@code element} is part of. {@code null} for
     * {@code null} and for an element without name that is none of these.
     */
    public static String of(ModelElement element) {
        if (element == null) {
            return null;
        }
        if (hasName(element)) {
            return element.getName();
        }
        return in(EcoreUtil.getRootContainer(element)).get(element);
    }

    /**
     * The names of the keys, constraints, indexes and triggers in
     * {@code root}'s containment tree, {@code root} included, by element: their
     * own, or their default names. For many lookups in one model, as when a
     * whole script is written, this walks the model once where {@link #of}
     * walks it per element. Elements that belong to no table and have no name
     * are not in the map.
     */
    public static Map<ModelElement, String> in(EObject root) {
        Map<ModelElement, String> names = new IdentityHashMap<>();
        if (root == null) {
            return names;
        }
        List<Named> elements = new ArrayList<>();
        Map<Object, Set<String>> taken = new HashMap<>();
        collect(root, elements, taken);
        for (EObject o : (Iterable<EObject>) root::eAllContents) {
            collect(o, elements, taken);
        }
        for (Named n : elements) {
            if (hasName(n.element())) {
                names.put(n.element(), n.element().getName());
            }
        }
        for (Named n : elements) {
            if (hasName(n.element()) || n.table() == null) {
                continue;
            }
            Set<String> inScope = taken.computeIfAbsent(n.scope(), k -> new HashSet<>());
            String base = n.prefix() + "_" + (n.table().getName() == null ? "" : n.table().getName());
            String name = base;
            for (int i = 2; inScope.contains(key(name)); i++) {
                name = base + "_" + i;
            }
            inScope.add(key(name));
            names.put(n.element(), name);
        }
        return names;
    }

    /**
     * Gives every key, constraint, index and trigger without a name in
     * {@code root}'s containment tree its default name, as a reader of a
     * database does for what the database does not name. Afterwards the model
     * no longer depends on the rule.
     */
    public static void assign(EObject root) {
        in(root).forEach((element, name) -> {
            if (!hasName(element)) {
                element.setName(name);
            }
        });
    }

    /**
     * The name of the procedure that holds the body of the trigger named
     * {@code triggerName}, where a database keeps a trigger's body in a
     * procedure of its own (a trigger function in PostgreSQL, a procedure the
     * trigger calls in MariaDB or SQL Server): {@code <trigger>_fn}. Each
     * trigger has its own, also where two share a body, so dropping one
     * trigger never takes the procedure of another; and a reader of the
     * database knows the procedure for a part of its trigger.
     */
    public static String triggerProcedure(String triggerName) {
        return triggerName + "_fn";
    }

    /**
     * The prefix of the default names of {@code element}'s kind: {@code pk},
     * {@code uc}, {@code ck}, {@code fk}, {@code idx} or {@code trg}; empty for
     * any other kind.
     */
    public static Optional<String> prefix(ModelElement element) {
        if (element instanceof PrimaryKey) {
            return Optional.of("pk");
        }
        if (element instanceof UniqueConstraint) {
            return Optional.of("uc");
        }
        if (element instanceof CheckConstraint) {
            return Optional.of("ck");
        }
        if (element instanceof ForeignKey) {
            return Optional.of("fk");
        }
        if (element instanceof SQLIndex) {
            return Optional.of("idx");
        }
        if (element instanceof Trigger) {
            return Optional.of("trg");
        }
        return Optional.empty();
    }

    /**
     * The table or view {@code element} belongs to, as the default name takes
     * it (see the table above); empty when there is none.
     */
    public static Optional<NamedColumnSet> tableOf(ModelElement element) {
        if (element instanceof SQLIndex index) {
            return index.getSpannedClass() instanceof NamedColumnSet ncs ? Optional.of(ncs) : Optional.empty();
        }
        if (element instanceof Trigger trigger) {
            return Optional.ofNullable(trigger.getTable());
        }
        if (element instanceof PrimaryKey || element instanceof UniqueConstraint || element instanceof ForeignKey
                || element instanceof CheckConstraint) {
            if (element.getNamespace() instanceof NamedColumnSet owner) {
                return Optional.of(owner);
            }
            if (element instanceof CheckConstraint check) {
                return check.getConstrainedElement().stream().filter(Column.class::isInstance)
                        .map(Column.class::cast).findFirst().flatMap(Columns::namedOwner);
            }
            if (element instanceof UniqueConstraint key) {
                return firstColumnOwner(key.getFeature());
            }
            if (element instanceof ForeignKey fk) {
                return firstColumnOwner(fk.getFeature());
            }
        }
        return Optional.empty();
    }

    private static Optional<NamedColumnSet> firstColumnOwner(List<? extends StructuralFeature> features) {
        return features.stream().filter(Column.class::isInstance).map(Column.class::cast).findFirst()
                .flatMap(Columns::namedOwner);
    }

    /** One element to name: its prefix, its table and the namespace its name must be free in. */
    private record Named(ModelElement element, String prefix, NamedColumnSet table, Object scope) {
    }

    private static void collect(EObject o, List<Named> elements, Map<Object, Set<String>> taken) {
        if (!(o instanceof ModelElement element)) {
            return;
        }
        if (element instanceof NamedColumnSet ncs) {
            if (hasName(ncs)) {
                taken.computeIfAbsent(scope(ncs), k -> new HashSet<>()).add(key(ncs.getName()));
            }
            return;
        }
        Optional<String> prefix = prefix(element);
        if (prefix.isEmpty()) {
            return;
        }
        NamedColumnSet table = tableOf(element).orElse(null);
        Object scope = table == null ? null : scope(table);
        elements.add(new Named(element, prefix.get(), table, scope));
        if (table != null && hasName(element)) {
            taken.computeIfAbsent(scope, k -> new HashSet<>()).add(key(element.getName()));
        }
    }

    /** The namespace a name is free in: the schema of the table, else the model the table is in. */
    private static Object scope(NamedColumnSet table) {
        Optional<Schema> schema = NamedColumnSets.findSchema(table);
        return schema.isPresent() ? schema.get() : EcoreUtil.getRootContainer(table);
    }

    private static boolean hasName(ModelElement element) {
        return element.getName() != null && !element.getName().isBlank();
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
