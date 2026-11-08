/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.deidentification.util;

import java.util.Objects;

import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Stages;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Anonymize;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationPolicy;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DestroyLinkage;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.Pseudonymize;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.PseudonymizationDomain;

/**
 * Adds the de-identifying stages to a retention schedule, in the order they follow: each named, due
 * after a {@code Period} literal from the start ({@code null} where another rule gives it).
 */
public final class DeidentificationStages {

    private static final DeidentificationFactory F = DeidentificationFactory.eINSTANCE;

    private DeidentificationStages() {
    }

    /** The data is pseudonymized by {@code policy}; it stays personal, the schedule goes on. */
    public static Pseudonymize pseudonymize(RetentionSchedule schedule, String name, String after,
            DeidentificationPolicy policy) {
        Pseudonymize stage = F.createPseudonymize();
        stage.setPolicy(Objects.requireNonNull(policy, "policy"));
        return Stages.add(schedule, name, after, stage);
    }

    /** The data is made anonymous by {@code policy}: a terminal stage. */
    public static Anonymize anonymize(RetentionSchedule schedule, String name, String after,
            DeidentificationPolicy policy) {
        Anonymize stage = F.createAnonymize();
        stage.setPolicy(Objects.requireNonNull(policy, "policy"));
        return Stages.add(schedule, name, after, stage);
    }

    /** The key or mapping of {@code domain} is destroyed: a terminal stage of the domain's schedule. */
    public static DestroyLinkage destroyLinkage(RetentionSchedule schedule, String name, String after,
            PseudonymizationDomain domain) {
        DestroyLinkage stage = F.createDestroyLinkage();
        stage.setDomain(Objects.requireNonNull(domain, "domain"));
        return Stages.add(schedule, name, after, stage);
    }
}
