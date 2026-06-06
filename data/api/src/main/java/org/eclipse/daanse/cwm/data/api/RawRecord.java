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

import java.util.Map;

/**
 * Represents a single parsed row from a data source as field name to raw string
 * value pairs. This is the common data type flowing through CSV pipelines.
 */
public interface RawRecord {

    /**
     * @return the field values keyed by field name
     */
    Map<String, String> fields();

    /**
     * @return the line number in the source (1-based)
     */
    long lineNumber();
}
