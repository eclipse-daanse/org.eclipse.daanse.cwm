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

/**
 * How a column is compared in a WHERE / JOIN-ON predicate. The distinction
 * drives index-column ordering (equality columns can lead a composite b-tree
 * key, range columns only help at the end) and sargability (a
 * {@link #NON_SARGABLE} predicate cannot use a b-tree index on that column at
 * all). A column may carry several kinds when it appears in several predicates.
 */
public enum PredicateKind {

    /** {@code =}, {@code IN (…)}, {@code IS [NOT] NULL} — pins one key value. */
    EQUALITY,

    /**
     * {@code < > <= >=}, {@code BETWEEN}, {@code <>}-free prefix match
     * {@code LIKE 'x%'} — selects a contiguous key range; useful only after all
     * equality columns of a composite index.
     */
    RANGE,

    /**
     * The predicate cannot be answered from a plain b-tree index on the column:
     * leading-wildcard {@code LIKE '%…'}, the column wrapped in a function,
     * arithmetic or cast, or {@code <>}. Such columns must not drive an index
     * proposal.
     */
    NON_SARGABLE
}
