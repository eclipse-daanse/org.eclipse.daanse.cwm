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
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.DatatypesFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.Enumeration;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.EnumerationLiteral;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Association;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.RelationshipsFactory;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.junit.jupiter.api.Test;

/**
 * The Ecore element of a CWM element, found by names alone — on an Ecore model written by hand,
 * without any annotation.
 */
class EcoreIndexTest {

    private static final CoreFactory CORE = CoreFactory.eINSTANCE;
    private static final EcoreFactory E = EcoreFactory.eINSTANCE;

    // CWM: model.shop { Customer(name, byte[] picture) 1 -- * Order, Status{OPEN} }, model.archive { Order }
    private final Package model = pkg("model", null);
    private final Package shop = pkg("shop", model);
    private final Package archive = pkg("archive", model);
    private final Class customer = cls("Customer", shop);
    private final Class order = cls("Order", shop);
    private final Class archivedOrder = cls("Order", archive);
    private final Attribute name = attribute("name", customer);
    private final Attribute picture = attribute("picture[]", customer);
    private final AssociationEnd orders = end("orders", order);
    private final AssociationEnd buyer = end("customer", customer);
    private final Enumeration status = DatatypesFactory.eINSTANCE.createEnumeration();
    private final EnumerationLiteral open = DatatypesFactory.eINSTANCE.createEnumerationLiteral();

    // Ecore: shop { Customer(name, pictureArray, orders), Order(customer), Status{OPEN} }, archive { Order }
    private final EPackage eShop = ePackage("shop");
    private final EPackage eArchive = ePackage("archive");
    private final EClass eCustomer = eClass("Customer", eShop);
    private final EClass eOrder = eClass("Order", eShop);
    private final EClass eArchivedOrder = eClass("Order", eArchive);
    private final EEnum eStatus = E.createEEnum();

    EcoreIndexTest() {
        Association placed = RelationshipsFactory.eINSTANCE.createAssociation();
        placed.setName("Customer_Order");
        placed.getFeature().addAll(List.of(buyer, orders));
        shop.getOwnedElement().add(placed);
        status.setName("Status");
        open.setName("OPEN");
        status.getLiteral().add(open);
        shop.getOwnedElement().add(status);

        eAttribute("name", eCustomer);
        eAttribute("pictureArray", eCustomer);
        eReference("orders", eCustomer, eOrder);
        eReference("customer", eOrder, eCustomer);
        eStatus.setName("Status");
        EEnumLiteral lit = E.createEEnumLiteral();
        lit.setName("OPEN");
        eStatus.getELiterals().add(lit);
        eShop.getEClassifiers().add(eStatus);
    }

    @Test
    void classesAttributesAndLiteralsByName() {
        EcoreIndex index = EcoreIndex.of(List.of(eShop, eArchive));

        assertThat(index.eClass(customer)).isSameAs(eCustomer);
        assertThat(index.eClass(order)).as("same name, other package").isSameAs(eOrder);
        assertThat(index.eClass(archivedOrder)).isSameAs(eArchivedOrder);
        assertThat(index.feature(name)).isSameAs(eCustomer.getEStructuralFeature("name"));
        assertThat(index.feature(picture)).as("no Java identifier: Ecore's name for it")
                .isSameAs(eCustomer.getEStructuralFeature("pictureArray"));
        assertThat(index.eEnum(status)).isSameAs(eStatus);
        assertThat(index.find(open)).isSameAs(eStatus.getEEnumLiteral("OPEN"));
        assertThat(index.find(shop)).isSameAs(eShop);
    }

    @Test
    void anAssociationEndIsTheFeatureAtTheClassOfTheOtherEnd() {
        EcoreIndex index = EcoreIndex.of(List.of(eShop, eArchive));

        assertThat(index.feature(orders)).isSameAs(eCustomer.getEStructuralFeature("orders"));
        assertThat(index.feature(buyer)).isSameAs(eOrder.getEStructuralFeature("customer"));
    }

    @Test
    void packagesNamedDifferentlyNeedThePackageMap() {
        eShop.setName("webshop");
        eShop.setNsURI("http://example.org/webshop");

        assertThat(EcoreIndex.of(List.of(eShop, eArchive)).eClass(customer))
                .as("Customer is the only classifier of its name").isSameAs(eCustomer);
        assertThat(EcoreIndex.of(List.of(eShop, eArchive)).eClass(order))
                .as("two classifiers named Order, the package does not tell which").isNull();
        assertThat(EcoreIndex.of(List.of(eShop, eArchive), Map.of("model.shop", "http://example.org/webshop"))
                .eClass(order)).isSameAs(eOrder);
        assertThat(EcoreIndex.of(List.of(eShop, eArchive), Map.of("shop", "webshop")).eClass(order))
                .as("by trailing part and EPackage name").isSameAs(eOrder);
    }

    @Test
    void amongEqualPackageNamesTheSuperPackagesDecide() {
        EPackage eModel = ePackage("model");
        EPackage other = ePackage("other");
        EPackage nestedShop = ePackage("shop");
        EPackage otherShop = ePackage("shop");
        eModel.getESubpackages().add(nestedShop);
        other.getESubpackages().add(otherShop);
        EClass nested = eClass("Customer", nestedShop);
        eClass("Customer", otherShop);

        assertThat(EcoreIndex.of(List.of(other, eModel)).eClass(customer)).isSameAs(nested);
    }

    private static Package pkg(String name, Package parent) {
        Package p = CORE.createPackage();
        p.setName(name);
        if (parent != null) {
            parent.getOwnedElement().add(p);
        }
        return p;
    }

    private static Class cls(String name, Package owner) {
        Class c = CORE.createClass();
        c.setName(name);
        owner.getOwnedElement().add(c);
        return c;
    }

    private static Attribute attribute(String name, Class owner) {
        Attribute a = CORE.createAttribute();
        a.setName(name);
        owner.getFeature().add(a);
        return a;
    }

    private static AssociationEnd end(String name, Class type) {
        AssociationEnd e = RelationshipsFactory.eINSTANCE.createAssociationEnd();
        e.setName(name);
        e.setType(type);
        e.setIsNavigable(true);
        return e;
    }

    private static EPackage ePackage(String name) {
        EPackage p = E.createEPackage();
        p.setName(name);
        p.setNsURI("http://example.org/" + name);
        return p;
    }

    private static EClass eClass(String name, EPackage owner) {
        EClass c = E.createEClass();
        c.setName(name);
        owner.getEClassifiers().add(c);
        return c;
    }

    private static void eAttribute(String name, EClass owner) {
        EAttribute a = E.createEAttribute();
        a.setName(name);
        a.setEType(EcorePackage.Literals.ESTRING);
        owner.getEStructuralFeatures().add(a);
    }

    private static void eReference(String name, EClass owner, EClass type) {
        EReference r = E.createEReference();
        r.setName(name);
        r.setEType(type);
        owner.getEStructuralFeatures().add(r);
    }
}
