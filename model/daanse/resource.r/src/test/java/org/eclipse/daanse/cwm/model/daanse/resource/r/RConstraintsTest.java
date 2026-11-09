/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.r;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.function.BiPredicate;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.daanse.resource.r.util.RValidator;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A valid and a violating case for each of the two OCL invariants. */
class RConstraintsTest {

    private static final RFactory RF = RFactory.eINSTANCE;

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void positiveDimensions() {
        RArrayType matrix = RF.createRArrayType();
        matrix.getDim().add(3L);
        matrix.getDim().add(4L);
        assertThat(severity((v, d) -> v.validateRArrayType_PositiveDimensions(matrix, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        matrix.getDim().add(-1L);
        assertThat(severity((v, d) -> v.validateRArrayType_PositiveDimensions(matrix, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void columnsAreRTypes() {
        RDataFrameType frame = RF.createRDataFrameType();
        RVectorType integers = RF.createRVectorType();
        integers.setSexpType(SexpType.RINTEGER);
        RComponent age = RF.createRComponent();
        age.setName("age");
        age.setType(integers);
        frame.getFeature().add(age);
        assertThat(severity((v, d) -> v.validateRDataFrameType_ColumnsAreRTypes(frame, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        RComponent foreign = RF.createRComponent();
        foreign.setName("foreign");
        foreign.setType(CoreFactory.eINSTANCE.createDataType());
        frame.getFeature().add(foreign);
        assertThat(severity((v, d) -> v.validateRDataFrameType_ColumnsAreRTypes(frame, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    private static int severity(BiPredicate<RValidator, BasicDiagnostic> check) {
        BasicDiagnostic diagnostic = new BasicDiagnostic();
        check.test(RValidator.INSTANCE, diagnostic);
        return diagnostic.getSeverity();
    }
}
