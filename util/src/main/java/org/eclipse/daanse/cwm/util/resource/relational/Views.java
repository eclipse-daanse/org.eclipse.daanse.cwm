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
package org.eclipse.daanse.cwm.util.resource.relational;

import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.QueryExpression;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;

public final class Views {

    private Views() {
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
