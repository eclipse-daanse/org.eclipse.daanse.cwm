/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.orm.util;

import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.Column;

/** Creates mapped {@link Column}s around catalog columns; the name is the column name. */
public final class Columns {

    private Columns() {
    }

    /** A mapped column for {@code column}. */
    public static Column of(org.eclipse.daanse.cwm.model.cwm.resource.relational.Column column) {
        Column c = CWMORMFactory.eINSTANCE.createColumn();
        c.setName(column.getName());
        c.setCwmColumn(column);
        return c;
    }

    /** A mapped column that is neither inserted nor updated by the provider. */
    public static Column readOnly(org.eclipse.daanse.cwm.model.cwm.resource.relational.Column column) {
        Column c = of(column);
        c.setInsertable(false);
        c.setUpdatable(false);
        return c;
    }
}
