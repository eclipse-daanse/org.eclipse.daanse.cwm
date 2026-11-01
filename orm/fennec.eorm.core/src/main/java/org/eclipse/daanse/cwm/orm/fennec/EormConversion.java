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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Parameter;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.daanse.orm.AssociationOverride;
import org.eclipse.daanse.cwm.model.daanse.orm.AttributeMapping;
import org.eclipse.daanse.cwm.model.daanse.orm.AttributeOverride;
import org.eclipse.daanse.cwm.model.daanse.orm.Base;
import org.eclipse.daanse.cwm.model.daanse.orm.Basic;
import org.eclipse.daanse.cwm.model.daanse.orm.CascadeKind;
import org.eclipse.daanse.cwm.model.daanse.orm.CollectionSupport;
import org.eclipse.daanse.cwm.model.daanse.orm.CollectionTable;
import org.eclipse.daanse.cwm.model.daanse.orm.Column;
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
import org.eclipse.daanse.cwm.model.daanse.orm.Generator;
import org.eclipse.daanse.cwm.model.daanse.orm.Id;
import org.eclipse.daanse.cwm.model.daanse.orm.IdentifiableType;
import org.eclipse.daanse.cwm.model.daanse.orm.Join;
import org.eclipse.daanse.cwm.model.daanse.orm.JoinTable;
import org.eclipse.daanse.cwm.model.daanse.orm.LifecycleCallback;
import org.eclipse.daanse.cwm.model.daanse.orm.ManagedType;
import org.eclipse.daanse.cwm.model.daanse.orm.ManyToMany;
import org.eclipse.daanse.cwm.model.daanse.orm.ManyToOne;
import org.eclipse.daanse.cwm.model.daanse.orm.MapKey;
import org.eclipse.daanse.cwm.model.daanse.orm.MappedSuperclass;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedAttributeNode;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedEntityGraph;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedNativeQuery;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedQuery;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedStoredProcedureQuery;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedSubgraph;
import org.eclipse.daanse.cwm.model.daanse.orm.OneToMany;
import org.eclipse.daanse.cwm.model.daanse.orm.OneToOne;
import org.eclipse.daanse.cwm.model.daanse.orm.Ordering;
import org.eclipse.daanse.cwm.model.daanse.orm.OrmElement;
import org.eclipse.daanse.cwm.model.daanse.orm.OverrideHost;
import org.eclipse.daanse.cwm.model.daanse.orm.OwnableRelationship;
import org.eclipse.daanse.cwm.model.daanse.orm.PersistenceUnitDefaults;
import org.eclipse.daanse.cwm.model.daanse.orm.PersistenceUnitMetadata;
import org.eclipse.daanse.cwm.model.daanse.orm.Query;
import org.eclipse.daanse.cwm.model.daanse.orm.QueryHint;
import org.eclipse.daanse.cwm.model.daanse.orm.RelationshipMapping;
import org.eclipse.daanse.cwm.model.daanse.orm.SecondaryTable;
import org.eclipse.daanse.cwm.model.daanse.orm.SequenceGenerator;
import org.eclipse.daanse.cwm.model.daanse.orm.SimpleBase;
import org.eclipse.daanse.cwm.model.daanse.orm.SqlResultSetMapping;
import org.eclipse.daanse.cwm.model.daanse.orm.TableGenerator;
import org.eclipse.daanse.cwm.model.daanse.orm.Transient;
import org.eclipse.daanse.cwm.model.daanse.orm.Version;
import org.eclipse.daanse.cwm.model.daanse.orm.util.OrmBindings;
import org.eclipse.daanse.cwm.model.daanse.orm.util.OrmBindings.JoinPair;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.fennec.persistence.eorm.AccessType;
import org.eclipse.fennec.persistence.eorm.ConstraintMode;
import org.eclipse.fennec.persistence.eorm.DiscriminatorType;
import org.eclipse.fennec.persistence.eorm.EClassObject;
import org.eclipse.fennec.persistence.eorm.EFeatureObject;
import org.eclipse.fennec.persistence.eorm.EORMFactory;
import org.eclipse.fennec.persistence.eorm.EnumType;
import org.eclipse.fennec.persistence.eorm.FetchType;
import org.eclipse.fennec.persistence.eorm.GenerationType;
import org.eclipse.fennec.persistence.eorm.InheritanceType;
import org.eclipse.fennec.persistence.eorm.LockModeType;
import org.eclipse.fennec.persistence.eorm.ParameterMode;
import org.eclipse.fennec.persistence.eorm.TemporalType;

/**
 * One CWM-ORM to EORM run. Reads the explicit CWM mapping, takes every derived fact from
 * {@link OrmBindings} and writes every value EORM can hold — so the result does not depend on
 * JPA or Fennec defaults. EORM types are written with their package, CWM-ORM types are imported.
 */
final class EormConversion {

    private static final EORMFactory F = EORMFactory.eINSTANCE;

    private final EntityMappings cwm;
    private final EcoreIndex index;
    private final List<Note> notes = new ArrayList<>();
    private final Set<OrmElement> kept = Collections.newSetFromMap(new IdentityHashMap<>());

    EormConversion(EntityMappings cwm, EcoreIndex index) {
        this.cwm = cwm;
        this.index = index;
    }

    List<Note> notes() {
        return notes;
    }

    org.eclipse.fennec.persistence.eorm.EntityMappings run() {
        var out = F.createEntityMappings();
        out.setName(cwm.getName() == null ? "mappings" : cwm.getName());
        out.setVersion("3.1");
        out.setDescription(keep(cwm));
        var objectPackage = OrmBindings.objectPackage(cwm);
        if (objectPackage != null) {
            out.setPackage(index.find(objectPackage) instanceof EPackage ep ? EcoreIndex.qualifiedName(ep)
                    : CwmPath.of(objectPackage));
        }
        String schema = uniform(OrmBindings::schema);
        if (!schema.isEmpty()) {
            out.setSchema(schema);
        }
        String catalog = uniform(OrmBindings::catalog);
        if (!catalog.isEmpty()) {
            out.setCatalog(catalog);
        }
        if (cwm.isSetAccess()) {
            out.setAccess(access(cwm.getAccess()));
        }
        out.setPersistenceUnitMetadata(metadata(cwm.getPersistenceUnitMetadata()));
        for (Generator g : cwm.getGenerator()) {
            if (g instanceof SequenceGenerator sg) {
                out.getSequenceGenerator().add(sequenceGenerator(sg));
            } else if (g instanceof TableGenerator tg) {
                out.getTableGenerator().add(tableGenerator(tg));
            }
        }
        for (NamedQuery q : cwm.getNamedQuery()) {
            out.getNamedQuery().add(namedQuery(q));
        }
        for (NamedNativeQuery q : cwm.getNamedNativeQuery()) {
            out.getNamedNativeQuery().add(nativeQuery(q));
        }
        for (NamedStoredProcedureQuery q : cwm.getNamedStoredProcedureQuery()) {
            out.getNamedStoredProcedureQuery().add(procedureQuery(q));
        }
        for (SqlResultSetMapping m : cwm.getSqlResultSetMapping()) {
            out.getSqlResultSetMapping().add(resultSetMapping(m));
        }
        for (Converter c : cwm.getConverter()) {
            out.getConverter().add(converter(c));
        }
        for (MappedSuperclass ms : cwm.getMappedSuperclass()) {
            out.getMappedSuperclass().add(mappedSuperclass(ms));
        }
        for (Entity e : cwm.getEntity()) {
            out.getEntity().add(entity(e));
        }
        for (Embeddable e : cwm.getEmbeddable()) {
            out.getEmbeddable().add(embeddable(e));
        }
        reportLostDescriptions();
        return out;
    }

    private String uniform(Function<ManagedType, String> derive) {
        Set<String> values = new LinkedHashSet<>();
        for (Entity e : cwm.getEntity()) {
            values.add(derive.apply(e));
        }
        return values.size() == 1 ? values.iterator().next() : "";
    }

    private org.eclipse.fennec.persistence.eorm.PersistenceUnitMetadata metadata(PersistenceUnitMetadata pum) {
        var out = F.createPersistenceUnitMetadata();
        out.setXmlMappingMetadataComplete(F.createEmptyType());
        if (pum != null) {
            out.setDescription(keep(pum));
            PersistenceUnitDefaults d = pum.getPersistenceUnitDefaults();
            if (d != null) {
                var defaults = F.createPersistenceUnitDefaults();
                defaults.setDescription(keep(d));
                if (d.isDelimitedIdentifiers()) {
                    defaults.setDelimitedIdentifiers(F.createEmptyType());
                }
                if (d.isCascadePersist()) {
                    defaults.setCascadePersist(F.createEmptyType());
                }
                if (!d.getListener().isEmpty()) {
                    defaults.setEntityListeners(listeners(d.getListener()));
                }
                out.setPersistenceUnitDefaults(defaults);
            }
        }
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.SequenceGenerator sequenceGenerator(SequenceGenerator g) {
        var out = F.createSequenceGenerator();
        out.setName(g.getName());
        out.setDescription(keep(g));
        out.setSequenceName(g.getSequenceName());
        if (g.getCwmSchema() != null) {
            out.setSchema(g.getCwmSchema().getName());
            if (g.getCwmSchema().eContainer() instanceof ModelElement c) {
                out.setCatalog(c.getName());
            }
        }
        if (g.isSetInitialValue()) {
            out.setInitialValue((int) g.getInitialValue());
        }
        if (g.isSetAllocationSize()) {
            out.setAllocationSize((int) g.getAllocationSize());
        }
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.TableGenerator tableGenerator(TableGenerator g) {
        var out = F.createTableGenerator();
        out.setName(g.getName());
        out.setDescription(keep(g));
        if (g.getCwmPkColumn() != null) {
            out.setPkColumnName(g.getCwmPkColumn().getName());
            if (g.getCwmPkColumn().getOwner() instanceof NamedColumnSet t) {
                out.setTable(t.getName());
                out.setSchema(schemaOf(t));
                out.setCatalog(catalogOf(t));
            }
        }
        if (g.getCwmValueColumn() != null) {
            out.setValueColumnName(g.getCwmValueColumn().getName());
        }
        out.setPkColumnValue(g.getPkColumnValue());
        if (g.isSetInitialValue()) {
            out.setInitialValue((int) g.getInitialValue());
        }
        if (g.isSetAllocationSize()) {
            out.setAllocationSize((int) g.getAllocationSize());
        }
        notes.add(Note.warning(EormNotes.FENNEC_TABLE_GENERATOR, path(g), "table generator '" + g.getName()
                + "': Fennec reports table-generator as not read and leaves the stored values alone"));
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.NamedQuery namedQuery(NamedQuery q) {
        var out = F.createNamedQuery();
        out.setName(q.getName());
        out.setDescription(keep(q));
        out.setQuery(q.getQuery());
        if (q.isSetLockMode()) {
            out.setLockMode(LockModeType.getByName(q.getLockMode().getName()));
        }
        hints(q, out.getHint());
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.NamedNativeQuery nativeQuery(NamedNativeQuery q) {
        var out = F.createNamedNativeQuery();
        out.setName(q.getName());
        out.setDescription(keep(q));
        if (q.getCwmQuery() != null && q.getCwmQuery().getQuery() != null) {
            out.setQuery(q.getCwmQuery().getQuery().getBody());
        }
        if (q.getCwmResultClass() != null) {
            out.setResultClass(className(q.getCwmResultClass(), q));
        }
        if (q.getResultSetMapping() != null) {
            out.setResultSetMapping(q.getResultSetMapping().getName());
        }
        hints(q, out.getHint());
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.NamedStoredProcedureQuery procedureQuery(NamedStoredProcedureQuery q) {
        var out = F.createNamedStoredProcedureQuery();
        out.setName(q.getName());
        out.setDescription(keep(q));
        if (q.getCwmProcedure() != null) {
            out.setProcedureName(q.getCwmProcedure().getName());
        }
        for (Parameter p : OrmBindings.parameters(q)) {
            var sp = F.createStoredProcedureParameter();
            sp.setName(p.getName());
            sp.setMode(switch (p.getKind()) {
            case PDK_INOUT -> ParameterMode.INOUT;
            case PDK_OUT, PDK_RETURN -> ParameterMode.OUT;
            default -> ParameterMode.IN;
            });
            out.getParameter().add(sp);
        }
        if (!out.getParameter().isEmpty()) {
            notes.add(Note.info(EormNotes.PARAMETER_CLASS, path(q), out.getParameter().size()
                    + " parameter(s) without 'class': EORM expects an EClass, the parameters have SQL types"));
        }
        for (var c : q.getCwmResultClass()) {
            out.getResultClass().add(className(c, q));
        }
        for (SqlResultSetMapping m : q.getResultSetMapping()) {
            out.getResultSetMapping().add(m.getName());
        }
        hints(q, out.getHint());
        return out;
    }

    private void hints(Query q, EList<org.eclipse.fennec.persistence.eorm.QueryHint> out) {
        for (QueryHint h : q.getHint()) {
            var hint = F.createQueryHint();
            hint.setName(h.getName());
            hint.setValue(h.getValue());
            hint.setDescription(keep(h));
            out.add(hint);
        }
    }

    private org.eclipse.fennec.persistence.eorm.SqlResultSetMapping resultSetMapping(SqlResultSetMapping m) {
        var out = F.createSqlResultSetMapping();
        out.setName(m.getName());
        out.setDescription(keep(m));
        for (EntityResult er : m.getEntityResult()) {
            var r = F.createEntityResult();
            r.setEntityClass(className(er.getCwmClass(), er));
            if (er.getCwmDiscriminatorColumn() != null) {
                r.setDiscriminatorColumn(er.getCwmDiscriminatorColumn().getName());
            }
            for (FieldResult fr : er.getFieldResult()) {
                var f = F.createFieldResult();
                f.setName(featureName(fr.getCwmFeature()));
                f.setColumn(fr.getCwmColumn() == null ? null : fr.getCwmColumn().getName());
                r.getFieldResult().add(f);
            }
            out.getEntityResult().add(r);
        }
        for (ConstructorResult cr : m.getConstructorResult()) {
            var r = F.createConstructorResult();
            r.setTargetClass(className(cr.getCwmClass(), cr));
            for (ColumnResult c : cr.getColumnResult()) {
                r.getColumn().add(columnResult(c));
            }
            out.getConstructorResult().add(r);
        }
        for (ColumnResult c : m.getColumnResult()) {
            out.getColumnResult().add(columnResult(c));
        }
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.ColumnResult columnResult(ColumnResult c) {
        var out = F.createColumnResult();
        out.setName(c.getCwmColumn() == null ? c.getName() : c.getCwmColumn().getName());
        if (c.getCwmType() != null) {
            EClassifier type = index.eClassifier(c.getCwmType());
            if (type != null) {
                out.setClass(EcoreIndex.qualifiedName(type));
            }
        }
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.Converter converter(Converter c) {
        var out = F.createConverter();
        out.setDescription(keep(c));
        out.setClass(eClass(c.getCwmClass(), c));
        if (c.isSetAutoApply()) {
            out.setAutoApply(c.isAutoApply());
        }
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.Entity entity(Entity ce) {
        var out = F.createEntity();
        out.setName(ce.getName());
        EClass ec = eClass(ce.getCwmClass(), ce);
        out.setClass(ec);
        if (ec != null) {
            out.setAccessibleObject(classObject(ec));
        }
        out.setDescription(keep(ce));
        if (ce.isSetAccess()) {
            out.setAccess(access(ce.getAccess()));
        }
        if (ce.isSetCacheable()) {
            out.setCacheable(ce.isCacheable());
        }
        NamedColumnSet table = OrmBindings.tableOf(ce);
        if (table != null) {
            var t = F.createTable();
            t.setName(table.getName());
            t.setSchema(blankToNull(OrmBindings.schema(ce)));
            t.setCatalog(blankToNull(OrmBindings.catalog(ce)));
            out.setTable(t);
        }
        if (ce.isSetInheritanceStrategy()) {
            var inheritance = F.createInheritance();
            inheritance.setStrategy(InheritanceType.getByName(ce.getInheritanceStrategy().getName()));
            out.setInheritance(inheritance);
        }
        var discriminator = ce.getCwmDiscriminatorColumn();
        if (discriminator != null) {
            var dc = F.createDiscriminatorColumn();
            dc.setName(discriminator.getName());
            DiscriminatorType type = DiscriminatorType.getByName(OrmBindings.discriminatorType(ce));
            dc.setDiscriminatorType(type);
            if (type == DiscriminatorType.STRING && discriminator.getLength() > 0) {
                dc.setLength((int) discriminator.getLength());
            }
            out.setDiscriminatorColumn(dc);
        }
        if (ce.getDiscriminatorValue() != null) {
            out.setDiscriminatorValue(ce.getDiscriminatorValue());
        }
        if (ce.getPrimaryKeyJoin() != null) {
            primaryKeyJoinColumns(out.getPrimaryKeyJoinColumn(), ce.getPrimaryKeyJoin());
            out.setPrimaryKeyForeignKey(foreignKey(ce.getPrimaryKeyJoin()));
        }
        for (SecondaryTable st : ce.getSecondaryTable()) {
            out.getSecondaryTable().add(secondaryTable(st));
        }
        identifiable(ce, out);
        overrides(ce, out, ce);
        for (NamedEntityGraph g : ce.getNamedEntityGraph()) {
            out.getNamedEntityGraph().add(entityGraph(g));
        }
        var attributes = F.createAttributes();
        attributes(ce, attributes);
        out.setAttributes(attributes);
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.MappedSuperclass mappedSuperclass(MappedSuperclass ms) {
        var out = F.createMappedSuperclass();
        out.setClass(eClass(ms.getCwmClass(), ms));
        out.setDescription(keep(ms));
        if (ms.isSetAccess()) {
            out.setAccess(access(ms.getAccess()));
        }
        identifiable(ms, out);
        var attributes = F.createAttributes();
        attributes(ms, attributes);
        out.setAttributes(attributes);
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.Embeddable embeddable(Embeddable em) {
        var out = F.createEmbeddable();
        out.setClass(eClass(em.getCwmClass(), em));
        out.setDescription(keep(em));
        if (em.isSetAccess()) {
            out.setAccess(access(em.getAccess()));
        }
        var attributes = F.createEmbeddableAttributes();
        attributes(em, attributes);
        out.setAttributes(attributes);
        return out;
    }

    /** Id class, listeners and callbacks; the same features on EORM Entity and MappedSuperclass. */
    private void identifiable(IdentifiableType t, EObject out) {
        if (t.getCwmIdClass() != null) {
            var idClass = F.createIdClass();
            idClass.setClass(eClass(t.getCwmIdClass(), t));
            set(out, "idClass", idClass);
            notes.add(Note.warning(EormNotes.FENNEC_ID_CLASS, path(t), "id class "
                    + t.getCwmIdClass().getName() + ": Fennec does not deploy id-class (one id per key attribute)"));
        }
        if (t.isExcludeDefaultListeners()) {
            set(out, "excludeDefaultListeners", F.createEmptyType());
        }
        if (t.isExcludeSuperclassListeners()) {
            set(out, "excludeSuperclassListeners", F.createEmptyType());
        }
        if (!t.getListener().isEmpty()) {
            set(out, "entityListeners", listeners(t.getListener()));
        }
        callbacks(t.getLifecycleCallback(), out);
    }

    private org.eclipse.fennec.persistence.eorm.EntityListeners listeners(List<EntityListener> used) {
        var out = F.createEntityListeners();
        for (EntityListener l : used) {
            var el = F.createEntityListener();
            el.setClass(eClass(l.getCwmClass(), l));
            el.setDescription(keep(l));
            callbacks(l.getLifecycleCallback(), el);
            out.getEntityListener().add(el);
        }
        return out;
    }

    private void callbacks(List<LifecycleCallback> callbacks, EObject out) {
        for (LifecycleCallback cb : callbacks) {
            String feature = switch (cb.getKind()) {
            case PREPERSIST -> "prePersist";
            case POSTPERSIST -> "postPersist";
            case PREREMOVE -> "preRemove";
            case POSTREMOVE -> "postRemove";
            case PREUPDATE -> "preUpdate";
            case POSTUPDATE -> "postUpdate";
            case POSTLOAD -> "postLoad";
            };
            EStructuralFeature f = feature(out, feature);
            if (out.eIsSet(f)) {
                notes.add(Note.warning(EormNotes.CALLBACK, path(cb), "a second " + feature
                        + " callback; EORM keeps one per kind"));
                continue;
            }
            EObject callback = EcoreUtil.create((EClass) f.getEType());
            set(callback, "methodName", cb.getCwmOperation() == null ? cb.getName() : cb.getCwmOperation().getName());
            set(callback, "description", keep(cb));
            out.eSet(f, callback);
        }
    }

    private void overrides(OverrideHost host, EObject out, ManagedType owner) {
        for (AttributeOverride ao : host.getAttributeOverride()) {
            add(out, "attributeOverride", attributeOverride(ao, owner));
        }
        for (AssociationOverride ao : host.getAssociationOverride()) {
            add(out, "associationOverride", associationOverride(ao, owner));
        }
        for (Convert c : host.getConvert()) {
            add(out, "convert", convert(c, true));
        }
    }

    private org.eclipse.fennec.persistence.eorm.AttributeOverride attributeOverride(AttributeOverride ao,
            ManagedType owner) {
        var out = F.createAttributeOverride();
        out.setName(OrmBindings.path(ao.getCwmPath()));
        out.setDescription(keep(ao));
        if (ao.getColumn() != null) {
            out.setColumn(column(ao.getColumn(), owner));
        }
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.AssociationOverride associationOverride(AssociationOverride ao,
            ManagedType owner) {
        var out = F.createAssociationOverride();
        out.setName(OrmBindings.path(ao.getCwmPath()));
        out.setDescription(keep(ao));
        if (ao.getJoin() != null) {
            joinColumns(out.getJoinColumn(), ao.getJoin(), primaryTable(owner));
            out.setForeignKey(foreignKey(ao.getJoin()));
        }
        if (ao.getJoinTable() != null) {
            out.setJoinTable(joinTable(ao.getJoinTable()));
        }
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.Convert convert(Convert c, boolean withPath) {
        var out = F.createConvert();
        out.setDescription(keep(c));
        if (withPath && !c.getCwmPath().isEmpty()) {
            out.setAttributeName(OrmBindings.path(c.getCwmPath()));
        }
        if (c.getConverter() != null) {
            EClass ec = index.eClass(c.getConverter().getCwmClass());
            out.setConverter(ec != null ? EcoreIndex.qualifiedName(ec) : c.getConverter().getName());
        }
        if (c.isSetDisableConversion()) {
            out.setDisableConversion(c.isDisableConversion());
        }
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.NamedEntityGraph entityGraph(NamedEntityGraph g) {
        var out = F.createNamedEntityGraph();
        out.setName(g.getName());
        if (g.isSetIncludeAllAttributes()) {
            out.setIncludeAllAttributes(g.isIncludeAllAttributes());
        }
        for (NamedAttributeNode n : g.getNamedAttributeNode()) {
            out.getNamedAttributeNode().add(attributeNode(n));
        }
        for (NamedSubgraph s : g.getSubgraph()) {
            out.getSubgraph().add(subgraph(s));
        }
        for (NamedSubgraph s : g.getSubclassSubgraph()) {
            out.getSubclassSubgraph().add(subgraph(s));
        }
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.NamedAttributeNode attributeNode(NamedAttributeNode n) {
        var out = F.createNamedAttributeNode();
        out.setName(featureName(n.getCwmFeature()));
        if (n.getSubgraph() != null) {
            out.setSubgraph(n.getSubgraph().getName());
        }
        if (n.getKeySubgraph() != null) {
            out.setKeySubgraph(n.getKeySubgraph().getName());
        }
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.NamedSubgraph subgraph(NamedSubgraph s) {
        var out = F.createNamedSubgraph();
        out.setName(s.getName());
        out.setClass(className(s.getCwmClass(), s));
        for (NamedAttributeNode n : s.getNamedAttributeNode()) {
            out.getNamedAttributeNode().add(attributeNode(n));
        }
        return out;
    }

    /** Fills EORM {@code Attributes} or {@code EmbeddableAttributes}; they share the feature names. */
    private void attributes(ManagedType mt, EObject out) {
        if (mt instanceof IdentifiableType it) {
            for (Id id : it.getId()) {
                add(out, "id", id(id, mt));
            }
            if (it.getEmbeddedId() != null) {
                set(out, "embeddedId", embeddedId(it.getEmbeddedId(), mt));
            }
            for (Version v : it.getVersion()) {
                add(out, "version", version(v, mt));
            }
        }
        for (Basic b : mt.getBasic()) {
            add(out, "basic", basic(b, mt));
        }
        for (ManyToOne m : mt.getManyToOne()) {
            add(out, "manyToOne", manyToOne(m, mt));
        }
        for (OneToMany m : mt.getOneToMany()) {
            add(out, "oneToMany", oneToMany(m));
        }
        for (OneToOne m : mt.getOneToOne()) {
            add(out, "oneToOne", oneToOne(m, mt));
        }
        for (ManyToMany m : mt.getManyToMany()) {
            add(out, "manyToMany", manyToMany(m));
        }
        for (ElementCollection ec : mt.getElementCollection()) {
            add(out, "elementCollection", elementCollection(ec, mt));
        }
        for (Embedded e : mt.getEmbedded()) {
            add(out, "embedded", embedded(e, mt));
        }
        for (Transient t : mt.getTransient()) {
            var tr = F.createTransient();
            tr.setName(name(t));
            add(out, "transient", tr);
        }
    }

    private void base(Base b, org.eclipse.fennec.persistence.eorm.Base out, ManagedType owner) {
        out.setName(name(b));
        out.setAccessibleObject(featureObject(b.getCwmAttribute(), b));
        if (b.isSetAccess()) {
            out.setAccess(access(b.getAccess()));
        }
        if (b.getColumn() != null) {
            out.setColumn(column(b.getColumn(), owner));
        }
        TemporalType temporal = temporal(b.getCwmAttribute(), b.getColumn());
        if (temporal != null) {
            out.setTemporal(temporal);
        }
    }

    private void simple(SimpleBase sb, org.eclipse.fennec.persistence.eorm.SimpleBase out) {
        if (sb.isSetFetch()) {
            out.setFetch(FetchType.getByName(sb.getFetch().getName()));
        }
        if (isLob(sb.getColumn())) {
            out.setLob(F.createLob());
        }
        EStructuralFeature feature = index.feature(sb.getCwmAttribute());
        EEnum enumType = feature != null && feature.getEType() instanceof EEnum e ? e
                : sb.getCwmAttribute() == null ? null : index.eEnum(sb.getCwmAttribute().getType());
        if (enumType != null) {
            out.setEnumerated(enumType);
        }
        if (sb.isSetEnumerated() && sb.getEnumerated() == org.eclipse.daanse.cwm.model.daanse.orm.EnumType.STRING) {
            notes.add(Note.warning(EormNotes.ENUM_STRING, path(sb), "enumerated STRING: EORM's enumerated names only"
                    + " the EEnum, the storage as name cannot be expressed"));
        }
    }

    private org.eclipse.fennec.persistence.eorm.Id id(Id id, ManagedType owner) {
        var out = F.createId();
        base(id, out, owner);
        GeneratedValue gv = id.getGeneratedValue();
        if (gv != null) {
            var g = F.createGeneratedValue();
            if (gv.isSetStrategy()) {
                g.setStrategy(GenerationType.getByName(gv.getStrategy().getName()));
            }
            Generator generator = OrmBindings.generatorOf(gv);
            if (generator != null) {
                g.setGenerator(generator.getName());
            }
            out.setGeneratedValue(g);
        }
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.Basic basic(Basic b, ManagedType owner) {
        var out = F.createBasic();
        base(b, out, owner);
        simple(b, out);
        out.setOptional(OrmBindings.optional(b));
        if (b.getConvert() != null) {
            out.setConvert(convert(b.getConvert(), false));
        }
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.Version version(Version v, ManagedType owner) {
        var out = F.createVersion();
        out.setName(name(v));
        if (v.isSetAccess()) {
            out.setAccess(access(v.getAccess()));
        }
        if (v.getColumn() != null) {
            out.setColumn(column(v.getColumn(), owner));
        }
        TemporalType temporal = temporal(v.getCwmAttribute(), v.getColumn());
        if (temporal != null) {
            out.setTemporal(temporal);
        }
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.Embedded embedded(Embedded e, ManagedType owner) {
        var out = F.createEmbedded();
        out.setName(name(e));
        if (e.isSetAccess()) {
            out.setAccess(access(e.getAccess()));
        }
        overrides(e, out, owner);
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.EmbeddedId embeddedId(EmbeddedId e, ManagedType owner) {
        var out = F.createEmbeddedId();
        out.setName(name(e));
        if (e.isSetAccess()) {
            out.setAccess(access(e.getAccess()));
        }
        for (AttributeOverride ao : e.getAttributeOverride()) {
            out.getAttributeOverride().add(attributeOverride(ao, owner));
        }
        String lost = e.getAssociationOverride().isEmpty() && e.getConvert().isEmpty() ? ""
                : "; its association overrides and converts have no place in EORM";
        notes.add(Note.warning(EormNotes.FENNEC_EMBEDDED_ID, path(e), "embedded id '" + out.getName()
                + "': Fennec does not deploy embedded-id (one id per key attribute)" + lost));
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.ElementCollection elementCollection(ElementCollection ec,
            ManagedType owner) {
        var out = F.createElementCollection();
        base(ec, out, null);
        simple(ec, out);
        EStructuralFeature feature = index.feature(ec.getCwmAttribute());
        if (feature != null && feature.getEType() != null) {
            out.setTargetClass(EcoreIndex.qualifiedName(feature.getEType()));
        }
        collection(ec, out);
        for (AttributeOverride ao : ec.getAttributeOverride()) {
            out.getAttributeOverride().add(attributeOverride(ao, null));
        }
        for (AssociationOverride ao : ec.getAssociationOverride()) {
            out.getAssociationOverride().add(associationOverride(ao, null));
        }
        if (!ec.getConvert().isEmpty()) {
            out.setConvert(convert(ec.getConvert().get(0), true));
            if (ec.getConvert().size() > 1) {
                notes.add(Note.warning(EormNotes.CONVERT, path(ec), ec.getConvert().size()
                        + " converts; EORM keeps the first"));
            }
        }
        if (ec.getCollectionTable() != null) {
            CollectionTable ct = ec.getCollectionTable();
            var table = F.createCollectionTable();
            tableNames(OrmBindings.tableOf(ct), table);
            joinColumns(table.getJoinColumn(), ct.getJoin(), null);
            table.setForeignKey(foreignKey(ct.getJoin()));
            out.setCollectionTable(table);
        }
        return out;
    }

    private void relationship(RelationshipMapping r, org.eclipse.fennec.persistence.eorm.BaseRef out) {
        out.setName(name(r));
        out.setAccessibleObject(featureObject(r.getCwmAssociationEnd(), r));
        if (r.isSetAccess()) {
            out.setAccess(access(r.getAccess()));
        }
        if (r.isSetFetch()) {
            out.setFetch(FetchType.getByName(r.getFetch().getName()));
        }
        if (!r.getCascade().isEmpty()) {
            var cascade = F.createCascadeType();
            for (CascadeKind k : r.getCascade()) {
                switch (k) {
                case ALL -> cascade.setCascadeAll(F.createEmptyType());
                case PERSIST -> cascade.setCascadePersist(F.createEmptyType());
                case MERGE -> cascade.setCascadeMerge(F.createEmptyType());
                case REMOVE -> cascade.setCascadeRemove(F.createEmptyType());
                case REFRESH -> cascade.setCascadeRefresh(F.createEmptyType());
                case DETACH -> cascade.setCascadeDetach(F.createEmptyType());
                }
            }
            out.setCascade(cascade);
        }
        if (r.getJoinTable() != null) {
            out.setJoinTable(joinTable(r.getJoinTable()));
        }
        AssociationEnd end = r.getCwmAssociationEnd();
        EClass target = end == null ? null : index.eClass(end.getType());
        if (end != null && target == null) {
            unresolved(end.getType(), r);
        }
        set(out, "targetEntity", target == null ? null : EcoreIndex.qualifiedName(target));
        if (r instanceof OwnableRelationship o && !o.isOwningSide()) {
            set(out, "mappedBy", mappedBy(end));
        }
    }

    /** The name of the other end of the association: the attribute of the owning side. */
    private String mappedBy(AssociationEnd end) {
        if (end == null || end.getOwner() == null) {
            return null;
        }
        for (Feature f : end.getOwner().getFeature()) {
            if (f instanceof AssociationEnd other && other != end) {
                return featureName(other);
            }
        }
        return null;
    }

    private org.eclipse.fennec.persistence.eorm.ManyToOne manyToOne(ManyToOne m, ManagedType owner) {
        var out = F.createManyToOne();
        relationship(m, out);
        out.setOptional(OrmBindings.optional(m));
        if (m.getJoin() != null) {
            joinColumns(out.getJoinColumn(), m.getJoin(), primaryTable(owner));
            out.setForeignKey(foreignKey(m.getJoin()));
        }
        if (m.isSetId()) {
            out.setId(m.isId());
        }
        if (m.isMapsId()) {
            out.setMapsId(m.getCwmMapsIdAttribute() == null ? "" : featureName(m.getCwmMapsIdAttribute()));
        }
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.OneToOne oneToOne(OneToOne m, ManagedType owner) {
        var out = F.createOneToOne();
        relationship(m, out);
        out.setOptional(OrmBindings.optional(m));
        if (m.getJoin() != null) {
            joinColumns(out.getJoinColumn(), m.getJoin(), primaryTable(owner));
            out.setForeignKey(foreignKey(m.getJoin()));
        }
        if (m.getPrimaryKeyJoin() != null) {
            primaryKeyJoinColumns(out.getPrimaryKeyJoinColumn(), m.getPrimaryKeyJoin());
            out.setPrimaryKeyForeignKey(foreignKey(m.getPrimaryKeyJoin()));
        }
        if (m.isSetId()) {
            out.setId(m.isId());
        }
        if (m.isMapsId()) {
            out.setMapsId(m.getCwmMapsIdAttribute() == null ? "" : featureName(m.getCwmMapsIdAttribute()));
        }
        if (m.isSetOrphanRemoval()) {
            out.setOrphanRemoval(m.isOrphanRemoval());
        }
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.OneToMany oneToMany(OneToMany m) {
        var out = F.createOneToMany();
        relationship(m, out);
        if (m.getJoin() != null) {
            joinColumns(out.getJoinColumn(), m.getJoin(), null);
            out.setForeignKey(foreignKey(m.getJoin()));
        }
        if (m.isSetOrphanRemoval()) {
            out.setOrphanRemoval(m.isOrphanRemoval());
        }
        collection(m, out);
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.ManyToMany manyToMany(ManyToMany m) {
        var out = F.createManyToMany();
        relationship(m, out);
        collection(m, out);
        return out;
    }

    /** Order and map key of a collection; EORM OneToMany, ManyToMany and ElementCollection share the feature names. */
    private void collection(CollectionSupport cs, EObject out) {
        if (!cs.getOrderBy().isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Ordering o : cs.getOrderBy()) {
                parts.add(OrmBindings.path(o.getCwmPath()) + (o.isDescending() ? " DESC" : ""));
            }
            set(out, "orderBy", String.join(", ", parts));
        }
        if (cs.getOrderColumn() != null) {
            var oc = F.createOrderColumn();
            var col = cs.getOrderColumn().getCwmColumn();
            oc.setName(col == null ? cs.getOrderColumn().getName() : col.getName());
            if (col != null) {
                oc.setNullable(col.getIsNullable() != NullableType.COLUMN_NO_NULLS);
            }
            flags(cs.getOrderColumn(), oc);
            set(out, "orderColumn", oc);
        }
        MapKey mk = cs.getMapKey();
        if (mk != null) {
            mapKey(mk, out);
        }
    }

    private void mapKey(MapKey mk, EObject out) {
        if (mk.getCwmAttribute() != null) {
            var key = F.createMapKey();
            key.setName(featureName(mk.getCwmAttribute()));
            set(out, "mapKey", key);
        }
        if (mk.getCwmClassifier() != null) {
            if (index.eClassifier(mk.getCwmClassifier()) instanceof EClass ec) {
                var key = F.createMapKeyClass();
                key.setClass(ec);
                set(out, "mapKeyClass", key);
            } else {
                notes.add(Note.info(EormNotes.MAP_KEY_CLASS, path(mk), "map key type "
                        + mk.getCwmClassifier().getName() + " is no class; EORM's mapKeyClass needs an EClass"));
            }
        }
        if (mk.isSetEnumerated()) {
            set(out, "mapKeyEnumerated", EnumType.getByName(mk.getEnumerated().getName()));
        }
        if (mk.getColumn() != null && mk.getColumn().getCwmColumn() != null) {
            var col = mk.getColumn().getCwmColumn();
            var key = F.createMapKeyColumn();
            key.setName(col.getName());
            key.setNullable(col.getIsNullable() != NullableType.COLUMN_NO_NULLS);
            if (mk.getColumn().isSetInsertable()) {
                key.setInsertable(mk.getColumn().isInsertable());
            }
            if (mk.getColumn().isSetUpdatable()) {
                key.setUpdatable(mk.getColumn().isUpdatable());
            }
            if (col.getLength() > 0) {
                key.setLength((int) col.getLength());
            }
            if (col.getPrecision() > 0) {
                key.setPrecision((int) col.getPrecision());
            }
            if (col.getScale() > 0) {
                key.setScale((int) col.getScale());
            }
            set(out, "mapKeyColumn", key);
        }
        if (mk.getJoin() != null) {
            for (JoinPair p : OrmBindings.joinColumns(mk.getJoin())) {
                var jc = F.createMapKeyJoinColumn();
                jc.setName(p.column().getName());
                if (p.referenced() != null) {
                    jc.setReferencedColumnName(p.referenced().getName());
                }
                jc.setNullable(p.column().getIsNullable() != NullableType.COLUMN_NO_NULLS);
                add(out, "mapKeyJoinColumn", jc);
            }
            set(out, "mapKeyForeignKey", foreignKey(mk.getJoin()));
        }
        for (AttributeOverride ao : mk.getAttributeOverride()) {
            add(out, "mapKeyAttributeOverride", attributeOverride(ao, null));
        }
        for (Convert c : mk.getConvert()) {
            add(out, "mapKeyConvert", convert(c, true));
        }
    }

    private org.eclipse.fennec.persistence.eorm.Column column(Column c, ManagedType owner) {
        var out = F.createColumn();
        var col = c.getCwmColumn();
        if (col == null) {
            out.setName(c.getName());
            return out;
        }
        out.setName(col.getName());
        NamedColumnSet own = primaryTable(owner);
        if (own != null && col.getOwner() instanceof NamedColumnSet t && t != own) {
            out.setTable(t.getName());
        }
        out.setNullable(col.getIsNullable() != NullableType.COLUMN_NO_NULLS);
        flags(c, out);
        if (col.getLength() > 0) {
            out.setLength((int) col.getLength());
        }
        if (col.getPrecision() > 0) {
            out.setPrecision((int) col.getPrecision());
        }
        if (col.getScale() > 0) {
            out.setScale((int) col.getScale());
        }
        return out;
    }

    private static void flags(Column c, org.eclipse.fennec.persistence.eorm.BaseColumn out) {
        if (c.isSetInsertable()) {
            out.setInsertable(c.isInsertable());
        }
        if (c.isSetUpdatable()) {
            out.setUpdatable(c.isUpdatable());
        }
    }

    /** The primary table of an entity (the hierarchy's under SINGLE_TABLE), else {@code null}. */
    private static NamedColumnSet primaryTable(ManagedType owner) {
        return owner instanceof Entity ? OrmBindings.tableOf(owner) : null;
    }

    private void joinColumns(EList<org.eclipse.fennec.persistence.eorm.JoinColumn> out, Join join,
            NamedColumnSet ownTable) {
        for (JoinPair p : OrmBindings.joinColumns(join)) {
            var jc = F.createJoinColumn();
            jc.setName(p.column().getName());
            if (p.referenced() != null) {
                jc.setReferencedColumnName(p.referenced().getName());
            }
            jc.setNullable(p.column().getIsNullable() != NullableType.COLUMN_NO_NULLS);
            if (join.isSetInsertable()) {
                jc.setInsertable(join.isInsertable());
            }
            if (join.isSetUpdatable()) {
                jc.setUpdatable(join.isUpdatable());
            }
            if (ownTable != null && p.column().getOwner() instanceof NamedColumnSet t && t != ownTable) {
                jc.setTable(t.getName());
            }
            out.add(jc);
        }
    }

    private void primaryKeyJoinColumns(EList<org.eclipse.fennec.persistence.eorm.PrimaryKeyJoinColumn> out,
            Join join) {
        for (JoinPair p : OrmBindings.joinColumns(join)) {
            var jc = F.createPrimaryKeyJoinColumn();
            jc.setName(p.column().getName());
            if (p.referenced() != null) {
                jc.setReferencedColumnName(p.referenced().getName());
            }
            out.add(jc);
        }
        if (join.isSetInsertable() || join.isSetUpdatable()) {
            notes.add(Note.info(EormNotes.PK_JOIN_FLAGS, path(join), "insertable/updatable of a primary-key join;"
                    + " EORM primary-key join columns have neither"));
        }
    }

    /** The foreign key of a join: the catalog constraint by name, or {@code NO_CONSTRAINT} for bare column pairs. */
    private org.eclipse.fennec.persistence.eorm.ForeignKey foreignKey(Join join) {
        var out = F.createForeignKey();
        out.setDescription(keep(join));
        if (join.getCwmForeignKey() != null) {
            out.setName(join.getCwmForeignKey().getName());
            out.setConstraintMode(ConstraintMode.CONSTRAINT);
        } else {
            out.setConstraintMode(ConstraintMode.NOCONSTRAINT);
        }
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.SecondaryTable secondaryTable(SecondaryTable st) {
        var out = F.createSecondaryTable();
        NamedColumnSet t = OrmBindings.tableOf(st);
        if (t != null) {
            out.setName(t.getName());
            out.setSchema(schemaOf(t));
            out.setCatalog(catalogOf(t));
        }
        primaryKeyJoinColumns(out.getPrimaryKeyJoinColumn(), st.getJoin());
        out.setPrimaryKeyForeignKey(foreignKey(st.getJoin()));
        return out;
    }

    private org.eclipse.fennec.persistence.eorm.JoinTable joinTable(JoinTable jt) {
        var out = F.createJoinTable();
        tableNames(OrmBindings.tableOf(jt), out);
        joinColumns(out.getJoinColumn(), jt.getJoin(), null);
        out.setForeignKey(foreignKey(jt.getJoin()));
        joinColumns(out.getInverseJoinColumn(), jt.getInverseJoin(), null);
        out.setInverseForeignKey(foreignKey(jt.getInverseJoin()));
        return out;
    }

    /** Name, schema and catalog of a table into an EORM table-like element. */
    private static void tableNames(NamedColumnSet t, EObject out) {
        if (t != null) {
            set(out, "name", t.getName());
            set(out, "schema", schemaOf(t));
            set(out, "catalog", catalogOf(t));
        }
    }

    /** Schema of a table: the namespace it lies in (as {@link OrmBindings#schema} reads it). */
    private static String schemaOf(NamedColumnSet t) {
        return t.eContainer() instanceof ModelElement s ? s.getName() : null;
    }

    /** Catalog of a table: the namespace of its schema (as {@link OrmBindings#catalog} reads it). */
    private static String catalogOf(NamedColumnSet t) {
        return t.eContainer() != null && t.eContainer().eContainer() instanceof ModelElement c ? c.getName() : null;
    }

    private TemporalType temporal(Attribute attribute, Column column) {
        if (column == null || column.getCwmColumn() == null) {
            return null;
        }
        EStructuralFeature f = index.feature(attribute);
        String javaType = f == null || f.getEType() == null ? null : f.getEType().getInstanceClassName();
        if (!"java.util.Date".equals(javaType) && !"java.util.Calendar".equals(javaType)) {
            return null;
        }
        String sql = sqlType(column);
        if (sql.startsWith("TIMESTAMP")) {
            return TemporalType.TIMESTAMP;
        }
        if (sql.equals("DATE")) {
            return TemporalType.DATE;
        }
        if (sql.startsWith("TIME")) {
            return TemporalType.TIME;
        }
        return null;
    }

    private static boolean isLob(Column column) {
        String sql = column == null || column.getCwmColumn() == null ? "" : sqlType(column);
        return sql.equals("BLOB") || sql.equals("CLOB") || sql.equals("NCLOB") || sql.contains("LARGE OBJECT");
    }

    private static String sqlType(Column column) {
        Classifier type = column.getCwmColumn().getType();
        return type == null || type.getName() == null ? "" : type.getName().trim().toUpperCase(Locale.ROOT);
    }

    private EClass eClass(ModelElement cls, OrmElement where) {
        if (cls == null) {
            return null;
        }
        EClass ec = index.eClass(cls);
        if (ec == null) {
            unresolved(cls, where);
        }
        return ec;
    }

    private String className(ModelElement cls, OrmElement where) {
        EClass ec = eClass(cls, where);
        return ec == null ? null : EcoreIndex.qualifiedName(ec);
    }

    private static EClassObject classObject(EClass ec) {
        EClassObject o = F.createEClassObject();
        o.setEclass(ec);
        o.setName(EcoreIndex.qualifiedName(ec));
        return o;
    }

    private EFeatureObject featureObject(StructuralFeature f, OrmElement where) {
        if (f == null) {
            return null;
        }
        EStructuralFeature ef = index.feature(f);
        if (ef == null) {
            unresolved(f, where);
            return null;
        }
        EFeatureObject o = F.createEFeatureObject();
        o.setFeature(ef);
        o.setName(ef.getName());
        return o;
    }

    /** The EORM attribute name: the name of the Ecore feature the mapping maps (Fennec resolves by it). */
    private String name(AttributeMapping m) {
        StructuralFeature f = OrmBindings.featureOf(m);
        return f == null ? m.getName() : featureName(f);
    }

    private String featureName(StructuralFeature f) {
        if (f == null) {
            return null;
        }
        EStructuralFeature ef = index.feature(f);
        return ef != null ? ef.getName() : f.getName();
    }

    private void unresolved(ModelElement cwmElement, OrmElement where) {
        notes.add(Note.error(EormNotes.UNRESOLVED, path(where), "no Ecore element for "
                + (cwmElement == null ? "(none)" : CwmPath.of(cwmElement))
                + " — is its package part of the converted Ecore?"));
    }

    private static AccessType access(org.eclipse.daanse.cwm.model.daanse.orm.AccessType a) {
        return AccessType.getByName(a.getName());
    }

    private String keep(OrmElement e) {
        kept.add(e);
        String d = e.getDescription();
        return d == null || d.isBlank() ? null : d;
    }

    private void reportLostDescriptions() {
        int lost = 0;
        for (Iterator<EObject> it = cwm.eAllContents(); it.hasNext();) {
            if (it.next() instanceof OrmElement e && !kept.contains(e) && e.getDescription() != null
                    && !e.getDescription().isBlank()) {
                lost++;
            }
        }
        if (lost > 0) {
            notes.add(Note.info(EormNotes.DESCRIPTION, path(cwm), lost + " description(s) of mapping elements"
                    + " without a description in EORM (attribute mappings, columns, join tables, results) are dropped"));
        }
    }

    private static String path(ModelElement e) {
        return CwmPath.of(e);
    }

    private static String blankToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static EStructuralFeature feature(EObject o, String name) {
        EStructuralFeature f = o.eClass().getEStructuralFeature(name);
        if (f == null) {
            throw new IllegalStateException(o.eClass().getName() + " has no feature " + name);
        }
        return f;
    }

    private static void set(EObject o, String feature, Object value) {
        if (value != null) {
            o.eSet(feature(o, feature), value);
        }
    }

    @SuppressWarnings("unchecked")
    private static void add(EObject o, String feature, EObject value) {
        ((List<EObject>) o.eGet(feature(o, feature))).add(value);
    }
}
