/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.recording.util;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;

/**
 * A small class model to hang rules on: model {@code shop} › package {@code sensors} › class
 * {@code Meter} (attributes {@code reading: Integer}, {@code state: String},
 * {@code takenAt: Timestamp}, {@code quality: String}) and class {@code Gauge} (attribute
 * {@code level: Float}); package {@code crm} › class {@code Customer} with the anchors of its history
 * ({@code validFrom}, {@code validTo}, {@code isCurrent}, {@code previousCity},
 * {@code currentCity}) and class {@code CustomerHistory} ({@code name}, {@code city},
 * {@code changedAt}).
 */
final class Shop {

    private static final CoreFactory F = CoreFactory.eINSTANCE;

    final Model model = F.createModel();
    final Package sensors = F.createPackage();
    final Package crm = F.createPackage();
    final DataType integer = dataType("Integer");
    final DataType string = dataType("String");
    final DataType timestamp = dataType("Timestamp");
    final DataType decimal = dataType("Float");
    final DataType bool = dataType("Boolean");
    final Class meter = cls(sensors, "Meter");
    final Class gauge = cls(sensors, "Gauge");
    final Attribute reading = attribute(meter, "reading", integer);
    final Attribute state = attribute(meter, "state", string);
    final Attribute takenAt = attribute(meter, "takenAt", timestamp);
    final Attribute quality = attribute(meter, "quality", string);
    final Attribute level = attribute(gauge, "level", decimal);
    final Class customer = cls(crm, "Customer");
    final Attribute name = attribute(customer, "name", string);
    final Attribute city = attribute(customer, "city", string);
    final Attribute validFrom = attribute(customer, "validFrom", timestamp);
    final Attribute validTo = attribute(customer, "validTo", timestamp);
    final Attribute isCurrent = attribute(customer, "isCurrent", bool);
    final Attribute previousCity = attribute(customer, "previousCity", string);
    final Attribute currentCity = attribute(customer, "currentCity", string);
    final Class customerHistory = cls(crm, "CustomerHistory");
    final Attribute historyName = attribute(customerHistory, "name", string);
    final Attribute historyCity = attribute(customerHistory, "city", string);
    final Attribute changedAt = attribute(customerHistory, "changedAt", timestamp);

    Shop() {
        model.setName("shop");
        sensors.setName("sensors");
        crm.setName("crm");
        model.getOwnedElement().add(sensors);
        model.getOwnedElement().add(crm);
    }

    private DataType dataType(String name) {
        DataType t = F.createDataType();
        t.setName(name);
        model.getOwnedElement().add(t);
        return t;
    }

    private static Class cls(Package owner, String name) {
        Class c = F.createClass();
        c.setName(name);
        owner.getOwnedElement().add(c);
        return c;
    }

    private static Attribute attribute(Class owner, String name, DataType type) {
        Attribute a = F.createAttribute();
        a.setName(name);
        a.setType(type);
        owner.getFeature().add(a);
        return a;
    }
}
