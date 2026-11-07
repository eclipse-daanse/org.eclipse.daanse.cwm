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
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.model.cwm.resource.relational.util;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.DatatypesFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.QueryExpression;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;

public final class Views {

    private Views() {
    }

    /**
     * A View whose {@code queryExpression} is {@code sql} (language
     * {@code SQL}), owned by {@code parent} via {@code ownedElement}. Creation
     * rule: see {@code objectmodel.core.util.Models}.
     */
    public static View create(Schema parent, String name, String sql) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sql, "sql");
        View view = RelationalFactory.eINSTANCE.createView();
        view.setName(name);
        QueryExpression query = DatatypesFactory.eINSTANCE.createQueryExpression();
        query.setLanguage("SQL");
        query.setBody(sql);
        view.setQueryExpression(query);
        parent.getOwnedElement().add(view);
        return view;
    }

    /** The query expression body, or empty if the view has no query. */
    public static Optional<String> queryBody(View view) {
        QueryExpression qe = view == null ? null : view.getQueryExpression();
        return qe == null ? Optional.empty() : Optional.ofNullable(qe.getBody());
    }

    /** The query expression language, or empty if the view has no query. */
    public static Optional<String> queryLanguage(View view) {
        QueryExpression qe = view == null ? null : view.getQueryExpression();
        return qe == null ? Optional.empty() : Optional.ofNullable(qe.getLanguage());
    }
}
