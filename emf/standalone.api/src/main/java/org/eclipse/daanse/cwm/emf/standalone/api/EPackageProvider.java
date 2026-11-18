/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.emf.standalone.api;

import org.eclipse.emf.ecore.EPackage;

/**
 * Gives the generated {@link EPackage} of a model module to a plain Java program. A model module implements it once
 * per package and names the implementation in {@code META-INF/services/} under the name of this interface, so that
 * {@link java.util.ServiceLoader} finds every model on the class path without the program naming it. Under OSGi a
 * model registers itself through its generated configurator and this interface is not used.
 */
public interface EPackageProvider {

    /**
     * The generated package of the model; reading it initialises the package, as reading its {@code eINSTANCE}
     * does.
     */
    EPackage ePackage();
}
