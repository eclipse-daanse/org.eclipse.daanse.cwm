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
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.assessment.AssessmentFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.assessment.AssessmentPackage;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.assessment.PersonalDataCategory;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.assessment.PrivacyClassRating;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.assessment.PrivacyFieldRating;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.assessment.PrivacyLevel;

/**
 * Rates data classes and fields: the privacy level of a class, the category of personal data of a
 * field. A category of Art. 9 or Art. 10 GDPR is special.
 */
public final class PrivacyRatings {

    private static final Set<PersonalDataCategory> SPECIAL = Set.of(PersonalDataCategory.ETHNIC_ORIGIN,
            PersonalDataCategory.RELIGIOUS_PHILOSOPHICAL_BELIEFS, PersonalDataCategory.TRADE_UNION_MEMBERSHIP,
            PersonalDataCategory.GENETIC_DATA, PersonalDataCategory.BIOMETRIC_DATA, PersonalDataCategory.HEALTH_DATA,
            PersonalDataCategory.SEXUAL_LIFE_ORIENTATION, PersonalDataCategory.CRIMINAL_CONVICTIONS_OFFENSES);

    private PrivacyRatings() {
    }

    /** The privacy level of a data class, rated in {@code owner}. */
    public static PrivacyClassRating rate(Namespace owner, String name, Classifier dataClass, PrivacyLevel level) {
        PrivacyClassRating rating = AssessmentFactory.eINSTANCE.createPrivacyClassRating();
        rating.setName(Objects.requireNonNull(name, "name"));
        rating.setDataClass(Objects.requireNonNull(dataClass, "dataClass"));
        rating.setPrivacyLevel(Objects.requireNonNull(level, "level"));
        owner.getOwnedElement().add(rating);
        return rating;
    }

    /** The category of personal data of a field, rated in {@code owner}. */
    public static PrivacyFieldRating rate(Namespace owner, String name, StructuralFeature field,
            PersonalDataCategory category) {
        PrivacyFieldRating rating = AssessmentFactory.eINSTANCE.createPrivacyFieldRating();
        rating.setName(Objects.requireNonNull(name, "name"));
        rating.setDataField(Objects.requireNonNull(field, "field"));
        rating.setPersonalDataCategory(Objects.requireNonNull(category, "category"));
        owner.getOwnedElement().add(rating);
        return rating;
    }

    /** The ratings of a field. */
    public static List<PrivacyFieldRating> of(StructuralFeature field) {
        return InverseReferences.referencingList(field, AssessmentPackage.Literals.PRIVACY_FIELD_RATING__DATA_FIELD,
                PrivacyFieldRating.class);
    }

    /** Whether a category is a special category of personal data (Art. 9, Art. 10 GDPR). */
    public static boolean special(PersonalDataCategory category) {
        return SPECIAL.contains(category);
    }
}
