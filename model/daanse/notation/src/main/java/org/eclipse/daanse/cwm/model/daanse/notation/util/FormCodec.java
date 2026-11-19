/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.notation.util;

/** Reads and writes the values of one form. */
interface FormCodec {

    /**
     * The value of {@code text} from {@code start} to {@code end}; positions in a failure count in the whole text.
     *
     * @throws UnfitTextException where the text does not fit the form
     */
    Object read(String text, int start, int end);

    /**
     * The text of {@code value}.
     *
     * @throws IllegalArgumentException where the form cannot write the value
     */
    String write(Object value);

    /** What the rest of a text looks like, for a message: up to twelve characters, or the end. */
    static String found(String text, int pos, int end) {
        if (pos >= end) {
            return "the end of the text";
        }
        String rest = text.substring(pos, Math.min(end, pos + 12));
        return "'" + rest + (end > pos + 12 ? "…'" : "'");
    }
}
