/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.gdpr.util;

import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.PeriodStart;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Periods;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.Obligations;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataErasure;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.DataProcessing;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.ProcessingFactory;

/**
 * Creates the erasures of a processing: deletion obligations of the model {@code obligations},
 * contained in their processing, that govern the data classes and fields to erase.
 */
public final class DataErasures {

    private DataErasures() {
    }

    /**
     * The data of the given classes or fields is gone within {@code maximum} (a {@code Period}
     * literal) from {@code start}; the erasure is contained in {@code processing}.
     */
    public static DataErasure create(DataProcessing processing, String name, String maximum, PeriodStart start,
            ModelElement... elements) {
        if (elements.length == 0) {
            throw new IllegalArgumentException("an erasure governs at least one element");
        }
        Periods.parse(maximum);
        DataErasure erasure = ProcessingFactory.eINSTANCE.createDataErasure();
        erasure.setName(Objects.requireNonNull(name, "name"));
        erasure.setMaximum(maximum);
        erasure.setStart(Objects.requireNonNull(start, "start"));
        erasure.getConstrainedElement().addAll(List.of(elements));
        processing.getDataErasures().add(erasure);
        return Obligations.refreshed(erasure);
    }

    /** The processing that promises the erasure. */
    public static DataProcessing processing(DataErasure erasure) {
        return erasure.eContainer() instanceof DataProcessing p ? p : null;
    }
}
