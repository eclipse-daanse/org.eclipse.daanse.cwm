/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.sensinact.examples;

import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.DatatypesFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.Enumeration;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.EnumerationLiteral;
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

/**
 * Builds the CWM class models the examples map from: packages, data types, enumerations, classes
 * with their generalizations (owned next to the child), attributes of one or many values. A class
 * typed attribute is the CWM form of an Ecore containment reference.
 */
public final class ClassModels {

    private static final CoreFactory CORE = CoreFactory.eINSTANCE;

    private ClassModels() {
    }

    /** A model. */
    public static Model model(String name) {
        Model model = CORE.createModel();
        model.setName(name);
        return model;
    }

    /** A package in {@code parent}. */
    public static Package pkg(Namespace parent, String name) {
        Package p = CORE.createPackage();
        p.setName(name);
        parent.getOwnedElement().add(p);
        return p;
    }

    /** A data type in {@code owner}. */
    public static DataType type(Namespace owner, String name) {
        DataType t = CORE.createDataType();
        t.setName(name);
        owner.getOwnedElement().add(t);
        return t;
    }

    /** An enumeration with literals in {@code owner}. */
    public static Enumeration enumeration(Namespace owner, String name, String... literals) {
        Enumeration e = DatatypesFactory.eINSTANCE.createEnumeration();
        e.setName(name);
        for (String l : literals) {
            EnumerationLiteral literal = DatatypesFactory.eINSTANCE.createEnumerationLiteral();
            literal.setName(l);
            e.getLiteral().add(literal);
        }
        owner.getOwnedElement().add(e);
        return e;
    }

    /** A class in {@code pkg}, a child of {@code parents} through generalizations owned in {@code pkg}. */
    public static Class cls(Package pkg, String name, Class... parents) {
        Class c = CORE.createClass();
        c.setName(name);
        pkg.getOwnedElement().add(c);
        for (Class parent : parents) {
            Generalization g = RelationshipsFactory.eINSTANCE.createGeneralization();
            g.setName(name + "_" + parent.getName());
            g.setChild(c);
            g.setParent(Objects.requireNonNull(parent, "parent"));
            pkg.getOwnedElement().add(g);
        }
        return c;
    }

    /** A single-valued attribute. */
    public static Attribute attr(Class owner, String name, Classifier type) {
        Attribute a = CORE.createAttribute();
        a.setName(name);
        a.setType(Objects.requireNonNull(type, "type"));
        owner.getFeature().add(a);
        return a;
    }

    /** Single-valued attributes of one type. */
    public static void attrs(Class owner, Classifier type, String... names) {
        for (String n : names) {
            attr(owner, n, type);
        }
    }

    /** An attribute of many values (multiplicity 0..*). */
    public static Attribute many(Class owner, String name, Classifier type) {
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
