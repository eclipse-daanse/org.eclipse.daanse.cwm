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
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.cwm.testkit.api;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;

/**
 * Supplies the CWM {@code Schema} to create before any data is loaded. Its
 * tables, columns, keys, indexes and views are materialized via the dialect's
 * DDL generator.
 */
public interface DatabaseSupplier {

    /**
     * The CWM Schema to create. A blank name or {@code "default"} creates the
     * tables in the default schema, unqualified.
     */
    Schema schema();
}
