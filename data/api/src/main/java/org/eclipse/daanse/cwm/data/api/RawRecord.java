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
import java.util.Map;

/**
 * A single parsed row, as raw string values in the order the source produced
 * them, with the field names alongside.
 *
 * <p>
 * The names belong to the source, not to the row, so {@link #fieldNames()}
 * returns the same list for every record: a consumer resolves the positions it
 * needs once and reads {@link #field(int)} afterwards, rather than looking up a
 * name per field per row.
 */
public interface RawRecord {

    /**
     * The field names, in value order. The same instance for every record of one
     * source, so it is safe (and intended) to resolve positions from it once.
     */
    List<String> fieldNames();

    /** How many values this record has. */
    int fieldCount();

    /**
     * The value at {@code index}, or {@code null} if the row is shorter than the
     * header.
     */
    String field(int index);

    /**
     * The value of the named field, or {@code null} if there is no such field.
     * Convenience for occasional access; see {@link #fieldNames()}.
     */
    String field(String name);

    /**
     * The values keyed by field name. Materialised on demand: nothing builds this
     * unless it is asked for.
     */
    Map<String, String> asMap();

    /** The line number in the source (1-based). */
    long lineNumber();
}
