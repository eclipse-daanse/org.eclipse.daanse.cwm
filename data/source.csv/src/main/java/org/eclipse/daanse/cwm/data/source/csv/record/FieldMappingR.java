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

import java.util.Optional;
import java.util.function.Function;

import org.eclipse.daanse.cwm.data.api.FieldMapping;

/**
 * Record implementation of {@link FieldMapping}.
 */
public record FieldMappingR(String sourceFieldName, String targetFeatureName,
        Optional<Function<String, Object>> converter) implements FieldMapping {
}
