/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.orm.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.eclipse.daanse.cwm.model.cwm.foundation.keysindexes.UniqueKey;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Parameter;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Multiplicity;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.MultiplicityRange;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Generalization;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLDataType;
import org.eclipse.daanse.cwm.model.daanse.orm.AttributeMapping;
import org.eclipse.daanse.cwm.model.daanse.orm.AttributeOverride;
import org.eclipse.daanse.cwm.model.daanse.orm.Base;
import org.eclipse.daanse.cwm.model.daanse.orm.Basic;
import org.eclipse.daanse.cwm.model.daanse.orm.CollectionSupport;
import org.eclipse.daanse.cwm.model.daanse.orm.Column;
import org.eclipse.daanse.cwm.model.daanse.orm.ElementCollection;
import org.eclipse.daanse.cwm.model.daanse.orm.Embeddable;
import org.eclipse.daanse.cwm.model.daanse.orm.Embedded;
import org.eclipse.daanse.cwm.model.daanse.orm.EmbeddedId;
import org.eclipse.daanse.cwm.model.daanse.orm.Entity;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityMappings;
import org.eclipse.daanse.cwm.model.daanse.orm.GeneratedValue;
import org.eclipse.daanse.cwm.model.daanse.orm.Generator;
import org.eclipse.daanse.cwm.model.daanse.orm.IdentifiableType;
import org.eclipse.daanse.cwm.model.daanse.orm.Join;
import org.eclipse.daanse.cwm.model.daanse.orm.JoinColumn;
import org.eclipse.daanse.cwm.model.daanse.orm.JoinTable;
import org.eclipse.daanse.cwm.model.daanse.orm.JoinedTable;
import org.eclipse.daanse.cwm.model.daanse.orm.ManagedType;
import org.eclipse.daanse.cwm.model.daanse.orm.ManyToMany;
import org.eclipse.daanse.cwm.model.daanse.orm.ManyToOne;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedStoredProcedureQuery;
import org.eclipse.daanse.cwm.model.daanse.orm.OneToMany;
import org.eclipse.daanse.cwm.model.daanse.orm.OneToOne;
import org.eclipse.daanse.cwm.model.daanse.orm.OverrideHost;
import org.eclipse.daanse.cwm.model.daanse.orm.RelationshipMapping;
import org.eclipse.daanse.cwm.model.daanse.orm.SecondaryTable;
import org.eclipse.daanse.cwm.model.daanse.orm.Transient;
import org.eclipse.daanse.cwm.model.daanse.orm.ValueMapping;
import org.eclipse.emf.ecore.EObject;

/**
 * Resolves an ORM mapping into readable bindings: for every attribute and every relationship role
 * of a managed type, the table and the columns it ends up in.
 *
 * <p>The model holds this answer in several places — a {@link Basic} through its {@link Column}, an
 * {@link Embedded} through overrides or the default binding of its {@link Embeddable}, a
 * relationship through a {@link Join} in its own table, the target table or a join table.
 * This class collects it per mapping kind into a {@link Binding}, so that generators (Java and
 * Python metadata) and runtime code share one resolution.</p>
 *
 * <p>It also holds the derivations the model deliberately does not carry as features (one
 * fact, one place): schema and catalog from the table, primary key, discriminator type from the
 * SQL type, optionality from the multiplicity, the table of a join form from its join columns,
 * the column pairs of a foreign key, path names, procedure parameters and the object package.</p>
 *
 * <p>Names always come from the referenced CWM elements ({@code cwm*}), never from name
 * copies. Columns are unqualified; {@link Binding#referencedColumns()} is qualified as
 * {@code TABLE.COLUMN}, because referenced columns usually live in another table.</p>
 */
public final class OrmBindings {

    private OrmBindings() {
    }

    /**
     * The binding of an attribute or a relationship role.
     *
     * @param feature            name of the attribute or association role
     * @param kind               mapping kind, e.g. {@code ID}, {@code BASIC}, {@code MANY_TO_MANY}
     * @param target             target class of a relationship, otherwise empty
     * @param table              table of the value columns; without value columns and without a join
     *                           table, the table of the join columns (for one-to-many the target
     *                           table), otherwise empty
     * @param columns            value columns (unqualified)
     * @param paths              sub-attributes parallel to {@code columns} (embedded), otherwise empty
     * @param joinTable          join table or collection table, otherwise empty
     * @param joinColumns        join columns (own side)
     * @param referencedColumns  the columns referenced by the join columns, qualified,
     *                           parallel to {@code joinColumns}
     * @param inverseJoinColumns join columns of the inverse side in a join table
     * @param inverseReferencedColumns the columns referenced by the inverse join columns,
     *                           qualified, parallel to {@code inverseJoinColumns}
     * @param orderColumn        order column of a collection, otherwise empty
     * @param mapKeyColumn       map-key column of a collection, otherwise empty
     */
    public record Binding(String feature, String kind, String target, String table,
            List<String> columns, List<String> paths, String joinTable, List<String> joinColumns,
            List<String> referencedColumns, List<String> inverseJoinColumns,
            List<String> inverseReferencedColumns, String orderColumn, String mapKeyColumn) {
    }

    /**
     * A column pair of a join: the foreign-key column and the column it points to.
     *
     * @param column     the column in the table of the join
     * @param referenced the referenced column of the target table
     */
    public record JoinPair(org.eclipse.daanse.cwm.model.cwm.resource.relational.Column column,
            org.eclipse.daanse.cwm.model.cwm.resource.relational.Column referenced) {
    }

    /** All managed types of a mapping set: mapped superclasses, entities, embeddables. */
    public static List<ManagedType> managedTypes(EntityMappings em) {
        List<ManagedType> all = new ArrayList<>();
        all.addAll(em.getMappedSuperclass());
        all.addAll(em.getEntity());
        all.addAll(em.getEmbeddable());
        return all;
    }

    /** {@code ENTITY}, {@code EMBEDDABLE} or {@code MAPPED_SUPERCLASS}. */
    public static String kind(ManagedType mt) {
        return constant(mt.eClass().getName());
    }

    /** Name of the class the managed type maps. */
    public static String className(ManagedType mt) {
        return name(mt.getCwmClass());
    }

    /**
     * The object package of a mapping set: the common container of the mapped classes (for classes
     * in different packages their nearest common ancestor), otherwise {@code null}.
     */
    public static org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package objectPackage(EntityMappings em) {
        List<EObject> common = null;
        for (ManagedType mt : managedTypes(em)) {
            if (mt.getCwmClass() == null) {
                continue;
            }
            List<EObject> chain = containerChain(mt.getCwmClass());
            if (common == null) {
                common = chain;
            } else {
                common.retainAll(chain);
            }
        }
        if (common != null) {
            for (EObject o : common) {
                if (o instanceof org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package p) {
                    return p;
                }
            }
        }
        return null;
    }

    /** The containers of an element, from the nearest up to the root. */
    private static List<EObject> containerChain(EObject o) {
        List<EObject> chain = new ArrayList<>();
        for (EObject c = o.eContainer(); c != null; c = c.eContainer()) {
            chain.add(c);
        }
        return chain;
    }

    /** The table of an entity; a subclass without a table of its own lives in that of its root. */
    public static NamedColumnSet tableOf(ManagedType mt) {
        for (ManagedType t : lineage(mt)) {
            if (t instanceof Entity e && e.getCwmTable() != null) {
                return e.getCwmTable();
            }
        }
        return null;
    }

    /**
     * The table of an entity. If the entity names none of its own — a subclass under
     * {@code SINGLE_TABLE} inheritance —, the table of the nearest ancestor entity applies.
     * Embeddables and mapped superclasses have none: empty.
     */
    public static String table(ManagedType mt) {
        return mt instanceof Entity ? name(tableOf(mt)) : "";
    }

    /** The schema that contains the table of an entity, otherwise empty. */
    public static String schema(ManagedType mt) {
        NamedColumnSet t = mt instanceof Entity ? tableOf(mt) : null;
        return t != null && t.eContainer() instanceof ModelElement s ? name(s) : "";
    }

    /** The catalog that contains the schema of the table of an entity, otherwise empty. */
    public static String catalog(ManagedType mt) {
        NamedColumnSet t = mt instanceof Entity ? tableOf(mt) : null;
        return t != null && t.eContainer() != null && t.eContainer().eContainer() instanceof ModelElement c
                ? name(c)
                : "";
    }

    /** The primary key of the table of an entity (for {@code SINGLE_TABLE} the root table), otherwise {@code null}. */
    public static PrimaryKey primaryKey(Entity e) {
        NamedColumnSet t = tableOf(e);
        if (t == null) {
            return null;
        }
        for (EObject o : t.getOwnedElement()) {
            if (o instanceof PrimaryKey pk) {
                return pk;
            }
        }
        return null;
    }

    /**
     * The mapped supertype: the managed type of the same {@code EntityMappings} whose class is the
     * superclass ({@code relationships::Generalization}, child → parent, in the container of the
     * class), otherwise {@code null}.
     */
    public static ManagedType superType(ManagedType mt) {
        var cls = mt.getCwmClass();
        if (cls == null || !(mt.eContainer() instanceof EntityMappings em)) {
            return null;
        }
        for (Classifier parent : parents(cls)) {
            for (ManagedType candidate : managedTypes(em)) {
                if (candidate.getCwmClass() == parent) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** The direct superclasses of a class: generalizations in the container of the class. */
    public static List<Classifier> parents(Classifier cls) {
        List<Classifier> out = new ArrayList<>();
        if (cls == null || cls.eContainer() == null) {
            return out;
        }
        for (EObject o : cls.eContainer().eContents()) {
            if (o instanceof Generalization g && g.getChild() == cls && g.getParent() != null) {
                out.add(g.getParent());
            }
        }
        return out;
    }

    /** The class itself and all its superclasses (cycle-safe). */
    public static List<Classifier> ancestors(Classifier cls) {
        List<Classifier> out = new ArrayList<>();
        List<Classifier> todo = new ArrayList<>();
        if (cls != null) {
            todo.add(cls);
        }
        while (!todo.isEmpty()) {
            Classifier c = todo.remove(0);
            if (!out.contains(c)) {
                out.add(c);
                todo.addAll(parents(c));
            }
        }
        return out;
    }

    /** The type itself, then its mapped supertypes up to the root (cycle-safe). */
    public static List<ManagedType> lineage(ManagedType mt) {
        List<ManagedType> out = new ArrayList<>();
        for (ManagedType t = mt; t != null && !out.contains(t); t = superType(t)) {
            out.add(t);
        }
        return out;
    }

    /** Name of the class of the mapped supertype, otherwise empty. */
    public static String superClassName(ManagedType mt) {
        ManagedType parent = superType(mt);
        return parent == null ? "" : className(parent);
    }

    /**
     * The inheritance strategy ({@code SINGLE_TABLE}, {@code JOINED}, …). A subclass that names
     * none of its own follows the strategy of its hierarchy: the nearest ancestor entity with a
     * set strategy determines it.
     */
    public static String inheritance(ManagedType mt) {
        for (ManagedType t : lineage(mt)) {
            if (t instanceof Entity e && e.isSetInheritanceStrategy()) {
                return e.getInheritanceStrategy().getLiteral();
            }
        }
        return "";
    }

    /** The discriminator column of the hierarchy: at the nearest ancestor entity that names it. */
    public static org.eclipse.daanse.cwm.model.cwm.resource.relational.Column discriminatorColumnOf(ManagedType mt) {
        for (ManagedType t : lineage(mt)) {
            if (t instanceof Entity e && e.getCwmDiscriminatorColumn() != null) {
                return e.getCwmDiscriminatorColumn();
            }
        }
        return null;
    }

    /**
     * The discriminator column. The model holds it at the root of the hierarchy; a subclass
     * gets it from the nearest ancestor entity that names it — its discriminator value
     * lives in exactly this column.
     */
    public static String discriminatorColumn(ManagedType mt) {
        return name(discriminatorColumnOf(mt));
    }

    /**
     * The JPA discriminator type from the SQL type of the discriminator column: {@code INTEGER} for
     * integer and numeric types, {@code CHAR} for characters of length 1, otherwise {@code STRING};
     * empty without a discriminator column.
     */
    public static String discriminatorType(ManagedType mt) {
        var col = discriminatorColumnOf(mt);
        if (col == null) {
            return "";
        }
        String type = col.getType() instanceof SQLDataType t && t.getName() != null
                ? t.getName().toUpperCase(Locale.ROOT)
                : "";
        if (type.matches("(TINY|SMALL|MEDIUM|BIG)?INT(EGER)?\\d*|NUMERIC|DECIMAL|NUMBER")) {
            return "INTEGER";
        }
        if (type.matches("N?CHAR(ACTER)?") && (col.getLength() == 1 || col.getLength() == 0)) {
            return "CHAR";
        }
        return "STRING";
    }

    /** The discriminator value, if set. */
    public static String discriminatorValue(ManagedType mt) {
        return mt instanceof Entity e && e.getDiscriminatorValue() != null
                ? e.getDiscriminatorValue() : "";
    }

    /** The secondary tables of an entity. */
    public static List<String> secondaryTables(ManagedType mt) {
        List<String> out = new ArrayList<>();
        if (mt instanceof Entity e) {
            for (SecondaryTable st : e.getSecondaryTable()) {
                add(out, name(tableOf(st)));
            }
        }
        return out;
    }

    /**
     * The column pairs of a join: with {@code cwmForeignKey} its {@code feature} ↔
     * {@code uniqueKey.feature} in key order, otherwise the explicit join columns.
     */
    public static List<JoinPair> joinColumns(Join join) {
        List<JoinPair> out = new ArrayList<>();
        if (join == null) {
            return out;
        }
        ForeignKey fk = join.getCwmForeignKey();
        if (fk != null) {
            UniqueKey uk = fk.getUniqueKey();
            List<StructuralFeature> referenced = uk == null ? List.of() : uk.getFeature();
            for (int i = 0; i < fk.getFeature().size(); i++) {
                if (fk.getFeature().get(i) instanceof org.eclipse.daanse.cwm.model.cwm.resource.relational.Column c) {
                    var ref = i < referenced.size()
                            && referenced.get(i) instanceof org.eclipse.daanse.cwm.model.cwm.resource.relational.Column r
                                    ? r
                                    : null;
                    out.add(new JoinPair(c, ref));
                }
            }
            return out;
        }
        for (JoinColumn jc : join.getJoinColumn()) {
            if (jc.getCwmColumn() != null) {
                out.add(new JoinPair(jc.getCwmColumn(), jc.getCwmReferencedColumn()));
            }
        }
        return out;
    }

    /** The table that holds the columns of a join: owner of the join columns or of the foreign key. */
    public static NamedColumnSet tableOf(Join join) {
        if (join == null) {
            return null;
        }
        if (join.getCwmForeignKey() != null && join.getCwmForeignKey().eContainer() instanceof NamedColumnSet t) {
            return t;
        }
        for (JoinPair p : joinColumns(join)) {
            if (p.column().getOwner() instanceof NamedColumnSet t) {
                return t;
            }
        }
        return null;
    }

    /** The table a join points to: owner of the referenced columns. */
    public static NamedColumnSet referencedTableOf(Join join) {
        for (JoinPair p : joinColumns(join)) {
            if (p.referenced() != null && p.referenced().getOwner() instanceof NamedColumnSet t) {
                return t;
            }
        }
        return null;
    }

    /** The table of a join form (secondary, collection or join table): the table of its join. */
    public static NamedColumnSet tableOf(JoinedTable jt) {
        return jt == null ? null : tableOf(jt.getJoin());
    }

    /** The path as text: the feature names joined with a dot, e.g. {@code address.street}. */
    public static String path(List<? extends StructuralFeature> path) {
        StringBuilder sb = new StringBuilder();
        for (StructuralFeature f : path) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(name(f));
        }
        return sb.toString();
    }

    /** The mapped feature of an attribute mapping: attribute, association end or transient feature. */
    public static StructuralFeature featureOf(AttributeMapping m) {
        if (m instanceof ValueMapping v) {
            return v.getCwmAttribute();
        }
        if (m instanceof RelationshipMapping r) {
            return r.getCwmAssociationEnd();
        }
        if (m instanceof Transient t) {
            return t.getCwmFeature();
        }
        return null;
    }

    /**
     * Whether the value may be absent: lower bound 0 in the multiplicity of the attribute or end.
     * Without a stated multiplicity {@code true} (the lower bound is then unknown).
     */
    public static boolean optional(AttributeMapping m) {
        StructuralFeature f = featureOf(m);
        Multiplicity mult = f == null ? null : f.getMultiplicity();
        if (mult == null || mult.getRange().isEmpty()) {
            return true;
        }
        for (MultiplicityRange r : mult.getRange()) {
            if (r.getLower() == 0) {
                return true;
            }
        }
        return false;
    }

    /** The generator of a generated value, otherwise {@code null}. */
    public static Generator generatorOf(GeneratedValue gv) {
        return gv == null ? null : gv.getGenerator();
    }

    /** The parameters of a stored-procedure query: the parameters of the procedure in call order. */
    public static List<Parameter> parameters(NamedStoredProcedureQuery q) {
        return q.getCwmProcedure() == null ? List.of() : List.copyOf(q.getCwmProcedure().getParameter());
    }

    /**
     * All attribute mappings in a fixed order: id, embedded id, version, basic, embedded,
     * transient, element collection, then the relationships.
     */
    public static List<AttributeMapping> mappings(ManagedType mt) {
        List<AttributeMapping> all = new ArrayList<>();
        if (mt instanceof IdentifiableType it) {
            all.addAll(it.getId());
            if (it.getEmbeddedId() != null) {
                all.add(it.getEmbeddedId());
            }
            all.addAll(it.getVersion());
        }
        all.addAll(mt.getBasic());
        all.addAll(mt.getEmbedded());
        all.addAll(mt.getTransient());
        all.addAll(mt.getElementCollection());
        all.addAll(mt.getManyToOne());
        all.addAll(mt.getOneToOne());
        all.addAll(mt.getOneToMany());
        all.addAll(mt.getManyToMany());
        return all;
    }

    /** The bindings of all attribute mappings of a managed type. */
    public static List<Binding> of(ManagedType mt) {
        return mappings(mt).stream().map(OrmBindings::of).toList();
    }

    /** The binding of a single attribute mapping. */
    public static Binding of(AttributeMapping m) {
        Builder b = new Builder(feature(m), constant(m.eClass().getName()));

        if (m instanceof EmbeddedId || m instanceof Embedded) {
            embedded(b, (OverrideHost) m, m);
        } else if (m instanceof ElementCollection ec) {
            b.value(column(ec.getColumn()), "");
            for (AttributeOverride ao : ec.getAttributeOverride()) {
                b.value(column(ao.getColumn()), path(ao.getCwmPath()));
            }
            if (ec.getCollectionTable() != null) {
                b.joinTable = name(tableOf(ec.getCollectionTable()));
                joinColumns(b, ec.getCollectionTable().getJoin());
            }
            collection(b, ec);
        } else if (m instanceof Base base) {
            // Id, Version, Basic
            b.value(column(base.getColumn()), "");
        }

        if (m instanceof RelationshipMapping r) {
            b.target = r.getCwmAssociationEnd() == null ? ""
                    : name(r.getCwmAssociationEnd().getType());
            if (m instanceof ManyToOne mto) {
                joinColumns(b, mto.getJoin());
            } else if (m instanceof OneToOne oto) {
                joinColumns(b, oto.getJoin());
                joinColumns(b, oto.getPrimaryKeyJoin());
            } else if (m instanceof OneToMany otm) {
                joinColumns(b, otm.getJoin());
                collection(b, otm);
            } else if (m instanceof ManyToMany mtm) {
                collection(b, mtm);
            }
            JoinTable jt = r.getJoinTable();
            if (jt != null) {
                b.joinTable = name(tableOf(jt));
                joinColumns(b, jt.getJoin());
                joinColumns(joinColumns(jt.getInverseJoin()), b.inverseJoinColumns,
                        b.inverseReferencedColumns);
            }
        }
        return b.build();
    }

    /** The overrides, otherwise the default binding of the embedded embeddable. */
    private static void embedded(Builder b, OverrideHost host, AttributeMapping m) {
        List<AttributeOverride> overrides = host.getAttributeOverride();
        if (!overrides.isEmpty()) {
            for (AttributeOverride ao : overrides) {
                b.value(column(ao.getColumn()), path(ao.getCwmPath()));
            }
            return;
        }
        Embeddable target = embeddableOf(m);
        if (target != null) {
            for (Basic ba : target.getBasic()) {
                b.value(column(ba.getColumn()), feature(ba));
            }
        }
    }

    /** The embeddable whose class is the type of the mapped attribute, otherwise {@code null}. */
    public static Embeddable embeddableOf(AttributeMapping m) {
        var attr = m instanceof ValueMapping v ? v.getCwmAttribute() : null;
        if (attr == null || attr.getType() == null) {
            return null;
        }
        for (var c = m.eContainer(); c != null; c = c.eContainer()) {
            if (c instanceof EntityMappings em) {
                for (Embeddable e : em.getEmbeddable()) {
                    if (e.getCwmClass() == attr.getType()) {
                        return e;
                    }
                }
                return null;
            }
        }
        return null;
    }

    private static void collection(Builder b, CollectionSupport cs) {
        b.orderColumn = name(column(cs.getOrderColumn()));
        b.mapKeyColumn = cs.getMapKey() == null ? "" : name(column(cs.getMapKey().getColumn()));
    }

    private static void joinColumns(Builder b, Join join) {
        List<JoinPair> pairs = joinColumns(join);
        for (JoinPair p : pairs) {
            if (b.joinColumnTable.isEmpty() && p.column().getOwner() instanceof NamedColumnSet t) {
                b.joinColumnTable = name(t);
            }
        }
        joinColumns(pairs, b.joinColumns, b.referencedColumns);
    }

    /** Each join column paired with its reference; a missing reference stays an empty entry. */
    private static void joinColumns(List<JoinPair> pairs, List<String> columns, List<String> referenced) {
        for (JoinPair p : pairs) {
            columns.add(name(p.column()));
            var ref = p.referenced();
            String owner = ref != null && ref.getOwner() instanceof NamedColumnSet t ? name(t) + "." : "";
            referenced.add(ref == null ? "" : owner + name(ref));
        }
    }

    private static org.eclipse.daanse.cwm.model.cwm.resource.relational.Column column(Column c) {
        return c == null ? null : c.getCwmColumn();
    }

    private static String feature(AttributeMapping m) {
        StructuralFeature f = featureOf(m);
        if (f != null) {
            return name(f);
        }
        return m.getName() == null ? "" : m.getName();
    }

    /** {@code ManyToMany} → {@code MANY_TO_MANY}. */
    private static String constant(String eClassName) {
        return eClassName.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }

    private static String name(ModelElement me) {
        return me == null || me.getName() == null ? "" : me.getName();
    }

    private static void add(List<String> list, String value) {
        if (!value.isEmpty()) {
            list.add(value);
        }
    }

    /** Collects a binding step by step; {@code columns} and {@code paths} stay parallel. */
    private static final class Builder {
        private final String feature;
        private final String kind;
        private String target = "";
        private String table = "";
        private String joinColumnTable = "";
        private final List<String> columns = new ArrayList<>();
        private final List<String> paths = new ArrayList<>();
        private String joinTable = "";
        private final List<String> joinColumns = new ArrayList<>();
        private final List<String> referencedColumns = new ArrayList<>();
        private final List<String> inverseJoinColumns = new ArrayList<>();
        private final List<String> inverseReferencedColumns = new ArrayList<>();
        private String orderColumn = "";
        private String mapKeyColumn = "";

        Builder(String feature, String kind) {
            this.feature = feature;
            this.kind = kind;
        }

        void value(org.eclipse.daanse.cwm.model.cwm.resource.relational.Column col, String path) {
            if (col == null) {
                return;
            }
            columns.add(name(col));
            paths.add(path == null ? "" : path);
            if (table.isEmpty() && col.getOwner() instanceof NamedColumnSet t) {
                table = name(t);
            }
        }

        Binding build() {
            boolean anyPath = paths.stream().anyMatch(p -> !p.isEmpty());
            String where = !table.isEmpty() || !joinTable.isEmpty() ? table : joinColumnTable;
            return new Binding(feature, kind, target, where, List.copyOf(columns),
                    anyPath ? List.copyOf(paths) : List.of(), joinTable, List.copyOf(joinColumns),
                    List.copyOf(referencedColumns), List.copyOf(inverseJoinColumns),
                    List.copyOf(inverseReferencedColumns), orderColumn, mapKeyColumn);
        }
    }
}
