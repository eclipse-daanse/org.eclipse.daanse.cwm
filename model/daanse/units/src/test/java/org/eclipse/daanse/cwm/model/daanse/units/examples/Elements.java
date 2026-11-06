/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.units.examples;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.BehavioralFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Parameter;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.XMIResource;

/**
 * The CWM elements the tests and examples of this module are built of: each created by the factory
 * of its package, named, and owned where the CWM model keeps it. Also the short ids the examples
 * are saved with.
 */
public final class Elements {

    private static final CoreFactory CORE = CoreFactory.eINSTANCE;
    private static final RelationalFactory RELATIONAL = RelationalFactory.eINSTANCE;

    private Elements() {
    }

    /** A model, the root of an example. */
    public static Model model(String name) {
        Model model = CORE.createModel();
        model.setName(Objects.requireNonNull(name, "name"));
        return model;
    }

    /** A package in the {@code ownedElement} of {@code parent}. */
    public static Package pkg(Namespace parent, String name) {
        return owned(parent, name, CORE.createPackage());
    }

    /** A data type in the {@code ownedElement} of {@code parent}. */
    public static DataType dataType(Namespace parent, String name) {
        return owned(parent, name, CORE.createDataType());
    }

    /** An in parameter of {@code type} in the {@code ownedElement} of {@code parent}. */
    public static Parameter parameter(Namespace parent, String name, Classifier type) {
        Parameter parameter = BehavioralFactory.eINSTANCE.createParameter();
        parameter.setKind(ParameterDirectionKind.PDK_IN);
        parameter.setType(Objects.requireNonNull(type, "type"));
        return owned(parent, name, parameter);
    }

    /** A catalog in the {@code ownedElement} of {@code parent}. */
    public static Catalog catalog(Namespace parent, String name) {
        return owned(parent, name, RELATIONAL.createCatalog());
    }

    /** A schema of {@code catalog}. */
    public static Schema schema(Catalog catalog, String name) {
        return owned(catalog, name, RELATIONAL.createSchema());
    }

    /** A table of {@code schema}. */
    public static Table table(Schema schema, String name) {
        return owned(schema, name, RELATIONAL.createTable());
    }

    /** A column of {@code type} in {@code table}. */
    public static Column column(Table table, String name, Classifier type) {
        Column column = RELATIONAL.createColumn();
        column.setName(Objects.requireNonNull(name, "name"));
        column.setType(Objects.requireNonNull(type, "type"));
        table.getFeature().add(column);
        return column;
    }

    /** An SQL type of number {@code typeNumber} in the {@code ownedElement} of {@code parent}. */
    public static SQLSimpleType sqlType(Namespace parent, String name, long typeNumber) {
        SQLSimpleType type = RELATIONAL.createSQLSimpleType();
        type.setTypeNumber(typeNumber);
        return owned(parent, name, type);
    }

    /**
     * Short ids for every object of {@code resource}: an underscore and the name of an element a
     * package owns (or of a root), else the id of its container, a dot and its name; an unnamed
     * object is named by the feature that holds it, numbered where the feature holds several. A
     * repeated id gets a number.
     */
    public static void shortIds(Resource resource) {
        if (resource instanceof XMIResource xmi) {
            Set<String> used = new HashSet<>();
            for (EObject root : resource.getContents()) {
                assign(xmi, root, null, used);
            }
        }
    }

    private static void assign(XMIResource xmi, EObject o, String containerId, Set<String> used) {
        boolean top = containerId == null || o.eContainer() instanceof Package
                && "ownedElement".equals(o.eContainingFeature().getName());
        String base = top ? "_" + segment(o) : containerId + "." + segment(o);
        String id = base;
        for (int n = 2; !used.add(id); n++) {
            id = base + "-" + n;
        }
        xmi.setID(o, id);
        for (EObject child : o.eContents()) {
            assign(xmi, child, id, used);
        }
    }

    private static String segment(EObject o) {
        String name = o instanceof ModelElement m ? m.getName() : null;
        if (name == null) {
            EStructuralFeature feature = o.eContainingFeature();
            if (feature == null) {
                name = o.eClass().getName().toLowerCase(java.util.Locale.ROOT);
            } else {
                name = feature.getName();
                if (o.eContainer().eGet(feature) instanceof List<?> list && list.size() > 1) {
                    name += list.indexOf(o) + 1;
                }
            }
        }
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static <T extends ModelElement> T owned(Namespace parent, String name, T element) {
        element.setName(Objects.requireNonNull(name, "name"));
        Objects.requireNonNull(parent, "parent").getOwnedElement().add(element);
        return element;
    }
}
