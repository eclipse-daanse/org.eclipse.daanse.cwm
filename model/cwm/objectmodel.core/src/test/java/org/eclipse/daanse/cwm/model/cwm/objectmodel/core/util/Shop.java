/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;

/**
 * The model the {@link Selection} examples use:
 *
 * <pre>
 * shop                 Model
 *   sales              Package
 *     customer         Class
 *     orders           Class
 *       id             Attribute
 *     order_log        Class
 *   stock              Package
 *     product          Class
 * </pre>
 */
final class Shop {

    static final CoreFactory CF = CoreFactory.eINSTANCE;

    final Model shop = Models.create("shop");
    final Package sales = Packages.create(shop, "sales");
    final Class customer = type(sales, "customer");
    final Class orders = type(sales, "orders");
    final Attribute id = attribute(orders, "id");
    final Class orderLog = type(sales, "order_log");
    final Package stock = Packages.create(shop, "stock");
    final Class product = type(stock, "product");

    static Class type(Package owner, String name) {
        Class type = CF.createClass();
        type.setName(name);
        owner.getOwnedElement().add(type);
        return type;
    }

    static Attribute attribute(Class owner, String name) {
        Attribute attribute = CF.createAttribute();
        attribute.setName(name);
        owner.getFeature().add(attribute);
        return attribute;
    }
}
