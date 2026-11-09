/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.function.BiPredicate;

import org.eclipse.daanse.cwm.model.daanse.resource.json.util.JsonValidator;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A valid and a violating case for each of the two OCL invariants. */
class JsonConstraintsTest {

    private static final JsonFactory JF = JsonFactory.eINSTANCE;

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void maxItemsNotBelowMinItems() {
        ArrayType array = JF.createArrayType();
        array.setMinItems(2);
        assertThat(severity((v, d) -> v.validateArrayType_MaxItemsNotBelowMinItems(array, d, new HashMap<>())))
                .as("no upper bound").isEqualTo(Diagnostic.OK);
        array.setMaxItems(5);
        assertThat(severity((v, d) -> v.validateArrayType_MaxItemsNotBelowMinItems(array, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        array.setMaxItems(1);
        assertThat(severity((v, d) -> v.validateArrayType_MaxItemsNotBelowMinItems(array, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void lengthsAreConsistent() {
        PrimitiveType text = JF.createPrimitiveType();
        text.setValueKind(JsonValueKind.JSON_STRING);
        text.setMinLength(3);
        text.setMaxLength(40);
        assertThat(severity((v, d) -> v.validatePrimitiveType_LengthsAreConsistent(text, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        text.setMaxLength(2);
        assertThat(severity((v, d) -> v.validatePrimitiveType_LengthsAreConsistent(text, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    private static int severity(BiPredicate<JsonValidator, BasicDiagnostic> check) {
        BasicDiagnostic diagnostic = new BasicDiagnostic();
        check.test(JsonValidator.INSTANCE, diagnostic);
        return diagnostic.getSeverity();
    }
}
