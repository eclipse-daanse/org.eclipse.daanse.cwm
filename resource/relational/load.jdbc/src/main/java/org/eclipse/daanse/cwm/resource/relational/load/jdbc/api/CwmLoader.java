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

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.jdbc.db.api.meta.MetaInfo;

/**
 * Builds a CWM relational {@link Catalog} from a JDBC {@link MetaInfo} snapshot —
 * the inverse of DDL generation. Registered as an OSGi service.
 */
public interface CwmLoader {

    /**
     * Load {@code info} into a fresh CWM {@link Catalog}, honouring {@code config}
     * (a {@code null} config means {@link JdbcToCwmConfig#all()}).
     */
    Catalog load(MetaInfo info, JdbcToCwmConfig config);
}
