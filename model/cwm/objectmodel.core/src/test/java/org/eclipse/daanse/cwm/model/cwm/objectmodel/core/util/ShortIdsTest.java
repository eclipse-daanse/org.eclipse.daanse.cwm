/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.junit.jupiter.api.Test;

class ShortIdsTest {

    private static final CoreFactory F = CoreFactory.eINSTANCE;

    @Test
    void anElementIsNamedFromThePackageThatHoldsIt() {
        Model shop = Models.create("shop");
        Package sales = Packages.create(shop, "sales");
        Class order = cls(sales, "Order");
        Attribute id = attribute(order, "id");
        id.setInitialValue(Expressions.expression("literal", "1"));
        TaggedValues.set(order, "kind", "entity");

        XMIResource r = assign(shop);

        assertThat(r.getID(shop)).isEqualTo("_shop");
        assertThat(r.getID(sales)).isEqualTo("_sales");
        assertThat(r.getID(order)).isEqualTo("_Order");
        assertThat(r.getID(id)).isEqualTo("_Order.id");
        assertThat(r.getID(id.getInitialValue())).isEqualTo("_Order.id.initialValue");
        assertThat(r.getID(order.getTaggedValue().get(0))).isEqualTo("_Order.kind");
    }

    @Test
    void namesThatCollideTakeTheirPackagesAndTheirContentFollows() {
        Model m = Models.create("battery");
        Class a = cls(Packages.create(m, "dragino"), "DecodedObject");
        Attribute batV = attribute(a, "batV");
        Class b = cls(Packages.create(m, "em310udl"), "DecodedObject");
        Class single = cls(Packages.create(m, "lorawan"), "UplinkMessage");

        XMIResource r = assign(m);

        assertThat(r.getID(a)).isEqualTo("_dragino.DecodedObject");
        assertThat(r.getID(batV)).isEqualTo("_dragino.DecodedObject.batV");
        assertThat(r.getID(b)).isEqualTo("_em310udl.DecodedObject");
        assertThat(r.getID(single)).isEqualTo("_UplinkMessage");
    }

    @Test
    void aPackageNamedLikeTheRootTakesTheRootsName() {
        Model root = Models.create("ecowitt");
        Package inner = Packages.create(root, "ecowitt");
        Class weather = cls(inner, "EcoWittWeather");

        XMIResource r = assign(root);

        assertThat(r.getID(root)).isEqualTo("_ecowitt");
        assertThat(r.getID(inner)).isEqualTo("_ecowitt.ecowitt");
        assertThat(r.getID(weather)).isEqualTo("_EcoWittWeather");
    }

    @Test
    void elementsWithTheSameNameInTheSamePlaceKeepApartByANumber() {
        Model m = Models.create("Warehouse");
        Model sub = Models.create(m, "Database Ä");
        DataType first = DataTypes.create(sub, "T");
        DataType second = DataTypes.create(sub, "T");
        TaggedValues.set(second, "kind", "x");

        XMIResource r = assign(m);

        assertThat(r.getID(sub)).isEqualTo("_Database_Ae");
        assertThat(r.getID(first)).isEqualTo("_T");
        assertThat(r.getID(second)).isEqualTo("_T-2");
        assertThat(r.getID(second.getTaggedValue().get(0))).isEqualTo("_T-2.kind");
    }

    @Test
    void everyIdStartsWithAnUnderscoreAndIsAnXmlName() {
        Model m = Models.create("1st model");
        cls(Packages.create(m, "p q"), "A/B");

        XMIResource r = assign(m);

        for (var it = r.getAllContents(); it.hasNext();) {
            assertThat(r.getID(it.next())).matches("_[A-Za-z0-9_.\\-]+");
        }
        assertThat(r.getID(m)).isEqualTo("_1st_model");
    }

    /**
     * An unnamed root takes the name of its kind in lower case, alike in every default locale: in a
     * Turkish one, {@code Index} lower-cased by the rules of the language is {@code ındex}, with a
     * dotless i, which the id would turn into {@code _ndex}.
     */
    @Test
    void anUnnamedRootTakesItsKind_alikeInATurkishLocale() {
        EObject index = SpeakingIdsTest.unnamed("Index");
        Locale before = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        XMIResource r;
        try {
            r = assign(index);
        } finally {
            Locale.setDefault(before);
        }

        assertThat(r.getID(index)).isEqualTo("_index");
    }

    private static XMIResource assign(EObject root) {
        XMIResource resource = new XMIResourceImpl(URI.createURI("test:/ids.xmi"));
        resource.getContents().add(root);
        ShortIds.assign(resource);
        return resource;
    }

    private static Class cls(Namespace owner, String name) {
        Class c = F.createClass();
        c.setName(name);
        owner.getOwnedElement().add(c);
        return c;
    }

    private static Attribute attribute(Class owner, String name) {
        Attribute a = F.createAttribute();
        a.setName(name);
        owner.getFeature().add(a);
        return a;
    }
}
