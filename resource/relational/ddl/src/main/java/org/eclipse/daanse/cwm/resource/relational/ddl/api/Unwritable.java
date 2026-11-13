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
package org.eclipse.daanse.cwm.resource.relational.ddl.api;

import java.util.Locale;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.QualifiedNames;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ProcedureType;
import org.eclipse.daanse.sql.dialect.api.Dialect;

/**
 * Something the model holds that a dialect cannot write as the model has it, and what is written in
 * its place where the settings are not strict ({@link DdlSettings#strict()}).
 *
 * @param dialect the name of the dialect
 * @param element the element, by its kind and qualified name: {@code view sales.open_orders}
 * @param what    what of it the dialect cannot write: {@code MATERIALIZED}, {@code BEFORE}, a
 *                comment on a view
 * @param instead what is written in its place where the settings are not strict:
 *                {@code written as a plain view}, {@code left out}
 */
public record Unwritable(String dialect, String element, String what, String instead) {

    public Unwritable {
        Objects.requireNonNull(dialect, "dialect");
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(what, "what");
        Objects.requireNonNull(instead, "instead");
    }

    /** The case of {@code element}, named by {@link #describe(ModelElement)}. */
    public static Unwritable of(Dialect dialect, ModelElement element, String what, String instead) {
        return new Unwritable(dialect.name(), describe(element), what, instead);
    }

    /**
     * An element by its kind and its qualified name: {@code index sales.orders.ix_open},
     * {@code foreign key sales.orders.fk_customer}. The kind is the name of its class in words, without
     * the prefix {@code SQL}; a procedure that is a function is a {@code function}.
     */
    public static String describe(ModelElement element) {
        if (element instanceof Procedure routine && routine.getType() == ProcedureType.FUNCTION) {
            return "function " + QualifiedNames.of(element);
        }
        String type = element.eClass().getName();
        if (type.startsWith("SQL") && type.length() > 3) {
            type = type.substring(3);
        }
        String kind = type.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        return kind + " " + QualifiedNames.of(element);
    }

    /** {@code view sales.open_orders: DuckDB cannot write MATERIALIZED; written as a plain view}. */
    @Override
    public String toString() {
        return element + ": " + dialect + " cannot write " + what + "; " + instead;
    }
}
