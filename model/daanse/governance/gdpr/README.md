# org.eclipse.daanse.cwm.model.daanse.governance.gdpr

The record of processing activities of the GDPR and the assessments that go with it. The
subpackage `processing` (nsPrefix `gdprprocessing`): a `DataProcessingRecord` of a controller
owns its `DataProcessing`s — purpose, kinds of processing, data classes, systems, lawfulness — and
a processing contains its `DataTransmission`s to recipients and its `DataErasure`s. The subpackage
`assessment` (`gdprassessment`): the threshold analysis, the data protection impact assessment with
its risks and their inherent and residual assessments, and the privacy ratings of data classes and
fields. `GdprModelValidator` checks the rules G1 to G4.

## Relations

- An erasure is a `DeletionObligation` of `obligations`, which checks it against the retention
  schedules of its data; the legal sources of a processing are `obligations::LegalSource`s that
  name it. A transmission names the `deidentification` policy the data passes.
- Builds on CWM `core`, `businessinformation` (parties), `softwaredeployment` (systems) and
  `warehouseoperation` (the change requests a residual risk relies on).
- The root package holds only the two subpackages; an application registers `ProcessingPackage`
  and `AssessmentPackage`.

## Example

A processing of a CRM with its legal ground, an erasure three years after the contract ended and
a transmission to a statistics office, anonymous by a policy (ids left out):

```xml
<!-- in the package governance of the model company -->
<ownedElement xsi:type="gdprprocessing:DataProcessingRecord" name="record" contactControllerGlobal="_controller" contactControllerInternal="_office" contactPrivacyOfficer="_officer">
  <ownedElement xsi:type="gdprprocessing:DataProcessing" name="customer-care" dataClasses="_customers" dataProcessingPurpose="care for customers" validFrom="2025-01-01">
    <personalReferenceTypes>DIRECT</personalReferenceTypes>
    <dataProcessingTypes>COLLECTION</dataProcessingTypes>
    <dataProcessingLawfulness name="contract" dataProcessingLawfulnessTypes="CONTRACTUAL_REQUIREMENT" dataProcessingLawfulnessAnnotation="the customer contract"/>
    <dataTransmissions name="to-statistics" dataClasses="_customers" contactRecipient="_statistics" policy="_counts"/>
    <dataErasures name="customers-3y" constrainedElement="_customers" scope="row" maximum="P3Y">
      <body body="delete within P3Y from event contractEnded (contract_end)" language="daanse.lifecycle"/>
      <start xsi:type="lifecycle:EventTime" event="_contractEnded" feature="_customers.contract_end"/>
    </dataErasures>
  </ownedElement>
</ownedElement>
<ownedElement xsi:type="obligations:LegalSource" name="gdpr-6" reference="https://eur-lex.europa.eu/eli/reg/2016/679/oj" type="statute" modelElement="_customer-care" jurisdiction="EU" provision="Art. 6 (1) (b)"/>
<ownedElement xsi:type="deid:DeidentificationPolicy" name="counts" constrainedElement="_customers" outcome="anonymous" audience="_statistics">
  <body body="anonymous for statistics" language="daanse.deidentification"/>
</ownedElement>
<!-- in the schema core of the catalog crm -->
<ownedElement xsi:type="relational:Table" name="customers" constraint="_customer-care.customers-3y _counts">
```
