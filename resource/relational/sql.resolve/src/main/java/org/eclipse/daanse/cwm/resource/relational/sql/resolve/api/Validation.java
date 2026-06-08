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

import java.util.List;

/**
 * Compares the columns declared on a {@code QueryColumnSet} (or {@code View})
 * against the columns the query actually produces. Used to spot drift between
 * metadata and SQL body.
 *
 * @param ok              {@code true} iff declared and produced columns match
 *                        by name and order.
 * @param missingDeclared Names produced by the query that are not declared on
 *                        the column-set features.
 * @param missingProduced Names declared on the column-set features that the
 *                        query does not produce.
 * @param outOfOrder      Names declared in the column-set whose order differs
 *                        from the query's projection order.
 */
public record Validation(boolean ok, List<String> missingDeclared, List<String> missingProduced,
        List<String> outOfOrder) {
}
