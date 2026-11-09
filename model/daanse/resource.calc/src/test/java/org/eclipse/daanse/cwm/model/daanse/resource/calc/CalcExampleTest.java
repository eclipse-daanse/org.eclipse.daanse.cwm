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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ShortIds;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;

/** The example of the README: a workbook whose sheet holds a table with three columns. */
class CalcExampleTest {

    private static final CalcFactory F = CalcFactory.eINSTANCE;

    /** Lines of the XMI {@link #aWorkbookWithATableOnASheet()} writes. */
    private static final String XMI = """
            <ownedElement xsi:type="core:DataType" name="String"/>
            <ownedElement xsi:type="core:DataType" name="Double"/>
            <ownedElement xsi:type="calc:Sheet" name="orders">
            <ownedElement xsi:type="calc:TableSection" name="orders" kind="calc_listObject">
            <feature xsi:type="calc:SheetColumn" name="order_no" type="_Double" header="Order" \
            valueType="calc_float"/>
            <feature xsi:type="calc:SheetColumn" name="customer" type="_String" columnIndex="1" \
            header="Customer" valueType="calc_string"/>
            <feature xsi:type="calc:SheetColumn" name="amount" type="_Double" columnIndex="2" \
            header="Amount" valueType="calc_currency" numberFormat="#,##0.00"/>
            <range lastRow="100" lastColumn="2" address="A1:C101"/>
            """;

    @Test
    void aWorkbookWithATableOnASheet() throws IOException {
        Workbook root = F.createWorkbook();
        root.setName("sales");
        root.setFormat(WorkbookFormat.CALC_XLSX);
        DataType text = dataType(root, "String");
        DataType number = dataType(root, "Double");
        Sheet sheet = F.createSheet();
        sheet.setName("orders");
        root.getOwnedElement().add(sheet);

        TableSection orders = F.createTableSection();
        orders.setName("orders");
        orders.setKind(SectionKind.CALC_LIST_OBJECT);
        CellRange range = F.createCellRange();
        range.setFirstRow(0);
        range.setFirstColumn(0);
        range.setLastRow(100);
        range.setLastColumn(2);
        range.setAddress("A1:C101");
        orders.setRange(range);
        orders.getFeature().add(column("order_no", 0, "Order", CellValueType.CALC_FLOAT, number));
        orders.getFeature().add(column("customer", 1, "Customer", CellValueType.CALC_STRING, text));
        SheetColumn amount = column("amount", 2, "Amount", CellValueType.CALC_CURRENCY, number);
        amount.setNumberFormat("#,##0.00");
        orders.getFeature().add(amount);
        sheet.getOwnedElement().add(orders);
        String xmi = xmi(root, "calc-example.xmi");
        for (String line : XMI.split("\n")) {
            assertThat(xmi).as(line).contains(line.trim());
        }
    }

    private static SheetColumn column(String name, long index, String header, CellValueType type, DataType dataType) {
        SheetColumn c = F.createSheetColumn();
        c.setName(name);
        c.setColumnIndex(index);
        c.setHeader(header);
        c.setValueType(type);
        c.setType(dataType);
        return c;
    }

    private static DataType dataType(Namespace owner, String name) {
        DataType t = CoreFactory.eINSTANCE.createDataType();
        t.setName(name);
        owner.getOwnedElement().add(t);
        return t;
    }

    /** {@code root} as XMI with short ids and without the ids, written to {@code target/<file>} as well. */
    private static String xmi(EObject root, String file) throws IOException {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
        Resource r = rs.createResource(URI.createURI(file));
        r.getContents().add(root);
        ShortIds.assign(r);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        r.save(out, Map.of(XMLResource.OPTION_ENCODING, "UTF-8"));
        String xmi = out.toString(StandardCharsets.UTF_8).replaceAll(" xmi:id=\"[^\"]*\"", "");
        Files.writeString(Path.of(System.getProperty("user.dir"), "target", file), xmi, StandardCharsets.UTF_8);
        return xmi;
    }
}
