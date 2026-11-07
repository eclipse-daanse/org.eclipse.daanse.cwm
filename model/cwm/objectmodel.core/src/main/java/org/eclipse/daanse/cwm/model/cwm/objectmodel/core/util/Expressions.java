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
package org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util;

import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.BooleanExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Expression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ProcedureExpression;

/**
 * Value objects of {@code core::Expression} and its subclasses. They are
 * contained by the single-valued reference that uses them (a transformation's
 * {@code function}, an event's {@code condition}), so they take no parent.
 */
public final class Expressions {

    private Expressions() {
    }

    /** An Expression with {@code language} and {@code body}. */
    public static Expression expression(String language, String body) {
        Expression e = CoreFactory.eINSTANCE.createExpression();
        fill(e, language, body);
        return e;
    }

    /** A ProcedureExpression with {@code language} and {@code body}. */
    public static ProcedureExpression procedure(String language, String body) {
        ProcedureExpression e = CoreFactory.eINSTANCE.createProcedureExpression();
        fill(e, language, body);
        return e;
    }

    /** A BooleanExpression with {@code language} and {@code body}. */
    public static BooleanExpression bool(String language, String body) {
        BooleanExpression e = CoreFactory.eINSTANCE.createBooleanExpression();
        fill(e, language, body);
        return e;
    }

    private static void fill(Expression e, String language, String body) {
        Objects.requireNonNull(body, "body");
        e.setLanguage(language);
        e.setBody(body);
    }
}
