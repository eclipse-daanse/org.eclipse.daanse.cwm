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
 * Löst ein ORM-Mapping in lesbare Bindungen auf: für jedes Attribut und jede Beziehungsrolle
 * eines Managed Type, in welcher Tabelle und in welchen Spalten es landet.
 *
 * <p>Das Modell hält diese Antwort verteilt — ein {@link Basic} über seine {@link Column}, ein
 * {@link Embedded} über Overrides oder die Default-Bindung seines {@link Embeddable}, eine
 * Beziehung über einen {@link Join} in der eigenen Tabelle, der Zieltabelle oder einer Join-Table.
 * Diese Klasse sammelt das je Mapping-Art in ein {@link Binding}, damit Generatoren (Java-,
 * Python-Metadaten) und Laufzeitcode dieselbe Auflösung nutzen.</p>
 *
 * <p>Sie hält zugleich die Ableitungen, die das Modell bewusst nicht als Feature trägt (ein
 * Fakt, ein Ort): Schema und Catalog aus der Tabelle, Primärschlüssel, Diskriminator-Typ aus dem
 * SQL-Typ, Optionalität aus der Multiplizität, die Tabelle einer Join-Form aus ihren Join-Spalten,
 * die Spaltenpaare eines Fremdschlüssels, Pfadnamen, Prozedurparameter und das Objektpaket.</p>
 *
 * <p>Namen kommen immer aus den referenzierten CWM-Elementen ({@code cwm*}), nie aus
 * Namenskopien. Spalten sind unqualifiziert; {@link Binding#referencedColumns()} ist als
 * {@code TABELLE.SPALTE} qualifiziert, weil referenzierte Spalten meist in einer anderen Tabelle
 * liegen.</p>
 */
public final class OrmBindings {

    private OrmBindings() {
    }

    /**
     * Die Bindung eines Attributs oder einer Beziehungsrolle.
     *
     * @param feature            Name des Attributs bzw. der Assoziationsrolle
     * @param kind               Mapping-Art, z. B. {@code ID}, {@code BASIC}, {@code MANY_TO_MANY}
     * @param target             Zielklasse einer Beziehung, sonst leer
     * @param table              Tabelle der Wert-Spalten; ohne Wert-Spalten und ohne Join-Table die
     *                           Tabelle der Join-Spalten (bei One-to-Many die Zieltabelle), sonst leer
     * @param columns            Wert-Spalten (unqualifiziert)
     * @param paths              zu {@code columns} parallele Unterattribute (Embedded), sonst leer
     * @param joinTable          Join- bzw. Collection-Table, sonst leer
     * @param joinColumns        Join-Spalten (eigene Seite)
     * @param referencedColumns  die von den Join-Spalten referenzierten Spalten, qualifiziert,
     *                           parallel zu {@code joinColumns}
     * @param inverseJoinColumns Join-Spalten der Gegenseite in einer Join-Table
     * @param inverseReferencedColumns die von den inversen Join-Spalten referenzierten Spalten,
     *                           qualifiziert, parallel zu {@code inverseJoinColumns}
     * @param orderColumn        Order-Spalte einer Collection, sonst leer
     * @param mapKeyColumn       Map-Key-Spalte einer Collection, sonst leer
     */
    public record Binding(String feature, String kind, String target, String table,
            List<String> columns, List<String> paths, String joinTable, List<String> joinColumns,
            List<String> referencedColumns, List<String> inverseJoinColumns,
            List<String> inverseReferencedColumns, String orderColumn, String mapKeyColumn) {
    }

    /**
     * Ein Spaltenpaar eines Joins: die Fremdschlüsselspalte und die Spalte, auf die sie zeigt.
     *
     * @param column     die Spalte in der Tabelle des Joins
     * @param referenced die referenzierte Spalte der Zieltabelle
     */
    public record JoinPair(org.eclipse.daanse.cwm.model.cwm.resource.relational.Column column,
            org.eclipse.daanse.cwm.model.cwm.resource.relational.Column referenced) {
    }

    // ---- Typ-Ebene -------------------------------------------------------------------------

    /** Alle Managed Types eines Mappings: Mapped Superclasses, Entities, Embeddables. */
    public static List<ManagedType> managedTypes(EntityMappings em) {
        List<ManagedType> all = new ArrayList<>();
        all.addAll(em.getMappedSuperclass());
        all.addAll(em.getEntity());
        all.addAll(em.getEmbeddable());
        return all;
    }

    /** {@code ENTITY}, {@code EMBEDDABLE} oder {@code MAPPED_SUPERCLASS}. */
    public static String kind(ManagedType mt) {
        return constant(mt.eClass().getName());
    }

    /** Name der Klasse, die der Managed Type mappt. */
    public static String className(ManagedType mt) {
        return name(mt.getCwmClass());
    }

    /**
     * Das Objektpaket eines Mappings: der gemeinsame Container der gemappten Klassen (bei Klassen
     * in verschiedenen Paketen deren nächster gemeinsamer Vorfahr), sonst {@code null}.
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

    /** Die Container eines Elements, vom nächsten bis zur Wurzel. */
    private static List<EObject> containerChain(EObject o) {
        List<EObject> chain = new ArrayList<>();
        for (EObject c = o.eContainer(); c != null; c = c.eContainer()) {
            chain.add(c);
        }
        return chain;
    }

    /** Die Tabelle einer Entity; eine Unterklasse ohne eigene Tabelle liegt in der ihrer Wurzel. */
    public static NamedColumnSet tableOf(ManagedType mt) {
        for (ManagedType t : lineage(mt)) {
            if (t instanceof Entity e && e.getCwmTable() != null) {
                return e.getCwmTable();
            }
        }
        return null;
    }

    /**
     * Die Tabelle einer Entity. Nennt die Entity keine eigene — eine Unterklasse bei
     * {@code SINGLE_TABLE}-Vererbung —, gilt die Tabelle der nächsten Vorfahren-Entity.
     * Embeddables und Mapped Superclasses haben keine: leer.
     */
    public static String table(ManagedType mt) {
        return mt instanceof Entity ? name(tableOf(mt)) : "";
    }

    /** Das Schema, in dem die Tabelle einer Entity liegt, sonst leer. */
    public static String schema(ManagedType mt) {
        NamedColumnSet t = mt instanceof Entity ? tableOf(mt) : null;
        return t != null && t.eContainer() instanceof ModelElement s ? name(s) : "";
    }

    /** Der Catalog, in dem das Schema der Tabelle einer Entity liegt, sonst leer. */
    public static String catalog(ManagedType mt) {
        NamedColumnSet t = mt instanceof Entity ? tableOf(mt) : null;
        return t != null && t.eContainer() != null && t.eContainer().eContainer() instanceof ModelElement c
                ? name(c)
                : "";
    }

    /** Der Primärschlüssel der Tabelle einer Entity (bei {@code SINGLE_TABLE} der Wurzeltabelle), sonst {@code null}. */
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
     * Der gemappte Obertyp: der Managed Type derselben {@code EntityMappings}, dessen Klasse die
     * Oberklasse ist ({@code relationships::Generalization}, child → parent, im Container der
     * Klasse), sonst {@code null}.
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

    /** Die direkten Oberklassen einer Klasse: Generalizations im Container der Klasse. */
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

    /** Die Klasse selbst und alle ihre Oberklassen (zyklenfest). */
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

    /** Der Typ selbst, dann seine gemappten Obertypen bis zur Wurzel (zyklenfest). */
    public static List<ManagedType> lineage(ManagedType mt) {
        List<ManagedType> out = new ArrayList<>();
        for (ManagedType t = mt; t != null && !out.contains(t); t = superType(t)) {
            out.add(t);
        }
        return out;
    }

    /** Name der Klasse des gemappten Obertyps, sonst leer. */
    public static String superClassName(ManagedType mt) {
        ManagedType parent = superType(mt);
        return parent == null ? "" : className(parent);
    }

    /**
     * Die Vererbungsstrategie ({@code SINGLE_TABLE}, {@code JOINED}, …). Eine Unterklasse, die
     * keine eigene nennt, steht in der Strategie ihrer Hierarchie: die nächste Vorfahren-Entity
     * mit gesetzter Strategie gibt sie vor.
     */
    public static String inheritance(ManagedType mt) {
        for (ManagedType t : lineage(mt)) {
            if (t instanceof Entity e && e.isSetInheritanceStrategy()) {
                return e.getInheritanceStrategy().getLiteral();
            }
        }
        return "";
    }

    /** Die Diskriminator-Spalte der Hierarchie: an der nächsten Vorfahren-Entity, die sie nennt. */
    public static org.eclipse.daanse.cwm.model.cwm.resource.relational.Column discriminatorColumnOf(ManagedType mt) {
        for (ManagedType t : lineage(mt)) {
            if (t instanceof Entity e && e.getCwmDiscriminatorColumn() != null) {
                return e.getCwmDiscriminatorColumn();
            }
        }
        return null;
    }

    /**
     * Die Diskriminator-Spalte. Das Modell hält sie an der Wurzel der Hierarchie; eine Unterklasse
     * bekommt sie von der nächsten Vorfahren-Entity, die sie nennt — ihr Diskriminator-Wert
     * steht in genau dieser Spalte.
     */
    public static String discriminatorColumn(ManagedType mt) {
        return name(discriminatorColumnOf(mt));
    }

    /**
     * Der JPA-Diskriminator-Typ aus dem SQL-Typ der Diskriminator-Spalte: {@code INTEGER} für
     * ganzzahlige und numerische Typen, {@code CHAR} für Zeichen der Länge 1, sonst {@code STRING};
     * leer ohne Diskriminator-Spalte.
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

    /** Der Diskriminator-Wert, sofern gesetzt. */
    public static String discriminatorValue(ManagedType mt) {
        return mt instanceof Entity e && e.getDiscriminatorValue() != null
                ? e.getDiscriminatorValue() : "";
    }

    /** Die Secondary Tables einer Entity. */
    public static List<String> secondaryTables(ManagedType mt) {
        List<String> out = new ArrayList<>();
        if (mt instanceof Entity e) {
            for (SecondaryTable st : e.getSecondaryTable()) {
                add(out, name(tableOf(st)));
            }
        }
        return out;
    }

    // ---- Join-Ebene ------------------------------------------------------------------------

    /**
     * Die Spaltenpaare eines Joins: mit {@code cwmForeignKey} dessen {@code feature} ↔
     * {@code uniqueKey.feature} in Schlüsselreihenfolge, sonst die expliziten Join-Spalten.
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

    /** Die Tabelle, in der die Spalten eines Joins liegen: Owner der Join-Spalten bzw. des Fremdschlüssels. */
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

    /** Die Tabelle, auf die ein Join zeigt: Owner der referenzierten Spalten. */
    public static NamedColumnSet referencedTableOf(Join join) {
        for (JoinPair p : joinColumns(join)) {
            if (p.referenced() != null && p.referenced().getOwner() instanceof NamedColumnSet t) {
                return t;
            }
        }
        return null;
    }

    /** Die Tabelle einer Join-Form (Secondary, Collection oder Join Table): die Tabelle ihres Joins. */
    public static NamedColumnSet tableOf(JoinedTable jt) {
        return jt == null ? null : tableOf(jt.getJoin());
    }

    // ---- Attribut-Ebene --------------------------------------------------------------------

    /** Der Pfad als Text: die Namen der Features mit Punkt verbunden, z. B. {@code address.street}. */
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

    /** Das gemappte Feature eines Attribut-Mappings: Attribut, Assoziationsende oder transientes Feature. */
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
     * Ob der Wert fehlen darf: Untergrenze 0 der Multiplizität des Attributs bzw. Endes. Ohne
     * angegebene Multiplizität {@code true} (die Untergrenze ist dann nicht bekannt).
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

    /** Der Generator eines generierten Werts, sonst {@code null}. */
    public static Generator generatorOf(GeneratedValue gv) {
        return gv == null ? null : gv.getGenerator();
    }

    /** Die Parameter einer Stored-Procedure-Abfrage: die Parameter der Prozedur in Aufrufreihenfolge. */
    public static List<Parameter> parameters(NamedStoredProcedureQuery q) {
        return q.getCwmProcedure() == null ? List.of() : List.copyOf(q.getCwmProcedure().getParameter());
    }

    /**
     * Alle Attribut-Mappings in fester Reihenfolge: Id, Embedded-Id, Version, Basic, Embedded,
     * Transient, Element-Collection, dann die Beziehungen.
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

    /** Die Bindungen aller Attribut-Mappings eines Managed Type. */
    public static List<Binding> of(ManagedType mt) {
        return mappings(mt).stream().map(OrmBindings::of).toList();
    }

    /** Die Bindung eines einzelnen Attribut-Mappings. */
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

    // ---- Hilfen ----------------------------------------------------------------------------

    /** Overrides, sonst die Default-Bindung des eingebetteten Embeddable. */
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

    /** Das Embeddable, dessen Klasse der Typ des gemappten Attributs ist, sonst {@code null}. */
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

    /** Join-Spalte und ihre Referenz paarweise; eine fehlende Referenz bleibt als leerer Eintrag. */
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

    /** Sammelt eine Bindung schrittweise; {@code columns} und {@code paths} bleiben parallel. */
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
