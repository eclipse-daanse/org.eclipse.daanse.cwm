# org.eclipse.daanse.cwm.model.daanse.governance.recording

When a value of a CWM element is recorded, and how history is kept. A recording rule decides per
value: `AbsoluteChangeRule`, `RelativeChangeRule` and `RangeChangeRule` (dead bands),
`SamplingRule`, `ThrottleRule`, `ChangedValueRule`, `SwingingDoorRule`; every rule may add a
minimum and a maximum interval (heartbeat), a status feature whose change is recorded, the
handling of late values and the interpolation between records. A `HistorisationRule` says how a
class or column keeps its history — overwrite, new version, previous value, history table, version
and current, snapshot — and names the features that carry it. `Recording.decide` evaluates a rule
on a stream of values; `RecordingModelValidator` checks the rules R1 to R7.

## Relations

- Builds on `lifecycle` (both rules are lifecycle rules; `Duration`) and CWM `core`.
- The Fennec converter of `sensinact` writes the recording rule of a resource as its change rule.

## Example

A dead band with a minimum interval, a heartbeat and a status feature on an attribute; the
versions of a table's rows and the previous value of a column (ids left out):

```xml
<!-- in the package governance of the model plant -->
<ownedElement xsi:type="recording:AbsoluteChangeRule" name="temperature-0.5" constrainedElement="_Sensor.temperature" minimumInterval="PT5M" maximumInterval="PT1H" statusFeature="_Sensor.quality" delta="0.5">
  <body body="time - lastTime >= PT5M and ((abs(value - last) >= 0.5) or time - lastTime >= PT1H or quality changed)" language="daanse.lifecycle"/>
</ownedElement>
<ownedElement xsi:type="recording:HistorisationRule" name="customer-versions" constrainedElement="_customers" scope="row" kind="newVersion" validFrom="_customers.valid_from" validTo="_customers.valid_to" currentFlag="_customers.is_current">
  <body body="rows: newVersion; valid from valid_from to valid_to; current is_current" language="daanse.lifecycle"/>
</ownedElement>
<ownedElement xsi:type="recording:HistorisationRule" name="city-previous" constrainedElement="_customers.city" kind="previousValue" previousValue="_customers.previous_city">
  <body body="values: previousValue; previous in previous_city" language="daanse.lifecycle"/>
</ownedElement>
<!-- in the class Sensor -->
<feature xsi:type="core:Attribute" name="temperature" constraint="_temperature-0.5" type="_Double"/>
<!-- in the schema core of the catalog crm -->
<ownedElement xsi:type="relational:Table" name="customers" constraint="_customer-versions">
  <feature xsi:type="relational:Column" name="city" constraint="_city-previous" type="_CHARACTER_VARYING"/>
```
