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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Multiplicity;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.MultiplicityRange;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Association;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalPackage;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.daanse.orm.AssociationOverride;
import org.eclipse.daanse.cwm.model.daanse.orm.AttributeMapping;
import org.eclipse.daanse.cwm.model.daanse.orm.AttributeOverride;
import org.eclipse.daanse.cwm.model.daanse.orm.Base;
import org.eclipse.daanse.cwm.model.daanse.orm.Basic;
import org.eclipse.daanse.cwm.model.daanse.orm.CollectionSupport;
import org.eclipse.daanse.cwm.model.daanse.orm.CollectionTable;
import org.eclipse.daanse.cwm.model.daanse.orm.ColumnRef;
import org.eclipse.daanse.cwm.model.daanse.orm.ColumnResult;
import org.eclipse.daanse.cwm.model.daanse.orm.ConstructorResult;
import org.eclipse.daanse.cwm.model.daanse.orm.Convert;
import org.eclipse.daanse.cwm.model.daanse.orm.Converter;
import org.eclipse.daanse.cwm.model.daanse.orm.ElementCollection;
import org.eclipse.daanse.cwm.model.daanse.orm.Embeddable;
import org.eclipse.daanse.cwm.model.daanse.orm.Embedded;
import org.eclipse.daanse.cwm.model.daanse.orm.EmbeddedId;
import org.eclipse.daanse.cwm.model.daanse.orm.Entity;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityListener;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityMappings;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityResult;
import org.eclipse.daanse.cwm.model.daanse.orm.FieldResult;
import org.eclipse.daanse.cwm.model.daanse.orm.GeneratedValue;
import org.eclipse.daanse.cwm.model.daanse.orm.GenerationType;
import org.eclipse.daanse.cwm.model.daanse.orm.Generator;
import org.eclipse.daanse.cwm.model.daanse.orm.Id;
import org.eclipse.daanse.cwm.model.daanse.orm.IdentifiableType;
import org.eclipse.daanse.cwm.model.daanse.orm.InheritanceType;
import org.eclipse.daanse.cwm.model.daanse.orm.Join;
import org.eclipse.daanse.cwm.model.daanse.orm.JoinTable;
import org.eclipse.daanse.cwm.model.daanse.orm.JoinedTable;
import org.eclipse.daanse.cwm.model.daanse.orm.LifecycleCallback;
import org.eclipse.daanse.cwm.model.daanse.orm.ManagedType;
import org.eclipse.daanse.cwm.model.daanse.orm.ManyToMany;
import org.eclipse.daanse.cwm.model.daanse.orm.ManyToOne;
import org.eclipse.daanse.cwm.model.daanse.orm.MapKey;
import org.eclipse.daanse.cwm.model.daanse.orm.MappedSuperclass;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedAttributeNode;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedEntityGraph;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedNativeQuery;
import org.eclipse.daanse.cwm.model.daanse.orm.OneToMany;
import org.eclipse.daanse.cwm.model.daanse.orm.OneToOne;
import org.eclipse.daanse.cwm.model.daanse.orm.Ordering;
import org.eclipse.daanse.cwm.model.daanse.orm.OverrideHost;
import org.eclipse.daanse.cwm.model.daanse.orm.OwnableRelationship;
import org.eclipse.daanse.cwm.model.daanse.orm.RelationshipMapping;
import org.eclipse.daanse.cwm.model.daanse.orm.SecondaryTable;
import org.eclipse.daanse.cwm.model.daanse.orm.SequenceGenerator;
import org.eclipse.daanse.cwm.model.daanse.orm.SqlResultSetMapping;
import org.eclipse.daanse.cwm.model.daanse.orm.TableGenerator;
import org.eclipse.daanse.cwm.model.daanse.orm.ValueMapping;
import org.eclipse.daanse.cwm.model.daanse.orm.util.OrmBindings.JoinPair;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Programmatic check of the model invariants of the {@code orm} metamodel — the explicit
 * profile.
 *
 * <p>The model itself is deliberately OCL-free (plain mapping model) and does not hold
 * context-dependent obligations as {@code lowerBound}. This class checks them: whatever JPA would
 * fill in silently (every attribute persistent, column = attribute name, join columns and join
 * tables by naming rule, {@code SINGLE_TABLE}, {@code AUTO}) must be stated explicitly in the
 * model.</p>
 *
 * <ul>
 * <li>{@code MULTIPLICITY} — to-one mappings on ends with upper bound 1, to-many on more.</li>
 * <li>{@code OWNING_SIDE} — exactly one owning side per bidirectionally mapped association.</li>
 * <li>{@code E1} completeness — every own attribute and every navigable end of the class has
 * exactly one mapping (if need be {@code Transient}); mappings of foreign features are errors.</li>
 * <li>{@code E2} column — Basic/Id/Version in entities name their column; whatever a mapped
 * superclass or an embeddable leaves open or places in a foreign table, every user overrides; in
 * {@code TABLE_PER_CLASS} this holds for everything inherited, also from entity superclasses,
 * because every concrete class has its columns in its own table; an element collection has its
 * collection table.</li>
 * <li>{@code E3} table — the columns of an entity lie in its table or a secondary table;
 * nullability and lower bound agree (warning).</li>
 * <li>{@code E4} relationships — the owning side has exactly one connection (join, join table,
 * primary-key join, mapsId), the inverse side none and a mapped opposite end; the target is an
 * entity.</li>
 * <li>{@code E5} join — foreign key xor column pairs; columns in one table, references in the
 * target table; join and inverse join of a join table in the same table.</li>
 * <li>{@code E6} inheritance — strategy at the root, discriminator column and values, own tables
 * and primary-key join per strategy.</li>
 * <li>{@code E7} column used twice — at most one writing use per column of an entity.</li>
 * <li>{@code E8} identity — Id xor Embedded Id, id class exactly for several ids and matching, id
 * columns in the primary key, mapsId on attributes of the embedded-id class.</li>
 * <li>{@code E9} consistency — generation strategy and generator, converter xor disabled
 * conversion, result class xor result set mapping, orderBy xor orderColumn, result columns from
 * the stored query, valid feature paths (overrides, converts, orderings).</li>
 * <li>{@code E10} names — name = anchor name for anchored elements; names of the unit-wide
 * definitions unique.</li>
 * <li>{@code E11} reverse direction (warning) — columns of mapped tables that no mapping uses.</li>
 * </ul>
 */
public final class OrmModelValidator {

    private OrmModelValidator() {
    }

    /** Severity of a finding; only {@link #ERROR} makes a mapping invalid. */
    public enum Severity {
        ERROR, WARNING
    }

    /**
     * A finding.
     *
     * @param severity severity
     * @param rule     rule, e.g. {@code E1} or {@code OWNING_SIDE}
     * @param element  the affected model element
     * @param message  human-readable description
     */
    public record Finding(Severity severity, String rule, EObject element, String message) {

        @Override
        public String toString() {
            return severity + " " + rule + ": " + message;
        }
    }

    /** Checks all {@link EntityMappings} in {@code root} (including {@code root}) and returns all findings. */
    public static List<Finding> check(EObject root) {
        List<Finding> out = new ArrayList<>();
        if (root instanceof EntityMappings em) {
            new Check(em, out).run();
        }
        for (TreeIterator<EObject> it = root.eAllContents(); it.hasNext();) {
            if (it.next() instanceof EntityMappings em) {
                new Check(em, out).run();
                it.prune();
            }
        }
        return out;
    }

    /** The errors ({@link Severity#ERROR}) as texts, rule first (empty ⇒ valid). */
    public static List<String> validate(EObject root) {
        return check(root).stream().filter(f -> f.severity() == Severity.ERROR)
                .map(f -> f.rule() + " " + f.message()).toList();
    }

    /** The check of one mapping set. */
    private static final class Check {

        private final EntityMappings em;
        private final List<Finding> out;
        private final List<ManagedType> types;
        private final Map<Classifier, ManagedType> byClass = new IdentityHashMap<>();
        private final Map<Classifier, List<AssociationEnd>> navigableEnds = new IdentityHashMap<>();

        Check(EntityMappings em, List<Finding> out) {
            this.em = em;
            this.out = out;
            this.types = OrmBindings.managedTypes(em);
            Set<EObject> roots = new LinkedHashSet<>();
            for (ManagedType t : types) {
                if (t.getCwmClass() != null) {
                    byClass.putIfAbsent(t.getCwmClass(), t);
                    roots.add(EcoreUtil.getRootContainer(t.getCwmClass()));
                }
            }
            for (EObject r : roots) {
                for (Iterator<EObject> it = r.eAllContents(); it.hasNext();) {
                    if (it.next() instanceof Association a) {
                        List<AssociationEnd> ends = ends(a);
                        if (ends.size() == 2) {
                            navigable(ends.get(0), ends.get(1));
                            navigable(ends.get(1), ends.get(0));
                        }
                    }
                }
            }
        }

        private void navigable(AssociationEnd end, AssociationEnd other) {
            if (end.isIsNavigable() && other.getType() != null) {
                navigableEnds.computeIfAbsent(other.getType(), k -> new ArrayList<>()).add(end);
            }
        }

        void run() {
            for (ManagedType t : types) {
                completeness(t);
            }
            relationships();
            columns();
            tables();
            joins();
            inheritance();
            doubleColumns();
            identity();
            consistency();
            names();
            unmappedColumns();
        }

        private void completeness(ManagedType t) {
            Classifier c = t.getCwmClass();
            if (c == null) {
                return;
            }
            Map<StructuralFeature, Integer> count = new IdentityHashMap<>();
            for (AttributeMapping m : OrmBindings.mappings(t)) {
                StructuralFeature f = OrmBindings.featureOf(m);
                if (f == null) {
                    continue;
                }
                count.merge(f, 1, Integer::sum);
                if (!belongsTo(f, c, false)) {
                    error("E1", m, label(m) + ": '" + name(f) + "' is not an own feature of the class "
                            + name(c) + ".");
                } else if (f instanceof AssociationEnd end && !end.isIsNavigable()) {
                    error("E1", m, label(m) + ": the end '" + name(f) + "' is not navigable.");
                }
            }
            List<StructuralFeature> required = new ArrayList<>();
            for (var f : c.getFeature()) {
                if (f instanceof Attribute a) {
                    required.add(a);
                }
            }
            required.addAll(navigableEnds.getOrDefault(c, List.of()));
            for (StructuralFeature f : required) {
                int n = count.getOrDefault(f, 0);
                if (n == 0) {
                    error("E1", t, label(t) + ": '" + name(f) + "' is not mapped (mapping or "
                            + "Transient missing).");
                } else if (n > 1) {
                    error("E1", t, label(t) + ": '" + name(f) + "' is mapped " + n + " times.");
                }
            }
        }

        private void relationships() {
            Map<Classifier, List<RelationshipMapping>> byAssociation = new LinkedHashMap<>();
            for (ManagedType t : types) {
                for (AttributeMapping m : OrmBindings.mappings(t)) {
                    if (!(m instanceof RelationshipMapping rm) || rm.getCwmAssociationEnd() == null) {
                        continue;
                    }
                    AssociationEnd end = rm.getCwmAssociationEnd();
                    multiplicity(rm, end);
                    if (end.getOwner() != null) {
                        byAssociation.computeIfAbsent(end.getOwner(), k -> new ArrayList<>()).add(rm);
                    }
                    connection(t, rm, end);
                }
            }
            for (Map.Entry<Classifier, List<RelationshipMapping>> e : byAssociation.entrySet()) {
                List<RelationshipMapping> mappings = e.getValue();
                if (mappings.size() < 2) {
                    continue;
                }
                long owners = mappings.stream().filter(OrmModelValidator::isOwningSide).count();
                if (owners != 1) {
                    error("OWNING_SIDE", e.getKey(), "Association '" + name(e.getKey()) + "': "
                            + mappings.size() + " mappings, " + owners
                            + " of them owning sides — exactly one side must be the owning side.");
                }
            }
        }

        /** MULTIPLICITY: a to-one mapping needs an end with upper bound 1, a to-many one more than 1. */
        private void multiplicity(RelationshipMapping rm, AssociationEnd end) {
            Multiplicity mult = end.getMultiplicity();
            if (mult == null || mult.getRange().isEmpty()) {
                return;
            }
            boolean toOne = rm instanceof ManyToOne || rm instanceof OneToOne;
            boolean toMany = rm instanceof OneToMany || rm instanceof ManyToMany;
            for (MultiplicityRange range : mult.getRange()) {
                long upper = range.getUpper();
                if (toOne && upper != 1) {
                    error("MULTIPLICITY", rm, label(rm) + ": target end '" + end.getName()
                            + "' has upper multiplicity " + upper + ", a to-one mapping requires 1.");
                }
                if (toMany && upper == 1) {
                    error("MULTIPLICITY", rm, label(rm) + ": target end '" + end.getName()
                            + "' has upper multiplicity 1, a to-many mapping requires more than 1 "
                            + "(or unbounded).");
                }
            }
        }

        /** E4: the connection of the owning side, none on the inverse side, the target is an entity. */
        private void connection(ManagedType owner, RelationshipMapping rm, AssociationEnd end) {
            if (!(byClass.get(end.getType()) instanceof Entity)) {
                error("E4", rm, label(rm) + ": the target " + name(end.getType()) + " is not an entity "
                        + "of this mapping set.");
            }
            int ways = connections(rm);
            if (isOwningSide(rm)) {
                if (owner instanceof Entity && ways != 1) {
                    error("E4", rm, label(rm) + ": the owning side needs exactly one connection (join, "
                            + "joinTable, primaryKeyJoin or mapsId), has " + ways + ".");
                } else if (!(owner instanceof Entity) && ways > 1) {
                    error("E4", rm, label(rm) + ": at most one connection, has " + ways + ".");
                }
                return;
            }
            if (ways > 0) {
                error("E4", rm, label(rm) + ": the inverse side carries no connection; the owning side "
                        + "holds it.");
            }
            AssociationEnd other = otherEnd(end);
            boolean mapped = false;
            for (ManagedType t : types) {
                for (AttributeMapping m : OrmBindings.mappings(t)) {
                    if (m instanceof RelationshipMapping o && o.getCwmAssociationEnd() == other && isOwningSide(o)) {
                        mapped = true;
                    }
                }
            }
            if (!mapped) {
                error("E4", rm, label(rm) + ": the opposite end '" + name(other) + "' is not mapped as "
                        + "the owning side.");
            }
        }

        private static int connections(RelationshipMapping rm) {
            int ways = rm.getJoinTable() != null ? 1 : 0;
            if (rm instanceof ManyToOne m) {
                ways += (m.getJoin() != null ? 1 : 0) + (m.isMapsId() ? 1 : 0);
            } else if (rm instanceof OneToOne o) {
                ways += (o.getJoin() != null ? 1 : 0) + (o.getPrimaryKeyJoin() != null ? 1 : 0)
                        + (o.isMapsId() ? 1 : 0);
            } else if (rm instanceof OneToMany o) {
                ways += o.getJoin() != null ? 1 : 0;
            }
            return ways;
        }

        private void columns() {
            for (ManagedType t : types) {
                if (t instanceof Entity e) {
                    Set<NamedColumnSet> tables = tables(e);
                    for (Base b : bases(e)) {
                        if (b.getColumn() == null) {
                            error("E2", b, label(b) + ": a mapping in an entity names its column.");
                        }
                    }
                    for (AttributeMapping m : OrmBindings.mappings(e)) {
                        if (m instanceof Embedded || m instanceof EmbeddedId) {
                            covered((OverrideHost) m, OrmBindings.embeddableOf(m), tables, m);
                        }
                    }
                    for (IdentifiableType above : inheritedInto(e)) {
                        coveredBySubclass(e, above, tables);
                    }
                }
                for (ElementCollection ec : t.getElementCollection()) {
                    if (ec.getCollectionTable() == null) {
                        error("E2", ec, label(ec) + ": an element collection names its collection table.");
                        continue;
                    }
                    Embeddable element = OrmBindings.embeddableOf(ec);
                    if (element == null && ec.getColumn() == null) {
                        error("E2", ec, label(ec) + ": the elements need their column.");
                    } else if (element != null) {
                        covered(ec, element, Set.of(OrmBindings.tableOf(ec.getCollectionTable())), ec);
                    }
                }
            }
        }

        /** The host covers, by override, the paths of an embeddable that are open or lie in a foreign table. */
        private void covered(OverrideHost host, Embeddable emb, Set<NamedColumnSet> tables, EObject where) {
            if (emb == null) {
                return;
            }
            Paths open = openPaths(emb, tables, new ArrayList<>());
            for (List<StructuralFeature> p : open.attributes) {
                if (!hasPath(host.getAttributeOverride(), p)) {
                    error("E2", where, label(where) + ": '" + OrmBindings.path(p) + "' has no column in "
                            + "this table — AttributeOverride missing.");
                }
            }
            for (List<StructuralFeature> p : open.associations) {
                if (!hasAssociationPath(host.getAssociationOverride(), p)) {
                    error("E2", where, label(where) + ": the relationship '" + OrmBindings.path(p)
                            + "' has no join — AssociationOverride missing.");
                }
            }
        }

        /**
         * The supertypes whose mappings end up in the table of {@code e}: the mapped superclasses up
         * to the nearest entity superclass; in {@code TABLE_PER_CLASS} all mapped supertypes, because
         * there every concrete class also holds the inherited columns in its own table.
         */
        private static List<IdentifiableType> inheritedInto(Entity e) {
            List<IdentifiableType> out = new ArrayList<>(mappedSuperclassesAbove(e));
            if (e.getCwmTable() != null
                    && InheritanceType.TABLEPERCLASS.getLiteral().equals(OrmBindings.inheritance(e))) {
                for (ManagedType t : OrmBindings.lineage(e)) {
                    if (t != e && t instanceof IdentifiableType it && !out.contains(it)) {
                        out.add(it);
                    }
                }
            }
            return out;
        }

        /** What a supertype leaves open or places in a foreign table, the inheriting entity sets by override. */
        private void coveredBySubclass(Entity e, IdentifiableType ms, Set<NamedColumnSet> tables) {
            for (Base b : bases(ms)) {
                var col = b.getColumn() == null ? null : b.getColumn().getCwmColumn();
                if ((col == null || !tables.contains(col.getOwner()))
                        && !overriddenFor(e, List.of(b.getCwmAttribute()), tables)) {
                    error("E2", e, label(e) + ": '" + name(b.getCwmAttribute()) + "' from " + label(ms)
                            + " has no column in " + name(OrmBindings.tableOf(e))
                            + " — AttributeOverride missing.");
                }
            }
            for (Embedded emb : ms.getEmbedded()) {
                Paths open = openPaths(OrmBindings.embeddableOf(emb), tables, new ArrayList<>());
                for (List<StructuralFeature> p : open.attributes) {
                    if (!hasPathIn(emb.getAttributeOverride(), p, tables)) {
                        List<StructuralFeature> full = prefixed(emb.getCwmAttribute(), p);
                        if (!overriddenFor(e, full, tables)) {
                            error("E2", e, label(e) + ": '" + OrmBindings.path(full) + "' from " + label(ms)
                                    + " has no column — AttributeOverride missing.");
                        }
                    }
                }
            }
            for (AttributeMapping m : OrmBindings.mappings(ms)) {
                if (m instanceof RelationshipMapping rm && isOwningSide(rm)
                        && (connections(rm) == 0 || joinOutside(rm, tables))
                        && !hasAssociationPath(e.getAssociationOverride(), List.of(rm.getCwmAssociationEnd()))) {
                    error("E2", e, label(e) + ": the relationship '" + name(rm.getCwmAssociationEnd())
                            + "' from " + label(ms) + " has no join — AssociationOverride missing.");
                }
            }
        }

        /** Whether the join columns of a to-one relationship lie outside {@code tables}. */
        private static boolean joinOutside(RelationshipMapping rm, Set<NamedColumnSet> tables) {
            Join join = rm instanceof ManyToOne mo ? mo.getJoin() : rm instanceof OneToOne oo ? oo.getJoin() : null;
            NamedColumnSet owner = join == null ? null : OrmBindings.tableOf(join);
            return owner != null && !tables.contains(owner);
        }

        private record Paths(List<List<StructuralFeature>> attributes, List<List<StructuralFeature>> associations) {
        }

        /** The paths of an embeddable without a column in {@code tables}, and its relationships without a join. */
        private Paths openPaths(Embeddable emb, Set<NamedColumnSet> tables, List<Embeddable> visiting) {
            Paths paths = new Paths(new ArrayList<>(), new ArrayList<>());
            if (emb == null || visiting.contains(emb)) {
                return paths;
            }
            visiting.add(emb);
            for (Basic b : emb.getBasic()) {
                var col = b.getColumn() == null ? null : b.getColumn().getCwmColumn();
                if (col == null || !tables.contains(col.getOwner())) {
                    paths.attributes.add(List.of(b.getCwmAttribute()));
                }
            }
            for (Embedded inner : emb.getEmbedded()) {
                Paths deeper = openPaths(OrmBindings.embeddableOf(inner), tables, visiting);
                for (List<StructuralFeature> p : deeper.attributes) {
                    if (!hasPath(inner.getAttributeOverride(), p)) {
                        paths.attributes.add(prefixed(inner.getCwmAttribute(), p));
                    }
                }
                for (List<StructuralFeature> p : deeper.associations) {
                    if (!hasAssociationPath(inner.getAssociationOverride(), p)) {
                        paths.associations.add(prefixed(inner.getCwmAttribute(), p));
                    }
                }
            }
            for (AttributeMapping m : OrmBindings.mappings(emb)) {
                if (m instanceof RelationshipMapping rm && isOwningSide(rm) && connections(rm) == 0) {
                    paths.associations.add(List.of(rm.getCwmAssociationEnd()));
                }
            }
            visiting.remove(emb);
            return paths;
        }

        private static List<StructuralFeature> prefixed(StructuralFeature head, List<StructuralFeature> tail) {
            List<StructuralFeature> p = new ArrayList<>();
            p.add(head);
            p.addAll(tail);
            return p;
        }

        private static boolean hasPath(List<AttributeOverride> overrides, List<? extends StructuralFeature> path) {
            return overrides.stream().anyMatch(o -> o.getCwmPath().equals(path));
        }

        /**
         * Whether {@code e} overrides the path itself or an entity superclass places it on a column in
         * {@code tables} (in {@code TABLE_PER_CLASS} the subclass inherits that if the column lies in its
         * own table).
         */
        private static boolean overriddenFor(Entity e, List<? extends StructuralFeature> path,
                Set<NamedColumnSet> tables) {
            if (hasPath(e.getAttributeOverride(), path)) {
                return true;
            }
            for (ManagedType t : OrmBindings.lineage(e)) {
                if (t != e && t instanceof Entity above && hasPathIn(above.getAttributeOverride(), path, tables)) {
                    return true;
                }
            }
            return false;
        }

        /** An override for {@code path} whose column lies in {@code tables}. */
        private static boolean hasPathIn(List<AttributeOverride> overrides, List<? extends StructuralFeature> path,
                Set<NamedColumnSet> tables) {
            return overrides.stream().anyMatch(o -> o.getCwmPath().equals(path) && o.getColumn() != null
                    && o.getColumn().getCwmColumn() != null && tables.contains(o.getColumn().getCwmColumn().getOwner()));
        }

        private static boolean hasAssociationPath(List<AssociationOverride> overrides,
                List<? extends StructuralFeature> path) {
            return overrides.stream().anyMatch(o -> o.getCwmPath().equals(path));
        }

        private void tables() {
            for (Entity e : em.getEntity()) {
                Set<NamedColumnSet> tables = tables(e);
                if (tables.isEmpty()) {
                    continue;
                }
                for (Base b : bases(e)) {
                    inTables(e, b, b.getColumn() == null ? null : b.getColumn().getCwmColumn(), tables);
                    nullability(b, b.getColumn() == null ? null : b.getColumn().getCwmColumn());
                }
                for (AttributeOverride ao : e.getAttributeOverride()) {
                    inTables(e, ao, ao.getColumn() == null ? null : ao.getColumn().getCwmColumn(), tables);
                }
                for (AttributeMapping m : OrmBindings.mappings(e)) {
                    if (m instanceof Embedded || m instanceof EmbeddedId) {
                        for (AttributeOverride ao : ((OverrideHost) m).getAttributeOverride()) {
                            inTables(e, ao, ao.getColumn() == null ? null : ao.getColumn().getCwmColumn(), tables);
                        }
                    }
                    Join join = m instanceof ManyToOne mto ? mto.getJoin() : m instanceof OneToOne oto ? oto.getJoin() : null;
                    if (join != null) {
                        joinInTables(e, m, join, tables);
                        for (JoinPair p : OrmBindings.joinColumns(join)) {
                            nullability(m, p.column());
                        }
                    }
                }
                for (AssociationOverride ao : e.getAssociationOverride()) {
                    if (ao.getJoin() != null) {
                        joinInTables(e, ao, ao.getJoin(), tables);
                    }
                }
            }
        }

        private void inTables(Entity e, EObject where,
                org.eclipse.daanse.cwm.model.cwm.resource.relational.Column col, Set<NamedColumnSet> tables) {
            if (col != null && !tables.contains(col.getOwner())) {
                error("E3", where, label(where) + ": the column " + qualified(col) + " does not lie in a "
                        + "table of " + label(e) + ".");
            }
        }

        private void joinInTables(Entity e, EObject where, Join join, Set<NamedColumnSet> tables) {
            NamedColumnSet t = OrmBindings.tableOf(join);
            if (t != null && !tables.contains(t)) {
                error("E3", where, label(where) + ": the join columns lie in " + name(t) + ", not in a "
                        + "table of " + label(e) + ".");
            }
        }

        /** Warns when the nullability of the column and the lower bound of the feature disagree. */
        private void nullability(AttributeMapping m, org.eclipse.daanse.cwm.model.cwm.resource.relational.Column col) {
            StructuralFeature f = OrmBindings.featureOf(m);
            if (col == null || f == null || f.getMultiplicity() == null || f.getMultiplicity().getRange().isEmpty()
                    || m instanceof Id) {
                return;
            }
            boolean optional = OrmBindings.optional(m);
            if (!optional && col.getIsNullable() == NullableType.COLUMN_NULLABLE) {
                warning("E3", m, label(m) + ": required feature, but the column " + qualified(col) + " is nullable.");
            } else if (optional && col.eIsSet(RelationalPackage.Literals.COLUMN__IS_NULLABLE)
                    && col.getIsNullable() == NullableType.COLUMN_NO_NULLS) {
                warning("E3", m, label(m) + ": optional feature, but the column " + qualified(col)
                        + " is NOT NULL.");
            }
        }

        private void joins() {
            for (Iterator<EObject> it = em.eAllContents(); it.hasNext();) {
                if (!(it.next() instanceof Join j)) {
                    continue;
                }
                boolean fk = j.getCwmForeignKey() != null;
                if (fk == !j.getJoinColumn().isEmpty()) {
                    error("E5", j, label(j) + ": either the foreign key or column pairs, "
                            + (fk ? "not both." : "but has neither."));
                }
                List<JoinPair> pairs = OrmBindings.joinColumns(j);
                Set<EObject> owners = new LinkedHashSet<>();
                Set<EObject> refOwners = new LinkedHashSet<>();
                for (JoinPair p : pairs) {
                    owners.add(p.column().getOwner());
                    if (p.referenced() == null) {
                        error("E5", j, label(j) + ": " + qualified(p.column()) + " has no referenced column.");
                    } else {
                        refOwners.add(p.referenced().getOwner());
                    }
                }
                if (fk && j.getCwmForeignKey().getUniqueKey() != null
                        && j.getCwmForeignKey().getUniqueKey().getFeature().size() != j.getCwmForeignKey().getFeature().size()) {
                    error("E5", j, label(j) + ": the foreign key has a different column count than the key it "
                            + "references.");
                }
                if (owners.size() > 1) {
                    error("E5", j, label(j) + ": the join columns lie in several tables.");
                }
                if (refOwners.size() > 1) {
                    error("E5", j, label(j) + ": the referenced columns lie in several tables.");
                }
                target(j);
            }
        }

        /** The referenced table of a join matches its place in the mapping. */
        private void target(Join j) {
            NamedColumnSet ref = OrmBindings.referencedTableOf(j);
            NamedColumnSet own = OrmBindings.tableOf(j);
            EObject c = j.eContainer();
            Set<NamedColumnSet> expectedRef = null;
            Set<NamedColumnSet> expectedOwn = null;
            if (c instanceof SecondaryTable st && st.eContainer() instanceof Entity e) {
                expectedRef = Set.of(OrmBindings.tableOf(e));
            } else if (c instanceof CollectionTable ct && ct.eContainer() instanceof ElementCollection ec
                    && ec.eContainer() instanceof Entity e) {
                expectedRef = tables(e);
            } else if (c instanceof JoinTable jt) {
                if (jt.getJoin() != null && jt.getInverseJoin() != null
                        && OrmBindings.tableOf(jt.getJoin()) != OrmBindings.tableOf(jt.getInverseJoin())) {
                    if (j == jt.getJoin()) {
                        error("E5", jt, label(jt) + ": join and inverse join lie in different tables.");
                    }
                }
                Entity owner = hostEntity(jt.eContainer());
                AssociationEnd end = endOf(jt.eContainer());
                if (j == jt.getJoin() && owner != null) {
                    expectedRef = lineageTables(owner);
                } else if (j == jt.getInverseJoin() && end != null) {
                    expectedRef = targetTables(end);
                }
            } else if (c instanceof Entity e && j == e.getPrimaryKeyJoin()) {
                expectedOwn = e.getCwmTable() == null ? null : Set.of(e.getCwmTable());
                ManagedType sup = superEntity(e);
                expectedRef = sup == null ? null : lineageTables(sup);
            } else if (c instanceof OneToMany otm && otm.eContainer() instanceof Entity e) {
                expectedOwn = targetTables(otm.getCwmAssociationEnd());
                expectedRef = lineageTables(e);
            } else if (c instanceof ManyToOne || c instanceof OneToOne) {
                expectedRef = targetTables(((RelationshipMapping) c).getCwmAssociationEnd());
            } else if (c instanceof AssociationOverride ao) {
                expectedRef = targetTables(endOf(ao));
            }
            if (expectedRef != null && !expectedRef.isEmpty() && ref != null && !expectedRef.contains(ref)) {
                error("E5", j, label(j) + ": the referenced columns lie in " + name(ref)
                        + ", expected " + names(expectedRef) + ".");
            }
            if (expectedOwn != null && !expectedOwn.isEmpty() && own != null && !expectedOwn.contains(own)) {
                error("E5", j, label(j) + ": the join columns lie in " + name(own) + ", expected "
                        + names(expectedOwn) + ".");
            }
        }

        private Entity hostEntity(EObject o) {
            for (EObject c = o; c != null; c = c.eContainer()) {
                if (c instanceof Entity e) {
                    return e;
                }
                if (c instanceof ManagedType) {
                    return null;
                }
            }
            return null;
        }

        private static AssociationEnd endOf(EObject o) {
            if (o instanceof RelationshipMapping rm) {
                return rm.getCwmAssociationEnd();
            }
            if (o instanceof AssociationOverride ao && !ao.getCwmPath().isEmpty()
                    && ao.getCwmPath().get(ao.getCwmPath().size() - 1) instanceof AssociationEnd end) {
                return end;
            }
            return null;
        }

        private Set<NamedColumnSet> targetTables(AssociationEnd end) {
            ManagedType target = end == null ? null : byClass.get(end.getType());
            return target == null ? null : lineageTables(target);
        }

        private void inheritance() {
            for (Entity e : em.getEntity()) {
                Entity sup = superEntity(e);
                Entity root = rootEntity(e);
                boolean sub = sup != null;
                boolean hasSubs = em.getEntity().stream().anyMatch(o -> superEntity(o) == e);
                boolean concrete = e.getCwmClass() != null && !e.getCwmClass().isIsAbstract();
                if (!sub) {
                    if (hasSubs && !e.isSetInheritanceStrategy()) {
                        error("E6", e, label(e) + ": root of a hierarchy without inheritanceStrategy.");
                    }
                    // an abstract TABLE_PER_CLASS root is the one entity without a table
                    boolean abstractTablePerClassRoot = !concrete && hasSubs && e.isSetInheritanceStrategy()
                            && e.getInheritanceStrategy() == InheritanceType.TABLEPERCLASS;
                    if (e.getCwmTable() == null && !abstractTablePerClassRoot) {
                        error("E6", e, label(e) + ": an entity without an entity superclass needs its table.");
                    }
                } else {
                    if (e.isSetInheritanceStrategy()) {
                        error("E6", e, label(e) + ": the strategy is stated only at the root " + label(root) + ".");
                    }
                    if (e.getCwmDiscriminatorColumn() != null) {
                        error("E6", e, label(e) + ": the discriminator column is stated only at the root "
                                + label(root) + ".");
                    }
                }
                if (!sub && !hasSubs) {
                    continue;
                }
                InheritanceType strategy = root.isSetInheritanceStrategy() ? root.getInheritanceStrategy() : null;
                if (strategy == InheritanceType.SINGLETABLE || strategy == InheritanceType.JOINED) {
                    if (!sub && e.getCwmDiscriminatorColumn() == null) {
                        error("E6", e, label(e) + ": " + strategy.getLiteral() + " needs the "
                                + "discriminator column at the root.");
                    }
                    if (concrete && e.getDiscriminatorValue() == null) {
                        error("E6", e, label(e) + ": a concrete entity of the hierarchy needs its "
                                + "discriminator value.");
                    }
                }
                if (sub && strategy == InheritanceType.JOINED) {
                    if (e.getCwmTable() == null || e.getPrimaryKeyJoin() == null) {
                        error("E6", e, label(e) + ": a JOINED subclass needs a table of its own and "
                                + "primaryKeyJoin.");
                    }
                } else if (e.getPrimaryKeyJoin() != null) {
                    error("E6", e, label(e) + ": primaryKeyJoin exists only in a JOINED subclass.");
                }
                if (strategy == InheritanceType.TABLEPERCLASS && concrete && e.getCwmTable() == null) {
                    error("E6", e, label(e) + ": TABLE_PER_CLASS needs a table of its own per concrete "
                            + "entity.");
                }
                if (sub && strategy == InheritanceType.SINGLETABLE && e.getCwmTable() != null
                        && e.getCwmTable() != root.getCwmTable()) {
                    error("E6", e, label(e) + ": a SINGLE_TABLE subclass lies in the table of the root.");
                }
            }
        }

        private Entity superEntity(Entity e) {
            for (ManagedType t : OrmBindings.lineage(e)) {
                if (t != e && t instanceof Entity s) {
                    return s;
                }
            }
            return null;
        }

        private Entity rootEntity(Entity e) {
            Entity root = e;
            for (Entity s = superEntity(e); s != null && s != e; s = superEntity(s)) {
                root = s;
            }
            return root;
        }

        private void doubleColumns() {
            for (Entity e : em.getEntity()) {
                Map<org.eclipse.daanse.cwm.model.cwm.resource.relational.Column, List<Boolean>> uses = new LinkedHashMap<>();
                for (Base b : bases(e)) {
                    use(uses, b.getColumn());
                }
                for (AttributeOverride ao : e.getAttributeOverride()) {
                    use(uses, ao.getColumn());
                }
                for (AttributeMapping m : OrmBindings.mappings(e)) {
                    if (m instanceof Embedded || m instanceof EmbeddedId) {
                        for (AttributeOverride ao : ((OverrideHost) m).getAttributeOverride()) {
                            use(uses, ao.getColumn());
                        }
                    }
                    if (m instanceof ManyToOne mto) {
                        use(uses, mto.getJoin());
                    } else if (m instanceof OneToOne oto) {
                        use(uses, oto.getJoin());
                    }
                }
                for (AssociationOverride ao : e.getAssociationOverride()) {
                    use(uses, ao.getJoin());
                }
                for (MappedSuperclass ms : mappedSuperclassesAbove(e)) {
                    for (Base b : bases(ms)) {
                        if (b.getColumn() != null && !hasPath(e.getAttributeOverride(), List.of(b.getCwmAttribute()))) {
                            use(uses, b.getColumn());
                        }
                    }
                }
                if (e.getCwmDiscriminatorColumn() != null) {
                    uses.computeIfAbsent(e.getCwmDiscriminatorColumn(), k -> new ArrayList<>()).add(true);
                }
                for (var entry : uses.entrySet()) {
                    long writers = entry.getValue().stream().filter(Boolean::booleanValue).count();
                    if (entry.getValue().size() > 1 && writers > 1) {
                        error("E7", e, label(e) + ": the column " + qualified(entry.getKey()) + " is used "
                                + entry.getValue().size() + " times, " + writers + " of them writing — "
                                + "at most one use may be insertable/updatable.");
                    }
                }
            }
        }

        private static void use(Map<org.eclipse.daanse.cwm.model.cwm.resource.relational.Column, List<Boolean>> uses,
                org.eclipse.daanse.cwm.model.daanse.orm.Column c) {
            if (c != null && c.getCwmColumn() != null) {
                boolean writes = !c.isSetInsertable() || c.isInsertable() || !c.isSetUpdatable() || c.isUpdatable();
                uses.computeIfAbsent(c.getCwmColumn(), k -> new ArrayList<>()).add(writes);
            }
        }

        private static void use(Map<org.eclipse.daanse.cwm.model.cwm.resource.relational.Column, List<Boolean>> uses,
                Join j) {
            if (j == null) {
                return;
            }
            boolean writes = !j.isSetInsertable() || j.isInsertable() || !j.isSetUpdatable() || j.isUpdatable();
            for (JoinPair p : OrmBindings.joinColumns(j)) {
                uses.computeIfAbsent(p.column(), k -> new ArrayList<>()).add(writes);
            }
        }

        private void identity() {
            for (Entity e : em.getEntity()) {
                List<Id> ids = new ArrayList<>();
                List<EmbeddedId> embeddedIds = new ArrayList<>();
                List<RelationshipMapping> relIds = new ArrayList<>();
                List<RelationshipMapping> mapsIds = new ArrayList<>();
                Classifier idClass = null;
                for (ManagedType t : OrmBindings.lineage(e)) {
                    if (!(t instanceof IdentifiableType it)) {
                        continue;
                    }
                    ids.addAll(it.getId());
                    if (it.getEmbeddedId() != null) {
                        embeddedIds.add(it.getEmbeddedId());
                    }
                    if (idClass == null) {
                        idClass = it.getCwmIdClass();
                    }
                    for (ManyToOne m : it.getManyToOne()) {
                        collectDerived(m, m.isId(), m.isMapsId(), m.getCwmMapsIdAttribute(), relIds, mapsIds);
                    }
                    for (OneToOne m : it.getOneToOne()) {
                        collectDerived(m, m.isId(), m.isMapsId(), m.getCwmMapsIdAttribute(), relIds, mapsIds);
                    }
                }
                int idCount = ids.size() + relIds.size();
                if (embeddedIds.size() > 1 || (idCount > 0) == (embeddedIds.size() == 1)) {
                    error("E8", e, label(e) + ": needs at least one Id or exactly one EmbeddedId in its "
                            + "lineage (has " + idCount + " Ids, " + embeddedIds.size() + " EmbeddedIds).");
                }
                if (idCount > 1 && idClass == null) {
                    error("E8", e, label(e) + ": " + idCount + " id attributes need cwmIdClass.");
                } else if (idClass != null && idCount <= 1) {
                    error("E8", e, label(e) + ": cwmIdClass only with more than one id.");
                } else if (idClass != null) {
                    idClassMatches(e, idClass, ids, relIds);
                }
                primaryKeyColumns(e);
                for (RelationshipMapping m : mapsIds) {
                    Attribute part = m instanceof ManyToOne mto ? mto.getCwmMapsIdAttribute()
                            : ((OneToOne) m).getCwmMapsIdAttribute();
                    if (embeddedIds.isEmpty() && part != null) {
                        error("E8", m, label(m) + ": mapsId with an attribute needs an EmbeddedId.");
                    } else if (part != null) {
                        Classifier keyClass = embeddedIds.get(0).getCwmAttribute() == null ? null
                                : embeddedIds.get(0).getCwmAttribute().getType();
                        if (keyClass == null || part.getOwner() != keyClass) {
                            error("E8", m, label(m) + ": '" + name(part) + "' is not an attribute of the "
                                    + "EmbeddedId class " + name(keyClass) + ".");
                        }
                    }
                }
            }
        }

        private void collectDerived(RelationshipMapping m, boolean id, boolean mapsId, Attribute part,
                List<RelationshipMapping> relIds, List<RelationshipMapping> mapsIds) {
            if (id) {
                relIds.add(m);
            }
            if (mapsId) {
                mapsIds.add(m);
            } else if (part != null) {
                error("E8", m, label(m) + ": cwmMapsIdAttribute without mapsId.");
            }
        }

        private void idClassMatches(Entity e, Classifier idClass, List<Id> ids, List<RelationshipMapping> relIds) {
            List<String> idNames = new ArrayList<>();
            for (var f : idClass.getFeature()) {
                if (!(f instanceof Attribute a)) {
                    continue;
                }
                idNames.add(a.getName());
                boolean match = ids.stream().anyMatch(i -> i.getCwmAttribute() != null
                        && a.getName() != null && a.getName().equals(i.getCwmAttribute().getName())
                        && a.getType() == i.getCwmAttribute().getType())
                        || relIds.stream().anyMatch(r -> a.getName() != null
                                && a.getName().equals(name(r.getCwmAssociationEnd())));
                if (!match) {
                    error("E8", e, label(e) + ": the attribute '" + a.getName() + "' of the id class "
                            + name(idClass) + " has no Id mapping of the same name and type.");
                }
            }
            for (Id i : ids) {
                if (!idNames.contains(name(i.getCwmAttribute()))) {
                    error("E8", i, label(i) + ": missing in the id class " + name(idClass) + ".");
                }
            }
        }

        /** The own id columns of an entity lie in the primary key of its table. */
        private void primaryKeyColumns(Entity e) {
            PrimaryKey pk = OrmBindings.primaryKey(e);
            if (pk == null) {
                return;
            }
            List<org.eclipse.daanse.cwm.model.cwm.resource.relational.Column> idColumns = new ArrayList<>();
            for (Id i : e.getId()) {
                if (i.getColumn() != null) {
                    idColumns.add(i.getColumn().getCwmColumn());
                }
            }
            if (e.getEmbeddedId() != null) {
                for (AttributeOverride ao : e.getEmbeddedId().getAttributeOverride()) {
                    if (ao.getColumn() != null) {
                        idColumns.add(ao.getColumn().getCwmColumn());
                    }
                }
            }
            for (ManagedType t : OrmBindings.lineage(e)) {
                if (t instanceof MappedSuperclass ms) {
                    for (Id i : ms.getId()) {
                        for (AttributeOverride ao : e.getAttributeOverride()) {
                            if (ao.getCwmPath().equals(List.of(i.getCwmAttribute())) && ao.getColumn() != null) {
                                idColumns.add(ao.getColumn().getCwmColumn());
                            }
                        }
                    }
                }
            }
            for (var col : idColumns) {
                if (col != null && !pk.getFeature().contains(col)) {
                    error("E8", e, label(e) + ": the id column " + qualified(col) + " does not belong to the "
                            + "primary key " + name(pk) + ".");
                }
            }
        }

        private void consistency() {
            for (Iterator<EObject> it = em.eAllContents(); it.hasNext();) {
                EObject o = it.next();
                if (o instanceof GeneratedValue gv) {
                    generatedValue(gv);
                } else if (o instanceof Convert c) {
                    if ((c.getConverter() != null) == (c.isSetDisableConversion() && c.isDisableConversion())) {
                        error("E9", c, label(c) + ": either a converter or disableConversion.");
                    }
                    if (c.eContainer() instanceof Basic && !c.getCwmPath().isEmpty()) {
                        error("E9", c, label(c) + ": on a Basic the conversion applies without a path.");
                    } else if (c.eContainer() instanceof OverrideHost host) {
                        path(c, host, c.getCwmPath(), false, null);
                    }
                } else if (o instanceof AttributeOverride ao && ao.eContainer() instanceof OverrideHost host) {
                    path(ao, host, ao.getCwmPath(), true, Attribute.class);
                } else if (o instanceof AssociationOverride ao && ao.eContainer() instanceof OverrideHost host) {
                    path(ao, host, ao.getCwmPath(), true, AssociationEnd.class);
                } else if (o instanceof CollectionSupport cs && !cs.getOrderBy().isEmpty()
                        && cs.getOrderColumn() != null) {
                    error("E9", o, label(o) + ": either orderBy (ordering on load) or orderColumn "
                            + "(stored position), not both.");
                } else if (o instanceof Ordering ord && ord.eContainer() instanceof CollectionSupport cs) {
                    path(ord, targetType(cs), ord.getCwmPath(), true, Attribute.class);
                } else if (o instanceof NamedNativeQuery q) {
                    if ((q.getCwmResultClass() != null) == (q.getResultSetMapping() != null)) {
                        error("E9", q, label(q) + ": either cwmResultClass or resultSetMapping.");
                    }
                    resultColumns(q);
                }
            }
        }

        private void generatedValue(GeneratedValue gv) {
            if (!gv.isSetStrategy()) {
                error("E9", gv, label(gv) + ": the strategy is not set (no silent AUTO).");
                return;
            }
            GenerationType s = gv.getStrategy();
            Generator g = gv.getGenerator();
            boolean ok = switch (s) {
                case SEQUENCE -> g instanceof SequenceGenerator;
                case TABLE -> g instanceof TableGenerator;
                default -> g == null;
            };
            if (!ok) {
                error("E9", gv, label(gv) + ": strategy " + s.getLiteral() + " does not match the generator "
                        + (g == null ? "(none)" : label(g)) + ".");
            }
        }

        /** Every result column of the result set mapping is a column of the stored query. */
        private void resultColumns(NamedNativeQuery q) {
            SqlResultSetMapping rsm = q.getResultSetMapping();
            if (rsm == null || q.getCwmQuery() == null) {
                return;
            }
            List<org.eclipse.daanse.cwm.model.cwm.resource.relational.Column> columns = new ArrayList<>();
            for (EntityResult er : rsm.getEntityResult()) {
                er.getFieldResult().forEach(f -> columns.add(f.getCwmColumn()));
                if (er.getCwmDiscriminatorColumn() != null) {
                    columns.add(er.getCwmDiscriminatorColumn());
                }
            }
            rsm.getColumnResult().forEach(c -> columns.add(c.getCwmColumn()));
            rsm.getConstructorResult().forEach(cr -> cr.getColumnResult().forEach(c -> columns.add(c.getCwmColumn())));
            for (var col : columns) {
                if (col != null && col.getOwner() != q.getCwmQuery()) {
                    error("E9", rsm, label(rsm) + ": the result column " + qualified(col) + " does not belong to "
                            + "the stored query " + name(q.getCwmQuery()) + " of " + label(q) + ".");
                }
            }
        }

        /** The type an ordering points into: the target of the relationship or the element embeddable. */
        private static Classifier targetType(CollectionSupport cs) {
            if (cs instanceof RelationshipMapping rm) {
                return rm.getCwmAssociationEnd() == null ? null : rm.getCwmAssociationEnd().getType();
            }
            if (cs instanceof ValueMapping vm) {
                return vm.getCwmAttribute() == null ? null : vm.getCwmAttribute().getType();
            }
            return null;
        }

        /** A feature path starts at the type of the host and follows the types of its elements. */
        private void path(EObject where, OverrideHost host, List<StructuralFeature> path, boolean required,
                java.lang.Class<?> last) {
            path(where, hostType(host), path, required, last);
        }

        /** A feature path starts at {@code start} and follows the types of its elements. */
        private void path(EObject where, Classifier start, List<StructuralFeature> path, boolean required,
                java.lang.Class<?> last) {
            if (path.isEmpty()) {
                if (required) {
                    error("E9", where, label(where) + ": the path is empty.");
                }
                return;
            }
            Classifier type = start;
            for (StructuralFeature f : path) {
                if (type != null && !belongsTo(f, type, true)) {
                    error("E9", where, label(where) + ": '" + name(f) + "' in the path " + OrmBindings.path(path)
                            + " is not a feature of " + name(type) + ".");
                    return;
                }
                type = f.getType();
            }
            if (last != null && !last.isInstance(path.get(path.size() - 1))) {
                error("E9", where, label(where) + ": the path " + OrmBindings.path(path) + " does not end in "
                        + (last == Attribute.class ? "an attribute." : "an association end."));
            }
        }

        private static Classifier hostType(OverrideHost host) {
            if (host instanceof Entity e) {
                return e.getCwmClass();
            }
            if (host instanceof ValueMapping vm) {
                return vm.getCwmAttribute() == null ? null : vm.getCwmAttribute().getType();
            }
            if (host instanceof MapKey k) {
                return k.getCwmAttribute() != null ? k.getCwmAttribute().getType() : k.getCwmClassifier();
            }
            return null;
        }

        private void names() {
            for (Iterator<EObject> it = em.eAllContents(); it.hasNext();) {
                EObject o = it.next();
                String anchor = anchorName(o);
                if (anchor != null && o instanceof ModelElement me && !anchor.equals(me.getName())) {
                    error("E10", o, o.eClass().getName() + " '" + me.getName() + "': name differs from the "
                            + "CWM anchor '" + anchor + "'.");
                }
            }
            unique("Entity", new ArrayList<>(em.getEntity()));
            unique("Generator", new ArrayList<>(em.getGenerator()));
            List<ModelElement> queries = new ArrayList<>();
            queries.addAll(em.getNamedQuery());
            queries.addAll(em.getNamedNativeQuery());
            queries.addAll(em.getNamedStoredProcedureQuery());
            unique("Query", queries);
            unique("SqlResultSetMapping", new ArrayList<>(em.getSqlResultSetMapping()));
            List<ModelElement> graphs = new ArrayList<>();
            for (Entity e : em.getEntity()) {
                graphs.addAll(e.getNamedEntityGraph());
            }
            unique("NamedEntityGraph", graphs);
            unique("Converter", new ArrayList<>(em.getConverter()));
            unique("EntityListener", new ArrayList<>(em.getEntityListener()));
        }

        /** The name an anchored element must carry, otherwise {@code null}. */
        private static String anchorName(EObject o) {
            if (o instanceof ManagedType t && !(t instanceof Entity)) {
                return nameOrNull(t.getCwmClass());
            }
            if (o instanceof AttributeMapping m) {
                return nameOrNull(OrmBindings.featureOf(m));
            }
            if (o instanceof ColumnRef c) {
                return nameOrNull(c.getCwmColumn());
            }
            if (o instanceof Join j) {
                return nameOrNull(j.getCwmForeignKey());
            }
            if (o instanceof JoinedTable jt) {
                return nameOrNull(OrmBindings.tableOf(jt));
            }
            if (o instanceof AttributeOverride ao) {
                return ao.getCwmPath().isEmpty() ? null : OrmBindings.path(ao.getCwmPath());
            }
            if (o instanceof AssociationOverride ao) {
                return ao.getCwmPath().isEmpty() ? null : OrmBindings.path(ao.getCwmPath());
            }
            if (o instanceof Converter c) {
                return nameOrNull(c.getCwmClass());
            }
            if (o instanceof EntityListener l) {
                return nameOrNull(l.getCwmClass());
            }
            if (o instanceof LifecycleCallback c) {
                return nameOrNull(c.getCwmOperation());
            }
            if (o instanceof MapKey k) {
                return nameOrNull(k.getCwmAttribute() != null ? k.getCwmAttribute() : k.getCwmClassifier());
            }
            if (o instanceof Ordering ord) {
                return ord.getCwmPath().isEmpty() ? null : OrmBindings.path(ord.getCwmPath());
            }
            if (o instanceof ColumnResult r) {
                return nameOrNull(r.getCwmColumn());
            }
            if (o instanceof NamedAttributeNode n) {
                return nameOrNull(n.getCwmFeature());
            }
            if (o instanceof FieldResult f) {
                return nameOrNull(f.getCwmFeature());
            }
            if (o instanceof EntityResult r) {
                return nameOrNull(r.getCwmClass());
            }
            if (o instanceof ConstructorResult r) {
                return nameOrNull(r.getCwmClass());
            }
            return null;
        }

        private void unique(String what, List<? extends ModelElement> elements) {
            Map<String, Integer> seen = new HashMap<>();
            for (ModelElement me : elements) {
                if (me.getName() != null && seen.merge(me.getName(), 1, Integer::sum) == 2) {
                    error("E10", me, what + " name '" + me.getName() + "' is not unique in the mapping set '"
                            + name(em) + "'.");
                }
            }
        }

        private void unmappedColumns() {
            Set<org.eclipse.daanse.cwm.model.cwm.resource.relational.Column> used = new LinkedHashSet<>();
            Set<NamedColumnSet> tables = new LinkedHashSet<>();
            for (Iterator<EObject> it = em.eAllContents(); it.hasNext();) {
                EObject o = it.next();
                if (o instanceof ColumnRef c && c.getCwmColumn() != null) {
                    used.add(c.getCwmColumn());
                } else if (o instanceof Join j) {
                    OrmBindings.joinColumns(j).forEach(p -> used.add(p.column()));
                } else if (o instanceof Entity e) {
                    if (e.getCwmTable() != null) {
                        tables.add(e.getCwmTable());
                    }
                    if (e.getCwmDiscriminatorColumn() != null) {
                        used.add(e.getCwmDiscriminatorColumn());
                    }
                }
                if (o instanceof JoinedTable jt && OrmBindings.tableOf(jt) != null) {
                    tables.add(OrmBindings.tableOf(jt));
                }
            }
            for (NamedColumnSet t : tables) {
                for (var f : t.getFeature()) {
                    if (f instanceof org.eclipse.daanse.cwm.model.cwm.resource.relational.Column c && !used.contains(c)) {
                        warning("E11", c, "Column " + qualified(c) + " is not used by any mapping "
                                + "(unmapped column).");
                    }
                }
            }
        }

        /** The tables of an entity: the primary table (for SINGLE_TABLE the root's) and the secondary tables. */
        private static Set<NamedColumnSet> tables(Entity e) {
            Set<NamedColumnSet> out = new LinkedHashSet<>();
            NamedColumnSet t = OrmBindings.tableOf(e);
            if (t != null) {
                out.add(t);
            }
            for (SecondaryTable st : e.getSecondaryTable()) {
                if (OrmBindings.tableOf(st) != null) {
                    out.add(OrmBindings.tableOf(st));
                }
            }
            return out;
        }

        /** The tables of a type and its mapped supertypes (target of a reference in JOINED hierarchies). */
        private static Set<NamedColumnSet> lineageTables(ManagedType mt) {
            Set<NamedColumnSet> out = new LinkedHashSet<>();
            for (ManagedType t : OrmBindings.lineage(mt)) {
                if (t instanceof Entity e) {
                    out.addAll(tables(e));
                }
            }
            return out;
        }

        /** The mapped superclasses between an entity and its nearest entity superclass. */
        private static List<MappedSuperclass> mappedSuperclassesAbove(Entity e) {
            List<MappedSuperclass> out = new ArrayList<>();
            for (ManagedType t : OrmBindings.lineage(e)) {
                if (t == e) {
                    continue;
                }
                if (t instanceof Entity) {
                    break;
                }
                if (t instanceof MappedSuperclass ms) {
                    out.add(ms);
                }
            }
            return out;
        }

        private static List<Base> bases(ManagedType t) {
            List<Base> out = new ArrayList<>();
            if (t instanceof IdentifiableType it) {
                out.addAll(it.getId());
                out.addAll(it.getVersion());
            }
            out.addAll(t.getBasic());
            return out;
        }

        /** Whether {@code f} is a feature of {@code c} (or, with {@code inherited}, of a superclass). */
        private static boolean belongsTo(StructuralFeature f, Classifier c, boolean inherited) {
            List<Classifier> owners = inherited ? OrmBindings.ancestors(c) : List.of(c);
            if (f instanceof AssociationEnd end) {
                AssociationEnd other = otherEnd(end);
                return other != null && owners.contains(other.getType());
            }
            return owners.contains(f.getOwner());
        }

        private static AssociationEnd otherEnd(AssociationEnd end) {
            if (end.getOwner() instanceof Association a) {
                for (AssociationEnd e : ends(a)) {
                    if (e != end) {
                        return e;
                    }
                }
            }
            return null;
        }

        private static List<AssociationEnd> ends(Association a) {
            List<AssociationEnd> out = new ArrayList<>();
            for (var f : a.getFeature()) {
                if (f instanceof AssociationEnd e) {
                    out.add(e);
                }
            }
            return out;
        }

        private void error(String rule, EObject element, String message) {
            out.add(new Finding(Severity.ERROR, rule, element, message));
        }

        private void warning(String rule, EObject element, String message) {
            out.add(new Finding(Severity.WARNING, rule, element, message));
        }

        private static String names(Set<NamedColumnSet> tables) {
            return tables.stream().map(OrmModelValidator::name).toList().toString();
        }
    }

    private static boolean isOwningSide(RelationshipMapping rm) {
        if (rm instanceof OwnableRelationship or) {
            return !or.isSetOwningSide() || or.isOwningSide();
        }
        // ManyToOne is always the owning side.
        return true;
    }

    /** {@code Class 'name'}; for attribute mappings followed by {@code in} and the mapped type. */
    private static String label(EObject o) {
        String n = o instanceof ModelElement me && me.getName() != null ? me.getName() : "?";
        String s = o.eClass().getName() + " '" + n + "'";
        if (o instanceof AttributeMapping && o.eContainer() instanceof ManagedType t) {
            s += " in " + n(t);
        }
        return s;
    }

    private static String n(ManagedType t) {
        return t.getName() != null ? t.getName() : name(t.getCwmClass());
    }

    private static String qualified(org.eclipse.daanse.cwm.model.cwm.resource.relational.Column c) {
        return c.getOwner() == null ? name(c) : name(c.getOwner()) + "." + name(c);
    }

    private static String name(ModelElement me) {
        return me == null || me.getName() == null ? "?" : me.getName();
    }

    private static String nameOrNull(ModelElement me) {
        return me == null ? null : me.getName();
    }
}
