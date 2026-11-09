/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.emf.ecore;

import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.CORE;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.DATATYPES;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.alias;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.association;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.attribute;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.bounds;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.cls;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.constraint;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.dataType;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.description;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.end;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.enumeration;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.expression;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.generalization;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.iface;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.in;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.literal;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.method;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.model;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.operation;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.parameter;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.pkg;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.range;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.stereotype;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.subsystem;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.tagged;
import static org.eclipse.daanse.cwm.emf.ecore.CwmModels.whole;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.Enumeration;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.UnionMember;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.BehavioralFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Interface;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Operation;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Parameter;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ChangeableKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Dependency;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.OrderingKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ScopeKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.VisibilityKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.instance.InstanceFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AggregationKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.xmi.XMLResource;

/**
 * The models of the test corpus, one per group of mapping rules; each file under
 * {@code src/test/resources/corpus} is written from here ({@link #write}). Every element stands for
 * a rule of the module README.
 */
final class CorpusModels {

    /** The corpus files and the models they hold. */
    enum File {
        PACKAGES("packages.xmi", CorpusModels::packages),
        CLASSIFIERS("classifiers.xmi", CorpusModels::classifiers),
        CLASSIFIERS_INVALID("classifiers-invalid.xmi", CorpusModels::classifiersInvalid),
        ATTRIBUTES("attributes.xmi", CorpusModels::attributes),
        RELATIONSHIPS("relationships.xmi", CorpusModels::relationships),
        RELATIONSHIPS_INVALID("relationships-invalid.xmi", CorpusModels::relationshipsInvalid),
        BEHAVIOUR("behaviour.xmi", CorpusModels::behaviour),
        BEHAVIOUR_INVALID("behaviour-invalid.xmi", CorpusModels::behaviourInvalid),
        CONSTRAINTS("constraints.xmi", CorpusModels::constraints),
        DOCUMENTATION("documentation.xmi", CorpusModels::documentation),
        NAMES("names.xmi", CorpusModels::names),
        SELECTION("selection.xmi", CorpusModels::selection),
        ECORE_FORMS("ecore-forms.xmi", CorpusModels::ecoreForms);

        final String fileName;
        final Supplier<Model> model;

        File(String fileName, Supplier<Model> model) {
            this.fileName = fileName;
            this.model = model;
        }
    }

    private static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore#//";

    private CorpusModels() {
    }

    /**
     * Packages: a model passed through, packages and a subsystem nested in a converted package, a
     * package of data types next to it that imports a class, a package that only holds a package,
     * and what stays out: a relational catalog, a package of data types only a column uses, a
     * dependency, an event, a union and an instance.
     */
    static Model packages() {
        Model shop = model("shop");
        Package types = pkg(shop, "types");
        DataType integer = dataType(types, "Integer");
        DataType money = tagged(dataType(types, "Money"), CwmAnnotations.TAG_INSTANCE_CLASS_NAME, "java.math.BigDecimal");
        Package classes = pkg(shop, "classes");
        Class customer = cls(classes, "Customer");
        attribute(customer, "number", integer);
        Package enumerations = pkg(classes, "enumerations");
        attribute(customer, "status", enumeration(enumerations, "Status", "OPEN", "CLOSED"));
        Package pricing = subsystem(classes, "pricing");
        Class price = cls(pricing, "Price");
        attribute(price, "amount", money);
        types.getImportedElement().add(customer);
        Dependency uses = in(classes, CORE.createDependency());
        uses.setName("CustomerUsesPrice");
        uses.getClient().add(customer);
        uses.getSupplier().add(price);
        in(classes, BehavioralFactory.eINSTANCE.createEvent()).setName("Ordered");
        var payment = in(types, DATATYPES.createUnion());
        payment.setName("Payment");
        UnionMember cash = DATATYPES.createUnionMember();
        cash.setName("cash");
        cash.setType(money);
        payment.getFeature().add(cash);
        in(classes, InstanceFactory.eINSTANCE.createObject()).setName("sampleCustomer");

        Package glue = pkg(shop, "glue");
        cls(pkg(glue, "inner"), "Note");

        Package storage = pkg(shop, "storage");
        DataType code = dataType(storage, "Code");
        Catalog db = in(storage, RelationalFactory.eINSTANCE.createCatalog());
        db.setName("db");
        Schema schema = in(db, RelationalFactory.eINSTANCE.createSchema());
        schema.setName("public");
        Table table = in(schema, RelationalFactory.eINSTANCE.createTable());
        table.setName("customer");
        Column column = RelationalFactory.eINSTANCE.createColumn();
        column.setName("code");
        column.setType(code);
        table.getFeature().add(column);
        return shop;
    }

    /**
     * Classifiers: an abstract class, multiple inheritance from a class and an interface, data
     * types by the default mapping, by tag and by a qualified Java name, type aliases (also of an
     * alias), an ordered enumeration with given and positional values, one with a value taken
     * twice.
     */
    static Model classifiers() {
        Model parties = model("parties");
        Package p = pkg(parties, "parties");
        DataType integer = dataType(p, "Integer");
        DataType text = dataType(p, "String");
        DataType year = tagged(dataType(p, "Year"), CwmAnnotations.TAG_INSTANCE_CLASS_NAME, "java.time.Year");
        DataType duration = dataType(p, "java.time.Duration");
        DataType money = tagged(dataType(p, "Money"), CwmAnnotations.TAG_INSTANCE_CLASS_NAME, "java.math.BigDecimal");
        var amount = alias(p, "Amount", money);
        var total = alias(p, "Total", amount);
        Interface addressable = iface(p, "Addressable");
        operation(addressable, "address", text);
        Class party = cls(p, "Party");
        party.setIsAbstract(true);
        attribute(party, "since", year);
        Class person = cls(p, "Person");
        attribute(person, "age", integer);
        attribute(person, "timeout", duration);
        Class company = cls(p, "Company");
        attribute(company, "revenue", amount);
        attribute(company, "total", total);
        generalization(p, person, party);
        generalization(p, person, addressable);
        generalization(p, company, party);
        Enumeration status = enumeration(p, "Status", "NEW", "PAID", "SHIPPED");
        status.setIsOrdered(true);
        status.getLiteral().get(0).setValue(expression("10"));
        status.getLiteral().get(1).setValue(expression("11"));
        Enumeration priority = enumeration(p, "Priority", "LOW", "MEDIUM", "HIGH");
        priority.getLiteral().get(0).setValue(expression("1"));
        priority.getLiteral().get(2).setValue(expression("1"));
        attribute(person, "status", status);
        attribute(person, "priority", priority);
        return parties;
    }

    /** Classifiers that cannot be converted: a data type without Java class, a cycle, bad aliases. */
    static Model classifiersInvalid() {
        Model m = model("invalid");
        Package p = pkg(m, "invalid");
        DataType unknown = dataType(p, "Unknown");
        Class a = cls(p, "A");
        Class b = cls(p, "B");
        attribute(a, "value", unknown);
        generalization(p, a, b);
        generalization(p, b, a);
        Enumeration kind = enumeration(p, "Kind", "X");
        alias(p, "KindAlias", kind);
        var loop = alias(p, "Loop", null);
        loop.setType(loop);
        return m;
    }

    /**
     * Attributes: bounds, an unbounded ordered list, two multiplicity ranges, upper bound 0,
     * frozen and add-only, classifier scope, the iD tag, initial values (a literal of a data type,
     * of an enumeration, and an OCL expression), an attribute typed by a class, one without
     * multiplicity.
     */
    static Model attributes() {
        Model crm = model("crm");
        Package p = pkg(crm, "crm");
        DataType text = dataType(p, "String");
        DataType integer = dataType(p, "Integer");
        DataType flag = dataType(p, "Boolean");
        Enumeration status = enumeration(p, "Status", "OPEN", "CLOSED");
        Class address = cls(p, "Address");
        attribute(address, "street", text);
        Class customer = cls(p, "Customer");
        bounds(attribute(customer, "name", text), 1, 1);
        Attribute tags = bounds(attribute(customer, "tags", text), 0, -1);
        tags.setOrdering(OrderingKind.OK_ORDERED);
        Attribute codes = bounds(attribute(customer, "codes", integer), 2, 5);
        codes.getMultiplicity().getRange().add(range(7, -1));
        bounds(attribute(customer, "legacy", text), 0, 0);
        Attribute createdAt = attribute(customer, "createdAt", integer);
        createdAt.setChangeability(ChangeableKind.CK_FROZEN);
        Attribute history = bounds(attribute(customer, "history", text), 0, -1);
        history.setChangeability(ChangeableKind.CK_ADD_ONLY);
        attribute(customer, "counter", integer).setOwnerScope(ScopeKind.SK_CLASSIFIER);
        tagged(attribute(customer, "id", integer), CwmAnnotations.TAG_ID, "true");
        attribute(customer, "active", flag).setInitialValue(literal("true"));
        attribute(customer, "rating", integer).setInitialValue(literal("3"));
        attribute(customer, "status", status).setInitialValue(literal("OPEN"));
        attribute(customer, "score", integer).setInitialValue(expression("self.rating * 2", "OCL"));
        attribute(customer, "address", address);
        attribute(customer, "note", text);
        return crm;
    }

    /**
     * Associations over two packages: both ends navigable, one end navigable, a composite at the
     * first and at the second end, a shared aggregate, a reflexive one, an ordered end, one without
     * navigable end, and one whose ends lie in different packages.
     */
    static Model relationships() {
        Model trade = model("trade");
        Package sales = pkg(trade, "sales");
        Package catalog = pkg(trade, "catalog");
        Class order = cls(sales, "Order");
        Class line = cls(sales, "OrderLine");
        Class customer = cls(sales, "Customer");
        Class invoice = cls(sales, "Invoice");
        Class employee = cls(sales, "Employee");
        Class product = cls(catalog, "Product");
        Class tag = cls(catalog, "Tag");
        Class warehouse = cls(catalog, "Warehouse");
        Class shelf = cls(catalog, "Shelf");
        association(sales, "OrderCustomer", end("orders", order, true, 0, -1), end("customer", customer, true, 1, 1));
        association(sales, "OrderInvoice", end("invoice", invoice, true, 0, 1), end("order", order, false, 1, 1));
        AssociationEnd lines = end("lines", line, true, 1, -1);
        lines.setOrdering(OrderingKind.OK_ORDERED);
        association(sales, "OrderLines", whole(end("order", order, true, 1, 1), AggregationKind.AK_COMPOSITE), lines);
        association(sales, "Management", end("manager", employee, true, 0, 1), end("reports", employee, true, 0, -1));
        association(sales, "LineProduct", end("product", product, true, 1, 1), end("lines", line, false, 0, -1));
        association(sales, "OrderProduct", end("orders", order, false, 0, -1), end("products", product, false, 0, -1));
        association(catalog, "WarehouseShelves", end("shelves", shelf, true, 0, -1),
                whole(end("warehouse", warehouse, true, 1, 1), AggregationKind.AK_COMPOSITE));
        association(catalog, "ProductTags", whole(end("product", product, true, 0, -1), AggregationKind.AK_AGGREGATE),
                end("tags", tag, true, 0, -1));
        return trade;
    }

    /**
     * Associations that cannot be converted: three ends, an own attribute, aggregation at both
     * ends, a composite end with upper bound *, an end typed by a class of a package that is not
     * chosen.
     */
    static Model relationshipsInvalid() {
        Model trade = model("trade");
        Package sales = pkg(trade, "sales");
        Package elsewhere = pkg(trade, "elsewhere");
        Class a = cls(sales, "A");
        Class b = cls(sales, "B");
        Class c = cls(sales, "C");
        Class x = cls(elsewhere, "X");
        association(sales, "Ternary", end("a", a, true, 0, 1), end("b", b, true, 0, 1), end("c", c, true, 0, 1));
        association(sales, "WithAttribute", end("a", a, true, 0, 1), end("b", b, true, 0, 1),
                attribute("since", null));
        association(sales, "BothAggregate", whole(end("a", a, true, 0, 1), AggregationKind.AK_AGGREGATE),
                whole(end("b", b, true, 0, 1), AggregationKind.AK_COMPOSITE));
        association(sales, "ManyWholes", whole(end("a", a, true, 0, -1), AggregationKind.AK_COMPOSITE),
                end("b", b, true, 0, -1));
        association(sales, "ToElsewhere", end("x", x, true, 0, 1), end("a", a, true, 0, -1));
        return trade;
    }

    /**
     * Behaviour: an interface operation that is a query, realized by an OCL method of a class
     * (an override of an inherited operation), an abstract operation, parameters of every
     * direction with a default value, a Java method, an OCL method of a subclass overriding the
     * superclass, and an OCL and a Java method for one operation in one class.
     */
    static Model behaviour() {
        Model store = model("store");
        Package p = pkg(store, "store");
        DataType integer = dataType(p, "Integer");
        DataType text = dataType(p, "String");
        Interface priced = iface(p, "Priced");
        Operation price = operation(priced, "price", integer);
        price.setIsQuery(true);
        Class product = cls(p, "Product");
        attribute(product, "quantity", integer);
        generalization(p, product, priced);
        method(product, "price", price, "OCL", "self.quantity * 2");
        Operation describe = operation(product, "describe", text);
        describe.setIsAbstract(true);
        Parameter prefix = parameter("prefix", text, ParameterDirectionKind.PDK_IN);
        prefix.setDefaultValue(literal("item"));
        Operation label = operation(product, "label", text, prefix,
                parameter("count", integer, ParameterDirectionKind.PDK_OUT),
                parameter("buffer", text, ParameterDirectionKind.PDK_INOUT));
        method(product, "label", label, "Java", "return prefix + getQuantity();");
        Class book = cls(p, "Book");
        generalization(p, book, product);
        method(book, "describe", describe, "OCL", "'book'");
        method(book, "price", price, "OCL", "self.quantity * 3");
        method(book, "priceInJava", price, "Java", "return getQuantity() * 3L;");
        return store;
    }

    /**
     * Methods without an Ecore form: a second OCL method for one operation, a method of an
     * operation of an unrelated class (C-4-10), one of an operation the class redeclares (C-4-11),
     * one in SQL, one without body, an OCL body of an operation without result.
     */
    static Model behaviourInvalid() {
        Model store = model("store");
        Package p = pkg(store, "store");
        DataType integer = dataType(p, "Integer");
        Class a = cls(p, "A");
        Operation get = operation(a, "get", integer);
        Operation run = operation(a, "run", null);
        method(a, "first", get, "OCL", "1");
        method(a, "second", get, "OCL", "2");
        method(a, "inSql", get, "SQL", "SELECT 1");
        method(a, "empty", get, "OCL", " ");
        method(a, "runInOcl", run, "OCL", "1");
        Class b = cls(p, "B");
        method(b, "foreign", get, "OCL", "1");
        Class c = cls(p, "C");
        generalization(p, c, a);
        operation(c, "get", integer);
        method(c, "redeclared", get, "OCL", "3");
        return store;
    }

    /**
     * Constraints: OCL on a class, on an attribute, on a navigable end, of a stereotype extending
     * two classes, in Java, a second one of the same name; and those without an invariant form: on
     * an operation, on two classes, in SQL, without body, in Java on a data type, on a table.
     */
    static Model constraints() {
        Model bank = model("bank");
        Package p = pkg(bank, "bank");
        DataType integer = dataType(p, "Integer");
        Class account = cls(p, "Account");
        Attribute balance = attribute(account, "balance", integer);
        Operation close = operation(account, "close", null);
        Class savings = cls(p, "Savings");
        generalization(p, savings, account);
        Class customer = cls(p, "Customer");
        AssociationEnd owner = end("owner", customer, true, 1, 1);
        association(p, "AccountOwner", owner, end("accounts", account, true, 0, -1));
        constraint(p, "NotOverdrawn", "OCL", "self.balance >= 0", account);
        constraint(p, "BalanceKnown", "OCL", "not self.balance.oclIsUndefined()", balance);
        constraint(p, "HasOwner", "OCL", "not self.owner.oclIsUndefined()", owner);
        Stereotype audited = stereotype(p, "Audited", account, customer);
        audited.getStereotypeConstraint().add(constraint("Audited", "OCL", "true"));
        constraint(p, "BalanceSet", "Java", "getBalance() != null", account);
        constraint(p, "NotOverdrawn", "OCL", "self.balance > -1", account);
        constraint(p, "OnOperation", "OCL", "true", close);
        constraint(p, "TwoClasses", "OCL", "true", account, customer);
        constraint(p, "InSql", "SQL", "balance >= 0", account);
        constraint(p, "Empty", "OCL", " ", account);
        constraint(p, "OnDataType", "Java", "true", integer);
        Catalog db = in(p, RelationalFactory.eINSTANCE.createCatalog());
        db.setName("db");
        Schema schema = in(db, RelationalFactory.eINSTANCE.createSchema());
        schema.setName("public");
        Table table = in(schema, RelationalFactory.eINSTANCE.createTable());
        table.setName("account");
        constraint(schema, "TableCheck", "SQL", "balance >= 0", table);
        return bank;
    }

    /**
     * Documentation: two descriptions of a package, a remark and a documentation of one class
     * (the remark wins), a description of a type that is no documentation, a documented literal;
     * tagged values; every visibility; two stereotypes on one class.
     */
    static Model documentation() {
        Model docs = model("docs");
        Package p = pkg(docs, "docs");
        DataType text = dataType(p, "String");
        Class customer = cls(p, "Customer");
        customer.setVisibility(VisibilityKind.VK_PRIVATE);
        tagged(customer, "owner", "crm-team");
        Attribute name = attribute(customer, "name", text);
        name.setVisibility(VisibilityKind.VK_PROTECTED);
        attribute(customer, "code", text).setVisibility(VisibilityKind.VK_PACKAGE);
        Attribute note = attribute(customer, "note", text);
        note.setVisibility(VisibilityKind.VK_PUBLIC);
        attribute(customer, "flag", text).setVisibility(VisibilityKind.VK_NOTAPPLICABLE);
        Enumeration status = enumeration(p, "Status", "OPEN", "CLOSED");
        description(p, "Package", "The documentation package.", "documentation", p);
        description(p, "PackageMore", "Its second paragraph.", "documentation", p);
        description(p, "CustomerLong", "A customer of the shop, with name and code.", "documentation", customer);
        description(p, "CustomerShort", "A customer.", "remark", customer);
        description(p, "Name", "The name the customer gives.", "documentation", name);
        description(p, "Note", "Shown as plain text only.", "text/plain", note);
        description(p, "Open", "Still in progress.", "documentation", status.getLiteral().get(0));
        stereotype(p, "Entity", customer);
        stereotype(p, "Audited", customer);
        return docs;
    }

    /** Names that are no Java identifiers: with a space, a leading digit, brackets. */
    static Model names() {
        Model m = model("names");
        Package p = pkg(m, "shop model");
        DataType text = dataType(p, "String");
        Class c = cls(p, "2ndClass");
        attribute(c, "byte[]", text);
        attribute(c, "first name", text);
        attribute(c, "state", enumeration(p, "Order State", "in progress", "done"));
        return m;
    }

    /** Selection: a class in package a uses a class in package b and a data type in package types. */
    static Model selection() {
        Model m = model("selection");
        Class b = cls(pkg(m, "b"), "B");
        DataType text = dataType(pkg(m, "types"), "String");
        Class a = cls(pkg(m, "a"), "A");
        attribute(a, "b", b);
        attribute(a, "text", text);
        return m;
    }

    /**
     * Ecore forms without a CWM counterpart, steered by tags: a parameterized data type that is
     * not serializable, derived attributes with a Java and an OCL derivation, a transient
     * containment, a map entry class and its map, a parameter lower bound, classifiers of the
     * Ecore metamodel as super type and end type; and what is reported: an unknown property tag,
     * the name tag, a feature and a super type of an external classifier, a reference on it.
     */
    static Model ecoreForms() {
        Model m = model("mapping");
        Package p = pkg(m, "mapping");
        DataType text = dataType(p, "String");
        DataType function = tagged(dataType(p, "StringFunction"), CwmAnnotations.TAG_INSTANCE_CLASS_NAME,
                "java.util.function.Function<org.eclipse.emf.ecore.EObject, java.lang.String>");
        tagged(function, "daanse.ecore.serializable", "false");
        Package ecore = pkg(m, "ecore");
        Class eObject = tagged(cls(ecore, "EObject"), CwmAnnotations.TAG_E_CLASSIFIER, ECORE + "EObject");
        attribute(eObject, "ignored", text);
        Class eAttribute = tagged(cls(ecore, "EAttribute"), CwmAnnotations.TAG_E_CLASSIFIER, ECORE + "EAttribute");
        Class eClass = tagged(cls(ecore, "EClass"), CwmAnnotations.TAG_E_CLASSIFIER, ECORE + "EClass");

        Class entry = tagged(cls(p, "StringToStringMap"), "daanse.ecore.instanceClassName", "java.util.Map$Entry");
        attribute(entry, "key", text);
        attribute(entry, "value", text);
        Class mapping = cls(p, "Mapping");
        tagged(mapping, "daanse.ecore.colour", "red");
        tagged(mapping, "daanse.ecore.name", "Renamed");
        tagged(bounds(attribute(mapping, "mid", text), 1, 1), CwmAnnotations.TAG_ID, "true");
        Attribute fqMid = tagged(attribute(mapping, "fqMid", text), CwmAnnotations.TAG_DERIVED, "true");
        constraint(p, "fqMidDerivation", "Java", "return getMid();", fqMid);
        Attribute shout = tagged(attribute(mapping, "shout", text), CwmAnnotations.TAG_DERIVED, "true");
        constraint(p, "shoutDerivation", "OCL", "self.mid.concat('!')", shout);
        bounds(attribute(mapping, "extraMetadata", entry), 0, -1);
        Parameter idFunction = tagged(parameter("idFunction", function, ParameterDirectionKind.PDK_IN),
                "daanse.ecore.lowerBound", "1");
        Parameter source = tagged(parameter("eobject", eObject, ParameterDirectionKind.PDK_IN),
                "daanse.ecore.lowerBound", "1");
        Operation mapId = operation(mapping, "mapId", null, idFunction, source);
        method(mapping, "mapId", mapId, "Java",
                "<%java.util.Objects%>.requireNonNull(idFunction);\nsetMid(idFunction.apply(eobject));");

        Class resource = cls(p, "ResourceMapping");
        generalization(p, resource, eAttribute);
        generalization(p, eObject, mapping);
        association(p, "Temporaries", whole(end("owner", mapping, false, 1, 1), AggregationKind.AK_COMPOSITE),
                tagged(end("temporaries", resource, true, 0, -1), "daanse.ecore.transient", "true"));
        association(p, "Target", end("targetClass", eClass, true, 0, 1), end("mappings", resource, true, 0, -1));
        return m;
    }

    /** Writes the model of {@code file} as XMI into {@code dir}, with ids from the name path. */
    static Path write(File file, Path dir) {
        CwmPackages.registerKnown();
        Path target = dir.resolve(file.fileName).toAbsolutePath().normalize();
        XMLResource r = (XMLResource) CwmPackages.newResourceSet().createResource(URI.createFileURI(target.toString()));
        r.getContents().add(file.model.get());
        assignIds(r);
        try {
            Files.createDirectories(dir);
            r.save(Map.of(XMLResource.OPTION_ENCODING, "UTF-8", XMLResource.OPTION_LINE_WIDTH, 120));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return target;
    }

    /**
     * An id for every object: its metaclass and its name path, an unnamed object named after the
     * feature that holds it; characters an XML id cannot hold become {@code _}, a second equal id
     * gets a counter.
     */
    private static void assignIds(XMLResource r) {
        Map<EObject, String> paths = new IdentityHashMap<>();
        Set<String> used = new HashSet<>();
        for (TreeIterator<EObject> it = r.getAllContents(); it.hasNext();) {
            EObject o = it.next();
            String own = o instanceof ModelElement me && me.getName() != null ? me.getName()
                    : o.eContainingFeature() == null ? "root" : o.eContainingFeature().getName();
            String parent = o.eContainer() == null ? null : paths.get(o.eContainer());
            String path = parent == null ? own : parent + "." + own;
            paths.put(o, path);
            String id = (o.eClass().getName() + "_" + path).replaceAll("[^A-Za-z0-9_.-]", "_");
            String unique = id;
            for (int i = 2; !used.add(unique); i++) {
                unique = id + "_" + i;
            }
            r.setID(o, unique);
        }
    }
}
