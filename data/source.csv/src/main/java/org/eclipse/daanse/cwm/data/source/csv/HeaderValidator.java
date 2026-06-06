/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.cwm.data.source.csv;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.resource.record.RecordDef;
import org.eclipse.daanse.cwm.data.api.ValidationResult;
import org.eclipse.daanse.cwm.data.source.csv.record.ValidationResultR;
import org.eclipse.emf.common.util.EList;

/**
 * Validates CSV header fields against a CWM RecordDef's field definitions.
 */
public class HeaderValidator {

    private HeaderValidator() {
    }

    /**
     * Checks the CSV header against the fields declared in {@code recordDef},
     * reporting matched, missing and extra field names.
     */
    public static ValidationResult validate(List<String> csvHeaders, RecordDef recordDef) {
        Set<String> headerSet = new LinkedHashSet<>(csvHeaders);

        EList<Feature> features = recordDef.getFeature();
        Set<String> expectedFields = new LinkedHashSet<>();
        for (Feature feature : features) {
            expectedFields.add(feature.getName());
        }

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> extra = new ArrayList<>();

        for (String expected : expectedFields) {
            if (headerSet.contains(expected)) {
                matched.add(expected);
            } else {
                missing.add(expected);
            }
        }

        for (String header : csvHeaders) {
            if (!expectedFields.contains(header)) {
                extra.add(header);
            }
        }

        boolean isValid = missing.isEmpty();
        return new ValidationResultR(isValid, List.copyOf(missing), List.copyOf(extra), List.copyOf(matched));
    }
}
