/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util;

import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.CountLimit;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.LifecycleFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.SizeLimit;

/** Adds limits to a retention schedule, the body of the schedule kept in step. */
public final class Limits {

    private Limits() {
    }

    /** Keeps the newest {@code keep}; with {@code keys}, the newest {@code keep} per value of the keys. */
    public static CountLimit count(RetentionSchedule schedule, long keep, StructuralFeature... keys) {
        CountLimit limit = LifecycleFactory.eINSTANCE.createCountLimit();
        limit.setKeep(keep);
        List.of(keys).forEach(k -> limit.getKey().add(Objects.requireNonNull(k, "key")));
        schedule.getLimit().add(limit);
        RetentionSchedules.refreshed(schedule);
        return limit;
    }

    /** Keeps the newest data up to {@code bytes}. */
    public static SizeLimit size(RetentionSchedule schedule, long bytes) {
        SizeLimit limit = LifecycleFactory.eINSTANCE.createSizeLimit();
        limit.setBytes(bytes);
        schedule.getLimit().add(limit);
        RetentionSchedules.refreshed(schedule);
        return limit;
    }
}
