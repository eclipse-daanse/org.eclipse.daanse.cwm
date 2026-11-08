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

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationPolicy;
import org.eclipse.daanse.cwm.model.daanse.governance.transparency.PublicationMode;
import org.eclipse.daanse.cwm.model.daanse.governance.transparency.TransparencyAssessment;
import org.eclipse.daanse.cwm.model.daanse.governance.transparency.TransparencyClassRating;
import org.eclipse.daanse.cwm.model.daanse.governance.transparency.TransparencyFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.transparency.TransparencyFieldRating;

/**
 * Creates transparency assessments and the ratings of their data classes and fields: how the data
 * is published, and the de-identification policy it is published under.
 */
public final class TransparencyRatings {

    private static final TransparencyFactory F = TransparencyFactory.eINSTANCE;

    private TransparencyRatings() {
    }

    /** An assessment in the {@code ownedElement} of {@code owner}, valid from {@code validFrom}. */
    public static TransparencyAssessment assess(Namespace owner, String name, String validFrom,
            ResponsibleParty responsible) {
        TransparencyAssessment assessment = F.createTransparencyAssessment();
        assessment.setName(Objects.requireNonNull(name, "name"));
        assessment.setValidFrom(Objects.requireNonNull(validFrom, "validFrom"));
        assessment.setContactResponsible(Objects.requireNonNull(responsible, "responsible"));
        owner.getOwnedElement().add(assessment);
        return assessment;
    }

    /** The rating of a data class: how it is published. */
    public static TransparencyClassRating rate(TransparencyAssessment assessment, String name, Classifier dataClass,
            PublicationMode mode) {
        TransparencyClassRating rating = F.createTransparencyClassRating();
        rating.setName(Objects.requireNonNull(name, "name"));
        rating.setDataClass(Objects.requireNonNull(dataClass, "dataClass"));
        rating.setPublicationMode(Objects.requireNonNull(mode, "mode"));
        assessment.getDataClasses().add(rating);
        return rating;
    }

    /** The rating of a field of the rated class: how it is published. */
    public static TransparencyFieldRating rate(TransparencyClassRating rating, String name, StructuralFeature field,
            PublicationMode mode) {
        TransparencyFieldRating fieldRating = F.createTransparencyFieldRating();
        fieldRating.setName(Objects.requireNonNull(name, "name"));
        fieldRating.setDataField(Objects.requireNonNull(field, "field"));
        fieldRating.setPublicationMode(Objects.requireNonNull(mode, "mode"));
        rating.getDataFields().add(fieldRating);
        return fieldRating;
    }

    /** The data of the class is published de-identified by {@code policy}. */
    public static TransparencyClassRating policy(TransparencyClassRating rating, DeidentificationPolicy policy) {
        rating.setPolicy(Objects.requireNonNull(policy, "policy"));
        return rating;
    }

    /** Whether a mode publishes the data without a request. */
    public static boolean proactive(PublicationMode mode) {
        return mode == PublicationMode.PROACTIVE_MANDATORY || mode == PublicationMode.PROACTIVE_OPTIONAL;
    }
}
