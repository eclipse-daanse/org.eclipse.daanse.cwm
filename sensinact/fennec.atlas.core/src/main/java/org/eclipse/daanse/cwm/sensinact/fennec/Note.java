/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.sensinact.fennec;

/**
 * A finding of a conversion: severity, rule (one of {@link AtlasNotes}), the CWM element it is
 * about (its dotted name), message.
 */
public record Note(Severity severity, String rule, String element, String message) {

    /** How serious a note is; an {@link #ERROR} means the output is incomplete. */
    public enum Severity {
        ERROR, WARNING, INFO
    }

    /** An error. */
    public static Note error(String rule, String element, String message) {
        return new Note(Severity.ERROR, rule, element, message);
    }

    /** A warning. */
    public static Note warning(String rule, String element, String message) {
        return new Note(Severity.WARNING, rule, element, message);
    }

    /** An information. */
    public static Note info(String rule, String element, String message) {
        return new Note(Severity.INFO, rule, element, message);
    }

    @Override
    public String toString() {
        return severity + " " + rule + " " + element + ": " + message;
    }
}
