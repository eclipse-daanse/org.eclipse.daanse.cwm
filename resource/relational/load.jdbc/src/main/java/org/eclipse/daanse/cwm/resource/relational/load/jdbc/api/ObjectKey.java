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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.resource.relational.load.jdbc.api;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;

/**
 * The schema and the name of a table, view or routine, as the key to find the
 * element by that a JDBC snapshot names. Schema and name stay apart: unlike a
 * dotted name, {@code ("a.b", "c")} and {@code ("a", "b.c")} are two keys. A
 * database without schemas reports none; its key has the schema {@code null},
 * as has the element in the catalog's schema without a name.
 *
 * @param schema the name of the schema, {@code null} for none
 * @param name   the name of the element
 */
public record ObjectKey(String schema, String name) {

    /** The key of the table or view {@code table} names, whatever its type. */
    public static ObjectKey of(TableReference table) {
        return new ObjectKey(table.schema().map(SchemaReference::name).orElse(null), table.name());
    }

    /**
     * The key of a table, view or routine of a catalog: the name of the schema
     * that owns it ({@code null} when no schema does) and its own name.
     */
    public static ObjectKey of(ModelElement element) {
        String schema = element.getNamespace() instanceof Schema owner ? owner.getName() : null;
        return new ObjectKey(schema, element.getName());
    }

    /** {@code schema.name}, or the name alone without a schema; for messages, not as a key. */
    @Override
    public String toString() {
        return schema == null ? name : schema + "." + name;
    }
}
