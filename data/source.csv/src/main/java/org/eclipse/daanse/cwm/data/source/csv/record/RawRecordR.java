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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.data.api.RawRecord;

/**
 * A row as an array of values plus the field names of its source.
 *
 * <p>
 * {@code fieldNames} is meant to be the one list the source built for itself,
 * handed to every record unchanged: it is not copied here, and it must not be
 * modified once records exist.
 *
 * @param fieldNames the names, in value order, shared across the source
 * @param values     the raw values; may be shorter than {@code fieldNames} when
 *                   the row was short
 * @param lineNumber the line in the source, 1-based
 */
public record RawRecordR(List<String> fieldNames, String[] values, long lineNumber) implements RawRecord {

    @Override
    public int fieldCount() {
        return values.length;
    }

    @Override
    public String field(int index) {
        return index >= 0 && index < values.length ? values[index] : null;
    }

    @Override
    public String field(String name) {
        return field(fieldNames.indexOf(name));
    }

    @Override
    public Map<String, String> asMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < fieldNames.size(); i++) {
            map.put(fieldNames.get(i), field(i));
        }
        return map;
    }
}
