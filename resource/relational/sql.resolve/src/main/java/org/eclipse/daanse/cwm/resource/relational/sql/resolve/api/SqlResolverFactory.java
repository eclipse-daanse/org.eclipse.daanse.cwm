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

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;

/**
 * Creates {@link SqlResolver}s scoped to a set of CWM {@link Schema}s.
 * Registered as an OSGi service.
 */
public interface SqlResolverFactory {

    /**
     * Resolver scoped to {@code schemas}. Catalog and current-schema are inferred
     * from the first schema (and its parent catalog, if any).
     */
    SqlResolver create(List<Schema> schemas);

    /**
     * Resolver scoped to {@code schemas}, with explicit current-catalog and
     * current-schema names used to resolve unqualified table references.
     */
    SqlResolver create(List<Schema> schemas, String currentCatalog, String currentSchema);
}
