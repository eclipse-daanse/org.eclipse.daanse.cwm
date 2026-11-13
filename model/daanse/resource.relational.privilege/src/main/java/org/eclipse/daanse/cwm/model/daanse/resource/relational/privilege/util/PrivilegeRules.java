/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationPackage;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.BusinessInformationRules;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ConsequenceRule;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ConsequenceRule.Consequence;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ElementRule;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ElementRules;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.RuleVocabulary;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selections;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalRules;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.PrivilegePackage;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.ProcedurePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;

/**
 * The {@link ElementRule}s and {@link ConsequenceRule}s of privileges, roles
 * and users, for a {@link org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection}
 * of a catalog that holds them.
 *
 * <table>
 * <caption>Privilege rules</caption>
 * <tr><th>Text</th><th>Code</th><th>Matches</th></tr>
 * <tr><td>{@code is:privilege}</td><td>{@link #PRIVILEGES}</td>
 * <td>table, procedure and role privileges (memberships)</td></tr>
 * <tr><td>{@code is:role}</td><td>{@link #ROLES}</td>
 * <td>parties branded {@value PrivilegeStereotypes#DATABASE_ROLE}</td></tr>
 * <tr><td>{@code is:user}</td><td>{@link #USERS}</td>
 * <td>parties branded {@value PrivilegeStereotypes#DATABASE_USER}</td></tr>
 * </table>
 * <p>
 * A grantee owns its privileges, so a role or user brings its privileges along
 * and {@code !is:user} leaves the users out with theirs. Roles and users are
 * told apart by their stereotype ({@link PrivilegeStereotypes}), not by the
 * text of {@code responsibility}; a copy keeps the stereotype of every party
 * it keeps.
 * </p>
 *
 * <p>
 * {@link #CONSEQUENCES} puts these rules in front of those of
 * {@link RelationalRules} and {@link BusinessInformationRules}:
 * </p>
 * <ul>
 * <li>A table privilege that loses its table is removed, and so is one that
 * loses one of the columns it is restricted to. A grant on fewer columns than
 * written grants something else than intended; and a grant that loses all its
 * columns would read as a grant on the whole table, wider than intended.
 * Neither may come about in silence.</li>
 * <li>A procedure privilege that loses its procedure is removed.</li>
 * <li>A membership ({@code RolePrivilege}) that loses its role is removed.</li>
 * </ul>
 * <p>
 * A privilege that loses its grantor stays without one, as when a database
 * does not report who granted it.
 * </p>
 *
 * @see Selections#copy
 */
public final class PrivilegeRules {

    /** {@code is:privilege}: table, procedure and role privileges. */
    public static final ElementRule PRIVILEGES = ElementRules.kind(PrivilegePackage.Literals.PRIVILEGE)
            .describedAs("is:privilege");

    /** {@code is:role}: parties branded {@value PrivilegeStereotypes#DATABASE_ROLE}. */
    public static final ElementRule ROLES = branded(PrivilegeStereotypes.DATABASE_ROLE).describedAs("is:role");

    /** {@code is:user}: parties branded {@value PrivilegeStereotypes#DATABASE_USER}. */
    public static final ElementRule USERS = branded(PrivilegeStereotypes.DATABASE_USER).describedAs("is:user");

    /** The words of {@link RelationalRules} and {@link BusinessInformationRules}, and those of this class. */
    public static final RuleVocabulary VOCABULARY = RelationalRules.VOCABULARY
            .with(BusinessInformationRules.VOCABULARY)
            .withRule("privilege", PRIVILEGES)
            .withRule("role", ROLES)
            .withRule("user", USERS);

    private static final ConsequenceRule TABLE_PRIVILEGES = ConsequenceRule.of(
            "table privilege without its table or a column",
            loss -> removeIf(loss.referrer() instanceof TablePrivilege
                    && (loss.reference() == PrivilegePackage.Literals.TABLE_PRIVILEGE__TABLE
                            || loss.reference() == PrivilegePackage.Literals.TABLE_PRIVILEGE__COLUMN)));

    private static final ConsequenceRule PROCEDURE_PRIVILEGES = ConsequenceRule.of(
            "procedure privilege without its procedure",
            loss -> removeIf(loss.referrer() instanceof ProcedurePrivilege
                    && loss.reference() == PrivilegePackage.Literals.PROCEDURE_PRIVILEGE__PROCEDURE));

    private static final ConsequenceRule MEMBERSHIPS = ConsequenceRule.of("membership without its role",
            loss -> removeIf(loss.referrer() instanceof RolePrivilege
                    && loss.reference() == PrivilegePackage.Literals.ROLE_PRIVILEGE__ROLE));

    /**
     * The consequences listed above, then those of {@link RelationalRules} and
     * {@link BusinessInformationRules}, then {@link ConsequenceRule#CORE}.
     */
    public static final List<ConsequenceRule> CONSEQUENCES = ConsequenceRule.combine(
            List.of(TABLE_PRIVILEGES, PROCEDURE_PRIVILEGES, MEMBERSHIPS), RelationalRules.CONSEQUENCES,
            BusinessInformationRules.CONSEQUENCES);

    private PrivilegeRules() {
    }

    private static ElementRule branded(String stereotype) {
        return ElementRules.kind(BusinessinformationPackage.Literals.RESPONSIBLE_PARTY)
                .and(ElementRules.stereotyped(stereotype));
    }

    private static Optional<Consequence> removeIf(boolean condition) {
        return condition ? Optional.of(Consequence.REMOVE) : Optional.empty();
    }
}
