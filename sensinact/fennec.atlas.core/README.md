# org.eclipse.daanse.cwm.sensinact.fennec.atlas.core

Converts CWM sensiNact mappings, with the recording rules, retention schedules, retention holds
and units of their resources, into Eclipse Fennec event.atlas provider mappings and profiles: one
mapping per CWM mapping, one `UNIFIED` profile per provider. Classes and features are found by
name in the given `.ecore` files and referenced by namespace URI. What Fennec cannot carry is an
error; with `lenient` it is a warning and the part is left out. `CwmToAtlas.convert` converts,
`AtlasGenerator.withEcore` writes `mappings/` and `profiles/`.

## Relations

- Reads `model/daanse/resource.sensinact`, `model/daanse/mapping.sensinact`, `governance/recording`,
  `governance/lifecycle`, `governance/obligations` and `units`; finds Ecore elements with `EcoreIndex`
  of `emf/standalone`.
- Writes the model of `org.eclipse.fennec.event.atlas:org.eclipse.fennec.event.atlas.mapping`
  (snapshot repository `https://central.sonatype.com/repository/maven-snapshots/`).
- Run by the Maven goal of `maven/atlas`.

The test resources in `ecore/`, `expected/` and `profiles/` are the examples of Eclipse Fennec
event.atlas (commit `a84dbdd`, EPL-2.0, unchanged), except `vendora.ecore` and `vendorb.ecore`,
which give the classes Fennec's temperature examples name.

## Example

The provider mapping written for the Dragino battery sensor of the sensinact example:

```xml
<mapping:ProviderMapping xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:ecore="http://www.eclipse.org/emf/2002/Ecore" xmlns:mapping="https://fennec.eclipse.org/event.atlas/mapping/1.0"
    mid="dragino-battery-sensor">
  <timestamp strategy="FEATURE">
    <featurePath xsi:type="ecore:EReference" href="https://eclipse.org/fennec/lorawan#//UplinkMessage/rxInfo"/>
    <featurePath xsi:type="ecore:EAttribute" href="https://eclipse.org/fennec/lorawan#//RxInfo/time"/>
  </timestamp>
  <name name="Dragino LSE01 Battery Sensor">
    <featurePath xsi:type="ecore:EAttribute" href="https://eclipse.org/fennec/lorawan#//UplinkMessage/deduplicationId"/>
  </name>
  <providerClasses href="https://eclipse.org/fennec/lorawan/dragino#//DraginoLSE01Uplink"/>
  <services mid="battery">
    <name name="Battery"/>
    <resources name="Battery Level" unit="V" timestamp="//@timestamp" mid="level">
      <eType xsi:type="ecore:EDataType" href="http://www.eclipse.org/emf/2002/Ecore#//EDouble"/>
      <valueFeature xsi:type="ecore:EReference" href="https://eclipse.org/fennec/lorawan/dragino#//DraginoLSE01Uplink/object"/>
      <valueFeature xsi:type="ecore:EAttribute" href="https://eclipse.org/fennec/lorawan/dragino#//DecodedObject/batV"/>
    </resources>
  </services>
  <admin mid="admin">
    <name name="Admin"/>
    <friendlyNameFeature xsi:type="ecore:EReference" href="https://eclipse.org/fennec/lorawan#//UplinkMessage/deviceInfo"/>
    <friendlyNameFeature xsi:type="ecore:EAttribute" href="https://eclipse.org/fennec/lorawan#//DeviceInfo/deviceProfileName"/>
  </admin>
  <profile href="../profiles/battery-sensor-profile.xmi#battery-sensor"/>
</mapping:ProviderMapping>
```
