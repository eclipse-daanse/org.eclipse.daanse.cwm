/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.calc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.function.BiPredicate;

import org.eclipse.daanse.cwm.model.daanse.resource.calc.util.CalcValidator;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A valid and a violating case for each of the five OCL invariants. */
class CalcConstraintsTest {

    private static final CalcFactory CF = CalcFactory.eINSTANCE;

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void lastRowNotBeforeFirst() {
        CellRange range = range(2, 0, -1, 3);
        range.unsetLastRow();
        assertThat(severity((v, d) -> v.validateCellRange_LastRowNotBeforeFirst(range, d, new HashMap<>())))
                .as("open-ended").isEqualTo(Diagnostic.OK);
        range.setLastRow(5);
        assertThat(severity((v, d) -> v.validateCellRange_LastRowNotBeforeFirst(range, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        range.setLastRow(1);
        assertThat(severity((v, d) -> v.validateCellRange_LastRowNotBeforeFirst(range, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void lastColumnNotBeforeFirst() {
        CellRange range = range(0, 2, 9, 4);
        assertThat(severity((v, d) -> v.validateCellRange_LastColumnNotBeforeFirst(range, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        range.setLastColumn(1);
        assertThat(severity((v, d) -> v.validateCellRange_LastColumnNotBeforeFirst(range, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void originNotNegative() {
        CellRange range = range(0, 0, 9, 3);
        assertThat(severity((v, d) -> v.validateCellRange_OriginNotNegative(range, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        range.setFirstColumn(-1);
        assertThat(severity((v, d) -> v.validateCellRange_OriginNotNegative(range, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void headerFitsRange() {
        TableSection table = CF.createTableSection();
        table.setRange(range(0, 0, 9, 3));
        table.setHeaderRowCount(1);
        table.setTotalsRowCount(1);
        assertThat(severity((v, d) -> v.validateTableSection_HeaderFitsRange(table, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        table.getRange().setLastRow(0);
        assertThat(severity((v, d) -> v.validateTableSection_HeaderFitsRange(table, d, new HashMap<>())))
                .as("header and totals in a range of one row").isEqualTo(Diagnostic.ERROR);
        table.getRange().unsetLastRow();
        assertThat(severity((v, d) -> v.validateTableSection_HeaderFitsRange(table, d, new HashMap<>())))
                .as("open-ended").isEqualTo(Diagnostic.OK);
    }

    @Test
    void spansArePositive() {
        Cell cell = CF.createCell();
        assertThat(severity((v, d) -> v.validateCell_SpansArePositive(cell, d, new HashMap<>())))
                .as("one by one").isEqualTo(Diagnostic.OK);
        cell.setColumnsSpanned(0);
        assertThat(severity((v, d) -> v.validateCell_SpansArePositive(cell, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    private static CellRange range(long firstRow, long firstColumn, long lastRow, long lastColumn) {
        CellRange r = CF.createCellRange();
        r.setFirstRow(firstRow);
        r.setFirstColumn(firstColumn);
        r.setLastRow(lastRow);
        r.setLastColumn(lastColumn);
        return r;
    }

    private static int severity(BiPredicate<CalcValidator, BasicDiagnostic> check) {
        BasicDiagnostic diagnostic = new BasicDiagnostic();
        check.test(CalcValidator.INSTANCE, diagnostic);
        return diagnostic.getSeverity();
    }
}
