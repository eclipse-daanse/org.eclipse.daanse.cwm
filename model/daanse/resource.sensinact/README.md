# org.eclipse.daanse.cwm.model.daanse.resource.sensinact

Eclipse sensiNact digital twins as CWM classes. `Provider` and `Service` are `core::Class`es, a
`Resource` is a `core::Attribute`: a provider owns its services in `ownedElement`, a service has its
resources as `feature`s, typed by a data type. The names become the ids of the twin.
`SensinactStructure` reads services and resources in order, `Resources.fromAttributes` derives
resources from the attributes of a class, `SensinactModelValidator` checks the rules P1 to P3.

## Relations

- Builds on CWM `core`, `relationships` (the ancestors of a class) and `businessinformation` (the
  description of a resource).
- `mapping.sensinact` maps a CWM class model onto these providers; a provider has as many mappings
  as there are sources for it.

## Example

A battery sensor with one service and one resource (ids left out):

```xml
<ownedElement xsi:type="core:Package" name="providers">
  <ownedElement xsi:type="sensinact:Provider" name="battery-sensor" friendlyNameRequired="true">
    <ownedElement xsi:type="sensinact:Service" name="battery" displayName="Battery">
      <feature xsi:type="sensinact:Resource" name="level" type="_EDouble" displayName="Battery Level"/>
    </ownedElement>
  </ownedElement>
</ownedElement>
```
