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

import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.BehavioralFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Operation;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Association;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.RelationshipsFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.QueryColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.daanse.orm.AssociationOverride;
import org.eclipse.daanse.cwm.model.daanse.orm.AttributeOverride;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.CallbackKind;
import org.eclipse.daanse.cwm.model.daanse.orm.Convert;
import org.eclipse.daanse.cwm.model.daanse.orm.Converter;
import org.eclipse.daanse.cwm.model.daanse.orm.Embedded;
import org.eclipse.daanse.cwm.model.daanse.orm.Entity;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityListener;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityMappings;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityResult;
import org.eclipse.daanse.cwm.model.daanse.orm.FieldResult;
import org.eclipse.daanse.cwm.model.daanse.orm.GeneratedValue;
import org.eclipse.daanse.cwm.model.daanse.orm.GenerationType;
import org.eclipse.daanse.cwm.model.daanse.orm.Join;
import org.eclipse.daanse.cwm.model.daanse.orm.JoinTable;
import org.eclipse.daanse.cwm.model.daanse.orm.LifecycleCallback;
import org.eclipse.daanse.cwm.model.daanse.orm.MapKey;
import org.eclipse.daanse.cwm.model.daanse.orm.MappedSuperclass;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedAttributeNode;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedEntityGraph;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedNativeQuery;
import org.eclipse.daanse.cwm.model.daanse.orm.Ordering;
import org.eclipse.daanse.cwm.model.daanse.orm.SequenceGenerator;
import org.eclipse.daanse.cwm.model.daanse.orm.SqlResultSetMapping;
import org.eclipse.daanse.cwm.model.daanse.orm.Transient;
import org.eclipse.daanse.cwm.model.daanse.orm.util.OrmModelValidator.Finding;
import org.junit.jupiter.api.Test;

/** Every create helper names the element after its anchor and hangs it into its owner. */
class OrmHelpersTest {

    private final CoreFactory core = CoreFactory.eINSTANCE;
    private final RelationalFactory rel = RelationalFactory.eINSTANCE;
    private final EntityMappings em = CWMORMFactory.eINSTANCE.createEntityMappings();
    private final org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package pkg = core.createPackage();
    private final Schema schema = rel.createSchema();

    @Test
    void typesAreNamedAfterTheirClassTheEntityByDefaultToo() {
        var cls = cls("Base");
        MappedSuperclass ms = MappedSuperclasses.create(em, cls);
        assertThat(ms.getName()).isEqualTo("Base");
        assertThat(em.getMappedSuperclass()).containsExactly(ms);
        Entity e = Entities.create(em, cls("Book"), table("BOOK", "ID"));
        assertThat(e.getName()).isEqualTo("Book");
        assertThat(Entities.create(em, "Novel", cls("NovelImpl"), null).getName()).as("own JPA entity name")
                .isEqualTo("Novel");
        assertThat(Embeddables.create(em, cls("Address")).getName()).isEqualTo("Address");
    }

    @Test
    void mappingsAreNamedAfterTheirFeature() {
        var book = cls("Book");
        Table t = table("BOOK", "ID", "TITLE", "VERSION");
        Entity e = Entities.create(em, book, t);
        assertThat(Ids.create(e, attr(book, "id"), col(t, "ID")).getName()).isEqualTo("id");
        assertThat(Basics.create(e, attr(book, "title"), col(t, "TITLE")).getColumn().getName())
                .isEqualTo("TITLE");
        assertThat(Versions.create(e, attr(book, "version"), col(t, "VERSION")).getName()).isEqualTo("version");
        Transient tr = Transients.create(e, attr(book, "cache"));
        assertThat(tr.getName()).isEqualTo("cache");
        assertThat(tr.getCwmFeature().getName()).isEqualTo("cache");
        assertThat(Embeddeds.create(e, attr(book, "address")).getName()).isEqualTo("address");
        assertThat(EmbeddedIds.create(e, attr(book, "key")).getName()).isEqualTo("key");
    }

    @Test
    void joinsAreNamedAfterTheirKeyOrColumnsAndJoinFormsAfterTheirTable() {
        Table book = table("BOOK", "ID", "PUBLISHER_ID");
        Table publisher = table("PUBLISHER", "ID");
        Table bookAuthor = table("BOOK_AUTHOR", "BOOK_ID", "AUTHOR_ID");
        Table author = table("AUTHOR", "ID");
        PrimaryKey pk = rel.createPrimaryKey();
        pk.setName("PK_PUBLISHER");
        pk.getFeature().add(col(publisher, "ID"));
        publisher.getOwnedElement().add(pk);
        ForeignKey fk = rel.createForeignKey();
        fk.setName("FK_BOOK_PUBLISHER");
        fk.getFeature().add(col(book, "PUBLISHER_ID"));
        fk.setUniqueKey(pk);
        book.getOwnedElement().add(fk);

        assertThat(Joins.of(fk).getName()).isEqualTo("FK_BOOK_PUBLISHER");
        Join pair = Joins.column(col(book, "PUBLISHER_ID"), col(publisher, "ID"));
        assertThat(pair.getName()).isEqualTo("PUBLISHER_ID");
        assertThat(pair.getJoinColumn()).singleElement().satisfies(jc -> {
            assertThat(jc.getName()).isEqualTo("PUBLISHER_ID");
            assertThat(jc.getCwmReferencedColumn().getName()).isEqualTo("ID");
        });
        JoinTable jt = JoinedTables.join(Joins.column(col(bookAuthor, "BOOK_ID"), col(book, "ID")),
                Joins.column(col(bookAuthor, "AUTHOR_ID"), col(author, "ID")));
        assertThat(jt.getName()).isEqualTo("BOOK_AUTHOR");
        assertThat(Joins.readOnly(pair).isInsertable()).isFalse();
        assertThat(Columns.readOnly(col(book, "ID")).isUpdatable()).isFalse();
    }

    @Test
    void relationshipsAreNamedAfterTheirEnd() {
        var book = cls("Book");
        var publisher = cls("Publisher");
        Association a = RelationshipsFactory.eINSTANCE.createAssociation();
        a.setName("Book_Publisher");
        AssociationEnd toPublisher = end(a, "publisher", publisher);
        AssociationEnd toBooks = end(a, "books", book);
        Entity eBook = Entities.create(em, book, table("BOOK", "ID"));
        Entity ePublisher = Entities.create(em, publisher, table("PUBLISHER", "ID"));
        assertThat(ManyToOnes.create(eBook, toPublisher, null).getName()).isEqualTo("publisher");
        assertThat(OneToManys.inverse(ePublisher, toBooks).isOwningSide()).isFalse();
        assertThat(OneToOnes.inverse(ePublisher, toBooks).getName()).isEqualTo("books");
        assertThat(ManyToManys.inverse(ePublisher, toBooks).getName()).isEqualTo("books");
        assertThat(ManyToOnes.mapsId(eBook, toPublisher, null).isMapsId()).isTrue();
    }

    @Test
    void overridesAndConvertsAreNamedAfterTheirPath() {
        var member = cls("Member");
        var address = cls("Address");
        Attribute addr = attr(member, "address");
        addr.setType(address);
        Attribute street = attr(address, "street");
        Table t = table("MEMBER", "ADDR_STREET");
        Entity e = Entities.create(em, member, t);
        Embedded emb = Embeddeds.create(e, addr);
        AttributeOverride ao = Overrides.attribute(emb, col(t, "ADDR_STREET"), street);
        assertThat(ao.getName()).isEqualTo("street");
        AttributeOverride deep = Overrides.attribute(e, col(t, "ADDR_STREET"), addr, street);
        assertThat(deep.getName()).isEqualTo("address.street");
        AssociationEnd toX = end(RelationshipsFactory.eINSTANCE.createAssociation(), "x", member);
        AssociationOverride aso = Overrides.association(e, (Join) null, addr, toX);
        assertThat(aso.getName()).isEqualTo("address.x");

        Converter converter = Converters.create(em, cls("StreetConverter"), true);
        assertThat(converter.getName()).isEqualTo("StreetConverter");
        Convert c = Converts.of(emb, converter, street);
        assertThat(c.getName()).isEqualTo("street");
        assertThat(Converts.disable(emb, street).isDisableConversion()).isTrue();
    }

    @Test
    void listenersCallbacksGeneratorsAndQueriesFollowTheirAnchorOrOwnName() {
        var auditor = cls("Auditor");
        Operation stamp = BehavioralFactory.eINSTANCE.createOperation();
        stamp.setName("stamp");
        auditor.getFeature().add(stamp);
        EntityListener l = Listeners.create(em, auditor);
        assertThat(l.getName()).isEqualTo("Auditor");
        LifecycleCallback cb = Listeners.callback(l, CallbackKind.PREPERSIST, stamp);
        assertThat(cb.getName()).isEqualTo("stamp");

        var book = cls("Book");
        Table t = table("BOOK", "ID");
        Entity e = Entities.create(em, book, t);
        Listeners.use(e, l);
        assertThat(e.getListener()).containsExactly(l);
        SequenceGenerator seq = Generators.sequence(em, "bookSeq", schema, "BOOK_SEQ");
        GeneratedValue gv = Generators.generated(Ids.create(e, attr(book, "id"), col(t, "ID")),
                GenerationType.SEQUENCE, seq);
        assertThat(gv.getName()).isEqualTo("bookSeq");
        assertThat(em.getGenerator()).containsExactly(seq);

        QueryColumnSet qcs = rel.createQueryColumnSet();
        qcs.setName("BOOKS_BY_TITLE");
        NamedNativeQuery q = Queries.nativeOf(em, qcs);
        assertThat(q.getName()).isEqualTo("BOOKS_BY_TITLE");
        assertThat(Queries.hint(q, "timeout", "5").getValue()).isEqualTo("5");
        SqlResultSetMapping rsm = ResultSets.create(em, "bookResult");
        EntityResult er = ResultSets.entity(rsm, book);
        assertThat(er.getName()).isEqualTo("Book");
        Column title = rel.createColumn();
        title.setName("T");
        Column count = rel.createColumn();
        count.setName("CNT");
        qcs.getFeature().add(title);
        qcs.getFeature().add(count);
        FieldResult fr = ResultSets.field(er, attr(book, "title"), title);
        assertThat(fr.getName()).isEqualTo("title");
        assertThat(fr.getCwmColumn()).isSameAs(title);
        assertThat(ResultSets.column(rsm, count).getName()).isEqualTo("CNT");

        NamedEntityGraph g = EntityGraphs.create(e, "Book.all");
        NamedAttributeNode n = EntityGraphs.node(g, attr(book, "title"));
        assertThat(n.getName()).isEqualTo("title");
        assertThat(EntityGraphs.subgraph(g, "authors", cls("Author")).getCwmClass().getName()).isEqualTo("Author");
    }

    @Test
    void mapKeysAndOrderingsAreNamedAfterTheirFeature() {
        var book = cls("Book");
        Table t = table("BOOK", "ID");
        Entity e = Entities.create(em, book, t);
        Attribute tags = attr(book, "tags");
        var ec = ElementCollections.create(e, tags, null, col(t, "ID"));
        MapKey k = MapKeys.attribute(ec, attr(book, "key"));
        assertThat(k.getName()).isEqualTo("key");
        Ordering o = Orderings.descending(ec, attr(book, "rank"));
        assertThat(o.getName()).isEqualTo("rank");
        var address = cls("Address");
        Ordering byCity = Orderings.by(ec, attr(book, "address"), attr(address, "city"));
        assertThat(byCity.getName()).isEqualTo("address.city");
        assertThat(byCity.getCwmPath()).hasSize(2);
        assertThat(o.isDescending()).isTrue();
        assertThat(MapKeys.type(ec, cls("Locale"), col(t, "ID")).getColumn().getName()).isEqualTo("ID");
    }

    @Test
    void helperBuiltElementsNeverDriftFromTheirAnchor() {
        mappingsAreNamedAfterTheirFeature();
        overridesAndConvertsAreNamedAfterTheirPath();
        listenersCallbacksGeneratorsAndQueriesFollowTheirAnchorOrOwnName();
        assertThat(OrmModelValidator.check(em)).filteredOn(f -> f.message().contains("name differs from the CWM anchor"))
                .extracting(Finding::message).isEmpty();
    }

    private org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class cls(String name) {
        var c = core.createClass();
        c.setName(name);
        pkg.getOwnedElement().add(c);
        return c;
    }

    private Attribute attr(Classifier owner, String name) {
        Attribute a = core.createAttribute();
        a.setName(name);
        owner.getFeature().add(a);
        return a;
    }

    private Table table(String name, String... columns) {
        Table t = rel.createTable();
        t.setName(name);
        for (String c : columns) {
            Column col = rel.createColumn();
            col.setName(c);
            t.getFeature().add(col);
        }
        schema.getOwnedElement().add(t);
        return t;
    }

    private static Column col(Table t, String name) {
        return t.getFeature().stream().filter(f -> name.equals(f.getName())).map(Column.class::cast).findFirst()
                .orElseThrow();
    }

    private static AssociationEnd end(Association a, String name, Classifier type) {
        AssociationEnd e = RelationshipsFactory.eINSTANCE.createAssociationEnd();
        e.setName(name);
        e.setType(type);
        e.setIsNavigable(true);
        a.getFeature().add(e);
        return e;
    }
}
