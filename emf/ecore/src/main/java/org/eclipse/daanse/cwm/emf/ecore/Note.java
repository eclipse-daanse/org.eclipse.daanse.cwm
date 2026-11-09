/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.emf.ecore;

/**
 * A finding of a conversion: something the target cannot express, a name that had to change, a
 * reference that leads out of the converted packages.
 *
 * @param severity how bad it is; only {@link Severity#ERROR} makes a result unusable
 * @param rule     a short, stable code, e.g. {@code TYPE}, {@code N_ARY}, {@code ECORE}
 * @param element  the qualified name of the source element the note is about
 * @param message  human-readable text
 */
public record Note(Severity severity, String rule, String element, String message) {

    /** Severity of a {@link Note}. */
    public enum Severity {
        /** The result is incomplete or invalid. */
        ERROR,
        /** The result is usable, but differs from the source or the runtime will complain. */
        WARNING,
        /** Information only. */
        INFO
    }

    /** An error note. */
    public static Note error(String rule, String element, String message) {
        return new Note(Severity.ERROR, rule, element, message);
    }

    /** A warning note. */
    public static Note warning(String rule, String element, String message) {
        return new Note(Severity.WARNING, rule, element, message);
    }

    /** An information note. */
    public static Note info(String rule, String element, String message) {
        return new Note(Severity.INFO, rule, element, message);
    }

    @Override
    public String toString() {
        return severity + " " + rule + " " + element + ": " + message;
    }
}
