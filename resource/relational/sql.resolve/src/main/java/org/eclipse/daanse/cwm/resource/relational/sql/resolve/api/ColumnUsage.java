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
 * Where in a query a column is referenced. A single column can have multiple
 * usages — e.g. a join key referenced in {@code SELECT}, {@code WHERE} and
 * {@code JOIN} simultaneously.
 */
public enum ColumnUsage {
    SELECT, WHERE, JOIN, GROUP_BY, HAVING, ORDER_BY
}
