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
package org.eclipse.daanse.cwm.resource.relational.sql.resolve.api;

import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.QueryExpression;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.QueryColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;

/**
 * Resolves a SQL query — a raw string, a {@link QueryColumnSet}, a
 * {@link QueryExpression}, or a {@link View} body — against the CWM
 * {@link org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema}s this
 * resolver was created for, reporting the columns it touches (classified by
 * clause) as the original CWM EObjects. Obtain one from a
 * {@link SqlResolverFactory}.
 */
public interface SqlResolver {

    /** Resolve the query body of a {@link QueryColumnSet}. */
    Resolution resolve(QueryColumnSet qcs);

    /** Resolve the defining query body of a {@link View}. */
    Resolution resolve(View view);

    /** Resolve a {@link QueryExpression} (e.g. {@code View.getQueryExpression()}). */
    Resolution resolve(QueryExpression qe);

    /** Resolve a raw SQL string. */
    Resolution resolve(String sql);

    /**
     * Validate a {@link QueryColumnSet}'s declared output columns against the
     * columns its query actually produces (from a prior {@link #resolve}).
     */
    Validation validate(QueryColumnSet qcs, Resolution resolution);
}
