/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.orm.util;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.CollectionSupport;
import org.eclipse.daanse.cwm.model.daanse.orm.MapKey;

/** Creates the {@link MapKey} of a map-valued collection; the name is the key feature or type name. */
public final class MapKeys {

    private MapKeys() {
    }

    /** The key is {@code feature} of the target type. */
    public static MapKey attribute(CollectionSupport collection, StructuralFeature feature) {
        MapKey k = CWMORMFactory.eINSTANCE.createMapKey();
        k.setName(feature.getName());
        k.setCwmAttribute(feature);
        collection.setMapKey(k);
        return k;
    }

    /** The key is a value of {@code type} in {@code column} ({@code null} for an embeddable key). */
    public static MapKey type(CollectionSupport collection, Classifier type, Column column) {
        MapKey k = CWMORMFactory.eINSTANCE.createMapKey();
        k.setName(type.getName());
        k.setCwmClassifier(type);
        if (column != null) {
            k.setColumn(Columns.of(column));
        }
        collection.setMapKey(k);
        return k;
    }
}
