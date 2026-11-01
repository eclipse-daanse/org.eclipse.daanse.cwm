/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.orm.fennec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.fennec.persistence.eorm.AssociationOverride;
import org.eclipse.fennec.persistence.eorm.AttributeOverride;
import org.eclipse.fennec.persistence.eorm.Base;
import org.eclipse.fennec.persistence.eorm.BaseRef;
import org.eclipse.fennec.persistence.eorm.Column;
import org.eclipse.fennec.persistence.eorm.ElementCollection;
import org.eclipse.fennec.persistence.eorm.Entity;
import org.eclipse.fennec.persistence.eorm.EntityMappings;
import org.eclipse.fennec.persistence.eorm.ForeignKey;
import org.eclipse.fennec.persistence.eorm.InheritanceType;
import org.eclipse.fennec.persistence.eorm.JoinColumn;
import org.eclipse.fennec.persistence.eorm.JoinTable;
import org.eclipse.fennec.persistence.eorm.ManyToMany;
import org.eclipse.fennec.persistence.eorm.ManyToOne;
import org.eclipse.fennec.persistence.eorm.MappedByRef;
import org.eclipse.fennec.persistence.eorm.OneToMany;
import org.eclipse.fennec.persistence.eorm.OneToOne;
import org.eclipse.fennec.persistence.eorm.Version;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Nothing in the converted corpus is left to a JPA or Fennec default: every entity names its
 * table (an abstract entity of a table-per-class hierarchy has none, it has no rows), every basic/id/version its column (or every user overrides it), every owning side its
 * join columns with referenced columns, its join table, its primary-key join or its mapsId, every
 * inverse side its mappedBy, every relationship its target entity, every single-table hierarchy
 * its discriminator column and values, every foreign key its constraint mode, and the unit is
 * metadata-complete.
 */
class ExplicitOutputTest {

    static Stream<String> files() {
        return Corpus.files().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("files")
    void noDefaultIsLeft(String file) {
        var cwm = Corpus.mapping(file);
        EntityMappings em = CwmOrmToEorm.convert(cwm, EcoreIndex.of(Corpus.ecorePackages(file))).eorm();

        assertThat(defaults(em)).isEmpty();
    }

    /** The places in a mapping set where a default would apply, described. */
    static List<String> defaults(EntityMappings em) {
        List<String> out = new ArrayList<>();
        if (em.getPersistenceUnitMetadata() == null
                || em.getPersistenceUnitMetadata().getXmlMappingMetadataComplete() == null) {
            out.add("metadata-complete missing");
        }
        List<String> overridden = new ArrayList<>();
        CorpusTest.all(em, AttributeOverride.class).forEach(o -> overridden.add(o.getName()));
        CorpusTest.all(em, AssociationOverride.class).forEach(o -> overridden.add(o.getName()));

        for (Entity e : em.getEntity()) {
            boolean abstractTablePerClass = ((EClass) e.getClass_()).isAbstract()
                    && strategy(em, e) == InheritanceType.TABLEPERCLASS;
            if ((e.getTable() == null || blank(e.getTable().getName())) && !abstractTablePerClass) {
                out.add("entity " + e.getName() + " without table");
            }
            if (e.getInheritance() != null && e.getInheritance().getStrategy() == InheritanceType.SINGLETABLE) {
                hierarchy(em, e, out);
            }
        }
        for (EObject o : CorpusTest.all(em, EObject.class)) {
            boolean inEntity = owningEntity(o) != null;
            String where = describe(o);
            if (o instanceof Base b && !(o instanceof ElementCollection) && noColumn(b.getColumn())
                    && (inEntity || !isOverridden(overridden, b.getName()))) {
                out.add(where + " without column");
            }
            if (o instanceof Version v && noColumn(v.getColumn()) && (inEntity || !isOverridden(overridden, v.getName()))) {
                out.add(where + " without column");
            }
            if (o instanceof ElementCollection ec && (ec.getCollectionTable() == null
                    || blank(ec.getCollectionTable().getName()) || !complete(ec.getCollectionTable().getJoinColumn()))) {
                out.add(where + " without collection table and its join columns");
            }
            if (o instanceof BaseRef r) {
                if (blank(target(r))) {
                    out.add(where + " without targetEntity");
                }
                boolean inverse = r instanceof MappedByRef m && !blank(m.getMappedBy());
                if (!inverse && !connected(r) && (inEntity || !isOverridden(overridden, r.getName()))) {
                    out.add(where + " owning side without join columns, join table, primary-key join or mapsId");
                }
            }
            if (o instanceof ForeignKey fk && !fk.isSetConstraintMode()) {
                out.add(where + " foreign key without constraint mode");
            }
        }
        return out;
    }

    private static void hierarchy(EntityMappings em, Entity root, List<String> out) {
        if (root.getDiscriminatorColumn() == null || blank(root.getDiscriminatorColumn().getName())
                || !root.getDiscriminatorColumn().isSetDiscriminatorType()) {
            out.add("hierarchy " + root.getName() + " without discriminator column and type");
        }
        EClass rootClass = (EClass) root.getClass_();
        for (Entity e : em.getEntity()) {
            EClass c = (EClass) e.getClass_();
            boolean member = c == rootClass || c.getEAllSuperTypes().contains(rootClass);
            if (member && !c.isAbstract() && blank(e.getDiscriminatorValue())) {
                out.add("entity " + e.getName() + " of hierarchy " + root.getName() + " without discriminator value");
            }
        }
    }

    /** The inheritance strategy of the hierarchy an entity belongs to, or {@code null}. */
    private static InheritanceType strategy(EntityMappings em, Entity e) {
        EClass c = (EClass) e.getClass_();
        for (Entity root : em.getEntity()) {
            if (root.getInheritance() != null
                    && (root.getClass_() == c || c.getEAllSuperTypes().contains(root.getClass_()))) {
                return root.getInheritance().getStrategy();
            }
        }
        return null;
    }

    private static boolean connected(BaseRef r) {
        if (r.getJoinTable() != null) {
            JoinTable jt = r.getJoinTable();
            return !blank(jt.getName()) && complete(jt.getJoinColumn()) && complete(jt.getInverseJoinColumn());
        }
        if (r instanceof ManyToOne m) {
            return complete(m.getJoinColumn()) || m.getMapsId() != null;
        }
        if (r instanceof OneToOne o) {
            return complete(o.getJoinColumn()) || o.getMapsId() != null || !o.getPrimaryKeyJoinColumn().isEmpty()
                    && o.getPrimaryKeyJoinColumn().stream().allMatch(c -> !blank(c.getReferencedColumnName()));
        }
        if (r instanceof OneToMany o) {
            return complete(o.getJoinColumn());
        }
        return !(r instanceof ManyToMany);
    }

    private static boolean complete(List<JoinColumn> columns) {
        return !columns.isEmpty()
                && columns.stream().allMatch(c -> !blank(c.getName()) && !blank(c.getReferencedColumnName()));
    }

    private static String target(BaseRef r) {
        return (String) r.eGet(r.eClass().getEStructuralFeature("targetEntity"));
    }

    private static boolean noColumn(Column c) {
        return c == null || blank(c.getName());
    }

    private static boolean isOverridden(List<String> overridden, String name) {
        return overridden.stream().anyMatch(o -> o.equals(name) || o.endsWith("." + name));
    }

    private static Entity owningEntity(EObject o) {
        for (EObject c = o; c != null; c = c.eContainer()) {
            if (c instanceof Entity e) {
                return e;
            }
            if (c instanceof org.eclipse.fennec.persistence.eorm.MappedSuperclass
                    || c instanceof org.eclipse.fennec.persistence.eorm.Embeddable) {
                return null;
            }
        }
        return null;
    }

    private static String describe(EObject o) {
        Object name = o.eClass().getEStructuralFeature("name") == null ? "" : o.eGet(o.eClass().getEStructuralFeature("name"));
        EObject owner = o.eContainer() == null ? null : o.eContainer().eContainer();
        Object ownerName = owner == null || owner.eClass().getEStructuralFeature("name") == null ? ""
                : owner.eGet(owner.eClass().getEStructuralFeature("name"));
        return o.eClass().getName() + " " + ownerName + "." + name;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
