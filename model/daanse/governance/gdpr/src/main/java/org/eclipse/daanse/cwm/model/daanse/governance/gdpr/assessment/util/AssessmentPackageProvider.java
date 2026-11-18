/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.gdpr.assessment.util;

import org.eclipse.daanse.cwm.emf.standalone.api.EPackageProvider;
import org.eclipse.daanse.cwm.model.daanse.governance.gdpr.assessment.AssessmentPackage;
import org.eclipse.emf.ecore.EPackage;

/** Gives {@link AssessmentPackage} to a plain Java program that registers the models on its class path. */
public final class AssessmentPackageProvider implements EPackageProvider {

    @Override
    public EPackage ePackage() {
        return AssessmentPackage.eINSTANCE;
    }
}
