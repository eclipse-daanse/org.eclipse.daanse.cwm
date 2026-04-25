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
package org.eclipse.daanse.cwm.util.objectmodel.core;

import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;

public final class Features {

    private Features() {
    }

    /** Owning {@link Classifier}, or empty if the feature is detached. */
    public static Optional<Classifier> owner(Feature f) {
        return f == null ? Optional.empty() : Optional.ofNullable(f.getOwner());
    }
}
