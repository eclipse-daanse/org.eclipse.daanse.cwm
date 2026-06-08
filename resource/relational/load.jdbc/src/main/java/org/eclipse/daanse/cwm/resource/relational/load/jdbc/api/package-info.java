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
/**
 * JDBC &rarr; CWM load API. A {@link CwmLoader} builds a CWM relational
 * {@code Catalog} from a JDBC metadata snapshot, scoped by {@link JdbcToCwmConfig}.
 */
@org.osgi.annotation.bundle.Export
@org.osgi.annotation.versioning.Version("0.0.1")
package org.eclipse.daanse.cwm.resource.relational.load.jdbc.api;
