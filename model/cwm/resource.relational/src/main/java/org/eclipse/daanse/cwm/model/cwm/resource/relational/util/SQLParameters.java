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
package org.eclipse.daanse.cwm.model.cwm.resource.relational.util;

import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.BehavioralFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLParameter;

/**
 * Creates {@link SQLParameter}s — the SQL-typed specialization of
 * {@code behavioral::Parameter}. A stored query ({@code QueryColumnSet}) owns
 * them via {@code ownedElement} in placeholder order (see
 * {@link QueryColumnSets}); a procedure owns them via
 * {@code BehavioralFeature.parameter}. Creation rule: see
 * {@code objectmodel.core.util.Models}.
 */
public final class SQLParameters {

    private SQLParameters() {
    }

    /** An IN SQLParameter of {@code type}, owned by {@code parent} via {@code ownedElement}. */
    public static SQLParameter create(Namespace parent, String name, Classifier type) {
        Objects.requireNonNull(parent, "parent");
        SQLParameter parameter = in(name, type);
        parent.getOwnedElement().add(parameter);
        return parameter;
    }

    /** An IN SQLParameter of {@code type}, owned by {@code parent} via {@code parameter}. */
    public static SQLParameter create(BehavioralFeature parent, String name, Classifier type) {
        Objects.requireNonNull(parent, "parent");
        SQLParameter parameter = in(name, type);
        parent.getParameter().add(parameter);
        return parameter;
    }

    private static SQLParameter in(String name, Classifier type) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        SQLParameter parameter = RelationalFactory.eINSTANCE.createSQLParameter();
        parameter.setName(name);
        parameter.setKind(ParameterDirectionKind.PDK_IN);
        parameter.setType(type);
        return parameter;
    }
}
