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
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import java.util.EnumSet;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;

/**
 * A routine of the same signature, name and kinds and types of its parameters, on both sides, that
 * differs in what else the differ compares. A routine whose signature differs is another routine: one
 * side's is dropped, the other's added, as a database keeps each overload apart.
 *
 * @param oldProcedure the routine of the old schema
 * @param newProcedure the routine of the new schema
 * @param aspects      what differs; never empty
 */
public record ProcedureChange(Procedure oldProcedure, Procedure newProcedure, Set<Aspect> aspects) {

    public ProcedureChange {
        aspects = aspects == null || aspects.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(aspects));
    }

    /** What the differ compares of a routine beyond its signature. */
    public enum Aspect {
        /** Function or procedure. */
        KIND,
        /** The names of the parameters. */
        PARAMETER_NAMES,
        /** The type a function returns, where both state one. */
        RETURN_TYPE,
        /** The text of the body, subject to {@link Tolerance#IGNORE_EXPRESSION_TEXT}. */
        BODY
    }
}
