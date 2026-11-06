/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.units.util;

import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.daanse.units.SystemOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.eclipse.daanse.cwm.model.daanse.units.UnitsFactory;

/** Creates systems of units. */
public final class SystemsOfUnits {

    private SystemsOfUnits() {
    }

    /** A system of units owned by {@code owner}. */
    public static SystemOfUnits create(Namespace owner, String name) {
        Objects.requireNonNull(owner, "owner");
        SystemOfUnits s = UnitsFactory.eINSTANCE.createSystemOfUnits();
        s.setName(Objects.requireNonNull(name, "name"));
        owner.getOwnedElement().add(s);
        return s;
    }

    /** Sets the short name of {@code system}, e.g. SI. */
    public static SystemOfUnits abbreviation(SystemOfUnits system, String abbreviation) {
        system.setAbbreviation(Objects.requireNonNull(abbreviation, "abbreviation"));
        return system;
    }

    /** Accepts units of other systems in {@code system}. */
    public static SystemOfUnits allow(SystemOfUnits system, Unit... units) {
        for (Unit unit : units) {
            system.getAllowedUnit().add(Objects.requireNonNull(unit, "unit"));
        }
        return system;
    }
}
