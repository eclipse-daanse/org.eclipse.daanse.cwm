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
package org.eclipse.daanse.cwm.data.api;

import java.util.List;

/**
 * Result of validating CSV headers against a CWM RecordDef's field definitions.
 */
public interface ValidationResult {

    /**
     * @return true if all RecordDef fields are present in the CSV header
     */
    boolean isValid();

    /**
     * @return field names defined in RecordDef but missing from CSV header
     */
    List<String> missingFields();

    /**
     * @return field names present in CSV header but not defined in RecordDef
     */
    List<String> extraFields();

    /**
     * @return field names that match between CSV header and RecordDef
     */
    List<String> matchedFields();
}
