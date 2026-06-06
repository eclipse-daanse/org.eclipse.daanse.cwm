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

import org.eclipse.daanse.cwm.testkit.api.dbcheck.DatabaseCheckSuite;

/**
 * Supplies the database-shape checks (schemas, tables, columns, queries) to run
 * against the loaded {@code DataSource}.
 */
public interface DatabaseCheckSuiteSupplier {

    DatabaseCheckSuite get();
}
