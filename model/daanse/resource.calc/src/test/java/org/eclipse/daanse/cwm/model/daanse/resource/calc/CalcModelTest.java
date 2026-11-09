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

import java.nio.file.Path;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.ECrossReferenceAdapter;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Checks the Calc model against the checklist in
 * {@code model/CWM-MODELLING-GUIDE.md}, section 8.
 */
class CalcModelTest {

    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";

    private final CalcFactory calc = CalcFactory.eINSTANCE;

    @TempDir
    Path tempDir;

    // ----------------------------------------------------------------- Model

    @Test
    void packageDeclaresHiddenOpposites() {
        assertThat(CalcPackage.eINSTANCE.getEAnnotation(
                "http://www.eclipse.org/emf/2002/Ecore/OCL").getDetails().get("hiddenOpposites"))
                .isEqualTo("true");
    }

    /**
     * Guide 2.4: eOpposite only where both ends are declared. That is nowhere
     * the case in this model - every opposite end is carried as
     * Property.oppositeRoleName.
     */
    @Test
    void everyReferenceIsOneWayAndNamesItsOppositeRole() {
        int hidden = 0;
        for (var classifier : CalcPackage.eINSTANCE.getEClassifiers()) {
            if (!(classifier instanceof org.eclipse.emf.ecore.EClass ec)) {
                continue;
            }
            for (EReference ref : ec.getEReferences()) {
                assertThat(ref.getEOpposite())
                        .as("%s.%s carries an eOpposite", ec.getName(), ref.getName())
                        .isNull();
                if (ref.isContainment()) {
                    continue;
                }
                var a = ref.getEAnnotation(EMOF);
                assertThat(a)
                        .as("%s.%s without Property.oppositeRoleName", ec.getName(), ref.getName())
                        .isNotNull();
                assertThat(a.getDetails().get("Property.oppositeRoleName")).isNotBlank();
                assertThat(ref.getEAnnotation(EMOF + "#Property.oppositeRoleName")
                        .getDetails().get("body"))
                        .isEqualTo(a.getDetails().get("Property.oppositeRoleName"));
                hidden++;
            }
        }
        assertThat(hidden).isEqualTo(3);
    }

    // --------------------------------------------------------------- Regions

    /**
     * The central requirement: a region always has a beginning, but not
     * necessarily an end.
     */
    @Test
    void aRangeMayBeOpenEnded() {
        CellRange open = calc.createCellRange();
        open.setFirstRow(3);
        open.setFirstColumn(0);

        assertThat(open.isSetLastRow()).isFalse();
        assertThat(open.getLastRow()).isEqualTo(-1L);
        assertThat(open.isSetLastColumn()).isFalse();

        CellRange bounded = calc.createCellRange();
        bounded.setFirstRow(3);
        bounded.setFirstColumn(0);
        bounded.setLastRow(42);
        bounded.setLastColumn(5);

        assertThat(bounded.isSetLastRow()).isTrue();
        assertThat(bounded.getLastRow()).isEqualTo(42L);

        bounded.unsetLastRow();
        assertThat(bounded.isSetLastRow()).isFalse();
        assertThat(bounded.getLastRow()).isEqualTo(-1L);
    }

    /**
     * A sheet carries several regions of differing structure; only their
     * position says which one is meant.
     */
    @Test
    void oneSheetCarriesSeveralSectionsOfDifferentShape() {
        Sheet sheet = newSheet("Umsatz", 0);

        TableSection sales = tableSection("Verkaeufe", 4, 0, 999, 3);
        MatrixSection matrix = calc.createMatrixSection();
        matrix.setName("Kreuztabelle");
        matrix.setKind(SectionKind.CALC_PLAIN_REGION);
        matrix.setRange(range(4, 6, 20, 12));
        matrix.setRowLabelRange(range(5, 6, 20, 6));
        matrix.setColumnLabelRange(range(4, 7, 4, 12));

        TableSection headline = calc.createTableSection();
        headline.setName("Kopfblock");
        headline.setKind(SectionKind.CALC_FREEFORM);
        headline.setRange(range(0, 0, 2, 12));

        sheet.getOwnedElement().add(sales);
        sheet.getOwnedElement().add(matrix);
        sheet.getOwnedElement().add(headline);

        assertThat(sheet.getOwnedElement()).hasSize(3);
        // The open region grows downwards, the header block does not.
        TableSection growing = tableSection("Eingaben", 30, 0, -1, 3);
        growing.getRange().unsetLastRow();
        assertThat(growing.getRange().isSetLastRow()).isFalse();
        assertThat(headline.getRange().isSetLastRow()).isTrue();
    }

    /** Column order lives in the inherited, ordered Classifier::feature. */
    @Test
    void columnOrderIsCarriedByTheInheritedFeatureList() {
        TableSection s = tableSection("Verkaeufe", 4, 0, 999, 2);
        SheetColumn tag = column("TAG", 0, CellValueType.CALC_DATE);
        SheetColumn artikel = column("ARTIKEL", 1, CellValueType.CALC_STRING);
        SheetColumn betrag = column("BETRAG", 2, CellValueType.CALC_CURRENCY);
        s.getFeature().add(tag);
        s.getFeature().add(artikel);
        s.getFeature().add(betrag);

        assertThat(s.getFeature()).containsExactly(tag, artikel, betrag);
        EStructuralFeature f = CalcPackage.eINSTANCE.getSection().getEStructuralFeature("range");
        assertThat(f.isMany()).isFalse();
    }

    // --------------------------------------------------- Cells and round trip

    @Test
    void savesAndReloadsWithoutUnresolvedProxies() throws Exception {
        Workbook wb = calc.createWorkbook();
        wb.setName("quartal.ods");
        wb.setFormat(WorkbookFormat.CALC_ODS);
        wb.setFormulaLanguage(FormulaLanguage.CALC_OPEN_FORMULA);
        wb.setDateSystem(DateSystem.CALC_DATE1900);

        Sheet sheet = newSheet("Umsatz", 0);
        wb.getOwnedElement().add(sheet);

        TableSection s = tableSection("Verkaeufe", 4, 0, -1, 2);
        s.getRange().unsetLastRow();
        SheetColumn betrag = column("BETRAG", 2, CellValueType.CALC_CURRENCY);
        betrag.setTotalsFunction(TotalsFunction.CALC_SUM);
        s.getFeature().add(betrag);
        sheet.getOwnedElement().add(s);

        NamedRange named = calc.createNamedRange();
        named.setName("Verkaufsdaten");
        named.setScope(NameScope.CALC_WORKBOOK);
        named.setIsNamedExpression(false);
        named.setRange(range(4, 0, -1, 2));
        named.getRange().unsetLastRow();
        named.setDesignatedSection(s);
        wb.getOwnedElement().add(named);

        SheetData data = calc.createSheetData();
        data.setName("Umsatz-Daten");
        data.setSheet(sheet);

        Row row = calc.createRow();
        row.setName("z5");
        row.setRowIndex(5);
        row.setClassifier(s);
        Cell cell = calc.createCell();
        cell.setName("C6");
        cell.setRowIndex(5);
        cell.setColumnIndex(2);
        cell.setValueType(CellValueType.CALC_CURRENCY);
        cell.setDataValue("1234.50");
        cell.setFormula("=SUM([.C2:.C4])");
        cell.setFeature(betrag);
        row.getSlot().add(cell);
        data.getOwnedElement().add(row);

        ResourceSet set = newResourceSet();
        Resource res = set.createResource(
                URI.createFileURI(tempDir.resolve("quartal.xmi").toFile().getAbsolutePath()));
        res.getContents().add(wb);
        res.getContents().add(data);
        res.save(Map.of());

        ResourceSet reloadSet = newResourceSet();
        Resource reloaded = reloadSet.getResource(res.getURI(), true);
        EcoreUtil.resolveAll(reloadSet);
        assertThat(EcoreUtil.UnresolvedProxyCrossReferencer.find(reloadSet)).isEmpty();

        Workbook back = (Workbook) reloaded.getContents().get(0);
        Sheet backSheet = (Sheet) back.getOwnedElement().get(0);
        TableSection backSection = (TableSection) backSheet.getOwnedElement().get(0);
        assertThat(backSection.getRange().isSetLastRow()).isFalse();
        assertThat(backSection.getRange().getFirstRow()).isEqualTo(4L);

        SheetData backData = (SheetData) reloaded.getContents().get(1);
        Row backRow = (Row) backData.getOwnedElement().get(0);
        Cell backCell = (Cell) backRow.getSlot().get(0);
        assertThat(backCell.getDataValue()).isEqualTo("1234.50");
        assertThat(backCell.getFeature()).isSameAs(backSection.getFeature().get(0));
    }

    /**
     * Guide 7.1: without an {@code ECrossReferenceAdapter} backward navigation
     * silently yields nothing.
     */
    @Test
    void backwardNavigationNeedsTheCrossReferenceIndex() {
        Sheet sheet = newSheet("Umsatz", 0);
        SheetData data = calc.createSheetData();
        data.setName("Umsatz-Daten");
        data.setSheet(sheet);

        ResourceSet set = newResourceSet();
        Resource res = set.createResource(URI.createFileURI("mem:/calc.xmi"));
        res.getContents().add(sheet);
        res.getContents().add(data);

        EReference sheetRef = CalcPackage.eINSTANCE.getSheetData_Sheet();
        assertThat(sheetRef.getEOpposite()).isNull();

        assertThat(referencing(set, sheet, sheetRef)).isEmpty();

        InverseReferences.install(set);
        assertThat(referencing(set, sheet, sheetRef)).containsExactly(data);
    }

    // --------------------------------------------------------------- Helpers

    private static java.util.List<EObject> referencing(ResourceSet set, EObject target,
            EReference ref) {
        ECrossReferenceAdapter adapter = ECrossReferenceAdapter.getCrossReferenceAdapter(set);
        if (adapter == null) {
            return java.util.List.of();
        }
        return adapter.getInverseReferences(target, true).stream()
                .filter(s -> s.getEStructuralFeature() == ref)
                .map(org.eclipse.emf.ecore.EStructuralFeature.Setting::getEObject)
                .toList();
    }

    private static ResourceSet newResourceSet() {
        ResourceSet set = new ResourceSetImpl();
        set.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
        return set;
    }

    private Sheet newSheet(String name, int position) {
        Sheet sheet = calc.createSheet();
        sheet.setName(name);
        sheet.setPosition(position);
        sheet.setSheetVisibility(SheetVisibility.CALC_VISIBLE);
        return sheet;
    }

    private CellRange range(int firstRow, int firstColumn, int lastRow, int lastColumn) {
        CellRange r = calc.createCellRange();
        r.setFirstRow(firstRow);
        r.setFirstColumn(firstColumn);
        r.setLastRow(lastRow);
        r.setLastColumn(lastColumn);
        return r;
    }

    private TableSection tableSection(String name, int firstRow, int firstColumn, int lastRow,
            int lastColumn) {
        TableSection s = calc.createTableSection();
        s.setName(name);
        s.setKind(SectionKind.CALC_DATABASE_RANGE);
        s.setOrientation(Orientation.CALC_COLUMNWISE);
        s.setContainsHeader(true);
        s.setRange(range(firstRow, firstColumn, lastRow, lastColumn));
        return s;
    }

    private SheetColumn column(String name, int index, CellValueType type) {
        SheetColumn c = calc.createSheetColumn();
        c.setName(name);
        c.setColumnIndex(index);
        c.setHeader(name);
        c.setValueType(type);
        return c;
    }
}
