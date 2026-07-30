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
package org.eclipse.daanse.cwm.resource.relational.ddl.api;

import org.eclipse.daanse.sql.dialect.api.Dialect;

/** Creates {@link PrivilegeSqlGenerator}s for a dialect. Registered as an OSGi service. */
public interface PrivilegeSqlGeneratorFactory {

    PrivilegeSqlGenerator create(Dialect dialect);
}
