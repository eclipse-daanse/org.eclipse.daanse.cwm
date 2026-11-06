/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.units.util;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.daanse.units.SystemOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.eclipse.daanse.cwm.model.daanse.units.examples.Elements;

/**
 * A laboratory: model {@code lab} with the SI, a package {@code sensors} with the data types
 * {@code Float}, {@code String} and {@code Celsius} and a class {@code Meter} (reading: Float,
 * label: String, inside: Celsius, outside: Celsius), and a package {@code quantities} for the
 * quantities.
 */
final class Lab {

    final Model model = Elements.model("lab");
    final SystemOfUnits si = SI.create(model);
    final Package sensors = Elements.pkg(model, "sensors");
    final DataType floatType = Elements.dataType(sensors, "Float");
    final DataType stringType = Elements.dataType(sensors, "String");
    final DataType celsius = Elements.dataType(sensors, "Celsius");
    final Class meter = cls(sensors, "Meter");
    final Attribute reading = attribute(meter, "reading", floatType);
    final Attribute label = attribute(meter, "label", stringType);
    final Attribute inside = attribute(meter, "inside", celsius);
    final Attribute outside = attribute(meter, "outside", celsius);
    final Package quantities = Elements.pkg(model, "quantities");

    Unit unit(String name) {
        return SI.unit(si, name);
    }

    static Class cls(Package owner, String name) {
        Class c = CoreFactory.eINSTANCE.createClass();
        c.setName(name);
        owner.getOwnedElement().add(c);
        return c;
    }

    static Attribute attribute(Class owner, String name, Classifier type) {
        Attribute a = CoreFactory.eINSTANCE.createAttribute();
        a.setName(name);
        a.setType(type);
        owner.getFeature().add(a);
        return a;
    }
}
