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
 * Programmatische Prüfung der Modell-Invarianten für das {@code orm}-Metamodell — das
 * Explizit-Profil.
 *
 * <p>Das Modell selbst ist bewusst OCL-frei (plain mapping model) und hält Pflichten, die vom
 * Kontext abhängen, nicht als {@code lowerBound}. Diese Klasse prüft sie: was JPA still ergänzen
 * würde (jedes Attribut persistent, Spalte = Attributname, Join-Spalten und Join-Tabellen nach
 * Namensregel, {@code SINGLE_TABLE}, {@code AUTO}), muss im Modell ausdrücklich stehen.</p>
 *
 * <ul>
 * <li>{@code MULTIPLICITY} — To-One-Mappings auf Enden mit Obergrenze 1, To-Many auf mehr.</li>
 * <li>{@code OWNING_SIDE} — genau eine Owning-Side je bidirektional gemappter Association.</li>
 * <li>{@code E1} Vollständigkeit — jedes eigene Attribut und jedes navigierbare Ende der Klasse hat
 * genau ein Mapping (notfalls {@code Transient}); Mappings fremder Features sind Fehler.</li>
 * <li>{@code E2} Spalte — Basic/Id/Version in Entities nennen ihre Spalte; was eine Mapped
 * Superclass oder ein Embeddable offen lässt oder in eine fremde Tabelle legt, überschreibt jeder
 * Nutzer; in {@code TABLE_PER_CLASS} gilt das für alles Geerbte, auch von Ober-Entities, denn jede
 * konkrete Klasse hat ihre Spalten in der eigenen Tabelle; eine Element Collection hat ihre
 * Collection Table.</li>
 * <li>{@code E3} Tabelle — Spalten einer Entity liegen in ihrer Tabelle oder einer Secondary Table;
 * Nullbarkeit und Untergrenze passen zusammen (Warnung).</li>
 * <li>{@code E4} Beziehungen — die Owning-Side hat genau eine Verbindung (Join, Join Table,
 * Primary-Key-Join, mapsId), die inverse Seite keine und ein gemapptes Gegenende; das Ziel ist
 * eine Entity.</li>
 * <li>{@code E5} Join — Fremdschlüssel xor Spaltenpaare; Spalten in einer Tabelle, Referenzen in
 * der Zieltabelle; Join und Inverse-Join einer Join Table in derselben Tabelle.</li>
 * <li>{@code E6} Vererbung — Strategie an der Wurzel, Diskriminator-Spalte und -Werte, eigene
 * Tabellen und Primary-Key-Join je Strategie.</li>
 * <li>{@code E7} doppelt genutzte Spalte — höchstens eine schreibende Nutzung je Spalte einer
 * Entity.</li>
 * <li>{@code E8} Identität — Id xor Embedded Id, Id-Klasse genau bei mehreren Ids und passend,
 * Id-Spalten im Primärschlüssel, mapsId auf Attribute der Embedded-Id-Klasse.</li>
 * <li>{@code E9} Konsistenz — Generierungsstrategie und Generator, Konverter xor Abschaltung,
 * Ergebnisklasse xor Ergebnisabbildung, orderBy xor orderColumn, Ergebnisspalten aus der
 * gespeicherten Abfrage, gültige Feature-Pfade (Overrides, Konvertierungen, Sortierungen).</li>
 * <li>{@code E10} Namen — Name = Ankername bei verankerten Elementen; Namen der Unit-weiten
 * Definitionen eindeutig.</li>
 * <li>{@code E11} Gegenrichtung (Warnung) — Spalten gemappter Tabellen, die kein Mapping nutzt.</li>
 * </ul>
 */
public final class OrmModelValidator {

    private OrmModelValidator() {
    }

    /** Schwere eines Befunds; nur {@link #ERROR} macht ein Mapping ungültig. */
    public enum Severity {
        ERROR, WARNING
    }

    /**
     * Ein Befund.
     *
     * @param severity Schwere
     * @param rule     Regel, z. B. {@code E1} oder {@code OWNING_SIDE}
     * @param element  das betroffene Modellelement
     * @param message  menschenlesbare Beschreibung
     */
    public record Finding(Severity severity, String rule, EObject element, String message) {

        @Override
        public String toString() {
            return severity + " " + rule + ": " + message;
        }
    }

    /** Prüft alle {@link EntityMappings} in {@code root} (einschließlich {@code root}) und liefert alle Befunde. */
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

    /** Die Fehler ({@link Severity#ERROR}) als Texte, Regel vorangestellt (leer ⇒ gültig). */
    public static List<String> validate(EObject root) {
        return check(root).stream().filter(f -> f.severity() == Severity.ERROR)
                .map(f -> f.rule() + " " + f.message()).toList();
    }

    /** Die Prüfung eines Mapping-Satzes. */
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

        // ---- E1 Vollständigkeit -----------------------------------------------------------

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
                    error("E1", m, label(m) + ": '" + name(f) + "' ist kein eigenes Feature der Klasse "
                            + name(c) + ".");
                } else if (f instanceof AssociationEnd end && !end.isIsNavigable()) {
                    error("E1", m, label(m) + ": das Ende '" + name(f) + "' ist nicht navigierbar.");
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
                    error("E1", t, label(t) + ": '" + name(f) + "' ist nicht gemappt (Mapping oder "
                            + "Transient fehlt).");
                } else if (n > 1) {
                    error("E1", t, label(t) + ": '" + name(f) + "' ist " + n + "-mal gemappt.");
                }
            }
        }

        // ---- MULTIPLICITY, OWNING_SIDE, E4 Beziehungen --------------------------------------

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
                            + mappings.size() + " Mappings, davon " + owners
                            + " Owning-Sides — genau eine Seite muss die Owning-Side sein.");
                }
            }
        }

        /** Ehemals CwmSingleTargetMultiplicity / CwmManyTargetMultiplicity. */
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
                    error("MULTIPLICITY", rm, label(rm) + ": Zielende '" + end.getName()
                            + "' hat obere Multiplizität " + upper + ", ein To-One-Mapping verlangt 1.");
                }
                if (toMany && upper == 1) {
                    error("MULTIPLICITY", rm, label(rm) + ": Zielende '" + end.getName()
                            + "' hat obere Multiplizität 1, ein To-Many-Mapping verlangt mehr als 1 "
                            + "(oder unbegrenzt).");
                }
            }
        }

        /** E4: die Verbindung der Owning-Side, keine an der inversen Seite, Ziel ist eine Entity. */
        private void connection(ManagedType owner, RelationshipMapping rm, AssociationEnd end) {
            if (!(byClass.get(end.getType()) instanceof Entity)) {
                error("E4", rm, label(rm) + ": das Ziel " + name(end.getType()) + " ist keine Entity "
                        + "dieses Mappings.");
            }
            int ways = connections(rm);
            if (isOwningSide(rm)) {
                if (owner instanceof Entity && ways != 1) {
                    error("E4", rm, label(rm) + ": die Owning-Side braucht genau eine Verbindung (join, "
                            + "joinTable, primaryKeyJoin oder mapsId), hat " + ways + ".");
                } else if (!(owner instanceof Entity) && ways > 1) {
                    error("E4", rm, label(rm) + ": höchstens eine Verbindung, hat " + ways + ".");
                }
                return;
            }
            if (ways > 0) {
                error("E4", rm, label(rm) + ": die inverse Seite trägt keine Verbindung; die Owning-Side "
                        + "hält sie.");
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
                error("E4", rm, label(rm) + ": das Gegenende '" + name(other) + "' ist nicht als "
                        + "Owning-Side gemappt.");
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

        // ---- E2 Spalte --------------------------------------------------------------------

        private void columns() {
            for (ManagedType t : types) {
                if (t instanceof Entity e) {
                    Set<NamedColumnSet> tables = tables(e);
                    for (Base b : bases(e)) {
                        if (b.getColumn() == null) {
                            error("E2", b, label(b) + ": eine Abbildung in einer Entity nennt ihre Spalte.");
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
                        error("E2", ec, label(ec) + ": eine Element Collection nennt ihre Collection Table.");
                        continue;
                    }
                    Embeddable element = OrmBindings.embeddableOf(ec);
                    if (element == null && ec.getColumn() == null) {
                        error("E2", ec, label(ec) + ": die Elemente brauchen ihre Spalte.");
                    } else if (element != null) {
                        covered(ec, element, Set.of(OrmBindings.tableOf(ec.getCollectionTable())), ec);
                    }
                }
            }
        }

        /** Die offenen oder tabellenfremden Pfade eines Embeddable deckt der Host per Override. */
        private void covered(OverrideHost host, Embeddable emb, Set<NamedColumnSet> tables, EObject where) {
            if (emb == null) {
                return;
            }
            Paths open = openPaths(emb, tables, new ArrayList<>());
            for (List<StructuralFeature> p : open.attributes) {
                if (!hasPath(host.getAttributeOverride(), p)) {
                    error("E2", where, label(where) + ": '" + OrmBindings.path(p) + "' hat keine Spalte in "
                            + "dieser Tabelle — AttributeOverride fehlt.");
                }
            }
            for (List<StructuralFeature> p : open.associations) {
                if (!hasAssociationPath(host.getAssociationOverride(), p)) {
                    error("E2", where, label(where) + ": die Beziehung '" + OrmBindings.path(p)
                            + "' hat keinen Join — AssociationOverride fehlt.");
                }
            }
        }

        /**
         * Die Obertypen, deren Mappings in der Tabelle von {@code e} landen: die Mapped Superclasses
         * bis zur nächsten Ober-Entity; in {@code TABLE_PER_CLASS} alle gemappten Obertypen, denn dort
         * hält jede konkrete Klasse auch die geerbten Spalten in ihrer eigenen Tabelle.
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

        /** Was ein Obertyp offen lässt oder in eine fremde Tabelle legt, legt die erbende Entity per Override fest. */
        private void coveredBySubclass(Entity e, IdentifiableType ms, Set<NamedColumnSet> tables) {
            for (Base b : bases(ms)) {
                var col = b.getColumn() == null ? null : b.getColumn().getCwmColumn();
                if ((col == null || !tables.contains(col.getOwner()))
                        && !overriddenFor(e, List.of(b.getCwmAttribute()), tables)) {
                    error("E2", e, label(e) + ": '" + name(b.getCwmAttribute()) + "' aus " + label(ms)
                            + " hat keine Spalte in " + name(OrmBindings.tableOf(e))
                            + " — AttributeOverride fehlt.");
                }
            }
            for (Embedded emb : ms.getEmbedded()) {
                Paths open = openPaths(OrmBindings.embeddableOf(emb), tables, new ArrayList<>());
                for (List<StructuralFeature> p : open.attributes) {
                    if (!hasPathIn(emb.getAttributeOverride(), p, tables)) {
                        List<StructuralFeature> full = prefixed(emb.getCwmAttribute(), p);
                        if (!overriddenFor(e, full, tables)) {
                            error("E2", e, label(e) + ": '" + OrmBindings.path(full) + "' aus " + label(ms)
                                    + " hat keine Spalte — AttributeOverride fehlt.");
                        }
                    }
                }
            }
            for (AttributeMapping m : OrmBindings.mappings(ms)) {
                if (m instanceof RelationshipMapping rm && isOwningSide(rm)
                        && (connections(rm) == 0 || joinOutside(rm, tables))
                        && !hasAssociationPath(e.getAssociationOverride(), List.of(rm.getCwmAssociationEnd()))) {
                    error("E2", e, label(e) + ": die Beziehung '" + name(rm.getCwmAssociationEnd()) + "' aus "
                            + label(ms) + " hat keinen Join — AssociationOverride fehlt.");
                }
            }
        }

        /** Ob die Join-Spalten einer To-One-Beziehung außerhalb von {@code tables} liegen. */
        private static boolean joinOutside(RelationshipMapping rm, Set<NamedColumnSet> tables) {
            Join join = rm instanceof ManyToOne mo ? mo.getJoin() : rm instanceof OneToOne oo ? oo.getJoin() : null;
            NamedColumnSet owner = join == null ? null : OrmBindings.tableOf(join);
            return owner != null && !tables.contains(owner);
        }

        private record Paths(List<List<StructuralFeature>> attributes, List<List<StructuralFeature>> associations) {
        }

        /** Pfade eines Embeddable ohne Spalte in {@code tables} bzw. Beziehungen ohne Join. */
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
         * Ob {@code e} den Pfad selbst überschreibt oder eine Ober-Entity ihn auf eine Spalte in
         * {@code tables} legt (in {@code TABLE_PER_CLASS} erbt die Unterklasse das, wenn die Spalte in
         * ihrer eigenen Tabelle liegt).
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

        /** Ein Override für {@code path}, dessen Spalte in {@code tables} liegt. */
        private static boolean hasPathIn(List<AttributeOverride> overrides, List<? extends StructuralFeature> path,
                Set<NamedColumnSet> tables) {
            return overrides.stream().anyMatch(o -> o.getCwmPath().equals(path) && o.getColumn() != null
                    && o.getColumn().getCwmColumn() != null && tables.contains(o.getColumn().getCwmColumn().getOwner()));
        }

        private static boolean hasAssociationPath(List<AssociationOverride> overrides,
                List<? extends StructuralFeature> path) {
            return overrides.stream().anyMatch(o -> o.getCwmPath().equals(path));
        }

        // ---- E3 Tabelle -------------------------------------------------------------------

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
                error("E3", where, label(where) + ": die Spalte " + qualified(col) + " liegt nicht in einer "
                        + "Tabelle von " + label(e) + ".");
            }
        }

        private void joinInTables(Entity e, EObject where, Join join, Set<NamedColumnSet> tables) {
            NamedColumnSet t = OrmBindings.tableOf(join);
            if (t != null && !tables.contains(t)) {
                error("E3", where, label(where) + ": die Join-Spalten liegen in " + name(t) + ", nicht in einer "
                        + "Tabelle von " + label(e) + ".");
            }
        }

        /** Warnung, wenn Nullbarkeit der Spalte und Untergrenze des Features auseinanderlaufen. */
        private void nullability(AttributeMapping m, org.eclipse.daanse.cwm.model.cwm.resource.relational.Column col) {
            StructuralFeature f = OrmBindings.featureOf(m);
            if (col == null || f == null || f.getMultiplicity() == null || f.getMultiplicity().getRange().isEmpty()
                    || m instanceof Id) {
                return;
            }
            boolean optional = OrmBindings.optional(m);
            if (!optional && col.getIsNullable() == NullableType.COLUMN_NULLABLE) {
                warning("E3", m, label(m) + ": Pflichtfeld, aber die Spalte " + qualified(col) + " ist nullbar.");
            } else if (optional && col.eIsSet(RelationalPackage.Literals.COLUMN__IS_NULLABLE)
                    && col.getIsNullable() == NullableType.COLUMN_NO_NULLS) {
                warning("E3", m, label(m) + ": optionales Feld, aber die Spalte " + qualified(col)
                        + " ist NOT NULL.");
            }
        }

        // ---- E5 Join ----------------------------------------------------------------------

        private void joins() {
            for (Iterator<EObject> it = em.eAllContents(); it.hasNext();) {
                if (!(it.next() instanceof Join j)) {
                    continue;
                }
                boolean fk = j.getCwmForeignKey() != null;
                if (fk == !j.getJoinColumn().isEmpty()) {
                    error("E5", j, label(j) + ": entweder der Fremdschlüssel oder Spaltenpaare, nicht "
                            + (fk ? "beides." : "keines."));
                }
                List<JoinPair> pairs = OrmBindings.joinColumns(j);
                Set<EObject> owners = new LinkedHashSet<>();
                Set<EObject> refOwners = new LinkedHashSet<>();
                for (JoinPair p : pairs) {
                    owners.add(p.column().getOwner());
                    if (p.referenced() == null) {
                        error("E5", j, label(j) + ": " + qualified(p.column()) + " hat keine referenzierte Spalte.");
                    } else {
                        refOwners.add(p.referenced().getOwner());
                    }
                }
                if (fk && j.getCwmForeignKey().getUniqueKey() != null
                        && j.getCwmForeignKey().getUniqueKey().getFeature().size() != j.getCwmForeignKey().getFeature().size()) {
                    error("E5", j, label(j) + ": der Fremdschlüssel hat nicht so viele Spalten wie sein Schlüssel.");
                }
                if (owners.size() > 1) {
                    error("E5", j, label(j) + ": die Join-Spalten liegen in mehreren Tabellen.");
                }
                if (refOwners.size() > 1) {
                    error("E5", j, label(j) + ": die referenzierten Spalten liegen in mehreren Tabellen.");
                }
                target(j);
            }
        }

        /** Die referenzierte Tabelle eines Joins passt zu seinem Ort im Mapping. */
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
                        error("E5", jt, label(jt) + ": Join und Inverse-Join liegen in verschiedenen Tabellen.");
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
                error("E5", j, label(j) + ": die referenzierten Spalten liegen in " + name(ref)
                        + ", erwartet " + names(expectedRef) + ".");
            }
            if (expectedOwn != null && !expectedOwn.isEmpty() && own != null && !expectedOwn.contains(own)) {
                error("E5", j, label(j) + ": die Join-Spalten liegen in " + name(own) + ", erwartet "
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

        // ---- E6 Vererbung -----------------------------------------------------------------

        private void inheritance() {
            for (Entity e : em.getEntity()) {
                Entity sup = superEntity(e);
                Entity root = rootEntity(e);
                boolean sub = sup != null;
                boolean hasSubs = em.getEntity().stream().anyMatch(o -> superEntity(o) == e);
                boolean concrete = e.getCwmClass() != null && !e.getCwmClass().isIsAbstract();
                if (!sub) {
                    if (hasSubs && !e.isSetInheritanceStrategy()) {
                        error("E6", e, label(e) + ": Wurzel einer Hierarchie ohne inheritanceStrategy.");
                    }
                    // an abstract TABLE_PER_CLASS root is the one entity without a table
                    boolean abstractTablePerClassRoot = !concrete && hasSubs && e.isSetInheritanceStrategy()
                            && e.getInheritanceStrategy() == InheritanceType.TABLEPERCLASS;
                    if (e.getCwmTable() == null && !abstractTablePerClassRoot) {
                        error("E6", e, label(e) + ": eine Entity ohne Oberklasse braucht ihre Tabelle.");
                    }
                } else {
                    if (e.isSetInheritanceStrategy()) {
                        error("E6", e, label(e) + ": die Strategie steht nur an der Wurzel " + label(root) + ".");
                    }
                    if (e.getCwmDiscriminatorColumn() != null) {
                        error("E6", e, label(e) + ": die Diskriminator-Spalte steht nur an der Wurzel "
                                + label(root) + ".");
                    }
                }
                if (!sub && !hasSubs) {
                    continue;
                }
                InheritanceType strategy = root.isSetInheritanceStrategy() ? root.getInheritanceStrategy() : null;
                if (strategy == InheritanceType.SINGLETABLE || strategy == InheritanceType.JOINED) {
                    if (!sub && e.getCwmDiscriminatorColumn() == null) {
                        error("E6", e, label(e) + ": " + strategy.getLiteral() + " braucht die "
                                + "Diskriminator-Spalte an der Wurzel.");
                    }
                    if (concrete && e.getDiscriminatorValue() == null) {
                        error("E6", e, label(e) + ": eine konkrete Entity der Hierarchie braucht ihren "
                                + "Diskriminator-Wert.");
                    }
                }
                if (sub && strategy == InheritanceType.JOINED) {
                    if (e.getCwmTable() == null || e.getPrimaryKeyJoin() == null) {
                        error("E6", e, label(e) + ": eine JOINED-Unterklasse braucht eigene Tabelle und "
                                + "primaryKeyJoin.");
                    }
                } else if (e.getPrimaryKeyJoin() != null) {
                    error("E6", e, label(e) + ": primaryKeyJoin gibt es nur in einer JOINED-Unterklasse.");
                }
                if (strategy == InheritanceType.TABLEPERCLASS && concrete && e.getCwmTable() == null) {
                    error("E6", e, label(e) + ": TABLE_PER_CLASS braucht eine eigene Tabelle je konkreter "
                            + "Entity.");
                }
                if (sub && strategy == InheritanceType.SINGLETABLE && e.getCwmTable() != null
                        && e.getCwmTable() != root.getCwmTable()) {
                    error("E6", e, label(e) + ": eine SINGLE_TABLE-Unterklasse liegt in der Tabelle der Wurzel.");
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

        // ---- E7 doppelt genutzte Spalte ---------------------------------------------------

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
                        error("E7", e, label(e) + ": die Spalte " + qualified(entry.getKey()) + " wird "
                                + entry.getValue().size() + "-mal genutzt, davon " + writers + "-mal schreibend — "
                                + "höchstens eine Nutzung darf insertable/updatable sein.");
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

        // ---- E8 Identität -----------------------------------------------------------------

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
                    error("E8", e, label(e) + ": braucht in ihrer Linie mindestens eine Id oder genau eine "
                            + "EmbeddedId (hat " + idCount + " Ids, " + embeddedIds.size() + " EmbeddedIds).");
                }
                if (idCount > 1 && idClass == null) {
                    error("E8", e, label(e) + ": " + idCount + " Id-Attribute brauchen cwmIdClass.");
                } else if (idClass != null && idCount <= 1) {
                    error("E8", e, label(e) + ": cwmIdClass nur bei mehr als einer Id.");
                } else if (idClass != null) {
                    idClassMatches(e, idClass, ids, relIds);
                }
                primaryKeyColumns(e);
                for (RelationshipMapping m : mapsIds) {
                    Attribute part = m instanceof ManyToOne mto ? mto.getCwmMapsIdAttribute()
                            : ((OneToOne) m).getCwmMapsIdAttribute();
                    if (embeddedIds.isEmpty() && part != null) {
                        error("E8", m, label(m) + ": mapsId mit Attribut braucht eine EmbeddedId.");
                    } else if (part != null) {
                        Classifier keyClass = embeddedIds.get(0).getCwmAttribute() == null ? null
                                : embeddedIds.get(0).getCwmAttribute().getType();
                        if (keyClass == null || part.getOwner() != keyClass) {
                            error("E8", m, label(m) + ": '" + name(part) + "' ist kein Attribut der "
                                    + "EmbeddedId-Klasse " + name(keyClass) + ".");
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
                error("E8", m, label(m) + ": cwmMapsIdAttribute ohne mapsId.");
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
                    error("E8", e, label(e) + ": das Attribut '" + a.getName() + "' der Id-Klasse "
                            + name(idClass) + " hat kein Id-Mapping gleichen Namens und Typs.");
                }
            }
            for (Id i : ids) {
                if (!idNames.contains(name(i.getCwmAttribute()))) {
                    error("E8", i, label(i) + ": fehlt in der Id-Klasse " + name(idClass) + ".");
                }
            }
        }

        /** Die eigenen Id-Spalten einer Entity liegen im Primärschlüssel ihrer Tabelle. */
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
                    error("E8", e, label(e) + ": die Id-Spalte " + qualified(col) + " gehört nicht zum "
                            + "Primärschlüssel " + name(pk) + ".");
                }
            }
        }

        // ---- E9 Konsistenz ----------------------------------------------------------------

        private void consistency() {
            for (Iterator<EObject> it = em.eAllContents(); it.hasNext();) {
                EObject o = it.next();
                if (o instanceof GeneratedValue gv) {
                    generatedValue(gv);
                } else if (o instanceof Convert c) {
                    if ((c.getConverter() != null) == (c.isSetDisableConversion() && c.isDisableConversion())) {
                        error("E9", c, label(c) + ": entweder ein Konverter oder disableConversion.");
                    }
                    if (c.eContainer() instanceof Basic && !c.getCwmPath().isEmpty()) {
                        error("E9", c, label(c) + ": an einem Basic gilt die Konvertierung ohne Pfad.");
                    } else if (c.eContainer() instanceof OverrideHost host) {
                        path(c, host, c.getCwmPath(), false, null);
                    }
                } else if (o instanceof AttributeOverride ao && ao.eContainer() instanceof OverrideHost host) {
                    path(ao, host, ao.getCwmPath(), true, Attribute.class);
                } else if (o instanceof AssociationOverride ao && ao.eContainer() instanceof OverrideHost host) {
                    path(ao, host, ao.getCwmPath(), true, AssociationEnd.class);
                } else if (o instanceof CollectionSupport cs && !cs.getOrderBy().isEmpty()
                        && cs.getOrderColumn() != null) {
                    error("E9", o, label(o) + ": entweder orderBy (Sortierung beim Laden) oder orderColumn "
                            + "(gespeicherte Position), nicht beides.");
                } else if (o instanceof Ordering ord && ord.eContainer() instanceof CollectionSupport cs) {
                    path(ord, targetType(cs), ord.getCwmPath(), true, Attribute.class);
                } else if (o instanceof NamedNativeQuery q) {
                    if ((q.getCwmResultClass() != null) == (q.getResultSetMapping() != null)) {
                        error("E9", q, label(q) + ": entweder cwmResultClass oder resultSetMapping.");
                    }
                    resultColumns(q);
                }
            }
        }

        private void generatedValue(GeneratedValue gv) {
            if (!gv.isSetStrategy()) {
                error("E9", gv, label(gv) + ": die Strategie ist nicht gesetzt (kein stilles AUTO).");
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
                error("E9", gv, label(gv) + ": Strategie " + s.getLiteral() + " passt nicht zum Generator "
                        + (g == null ? "(keiner)" : label(g)) + ".");
            }
        }

        /** Jede Ergebnisspalte der Ergebnisabbildung ist eine Spalte der gespeicherten Abfrage. */
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
                    error("E9", rsm, label(rsm) + ": die Ergebnisspalte " + qualified(col) + " gehört nicht zur "
                            + "gespeicherten Abfrage " + name(q.getCwmQuery()) + " von " + label(q) + ".");
                }
            }
        }

        /** Der Typ, in den eine Sortierung zeigt: das Ziel der Beziehung bzw. das Element-Embeddable. */
        private static Classifier targetType(CollectionSupport cs) {
            if (cs instanceof RelationshipMapping rm) {
                return rm.getCwmAssociationEnd() == null ? null : rm.getCwmAssociationEnd().getType();
            }
            if (cs instanceof ValueMapping vm) {
                return vm.getCwmAttribute() == null ? null : vm.getCwmAttribute().getType();
            }
            return null;
        }

        /** Ein Feature-Pfad beginnt beim Typ des Hosts und folgt den Typen seiner Elemente. */
        private void path(EObject where, OverrideHost host, List<StructuralFeature> path, boolean required,
                java.lang.Class<?> last) {
            path(where, hostType(host), path, required, last);
        }

        /** Ein Feature-Pfad beginnt bei {@code start} und folgt den Typen seiner Elemente. */
        private void path(EObject where, Classifier start, List<StructuralFeature> path, boolean required,
                java.lang.Class<?> last) {
            if (path.isEmpty()) {
                if (required) {
                    error("E9", where, label(where) + ": der Pfad ist leer.");
                }
                return;
            }
            Classifier type = start;
            for (StructuralFeature f : path) {
                if (type != null && !belongsTo(f, type, true)) {
                    error("E9", where, label(where) + ": '" + name(f) + "' im Pfad " + OrmBindings.path(path)
                            + " ist kein Feature von " + name(type) + ".");
                    return;
                }
                type = f.getType();
            }
            if (last != null && !last.isInstance(path.get(path.size() - 1))) {
                error("E9", where, label(where) + ": der Pfad " + OrmBindings.path(path) + " endet nicht in "
                        + (last == Attribute.class ? "einem Attribut." : "einem Assoziationsende."));
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

        // ---- E10 Namen --------------------------------------------------------------------

        private void names() {
            for (Iterator<EObject> it = em.eAllContents(); it.hasNext();) {
                EObject o = it.next();
                String anchor = anchorName(o);
                if (anchor != null && o instanceof ModelElement me && !anchor.equals(me.getName())) {
                    error("E10", o, o.eClass().getName() + " '" + me.getName() + "': Name weicht vom CWM-Anker '"
                            + anchor + "' ab.");
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

        /** Der Name, den ein verankertes Element tragen muss, sonst {@code null}. */
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
                    error("E10", me, what + "-Name '" + me.getName() + "' ist im Mapping-Satz '" + name(em)
                            + "' nicht eindeutig.");
                }
            }
        }

        // ---- E11 Gegenrichtung ------------------------------------------------------------

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
                        warning("E11", c, "Spalte " + qualified(c) + " wird von keinem Mapping genutzt "
                                + "(unmapped column).");
                    }
                }
            }
        }

        // ---- Hilfen -----------------------------------------------------------------------

        /** Die Tabellen einer Entity: Haupttabelle (bei SINGLE_TABLE die der Wurzel) und Secondary Tables. */
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

        /** Die Tabellen eines Typs und seiner gemappten Obertypen (Ziel einer Referenz in JOINED-Hierarchien). */
        private static Set<NamedColumnSet> lineageTables(ManagedType mt) {
            Set<NamedColumnSet> out = new LinkedHashSet<>();
            for (ManagedType t : OrmBindings.lineage(mt)) {
                if (t instanceof Entity e) {
                    out.addAll(tables(e));
                }
            }
            return out;
        }

        /** Die Mapped Superclasses zwischen einer Entity und ihrer nächsten Ober-Entity. */
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

        /** Ob {@code f} ein Feature von {@code c} (oder, mit {@code inherited}, einer Oberklasse) ist. */
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
        // ManyToOne ist immer die Owning-Side.
        return true;
    }

    /** {@code Klasse 'name'}, bei Attribut-Mappings mit dem gemappten Typ. */
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
