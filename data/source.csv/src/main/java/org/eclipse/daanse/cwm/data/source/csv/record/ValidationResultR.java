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
package org.eclipse.daanse.cwm.data.source.csv.record;

import java.util.List;

import org.eclipse.daanse.cwm.data.api.ValidationResult;

/**
 * Record implementation of {@link ValidationResult}.
 */
public record ValidationResultR(boolean isValid, List<String> missingFields, List<String> extraFields,
        List<String> matchedFields) implements ValidationResult {
}
