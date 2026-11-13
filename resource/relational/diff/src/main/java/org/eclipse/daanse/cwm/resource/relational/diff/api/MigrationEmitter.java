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
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import java.util.List;

import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlGenerator;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.UnwritableException;
import org.eclipse.daanse.sql.dialect.api.Dialect;

/**
 * Renders an ordered {@link ChangeOp} plan as {@code ALTER}/{@code DROP}/
 * {@code CREATE} statements for one dialect. The emitter translates operation
 * by operation and does not reorder — ordering is the {@link ChangePlanner}'s
 * job. OSGi service.
 *
 * <p>
 * What the DDL generator writes for an element, the emitter writes the same
 * way, by the same code ({@link DdlGenerator#createElement},
 * {@link DdlGenerator#dropElement}): the settings hold for every statement
 * ({@code includeSchema}, {@code IF [NOT] EXISTS}, {@code CASCADE}), and the
 * dialect decides as there. A table the plan creates is written with the
 * constraints the dialect declares in {@code CREATE TABLE}, and the operation
 * that adds such a constraint to it writes nothing; adding one to, or dropping
 * one from, a table the plan does not create is a case the dialect cannot
 * write. What the create script leaves out or writes plainer is left out or
 * written plainer here too, and such cases are one rule for the whole plan
 * ({@link DdlSettings#strict()}).
 * </p>
 */
public interface MigrationEmitter {

    /** {@link #emit(List, Dialect, DdlSettings)} with {@link DdlSettings#defaults()}. */
    default List<String> emit(List<ChangeOp> ops, Dialect dialect) {
        return emit(ops, dialect, DdlSettings.defaults());
    }

    /**
     * The statements of {@code ops}, in their order, for {@code dialect}, written with
     * {@code settings} as the DDL generator writes them. What the dialect cannot write — what the
     * generator cannot, a constraint the dialect declares in {@code CREATE TABLE} only, to add to,
     * drop from or rename on a table the plan neither creates nor drops, a rename it has no statement
     * for — is left out, and the log names each case.
     *
     * @throws UnwritableException where {@code settings} are strict ({@link DdlSettings#strict()}) and
     *                             the plan holds such cases: with every one of them
     */
    List<String> emit(List<ChangeOp> ops, Dialect dialect, DdlSettings settings);
}
