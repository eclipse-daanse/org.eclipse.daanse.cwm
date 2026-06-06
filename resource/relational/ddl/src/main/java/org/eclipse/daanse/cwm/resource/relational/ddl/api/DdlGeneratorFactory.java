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

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;

/** Creates {@link DdlGenerator}s for a given dialect. Registered as an OSGi service. */
public interface DdlGeneratorFactory {

    /** Generator for {@code dialect} with default {@link DdlSettings}. */
    DdlGenerator create(Dialect dialect);

    /** Generator for {@code dialect} with the given {@code settings}. */
    DdlGenerator create(Dialect dialect, DdlSettings settings);
}
