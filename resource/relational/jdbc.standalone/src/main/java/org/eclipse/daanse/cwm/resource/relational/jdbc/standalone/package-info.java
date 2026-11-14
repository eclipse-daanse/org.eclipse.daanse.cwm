/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
/**
 * A live database and a CWM relational model, both ways, without OSGi: {@link CwmJdbcImport} reads
 * a database into a catalog, running the structure loader with the privilege loader as its part on
 * one metadata snapshot; {@link CwmJdbcExport} writes a model into a database, cut to a selection,
 * as the DDL of its dialect, with {@code dropFirst} idempotently and in one transaction where the
 * database runs DDL in transactions. {@link Connections} opens a connection by URL for both. The
 * classes wire by hand what the service registry wires inside OSGi, where the {@code CwmLoader}
 * service, with the {@code PrivilegeLoader} service as its part, and the DDL generator factory are
 * the entry. The Maven goals {@code cwm-jdbc:import} and {@code cwm-jdbc:export} run the import and
 * the export.
 */
package org.eclipse.daanse.cwm.resource.relational.jdbc.standalone;
