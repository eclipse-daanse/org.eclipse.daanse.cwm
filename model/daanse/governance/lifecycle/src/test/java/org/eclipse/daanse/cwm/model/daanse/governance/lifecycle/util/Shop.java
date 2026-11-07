/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;

/**
 * A small class model to hang rules on: model {@code shop} › package {@code sensors} › class
 * {@code Meter} (attributes {@code reading: Integer}, {@code state: String},
 * {@code takenAt: Timestamp}) and class {@code Gauge} (attribute {@code level: Float}); the rules
 * live in the package {@code governance} beside it.
 */
final class Shop {

    private static final CoreFactory F = CoreFactory.eINSTANCE;

    final Model model = F.createModel();
    final Package sensors = F.createPackage();
    final Package governance = F.createPackage();
    final DataType integer = dataType("Integer");
    final DataType string = dataType("String");
    final DataType timestamp = dataType("Timestamp");
    final DataType decimal = dataType("Float");
    final Class meter = cls("Meter");
    final Class gauge = cls("Gauge");
    final Attribute reading = attribute(meter, "reading", integer);
    final Attribute state = attribute(meter, "state", string);
    final Attribute takenAt = attribute(meter, "takenAt", timestamp);
    final Attribute level = attribute(gauge, "level", decimal);

    Shop() {
        model.setName("shop");
        sensors.setName("sensors");
        model.getOwnedElement().add(sensors);
        governance.setName("governance");
        model.getOwnedElement().add(governance);
    }

    private DataType dataType(String name) {
        DataType t = F.createDataType();
        t.setName(name);
        model.getOwnedElement().add(t);
        return t;
    }

    private Class cls(String name) {
        Class c = F.createClass();
        c.setName(name);
        sensors.getOwnedElement().add(c);
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
