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
 * CWM &rarr; SQL DDL generation API. A {@link DdlGenerator} (obtained from a
 * {@link DdlGeneratorFactory}) emits ordered {@code CREATE}/{@code DROP}
 * statements for a CWM relational {@code Schema} via the jdbc.db dialect,
 * configured by {@link DdlSettings} and scoped per {@link Feature}.
 * {@link CwmSchemaMapper} is the pure element-to-record bridge it builds on.
 */
@org.osgi.annotation.bundle.Export
@org.osgi.annotation.versioning.Version("0.0.1")
package org.eclipse.daanse.cwm.resource.relational.ddl.api;
