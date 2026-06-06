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
package org.eclipse.daanse.cwm.resource.relational.ddl.internal;

import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlGenerator;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlGeneratorFactory;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.osgi.service.component.annotations.Component;

/** Default {@link DdlGeneratorFactory}, producing {@link DdlGeneratorImpl}s. */
@Component(service = DdlGeneratorFactory.class)
public class DdlGeneratorFactoryImpl implements DdlGeneratorFactory {

    @Override
    public DdlGenerator create(Dialect dialect) {
        return new DdlGeneratorImpl(dialect);
    }

    @Override
    public DdlGenerator create(Dialect dialect, DdlSettings settings) {
        return new DdlGeneratorImpl(dialect, settings);
    }
}
