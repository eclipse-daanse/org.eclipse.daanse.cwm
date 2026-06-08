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
package org.eclipse.daanse.cwm.resource.relational.sql.resolve.api;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;

/**
 * Outcome of resolving a query against a CWM schema scope.
 *
 * @param ok              {@code true} iff the query parsed and every referenced
 *                        table / column was found.
 * @param failure         {@link Failure} category when {@link #ok} is false;
 *                        {@code Optional.empty()} on success.
 * @param message         Human-readable description (never {@code null}).
 * @param columnsUsed     Original CWM {@link Column} EObjects the query touches
 *                        anywhere (SELECT / WHERE / JOIN / …). Identity-keyed
 *                        set.
 * @param columnUsage     Per-column classification by clause.
 * @param clauseColumns   Per-clause ordered column list (best-effort textual
 *                        order from JSQLResolver's AST walk), identity-deduped.
 *                        Suitable for shaping composite indexes by query column
 *                        order. Empty on failure.
 * @param tablesUsed      Tables, views and query-column-sets the query reads
 *                        from.
 * @param producedColumns The columns the query's SELECT projection emits, in
 *                        projection order. Each entry's source links back to
 *                        the underlying CWM Column when one exists (or is
 *                        {@code null} for computed expressions).
 * @param functionNames   Function names invoked in the query (lower-case).
 * @param rewrittenSql    Schema-qualified rewrite of the input SQL, or
 *                        {@code null} if the parse step failed.
 */
public record Resolution(boolean ok, Optional<Failure> failure, String message, Set<Column> columnsUsed,
        Map<Column, EnumSet<ColumnUsage>> columnUsage, Map<ColumnUsage, List<Column>> clauseColumns,
        Set<NamedColumnSet> tablesUsed, List<ProducedColumn> producedColumns, Set<String> functionNames,
        String rewrittenSql) {
}
