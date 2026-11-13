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

import java.util.List;
import java.util.stream.Collectors;

/**
 * Thrown where the settings are strict ({@link DdlSettings#strict()}) and the model holds what the
 * dialect cannot write as the model has it: it names every such case of the run, not only the first,
 * each with its element, what the dialect cannot write and the dialect. Nothing of the run is
 * written.
 */
public final class UnwritableException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final transient List<Unwritable> cases;

    /** @param cases at least one */
    public UnwritableException(List<Unwritable> cases) {
        super(message(cases));
        this.cases = List.copyOf(cases);
    }

    /** Every case, in the order they were found. */
    public List<Unwritable> cases() {
        return cases;
    }

    private static String message(List<Unwritable> cases) {
        if (cases == null || cases.isEmpty()) {
            throw new IllegalArgumentException("an UnwritableException names at least one case");
        }
        return "strict: the dialect cannot write what the model holds, in " + cases.size()
                + (cases.size() == 1 ? " case" : " cases") + cases.stream()
                        .map(c -> c.element() + ": " + c.dialect() + " cannot write " + c.what()
                                + " (not strict: " + c.instead() + ")")
                        .collect(Collectors.joining("\n  ", ":\n  ", ""));
    }
}
