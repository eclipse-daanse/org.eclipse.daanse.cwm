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
package org.eclipse.daanse.cwm.util.resource.relational;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.instance.DataSlot;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Row;

public final class Rows {

    private Rows() {
    }

    /** All {@link DataSlot}s attached to this row, in declaration order. */
    public static List<DataSlot> slots(Row row) {
        return slotStream(row).toList();
    }

    /** Stream of {@link DataSlot}s attached to this row. */
    public static Stream<DataSlot> slotStream(Row row) {
        if (row == null) {
            return Stream.empty();
        }
        return row.getSlot().stream().filter(DataSlot.class::isInstance).map(DataSlot.class::cast);
    }

    /**
     * Literal value of the slot bound to {@code column}, or empty if no such slot.
     */
    public static Optional<String> valueOf(Row row, Column column) {
        if (row == null || column == null) {
            return Optional.empty();
        }
        for (DataSlot ds : slots(row)) {
            if (ds.getFeature() == column) {
                return Optional.ofNullable(ds.getDataValue());
            }
        }
        return Optional.empty();
    }
}
