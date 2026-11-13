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
package org.eclipse.daanse.cwm.resource.relational.load.privilege.api;

import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.CatalogPartLoader;

/**
 * The privileges as a part of the catalog a {@code CwmLoader} reads: the
 * common core of database grants, what all databases share. Registered as an
 * OSGi service, also as {@link CatalogPartLoader}.
 *
 * <p>
 * The principals become {@code businessinformation::ResponsibleParty}s owned by
 * the catalog, marked by the {@code core::Stereotype}s {@code databaseRole} and
 * {@code databaseUser}, which the catalog owns as well. A party owns its
 * privileges ({@code ownedElement}): table and column privileges as
 * {@code TablePrivilege}, EXECUTE on a routine as {@code ProcedurePrivilege},
 * memberships as {@code RolePrivilege}, each on the elements of the catalog.
 * Rows outside the common core (dialect actions, DENY, schema privileges,
 * self-memberships) and rows whose target the catalog lacks are skipped.
 * </p>
 *
 * <p>
 * The selection decides as for the rest of the catalog: {@code !is:privilege}
 * leaves the privileges out and keeps the parties, {@code !kind:ResponsibleParty}
 * reads none of it. A party is reached by an include rule of its own
 * ({@code is:role}, {@code is:user}, its name), not by one on a schema: with
 * {@code schema:sales} alone the catalog holds no party.
 * </p>
 */
public interface PrivilegeLoader extends CatalogPartLoader {

    /** The human-readable {@code responsibility} of roles (and the default for unknown grantees). */
    String RESPONSIBILITY_DATABASE_ROLE = "database role";

    /** The human-readable {@code responsibility} of login-capable users. */
    String RESPONSIBILITY_DATABASE_USER = "database user";

    /** The human-readable {@code responsibility} of the PUBLIC pseudo grantee. */
    String RESPONSIBILITY_PUBLIC = "public";
}
