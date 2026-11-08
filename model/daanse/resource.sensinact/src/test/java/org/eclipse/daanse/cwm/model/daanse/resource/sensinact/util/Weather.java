/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Multiplicity;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.MultiplicityRange;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Generalization;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.RelationshipsFactory;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Provider;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Resource;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Service;

/**
 * A small class model and a provider for the tests of the helpers, the structure and the validator.
 *
 * <pre>
 * source:   Station { name: String, readings: Reading[*], location: Location }
 *           Reading { value: Double, takenAt: Timestamp, state: String }, Gauge &lt; Reading { level: Double }
 * provider: weather › climate › temperature: Double (required), state: String (optional)
 * </pre>
 */
final class Weather {

    private static final CoreFactory CORE = CoreFactory.eINSTANCE;

    final Model model = CORE.createModel();
    final Package source = pkg(model, "source");
    final DataType string = type(model, "String");
    final DataType decimal = type(model, "Double");
    final DataType timestamp = type(model, "Timestamp");
    final Class station = cls(source, "Station");
    final Class location = cls(source, "Location");
    final Class reading = cls(source, "Reading");
    final Class gauge = cls(source, "Gauge", reading);
    final Attribute name = attr(station, "name", string);
    final Attribute readings = many(station, "readings", reading);
    final Attribute locationRef = attr(station, "location", location);
    final Attribute value = attr(reading, "value", decimal);
    final Attribute takenAt = attr(reading, "takenAt", timestamp);
    final Attribute state = attr(reading, "state", string);
    final Attribute level = attr(gauge, "level", decimal);

    final Package providers = pkg(model, "providers");
    final Provider weather = Providers.create(providers, "weather");
    final Service climate = Services.create(weather, "climate");
    final Resource temperature = Resources.create(climate, "temperature", decimal);
    final Resource stateResource = Resources.optional(climate, "state", string);

    Weather() {
        model.setName("sensors");
    }

    private static Package pkg(Namespace parent, String name) {
        Package p = CORE.createPackage();
        p.setName(name);
        parent.getOwnedElement().add(p);
        return p;
    }

    private static DataType type(Namespace owner, String name) {
        DataType t = CORE.createDataType();
        t.setName(name);
        owner.getOwnedElement().add(t);
        return t;
    }

    /** A class in {@code pkg}, a child of {@code parents} through generalizations owned in {@code pkg}. */
    private static Class cls(Package pkg, String name, Class... parents) {
        Class c = CORE.createClass();
        c.setName(name);
        pkg.getOwnedElement().add(c);
        for (Class parent : parents) {
            Generalization g = RelationshipsFactory.eINSTANCE.createGeneralization();
            g.setName(name + "_" + parent.getName());
            g.setChild(c);
            g.setParent(parent);
            pkg.getOwnedElement().add(g);
        }
        return c;
    }

    private static Attribute attr(Class owner, String name, Classifier type) {
        Attribute a = CORE.createAttribute();
        a.setName(name);
        a.setType(type);
        owner.getFeature().add(a);
        return a;
    }

    /** An attribute of many values (multiplicity 0..*). */
    private static Attribute many(Class owner, String name, Classifier type) {
        Attribute a = attr(owner, name, type);
        Multiplicity m = CORE.createMultiplicity();
        MultiplicityRange r = CORE.createMultiplicityRange();
        r.setLower(0);
        r.setUpper(-1);
        m.getRange().add(r);
        a.setMultiplicity(m);
        return a;
    }
}
