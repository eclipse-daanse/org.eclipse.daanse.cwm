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
package org.eclipse.daanse.cwm.model.cwm.resource.record;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;

import org.eclipse.daanse.cwm.model.cwm.resource.record.util.RecordValidator;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * C-2 and C-3 of Field through the OCL validation delegate. Length, precision
 * and scale are optional Integers, mapped to the primitive long: 0 is "not
 * specified".
 */
class FieldConstraintsTest {

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void noSizeGiven() {
        Field field = RecordFactory.eINSTANCE.createField();
        assertThat(scaleRequiresPrecision(field).getSeverity()).isEqualTo(Diagnostic.OK);
        assertThat(precisionExcludesLength(field).getSeverity()).isEqualTo(Diagnostic.OK);
    }

    @Test
    void characterFieldWithLength() {
        Field field = RecordFactory.eINSTANCE.createField();
        field.setLength(50);
        assertThat(scaleRequiresPrecision(field).getSeverity()).isEqualTo(Diagnostic.OK);
        assertThat(precisionExcludesLength(field).getSeverity()).isEqualTo(Diagnostic.OK);
    }

    @Test
    void numericFieldWithPrecisionAndScale() {
        Field field = RecordFactory.eINSTANCE.createField();
        field.setPrecision(10);
        field.setScale(2);
        assertThat(scaleRequiresPrecision(field).getSeverity()).isEqualTo(Diagnostic.OK);
        assertThat(precisionExcludesLength(field).getSeverity()).isEqualTo(Diagnostic.OK);
    }

    @Test
    void precisionWithLengthIsViolated() {
        Field field = RecordFactory.eINSTANCE.createField();
        field.setPrecision(10);
        field.setLength(50);
        assertThat(precisionExcludesLength(field).getChildren()).singleElement()
                .extracting(Diagnostic::getMessage).asString().contains("'PrecisionExcludesLength' constraint is violated");
    }

    @Test
    void scaleWithoutPrecisionIsViolated() {
        Field field = RecordFactory.eINSTANCE.createField();
        field.setScale(2);
        assertThat(scaleRequiresPrecision(field).getChildren()).singleElement()
                .extracting(Diagnostic::getMessage).asString().contains("'ScaleRequiresPrecision' constraint is violated");
    }

    private static BasicDiagnostic scaleRequiresPrecision(Field field) {
        BasicDiagnostic diagnostic = new BasicDiagnostic();
        RecordValidator.INSTANCE.validateField_ScaleRequiresPrecision(field, diagnostic, new HashMap<>());
        return diagnostic;
    }

    private static BasicDiagnostic precisionExcludesLength(Field field) {
        BasicDiagnostic diagnostic = new BasicDiagnostic();
        RecordValidator.INSTANCE.validateField_PrecisionExcludesLength(field, diagnostic, new HashMap<>());
        return diagnostic;
    }
}
