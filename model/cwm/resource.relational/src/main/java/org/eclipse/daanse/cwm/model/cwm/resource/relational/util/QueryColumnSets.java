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
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.model.cwm.resource.relational.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.DatatypesFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.QueryExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Namespaces;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.QueryColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLParameter;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;

/**
 * Helpers for stored queries modeled as {@link QueryColumnSet}s, including the
 * prepared-statement style: {@link SQLParameter}s placed into the query's
 * {@code ownedElement} list.
 *
 * <p>
 * Convention: a QueryColumnSet is a Namespace (via {@code core::Class}), so its
 * parameters live in {@code getOwnedElement()}. CWM declares
 * {@code Namespace.ownedElement} as unordered; this project relies on the EMF
 * list order (which XMI serialization preserves) — the position of a parameter
 * is its index among the {@code SQLParameter} owned elements, matching the
 * placeholder order in the SQL body. Parameter name is
 * {@code ModelElement.name}, the type a {@code SQLSimpleType}, and the
 * direction {@code PDK_IN}. Tools that legally reorder {@code ownedElement}
 * break the positional mapping; named placeholders ({@code :name}) are the
 * order-independent alternative.
 * </p>
 */
public final class QueryColumnSets {

    private QueryColumnSets() {
    }

    /** QueryColumnSet with name and a {@code QueryExpression(language="SQL")}. */
    public static QueryColumnSet create(String name, String sql) {
        QueryColumnSet qcs = RelationalFactory.eINSTANCE.createQueryColumnSet();
        qcs.setName(name);
        QueryExpression qe = DatatypesFactory.eINSTANCE.createQueryExpression();
        qe.setLanguage("SQL");
        qe.setBody(sql);
        qcs.setQuery(qe);
        return qcs;
    }

    /**
     * Full form: declared output columns (into {@code feature}) and parameters in
     * declaration order (into {@code ownedElement}).
     */
    public static QueryColumnSet create(String name, String sql, List<Column> columns, List<SQLParameter> parameters) {
        QueryColumnSet qcs = create(name, sql);
        if (columns != null) {
            qcs.getFeature().addAll(columns);
        }
        if (parameters != null) {
            parameters.forEach(p -> addParameter(qcs, p));
        }
        return qcs;
    }

    /** An IN-parameter: name, {@code PDK_IN}, and the given SQL type. */
    public static SQLParameter inParameter(String name, SQLSimpleType type) {
        SQLParameter p = RelationalFactory.eINSTANCE.createSQLParameter();
        p.setName(name);
        p.setKind(ParameterDirectionKind.PDK_IN);
        p.setType(type);
        return p;
    }

    /** Append a parameter; its position is the current parameter count. */
    public static void addParameter(QueryColumnSet qcs, SQLParameter parameter) {
        qcs.getOwnedElement().add(parameter);
    }

    /** Declared parameters in list order (the positional convention). */
    public static List<SQLParameter> parametersOf(QueryColumnSet qcs) {
        return Namespaces.ownedElements(qcs, SQLParameter.class);
    }

    public static boolean isParameterized(QueryColumnSet qcs) {
        return qcs != null && Namespaces.ownedElementStream(qcs, SQLParameter.class).findAny().isPresent();
    }

    /** The query expression body, or empty if the query is unset. */
    public static Optional<String> queryBody(QueryColumnSet qcs) {
        QueryExpression qe = qcs == null ? null : qcs.getQuery();
        return qe == null ? Optional.empty() : Optional.ofNullable(qe.getBody());
    }

    /**
     * Convention warnings (never errors): parameters without a type, duplicate
     * names, non-IN direction, and a mix of named and unnamed parameters.
     */
    public static List<String> checkConvention(QueryColumnSet qcs) {
        List<String> warnings = new ArrayList<>();
        List<SQLParameter> params = parametersOf(qcs);
        Set<String> names = new HashSet<>();
        boolean anyNamed = false;
        boolean anyUnnamed = false;
        for (int i = 0; i < params.size(); i++) {
            SQLParameter p = params.get(i);
            String label = p.getName() == null ? "#" + i : p.getName();
            if (p.getName() == null || p.getName().isBlank()) {
                anyUnnamed = true;
            } else {
                anyNamed = true;
                if (!names.add(p.getName().toLowerCase())) {
                    warnings.add("duplicate parameter name: " + p.getName());
                }
            }
            if (p.getType() == null) {
                warnings.add("parameter " + label + " has no type");
            }
            if (p.getKind() != ParameterDirectionKind.PDK_IN) {
                warnings.add("parameter " + label + " has kind " + p.getKind() + " (expected PDK_IN)");
            }
        }
        if (anyNamed && anyUnnamed) {
            warnings.add("mix of named and unnamed parameters");
        }
        return warnings;
    }
}
