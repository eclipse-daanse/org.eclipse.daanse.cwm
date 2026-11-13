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
package org.eclipse.daanse.cwm.resource.relational.load.jdbc.api;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.UnmatchedRulesException;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;

/**
 * Builds a CWM relational {@link Catalog} from a JDBC {@link MetaInfo} snapshot,
 * the inverse of DDL generation; the {@link CatalogPartLoader}s add what lies
 * beyond the structure. Registered as an OSGi service.
 *
 * <p>
 * The catalog holds what {@link JdbcToCwmConfig#selection()} selects, as
 * {@code Selections.copy} would cut the catalog of the whole database: the
 * selected elements, their owners as containers, what they need (the SQL types
 * of their columns), with the consequences of the relational model and of the
 * parts for what is left out. What the selection excludes is not read at all:
 * a schema or table it excludes, or a whole kind ({@link Selection#excludesKind}).
 * </p>
 *
 * <p>
 * A table of a database without schemas is put into a schema without name. A
 * materialized view is a {@code View}, read-only and tagged
 * {@code RelationalRules.MATERIALIZED_TAG}; a system or temporary table says so
 * in {@code Table.isSystem} and {@code Table.isTemporary}.
 * </p>
 */
public interface CwmLoader {

    /**
     * Loads {@code info} into a new {@link Catalog} as {@code config} says
     * ({@code null}: {@link JdbcToCwmConfig#defaults()}).
     *
     * @throws UnmatchedRulesException when an include rule of the selection matches
     *                                 nothing in the database, usually a mistyped name
     */
    Catalog load(MetaInfo info, JdbcToCwmConfig config);
}
