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
package org.eclipse.daanse.cwm.resource.relational.ddl.api;

import java.util.EnumSet;
import java.util.Set;

/** Schema entity kinds a {@link DdlGenerator} can emit or skip. */
public enum Feature {
    SCHEMA, TABLE, PRIMARY_KEY, UNIQUE, CHECK, INDEX, FOREIGN_KEY, VIEW, TRIGGER;

    /** All features. */
    public static final Set<Feature> ALL = EnumSet.allOf(Feature.class);
}
