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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.DatatypesFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.QueryExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Parameter;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Namespaces;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.QueryColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLParameter;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;

/**
 * Helpers for stored queries modeled as {@link QueryColumnSet}s, including the
 * prepared-statement style: parameters placed into the query's
 * {@code ownedElement} list. A parameter is a {@code behavioral::Parameter} —
 * typed by any Classifier, e.g. a Java type of a TypeSystem — or its
 * specialization {@link SQLParameter}, typed by an SQL type; every reader here
 * sees both kinds.
 *
 * <p>
 * Convention: a QueryColumnSet is a Namespace (via {@code core::Class}), so its
 * parameters live in {@code getOwnedElement()}. CWM declares
 * {@code Namespace.ownedElement} as unordered; this project relies on the EMF
 * list order (which XMI serialization preserves) — the position of a parameter
 * is its index among the {@code Parameter} owned elements, matching the
 * placeholder order in the SQL body. Parameter name is
 * {@code ModelElement.name}, the type a Classifier (an {@code SQLSimpleType}
 * for SQLParameters), and the direction {@code PDK_IN}. Tools that legally reorder {@code ownedElement}
 * break the positional mapping; named placeholders ({@code :name}) are the
 * order-independent alternative.
 * </p>
 */
public final class QueryColumnSets {

    private QueryColumnSets() {
    }

    /**
     * A QueryColumnSet with a {@code QueryExpression(language="SQL")}, owned by
     * {@code parent} via {@code ownedElement}; result columns follow with
     * {@link Columns#create}, parameters with {@link SQLParameters#create} or
     * {@code behavioral.util.Parameters#create}. Creation rule: see
     * {@code objectmodel.core.util.Models}.
     */
    public static QueryColumnSet create(Namespace parent, String name, String sql) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sql, "sql");
        QueryColumnSet qcs = create(name, sql);
        parent.getOwnedElement().add(qcs);
        return qcs;
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
    public static QueryColumnSet create(String name, String sql, List<Column> columns,
            List<? extends Parameter> parameters) {
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
    public static void addParameter(QueryColumnSet qcs, Parameter parameter) {
        qcs.getOwnedElement().add(parameter);
    }

    /**
     * Declared parameters in list order (the positional convention) — plain
     * Parameters and SQLParameters alike.
     */
    public static List<Parameter> parametersOf(QueryColumnSet qcs) {
        return Namespaces.ownedElements(qcs, Parameter.class);
    }

    public static boolean isParameterized(QueryColumnSet qcs) {
        return qcs != null && Namespaces.ownedElementStream(qcs, Parameter.class).findAny().isPresent();
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
        List<Parameter> params = parametersOf(qcs);
        Set<String> names = new HashSet<>();
        boolean anyNamed = false;
        boolean anyUnnamed = false;
        for (int i = 0; i < params.size(); i++) {
            Parameter p = params.get(i);
            String label = p.getName() == null ? "#" + i : p.getName();
            if (p.getName() == null || p.getName().isBlank()) {
                anyUnnamed = true;
            } else {
                anyNamed = true;
                if (!names.add(p.getName().toLowerCase(Locale.ROOT))) {
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
