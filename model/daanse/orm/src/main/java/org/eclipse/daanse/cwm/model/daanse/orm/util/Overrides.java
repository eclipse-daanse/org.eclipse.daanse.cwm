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

import java.util.List;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.daanse.orm.AssociationOverride;
import org.eclipse.daanse.cwm.model.daanse.orm.AttributeOverride;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.Join;
import org.eclipse.daanse.cwm.model.daanse.orm.JoinTable;
import org.eclipse.daanse.cwm.model.daanse.orm.OverrideHost;

/**
 * Creates attribute and association overrides on an {@link OverrideHost}. The path is typed; the
 * name is the path names joined with a dot.
 */
public final class Overrides {

    private Overrides() {
    }

    /** Overrides the column of the value feature at {@code path} with {@code column}. */
    public static AttributeOverride attribute(OverrideHost host,
            org.eclipse.daanse.cwm.model.cwm.resource.relational.Column column, StructuralFeature... path) {
        return attribute(host, Columns.of(column), path);
    }

    /** Overrides the column of the value feature at {@code path} with a prepared mapped column. */
    public static AttributeOverride attribute(OverrideHost host,
            org.eclipse.daanse.cwm.model.daanse.orm.Column column, StructuralFeature... path) {
        AttributeOverride ao = CWMORMFactory.eINSTANCE.createAttributeOverride();
        ao.getCwmPath().addAll(List.of(path));
        ao.setName(OrmBindings.path(ao.getCwmPath()));
        ao.setColumn(column);
        host.getAttributeOverride().add(ao);
        return ao;
    }

    /** Overrides the join of the relationship at {@code path}. */
    public static AssociationOverride association(OverrideHost host, Join join, StructuralFeature... path) {
        AssociationOverride ao = base(host, path);
        ao.setJoin(join);
        return ao;
    }

    /** Overrides the join table of the relationship at {@code path}. */
    public static AssociationOverride association(OverrideHost host, JoinTable joinTable,
            StructuralFeature... path) {
        AssociationOverride ao = base(host, path);
        ao.setJoinTable(joinTable);
        return ao;
    }

    private static AssociationOverride base(OverrideHost host, StructuralFeature... path) {
        AssociationOverride ao = CWMORMFactory.eINSTANCE.createAssociationOverride();
        ao.getCwmPath().addAll(List.of(path));
        ao.setName(OrmBindings.path(ao.getCwmPath()));
        host.getAssociationOverride().add(ao);
        return ao;
    }
}
