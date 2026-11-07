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
package org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util;

import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;

/**
 * Creates {@code core::Model}s — the package kind CWM uses for "a whole
 * description of a system" (spec 4.3.2.x, ObjectModel Core).
 *
 * <p>
 * Creation rule shared by every {@code create} helper of the model modules:
 * {@code Xs.create(parent, name, ...)} creates exactly one element, sets its
 * {@code name} and the attributes the parameters carry, attaches it to
 * {@code parent} through the containment reference the Ecore defines for that
 * class, and returns it. {@code create} never upserts; upserting helpers are
 * verbs ({@code describe}, {@code set}, {@code ownedIn}). Referenced elements
 * must be part of the same model when it is saved — the helpers do not check
 * that, the XMI save does.
 * </p>
 */
public final class Models {

    private Models() {
    }

    /** A root Model, owned by no namespace. */
    public static Model create(String name) {
        Objects.requireNonNull(name, "name");
        Model model = CoreFactory.eINSTANCE.createModel();
        model.setName(name);
        return model;
    }

    /** A Model owned by {@code parent} via {@code ownedElement}. */
    public static Model create(Namespace parent, String name) {
        Objects.requireNonNull(parent, "parent");
        Model model = create(name);
        parent.getOwnedElement().add(model);
        return model;
    }
}
