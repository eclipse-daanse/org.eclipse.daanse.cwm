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

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.ColumnResult;
import org.eclipse.daanse.cwm.model.daanse.orm.ConstructorResult;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityMappings;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityResult;
import org.eclipse.daanse.cwm.model.daanse.orm.FieldResult;
import org.eclipse.daanse.cwm.model.daanse.orm.SqlResultSetMapping;

/**
 * Creates result-set mappings of native queries. Every result column is a column of the stored
 * query ({@code relational::QueryColumnSet}) the native query refers to; its name is the alias in
 * the result set. Entity, field and constructor results are named after their anchors, column
 * results after their column.
 */
public final class ResultSets {

    private ResultSets() {
    }

    /** A result-set mapping of the unit. */
    public static SqlResultSetMapping create(EntityMappings em, String name) {
        SqlResultSetMapping m = CWMORMFactory.eINSTANCE.createSqlResultSetMapping();
        m.setName(name);
        em.getSqlResultSetMapping().add(m);
        return m;
    }

    /** An entity result of class {@code cls}. */
    public static EntityResult entity(SqlResultSetMapping mapping, Class cls) {
        EntityResult r = CWMORMFactory.eINSTANCE.createEntityResult();
        r.setName(cls.getName());
        r.setCwmClass(cls);
        mapping.getEntityResult().add(r);
        return r;
    }

    /** The result column {@code column} fills {@code feature} of the entity result. */
    public static FieldResult field(EntityResult result, StructuralFeature feature, Column column) {
        FieldResult f = CWMORMFactory.eINSTANCE.createFieldResult();
        f.setName(feature.getName());
        f.setCwmFeature(feature);
        f.setCwmColumn(column);
        result.getFieldResult().add(f);
        return f;
    }

    /** A scalar result: the result column {@code column}. */
    public static ColumnResult column(SqlResultSetMapping mapping, Column column) {
        ColumnResult c = columnResult(column);
        mapping.getColumnResult().add(c);
        return c;
    }

    /** A constructor result invoking a constructor of {@code cls}. */
    public static ConstructorResult constructor(SqlResultSetMapping mapping, Class cls) {
        ConstructorResult r = CWMORMFactory.eINSTANCE.createConstructorResult();
        r.setName(cls.getName());
        r.setCwmClass(cls);
        mapping.getConstructorResult().add(r);
        return r;
    }

    /** The next constructor argument: the result column {@code column}. */
    public static ColumnResult argument(ConstructorResult result, Column column) {
        ColumnResult c = columnResult(column);
        result.getColumnResult().add(c);
        return c;
    }

    private static ColumnResult columnResult(Column column) {
        ColumnResult c = CWMORMFactory.eINSTANCE.createColumnResult();
        c.setName(column.getName());
        c.setCwmColumn(column);
        return c;
    }
}
