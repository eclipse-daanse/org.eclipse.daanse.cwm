# org.eclipse.daanse.cwm.maven.atlas

The Maven goal `fennec-atlas:generate`, bound to `generate-resources`: runs the converter of
`sensinact/fennec.atlas.core` on a CWM XMI and writes `mappings/<mapping>.xmi` and
`profiles/<provider>-profile.xmi` for Eclipse Fennec event.atlas.

## Relations

- Runs `sensinact/fennec.atlas.core`; loads the CWM XMI with `emf/standalone`, which registers the
  CWM and Daanse packages.
- Needs the Fennec snapshot repository `https://central.sonatype.com/repository/maven-snapshots/`
  as plugin repository.

## Usage

```xml
<plugin>
  <groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.cwm.maven.atlas</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <executions>
    <execution>
      <goals>
        <goal>generate</goal>
      </goals>
      <configuration>
        <modelFile>${project.basedir}/src/main/cwm/mapping.xmi</modelFile>
        <ecoreFile>${project.basedir}/src/main/ecore/ecowitt.ecore</ecoreFile>
      </configuration>
    </execution>
  </executions>
</plugin>
```

| Parameter | Default | |
| --- | --- | --- |
| `modelFile` | required | the CWM XMI with the provider mappings; files it refers to by `href` load with it |
| `mappings` | all | the names of the provider mappings to convert |
| `ecoreFile`, `ecoreFiles` | one required | the `.ecore` files of the source class model |
| `packageMap` | — | CWM package → EPackage, where the names differ |
| `outputDirectory` | `${project.build.directory}/generated-resources/atlas` | where `mappings/` and `profiles/` go |
| `profiles` | `true` | one `UNIFIED` profile per provider; `false` writes none |
| `dataTypes` | — | CWM data type name → Ecore data type name |
| `failOnValidation` | `true` | `false` converts mappings the validators reject and reports |
| `attachResource` | `true` | adds the output directory as a resource directory |
| `extraPackages` | — | interfaces of further generated EPackages to register |

## Example

A mapping of the input and the rules of its resources (ids left out):

```xml
<ownedElement xsi:type="sensinact:ProviderMapping" name="ecowitt-persistence" provider="_ecowitt-weather">
  <ownedElement xsi:type="sensinact:ServiceMapping" name="weather" service="_ecowitt-weather.weather">
    <ownedElement xsi:type="sensinact:ResourceMapping" name="temperature" resource="_ecowitt-weather.weather.temperature">
      <value xsi:type="sensinact:PathSource">
        <path xsi:type="core:Attribute" href="classes.xmi#_EcoWittWeather.temperaturOutdoor"/>
      </value>
    </ownedElement>
  </ownedElement>
</ownedElement>
<ownedElement xsi:type="recording:RelativeChangeRule" name="pct-5" constrainedElement="_ecowitt-weather.weather.temperature" percent="5.0">
  <body body="abs(value - last) >= 5.0 % of abs(last)" language="daanse.lifecycle"/>
</ownedElement>
<ownedElement xsi:type="lifecycle:RetentionSchedule" name="keep-1y" constrainedElement="_ecowitt-weather.weather.temperature" cleanupInterval="P7D">
  <body body="from the time of the value: delete after P365D; cleanup every P7D" language="daanse.lifecycle"/>
  <start xsi:type="lifecycle:ValueTime"/>
  <stage xsi:type="lifecycle:Delete" name="delete" after="P365D"/>
</ownedElement>
```

`mvn process-resources` then writes `target/generated-resources/atlas/mappings/ecowitt-persistence.xmi`
with a change rule of 5 % and a deletion rule of 365 days for the temperature.
