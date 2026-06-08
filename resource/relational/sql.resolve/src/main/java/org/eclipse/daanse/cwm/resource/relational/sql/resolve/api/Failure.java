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
 * Why a query failed to resolve. Mapped from JSQLTranspiler's typed exceptions
 * plus a couple of pre-parse conditions.
 */
public enum Failure {
    /** Empty or whitespace-only SQL body. */
    EMPTY,
    /**
     * {@code QueryExpression.getLanguage()} is set to something other than
     * {@code "SQL"} (case-insensitive) or {@code null}.
     */
    NON_SQL_LANGUAGE,
    /** JSQLParser couldn't parse the SQL body. */
    PARSE, UNKNOWN_CATALOG, UNKNOWN_SCHEMA, UNKNOWN_TABLE, UNKNOWN_COLUMN, AMBIGUOUS,
    /** Catch-all for any other resolver/runtime error. */
    OTHER
}
