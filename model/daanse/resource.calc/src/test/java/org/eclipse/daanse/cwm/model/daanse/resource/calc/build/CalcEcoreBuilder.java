/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.calc.build;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

/**
 * Programmatic builder for the {@code calc} metamodel. The Calc package describes the structure of spreadsheets - OpenDocument Calc (OASIS ODF, ISO 26300) and OOXML SpreadsheetML (Excel) - and of the data held in them.

What separates a spreadsheet from a CSV file.
 *
 * <p>Every classifier, feature, literal and annotation is created in the order of
 * {@code model/calc.ecore}; the references into CWM are resolved in the CWM Ecores next to the
 * module. Construction and serialization follow the builders of the other Daanse models.</p>
 */
public final class CalcEcoreBuilder {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    /** The namespace URI of the package. */
    public static final String NS_URI = "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.resource.calc";
    private static final String CONSTRAINTS_NS = NS_URI + "/constraints";
    private static final String CORE = "http://www.omg.org/spec/CWM/1.1/objectmodel/core";
    private static final String INSTANCE = "http://www.omg.org/spec/CWM/1.1/objectmodel/instance";

    private static final String EPL_HEADER = String.join("\n",
            "Copyright (c) 2026 Contributors to the Eclipse Foundation.",
            "",
            "This program and the accompanying materials are made",
            "available under the terms of the Eclipse Public License 2.0",
            "which is available at https://www.eclipse.org/legal/epl-2.0/",
            "",
            "SPDX-License-Identifier: EPL-2.0");

    private final EcoreFactory ef = EcoreFactory.eINSTANCE;
    private final Map<String, EPackage> cwm;

    /** A builder over the CWM packages it refers to, by namespace URI. */
    public CalcEcoreBuilder(Map<String, EPackage> cwm) {
        this.cwm = cwm;
    }

    /**
     * Builds the package from the CWM Ecores next to {@code moduleDir} (module directory
     * {@code model/daanse/resource.calc}) and writes it to {@code outFile}.
     */
    public static void generate(File moduleDir, File outFile) throws IOException {
        File cwmDir = new File(moduleDir, "../../cwm").getCanonicalFile();
        ResourceSet rs = newResourceSet();
        Map<String, EPackage> cwm = new HashMap<>();
        load(rs, cwm, cwmDir, "objectmodel.core/model/core.ecore");
        load(rs, cwm, cwmDir, "objectmodel.instance/model/instance.ecore");
        outFile.getParentFile().mkdirs();
        save(new CalcEcoreBuilder(cwm).build(), rs, URI.createFileURI(outFile.getAbsolutePath()));
    }

    /** Creates the EPackage {@code calc}. */
    public EPackage build() {
        EPackage p = ef.createEPackage();
        p.setName("calc");
        p.setNsURI(NS_URI);
        p.setNsPrefix("calc");
        ann(p, ECORE,
                "invocationDelegates", ECORE_OCL,
                "settingDelegates", ECORE_OCL,
                "validationDelegates", ECORE_OCL);
        ann(p, ECORE_OCL,
                "hiddenOpposites", "true");
        ann(p, GENMODEL,
                "documentation", "The Calc package describes the structure of spreadsheets - OpenDocument Calc (OASIS "
                    + "ODF, ISO 26300) and OOXML SpreadsheetML (Excel) - and of the data held in them.\n\nWhat "
                    + "separates a spreadsheet from a CSV file. Both carry column-shaped data, and the "
                    + "individual column is an objectmodel/core::Attribute in either case. Three things are "
                    + "added, though. First, one file contains several sheets. Second, a sheet is a "
                    + "two-dimensional grid on which several mutually independent regions of quite different "
                    + "shape may lie side by side or one below the other - a header block, a table below it, "
                    + "a cross tabulation beside it. Third, position is therefore part of the structure "
                    + "rather than incidental: only the row and column number says which region is meant. A "
                    + "stream of records needs none of this, which is why nothing here inherits from "
                    + "resource/record::RecordDef - its mandatory attributes fieldDelimiter and "
                    + "textDelimiter describe delimiters, whereas in a spreadsheet the grid itself does the "
                    + "separating.\n\nOpen-ended regions. A region need not have an end. A database or list "
                    + "table grows downwards as rows are appended, and its last row is not fixed at "
                    + "description time. That is why lastRow and lastColumn on CellRange are unsettable and "
                    + "default to -1: where the value is unset, the region is open in that direction. "
                    + "firstRow and firstColumn, by contrast, are always fixed - a region always has a "
                    + "beginning.\n\nCell contents. In ODF, table:table-cell carries the content through "
                    + "office:value-type and office:value, the formula through table:formula, and the "
                    + "merging of several cells through table:number-columns-spanned and "
                    + "table:number-rows-spanned; covered cells appear as table:covered-table-cell. In "
                    + "SpreadsheetML the cell stands as c with the A1 address in r and the type code in t, "
                    + "the value in v and the formula in f. Named regions are held by ODF as "
                    + "table:named-range or table:named-expression, and by SpreadsheetML as definedName. "
                    + "Structured regions are called table:database-range in ODF and table (a ListObject) "
                    + "with ref, headerRowCount and totalsRowCount in SpreadsheetML.\n\nFormulas are kept here "
                    + "as text, in the notation of the source file; which notation that is is stated by "
                    + "formulaLanguage on the Workbook. A parsed representation belongs in "
                    + "foundation/expressions rather than here.\n\nMapping onto CWM. The Workbook is an "
                    + "objectmodel/core::Package and so is the sheet - both are namespaces and carry their "
                    + "content through the inherited ownedElement. A region is an objectmodel/core::Class "
                    + "and carries its columns through the inherited, ordered containment reference "
                    + "Classifier::feature. A row is an objectmodel/instance::Object and a cell an "
                    + "objectmodel/instance::DataSlot - the same pair with which resource/relational maps "
                    + "Row and ColumnValue.\n\nSources: OASIS Open Document Format for Office Applications "
                    + "v1.2/v1.3 Part 1 (Schema) and Part 4 (OpenFormula); ECMA-376 Office Open XML, Part 1, "
                    + "SpreadsheetML.",
                "basePackage", "org.eclipse.daanse.cwm.model.daanse.resource",
                "prefix", "Calc",
                "oSGiCompatible", "true",
                "literalsInterface", "true",
                "loadInitialization", "false",
                "copyrightText", EPL_HEADER);

        // the classifiers first: they refer to each other
        EEnum workbookFormat = enm(p, "WorkbookFormat");
        EEnum sheetVisibility = enm(p, "SheetVisibility");
        EEnum cellValueType = enm(p, "CellValueType");
        EEnum sectionKind = enm(p, "SectionKind");
        EEnum orientation = enm(p, "Orientation");
        EEnum totalsFunction = enm(p, "TotalsFunction");
        EEnum formulaLanguage = enm(p, "FormulaLanguage");
        EEnum dateSystem = enm(p, "DateSystem");
        EEnum nameScope = enm(p, "NameScope");
        EClass cellRange = cls(p, "CellRange", false);
        EClass workbook = cls(p, "Workbook", false);
        EClass sheet = cls(p, "Sheet", false);
        EClass section = cls(p, "Section", true);
        EClass tableSection = cls(p, "TableSection", false);
        EClass matrixSection = cls(p, "MatrixSection", false);
        EClass pivotSection = cls(p, "PivotSection", false);
        EClass sheetColumn = cls(p, "SheetColumn", false);
        EClass namedRange = cls(p, "NamedRange", false);
        EClass sheetData = cls(p, "SheetData", false);
        EClass row = cls(p, "Row", false);
        EClass cell = cls(p, "Cell", false);
        EStructuralFeature f;
        EEnumLiteral l;

        // WorkbookFormat
        doc(workbookFormat, "The file format of the spreadsheet.");
        l = literal(workbookFormat, "calc_ods", 0);
        doc(l, "OpenDocument Spreadsheet (.ods), OASIS ODF / ISO 26300.");
        l = literal(workbookFormat, "calc_xlsx", 1);
        doc(l, "Office Open XML Spreadsheet (.xlsx), ECMA-376.");
        l = literal(workbookFormat, "calc_xlsm", 2);
        doc(l, "Office Open XML Spreadsheet with macros (.xlsm).");
        l = literal(workbookFormat, "calc_xlsb", 3);
        doc(l, "Office Open XML in a binary container (.xlsb).");
        l = literal(workbookFormat, "calc_xls", 4);
        doc(l, "The older binary Excel format (.xls, BIFF).");
        l = literal(workbookFormat, "calc_other", 5);
        doc(l, "Some other or unknown format.");

        // SheetVisibility
        doc(sheetVisibility, "Whether and how a sheet appears in the user interface.");
        l = literal(sheetVisibility, "calc_visible", 0);
        doc(l, "Visible.");
        l = literal(sheetVisibility, "calc_hidden", 1);
        doc(l, "Hidden, but can be shown again through the user interface.");
        l = literal(sheetVisibility, "calc_veryHidden", 2);
        doc(l, "Can be shown again only through program code. Excel calls this state veryHidden.");

        // CellValueType
        doc(cellValueType, "The value type of a cell. The literals unify office:value-type from ODF and the type "
                + "code t from SpreadsheetML.");
        l = literal(cellValueType, "calc_empty", 0);
        doc(l, "Empty - the cell carries no value.");
        l = literal(cellValueType, "calc_string", 1);
        doc(l, "Text.");
        l = literal(cellValueType, "calc_float", 2);
        doc(l, "A number.");
        l = literal(cellValueType, "calc_percentage", 3);
        doc(l, "A number to be read as a proportion.");
        l = literal(cellValueType, "calc_currency", 4);
        doc(l, "A monetary amount; the currency is part of the formatting.");
        l = literal(cellValueType, "calc_date", 5);
        doc(l, "A date, possibly with a time of day.");
        l = literal(cellValueType, "calc_time", 6);
        doc(l, "A duration or time of day.");
        l = literal(cellValueType, "calc_boolean", 7);
        doc(l, "A truth value.");
        l = literal(cellValueType, "calc_error", 8);
        doc(l, "An error value such as #DIV/0! or #N/A, arising from the evaluation of a formula.");

        // SectionKind
        doc(sectionKind, "Where a region takes its delimitation from. This matters for how dependable its "
                + "bounds are: a region declared in the file is binding, a detected one is merely the "
                + "reading tool's conjecture.");
        l = literal(sectionKind, "calc_databaseRange", 0);
        doc(l, "Declared in ODF as table:database-range. Grows as rows are appended and therefore "
                + "often has no fixed end.");
        l = literal(sectionKind, "calc_listObject", 1);
        doc(l, "Declared in SpreadsheetML as a separate table part (a ListObject), with ref, "
                + "headerRowCount and totalsRowCount.");
        l = literal(sectionKind, "calc_namedRange", 2);
        doc(l, "Delimited through a named range.");
        l = literal(sectionKind, "calc_pivotTable", 3);
        doc(l, "The result of a pivot or data-pilot evaluation.");
        l = literal(sectionKind, "calc_labelRange", 4);
        doc(l, "Declared in ODF as table:label-range: labels that let formulas address rows or "
                + "columns.");
        l = literal(sectionKind, "calc_plainRegion", 5);
        doc(l, "Not declared in the file but detected by the reading tool from contiguously occupied "
                + "cells.");
        l = literal(sectionKind, "calc_freeform", 6);
        doc(l, "A region without column-shaped structure - a header block, a note field. Only its "
                + "position is described.");

        // Orientation
        doc(orientation, "The direction in which the like-kinded values of a region lie.");
        l = literal(orientation, "calc_columnwise", 0);
        doc(l, "Column-wise: one attribute per column, one record per row. The regular case and the "
                + "shape a CSV file has as well.");
        l = literal(orientation, "calc_rowwise", 1);
        doc(l, "Row-wise: one attribute per row, one record per column. The transposed case, declared "
                + "in ODF through table:orientation.");

        // TotalsFunction
        doc(totalsFunction, "The function with which a column is aggregated in the totals row; in SpreadsheetML "
                + "the attribute totalsRowFunction.");
        l = literal(totalsFunction, "calc_none", 0);
        doc(l, "No aggregation.");
        l = literal(totalsFunction, "calc_sum", 1);
        doc(l, "Sum.");
        l = literal(totalsFunction, "calc_min", 2);
        doc(l, "Smallest value.");
        l = literal(totalsFunction, "calc_max", 3);
        doc(l, "Largest value.");
        l = literal(totalsFunction, "calc_average", 4);
        doc(l, "Mean.");
        l = literal(totalsFunction, "calc_count", 5);
        doc(l, "Number of occupied cells.");
        l = literal(totalsFunction, "calc_countNums", 6);
        doc(l, "Number of cells holding numbers.");
        l = literal(totalsFunction, "calc_stdDev", 7);
        doc(l, "Standard deviation.");
        l = literal(totalsFunction, "calc_var", 8);
        doc(l, "Variance.");
        l = literal(totalsFunction, "calc_custom", 9);
        doc(l, "A custom formula; it is held in totalsFormula.");

        // FormulaLanguage
        doc(formulaLanguage, "The notation in which the formulas of the file are written.");
        l = literal(formulaLanguage, "calc_openFormula", 0);
        doc(l, "OpenFormula per ODF Part 4, with addresses of the shape [Sheet1.A1:.B5].");
        l = literal(formulaLanguage, "calc_excelA1", 1);
        doc(l, "Excel in A1 notation, for example Sheet1!$A$1:$B$5.");
        l = literal(formulaLanguage, "calc_excelR1C1", 2);
        doc(l, "Excel in R1C1 notation, with references relative to the cell.");

        // DateSystem
        doc(dateSystem, "The epoch against which date numbers are to be computed. Confusing the two shifts "
                + "every date in the file by four years.");
        l = literal(dateSystem, "calc_date1900", 0);
        doc(l, "The count from 30 December 1899. The default of Excel on Windows and of ODF; for "
                + "compatibility it includes the 29th of February 1900, a day that never existed.");
        l = literal(dateSystem, "calc_date1904", 1);
        doc(l, "The count from 1 January 1904. The default of the former Excel for Macintosh.");

        // NameScope
        doc(nameScope, "The scope in which a named range is valid.");
        l = literal(nameScope, "calc_workbook", 0);
        doc(l, "Valid throughout the file.");
        l = literal(nameScope, "calc_sheet", 1);
        doc(l, "Valid on one sheet only. The same name may then denote something else on other sheets.");

        // CellRange
        ann(cellRange, ECORE,
                "constraints", "LastRowNotBeforeFirst LastColumnNotBeforeFirst OriginNotNegative");
        ann(cellRange, ECORE_OCL,
                "LastRowNotBeforeFirst", "self.lastRow < 0 or self.lastRow >= self.firstRow",
                "LastColumnNotBeforeFirst", "self.lastColumn < 0 or self.lastColumn >= self.firstColumn",
                "OriginNotNegative", "self.firstRow >= 0 and self.firstColumn >= 0");
        ann(cellRange, CONSTRAINTS_NS,
                "LastRowNotBeforeFirst.name", "Last Row Not Before First",
                "LastRowNotBeforeFirst.description", "Where the region has a lower end, it does not lie above its beginning.",
                "LastRowNotBeforeFirst.level", "error",
                "LastColumnNotBeforeFirst.name", "Last Column Not Before First",
                "LastColumnNotBeforeFirst.description", "Where the region has a right-hand end, it does not lie left of its beginning.",
                "LastColumnNotBeforeFirst.level", "error",
                "OriginNotNegative.name", "Origin Not Negative",
                "OriginNotNegative.description", "The top left corner of a region lies within the grid.",
                "OriginNotNegative.level", "error");
        doc(cellRange, "A rectangular section of a sheet, given by its top left corner and, where present, "
                + "its bottom right one. The end may be absent: a database table grows downwards, and "
                + "its last row is not fixed at description time. Where lastRow is unset, the region "
                + "extends open-ended downwards; where lastColumn is unset, to the right.\n\nInherits from "
                + "objectmodel/core::Element rather than ModelElement: a section has no name and belongs "
                + "to no namespace, it is contained by whatever needs it.");
        cellRange.getESuperTypes().add((EClass) cwm(CORE, "Element"));
        f = attr(cellRange, "firstRow", cwm(CORE, "Integer"), 1, 1);
        doc(f, "The zero-based number of the first row. A region always has a beginning.");
        f = attr(cellRange, "firstColumn", cwm(CORE, "Integer"), 1, 1);
        doc(f, "The zero-based number of the first column.");
        f = attr(cellRange, "lastRow", cwm(CORE, "Integer"), 0, 1);
        f.setUnsettable(true);
        f.setDefaultValueLiteral("-1");
        doc(f, "The zero-based number of the last row. Unset (isSetLastRow() returns false, the "
                + "getter returns -1) when the region is open-ended downwards.");
        f = attr(cellRange, "lastColumn", cwm(CORE, "Integer"), 0, 1);
        f.setUnsettable(true);
        f.setDefaultValueLiteral("-1");
        doc(f, "The zero-based number of the last column. Unset (isSetLastColumn() returns false, the "
                + "getter returns -1) when the region is open-ended to the right.");
        f = attr(cellRange, "sheetName", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The sheet on which the region lies, where it is not the enclosing one. A named range "
                + "may point across sheet boundaries.");
        f = attr(cellRange, "address", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The address in the notation of the source file, that is, [Sheet1.A1:.D100] in ODF or "
                + "Sheet1!$A$1:$D$100 in SpreadsheetML. It is carried along so that the original "
                + "notation can be restored when writing back.");

        // Workbook
        doc(workbook, "A spreadsheet file. It is the namespace of its sheets and its named ranges; both are "
                + "the inherited ownedElement. There is deliberately no separate containment reference "
                + "for the sheets - an object can have only one container, and the order of the sheet "
                + "tabs is carried by Sheet::position.");
        workbook.getESuperTypes().add((EClass) cwm(CORE, "Package"));
        f = attr(workbook, "format", workbookFormat, 1, 1);
        doc(f, "The file format.");
        f = attr(workbook, "formulaLanguage", formulaLanguage, 1, 1);
        doc(f, "The notation in which the formulas of this file are written.");
        f = attr(workbook, "dateSystem", dateSystem, 1, 1);
        doc(f, "The epoch for date numbers.");
        f = attr(workbook, "isProtected", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when the structure of the file is protected and sheets can neither be created "
                + "nor removed nor renamed.");
        f = attr(workbook, "producer", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The producing program and its version, as far as the file records it.");

        // Sheet
        doc(sheet, "A single sheet: a two-dimensional grid of rows and columns. The regions lie on it, "
                + "and there may well be several of quite different shape. They are the inherited "
                + "ownedElement; their position is held in their respective CellRange.");
        sheet.getESuperTypes().add((EClass) cwm(CORE, "Package"));
        f = attr(sheet, "position", cwm(CORE, "Integer"), 1, 1);
        doc(f, "The zero-based position in the tab bar. It is carried as an attribute rather than "
                + "through an ordered containment reference, because the content is already held through "
                + "the inherited, unordered ownedElement.");
        f = attr(sheet, "sheetVisibility", sheetVisibility, 1, 1);
        doc(f, "Whether the sheet appears in the user interface. Deliberately not called visibility: "
                + "that name is already taken by the inherited mandatory attribute "
                + "ModelElement::visibility, which means visibility in the sense of the namespace and "
                + "not that of the sheet tab.");
        f = attr(sheet, "isProtected", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when the sheet is protected against changes.");
        f = attr(sheet, "usedRowCount", cwm(CORE, "Integer"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The number of rows up to the last occupied one, where known.");
        f = attr(sheet, "usedColumnCount", cwm(CORE, "Integer"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The number of columns up to the last occupied one, where known.");
        f = ref(sheet, "mergedRegion", cellRange, 0, -1);
        f.setOrdered(false);
        ((EReference) f).setContainment(true);
        doc(f, "The merged cell ranges of the sheet. The value of such a range is held in its top "
                + "left cell; the remaining ones are covered and appear in ODF as "
                + "table:covered-table-cell.");

        // Section
        doc(section, "A contiguous region of a sheet with a uniform structure. The region is the reason a "
                + "spreadsheet cannot be mapped as a single table: several regions of differing "
                + "structure may lie on one sheet, and only their position says which one is meant.");
        section.getESuperTypes().add((EClass) cwm(CORE, "Class"));
        f = ref(section, "range", cellRange, 1, 1);
        ((EReference) f).setContainment(true);
        doc(f, "The position of the region on the sheet. Its end may be left open.");
        f = attr(section, "kind", sectionKind, 1, 1);
        doc(f, "Where the delimitation comes from and how dependable it is.");
        f = attr(section, "isProtected", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when the region is protected against changes.");

        // TableSection
        ann(tableSection, ECORE,
                "constraints", "HeaderFitsRange");
        ann(tableSection, ECORE_OCL,
                "HeaderFitsRange", "self.range.lastRow < 0 or self.range.firstRow + self.headerRowCount + "
                    + "self.totalsRowCount <= self.range.lastRow + 1");
        ann(tableSection, CONSTRAINTS_NS,
                "HeaderFitsRange.name", "Header Fits Range",
                "HeaderFitsRange.description", "Where the region has a lower end, the header and totals rows fit into it together.",
                "HeaderFitsRange.level", "error");
        doc(tableSection, "A column-shaped region: like-kinded rows below a header row. This is the structure a "
                + "CSV file has as well, and the most frequent case. The columns are the inherited, "
                + "ordered features.");
        tableSection.getESuperTypes().add((EClass) section);
        f = attr(tableSection, "orientation", orientation, 1, 1);
        doc(f, "Whether the attributes sit in the columns or in the rows.");
        f = attr(tableSection, "containsHeader", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when the region carries a header row holding the column names.");
        f = attr(tableSection, "headerRowCount", cwm(CORE, "Integer"), 0, 1);
        f.setUnsettable(true);
        f.setDefaultValueLiteral("1");
        doc(f, "The number of header rows. In SpreadsheetML the default is one header row; multi-row "
                + "headers occur in hand-maintained sheets.");
        f = attr(tableSection, "totalsRowCount", cwm(CORE, "Integer"), 0, 1);
        f.setUnsettable(true);
        f.setDefaultValueLiteral("0");
        doc(f, "The number of totals rows at the lower edge. In SpreadsheetML the default is zero.");
        f = attr(tableSection, "hasFilterButtons", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when the header carries filter buttons.");

        // MatrixSection
        doc(matrixSection, "A cross tabulation: labels sit both at the left and at the top edge, with the values "
                + "between them. The structure cannot be read column-wise, because a column here is not "
                + "an attribute but a value of one. In ODF the two label regions are declared as "
                + "table:label-range.");
        matrixSection.getESuperTypes().add((EClass) section);
        f = ref(matrixSection, "rowLabelRange", cellRange, 0, 1);
        ((EReference) f).setContainment(true);
        doc(f, "The region holding the row labels.");
        f = ref(matrixSection, "columnLabelRange", cellRange, 0, 1);
        ((EReference) f).setContainment(true);
        doc(f, "The region holding the column labels.");
        f = attr(matrixSection, "rowLabelColumnCount", cwm(CORE, "Integer"), 1, 1);
        f.setDefaultValueLiteral("1");
        doc(f, "The number of columns at the left edge that carry labels.");
        f = attr(matrixSection, "columnLabelRowCount", cwm(CORE, "Integer"), 1, 1);
        f.setDefaultValueLiteral("1");
        doc(f, "The number of rows at the top edge that carry labels.");

        // PivotSection
        doc(pivotSection, "An aggregated region the application computes from other data - a "
                + "table:data-pilot-table in ODF, a PivotTable in SpreadsheetML. Its content is derived "
                + "and is rewritten on refresh; whoever reads it as a source reads a result and not base "
                + "data.");
        pivotSection.getESuperTypes().add((EClass) section);
        f = ref(pivotSection, "sourceRange", cellRange, 0, 1);
        ((EReference) f).setContainment(true);
        doc(f, "The region aggregated from, where the source lies in the same file.");
        f = ref(pivotSection, "sourceSection", section, 0, 1);
        oppositeRole(f, "pivotSection");
        doc(f, "The region aggregated from, where it is described as such. The opposite end is not a "
                + "feature: navigating from base data to the evaluations computed from it is not needed.");
        f = attr(pivotSection, "sourceDescription", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "A description of the source when it lies elsewhere - a database query, another file.");
        f = attr(pivotSection, "ignoresEmptyRows", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when empty rows of the source are skipped.");

        // SheetColumn
        doc(sheetColumn, "A column of a column-shaped region - or, with transposed orientation, a row. This is "
                + "the counterpart of resource/relational::Column and resource/record::Field. The type "
                + "is held by the inherited reference StructuralFeature::type; the order follows from "
                + "the ordered containment reference Classifier::feature.");
        sheetColumn.getESuperTypes().add((EClass) cwm(CORE, "Attribute"));
        f = attr(sheetColumn, "columnIndex", cwm(CORE, "Integer"), 1, 1);
        doc(f, "The zero-based position within the region, not within the sheet. The sheet column "
                + "follows by adding range.firstColumn.");
        f = attr(sheetColumn, "header", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The label from the header row. It may differ from the inherited mandatory attribute "
                + "name where that has been normalised into a valid identifier.");
        f = attr(sheetColumn, "valueType", cellValueType, 0, 1);
        f.setUnsettable(true);
        doc(f, "The value type the cells of this column carry. In a spreadsheet this is an "
                + "observation and not a guarantee: a column may be occupied by mixed types.");
        f = attr(sheetColumn, "numberFormat", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The formatting code of the column, for example 0.00 or YYYY-MM-DD. It does not decide "
                + "the value, but it does decide how the value is read - a date number without a format "
                + "is a number.");
        f = attr(sheetColumn, "formula", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The formula of a calculated column, in the notation of the source file.");
        f = attr(sheetColumn, "totalsFunction", totalsFunction, 0, 1);
        f.setUnsettable(true);
        doc(f, "The aggregation in the totals row.");
        f = attr(sheetColumn, "totalsFormula", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The custom formula of the totals row, where totalsFunction is calc_custom.");
        f = attr(sheetColumn, "isNullable", cwm(CORE, "Boolean"), 1, 1);
        f.setDefaultValueLiteral("true");
        doc(f, "True when cells of this column may be left empty.");

        // NamedRange
        doc(namedRange, "A named range or expression - table:named-range or table:named-expression in ODF, "
                + "definedName in SpreadsheetML. It belongs to the Workbook or to a sheet and is "
                + "frequently the only place where a region is delimited bindingly at all.");
        namedRange.getESuperTypes().add((EClass) cwm(CORE, "ModelElement"));
        f = ref(namedRange, "range", cellRange, 0, 1);
        ((EReference) f).setContainment(true);
        doc(f, "The designated region. Unset when the name denotes an expression rather than a region.");
        f = attr(namedRange, "expression", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The expression, where the name denotes a formula or a constant value rather than a "
                + "region.");
        f = attr(namedRange, "isNamedExpression", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when the name denotes an expression, false for a region.");
        f = attr(namedRange, "scope", nameScope, 1, 1);
        doc(f, "Whether the name is valid throughout the file or on one sheet only.");
        f = attr(namedRange, "baseCellAddress", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The cell against which relative references of the name are resolved; "
                + "table:base-cell-address in ODF. Without it a name holding relative references cannot "
                + "be evaluated.");
        f = ref(namedRange, "designatedSection", section, 0, 1);
        oppositeRole(f, "namedRange");
        doc(f, "The region this name denotes, where it is described as such. The opposite end is not "
                + "a feature: a region need not know the names pointing at it.");

        // SheetData
        doc(sheetData, "The actually occupied cells of a sheet. The rows are the inherited ownedElement.");
        sheetData.getESuperTypes().add((EClass) cwm(INSTANCE, "Extent"));
        f = ref(sheetData, "sheet", sheet, 1, 1);
        oppositeRole(f, "data");
        doc(f, "The sheet whose content is held here. The opposite end is not a feature: the "
                + "description of a sheet stays independent of whether and how often it is populated "
                + "with data.");

        // Row
        doc(row, "An occupied row within a column-shaped region. The describing region is held by the "
                + "inherited reference Instance::classifier, the cells by the inherited containment "
                + "reference Object::slot.");
        row.getESuperTypes().add((EClass) cwm(INSTANCE, "Object"));
        f = attr(row, "rowIndex", cwm(CORE, "Integer"), 1, 1);
        doc(f, "The zero-based row number on the sheet, not within the region.");
        f = attr(row, "isHidden", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when the row is hidden - by hand or by a filter.");

        // Cell
        ann(cell, ECORE,
                "constraints", "SpansArePositive");
        ann(cell, ECORE_OCL,
                "SpansArePositive", "self.columnsSpanned >= 1 and self.rowsSpanned >= 1");
        ann(cell, CONSTRAINTS_NS,
                "SpansArePositive.name", "Spans Are Positive",
                "SpansArePositive.description", "A cell extends across at least one row and one column.",
                "SpansArePositive.level", "error");
        doc(cell, "A single cell. The column it belongs to is held by the inherited mandatory reference "
                + "Slot::feature, the value by DataSlot::dataValue - the same pair with which "
                + "resource/relational maps a ColumnValue. A cell outside a column-shaped region is not "
                + "carried as a Cell: for such a region only the position is described.");
        cell.getESuperTypes().add((EClass) cwm(INSTANCE, "DataSlot"));
        f = attr(cell, "rowIndex", cwm(CORE, "Integer"), 1, 1);
        doc(f, "The zero-based row number on the sheet.");
        f = attr(cell, "columnIndex", cwm(CORE, "Integer"), 1, 1);
        doc(f, "The zero-based column number on the sheet.");
        f = attr(cell, "valueType", cellValueType, 1, 1);
        doc(f, "The value type of this cell. It sits on the cell and not only on the column, because "
                + "a column may be occupied by mixed types.");
        f = attr(cell, "formula", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The formula of the cell in the notation of the source file. The value held in "
                + "dataValue is then the result computed last and may be stale.");
        f = attr(cell, "formattedText", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The displayed text as the formatting produces it. It may differ considerably from the "
                + "value - a date number appears as a date, a rounded number with fewer digits.");
        f = attr(cell, "columnsSpanned", cwm(CORE, "Integer"), 1, 1);
        f.setDefaultValueLiteral("1");
        doc(f, "The number of columns this cell is merged across; one for an unmerged cell.");
        f = attr(cell, "rowsSpanned", cwm(CORE, "Integer"), 1, 1);
        f.setDefaultValueLiteral("1");
        doc(f, "The number of rows this cell is merged across; one for an unmerged cell.");
        f = attr(cell, "isCovered", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when the cell is covered by a merged cell and carries no value itself; "
                + "table:covered-table-cell in ODF.");
        f = attr(cell, "annotation", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The text of a comment attached to the cell.");

        return p;
    }

    private EClassifier cwm(String nsUri, String name) {
        EClassifier c = cwm.get(nsUri).getEClassifier(name);
        if (c == null) {
            throw new IllegalStateException("CWM classifier not found: " + name + " in " + nsUri);
        }
        return c;
    }

    private EClass cls(EPackage pkg, String name, boolean isAbstract) {
        EClass c = ef.createEClass();
        c.setName(name);
        c.setAbstract(isAbstract);
        pkg.getEClassifiers().add(c);
        return c;
    }

    private EEnum enm(EPackage pkg, String name) {
        EEnum e = ef.createEEnum();
        e.setName(name);
        pkg.getEClassifiers().add(e);
        return e;
    }

    private EEnumLiteral literal(EEnum e, String name, int value) {
        EEnumLiteral l = ef.createEEnumLiteral();
        l.setName(name);
        l.setValue(value);
        e.getELiterals().add(l);
        return l;
    }

    private EAttribute attr(EClass owner, String name, EClassifier type, int lower, int upper) {
        EAttribute a = ef.createEAttribute();
        a.setName(name);
        a.setEType(type);
        a.setLowerBound(lower);
        a.setUpperBound(upper);
        owner.getEStructuralFeatures().add(a);
        return a;
    }

    private EReference ref(EClass owner, String name, EClassifier type, int lower, int upper) {
        EReference r = ef.createEReference();
        r.setName(name);
        r.setEType(type);
        r.setLowerBound(lower);
        r.setUpperBound(upper);
        owner.getEStructuralFeatures().add(r);
        return r;
    }

    /** An annotation with its details, given as key and value in turn. */
    private EAnnotation ann(EModelElement el, String source, String... keysAndValues) {
        EAnnotation a = ef.createEAnnotation();
        a.setSource(source);
        for (int i = 0; i < keysAndValues.length; i += 2) {
            a.getDetails().put(keysAndValues[i], keysAndValues[i + 1]);
        }
        el.getEAnnotations().add(a);
        return a;
    }

    private void doc(EModelElement el, String documentation) {
        ann(el, GENMODEL, "documentation", documentation);
    }

    /** {@code Property.oppositeRoleName} pair (convention for hidden association ends). */
    private void oppositeRole(EModelElement el, String roleName) {
        ann(el, EMOF, "Property.oppositeRoleName", roleName);
        ann(el, EMOF + "#Property.oppositeRoleName", "body", roleName);
    }

    /** A resource set reading {@code .ecore} files. */
    public static ResourceSet newResourceSet() {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
        return rs;
    }

    /**
     * Loads a CWM ecore; its resource URI becomes the nsURI, so that references serialize as
     * {@code <nsURI>#//Class}.
     */
    private static void load(ResourceSet rs, Map<String, EPackage> cwm, File cwmDir, String path) {
        File f = new File(cwmDir, path);
        if (!f.isFile()) {
            throw new IllegalStateException("CWM ecore not found: " + f);
        }
        Resource res = rs.getResource(URI.createFileURI(f.getAbsolutePath()), true);
        EPackage pkg = (EPackage) res.getContents().get(0);
        res.setURI(URI.createURI(pkg.getNsURI()));
        rs.getPackageRegistry().put(pkg.getNsURI(), pkg);
        cwm.put(pkg.getNsURI(), pkg);
    }

    /** Writes the package as UTF-8 with the EPL header after the XML declaration. */
    public static void save(EPackage pkg, ResourceSet rs, URI outUri) throws IOException {
        Resource out = rs.createResource(outUri);
        out.getContents().add(pkg);
        Map<Object, Object> opts = new HashMap<>();
        opts.put(XMLResource.OPTION_ENCODING, "UTF-8");
        opts.put(XMLResource.OPTION_LINE_WIDTH, Integer.valueOf(96));
        out.save(opts);
        Path file = Path.of(outUri.toFileString());
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String comment = "<!--\n"
                + "/*********************************************************************\n"
                + "* Copyright (c) 2026 Contributors to the Eclipse Foundation.\n"
                + "*\n"
                + "* This program and the accompanying materials are made\n"
                + "* available under the terms of the Eclipse Public License 2.0\n"
                + "* which is available at https://www.eclipse.org/legal/epl-2.0/\n"
                + "*\n"
                + "* SPDX-License-Identifier: EPL-2.0\n"
                + "**********************************************************************/\n"
                + "-->\n";
        int nl = content.indexOf('\n');
        Files.writeString(file, content.substring(0, nl + 1) + comment + content.substring(nl + 1),
                StandardCharsets.UTF_8);
    }
}
