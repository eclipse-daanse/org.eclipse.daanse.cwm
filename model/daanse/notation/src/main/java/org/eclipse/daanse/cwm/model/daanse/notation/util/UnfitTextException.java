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

/**
 * A text that does not fit its notation: where (the position of the first character that does not fit, from
 * 0, in the text as given, before trimming) and why.
 */
public final class UnfitTextException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String text;
    private final int position;
    private final String reason;

    /** A text that does not fit at {@code position} for {@code reason}. */
    public UnfitTextException(String text, int position, String reason) {
        super("'" + text + "' does not fit at position " + position + ": " + reason);
        this.text = text;
        this.position = position;
        this.reason = reason;
    }

    /** The text as given. */
    public String text() {
        return text;
    }

    /** The position of the first character that does not fit, from 0; the length of the text where it ends early. */
    public int position() {
        return position;
    }

    /** Why the text does not fit there. */
    public String reason() {
        return reason;
    }
}
