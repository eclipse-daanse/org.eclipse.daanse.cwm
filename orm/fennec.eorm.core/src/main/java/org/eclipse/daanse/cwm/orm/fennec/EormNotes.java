/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.orm.fennec;

import java.util.Set;

/**
 * The rules of the notes a CWM-ORM to EORM conversion reports. Everything the conversion cannot
 * carry over, and every construct the Fennec runtime does not read, has one rule here; the
 * README lists them with their meaning.
 */
public final class EormNotes {

    /** ERROR: a CWM class, feature or enumeration has no Ecore counterpart in the index. */
    public static final String UNRESOLVED = "UNRESOLVED";

    /** WARNING: an embedded id — Fennec does not deploy {@code embedded-id} (declare one id per key attribute). */
    public static final String FENNEC_EMBEDDED_ID = "FENNEC_EMBEDDED_ID";

    /** WARNING: an id class — Fennec does not deploy {@code id-class}. */
    public static final String FENNEC_ID_CLASS = "FENNEC_ID_CLASS";

    /** WARNING: a table generator — Fennec reports it and leaves the stored values alone. */
    public static final String FENNEC_TABLE_GENERATOR = "FENNEC_TABLE_GENERATOR";

    /** INFO: descriptions of mapping elements that have no {@code description} in EORM, counted. */
    public static final String DESCRIPTION = "DESCRIPTION";

    /** INFO: {@code insertable}/{@code updatable} on a primary-key join — EORM primary-key join columns have neither. */
    public static final String PK_JOIN_FLAGS = "PK_JOIN_FLAGS";

    /** WARNING: {@code enumerated=STRING} on a basic — EORM's {@code SimpleBase.enumerated} only names the EEnum. */
    public static final String ENUM_STRING = "ENUM_STRING";

    /** INFO: stored procedure parameters — EORM's parameter {@code class} is an EClass, SQL parameter types are none. */
    public static final String PARAMETER_CLASS = "PARAMETER_CLASS";

    /** WARNING: an element collection with more than one convert — EORM keeps one. */
    public static final String CONVERT = "CONVERT";

    /** INFO: a map key classifier that is no class — EORM's {@code mapKeyClass} is an EClass. */
    public static final String MAP_KEY_CLASS = "MAP_KEY_CLASS";

    /** WARNING: two lifecycle callbacks of the same kind — EORM keeps one per kind. */
    public static final String CALLBACK = "CALLBACK";

    /** WARNING or ERROR: a finding of the explicit-profile validator, passed through (lenient mode). */
    public static final String VALIDATION = "VALIDATION";

    /** All rules above. */
    public static final Set<String> ALL = Set.of(UNRESOLVED, FENNEC_EMBEDDED_ID, FENNEC_ID_CLASS,
            FENNEC_TABLE_GENERATOR, DESCRIPTION, PK_JOIN_FLAGS, ENUM_STRING, PARAMETER_CLASS, CONVERT, MAP_KEY_CLASS,
            CALLBACK, VALIDATION);

    private EormNotes() {
    }
}
