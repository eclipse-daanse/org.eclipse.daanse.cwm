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
package org.eclipse.daanse.cwm.resource.relational.load.jdbc.api;

import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;

/**
 * What a {@link CwmLoader} reads and how it names it. Never changes; start from
 * {@link #defaults()} and derive with the {@code with} methods.
 *
 * @param catalogName the name of the catalog; {@code null} or blank: the name of the database product
 * @param selection   what is read, as {@link Selection} decides it on the catalog read: rules on schemas,
 *                    tables and views, on kinds such as indexes or privileges, on tagged values. Write rule
 *                    texts in the vocabulary of the model read, such as {@code RelationalRules.VOCABULARY}
 * @param commentType the {@code type} of the descriptions the comments of the database are read into
 */
public record JdbcToCwmConfig(String catalogName, Selection selection, String commentType) {

    public JdbcToCwmConfig {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(commentType, "commentType");
        if (commentType.isBlank()) {
            throw new IllegalArgumentException("commentType must not be blank; leave comments out with !is:comment");
        }
    }

    /**
     * The name of the database, everything, the comments as
     * {@link Descriptions#JDBC_REMARKS}.
     */
    public static JdbcToCwmConfig defaults() {
        return new JdbcToCwmConfig(null, Selection.all(), Descriptions.JDBC_REMARKS);
    }

    public JdbcToCwmConfig withCatalogName(String name) {
        return new JdbcToCwmConfig(name, selection, commentType);
    }

    public JdbcToCwmConfig withSelection(Selection value) {
        return new JdbcToCwmConfig(catalogName, value, commentType);
    }

    public JdbcToCwmConfig withCommentType(String type) {
        return new JdbcToCwmConfig(catalogName, selection, type);
    }
}
