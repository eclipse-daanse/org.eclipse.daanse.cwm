# org.eclipse.daanse.cwm.model.daanse.governance.criticality

How critical CWM elements are. A `CriticalityRating` rates any `core::ModelElement`s with an
overall level, a classification and a score per dimension — confidentiality, integrity,
availability, recovery, dependency, public safety, market stability, disclosure, change — with the
measures assigned and the evidence for them. A `CriticalityAssessment` of a domain, operator or
service holds the ratings with the regime and sector profiles, the assessments of major changes
and the equivalences between regimes; it owns nothing else (OCL `OnlyGovernanceContent`).

## Relations

- Builds on CWM `core` and `warehouseoperation`: a score rests on a `Measurement`, a measure is a
  `ChangeRequest`.
- Independent of the other governance models.

## Example

```xml
<core:Package xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:core="http://www.omg.org/spec/CWM/1.1/objectmodel/core"
    xmlns:criticality="https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.governance.criticality"
    name="waterworks">
  <ownedElement xsi:type="core:Class" xmi:id="Reservoir" name="Reservoir"/>
  <ownedElement xsi:type="criticality:CriticalityAssessment" name="kritis-water-2026" validFrom="2026-01-01">
    <ownedElement xsi:type="criticality:SectorProfile" name="water" sector="WATER" category="CRITICAL_INSTALLATION"/>
    <ownedElement xsi:type="criticality:CriticalityRating" name="reservoir" element="Reservoir" overallLevel="VERY_HIGH" classification="CONFIDENTIAL" rationale="Location of a critical installation.">
      <dimensionScore level="VERY_HIGH"/>
      <dimensionScore dimension="AVAILABILITY" level="HIGH"/>
    </ownedElement>
  </ownedElement>
</core:Package>
```

The first score is of the default dimension, confidentiality, which XMI leaves out.
