# CWM sensiNact mappings to Fennec event.atlas — Maven plugin `fennec-atlas`

`org.eclipse.daanse:org.eclipse.daanse.cwm.maven.atlas` converts the sensiNact mappings of a CWM
model, with the recording and retention rules and the units of their resources, into Eclipse
Fennec event.atlas provider mappings and profiles. The plugin has one goal,
**`fennec-atlas:generate`**, bound to `generate-resources`.

The conversion lives in [`sensinact/fennec.atlas.core`](../../sensinact/fennec.atlas.core/README.md),
the plugin's only dependency. This page is about using the plugin.

- [Requirements](#requirements)
- [Quick start](#quick-start)
- [What you get](#what-you-get)
- [All settings](#all-settings)
- [Recipes](#recipes)
- [Messages and failures](#messages-and-failures)
- [Troubleshooting](#troubleshooting)
- [Tests of this module](#tests-of-this-module)

## Requirements

| What | Version / value |
| --- | --- |
| Java | 21 or newer (the plugin is compiled for Java 21) |
| Maven | 3.9 or newer (built against the 3.9 plugin API; this repository builds with Maven 4) |
| Plugin coordinates | `org.eclipse.daanse:org.eclipse.daanse.cwm.maven.atlas:0.0.1-SNAPSHOT` |
| Where it comes from | built from this repository: `mvn install -pl maven/atlas -am` puts it and its core into the local repository |
| Dependencies | only its core `org.eclipse.daanse.cwm.sensinact.fennec.atlas.core` (plus the Maven API) |
| Plugin repository | the Fennec snapshot repository `https://central.sonatype.com/repository/maven-snapshots/` |
| Input | a CWM XMI with `mapping.sensinact` provider mappings, and the `.ecore` files of the source class model |

Every CWM 1.1 package and the Daanse packages (sensiNact, governance, units) are on the plugin's
class path and registered before the model is loaded. A model that uses further metamodels needs
[`extraPackages`](#all-settings).

## Quick start

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

The CWM model holds a provider mapping and the rules of its resources (ids left out):

```xml
<ownedElement xsi:type="sensinactmapping:ProviderMapping" name="ecowitt-persistence" provider="_ecowitt-weather">
  <ownedElement xsi:type="sensinactmapping:ServiceMapping" name="weather" service="_ecowitt-weather.weather">
    <ownedElement xsi:type="sensinactmapping:ResourceMapping" name="temperature" resource="_ecowitt-weather.weather.temperature">
      <value xsi:type="sensinactmapping:PathSource">
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

`mvn process-resources` then writes
`target/generated-resources/atlas/mappings/ecowitt-persistence.xmi` with a change rule of 5 % and
a deletion rule of 365 days for the temperature, and the profile of the provider below
`profiles/`.

## What you get

Below `outputDirectory`:

| File | Content |
| --- | --- |
| `mappings/<mapping>.xmi` | one Fennec `ProviderMapping` per CWM provider mapping: provider classes, timestamp, name and admin features, services and resources with type, unit, value features, change and deletion rules |
| `profiles/<provider>-profile.xmi` | with `profiles`: one `UNIFIED` profile per provider, so all mappings onto a provider build one sensiNact model named after it |

The written files refer to the classes and features of the source model by namespace URI
(`http://www.ecowitt.com/weatherstation#//EcoWittWeather/rainRate`); the `.ecore` files are not
copied, a Fennec runtime has the source packages registered anyway. Classes and features are found
by name in the given `.ecore` files, so the Ecore model may come from any tool, for example from
[`cwm-ecore:generate`](../ecore/README.md) in `generate-sources` of the same build.

## All settings

Every setting is an element inside `<configuration>`; those with a property also take
`-D<property>=<value>`.

| Setting | Property | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `modelFile` | `fennec-atlas.modelFile` | file | — (required) | The CWM XMI with the provider mappings; files it refers to by relative `href` are loaded with it. |
| `mappings` | — | list | all | The names of the provider mappings to convert. A name that matches none fails the build, naming those there are. |
| `ecoreFile` | `fennec-atlas.ecoreFile` | file | — | The `.ecore` of the source class model; with `ecoreFiles` at least one is required. |
| `ecoreFiles` | — | list of files | — | Further `.ecore` files of the source class model, e.g. one per package. Each must exist. |
| `packageMap` | — | map | — | CWM package (qualified name or trailing part) → EPackage (namespace URI, qualified name or name), for packages named differently. |
| `dataTypes` | — | map | — | CWM data type name → Ecore data type name (`EDouble` → `EFloat`), before every other rule. |
| `profiles` | `fennec-atlas.profiles` | boolean | `true` | One `UNIFIED` profile per provider; `false` writes none, each mapping then builds its own model. |
| `failOnValidation` | `fennec-atlas.failOnValidation` | boolean | `true` | Reject mappings the validators reject; `false` converts them anyway and reports. |
| `outputDirectory` | `fennec-atlas.outputDirectory` | directory | `${project.build.directory}/generated-resources/atlas` | Where `mappings/` and `profiles/` are written. |
| `attachResource` | `fennec-atlas.attachResource` | boolean | `true` | Add `outputDirectory` as a resource directory, so the files land in the jar. |
| `extraPackages` | — | list | — | Interfaces of further generated EPackages to register before loading; their jars come as plugin dependencies. |
| `skip` | `fennec-atlas.skip` | boolean | `false` | Do nothing, e.g. `-Dfennec-atlas.skip` for one build. |

## Recipes

### Some of the mappings

```xml
<mappings>
  <mapping>em310udl-battery-sensor</mapping>
</mappings>
```

The profile of the provider is written all the same, so the mapping finds it.

### Source classes in several Ecore files

```xml
<ecoreFiles>
  <ecoreFile>${project.basedir}/src/main/ecore/lorawan-uplink.ecore</ecoreFile>
  <ecoreFile>${project.basedir}/src/main/ecore/dragino-message.ecore</ecoreFile>
</ecoreFiles>
```

### A package named differently in Ecore

```xml
<packageMap>
  <ecowitt>http://example.org/station</ecowitt>
</packageMap>
```

Without the entry a class is still found when its name is unique among the given Ecore packages.

## Messages and failures

| Situation | Result |
| --- | --- |
| `modelFile` missing | build fails |
| neither `ecoreFile` nor `ecoreFiles`, or one of them missing | build fails, nothing is written |
| the model holds no provider mapping | build fails |
| a name in `mappings` matches no provider mapping | build fails, naming the mappings there are |
| a mapping the validators reject (rules `P1`–`P3`, `S1`–`S8`) | build fails with the findings; with `failOnValidation=false` they are logged and the mapping is converted |
| a class or feature not found in the Ecore model (`UNRESOLVED`), a rule Fennec cannot carry | build fails with the conversion errors, nothing is written |
| an `extraPackages` entry that is not on the class path, a file that cannot be read | build error |

Warnings and notes of the conversion are logged with the prefix `fennec-atlas:`; on success the
goal logs how many mappings and profiles it wrote.

## Troubleshooting

| Symptom | Cause and remedy |
| --- | --- |
| `UNRESOLVED` for a class or feature | The `.ecore` files do not hold it under the CWM name: add the file, or map the package with `packageMap`. |
| The plugin cannot be resolved | The Fennec snapshot repository is missing as plugin repository. |
| A resource has the wrong Ecore type | Map the CWM data type with `dataTypes`. |

## Tests of this module

`GenerateAtlasMojoTest` runs the goal in JUnit — the Mojo instantiated, its parameters set as a
POM sets them — on the projects under `src/test/resources/it`: `ecowitt` (a weather station,
class model and mapping in two files, three kinds of change rules and two retention schedules) and
`battery` (two sensors mapped onto one provider, three `.ecore` files):

- the mapping with the rules and units of its resources, loaded again with Fennec's own model;
- one profile per provider, and the chosen mappings only; an unknown mapping name fails;
- no `.ecore`, a missing `.ecore` and a class missing in the Ecore model fail without writing;
- a mapping the validators reject fails unless `failOnValidation=false`;
- `dataTypes` overrides the type of a resource; a package named differently is found by its
  classes or through `packageMap`;
- a further EPackage is registered before loading, a missing one fails.

`ItProjectsUpToDateTest` checks that the CWM files of the test projects are exactly the examples of
`model/daanse/mapping.sensinact` they are written from; `-Dfennec-atlas.it.update=true` rewrites
them.
