# org.eclipse.daanse.cwm.model.daanse.governance.obligations

Legal duties on the data of CWM elements. A `RetentionObligation` keeps data at least a period
(`immutable`: unchanged), a `DeletionObligation` wants it gone within a period
(`anonymizationSatisfies`: anonymous counts as gone), a `RetentionHold` suspends the destructive
stages while it lasts, a `DeletionClass` gives a kind of data its event and its standard term, and
a `LegalSource` names the duties that rest on it. Every duty along the path applies.
`Compliance.check` tests the retention schedules of an element against its duties and holds with
instants; `ObligationsModelValidator` checks the rules O1 to O9 in the model.

## Relations

- Builds on `lifecycle` (duties, holds and classes are lifecycle rules; starts and periods) and
  CWM `businessinformation` (a legal source is a `Document`, a holder a `ResponsibleParty`).
- `gdpr` makes an erasure a deletion obligation, `transparency` keeps published information with a
  retention obligation, and the Fennec converter of `sensinact` reports a hold that is not over.
  The `Anonymize` stage of `deidentification` keeps a deletion obligation where
  `anonymizationSatisfies` says so.

## Example

Invoices kept eight years unchanged but personal data in them gone after one: restricted after
one year, deleted after eight; a tax audit holds the schema (ids left out):

```xml
<!-- in the package governance of the model company -->
<ownedElement xsi:type="obligations:RetentionObligation" name="invoices-8y" constrainedElement="_invoices" scope="row" minimum="P8Y" immutable="true">
  <body body="keep at least P8Y from issued_on, end of year in Europe/Berlin; immutable" language="daanse.lifecycle"/>
  <start xsi:type="lifecycle:FeatureTime" alignment="endOfYear" zone="Europe/Berlin" feature="_invoices.issued_on"/>
</ownedElement>
<ownedElement xsi:type="obligations:DeletionObligation" name="invoice-privacy-1y" constrainedElement="_invoices" scope="row" maximum="P1Y">
  <body body="delete within P1Y from issued_on, end of year in Europe/Berlin" language="daanse.lifecycle"/>
  <start xsi:type="lifecycle:FeatureTime" alignment="endOfYear" zone="Europe/Berlin" feature="_invoices.issued_on"/>
</ownedElement>
<ownedElement xsi:type="lifecycle:RetentionSchedule" name="invoice-rows" constrainedElement="_invoices" scope="row">
  <body body="from issued_on, end of year in Europe/Berlin: restrict after P1Y, delete after P8Y" language="daanse.lifecycle"/>
  <start xsi:type="lifecycle:FeatureTime" alignment="endOfYear" zone="Europe/Berlin" feature="_invoices.issued_on"/>
  <stage xsi:type="lifecycle:Restrict" name="restrict" after="P1Y"/>
  <stage xsi:type="lifecycle:Delete" name="delete" after="P8Y"/>
</ownedElement>
<ownedElement xsi:type="obligations:RetentionHold" name="tax-audit-2028" constrainedElement="_sales" since="2028-10-01T00:00:00Z" reason="tax audit 2028" holder="_legal">
  <body body="hold since 2028-10-01T00:00:00Z: tax audit 2028" language="daanse.lifecycle"/>
</ownedElement>
<!-- in the package law of the model company -->
<ownedElement xsi:type="obligations:LegalSource" name="gdpr-17" reference="https://eur-lex.europa.eu/eli/reg/2016/679/oj" type="statute" modelElement="_invoice-privacy-1y" jurisdiction="EU" provision="Art. 17 (1) (a)"/>
<!-- in the catalog erp -->
<ownedElement xsi:type="relational:Schema" name="sales" constraint="_tax-audit-2028">
  <ownedElement xsi:type="relational:Table" name="invoices" constraint="_invoices-8y _invoice-privacy-1y _invoice-rows">
```
