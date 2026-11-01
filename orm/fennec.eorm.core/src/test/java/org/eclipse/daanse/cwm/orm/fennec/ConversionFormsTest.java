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

import java.util.List;

import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.CallbackKind;
import org.eclipse.daanse.cwm.model.daanse.orm.CascadeKind;
import org.eclipse.daanse.cwm.model.daanse.orm.CollectionTable;
import org.eclipse.daanse.cwm.model.daanse.orm.Converter;
import org.eclipse.daanse.cwm.model.daanse.orm.ElementCollection;
import org.eclipse.daanse.cwm.model.daanse.orm.Embeddable;
import org.eclipse.daanse.cwm.model.daanse.orm.Embedded;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityListener;
import org.eclipse.daanse.cwm.model.daanse.orm.EnumType;
import org.eclipse.daanse.cwm.model.daanse.orm.FetchType;
import org.eclipse.daanse.cwm.model.daanse.orm.GenerationType;
import org.eclipse.daanse.cwm.model.daanse.orm.Id;
import org.eclipse.daanse.cwm.model.daanse.orm.InheritanceType;
import org.eclipse.daanse.cwm.model.daanse.orm.ManyToMany;
import org.eclipse.daanse.cwm.model.daanse.orm.ManyToOne;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedEntityGraph;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedStoredProcedureQuery;
import org.eclipse.daanse.cwm.model.daanse.orm.OneToMany;
import org.eclipse.daanse.cwm.model.daanse.orm.PersistenceUnitDefaults;
import org.eclipse.daanse.cwm.model.daanse.orm.PersistenceUnitMetadata;
import org.eclipse.daanse.cwm.model.daanse.orm.SequenceGenerator;
import org.eclipse.daanse.cwm.model.daanse.orm.SqlResultSetMapping;
import org.eclipse.daanse.cwm.model.daanse.orm.util.Basics;
import org.eclipse.daanse.cwm.model.daanse.orm.util.Columns;
import org.eclipse.daanse.cwm.model.daanse.orm.util.Converters;
import org.eclipse.daanse.cwm.model.daanse.orm.util.Converts;
import org.eclipse.daanse.cwm.model.daanse.orm.util.ElementCollections;
import org.eclipse.daanse.cwm.model.daanse.orm.util.Embeddables;
import org.eclipse.daanse.cwm.model.daanse.orm.util.EmbeddedIds;
import org.eclipse.daanse.cwm.model.daanse.orm.util.Embeddeds;
import org.eclipse.daanse.cwm.model.daanse.orm.util.Entities;
import org.eclipse.daanse.cwm.model.daanse.orm.util.EntityGraphs;
import org.eclipse.daanse.cwm.model.daanse.orm.util.Generators;
import org.eclipse.daanse.cwm.model.daanse.orm.util.Ids;
import org.eclipse.daanse.cwm.model.daanse.orm.util.JoinedTables;
import org.eclipse.daanse.cwm.model.daanse.orm.util.Joins;
import org.eclipse.daanse.cwm.model.daanse.orm.util.Listeners;
import org.eclipse.daanse.cwm.model.daanse.orm.util.ManyToManys;
import org.eclipse.daanse.cwm.model.daanse.orm.util.ManyToOnes;
import org.eclipse.daanse.cwm.model.daanse.orm.util.MapKeys;
import org.eclipse.daanse.cwm.model.daanse.orm.util.OneToManys;
import org.eclipse.daanse.cwm.model.daanse.orm.util.OneToOnes;
import org.eclipse.daanse.cwm.model.daanse.orm.util.Orderings;
import org.eclipse.daanse.cwm.model.daanse.orm.util.Overrides;
import org.eclipse.daanse.cwm.model.daanse.orm.util.Queries;
import org.eclipse.daanse.cwm.model.daanse.orm.util.ResultSets;
import org.eclipse.daanse.cwm.model.daanse.orm.util.Transients;
import org.eclipse.daanse.cwm.model.daanse.orm.util.Versions;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.fennec.persistence.eorm.ConstraintMode;
import org.eclipse.fennec.persistence.eorm.DiscriminatorType;
import org.eclipse.fennec.persistence.eorm.EClassObject;
import org.eclipse.fennec.persistence.eorm.EFeatureObject;
import org.eclipse.fennec.persistence.eorm.Entity;
import org.eclipse.fennec.persistence.eorm.ParameterMode;
import org.eclipse.fennec.persistence.eorm.TemporalType;
import org.junit.jupiter.api.Test;

/**
 * One test per conversion form: each builds the CWM-ORM input with the create helpers on the
 * {@link Fixture} library and checks the EORM output, with the Ecore elements of the same class
 * model found by names.
 */
class ConversionFormsTest {

    @Test
    void unitLevelPackageSchemaCatalogVersionAndMetadataComplete() {
        Fixture f = new Fixture();
        Entities.create(f.mappings, f.publisher, f.publisherTable);
        Entities.create(f.mappings, f.author, f.authorTable);
        f.mappings.setAccess(org.eclipse.daanse.cwm.model.daanse.orm.AccessType.FIELD);

        var em = f.convert().eorm();

        assertThat(em.getName()).isEqualTo("library");
        assertThat(em.getPackage()).isEqualTo("library");
        assertThat(em.getSchema()).isEqualTo("LIB");
        assertThat(em.getCatalog()).isEqualTo("LIBDB");
        assertThat(em.getVersion()).isEqualTo("3.1");
        assertThat(em.getAccess()).isEqualTo(org.eclipse.fennec.persistence.eorm.AccessType.FIELD);
        assertThat(em.getPersistenceUnitMetadata().getXmlMappingMetadataComplete()).isNotNull();
    }

    @Test
    void persistenceUnitDefaultsWithListeners() {
        Fixture f = new Fixture();
        PersistenceUnitMetadata pum = CWMORMFactory.eINSTANCE.createPersistenceUnitMetadata();
        PersistenceUnitDefaults defaults = CWMORMFactory.eINSTANCE.createPersistenceUnitDefaults();
        defaults.setDelimitedIdentifiers(true);
        defaults.setCascadePersist(true);
        pum.setPersistenceUnitDefaults(defaults);
        f.mappings.setPersistenceUnitMetadata(pum);
        EntityListener audit = Listeners.create(f.mappings, f.audit);
        Listeners.callback(audit, CallbackKind.PREPERSIST, f.onPersist);
        Listeners.defaults(defaults, audit);

        var d = f.convert().eorm().getPersistenceUnitMetadata().getPersistenceUnitDefaults();

        assertThat(d.getDelimitedIdentifiers()).isNotNull();
        assertThat(d.getCascadePersist()).isNotNull();
        assertThat(d.getEntityListeners().getEntityListener()).hasSize(1);
        assertThat(d.getEntityListeners().getEntityListener().get(0).getPrePersist().getMethodName())
                .isEqualTo("onPersist");
    }

    @Test
    void entityReferencesItsEClassAndNamesItsTable() {
        Fixture f = new Fixture();
        var e = Entities.create(f.mappings, "Verlag", f.publisher, f.publisherTable);
        e.setCacheable(true);
        EcoreIndex index = f.index();

        Entity out = CwmOrmToEorm.convert(f.mappings, index, true).eorm().getEntity().get(0);

        assertThat(out.getName()).isEqualTo("Verlag");
        assertThat(out.getClass_()).isSameAs(index.eClass(f.publisher));
        assertThat(((EClassObject) out.getAccessibleObject()).getEclass()).isSameAs(index.eClass(f.publisher));
        assertThat(out.getAccessibleObject().getName()).isEqualTo("library.Publisher");
        assertThat(out.getTable().getName()).isEqualTo("PUBLISHER");
        assertThat(out.getTable().getSchema()).isEqualTo("LIB");
        assertThat(out.getTable().getCatalog()).isEqualTo("LIBDB");
        assertThat(out.isCacheable()).isTrue();
    }

    @Test
    void singleTableHierarchyWithIntegerDiscriminator() {
        Fixture f = new Fixture();
        var root = Entities.create(f.mappings, f.item, f.itemTable);
        root.setInheritanceStrategy(InheritanceType.SINGLETABLE);
        root.setCwmDiscriminatorColumn(f.dtype);
        var book = Entities.create(f.mappings, f.book, null);
        book.setDiscriminatorValue("1");

        var em = f.convert().eorm();

        Entity item = em.getEntity().get(0);
        assertThat(item.getInheritance().getStrategy())
                .isEqualTo(org.eclipse.fennec.persistence.eorm.InheritanceType.SINGLETABLE);
        assertThat(item.getDiscriminatorColumn().getName()).isEqualTo("DTYPE");
        assertThat(item.getDiscriminatorColumn().getDiscriminatorType()).isEqualTo(DiscriminatorType.INTEGER);
        Entity b = em.getEntity().get(1);
        assertThat(b.getDiscriminatorValue()).isEqualTo("1");
        assertThat(b.getTable().getName()).as("the hierarchy's table").isEqualTo("ITEM");
        assertThat(b.getInheritance()).isNull();
    }

    @Test
    void discriminatorTypeFollowsTheSqlType() {
        Fixture f = new Fixture();
        f.kind.setLength(1);
        var root = Entities.create(f.mappings, f.item, f.itemTable);
        root.setInheritanceStrategy(InheritanceType.SINGLETABLE);
        root.setCwmDiscriminatorColumn(f.kind);

        assertThat(f.convert().eorm().getEntity().get(0).getDiscriminatorColumn().getDiscriminatorType())
                .isEqualTo(DiscriminatorType.CHAR);

        f.kind.setType(f.varchar);
        f.kind.setLength(20);
        var dc = f.convert().eorm().getEntity().get(0).getDiscriminatorColumn();
        assertThat(dc.getDiscriminatorType()).isEqualTo(DiscriminatorType.STRING);
        assertThat(dc.getLength()).isEqualTo(20);
    }

    @Test
    void joinedSubclassWithPrimaryKeyJoinAndSecondaryTable() {
        Fixture f = new Fixture();
        var root = Entities.create(f.mappings, f.item, f.itemTable);
        root.setInheritanceStrategy(InheritanceType.JOINED);
        var magazine = Entities.create(f.mappings, f.magazine, f.magazineTable);
        magazine.setPrimaryKeyJoin(Joins.of(f.magazineFk));
        var book = Entities.create(f.mappings, f.book, null);
        JoinedTables.secondary(book, Joins.of(f.extraFk));
        Basics.create(book, f.blurb, f.blurbCol);

        var em = f.convert().eorm();

        Entity m = em.getEntity().get(1);
        assertThat(m.getPrimaryKeyJoinColumn()).extracting(c -> c.getName() + "->" + c.getReferencedColumnName())
                .containsExactly("ID->ID");
        assertThat(m.getPrimaryKeyForeignKey().getName()).isEqualTo("FK_MAGAZINE_ITEM");
        Entity b = em.getEntity().get(2);
        var st = b.getSecondaryTable().get(0);
        assertThat(st.getName()).isEqualTo("BOOK_EXTRA");
        assertThat(st.getSchema()).isEqualTo("LIB");
        assertThat(st.getPrimaryKeyJoinColumn()).extracting(c -> c.getName() + "->" + c.getReferencedColumnName())
                .containsExactly("ITEM_ID->ID");
        assertThat(b.getAttributes().getBasic().get(0).getColumn().getTable()).isEqualTo("BOOK_EXTRA");
    }

    @Test
    void idClassReferencesTheEClassAndIsReported() {
        Fixture f = new Fixture();
        var e = Entities.create(f.mappings, f.book, f.itemTable);
        e.setCwmIdClass(f.bookKey);
        EcoreIndex index = f.index();

        var r = CwmOrmToEorm.convert(f.mappings, index, true);

        assertThat(r.eorm().getEntity().get(0).getIdClass().getClass_()).isSameAs(index.eClass(f.bookKey));
        assertThat(r.notes()).extracting(Note::rule).contains(EormNotes.FENNEC_ID_CLASS);
    }

    @Test
    void mappedSuperclassAndEmbeddableReferenceTheirEClasses() {
        Fixture f = new Fixture();
        var ms = org.eclipse.daanse.cwm.model.daanse.orm.util.MappedSuperclasses.create(f.mappings, f.item);
        Ids.create(ms, f.itemId);
        Embeddable em = Embeddables.create(f.mappings, f.address);
        Basics.create(em, f.street, f.streetCol);
        EcoreIndex index = f.index();

        var out = CwmOrmToEorm.convert(f.mappings, index, true).eorm();

        assertThat(out.getMappedSuperclass().get(0).getClass_()).isSameAs(index.eClass(f.item));
        assertThat(out.getMappedSuperclass().get(0).getAttributes().getId()).hasSize(1);
        assertThat(out.getEmbeddable().get(0).getClass_()).isSameAs(index.eClass(f.address));
        assertThat(out.getEmbeddable().get(0).getAttributes().getBasic().get(0).getColumn().getName())
                .isEqualTo("STREET");
    }

    @Test
    void basicColumnWithFacetsNullabilityAndFlags() {
        Fixture f = new Fixture();
        f.priceCol.setPrecision(10);
        f.priceCol.setScale(2);
        f.titleCol.setLength(200);
        var e = Entities.create(f.mappings, f.book, f.itemTable);
        Basics.create(e, f.title, f.titleCol);
        var price = Basics.create(e, f.price);
        price.setColumn(Columns.readOnly(f.priceCol));
        EcoreIndex index = f.index();

        var basics = CwmOrmToEorm.convert(f.mappings, index, true).eorm().getEntity().get(0).getAttributes()
                .getBasic();

        var title = basics.get(0);
        assertThat(title.getName()).isEqualTo("title");
        assertThat(((EFeatureObject) title.getAccessibleObject()).getFeature()).isSameAs(index.feature(f.title));
        assertThat(title.getColumn().getName()).isEqualTo("TITLE");
        assertThat(title.getColumn().getLength()).isEqualTo(200);
        assertThat(title.getColumn().isNullable()).isFalse();
        assertThat(title.getColumn().getTable()).as("own table").isNull();
        assertThat(title.isOptional()).as("lower bound 1").isFalse();
        var p = basics.get(1);
        assertThat(p.getColumn().getPrecision()).isEqualTo(10);
        assertThat(p.getColumn().getScale()).isEqualTo(2);
        assertThat(p.getColumn().isNullable()).isTrue();
        assertThat(p.getColumn().isInsertable()).isFalse();
        assertThat(p.getColumn().isUpdatable()).isFalse();
        assertThat(p.isOptional()).isTrue();
    }

    @Test
    void temporalOnlyForJavaUtilDateLobForBlob() {
        Fixture f = new Fixture();
        var e = Entities.create(f.mappings, f.book, f.itemTable);
        Basics.create(e, f.published, f.publishedCol);
        Basics.create(e, f.released, f.releasedCol);
        Basics.create(e, f.cover, f.coverCol);

        var basics = f.convert().eorm().getEntity().get(0).getAttributes().getBasic();

        assertThat(basics.get(0).getTemporal()).isEqualTo(TemporalType.DATE);
        assertThat(basics.get(1).getTemporal()).as("java.time types take no temporal").isNull();
        assertThat(basics.get(2).getLob()).isNotNull();
        assertThat(basics.get(0).getLob()).isNull();
    }

    @Test
    void enumeratedNamesTheEEnumAndStringIsReported() {
        Fixture f = new Fixture();
        var e = Entities.create(f.mappings, f.book, f.itemTable);
        var genre = Basics.create(e, f.bookGenre, f.genreCol);
        EcoreIndex index = f.index();

        var out = CwmOrmToEorm.convert(f.mappings, index, true);
        EEnum ee = out.eorm().getEntity().get(0).getAttributes().getBasic().get(0).getEnumerated();
        assertThat(ee).isSameAs(index.eEnum(f.genre));
        assertThat(out.notes()).extracting(Note::rule).doesNotContain(EormNotes.ENUM_STRING);

        genre.setEnumerated(EnumType.STRING);
        assertThat(f.convert().notes()).extracting(Note::rule).contains(EormNotes.ENUM_STRING);
    }

    @Test
    void basicFetchAndConvertWithConverterClass() {
        Fixture f = new Fixture();
        Converter yesNo = Converters.create(f.mappings, f.yesNo, true);
        var e = Entities.create(f.mappings, f.book, f.itemTable);
        var subtitle = Basics.create(e, f.subtitle, f.subtitleCol);
        subtitle.setFetch(FetchType.LAZY);
        Converts.of(subtitle, yesNo);
        EcoreIndex index = f.index();

        var em = CwmOrmToEorm.convert(f.mappings, index, true).eorm();

        var b = em.getEntity().get(0).getAttributes().getBasic().get(0);
        assertThat(b.getFetch()).isEqualTo(org.eclipse.fennec.persistence.eorm.FetchType.LAZY);
        assertThat(b.getConvert().getConverter()).isEqualTo("library.YesNoConverter");
        assertThat(b.getConvert().getAttributeName()).isNull();
        assertThat(em.getConverter().get(0).getClass_()).isSameAs(index.eClass(f.yesNo));
        assertThat(em.getConverter().get(0).isAutoApply()).isTrue();
    }

    @Test
    void idWithGeneratedValueAndSequenceGenerator() {
        Fixture f = new Fixture();
        SequenceGenerator seq = Generators.sequence(f.mappings, "bookSeq", f.schema, "BOOK_SEQ");
        seq.setAllocationSize(50);
        var e = Entities.create(f.mappings, f.book, f.itemTable);
        Id id = Ids.create(e, f.itemId, f.itemIdCol);
        Generators.generated(id, GenerationType.SEQUENCE, seq);

        var em = f.convert().eorm();

        var eid = em.getEntity().get(0).getAttributes().getId().get(0);
        assertThat(eid.getColumn().getName()).isEqualTo("ID");
        assertThat(eid.getGeneratedValue().getStrategy())
                .isEqualTo(org.eclipse.fennec.persistence.eorm.GenerationType.SEQUENCE);
        assertThat(eid.getGeneratedValue().getGenerator()).isEqualTo("bookSeq");
        var sg = em.getSequenceGenerator().get(0);
        assertThat(sg.getSequenceName()).isEqualTo("BOOK_SEQ");
        assertThat(sg.getSchema()).isEqualTo("LIB");
        assertThat(sg.getCatalog()).isEqualTo("LIBDB");
        assertThat(sg.getAllocationSize()).isEqualTo(50);
    }

    @Test
    void tableGeneratorNamesItsTableAndColumnsAndIsReported() {
        Fixture f = new Fixture();
        Generators.table(f.mappings, "ids", f.genNameCol, f.genValueCol, "BOOK");

        var r = f.convert();

        var tg = r.eorm().getTableGenerator().get(0);
        assertThat(tg.getTable()).isEqualTo("ID_GEN");
        assertThat(tg.getPkColumnName()).isEqualTo("NAME");
        assertThat(tg.getValueColumnName()).isEqualTo("VAL");
        assertThat(tg.getPkColumnValue()).isEqualTo("BOOK");
        assertThat(r.notes()).extracting(Note::rule).contains(EormNotes.FENNEC_TABLE_GENERATOR);
    }

    @Test
    void versionAndTransient() {
        Fixture f = new Fixture();
        var e = Entities.create(f.mappings, f.book, f.itemTable);
        Versions.create(e, f.version, f.versionCol);
        Transients.create(e, f.subtitle);

        var attrs = f.convert().eorm().getEntity().get(0).getAttributes();

        assertThat(attrs.getVersion().get(0).getName()).isEqualTo("version");
        assertThat(attrs.getVersion().get(0).getColumn().getName()).isEqualTo("VERSION");
        assertThat(attrs.getTransient()).extracting(t -> t.getName()).containsExactly("subtitle");
    }

    @Test
    void embeddedWithAttributeOverridesNamedByPath() {
        Fixture f = new Fixture();
        Embeddables.create(f.mappings, f.address);
        var e = Entities.create(f.mappings, f.book, f.itemTable);
        Embedded embedded = Embeddeds.create(e, f.bookAddress);
        Overrides.attribute(embedded, f.streetCol, f.street);
        Overrides.attribute(embedded, f.cityCol, f.city);

        var emb = f.convert().eorm().getEntity().get(0).getAttributes().getEmbedded().get(0);

        assertThat(emb.getName()).isEqualTo("address");
        assertThat(emb.getAttributeOverride()).extracting(o -> o.getName() + "=" + o.getColumn().getName())
                .containsExactly("street=STREET", "city=CITY");
    }

    @Test
    void embeddedIdIsWrittenAndReported() {
        Fixture f = new Fixture();
        var e = Entities.create(f.mappings, f.book, f.itemTable);
        EmbeddedIds.create(e, f.bookAddress);

        var r = f.convert();

        assertThat(r.eorm().getEntity().get(0).getAttributes().getEmbeddedId().getName()).isEqualTo("address");
        assertThat(r.notes()).extracting(Note::rule).contains(EormNotes.FENNEC_EMBEDDED_ID);
    }

    @Test
    void manyToOneWithForeignKeyJoinTargetEntityAndCascade() {
        Fixture f = new Fixture();
        Entities.create(f.mappings, f.publisher, f.publisherTable);
        var e = Entities.create(f.mappings, f.book, f.itemTable);
        ManyToOne m = ManyToOnes.create(e, f.publisherOfBook, Joins.of(f.itemPublisherFk));
        m.setFetch(FetchType.LAZY);
        m.getCascade().addAll(List.of(CascadeKind.PERSIST, CascadeKind.MERGE));
        EcoreIndex index = f.index();

        var out = CwmOrmToEorm.convert(f.mappings, index, true).eorm().getEntity().get(1).getAttributes()
                .getManyToOne().get(0);

        assertThat(out.getName()).isEqualTo("publisher");
        assertThat(((EFeatureObject) out.getAccessibleObject()).getFeature())
                .isSameAs(index.feature(f.publisherOfBook)).isInstanceOf(EReference.class);
        assertThat(out.getTargetEntity()).isEqualTo("library.Publisher");
        assertThat(out.isOptional()).isFalse();
        assertThat(out.getJoinColumn()).extracting(c -> c.getName() + "->" + c.getReferencedColumnName())
                .containsExactly("PUBLISHER_ID->ID");
        assertThat(out.getJoinColumn().get(0).isNullable()).isFalse();
        assertThat(out.getForeignKey().getName()).isEqualTo("FK_ITEM_PUBLISHER");
        assertThat(out.getForeignKey().getConstraintMode()).isEqualTo(ConstraintMode.CONSTRAINT);
        assertThat(out.getCascade().getCascadePersist()).isNotNull();
        assertThat(out.getCascade().getCascadeMerge()).isNotNull();
        assertThat(out.getCascade().getCascadeAll()).isNull();
    }

    @Test
    void joinOfBareColumnPairsHasNoConstraintAndReadOnlyFlags() {
        Fixture f = new Fixture();
        var e = Entities.create(f.mappings, f.book, f.itemTable);
        ManyToOnes.create(e, f.publisherOfBook, Joins.readOnly(Joins.column(f.itemPublisherCol, f.publisherIdCol)));

        var out = f.convert().eorm().getEntity().get(0).getAttributes().getManyToOne().get(0);

        assertThat(out.getForeignKey().getConstraintMode()).isEqualTo(ConstraintMode.NOCONSTRAINT);
        assertThat(out.getForeignKey().getName()).isNull();
        assertThat(out.getJoinColumn().get(0).isInsertable()).isFalse();
        assertThat(out.getJoinColumn().get(0).isUpdatable()).isFalse();
    }

    @Test
    void inverseOneToManyNamesTheOwningEndAndOrders() {
        Fixture f = new Fixture();
        var p = Entities.create(f.mappings, f.publisher, f.publisherTable);
        OneToMany books = OneToManys.inverse(p, f.booksOfPublisher);
        Orderings.descending(books, f.title);
        Orderings.by(books, f.bookAddress, f.city);

        var out = f.convert().eorm().getEntity().get(0).getAttributes().getOneToMany().get(0);

        assertThat(out.getMappedBy()).isEqualTo("publisher");
        assertThat(out.getTargetEntity()).isEqualTo("library.Book");
        assertThat(out.getOrderBy()).isEqualTo("title DESC, address.city");
        assertThat(out.getJoinColumn()).isEmpty();
    }

    @Test
    void manyToManyJoinTableBothSides() {
        Fixture f = new Fixture();
        var b = Entities.create(f.mappings, f.book, f.itemTable);
        ManyToMany authors = ManyToManys.create(b, f.authorsOfBook,
                JoinedTables.join(Joins.of(f.baBookFk), Joins.of(f.baAuthorFk)));
        var a = Entities.create(f.mappings, f.author, f.authorTable);
        ManyToManys.inverse(a, f.booksOfAuthor);
        authors.setFetch(FetchType.EAGER);

        var em = f.convert().eorm();

        var owning = em.getEntity().get(0).getAttributes().getManyToMany().get(0);
        var jt = owning.getJoinTable();
        assertThat(jt.getName()).isEqualTo("BOOK_AUTHOR");
        assertThat(jt.getSchema()).isEqualTo("LIB");
        assertThat(jt.getJoinColumn()).extracting(c -> c.getName() + "->" + c.getReferencedColumnName())
                .containsExactly("BOOK_ID->ID");
        assertThat(jt.getInverseJoinColumn()).extracting(c -> c.getName() + "->" + c.getReferencedColumnName())
                .containsExactly("AUTHOR_ID->ID");
        assertThat(jt.getForeignKey().getName()).isEqualTo("FK_BA_BOOK");
        assertThat(jt.getInverseForeignKey().getName()).isEqualTo("FK_BA_AUTHOR");
        assertThat(owning.getMappedBy()).isNull();
        assertThat(em.getEntity().get(1).getAttributes().getManyToMany().get(0).getMappedBy()).isEqualTo("authors");
    }

    @Test
    void oneToOneOwningJoinInverseMappedByAndMapsId() {
        Fixture f = new Fixture();
        var b = Entities.create(f.mappings, f.book, f.itemTable);
        var detail = OneToOnes.create(b, f.detailOfBook, Joins.column(f.itemDetailCol, f.detailIdCol));
        detail.setOrphanRemoval(true);
        var d = Entities.create(f.mappings, f.detail, f.detailTable);
        OneToOnes.inverse(d, f.bookOfDetail);
        var m = Entities.create(f.mappings, f.magazine, f.magazineTable);
        ManyToOnes.mapsId(m, f.publisherOfBook, null);

        var em = f.convert().eorm();

        var owning = em.getEntity().get(0).getAttributes().getOneToOne().get(0);
        assertThat(owning.getJoinColumn()).extracting(c -> c.getName() + "->" + c.getReferencedColumnName())
                .containsExactly("DETAIL_ID->ID");
        assertThat(owning.isOrphanRemoval()).isTrue();
        assertThat(owning.isOptional()).isTrue();
        assertThat(em.getEntity().get(1).getAttributes().getOneToOne().get(0).getMappedBy()).isEqualTo("detail");
        assertThat(em.getEntity().get(2).getAttributes().getManyToOne().get(0).getMapsId()).isEmpty();
    }

    @Test
    void oneToOnePrimaryKeyJoin() {
        Fixture f = new Fixture();
        var b = Entities.create(f.mappings, f.book, f.itemTable);
        OneToOnes.primaryKeyJoin(b, f.detailOfBook, Joins.column(f.itemIdCol, f.detailIdCol));

        var owning = f.convert().eorm().getEntity().get(0).getAttributes().getOneToOne().get(0);

        assertThat(owning.getPrimaryKeyJoinColumn()).extracting(c -> c.getName() + "->" + c.getReferencedColumnName())
                .containsExactly("ID->ID");
        assertThat(owning.getPrimaryKeyForeignKey().getConstraintMode()).isEqualTo(ConstraintMode.NOCONSTRAINT);
    }

    @Test
    void associationOverrideWithJoinColumns() {
        Fixture f = new Fixture();
        var e = Entities.create(f.mappings, f.book, f.itemTable);
        Overrides.association(e, Joins.of(f.itemPublisherFk), f.publisherOfBook);

        var ao = f.convert().eorm().getEntity().get(0).getAssociationOverride().get(0);

        assertThat(ao.getName()).isEqualTo("publisher");
        assertThat(ao.getJoinColumn()).extracting(c -> c.getName()).containsExactly("PUBLISHER_ID");
        assertThat(ao.getForeignKey().getName()).isEqualTo("FK_ITEM_PUBLISHER");
    }

    @Test
    void elementCollectionWithCollectionTableOrderColumnAndTargetClass() {
        Fixture f = new Fixture();
        var e = Entities.create(f.mappings, f.book, f.itemTable);
        CollectionTable table = JoinedTables.collection(Joins.of(f.tagFk));
        ElementCollection tags = ElementCollections.create(e, f.tags, table, f.tagCol);
        tags.setOrderColumn(Columns.of(f.tagPosCol));

        var ec = f.convert().eorm().getEntity().get(0).getAttributes().getElementCollection().get(0);

        assertThat(ec.getName()).isEqualTo("tags");
        assertThat(ec.getTargetClass()).isEqualTo("java.lang.String");
        assertThat(ec.getColumn().getName()).isEqualTo("TAG");
        assertThat(ec.getColumn().getTable()).as("value column in the collection table").isNull();
        assertThat(ec.getCollectionTable().getName()).isEqualTo("BOOK_TAG");
        assertThat(ec.getCollectionTable().getJoinColumn())
                .extracting(c -> c.getName() + "->" + c.getReferencedColumnName()).containsExactly("BOOK_ID->ID");
        assertThat(ec.getCollectionTable().getForeignKey().getName()).isEqualTo("FK_TAG_BOOK");
        assertThat(ec.getOrderColumn().getName()).isEqualTo("POS");
    }

    @Test
    void mapKeyByAttributeAndByClassWithColumn() {
        Fixture f = new Fixture();
        var p = Entities.create(f.mappings, f.publisher, f.publisherTable);
        OneToMany byTitle = OneToManys.inverse(p, f.booksOfPublisher);
        MapKeys.attribute(byTitle, f.title);
        var a = Entities.create(f.mappings, f.author, f.authorTable);
        ManyToMany byKey = ManyToManys.inverse(a, f.booksOfAuthor);
        MapKeys.type(byKey, f.bookKey, f.titleCol);
        EcoreIndex index = f.index();

        var em = CwmOrmToEorm.convert(f.mappings, index, true).eorm();

        assertThat(em.getEntity().get(0).getAttributes().getOneToMany().get(0).getMapKey().getName())
                .isEqualTo("title");
        var m2m = em.getEntity().get(1).getAttributes().getManyToMany().get(0);
        assertThat(m2m.getMapKeyClass().getClass_()).isSameAs(index.eClass(f.bookKey));
        assertThat(m2m.getMapKeyColumn().getName()).isEqualTo("TITLE");
    }

    @Test
    void namedAndNativeQueriesWithHintsAndResultSetMapping() {
        Fixture f = new Fixture();
        var q = Queries.named(f.mappings, "Book.all", "SELECT b FROM Book b");
        Queries.hint(q, "jakarta.persistence.query.timeout", "100");
        SqlResultSetMapping rs = ResultSets.create(f.mappings, "titles");
        var er = ResultSets.entity(rs, f.book);
        ResultSets.field(er, f.title, f.titlesTitle);
        ResultSets.column(rs, f.titlesId);
        var ctor = ResultSets.constructor(rs, f.bookKey);
        ResultSets.argument(ctor, f.titlesTitle);
        var nq = Queries.nativeOf(f.mappings, f.titles);
        nq.setResultSetMapping(rs);

        var em = f.convert().eorm();

        assertThat(em.getNamedQuery().get(0).getQuery()).isEqualTo("SELECT b FROM Book b");
        assertThat(em.getNamedQuery().get(0).getHint().get(0).getValue()).isEqualTo("100");
        var nnq = em.getNamedNativeQuery().get(0);
        assertThat(nnq.getName()).isEqualTo("BOOK_TITLES");
        assertThat(nnq.getQuery()).isEqualTo("SELECT ID, TITLE FROM LIB.ITEM");
        assertThat(nnq.getResultSetMapping()).isEqualTo("titles");
        var m = em.getSqlResultSetMapping().get(0);
        assertThat(m.getEntityResult().get(0).getEntityClass()).isEqualTo("library.Book");
        assertThat(m.getEntityResult().get(0).getFieldResult()).extracting(r -> r.getName() + "=" + r.getColumn())
                .containsExactly("title=TITLE");
        assertThat(m.getColumnResult()).extracting(c -> c.getName()).containsExactly("ID");
        assertThat(m.getConstructorResult().get(0).getTargetClass()).isEqualTo("library.BookKey");
        assertThat(m.getConstructorResult().get(0).getColumn()).extracting(c -> c.getName()).containsExactly("TITLE");
    }

    @Test
    void storedProcedureQueryWithParametersAndResultClass() {
        Fixture f = new Fixture();
        NamedStoredProcedureQuery q = Queries.procedure(f.mappings, "extend", f.extend);
        q.getCwmResultClass().add(f.book);

        var r = f.convert();

        var sp = r.eorm().getNamedStoredProcedureQuery().get(0);
        assertThat(sp.getProcedureName()).isEqualTo("EXTEND_LOAN");
        assertThat(sp.getParameter()).extracting(p -> p.getName() + ":" + p.getMode())
                .containsExactly("days:" + ParameterMode.IN, "result:" + ParameterMode.OUT);
        assertThat(sp.getResultClass()).containsExactly("library.Book");
        assertThat(r.notes()).extracting(Note::rule).contains(EormNotes.PARAMETER_CLASS);
    }

    @Test
    void entityListenersCopiedPerUseAndOwnCallbacks() {
        Fixture f = new Fixture();
        EntityListener audit = Listeners.create(f.mappings, f.audit);
        Listeners.callback(audit, CallbackKind.PREPERSIST, f.onPersist);
        var b = Entities.create(f.mappings, f.book, f.itemTable);
        var p = Entities.create(f.mappings, f.publisher, f.publisherTable);
        Listeners.use(b, audit);
        Listeners.use(p, audit);
        Listeners.callback(b, CallbackKind.PREUPDATE, f.validate);
        b.setExcludeDefaultListeners(true);
        EcoreIndex index = f.index();

        var em = CwmOrmToEorm.convert(f.mappings, index, true).eorm();

        Entity book = em.getEntity().get(0);
        var l = book.getEntityListeners().getEntityListener().get(0);
        assertThat(l.getClass_()).isSameAs(index.eClass(f.audit));
        assertThat(l.getPrePersist().getMethodName()).isEqualTo("onPersist");
        assertThat(em.getEntity().get(1).getEntityListeners().getEntityListener().get(0)).isNotSameAs(l);
        assertThat(book.getPreUpdate().getMethodName()).isEqualTo("validate");
        assertThat(book.getExcludeDefaultListeners()).isNotNull();
    }

    @Test
    void namedEntityGraphWithSubgraph() {
        Fixture f = new Fixture();
        var b = Entities.create(f.mappings, f.book, f.itemTable);
        NamedEntityGraph g = EntityGraphs.create(b, "Book.withPublisher");
        var node = EntityGraphs.node(g, f.publisherOfBook);
        var sub = EntityGraphs.subgraph(g, "publisherDetails", f.publisher);
        EntityGraphs.node(sub, f.publisherName);
        node.setSubgraph(sub);

        var graph = f.convert().eorm().getEntity().get(0).getNamedEntityGraph().get(0);

        assertThat(graph.getName()).isEqualTo("Book.withPublisher");
        assertThat(graph.getNamedAttributeNode().get(0).getName()).isEqualTo("publisher");
        assertThat(graph.getNamedAttributeNode().get(0).getSubgraph()).isEqualTo("publisherDetails");
        assertThat(graph.getSubgraph().get(0).getClass_()).isEqualTo("library.Publisher");
        assertThat(graph.getSubgraph().get(0).getNamedAttributeNode().get(0).getName()).isEqualTo("name");
    }

    @Test
    void descriptionsGoWhereEormHasOneTheOthersAreCounted() {
        Fixture f = new Fixture();
        f.mappings.setDescription("the library");
        var e = Entities.create(f.mappings, f.book, f.itemTable);
        e.setDescription("a book");
        Basics.create(e, f.title, f.titleCol).setDescription("its title");
        ManyToOnes.create(e, f.publisherOfBook, Joins.of(f.itemPublisherFk)).getJoin().setDescription("via FK");

        var r = f.convert();

        assertThat(r.eorm().getDescription()).isEqualTo("the library");
        assertThat(r.eorm().getEntity().get(0).getDescription()).isEqualTo("a book");
        assertThat(r.eorm().getEntity().get(0).getAttributes().getManyToOne().get(0).getForeignKey().getDescription())
                .isEqualTo("via FK");
        assertThat(r.notes()).filteredOn(n -> n.rule().equals(EormNotes.DESCRIPTION)).singleElement()
                .extracting(Note::message).asString().startsWith("1 description");
    }

    @Test
    void featuresOfClassTypedAttributesAreReferences() {
        Fixture f = new Fixture();
        EcoreIndex index = f.index();

        assertThat(index.feature(f.bookAddress)).isInstanceOf(EReference.class);
        assertThat(((EReference) index.feature(f.bookAddress)).isContainment()).isTrue();
        assertThat(index.feature(f.title)).isInstanceOf(EAttribute.class);
        assertThat(index.eClass(f.book).getESuperTypes()).containsExactly(index.eClass(f.item));
        assertThat(index.eClass(f.item)).isInstanceOf(EClass.class);
    }
}
