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
package org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.util;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.BehavioralFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.BehavioralFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Parameter;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Namespaces;

/**
 * Creates and lists {@code behavioral::Parameter}s.
 *
 * <p>
 * A Parameter declared by a namespace that is not a behavioral feature — the
 * stored query ({@code relational::QueryColumnSet}) is the main case — lives in
 * that namespace's {@code ownedElement}; its position among the owned
 * Parameters is its position in the statement. Operations own theirs in
 * {@code BehavioralFeature.parameter}. {@code relational::SQLParameter}
 * specializes Parameter, so every reader here sees both kinds: a plain
 * Parameter typed by any Classifier (e.g. a Java type of a TypeSystem) and an
 * SQLParameter typed by an SQL type. Creation rule: see
 * {@code objectmodel.core.util.Models}.
 * </p>
 */
public final class Parameters {

    private Parameters() {
    }

    /** An IN Parameter of {@code type}, owned by {@code parent} via {@code ownedElement}. */
    public static Parameter create(Namespace parent, String name, Classifier type) {
        Objects.requireNonNull(parent, "parent");
        Parameter parameter = in(name, type);
        parent.getOwnedElement().add(parameter);
        return parameter;
    }

    /** An IN Parameter of {@code type}, owned by {@code parent} via {@code parameter}. */
    public static Parameter create(BehavioralFeature parent, String name, Classifier type) {
        Objects.requireNonNull(parent, "parent");
        Parameter parameter = in(name, type);
        parent.getParameter().add(parameter);
        return parameter;
    }

    /**
     * Parameters declared by {@code owner} in list order — SQLParameters
     * included, as they are Parameters.
     */
    public static List<Parameter> declared(Namespace owner) {
        return Namespaces.ownedElements(owner, Parameter.class);
    }

    /** The declared Parameter named {@code name} (exact match). */
    public static Optional<Parameter> find(Namespace owner, String name) {
        return Namespaces.findOwnedByName(owner, Parameter.class, name);
    }

    private static Parameter in(String name, Classifier type) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Parameter parameter = BehavioralFactory.eINSTANCE.createParameter();
        parameter.setName(name);
        parameter.setKind(ParameterDirectionKind.PDK_IN);
        parameter.setType(type);
        return parameter;
    }
}
