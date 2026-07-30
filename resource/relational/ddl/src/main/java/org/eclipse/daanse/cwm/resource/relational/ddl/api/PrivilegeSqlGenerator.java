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

import java.util.Collection;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.Privilege;

/**
 * Serialises {@code privilege}-model instances to dialect-specific SQL. The
 * model stays a <em>static description</em>: the same privilege list yields
 * both {@link #grantStatements(List)} and {@link #revokeStatements(List)}.
 * The grantee is the owning {@code ResponsibleParty} ({@code namespace}); the
 * {@code grantor} is <b>never</b> rendered (implied by the executing session);
 * {@code grantable} maps WITH GRANT/ADMIN OPTION. Role creation is a party
 * operation — callers select creatable roles via
 * {@code PrivilegeStereotypes.databaseRoles(...)}. Obtain instances from a
 * {@link PrivilegeSqlGeneratorFactory}; they are reusable and thread-safe.
 */
public interface PrivilegeSqlGenerator {

    /** Ordered {@code GRANT}s for {@code privileges} (deterministically sorted). */
    List<String> grantStatements(List<? extends Privilege> privileges);

    /** Reverse of {@link #grantStatements(List)} — ordered {@code REVOKE}s. */
    List<String> revokeStatements(List<? extends Privilege> privileges);

    /** The {@code GRANT} for a single privilege. */
    String grantStatement(Privilege privilege);

    /** The {@code REVOKE} for a single privilege. */
    String revokeStatement(Privilege privilege);

    /** {@code CREATE ROLE} for each given party, sorted by name. */
    List<String> createRoleStatements(Collection<? extends ResponsibleParty> roles);

    /** Reverse of {@link #createRoleStatements(Collection)} — {@code DROP ROLE}s. */
    List<String> dropRoleStatements(Collection<? extends ResponsibleParty> roles);
}
