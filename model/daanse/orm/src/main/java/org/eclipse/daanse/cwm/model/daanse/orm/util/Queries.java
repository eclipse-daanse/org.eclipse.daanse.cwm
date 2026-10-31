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

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.QueryColumnSet;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityMappings;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedNativeQuery;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedQuery;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedStoredProcedureQuery;
import org.eclipse.daanse.cwm.model.daanse.orm.Query;
import org.eclipse.daanse.cwm.model.daanse.orm.QueryHint;

/** Creates the named queries of the unit and their hints; queries live only at the root. */
public final class Queries {

    private Queries() {
    }

    /** A named JPQL query. */
    public static NamedQuery named(EntityMappings em, String name, String jpql) {
        NamedQuery q = CWMORMFactory.eINSTANCE.createNamedQuery();
        q.setName(name);
        q.setQuery(jpql);
        em.getNamedQuery().add(q);
        return q;
    }

    /** A named native query over the stored query {@code query}; the name is the query's name. */
    public static NamedNativeQuery nativeOf(EntityMappings em, QueryColumnSet query) {
        return nativeOf(em, query.getName(), query);
    }

    /** A named native query with its own name over the stored query {@code query}. */
    public static NamedNativeQuery nativeOf(EntityMappings em, String name, QueryColumnSet query) {
        NamedNativeQuery q = CWMORMFactory.eINSTANCE.createNamedNativeQuery();
        q.setName(name);
        q.setCwmQuery(query);
        em.getNamedNativeQuery().add(q);
        return q;
    }

    /** A named stored-procedure query calling {@code procedure}. */
    public static NamedStoredProcedureQuery procedure(EntityMappings em, String name, Procedure procedure) {
        NamedStoredProcedureQuery q = CWMORMFactory.eINSTANCE.createNamedStoredProcedureQuery();
        q.setName(name);
        q.setCwmProcedure(procedure);
        em.getNamedStoredProcedureQuery().add(q);
        return q;
    }

    /** A provider hint of {@code query}. */
    public static QueryHint hint(Query query, String name, String value) {
        QueryHint h = CWMORMFactory.eINSTANCE.createQueryHint();
        h.setName(name);
        h.setValue(value);
        query.getHint().add(h);
        return h;
    }
}
