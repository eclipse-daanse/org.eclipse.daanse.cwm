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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Multiplicity;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.MultiplicityRange;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Association;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.RelationshipsFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLParameter;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.daanse.orm.Basic;
import org.eclipse.daanse.cwm.model.daanse.orm.ElementCollection;
import org.eclipse.daanse.cwm.model.daanse.orm.Embeddable;
import org.eclipse.daanse.cwm.model.daanse.orm.Embedded;
import org.eclipse.daanse.cwm.model.daanse.orm.Entity;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityMappings;
import org.eclipse.daanse.cwm.model.daanse.orm.GenerationType;
import org.eclipse.daanse.cwm.model.daanse.orm.Id;
import org.eclipse.daanse.cwm.model.daanse.orm.InheritanceType;
import org.eclipse.daanse.cwm.model.daanse.orm.JoinTable;
import org.eclipse.daanse.cwm.model.daanse.orm.ManyToOne;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedStoredProcedureQuery;
import org.eclipse.daanse.cwm.model.daanse.orm.SequenceGenerator;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.util.OrmBindings.Binding;
import org.eclipse.daanse.cwm.model.daanse.orm.util.OrmBindings.JoinPair;
import org.junit.jupiter.api.Test;

/**
 * Je Mapping-Art eine Bindung: wohin ein Attribut oder eine Rolle in der Datenbank geht — und die
 * Ableitungen, die das Modell bewusst nicht als Feature trägt.
 */
class OrmBindingsTest {

    private static final CoreFactory CORE = CoreFactory.eINSTANCE;
    private static final RelationalFactory REL = RelationalFactory.eINSTANCE;
    private static final RelationshipsFactory RS = RelationshipsFactory.eINSTANCE;

    private final Library lib = new Library();

    @Test
    void idAndBasicBindOneColumnEach() {
        assertThat(binding(lib.book, "id")).satisfies(b -> {
            assertThat(b.kind()).isEqualTo("ID");
            assertThat(b.table()).isEqualTo("BOOK");
            assertThat(b.columns()).containsExactly("ID");
            assertThat(b.paths()).isEmpty();
        });
        assertThat(binding(lib.book, "title").columns()).containsExactly("TITLE");
    }

    @Test
    void embeddedWithOverridesPairsPathsAndColumns() {
        Binding b = binding(lib.member, "address");
        assertThat(b.kind()).isEqualTo("EMBEDDED");
        assertThat(b.table()).isEqualTo("MEMBER");
        assertThat(b.columns()).containsExactly("ADDR_STREET", "ADDR_CITY");
        assertThat(b.paths()).containsExactly("street", "city");
    }

    @Test
    void embeddedWithoutOverridesTakesTheEmbeddablesDefault() {
        Binding b = binding(lib.publisher, "address");
        assertThat(b.columns()).containsExactly("STREET", "CITY");
        assertThat(b.paths()).containsExactly("street", "city");
    }

    @Test
    void elementCollectionLivesInItsCollectionTable() {
        Binding b = binding(lib.book, "keywords");
        assertThat(b.kind()).isEqualTo("ELEMENT_COLLECTION");
        assertThat(b.table()).isEqualTo("BOOK_KEYWORD");
        assertThat(b.columns()).containsExactly("KEYWORD");
        assertThat(b.joinTable()).isEqualTo("BOOK_KEYWORD");
        assertThat(b.joinColumns()).containsExactly("BOOK_ID");
        assertThat(b.referencedColumns()).containsExactly("BOOK.ID");
        assertThat(b.orderColumn()).isEqualTo("IDX");
    }

    @Test
    void manyToOneJoinsInItsOwnTable() {
        Binding b = binding(lib.book, "publisher");
        assertThat(b.kind()).isEqualTo("MANY_TO_ONE");
        assertThat(b.target()).isEqualTo("Publisher");
        assertThat(b.table()).isEqualTo("BOOK");
        assertThat(b.joinColumns()).containsExactly("PUBLISHER_ID");
        assertThat(b.referencedColumns()).containsExactly("PUBLISHER.ID");
        assertThat(b.joinTable()).isEmpty();
    }

    @Test
    void oneToManyJoinsInTheTargetTable() {
        Binding b = binding(lib.publisher, "books");
        assertThat(b.kind()).isEqualTo("ONE_TO_MANY");
        assertThat(b.target()).isEqualTo("Book");
        assertThat(b.table()).as("the foreign key lives in the target table").isEqualTo("BOOK");
        assertThat(b.joinColumns()).containsExactly("PUBLISHER_ID");
    }

    @Test
    void manyToManyGoesThroughItsJoinTable() {
        Binding b = binding(lib.book, "authors");
        assertThat(b.kind()).isEqualTo("MANY_TO_MANY");
        assertThat(b.joinTable()).isEqualTo("BOOK_AUTHOR");
        assertThat(b.table()).isEmpty();
        assertThat(b.joinColumns()).containsExactly("BOOK_ID");
        assertThat(b.referencedColumns()).containsExactly("BOOK.ID");
        assertThat(b.inverseJoinColumns()).containsExactly("AUTHOR_ID");
        assertThat(b.inverseReferencedColumns()).containsExactly("AUTHOR.ID");
    }

    @Test
    void transientBindsNothing() {
        Binding b = binding(lib.book, "cachedRating");
        assertThat(b.kind()).isEqualTo("TRANSIENT");
        assertThat(b.columns()).isEmpty();
        assertThat(b.table()).isEmpty();
    }

    @Test
    void typeLevelNamesTableSchemaAndInheritance() {
        assertThat(OrmBindings.kind(lib.book)).isEqualTo("ENTITY");
        assertThat(OrmBindings.kind(lib.address)).isEqualTo("EMBEDDABLE");
        assertThat(OrmBindings.className(lib.book)).isEqualTo("Book");
        assertThat(OrmBindings.table(lib.book)).isEqualTo("BOOK");
        assertThat(OrmBindings.schema(lib.book)).isEqualTo("LIB");
        assertThat(OrmBindings.inheritance(lib.book)).isEqualTo("SINGLE_TABLE");
        assertThat(OrmBindings.discriminatorColumn(lib.book)).isEqualTo("KIND");
        assertThat(OrmBindings.discriminatorValue(lib.book)).isEqualTo("B");
        assertThat(OrmBindings.inheritance(lib.publisher)).isEmpty();
        assertThat(OrmBindings.managedTypes(lib.em)).hasSize(4);
    }

    @Test
    void aSubclassInheritsStrategyAndDiscriminatorColumnAndLivesInItsRootsTable() {
        Entity novel = subclassOfBook();

        assertThat(OrmBindings.superClassName(novel)).isEqualTo("Book");
        assertThat(OrmBindings.inheritance(novel)).isEqualTo("SINGLE_TABLE");
        assertThat(OrmBindings.discriminatorColumn(novel)).isEqualTo("KIND");
        assertThat(OrmBindings.discriminatorValue(novel)).isEqualTo("N");
        assertThat(OrmBindings.table(novel)).isEqualTo("BOOK");
        assertThat(OrmBindings.primaryKey(novel)).as("the root's key").isSameAs(lib.pkBook);
    }

    @Test
    void aRootHasNoSuperclass() {
        assertThat(OrmBindings.superType(lib.book)).isNull();
        assertThat(OrmBindings.superClassName(lib.book)).isEmpty();
        assertThat(OrmBindings.table(lib.address)).isEmpty();
    }

    @Test
    void mappingsComeInAFixedOrder() {
        assertThat(OrmBindings.of(lib.book)).extracting(Binding::feature)
                .containsExactly("id", "title", "cachedRating", "keywords", "publisher", "authors");
    }

    // ---- derivations of the removed features ---------------------------------------------

    @Test
    void catalogAndObjectPackageFollowFromTheAnchors() {
        assertThat(OrmBindings.catalog(lib.book)).isEqualTo("LIBRARYDB");
        assertThat(OrmBindings.catalog(lib.address)).isEmpty();
        assertThat(OrmBindings.objectPackage(lib.em)).isSameAs(lib.pkg);
    }

    @Test
    void primaryKeyIsTheKeyOfTheEntityTable() {
        assertThat(OrmBindings.primaryKey(lib.book)).isSameAs(lib.pkBook);
        assertThat(OrmBindings.primaryKey(lib.member)).isNull();
    }

    @Test
    void discriminatorTypeFollowsFromTheSqlType() {
        assertThat(OrmBindings.discriminatorType(lib.book)).isEqualTo("STRING");
        lib.kind.setType(lib.sqlType("INTEGER"));
        assertThat(OrmBindings.discriminatorType(lib.book)).isEqualTo("INTEGER");
        lib.kind.setType(lib.sqlType("CHAR"));
        lib.kind.setLength(1);
        assertThat(OrmBindings.discriminatorType(lib.book)).isEqualTo("CHAR");
        assertThat(OrmBindings.discriminatorType(lib.publisher)).isEmpty();
    }

    @Test
    void optionalFollowsFromTheLowerBound() {
        ManyToOne publisher = lib.book.getManyToOne().get(0);
        assertThat(OrmBindings.optional(publisher)).as("no multiplicity: not known to be required").isTrue();
        multiplicity(publisher.getCwmAssociationEnd(), 1, 1);
        assertThat(OrmBindings.optional(publisher)).isFalse();
        multiplicity(publisher.getCwmAssociationEnd(), 0, 1);
        assertThat(OrmBindings.optional(publisher)).isTrue();
    }

    @Test
    void theTableOfAJoinFormIsTheTableOfItsJoinColumns() {
        ElementCollection keywords = lib.book.getElementCollection().get(0);
        assertThat(OrmBindings.tableOf(keywords.getCollectionTable()).getName()).isEqualTo("BOOK_KEYWORD");
        JoinTable bookAuthor = lib.book.getManyToMany().get(0).getJoinTable();
        assertThat(OrmBindings.tableOf(bookAuthor).getName()).isEqualTo("BOOK_AUTHOR");
        assertThat(bookAuthor.getName()).as("the helper names the form after its table").isEqualTo("BOOK_AUTHOR");
    }

    @Test
    void aForeignKeyJoinPairsTheKeyColumnsWithTheReferencedKey() {
        ManyToOne publisher = lib.book.getManyToOne().get(0);
        assertThat(publisher.getJoin().getCwmForeignKey()).isSameAs(lib.fkBookPublisher);
        assertThat(publisher.getJoin().getJoinColumn()).isEmpty();
        List<JoinPair> pairs = OrmBindings.joinColumns(publisher.getJoin());
        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).column().getName()).isEqualTo("PUBLISHER_ID");
        assertThat(pairs.get(0).referenced().getName()).isEqualTo("ID");
        assertThat(OrmBindings.tableOf(publisher.getJoin()).getName()).isEqualTo("BOOK");
        assertThat(OrmBindings.referencedTableOf(publisher.getJoin()).getName()).isEqualTo("PUBLISHER");
    }

    @Test
    void aPathReadsAsDottedNames() {
        var override = ((Embedded) lib.member.getEmbedded().get(0)).getAttributeOverride().get(0);
        assertThat(OrmBindings.path(override.getCwmPath())).isEqualTo("street");
        assertThat(override.getName()).isEqualTo("street");
        Attribute address = Library.attrOf(lib.member.getCwmClass(), "address");
        Attribute street = Library.attrOf(lib.address.getCwmClass(), "street");
        assertThat(OrmBindings.path(List.of(address, street))).isEqualTo("address.street");
    }

    @Test
    void generatorAndProcedureParametersComeFromTheAnchors() {
        SequenceGenerator seq = Generators.sequence(lib.em, "bookSeq", lib.schema, "BOOK_SEQ");
        Id id = lib.book.getId().get(0);
        Generators.generated(id, GenerationType.SEQUENCE, seq);
        assertThat(OrmBindings.generatorOf(id.getGeneratedValue())).isSameAs(seq);

        Procedure proc = REL.createProcedure();
        proc.setName("COUNT_BOOKS");
        SQLParameter p = REL.createSQLParameter();
        p.setName("PUBLISHER_ID");
        p.setKind(ParameterDirectionKind.PDK_IN);
        proc.getParameter().add(p);
        lib.schema.getOwnedElement().add(proc);
        NamedStoredProcedureQuery q = Queries.procedure(lib.em, "Book.count", proc);
        assertThat(OrmBindings.parameters(q)).containsExactly(p);
    }

    // ---- helpers ---------------------------------------------------------------------------

    /** Novel extends Book: no table and no discriminator column of its own, only its value. */
    private Entity subclassOfBook() {
        var bookClass = lib.book.getCwmClass();
        var novelClass = Library.cls(lib.pkg, "Novel");
        Attribute genre = Library.attr(novelClass, "genre", null);
        var g = RS.createGeneralization();
        g.setChild(novelClass);
        g.setParent(bookClass);
        lib.pkg.getOwnedElement().add(g);

        Entity novel = Entities.create(lib.em, novelClass, null);
        novel.setDiscriminatorValue("N");
        Basics.create(novel, genre, Library.col(lib.tBook, "TITLE"));
        return novel;
    }

    private static void multiplicity(AssociationEnd end, long lower, long upper) {
        Multiplicity m = CORE.createMultiplicity();
        MultiplicityRange r = CORE.createMultiplicityRange();
        r.setLower(lower);
        r.setUpper(upper);
        m.getRange().add(r);
        end.setMultiplicity(m);
    }

    private static Binding binding(Entity e, String feature) {
        return OrmBindings.of(e).stream().filter(b -> b.feature().equals(feature)).findFirst()
                .orElseThrow();
    }

    /** Book, Member, Publisher, Author und das Embeddable Address — beide Welten und das Mapping. */
    private static final class Library {
        final EntityMappings em = CWMORMFactory.eINSTANCE.createEntityMappings();
        final org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package pkg = CORE.createPackage();
        final Catalog catalog = REL.createCatalog();
        final Schema schema = REL.createSchema();
        final Table tBook;
        final Column kind;
        final PrimaryKey pkBook;
        final ForeignKey fkBookPublisher;
        final Entity book;
        final Entity member;
        final Entity publisher;
        final Embeddable address;

        Library() {
            em.setName("library");
            pkg.setName("library");
            var cBook = cls(pkg, "Book");
            var cMember = cls(pkg, "Member");
            var cPublisher = cls(pkg, "Publisher");
            var cAuthor = cls(pkg, "Author");
            var cAddress = cls(pkg, "Address");
            Attribute bookId = attr(cBook, "id", null);
            Attribute title = attr(cBook, "title", null);
            Attribute cachedRating = attr(cBook, "cachedRating", null);
            Attribute keywords = attr(cBook, "keywords", null);
            Attribute memberId = attr(cMember, "id", null);
            Attribute memberAddress = attr(cMember, "address", cAddress);
            Attribute publisherId = attr(cPublisher, "id", null);
            Attribute publisherAddress = attr(cPublisher, "address", cAddress);
            Attribute street = attr(cAddress, "street", null);
            Attribute city = attr(cAddress, "city", null);
            AssociationEnd toPublisher = association(pkg, "Book_Publisher", cBook, "books", cPublisher, "publisher");
            AssociationEnd toBooks = otherEnd(toPublisher);
            AssociationEnd toAuthors = association(pkg, "Book_Author", cBook, "books", cAuthor, "authors");

            catalog.setName("LIBRARYDB");
            schema.setName("LIB");
            catalog.getOwnedElement().add(schema);
            tBook = table(schema, "BOOK", "ID", "TITLE", "KIND", "PUBLISHER_ID");
            kind = col(tBook, "KIND");
            kind.setType(sqlType("VARCHAR"));
            Table tMember = table(schema, "MEMBER", "ID", "ADDR_STREET", "ADDR_CITY");
            Table tPublisher = table(schema, "PUBLISHER", "ID", "STREET", "CITY");
            Table tAuthor = table(schema, "AUTHOR", "ID");
            Table tBookAuthor = table(schema, "BOOK_AUTHOR", "BOOK_ID", "AUTHOR_ID");
            Table tKeyword = table(schema, "BOOK_KEYWORD", "BOOK_ID", "KEYWORD", "IDX");
            pkBook = primaryKey(tBook, "PK_BOOK", "ID");
            PrimaryKey pkPublisher = primaryKey(tPublisher, "PK_PUBLISHER", "ID");
            fkBookPublisher = REL.createForeignKey();
            fkBookPublisher.setName("FK_BOOK_PUBLISHER");
            fkBookPublisher.getFeature().add(col(tBook, "PUBLISHER_ID"));
            fkBookPublisher.setUniqueKey(pkPublisher);
            tBook.getOwnedElement().add(fkBookPublisher);

            address = Embeddables.create(em, cAddress);
            Basics.create(address, street, col(tPublisher, "STREET"));
            Basics.create(address, city, col(tPublisher, "CITY"));

            book = Entities.create(em, cBook, tBook);
            book.setInheritanceStrategy(InheritanceType.SINGLETABLE);
            book.setCwmDiscriminatorColumn(kind);
            book.setDiscriminatorValue("B");
            Ids.create(book, bookId, col(tBook, "ID"));
            Basics.create(book, title, col(tBook, "TITLE"));
            Transients.create(book, cachedRating);
            ElementCollection kw = ElementCollections.create(book, keywords,
                    JoinedTables.collection(Joins.column(col(tKeyword, "BOOK_ID"), col(tBook, "ID"))),
                    col(tKeyword, "KEYWORD"));
            kw.setOrderColumn(Columns.of(col(tKeyword, "IDX")));
            ManyToOnes.create(book, toPublisher, Joins.of(fkBookPublisher));
            ManyToManys.create(book, toAuthors, JoinedTables.join(
                    Joins.column(col(tBookAuthor, "BOOK_ID"), col(tBook, "ID")),
                    Joins.column(col(tBookAuthor, "AUTHOR_ID"), col(tAuthor, "ID"))));

            member = Entities.create(em, cMember, tMember);
            Ids.create(member, memberId, col(tMember, "ID"));
            Embedded memAddr = Embeddeds.create(member, memberAddress);
            Overrides.attribute(memAddr, col(tMember, "ADDR_STREET"), street);
            Overrides.attribute(memAddr, col(tMember, "ADDR_CITY"), city);

            publisher = Entities.create(em, cPublisher, tPublisher);
            Ids.create(publisher, publisherId, col(tPublisher, "ID"));
            Embeddeds.create(publisher, publisherAddress);
            OneToManys.create(publisher, toBooks, Joins.column(col(tBook, "PUBLISHER_ID"), col(tPublisher, "ID")));
        }

        SQLSimpleType sqlType(String name) {
            SQLSimpleType t = REL.createSQLSimpleType();
            t.setName(name);
            catalog.getOwnedElement().add(t);
            return t;
        }

        static org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class cls(
                org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package pkg, String name) {
            var c = CORE.createClass();
            c.setName(name);
            pkg.getOwnedElement().add(c);
            return c;
        }

        static Attribute attr(Classifier owner, String name, Classifier type) {
            Attribute a = CORE.createAttribute();
            a.setName(name);
            a.setType(type);
            owner.getFeature().add(a);
            return a;
        }

        static Attribute attrOf(Classifier owner, String name) {
            return owner.getFeature().stream().filter(f -> name.equals(f.getName()))
                    .map(Attribute.class::cast).findFirst().orElseThrow();
        }

        /** A navigable binary association; returns the end typed {@code typeB} (named {@code endB}). */
        private static AssociationEnd association(org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package pkg,
                String name, Classifier typeA, String endA, Classifier typeB, String endB) {
            Association a = RS.createAssociation();
            a.setName(name);
            AssociationEnd ea = RS.createAssociationEnd();
            ea.setName(endA);
            ea.setType(typeA);
            ea.setIsNavigable(true);
            AssociationEnd eb = RS.createAssociationEnd();
            eb.setName(endB);
            eb.setType(typeB);
            eb.setIsNavigable(true);
            a.getFeature().add(ea);
            a.getFeature().add(eb);
            pkg.getOwnedElement().add(a);
            return eb;
        }

        private static AssociationEnd otherEnd(AssociationEnd end) {
            return end.getOwner().getFeature().stream().filter(f -> f != end).map(AssociationEnd.class::cast)
                    .findFirst().orElseThrow();
        }

        private static Table table(Schema schema, String name, String... columns) {
            Table t = REL.createTable();
            t.setName(name);
            for (String c : columns) {
                Column col = REL.createColumn();
                col.setName(c);
                t.getFeature().add(col);
            }
            schema.getOwnedElement().add(t);
            return t;
        }

        private static PrimaryKey primaryKey(Table t, String name, String column) {
            PrimaryKey pk = REL.createPrimaryKey();
            pk.setName(name);
            pk.getFeature().add(col(t, column));
            t.getOwnedElement().add(pk);
            return pk;
        }

        static Column col(Table t, String name) {
            return t.getFeature().stream().filter(f -> name.equals(f.getName()))
                    .map(Column.class::cast).findFirst().orElseThrow();
        }
    }
}
