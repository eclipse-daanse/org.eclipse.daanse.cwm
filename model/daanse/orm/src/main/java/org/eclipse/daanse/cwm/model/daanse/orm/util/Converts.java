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

import java.util.List;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.daanse.orm.Basic;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.Convert;
import org.eclipse.daanse.cwm.model.daanse.orm.Converter;
import org.eclipse.daanse.cwm.model.daanse.orm.OverrideHost;

/**
 * Creates {@link Convert}s. The name is the path names joined with a dot, or for the attribute
 * itself the converter name ({@code disableConversion} when conversion is switched off).
 */
public final class Converts {

    private Converts() {
    }

    /** Applies {@code converter} to the value of {@code basic}. */
    public static Convert of(Basic basic, Converter converter) {
        Convert c = create(converter, List.of());
        basic.setConvert(c);
        return c;
    }

    /** Applies {@code converter} to the feature at {@code path} of the host. */
    public static Convert of(OverrideHost host, Converter converter, StructuralFeature... path) {
        Convert c = create(converter, List.of(path));
        host.getConvert().add(c);
        return c;
    }

    /** Disables an auto-applied converter for the value of {@code basic}. */
    public static Convert disable(Basic basic) {
        Convert c = create(null, List.of());
        basic.setConvert(c);
        return c;
    }

    /** Disables an auto-applied converter for the feature at {@code path} of the host. */
    public static Convert disable(OverrideHost host, StructuralFeature... path) {
        Convert c = create(null, List.of(path));
        host.getConvert().add(c);
        return c;
    }

    private static Convert create(Converter converter, List<StructuralFeature> path) {
        Convert c = CWMORMFactory.eINSTANCE.createConvert();
        c.getCwmPath().addAll(path);
        if (converter != null) {
            c.setConverter(converter);
        } else {
            c.setDisableConversion(true);
        }
        c.setName(!path.isEmpty() ? OrmBindings.path(path)
                : converter != null ? converter.getName() : "disableConversion");
        return c;
    }
}
