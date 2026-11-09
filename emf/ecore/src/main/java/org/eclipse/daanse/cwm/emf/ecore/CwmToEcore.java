/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.emf.ecore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

/**
 * Converts a CWM class model into Ecore — one way: the CWM model stays the source, the Ecore
 * model is derived from it and carries annotations that lead back ({@link CwmAnnotations}).
 *
 * <p>Converted are the packages that hold classes, enumerations or data types the classes use
 * (and the packages nested in those). Relational catalogs and schemas, record files, OLAP and every
 * other {@code core::Package} subtype are left alone; packages that only contain packages are
 * passed through, not mapped; a package of data types only record fields or columns use stays
 * out. What cannot be expressed in Ecore, or leads out of the converted packages, is reported as a
 * {@link Note}.</p>
 *
 * <pre>{@code
 * Result r = CwmToEcore.convert(CwmToEcore.packagesOf(resource), Options.defaults());
 * if (!r.hasErrors()) {
 *     EcoreWriter.save(r, outputDir, null);
 * }
 * }</pre>
 */
public final class CwmToEcore {

    private CwmToEcore() {
    }

    /** Converts the class model below {@code roots}. */
    public static Result convert(Collection<? extends Package> roots, Options options) {
        return new Conversion(options == null ? Options.defaults() : options).run(roots);
    }

    /** Converts the class model below {@code roots} with the default options. */
    public static Result convert(Collection<? extends Package> roots) {
        return convert(roots, Options.defaults());
    }

    /** The CWM packages among the root objects of a resource. */
    public static List<Package> packagesOf(Resource resource) {
        List<Package> out = new ArrayList<>();
        for (EObject o : resource.getContents()) {
            if (o instanceof Package p) {
                out.add(p);
            }
        }
        return out;
    }
}
