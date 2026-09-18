/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.liquibase.render;

/**
 * Options for changelog output.
 *
 * @param author          the changeSet author attribute
 * @param logicalFilePath keeps checksums stable when the file moves; null omits it
 * @param includeRollback write {@code <rollback>} blocks where the inverse is computable
 */
public record ChangelogSettings(String author, String logicalFilePath, boolean includeRollback) {

    public static ChangelogSettings defaults() {
        return new ChangelogSettings("cwm-diff", "cwm/changelog.xml", true);
    }

    public ChangelogSettings withAuthor(String a) {
        return new ChangelogSettings(a, logicalFilePath, includeRollback);
    }

    public ChangelogSettings withLogicalFilePath(String p) {
        return new ChangelogSettings(author, p, includeRollback);
    }

    public ChangelogSettings withRollback(boolean on) {
        return new ChangelogSettings(author, logicalFilePath, on);
    }
}
