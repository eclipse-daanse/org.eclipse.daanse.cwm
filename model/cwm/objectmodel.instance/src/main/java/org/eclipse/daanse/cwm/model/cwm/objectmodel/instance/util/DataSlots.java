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
package org.eclipse.daanse.cwm.model.cwm.objectmodel.instance.util;

import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.instance.DataSlot;

public final class DataSlots {

    private DataSlots() {
    }

    /** String literal carried by the slot, null-safe. */
    public static Optional<String> dataValue(DataSlot slot) {
        return slot == null ? Optional.empty() : Optional.ofNullable(slot.getDataValue());
    }

    /**
     * Feature the slot is bound to. A caller in a specific resource narrows it —
     * relational binds its slots to a {@code Column}.
     */
    public static Optional<StructuralFeature> feature(DataSlot slot) {
        return slot == null ? Optional.empty() : Optional.ofNullable(slot.getFeature());
    }

    /** Whether the slot is bound to {@code feature}. */
    public static boolean boundTo(DataSlot slot, StructuralFeature feature) {
        return slot != null && feature != null && slot.getFeature() == feature;
    }
}
