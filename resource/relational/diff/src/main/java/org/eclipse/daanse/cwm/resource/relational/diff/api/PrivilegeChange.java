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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import java.util.EnumSet;
import java.util.Set;

import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.Privilege;

/**
 * The same privilege, action and target, or membership, on both sides, granted differently.
 *
 * @param left    the privilege of the first model
 * @param right   the privilege of the second model
 * @param aspects what differs; never empty
 */
public record PrivilegeChange(Privilege left, Privilege right, Set<Aspect> aspects) {

    public PrivilegeChange {
        aspects = aspects == null || aspects.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(aspects));
    }

    /** What the comparison looks at of a privilege beyond what it grants on what. */
    public enum Aspect {
        /** {@code WITH GRANT OPTION}, for a membership {@code WITH ADMIN OPTION}; unknown counts as without. */
        GRANTABLE,
        /** Who granted it, subject to {@link Tolerance#IGNORE_GRANTOR}. */
        GRANTOR
    }
}
