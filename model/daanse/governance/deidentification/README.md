# org.eclipse.daanse.cwm.model.daanse.governance.deidentification

De-identification of the data of CWM classifiers. A `DeidentificationPolicy` treats each feature of
the governed classifiers once — `Unchanged`, `Removal`, `Masking`, `Truncation`, `Rounding`,
`TopBottomCoding`, `NoiseAddition`, `DateShift`, `Generalization`, `Pseudonymization` — with its
role (identifying, quasi-identifying, sensitive, insensitive), names the privacy models the result
meets (`KAnonymity`, `LDiversity`, `TCloseness`, `DifferentialPrivacy`) and claims an outcome,
pseudonymous or anonymous, for an audience. A `PseudonymizationDomain` says who holds the means to
reverse pseudonyms. The stages `Pseudonymize`, `Anonymize` and `DestroyLinkage` put policies and
domains into retention schedules. A `DisclosureControl` protects the cells of cubes and measures.
`DeidentificationModelValidator` checks the rules D1 to D9.

## Relations

- Builds on `lifecycle` (its three stages are lifecycle stages), CWM `core` (policies and controls
  are `core::Constraint`s) and `businessinformation` (audience, key holder).
- `gdpr` names a policy at a transmission, `transparency` at a rating. `Anonymize` keeps a
  deletion obligation of `obligations` where that obligation lets anonymous data count as gone.

## Example

Hospital records pseudonymized for research after six months and made anonymous for statistics
after ten years; the key of the pseudonyms destroyed ten years after the study (ids left out):

```xml
<!-- in the package governance of the model hospital -->
<ownedElement xsi:type="deid:PseudonymizationDomain" name="study-pseudonyms" constraint="_pseudonym-keys" keyHolder="_trust-centre" mapping="_pseudonyms"/>
<ownedElement xsi:type="lifecycle:RetentionSchedule" name="pseudonym-keys" constrainedElement="_study-pseudonyms">
  <body body="from event studyEnded: destroy linkage study-pseudonyms after P10Y" language="daanse.lifecycle"/>
  <start xsi:type="lifecycle:EventTime" event="_studyEnded"/>
  <stage xsi:type="deid:DestroyLinkage" name="destroy-linkage" after="P10Y" domain="_study-pseudonyms"/>
</ownedElement>
<ownedElement xsi:type="deid:DeidentificationPolicy" name="records-research" constrainedElement="_records" outcome="pseudonymous" audience="_research-partner">
  <treatment xsi:type="deid:Pseudonymization" feature="_records.insurance_no" role="identifying" domain="_study-pseudonyms"/>
  <treatment xsi:type="deid:Removal" feature="_records.name" role="identifying"/>
  <treatment xsi:type="deid:Generalization" feature="_records.birth_date" role="quasiIdentifying" granularity="month"/>
  <treatment xsi:type="deid:Unchanged" feature="_records.diagnosis" role="sensitive"/>
  <treatment xsi:type="deid:DateShift" feature="_records.collected_on" role="quasiIdentifying" maximum="P14D" consistency="perRow"/>
</ownedElement>
<ownedElement xsi:type="deid:DeidentificationPolicy" name="records-statistics" constrainedElement="_records" outcome="anonymous" audience="_statistics-office" suppressionLimit="0.02">
  <treatment xsi:type="deid:Truncation" feature="_records.zip" role="quasiIdentifying" keep="3"/>
  <model xsi:type="deid:KAnonymity" k="5"/>
  <model xsi:type="deid:LDiversity" l="2" sensitive="_records.diagnosis"/>
</ownedElement>
<ownedElement xsi:type="lifecycle:RetentionSchedule" name="record-rows" constrainedElement="_records" scope="row">
  <body body="from collected_on: pseudonymize records-research after P6M, anonymize records-statistics after P10Y" language="daanse.lifecycle"/>
  <start xsi:type="lifecycle:FeatureTime" feature="_records.collected_on"/>
  <stage xsi:type="deid:Pseudonymize" name="pseudonymize" after="P6M" policy="_records-research"/>
  <stage xsi:type="deid:Anonymize" name="anonymize" after="P10Y" policy="_records-statistics"/>
</ownedElement>
```
