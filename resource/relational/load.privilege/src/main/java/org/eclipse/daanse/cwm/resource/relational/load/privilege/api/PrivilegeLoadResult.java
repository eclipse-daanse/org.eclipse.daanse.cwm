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

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;

/**
 * Result of a {@link PrivilegeLoader} run: the interned parties (grantees,
 * grantors, principals without privileges) and the {@code core::Stereotype}
 * markers ({@code databaseRole}/{@code databaseUser}) that classify them.
 *
 * <p>The stereotypes must be returned explicitly because the party side of the
 * marker is a hidden association end ({@code Stereotype.extendedElement},
 * {@code oppositeRoleName = "stereotype"}) — parties hold no reference to their
 * stereotype. Everything is unattached: callers place parties <b>and</b>
 * stereotypes wherever they belong — into an own {@code core::Package} or into
 * the catalog — always via {@code ownedElement}.</p>
 */
public record PrivilegeLoadResult(
        List<ResponsibleParty> parties,
        List<Stereotype> stereotypes) {
}
