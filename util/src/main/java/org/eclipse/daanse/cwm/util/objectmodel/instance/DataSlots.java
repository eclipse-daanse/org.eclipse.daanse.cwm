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
package org.eclipse.daanse.cwm.util.objectmodel.instance;

import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.instance.DataSlot;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;

public final class DataSlots {

    private DataSlots() {
    }

    /**
     * {@link Column} the slot is bound to, or empty if the feature is missing or
     * not a column.
     */
    public static Optional<Column> column(DataSlot slot) {
        if (slot == null || slot.getFeature() == null) {
            return Optional.empty();
        }
        if (slot.getFeature() instanceof Column c) {
            return Optional.of(c);
        }
        return Optional.empty();
    }

    /** String literal carried by the slot, null-safe. */
    public static String dataValue(DataSlot slot) {
        return slot == null ? null : slot.getDataValue();
    }
}
