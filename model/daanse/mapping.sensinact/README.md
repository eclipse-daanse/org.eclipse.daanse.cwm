# org.eclipse.daanse.cwm.model.daanse.mapping.sensinact

The mapping of a CWM class model onto the sensiNact providers of `resource.sensinact`. A
`ProviderMapping` names its provider and its source classes and contains `ServiceMapping`s with
their `ResourceMapping`s and an `AdminMapping`; every value comes from a `ValueSource` — a
`PathSource` (a typed path of structural features with index, filter, format and fallback), a
`LiteralSource` or an `ArrivalTimeSource`. `SensinactMappingModelValidator` checks the rules S1 to
S8 and reports the findings P1 to P3 of the providers along; `Transformations.export` writes a
mapping as CWM transformation maps.

## Relations

- Builds on `resource.sensinact` (the providers mapped onto), CWM `core`, `relationships`
  (association ends in a path), `datatypes`, `businessinformation` and `transformation` (the
  export).
- Independent of `lifecycle`, `recording`, `obligations` and `units`: the Fennec converter
  `sensinact/fennec.atlas.core` resolves the rules and units of a resource and writes Eclipse
  Fennec event.atlas mapping files.

## Example

The mapping of a Dragino uplink onto the battery sensor of `resource.sensinact` (ids left out):

```xml
<ownedElement xsi:type="core:Package" name="mappings">
  <ownedElement xsi:type="sensinactmapping:ProviderMapping" name="dragino-battery-sensor" provider="_battery-sensor" sourceClass="_DraginoLSE01Uplink">
    <ownedElement xsi:type="sensinactmapping:ServiceMapping" name="battery" service="_battery-sensor.battery">
      <ownedElement xsi:type="sensinactmapping:ResourceMapping" name="level" resource="_battery-sensor.battery.level">
        <value xsi:type="sensinactmapping:PathSource" path="_DraginoLSE01Uplink.object _dragino.DecodedObject.batV"/>
      </ownedElement>
    </ownedElement>
    <id xsi:type="sensinactmapping:PathSource" path="_UplinkMessage.deduplicationId">
      <fallback xsi:type="sensinactmapping:LiteralSource" literal="Dragino LSE01 Battery Sensor"/>
    </id>
    <timestamp xsi:type="sensinactmapping:PathSource" path="_UplinkMessage.rxInfo _RxInfo.time" index="0"/>
    <admin name="admin">
      <friendlyName xsi:type="sensinactmapping:PathSource" path="_UplinkMessage.deviceInfo _DeviceInfo.deviceProfileName"/>
    </admin>
  </ownedElement>
</ownedElement>
```
