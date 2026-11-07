# org.eclipse.daanse.cwm.model.daanse.governance.lifecycle

How long the data of a CWM element is kept and what happens to it on the way out. A
`RetentionSchedule` governs the values of features or the rows of classifiers and runs from a
`PeriodStart`: the time of the value, a time feature, or a `behavioral::Event`, aligned to the end of
a month, quarter or year in a zone. Its stages — `Restrict`, `Review`, `Notify`, `Tier`, `Transfer`,
`Aggregate`, `Delete` — fall due one after another; limits keep the newest values or a size. The
nearest schedule of a scope applies, along feature, owner and namespaces. `Retention` computes the
plan of an element at a time; `LifecycleModelValidator` checks the rules L1 to L10.

## Relations

- Builds on CWM `core` (every rule is a `core::Constraint`), `behavioral` (the event a period
  starts from) and `businessinformation` (who reviews or is notified).
- `recording`, `obligations` and `deidentification` extend its rule and its stages; the Fennec
  converter of `sensinact` writes the schedule of a resource as its deletion rule.

## Example

The rows of a table kept ten years from the end of the year of `issued_on`, restricted after
eight; the values of a column deleted after 90 days (ids left out):

```xml
<core:Model xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:core="http://www.omg.org/spec/CWM/1.1/objectmodel/core"
    xmlns:lifecycle="https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.governance.lifecycle"
    xmlns:relational="http://www.omg.org/spec/CWM/1.1/resource/relational" name="erp">
  <ownedElement xsi:type="relational:Catalog" name="finance">
    <ownedElement xsi:type="relational:Schema" name="ledger">
      <ownedElement xsi:type="relational:Table" name="invoices" constraint="_invoices-10y">
        <feature xsi:type="relational:Column" name="issued_on" type="_DATE"/>
        <feature xsi:type="relational:Column" name="note" constraint="_note-90d" type="_CHARACTER_VARYING"/>
      </ownedElement>
    </ownedElement>
    <ownedElement xsi:type="relational:SQLSimpleType" name="DATE" typeNumber="91"/>
    <ownedElement xsi:type="relational:SQLSimpleType" name="CHARACTER VARYING" typeNumber="12"/>
  </ownedElement>
  <ownedElement xsi:type="core:Package" name="rules">
    <ownedElement xsi:type="lifecycle:RetentionSchedule" name="invoices-10y" constrainedElement="_invoices" scope="row">
      <body body="from issued_on, end of year in Europe/Berlin: restrict after P8Y, delete after P10Y" language="daanse.lifecycle"/>
      <start xsi:type="lifecycle:FeatureTime" alignment="endOfYear" zone="Europe/Berlin" feature="_invoices.issued_on"/>
      <stage xsi:type="lifecycle:Restrict" name="restrict" after="P8Y"/>
      <stage xsi:type="lifecycle:Delete" name="delete" after="P10Y"/>
    </ownedElement>
    <ownedElement xsi:type="lifecycle:RetentionSchedule" name="note-90d" constrainedElement="_invoices.note">
      <body body="from the time of the value: delete after P90D" language="daanse.lifecycle"/>
      <start xsi:type="lifecycle:ValueTime"/>
      <stage xsi:type="lifecycle:Delete" name="delete" after="P90D"/>
    </ownedElement>
  </ownedElement>
</core:Model>
```
