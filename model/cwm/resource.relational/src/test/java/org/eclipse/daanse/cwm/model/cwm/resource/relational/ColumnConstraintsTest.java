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
package org.eclipse.daanse.cwm.model.cwm.resource.relational;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalValidator;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * C-3 of Column through the OCL validation delegate. Precision and scale are
 * optional Integers, mapped to the primitive long: 0 is "not specified".
 */
class ColumnConstraintsTest {

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void noSizeGiven() {
        assertThat(scaleRequiresPrecision(RelationalFactory.eINSTANCE.createColumn()).getSeverity())
                .isEqualTo(Diagnostic.OK);
    }

    @Test
    void precisionAndScale() {
        Column column = RelationalFactory.eINSTANCE.createColumn();
        column.setPrecision(10);
        column.setScale(2);
        assertThat(scaleRequiresPrecision(column).getSeverity()).isEqualTo(Diagnostic.OK);
    }

    @Test
    void scaleWithoutPrecisionIsViolated() {
        Column column = RelationalFactory.eINSTANCE.createColumn();
        column.setScale(2);
        assertThat(scaleRequiresPrecision(column).getChildren()).singleElement()
                .extracting(Diagnostic::getMessage).asString().contains("'ScaleRequiresPrecision' constraint is violated");
    }

    private static BasicDiagnostic scaleRequiresPrecision(Column column) {
        BasicDiagnostic diagnostic = new BasicDiagnostic();
        RelationalValidator.INSTANCE.validateColumn_ScaleRequiresPrecision(column, diagnostic, new HashMap<>());
        return diagnostic;
    }
}
