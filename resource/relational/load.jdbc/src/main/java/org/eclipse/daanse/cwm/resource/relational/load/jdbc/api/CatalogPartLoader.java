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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.resource.relational.load.jdbc.api;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ConsequenceRule;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;

/**
 * Reads one more part of a catalog from the snapshot the {@link CwmLoader}
 * reads, beyond the relational structure: the roles, users and privileges, for
 * one. Registered as an OSGi service, the {@code CwmLoader} calls every part.
 *
 * <p>
 * A part is called after the structure is in the catalog and before the
 * selection is applied to it. It adds its elements to the catalog itself, so
 * the selection, its consequences and the check of its include rules treat
 * them as the rest of the catalog: what the part reads is cut afterwards as a
 * copy of the whole catalog would be. A part need not read what the selection
 * excludes, and should not, where reading costs.
 * </p>
 */
public interface CatalogPartLoader {

    /**
     * Adds the elements of this part, read from {@code info}, to
     * {@code catalog}; references go to the elements of the catalog.
     *
     * @param info      the snapshot the catalog's structure was read from
     * @param catalog   the catalog, with its structure; not yet cut to {@code selection}
     * @param selection what is to be read
     */
    void load(MetaInfo info, Catalog catalog, Selection selection);

    /**
     * The consequences of the references of this part's elements, which the
     * {@code CwmLoader} puts in front of those of the relational model when it
     * cuts the catalog to the selection.
     */
    List<ConsequenceRule> consequences();
}
