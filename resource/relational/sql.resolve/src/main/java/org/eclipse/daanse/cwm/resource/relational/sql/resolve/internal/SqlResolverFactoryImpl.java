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
package org.eclipse.daanse.cwm.resource.relational.sql.resolve.internal;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.api.SqlResolver;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.api.SqlResolverFactory;
import org.eclipse.daanse.cwm.util.resource.relational.Schemas;
import org.osgi.service.component.annotations.Component;

/**
 * Default {@link SqlResolverFactory}: builds a {@link CwmJdbcMetaData} for the
 * given schemas and wraps it in a {@link SqlResolverImpl}.
 */
@Component(service = SqlResolverFactory.class)
public class SqlResolverFactoryImpl implements SqlResolverFactory {

    @Override
    public SqlResolver create(List<Schema> schemas) {
        String currentCatalog = "";
        String currentSchema = "";
        if (!schemas.isEmpty()) {
            Schema first = schemas.get(0);
            if (first.getName() != null)
                currentSchema = first.getName();
            currentCatalog = Schemas.findCatalog(first).map(c -> c.getName() == null ? "" : c.getName()).orElse("");
        }
        return create(schemas, currentCatalog, currentSchema);
    }

    @Override
    public SqlResolver create(List<Schema> schemas, String currentCatalog, String currentSchema) {
        return new SqlResolverImpl(new CwmJdbcMetaData(schemas, currentCatalog, currentSchema));
    }
}
