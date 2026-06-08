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

import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Filters and toggles for {@link JdbcToCwmLoader}. Use {@link #all()} to import
 * every schema and table, or build via {@link Builder} to scope the load.
 */
public final class JdbcToCwmConfig {

    private final String catalogName;
    private final Set<String> schemaFilter;
    private final BiPredicate<String, String> tableFilter;
    private final boolean includeViews;
    private final boolean includeIndexes;
    private final boolean includeForeignKeys;
    private final boolean includeUniqueConstraints;
    private final boolean includeCheckConstraints;
    private final boolean includeTriggers;

    private JdbcToCwmConfig(Builder b) {
        this.catalogName = b.catalogName;
        this.schemaFilter = Set.copyOf(b.schemaFilter);
        this.tableFilter = b.tableFilter;
        this.includeViews = b.includeViews;
        this.includeIndexes = b.includeIndexes;
        this.includeForeignKeys = b.includeForeignKeys;
        this.includeUniqueConstraints = b.includeUniqueConstraints;
        this.includeCheckConstraints = b.includeCheckConstraints;
        this.includeTriggers = b.includeTriggers;
    }

    /** {@code null} (default) lets the loader pick the database name. */
    public String catalogName() {
        return catalogName;
    }

    /** Empty (default) means "every schema the snapshot reports". */
    public Set<String> schemaFilter() {
        return schemaFilter;
    }

    /** Default accepts everything that survives the schema filter. */
    public BiPredicate<String, String> tableFilter() {
        return tableFilter;
    }

    public boolean includeViews() {
        return includeViews;
    }

    public boolean includeIndexes() {
        return includeIndexes;
    }

    public boolean includeForeignKeys() {
        return includeForeignKeys;
    }

    public boolean includeUniqueConstraints() {
        return includeUniqueConstraints;
    }

    public boolean includeCheckConstraints() {
        return includeCheckConstraints;
    }

    public boolean includeTriggers() {
        return includeTriggers;
    }

    /** Import everything. */
    public static JdbcToCwmConfig all() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String catalogName;
        private Set<String> schemaFilter = Set.of();
        private BiPredicate<String, String> tableFilter = (s, t) -> true;
        private boolean includeViews = true;
        private boolean includeIndexes = true;
        private boolean includeForeignKeys = true;
        private boolean includeUniqueConstraints = true;
        private boolean includeCheckConstraints = true;
        private boolean includeTriggers = true;

        private Builder() {
        }

        public Builder catalogName(String name) {
            this.catalogName = name;
            return this;
        }

        public Builder schemaFilter(Set<String> schemas) {
            this.schemaFilter = schemas == null ? Set.of() : Set.copyOf(schemas);
            return this;
        }

        public Builder schemas(String... schemas) {
            this.schemaFilter = Set.of(schemas);
            return this;
        }

        public Builder tableFilter(BiPredicate<String, String> filter) {
            this.tableFilter = filter == null ? (s, t) -> true : filter;
            return this;
        }

        public Builder includeViews(boolean include) {
            this.includeViews = include;
            return this;
        }

        public Builder includeIndexes(boolean include) {
            this.includeIndexes = include;
            return this;
        }

        public Builder includeForeignKeys(boolean include) {
            this.includeForeignKeys = include;
            return this;
        }

        public Builder includeUniqueConstraints(boolean include) {
            this.includeUniqueConstraints = include;
            return this;
        }

        public Builder includeCheckConstraints(boolean include) {
            this.includeCheckConstraints = include;
            return this;
        }

        public Builder includeTriggers(boolean include) {
            this.includeTriggers = include;
            return this;
        }

        public JdbcToCwmConfig build() {
            return new JdbcToCwmConfig(this);
        }
    }
}
