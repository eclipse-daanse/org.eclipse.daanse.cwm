# CWM-ORM mapping to Fennec EORM — Maven plugin `fennec-eorm`

`org.eclipse.daanse:org.eclipse.daanse.cwm.maven.eorm` turns one CWM-ORM mapping set
(`orm::EntityMappings` of the Daanse ORM model) into a Fennec `.eorm` file: the object-relational
mapping that [Eclipse Fennec Persistence](https://github.com/eclipse-fennec/emf.persistence-jpa)
loads instead of deriving a mapping from names. The `.eorm` refers to the Ecore classes and
features of the mapped class model by relative `href`, so it is written next to those `.ecore`
files. The plugin has one goal, **`fennec-eorm:generate`**, bound by default to the phase
`generate-resources`.

The `.ecore` files of the class model are an input. Any Ecore model does whose names match the CWM
class model: the goal finds every class and feature [by its name](#how-classes-and-features-are-found)
and reads no annotation, so it does not depend on how the Ecore model was written.

The conversion itself lives in [`fennec.eorm.core`](../../orm/fennec.eorm.core/README.md), which lists
every mapping rule. This page is about using the plugin.

- [Requirements](#requirements)
- [Quick start](#quick-start)
- [What you get](#what-you-get)
- [How classes and features are found](#how-classes-and-features-are-found)
- [All settings](#all-settings)
- [Recipes](#recipes)
- [The explicit profile](#the-explicit-profile)
- [Messages and failures](#messages-and-failures)
- [Loading the `.eorm` in Fennec](#loading-the-eorm-in-fennec)
- [Troubleshooting](#troubleshooting)
- [Tests of this module](#tests-of-this-module)

## Requirements

| What | Version / value |
| --- | --- |
| Java | 21 or newer (the plugin is compiled for Java 21) |
| Maven | 3.9 or newer (built against the 3.9 plugin API; this repository builds with Maven 4) |
| Plugin coordinates | `org.eclipse.daanse:org.eclipse.daanse.cwm.maven.eorm:0.0.1-SNAPSHOT` |
| Where it comes from | built from this repository: `mvn install -pl maven/eorm -am` puts it and its core into the local repository |
| Dependencies | only its core `org.eclipse.daanse.cwm.orm.fennec.eorm.core` (plus the Maven API) |
| Fennec EORM model | `org.eclipse.fennec.persistence:org.eclipse.fennec.persistence.orm:0.1.0-SNAPSHOT`, published as snapshot only |

The plugin depends on the Fennec snapshot, so a project that uses it declares the Sonatype
snapshot repository as plugin repository (once online; afterwards `-o` works):

```xml
<pluginRepositories>
  <pluginRepository>
    <id>ossrh</id>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <releases>
      <enabled>false</enabled>
    </releases>
    <snapshots>
      <enabled>true</enabled>
    </snapshots>
  </pluginRepository>
</pluginRepositories>
```

Only the EMF model classes of that jar are used; EclipseLink, the Fennec runtime and the OSGi
services are excluded and not needed at build time.

## Quick start

The EORM model refers to Ecore (`Entity.class`, `accessibleObject`, `enumerated`, …), so the Ecore
model of the classes is given, here `src/main/ecore/library.ecore`:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.eclipse.daanse</groupId>
      <artifactId>org.eclipse.daanse.cwm.maven.eorm</artifactId>
      <version>0.0.1-SNAPSHOT</version>
      <executions>
        <execution>
          <goals>
            <goal>generate</goal>
          </goals>
          <configuration>
            <modelFile>${project.basedir}/src/main/cwm/mapping.cwmorm</modelFile>
            <ecoreFile>${project.basedir}/src/main/ecore/library.ecore</ecoreFile>
            <eormName>library.eorm</eormName>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

`mvn process-resources` writes `target/generated-resources/eorm/library.eorm` with a copy of
`library.ecore` next to it; the `.eorm` holds `<class href="library.ecore#//Book"/>`. The directory
becomes a resource directory, so the files land in the jar. When the Ecore model is generated in
the same build, let that run in `generate-sources`: this goal runs in `generate-resources`, which
comes later in the lifecycle.

From the command line (only parameters with a property; lists and maps cannot be set this way):

```shell
mvn org.eclipse.daanse:org.eclipse.daanse.cwm.maven.eorm:0.0.1-SNAPSHOT:generate \
    -Dfennec-eorm.modelFile=src/main/cwm/mapping.cwmorm \
    -Dfennec-eorm.ecoreFile=src/main/ecore/library.ecore
```

## What you get

| Output | Content |
| --- | --- |
| `<eormName>` (default `<model file name>.eorm`) | exactly one Fennec `EntityMappings` for the one chosen mapping set — a mapping set is never split |
| the `.ecore` files next to it | copies of the given `.ecore` files and of those they reference (when they live elsewhere) |

The `.eorm` is complete and explicit: `metadata-complete`, every entity with its table, every
basic, id and version with its column, every owning side with its join columns and
`referencedColumnName` or join table, every hierarchy with strategy and discriminator, every
relationship with its `targetEntity`, named queries, result set mappings, converters, listeners
and entity graphs as the CWM mapping states them. Nothing is left to JPA defaults.

## How classes and features are found

By names, the way the Ecore model of a CWM class model is named:

| CWM element | Ecore element |
| --- | --- |
| package | an entry of [`packageMap`](#input); else the EPackage of that name; among several of that name, the one whose super packages match the CWM containers |
| class, interface, enumeration, data type | the classifier of that name in the EPackage of its package; when no EPackage matches the package, the only classifier of that name in all given files |
| attribute | the feature of that name of the class's EClass (inherited features included) |
| navigable association end | the feature named after the end at the EClass of the **other** end's type (CWM 1.1, 4.5) |
| enumeration literal | the literal of that name |

A CWM name that is no Java identifier is matched in the form Ecore needs (`byte[]` → `byteArray`,
characters that are not allowed dropped and the next letter upper-cased). A class or feature that is not found is an error of the
conversion, so nothing silently goes missing. The `.eorm` itself refers to the Ecore elements by
name paths (`library.ecore#//Book/title`) as well.

## All settings

Set in `<configuration>`; the property in the second column sets the same value from the command
line or from `<properties>`.

### Input

| Parameter | Property | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `modelFile` | `fennec-eorm.modelFile` | file | — (required) | The CWM XMI with the mapping set (a `.cwmorm` file is XMI as well). Classes and tables may live in files it references by relative `href`; they load with it. |
| `mapping` | `fennec-eorm.mapping` | string | — | The name of the mapping set to convert. Without it the file must hold exactly one; with several, a missing or wrong name fails the build and says how many there are. |
| `ecoreFile` | `fennec-eorm.ecoreFile` | file | — | The `.ecore` of the mapped class model; files it references by `href` load with it. `ecoreFile` and `ecoreFiles` together must name at least one file. |
| `ecoreFiles` | — | list of files | — | Further `.ecore` files of the class model, e.g. one per package. Each must exist, otherwise the build fails. |
| `packageMap` | — | map | — | CWM packages whose EPackage is named differently: key = the CWM package by qualified name or a trailing part (`model.shop`, `shop`), value = the EPackage by namespace URI, qualified name (`shop.enumerations`) or name. Packages without entry are matched by name. |
| `extraPackages` | — | list of strings | — | Fully qualified names of generated EPackage interfaces to register before loading, for models that use further metamodels (e.g. `org.eclipse.daanse.rolap.mapping.model.RolapMappingPackage`); their jars go into the `<dependencies>` of the plugin. |

### Output

| Parameter | Property | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `outputDirectory` | `fennec-eorm.outputDirectory` | file | `${project.build.directory}/generated-resources/eorm` | Where the `.eorm` and the `.ecore` files next to it are written. `.ecore` files from elsewhere are copied; all of them move together, so the relative `href`s between them stay valid. |
| `eormName` | `fennec-eorm.eormName` | string | `<model file name>.eorm` | File name of the `.eorm`. |
| `attachResource` | `fennec-eorm.attachResource` | boolean | `true` | Add `outputDirectory` as a resource directory, so the files land in the jar. |

### Validation

| Parameter | Property | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `failOnValidation` | `fennec-eorm.failOnValidation` | boolean | `true` | The mapping set must pass the [explicit profile](#the-explicit-profile) before anything is written. `false` converts anyway and logs the findings — for reports on incomplete mappings; the result may then rely on defaults. |

## Recipes

### Choose the mapping set

```xml
<configuration>
  <modelFile>${project.basedir}/src/main/cwm/shop.xmi</modelFile>
  <mapping>sales</mapping>
  <ecoreFile>${project.build.directory}/generated-ecore/shop.ecore</ecoreFile>
</configuration>
```

For several `.eorm` files, one execution per mapping set, each with its own `id`, `mapping` and
`eormName`.

### A class model split into several files

One `.ecore` per package:

```xml
<configuration>
  <modelFile>${project.basedir}/src/main/cwm/shop.xmi</modelFile>
  <mapping>sales</mapping>
  <ecoreFiles>
    <ecoreFile>${project.build.directory}/generated-ecore-split/shop-types.ecore</ecoreFile>
    <ecoreFile>${project.build.directory}/generated-ecore-split/shop-classes.ecore</ecoreFile>
    <ecoreFile>${project.build.directory}/generated-ecore-split/shop-enums.ecore</ecoreFile>
  </ecoreFiles>
  <outputDirectory>${project.build.directory}/generated-resources/eorm-split</outputDirectory>
</configuration>
```

gives one `shop.eorm` whose classes point into `shop-classes.ecore`, next to copies of the
three files. Listing only `shop-classes.ecore` is enough as long as it references the other
two; listing all of them makes it independent of that.

### Model with further metamodels

A model that also holds a Daanse ROLAP catalog:

```xml
<plugin>
  <groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.cwm.maven.eorm</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <executions>
    <execution>
      <goals>
        <goal>generate</goal>
      </goals>
      <configuration>
        <modelFile>${project.basedir}/src/main/cwm/shop.xmi</modelFile>
        <ecoreFile>${project.build.directory}/generated-ecore/shop.ecore</ecoreFile>
        <extraPackages>
          <extraPackage>org.eclipse.daanse.rolap.mapping.model.RolapMappingPackage</extraPackage>
        </extraPackages>
      </configuration>
    </execution>
  </executions>
  <dependencies>
    <dependency>
      <groupId>org.eclipse.daanse</groupId>
      <artifactId>org.eclipse.daanse.rolap.mapping.model</artifactId>
      <version>0.0.1-SNAPSHOT</version>
    </dependency>
  </dependencies>
</plugin>
```

### EPackages named differently

An Ecore model works when its classes and features carry the CWM names. Packages may be named
differently:

```xml
<configuration>
  <modelFile>${project.basedir}/src/main/cwm/library.xmi</modelFile>
  <ecoreFile>${project.basedir}/src/main/model/bib.ecore</ecoreFile>
  <packageMap>
    <library>http://example.org/bib</library>
  </packageMap>
</configuration>
```

Without the entry a class is still found when its name is unique among all given files.

### Report on a mapping that is not complete yet

```xml
<failOnValidation>false</failOnValidation>
```

writes the `.eorm` and logs every finding of the explicit profile as a note `VALIDATION`.

## The explicit profile

JPA fills gaps silently (every attribute persistent, column named like the attribute, join column
`<attribute>_<pk>`, `SINGLE_TABLE` with `DTYPE`, …). The CWM-ORM model is meant to say everything,
so before converting, the mapping set is checked by `OrmModelValidator` of the module
`model/daanse/orm`:

| Rule | Checks |
| --- | --- |
| `MULTIPLICITY` | to-one mappings on ends with upper bound 1, to-many on more |
| `OWNING_SIDE` | exactly one owning side per association mapped from both ends |
| `E1` completeness | every own attribute and every navigable end of a mapped class has exactly one mapping (`Transient` if not persistent); no mapping of a foreign feature |
| `E2` column | basic, id and version of an entity name their column; what a mapped superclass or embeddable leaves open is overridden by every user; in `TABLE_PER_CLASS` all inherited columns |
| `E3` table | the columns of an entity lie in its table or a secondary table; nullability fits the lower bound (warning) |
| `E4` relationships | the owning side has exactly one connection (join, join table, primary-key join, `mapsId`), the inverse side none and a mapped opposite end; the target is an entity |
| `E5` join | foreign key or column pairs; columns in one table, references in the target table |
| `E6` inheritance | strategy at the root, discriminator column and values, own tables and primary-key joins per strategy |
| `E7` shared column | at most one writing use of a column per entity |
| `E8` identity | id or embedded id, id class exactly for several ids, id columns in the primary key |
| `E9` consistency | generator and strategy, converter or disabled, result class or result mapping, `orderBy` or `orderColumn`, result columns from the stored query, valid feature paths |
| `E10` names | the name of an anchored element is its anchor's name; unit-wide names unique |
| `E11` reverse (warning) | columns of mapped tables that no mapping uses |

Errors stop the build before any file is written (with `failOnValidation=true`).

## Messages and failures

| Rule | Severity | Meaning |
| --- | --- | --- |
| explicit profile (`E1`–`E11`, …) | error | the mapping set is not complete; nothing is written |
| `UNRESOLVED` | error | a CWM class, feature or enumeration without Ecore counterpart: the wrong or an incomplete `ecoreFile`/`ecoreFiles` |
| `FENNEC_EMBEDDED_ID`, `FENNEC_ID_CLASS` | warning | written as stated, but Fennec does not deploy `embedded-id` / `id-class` |
| `FENNEC_TABLE_GENERATOR` | warning | written; Fennec reports it and leaves the stored values alone |
| `ENUM_STRING` | warning | `enumerated=STRING` has no EORM form |
| `CONVERT`, `CALLBACK` | warning | several converts of an element collection, two callbacks of one kind: EORM keeps one |
| `DESCRIPTION`, `PK_JOIN_FLAGS`, `PARAMETER_CLASS`, `MAP_KEY_CLASS` | info | facts EORM cannot hold, counted or named |
| `VALIDATION` | error / warning | a finding of the explicit profile with `failOnValidation=false` |

Errors fail the build; warnings and information are logged.

## Loading the `.eorm` in Fennec

OSGi, with the pre-serialized mapping (Fennec `getting-started.md`, "Register the EntityMappings"):

```properties
# fennec.jpa.EORMLoader~library.cfg
fennec.jpa.eorm.model.target=(emf.name=library)
fennec.jpa.eorm.name=library
fennec.jpa.eorm.uri=file:/path/to/library.eorm
```

and the persistence unit binds it with `fennec.jpa.mapping.target=(fennec.jpa.orm.mapping.name=library)`.
The `href`s of the `.eorm` point at the `.ecore` next to it; the EPackage registered in the
framework must be the one they resolve to (the file next to it, or a URI mapping from
`library.ecore` to the registered package).

Without OSGi, with the Fennec EORM model on the class path:

```java
ResourceSet rs = EormWriter.newResourceSet();   // or: register EcorePackage, EORMPackage,
                                                // EPersistencePackage and EORMResourceFactoryImpl for "eorm"
EPackage library = (EPackage) rs.getResource(URI.createFileURI("…/library.ecore"), true).getContents().get(0);
rs.getPackageRegistry().put(library.getNsURI(), library);
EntityMappings mappings = EormWriter.load(rs, Path.of("…/library.eorm"));
```

## Troubleshooting

| Symptom | Cause and remedy |
| --- | --- |
| `… holds 2 mapping set(s); name one with <mapping>` | set `mapping` to the name of the set |
| `… holds 0 mapping set(s) named 'x'` | no mapping set has that name; the name is that of the `EntityMappings` in the model |
| `ecoreFiles entry does not exist` | the Ecore goal did not run before, or wrote other names (`fileNames`) |
| `no .ecore of the class model` | set `ecoreFile` (or `ecoreFiles`) |
| `UNRESOLVED` for classes or features | `ecoreFile` points at the wrong file; a split class model is not complete (list all files in `ecoreFiles`); an EPackage is named differently (`packageMap`); a class or feature in the Ecore model has another name than in CWM |
| `explicit` … errors and no file | the mapping set breaks the explicit profile; fix the mapping, or `failOnValidation=false` for a report |
| `Could not resolve … org.eclipse.fennec.persistence.orm:0.1.0-SNAPSHOT` | the Sonatype snapshot plugin repository is missing, or the build runs offline before the first online build |
| `Package with uri … not found` | a metamodel outside CWM/ORM: `extraPackages` plus the jar as plugin dependency |

## Tests of this module

`GenerateEormMojoTest` runs the goal in JUnit — the Mojo instantiated, its parameters set as a
POM sets them — on the projects under `src/test/resources/it`. Their `.ecore` files carry no CWM
annotation, so every class and feature is found by name:

- `library` (the many-to-one ORM tutorial: `objects.xmi`, `catalog.xmi`, `mapping.cwmorm`, and
  `library.ecore`): `fennec-eorm:generate` → `library.eorm`,
  `Entity.class` → `library.ecore#//Book`, metadata-complete, reload without unresolved proxies;
- the same with the Ecore file named `bibliothek.ecore` and a model holding two mapping sets,
  built in the temporary copy: without `mapping` the build fails, with `mapping=zweite` exactly one
  `two.eorm` is written over `bibliothek.ecore` (given by `ecoreFiles`); a missing `ecoreFiles`
  entry fails;
- `library` with its Ecore package renamed to `bib`: found by the unique class names, and through
  `packageMap`;
- without `ecoreFile`/`ecoreFiles`, or with a missing file, the build fails;
- a further EPackage named in `extraPackages` is registered before loading; an unknown one fails
  the build with its name;
- a mapping without the column of `Book.title` fails with `failOnValidation=true` before writing
  anything and converts with `false`;
- the plugin descriptor binds the goal to `generate-resources`, after `generate-sources`, where an
  Ecore model of the same build is written.
