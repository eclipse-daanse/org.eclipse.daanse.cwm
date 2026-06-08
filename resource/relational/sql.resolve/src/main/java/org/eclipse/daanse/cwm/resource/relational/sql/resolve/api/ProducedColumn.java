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

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;

/**
 * One column produced by the query's SELECT projection.
 *
 * @param name   The column label as it appears in the result set (an alias when
 *               one is given, otherwise the underlying column name or the
 *               literal text of the projection expression).
 * @param source The original CWM {@link Column} the projection points at, or
 *               {@code null} when the projection is a computed expression /
 *               aggregate / literal that has no single backing column.
 */
public record ProducedColumn(String name, Column source) {
}
