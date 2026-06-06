/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.cwm.testkit.api.dbcheck;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.util.resource.relational.Schemas;

/**
 * Asserts a schema exists (or is absent, per {@link #expectAbsent()}) and
 * contains the listed tables. A blank {@code schemaName} targets the default,
 * unqualified schema.
 */
public record DatabaseSchemaCheck(String schemaName, boolean expectAbsent, List<DatabaseTableCheck> tableChecks) {

    public DatabaseSchemaCheck {
        tableChecks = List.copyOf(tableChecks);
    }

    public DatabaseSchemaCheck(String schemaName, List<DatabaseTableCheck> tableChecks) {
        this(schemaName, false, tableChecks);
    }

    /**
     * Derives a presence check for every table in the CWM {@link Schema}.
     */
    public static DatabaseSchemaCheck fromCwm(Schema cwmSchema) {
        List<DatabaseTableCheck> tables = new ArrayList<>();
        for (Table t : Schemas.tables(cwmSchema)) {
            tables.add(DatabaseTableCheck.fromCwm(t));
        }
        return new DatabaseSchemaCheck(cwmSchema.getName() == null ? "" : cwmSchema.getName(), false, tables);
    }
}
