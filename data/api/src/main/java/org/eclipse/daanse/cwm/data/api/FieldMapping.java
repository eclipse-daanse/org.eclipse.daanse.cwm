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

import java.util.Optional;
import java.util.function.Function;

/**
 * A single field-level mapping from a source field name to a target feature
 * name, with an optional value converter.
 */
public interface FieldMapping {

    /**
     * @return the source field name
     */
    String sourceFieldName();

    /**
     * @return the target column or feature name
     */
    String targetFeatureName();

    /**
     * @return an optional converter from the raw string to the target value
     */
    Optional<Function<String, Object>> converter();
}
