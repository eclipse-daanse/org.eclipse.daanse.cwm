# org.eclipse.daanse.cwm.model.daanse.governance.transparency

The transparency of public data under the Thuringian Transparency Act (ThuerTG). A
`TransparencyAssessment` of a data domain contains `TransparencyClassRating`s of data classes —
publication mode (without a request, mandatory or optional; on request; excluded), information
category, exclusion ground, data format level, the provision applied — which contain the
`TransparencyFieldRating`s of their fields. `TransparencyModelValidator` checks the rules T1 to T3.

## Relations

- A class rating names the `deidentification` policy its data is published under.
- `TransparencyLifecycle.retention` keeps published information as a `RetentionObligation` of
  `obligations` from the end of the year of the value, resting on § 7 (8) ThuerTG.
- Builds on CWM `core` and `businessinformation` (the responsible party).

## Example

Contracts of a city published without a request, their partner field on request, kept ten years
(ids left out):

```xml
<!-- in the package governance of the model city -->
<ownedElement xsi:type="transparency:TransparencyAssessment" name="assessment-2026" validFrom="2026-01-01" contactResponsible="_transparency-office">
  <ownedElement xsi:type="obligations:RetentionObligation" name="contracts-kept" constrainedElement="_contracts" scope="row" minimum="P10Y">
    <body body="keep at least P10Y from the time of the value, end of year in Europe/Berlin" language="daanse.lifecycle"/>
    <start xsi:type="lifecycle:ValueTime" alignment="endOfYear" zone="Europe/Berlin"/>
  </ownedElement>
  <dataClasses name="contracts" dataClass="_contracts" publicationMode="PROACTIVE_MANDATORY">
    <dataFields name="partner" dataField="_contracts.partner" publicationMode="ON_REQUEST"/>
  </dataClasses>
</ownedElement>
<ownedElement xsi:type="obligations:LegalSource" name="thuertg-7-8" reference="ThuerTG" type="statute" modelElement="_contracts-kept" jurisdiction="DE-TH" provision="§ 7 (8)"/>
<!-- in the schema public of the catalog data -->
<ownedElement xsi:type="relational:Table" name="contracts" constraint="_contracts-kept">
```
