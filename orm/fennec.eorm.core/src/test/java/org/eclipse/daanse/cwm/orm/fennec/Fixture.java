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

import java.nio.file.Path;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.DatatypesFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.Enumeration;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.EnumerationLiteral;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.QueryExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.BehavioralFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Operation;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Multiplicity;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.MultiplicityRange;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Association;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Generalization;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.RelationshipsFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.QueryColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLParameter;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityMappings;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

/**
 * A small library in CWM: class model (package {@code library}), relational catalog
 * ({@code LIBDB.LIB}) and an empty mapping set. Each test maps what it needs with the ORM create
 * helpers and converts with an Ecore index over {@code /ecore/fixture/library.ecore}: the Ecore model
 * of this class model, written by a CWM-to-Ecore conversion and stripped of its CWM annotations,
 * so it binds by names only.
 */
final class Fixture {

    static final CoreFactory CORE = CoreFactory.eINSTANCE;
    static final RelationalFactory SQL = RelationalFactory.eINSTANCE;

    final Package root = pkg("model");
    final Package library = pkg("library");

    final DataType longType = dataType("long");
    final DataType string = dataType("String");
    final DataType date = dataType("Date");
    final DataType localDate = dataType("LocalDate");
    final DataType bytes = dataType("byte[]");
    final DataType integer = dataType("int");
    final DataType decimal = dataType("BigDecimal");
    final Enumeration genre = DatatypesFactory.eINSTANCE.createEnumeration();

    final Class item = cls("Item");
    final Attribute itemId = attribute(item, "id", longType, 1, 1);
    final Class book = cls("Book");
    final Attribute title = attribute(book, "title", string, 1, 1);
    final Attribute subtitle = attribute(book, "subtitle", string, 0, 1);
    final Attribute published = attribute(book, "published", date, 0, 1);
    final Attribute released = attribute(book, "released", localDate, 0, 1);
    final Attribute cover = attribute(book, "cover", bytes, 0, 1);
    final Attribute bookGenre;
    final Attribute price = attribute(book, "price", decimal, 0, 1);
    final Attribute version = attribute(book, "version", integer, 1, 1);
    final Attribute blurb = attribute(book, "blurb", string, 0, 1);
    final Attribute tags = attribute(book, "tags", string, 0, -1);
    final Class magazine = cls("Magazine");
    final Attribute issue = attribute(magazine, "issue", integer, 1, 1);
    final Class address = cls("Address");
    final Attribute street = attribute(address, "street", string, 0, 1);
    final Attribute city = attribute(address, "city", string, 0, 1);
    final Attribute bookAddress = attribute(book, "address", address, 0, 1);
    final Class publisher = cls("Publisher");
    final Attribute publisherId = attribute(publisher, "id", longType, 1, 1);
    final Attribute publisherName = attribute(publisher, "name", string, 1, 1);
    final Class author = cls("Author");
    final Attribute authorId = attribute(author, "id", longType, 1, 1);
    final Class detail = cls("Detail");
    final Attribute detailId = attribute(detail, "id", longType, 1, 1);
    final Class bookKey = cls("BookKey");
    final Attribute keyIsbn = attribute(bookKey, "isbn", string, 1, 1);
    final Class audit = cls("Audit");
    final Operation onPersist = operation(audit, "onPersist");
    final Operation validate = operation(book, "validate");
    final Class yesNo = cls("YesNoConverter");

    final AssociationEnd booksOfPublisher = end("books", book, true, 0, -1);
    final AssociationEnd publisherOfBook = end("publisher", publisher, true, 1, 1);
    final AssociationEnd booksOfAuthor = end("books", book, true, 0, -1);
    final AssociationEnd authorsOfBook = end("authors", author, true, 0, -1);
    final AssociationEnd bookOfDetail = end("book", book, true, 1, 1);
    final AssociationEnd detailOfBook = end("detail", detail, true, 0, 1);

    final Catalog catalog = SQL.createCatalog();
    final Schema schema = SQL.createSchema();
    final SQLSimpleType bigint = sqlType("BIGINT");
    final SQLSimpleType varchar = sqlType("VARCHAR");
    final SQLSimpleType dateSql = sqlType("DATE");
    final SQLSimpleType blob = sqlType("BLOB");
    final SQLSimpleType intSql = sqlType("INTEGER");
    final SQLSimpleType decimalSql = sqlType("DECIMAL");
    final SQLSimpleType charSql = sqlType("CHAR");

    final Table itemTable = table("ITEM");
    final Column itemIdCol = column(itemTable, "ID", bigint, false);
    final Column dtype = column(itemTable, "DTYPE", intSql, false);
    final Column kind = column(itemTable, "KIND", charSql, false);
    final Column titleCol = column(itemTable, "TITLE", varchar, false);
    final Column subtitleCol = column(itemTable, "SUBTITLE", varchar, true);
    final Column publishedCol = column(itemTable, "PUBLISHED", dateSql, true);
    final Column releasedCol = column(itemTable, "RELEASED", dateSql, true);
    final Column coverCol = column(itemTable, "COVER", blob, true);
    final Column genreCol = column(itemTable, "GENRE", intSql, true);
    final Column priceCol = column(itemTable, "PRICE", decimalSql, true);
    final Column versionCol = column(itemTable, "VERSION", intSql, false);
    final Column streetCol = column(itemTable, "STREET", varchar, true);
    final Column cityCol = column(itemTable, "CITY", varchar, true);
    final Column issueCol = column(itemTable, "ISSUE", intSql, true);
    final Column itemPublisherCol = column(itemTable, "PUBLISHER_ID", bigint, false);
    final Column itemDetailCol = column(itemTable, "DETAIL_ID", bigint, true);
    final PrimaryKey itemPk = primaryKey(itemTable, "PK_ITEM", itemIdCol);

    final Table extraTable = table("BOOK_EXTRA");
    final Column extraItemCol = column(extraTable, "ITEM_ID", bigint, false);
    final Column blurbCol = column(extraTable, "BLURB", varchar, true);
    final ForeignKey extraFk = foreignKey(extraTable, "FK_EXTRA_ITEM", itemPk, extraItemCol);

    final Table magazineTable = table("MAGAZINE");
    final Column magazineIdCol = column(magazineTable, "ID", bigint, false);
    final Column magazineIssueCol = column(magazineTable, "ISSUE", intSql, false);
    final ForeignKey magazineFk = foreignKey(magazineTable, "FK_MAGAZINE_ITEM", itemPk, magazineIdCol);

    final Table publisherTable = table("PUBLISHER");
    final Column publisherIdCol = column(publisherTable, "ID", bigint, false);
    final Column publisherNameCol = column(publisherTable, "NAME", varchar, false);
    final PrimaryKey publisherPk = primaryKey(publisherTable, "PK_PUBLISHER", publisherIdCol);
    final ForeignKey itemPublisherFk = foreignKey(itemTable, "FK_ITEM_PUBLISHER", publisherPk, itemPublisherCol);

    final Table authorTable = table("AUTHOR");
    final Column authorIdCol = column(authorTable, "ID", bigint, false);
    final PrimaryKey authorPk = primaryKey(authorTable, "PK_AUTHOR", authorIdCol);

    final Table bookAuthorTable = table("BOOK_AUTHOR");
    final Column baBookCol = column(bookAuthorTable, "BOOK_ID", bigint, false);
    final Column baAuthorCol = column(bookAuthorTable, "AUTHOR_ID", bigint, false);
    final ForeignKey baBookFk = foreignKey(bookAuthorTable, "FK_BA_BOOK", itemPk, baBookCol);
    final ForeignKey baAuthorFk = foreignKey(bookAuthorTable, "FK_BA_AUTHOR", authorPk, baAuthorCol);

    final Table tagTable = table("BOOK_TAG");
    final Column tagBookCol = column(tagTable, "BOOK_ID", bigint, false);
    final Column tagCol = column(tagTable, "TAG", varchar, false);
    final Column tagPosCol = column(tagTable, "POS", intSql, false);
    final ForeignKey tagFk = foreignKey(tagTable, "FK_TAG_BOOK", itemPk, tagBookCol);

    final Table detailTable = table("DETAIL");
    final Column detailIdCol = column(detailTable, "ID", bigint, false);
    final PrimaryKey detailPk = primaryKey(detailTable, "PK_DETAIL", detailIdCol);

    final Table genTable = table("ID_GEN");
    final Column genNameCol = column(genTable, "NAME", varchar, false);
    final Column genValueCol = column(genTable, "VAL", bigint, false);

    final QueryColumnSet titles = SQL.createQueryColumnSet();
    final Column titlesId;
    final Column titlesTitle;
    final Procedure extend = SQL.createProcedure();

    final EntityMappings mappings = CWMORMFactory.eINSTANCE.createEntityMappings();

    Fixture() {
        root.getOwnedElement().add(library);
        genre.setName("Genre");
        for (String l : List.of("FICTION", "SCIENCE")) {
            EnumerationLiteral lit = DatatypesFactory.eINSTANCE.createEnumerationLiteral();
            lit.setName(l);
            genre.getLiteral().add(lit);
        }
        library.getOwnedElement().add(genre);
        bookGenre = attribute(book, "genre", genre, 0, 1);
        item.setIsAbstract(true);
        library.getOwnedElement().addAll(List.of(generalization(book, item), generalization(magazine, item)));
        library.getOwnedElement().addAll(List.of(association("BookPublisher", booksOfPublisher, publisherOfBook),
                association("BookAuthor", booksOfAuthor, authorsOfBook),
                association("BookDetail", bookOfDetail, detailOfBook)));

        catalog.setName("LIBDB");
        schema.setName("LIB");
        catalog.getOwnedElement().add(schema);
        root.getOwnedElement().add(catalog);
        extend.setName("EXTEND_LOAN");
        for (String p : List.of("days", "result")) {
            SQLParameter param = SQL.createSQLParameter();
            param.setName(p);
            param.setType(intSql);
            param.setKind(p.equals("days") ? ParameterDirectionKind.PDK_IN : ParameterDirectionKind.PDK_OUT);
            extend.getParameter().add(param);
        }
        schema.getOwnedElement().add(extend);
        titles.setName("BOOK_TITLES");
        QueryExpression q = DatatypesFactory.eINSTANCE.createQueryExpression();
        q.setBody("SELECT ID, TITLE FROM LIB.ITEM");
        q.setLanguage("SQL");
        titles.setQuery(q);
        schema.getOwnedElement().add(titles);
        titlesId = column(titles, "ID", bigint, false);
        titlesTitle = column(titles, "TITLE", varchar, false);

        mappings.setName("library");
        root.getOwnedElement().add(mappings);
    }

    /** The Ecore index of the class model: {@code library.ecore}, found by names. */
    EcoreIndex index() {
        ResourceSet rs = EormWriter.newResourceSet();
        Resource r = rs.getResource(URI.createFileURI(ecoreFile().toString()), true);
        return EcoreIndex.of(List.of((EPackage) r.getContents().get(0)));
    }

    /** The {@code .ecore} file of the class model. */
    static Path ecoreFile() {
        return ecoreDir().resolve("fixture/library.ecore");
    }

    /** The folder of the {@code .ecore} test data. */
    static Path ecoreDir() {
        try {
            return Path.of(Fixture.class.getResource("/ecore").toURI());
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Converts the mapping set with the explicit profile enforced. */
    CwmOrmToEorm.Result convertStrict() {
        return CwmOrmToEorm.convert(mappings, index());
    }

    /** Converts the mapping set leniently (the forms, not the profile, are under test). */
    CwmOrmToEorm.Result convert() {
        return CwmOrmToEorm.convert(mappings, index(), true);
    }

    private static Package pkg(String name) {
        Package p = CORE.createPackage();
        p.setName(name);
        return p;
    }

    private DataType dataType(String name) {
        DataType d = CORE.createDataType();
        d.setName(name);
        library().getOwnedElement().add(d);
        return d;
    }

    private Class cls(String name) {
        Class c = CORE.createClass();
        c.setName(name);
        library().getOwnedElement().add(c);
        return c;
    }

    /** The package, also while the field initialisers run. */
    private Package library() {
        return library;
    }

    private static Attribute attribute(Class owner, String name, Classifier type, long lower, long upper) {
        Attribute a = CORE.createAttribute();
        a.setName(name);
        a.setType(type);
        a.setMultiplicity(multiplicity(lower, upper));
        owner.getFeature().add(a);
        return a;
    }

    private static Operation operation(Class owner, String name) {
        Operation o = BehavioralFactory.eINSTANCE.createOperation();
        o.setName(name);
        owner.getFeature().add(o);
        return o;
    }

    private static Multiplicity multiplicity(long lower, long upper) {
        Multiplicity m = CORE.createMultiplicity();
        MultiplicityRange r = CORE.createMultiplicityRange();
        r.setLower(lower);
        r.setUpper(upper);
        m.getRange().add(r);
        return m;
    }

    private static Generalization generalization(Class child, Class parent) {
        Generalization g = RelationshipsFactory.eINSTANCE.createGeneralization();
        g.setName(child.getName() + " extends " + parent.getName());
        g.setChild(child);
        g.setParent(parent);
        return g;
    }

    private static AssociationEnd end(String name, Class type, boolean navigable, long lower, long upper) {
        AssociationEnd e = RelationshipsFactory.eINSTANCE.createAssociationEnd();
        e.setName(name);
        e.setType(type);
        e.setIsNavigable(navigable);
        e.setMultiplicity(multiplicity(lower, upper));
        return e;
    }

    private static Association association(String name, AssociationEnd a, AssociationEnd b) {
        Association as = RelationshipsFactory.eINSTANCE.createAssociation();
        as.setName(name);
        as.getFeature().addAll(List.of(a, b));
        return as;
    }

    private SQLSimpleType sqlType(String name) {
        SQLSimpleType t = SQL.createSQLSimpleType();
        t.setName(name);
        catalogOwned().getOwnedElement().add(t);
        return t;
    }

    private Catalog catalogOwned() {
        return catalog;
    }

    private Table table(String name) {
        Table t = SQL.createTable();
        t.setName(name);
        schemaOwned().getOwnedElement().add(t);
        return t;
    }

    private Schema schemaOwned() {
        return schema;
    }

    private static Column column(org.eclipse.daanse.cwm.model.cwm.resource.relational.ColumnSet table, String name,
            SQLSimpleType type, boolean nullable) {
        Column c = SQL.createColumn();
        c.setName(name);
        c.setType(type);
        c.setIsNullable(nullable ? NullableType.COLUMN_NULLABLE : NullableType.COLUMN_NO_NULLS);
        table.getFeature().add(c);
        return c;
    }

    private static PrimaryKey primaryKey(Table t, String name, Column... columns) {
        PrimaryKey pk = SQL.createPrimaryKey();
        pk.setName(name);
        pk.getFeature().addAll(List.of(columns));
        t.getOwnedElement().add(pk);
        return pk;
    }

    private static ForeignKey foreignKey(Table t, String name, PrimaryKey target, Column... columns) {
        ForeignKey fk = SQL.createForeignKey();
        fk.setName(name);
        fk.getFeature().addAll(List.of(columns));
        fk.setUniqueKey(target);
        t.getOwnedElement().add(fk);
        return fk;
    }
}
