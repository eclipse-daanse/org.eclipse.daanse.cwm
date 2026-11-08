/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.transparency.util;

import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Alignment;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.PeriodStarts;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.LegalSource;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.RetentionObligation;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.LegalSources;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.Obligations;
import org.eclipse.daanse.cwm.model.daanse.governance.transparency.TransparencyAssessment;
import org.eclipse.daanse.cwm.model.daanse.governance.transparency.TransparencyClassRating;

/**
 * How long published information is kept: a retention obligation of the model {@code obligations}
 * on the data class of a rating, owned by its assessment and resting on a legal source.
 */
public final class TransparencyLifecycle {

    private TransparencyLifecycle() {
    }

    /** The source of the retention of published information: § 7 (8) ThuerTG, in Thuringia. */
    public static LegalSource thuertg(Namespace owner) {
        return LegalSources.provision(LegalSources.create(owner, "thuertg-7-8", "ThuerTG", LegalSources.STATUTE),
                "§ 7 (8)", "DE-TH");
    }

    /**
     * The rows of the rated data class are kept at least {@code years} from the end of the year of
     * their value in {@code zone}; {@code source} names the obligation.
     */
    public static RetentionObligation retention(TransparencyAssessment assessment, TransparencyClassRating rating,
            int years, String zone, LegalSource source) {
        if (years < 1) {
            throw new IllegalArgumentException("a retention lasts at least a year: " + years);
        }
        RetentionObligation obligation = Obligations.onRows(Obligations.retain(assessment, rating.getName() + "-kept",
                "P" + years + "Y", PeriodStarts.aligned(PeriodStarts.valueTime(), Alignment.END_OF_YEAR,
                        Objects.requireNonNull(zone, "zone")),
                rating.getDataClass()));
        LegalSources.cite(Objects.requireNonNull(source, "source"), obligation);
        return obligation;
    }
}
