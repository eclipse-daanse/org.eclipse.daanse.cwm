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

import java.util.Set;

/** The rules of the {@link Note}s of a conversion. */
public final class AtlasNotes {

    /** A finding of the sensiNact, lifecycle, recording or units validator, carried over (rule and message inside). */
    public static final String VALIDATION = "VALIDATION";
    /** A source class or feature has no element in the given Ecore model (error). */
    public static final String UNRESOLVED = "UNRESOLVED";
    /**
     * The Ecore type of a resource: an override that names no Ecore data type (error), a data type
     * without Ecore type (warning, {@code EString}), an enumeration outside the given Ecore model (info,
     * {@code EString}).
     */
    public static final String DATA_TYPE = "DATA_TYPE";
    /**
     * A lifecycle rule, or a part of one, Fennec's mapping model cannot carry: an error (nothing is
     * written), with {@code lenient} a warning (the part is left out); a range rule written as an
     * absolute one (info); a retention rule Fennec's deletion rule carries only in part (warning, info).
     */
    public static final String RULE = "RULE";
    /** A value path picks an element Fennec cannot pick there: values and admin paths read element 0. */
    public static final String PATH = "PATH";
    /** A literal Fennec cannot carry where it stands, or carries in another form. */
    public static final String LITERAL = "LITERAL";
    /** A provider mapping without admin mapping: Fennec needs one, an empty one is written. */
    public static final String ADMIN = "ADMIN";
    /** Without profiles, several mappings onto one provider make several provider models. */
    public static final String PROFILE = "PROFILE";
    /** Two provider mappings with one name: the name is the key of a mapping in Fennec's registry. */
    public static final String NAME = "NAME";

    /** Every rule. */
    public static final Set<String> ALL = Set.of(VALIDATION, UNRESOLVED, DATA_TYPE, RULE, PATH, LITERAL, ADMIN,
            PROFILE, NAME);

    private AtlasNotes() {
    }
}
