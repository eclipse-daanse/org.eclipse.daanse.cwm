# org.eclipse.daanse.cwm.model.daanse.resource.calc

Spreadsheets on CWM: workbooks in the OpenDocument Calc (ODF) and OOXML SpreadsheetML (Excel)
formats and the data in them. A `Workbook` and a `Sheet` are `core::Package`s. The regions on a
sheet are `Section`s, `core::Class`es with their columns as `SheetColumn`s (`core::Attribute`s):
a `TableSection` (a database range or list object, with header and totals rows), a
`MatrixSection` (a cross tabulation with row and column labels) and a `PivotSection`. Several
sections of different shape may lie on one sheet; each has its `CellRange`, which may be open
towards the bottom or the right (`lastRow`, `lastColumn` unset). A `NamedRange` names a range or
an expression. The cells are CWM instance data: `SheetData` (an `instance::Extent`), `Row` and
`Cell`. Formulas are kept as text in the notation `Workbook.formulaLanguage` names. 5 OCL
invariants check the ranges, the header and totals rows and the cell spans.

## Relations

- Builds on CWM `core` and `instance`.
- Deliberately not on `resource.record`: a spreadsheet has no field or text delimiters; the grid
  does the separating, and the position of a region is part of the structure.

## Example

A workbook whose sheet holds a table of orders with three columns (namespace declarations and ids
left out):

```xml
<calc:Workbook name="sales" format="calc_xlsx">
  <ownedElement xsi:type="core:DataType" name="String"/>
  <ownedElement xsi:type="core:DataType" name="Double"/>
  <ownedElement xsi:type="calc:Sheet" name="orders">
    <ownedElement xsi:type="calc:TableSection" name="orders" kind="calc_listObject">
      <feature xsi:type="calc:SheetColumn" name="order_no" type="_Double" header="Order" valueType="calc_float"/>
      <feature xsi:type="calc:SheetColumn" name="customer" type="_String" columnIndex="1" header="Customer" valueType="calc_string"/>
      <feature xsi:type="calc:SheetColumn" name="amount" type="_Double" columnIndex="2" header="Amount" valueType="calc_currency" numberFormat="#,##0.00"/>
      <range lastRow="100" lastColumn="2" address="A1:C101"/>
    </ownedElement>
  </ownedElement>
</calc:Workbook>
```
