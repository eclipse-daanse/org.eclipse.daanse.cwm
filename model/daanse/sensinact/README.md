# org.eclipse.daanse.cwm.model.daanse.sensinact

Eclipse sensiNact digital twins as CWM classes, and the mapping of a class model onto them.
`Provider` and `Service` are `core::Class`es, a `Resource` is a `core::Attribute`. A
`ProviderMapping` names its provider and its source classes and contains `ServiceMapping`s with
their `ResourceMapping`s and an `AdminMapping`; every value comes from a `ValueSource` — a
`PathSource` (a typed path of structural features with index, filter, format and fallback), a
`LiteralSource` or an `ArrivalTimeSource`. `SensinactModelValidator` checks the rules S1 to S8;
`Transformations.export` writes a mapping as CWM transformation maps.

## Relations

- Builds on CWM `core`, `relationships` (association ends in a path), `datatypes`,
  `businessinformation` and `transformation` (the export).
- Independent of `lifecycle`, `recording`, `obligations` and `units`: the Fennec converter
  `sensinact/fennec.atlas.core` resolves the rules and units of a resource and writes Eclipse
  Fennec event.atlas mapping files.

## Example

A battery sensor and the mapping of a Dragino uplink onto it (ids left out):

```xml
<ownedElement xsi:type="core:Package" name="providers">
  <ownedElement xsi:type="sensinact:Provider" name="battery-sensor" friendlyNameRequired="true">
    <ownedElement xsi:type="sensinact:Service" name="battery" displayName="Battery">
      <feature xsi:type="sensinact:Resource" name="level" type="_EDouble" displayName="Battery Level"/>
    </ownedElement>
  </ownedElement>
</ownedElement>
<ownedElement xsi:type="core:Package" name="mappings">
  <ownedElement xsi:type="sensinact:ProviderMapping" name="dragino-battery-sensor" provider="_battery-sensor" sourceClass="_DraginoLSE01Uplink">
    <ownedElement xsi:type="sensinact:ServiceMapping" name="battery" service="_battery-sensor.battery">
      <ownedElement xsi:type="sensinact:ResourceMapping" name="level" resource="_battery-sensor.battery.level">
        <value xsi:type="sensinact:PathSource" path="_DraginoLSE01Uplink.object _dragino.DecodedObject.batV"/>
      </ownedElement>
    </ownedElement>
    <id xsi:type="sensinact:PathSource" path="_UplinkMessage.deduplicationId">
      <fallback xsi:type="sensinact:LiteralSource" literal="Dragino LSE01 Battery Sensor"/>
    </id>
    <timestamp xsi:type="sensinact:PathSource" path="_UplinkMessage.rxInfo _RxInfo.time" index="0"/>
    <admin name="admin">
      <friendlyName xsi:type="sensinact:PathSource" path="_UplinkMessage.deviceInfo _DeviceInfo.deviceProfileName"/>
    </admin>
  </ownedElement>
</ownedElement>
```
