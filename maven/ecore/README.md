# CWM class model to Ecore — Maven plugin `cwm-ecore`

`org.eclipse.daanse:org.eclipse.daanse.cwm.maven.ecore` turns the class model of a CWM XMI
(CWM 1.1 `core::Package`, `Class`, `Attribute`, `DataType`, `Constraint`, `datatypes::Enumeration`,
`relationships::Association`, `Generalization`, `behavioral::Interface`, `Operation`, `Method`) into
Ecore: `.ecore` files with the GenModel annotations EMF code generation reads and, if asked for, a
`.genmodel`. It has one goal,
**`cwm-ecore:generate`**, bound by default to the phase `generate-sources`.

The conversion itself lives in [`emf/ecore`](../../emf/ecore/README.md), which lists every
mapping rule. This page is about using the plugin.

- [Requirements](#requirements)
- [Quick start](#quick-start)
- [What you get](#what-you-get)
- [All settings](#all-settings)
- [Recipes](#recipes)
- [Messages and failures](#messages-and-failures)
- [Using the result](#using-the-result)
- [Troubleshooting](#troubleshooting)
- [Tests of this module](#tests-of-this-module)

## Requirements

| What | Version / value |
| --- | --- |
| Java | 21 or newer (the plugin is compiled for Java 21) |
| Maven | 3.9 or newer (built against the 3.9 plugin API; this repository builds with Maven 4) |
| Plugin coordinates | `org.eclipse.daanse:org.eclipse.daanse.cwm.maven.ecore:0.0.1-SNAPSHOT` |
| Where it comes from | built from this repository: `mvn install -pl maven/ecore -am` puts it and its core into the local repository |
| Dependencies | only its core `org.eclipse.daanse.cwm.emf.ecore` (plus the Maven API) |

All CWM 1.1 packages and the Daanse ORM package are on the plugin's class path (they come with
[`emf/standalone`](../../emf/standalone/README.md) through the core) and registered before the model is
loaded, so an XMI that uses only those needs no further setup. A model that also uses other
metamodels (for example the Daanse ROLAP mapping) needs
[`extraPackages`](#a-model-that-uses-further-metamodels).

## Quick start

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.eclipse.daanse</groupId>
      <artifactId>org.eclipse.daanse.cwm.maven.ecore</artifactId>
      <version>0.0.1-SNAPSHOT</version>
      <executions>
        <execution>
          <goals>
            <goal>generate</goal>
          </goals>
          <configuration>
            <modelFile>${project.basedir}/src/main/cwm/objects.xmi</modelFile>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

`mvn generate-sources` writes one `.ecore` per root package of the class model into
`target/generated-ecore/` and adds that directory as a resource directory, so the files end up in
the jar.

From the command line, without a POM entry (only parameters with a property can be set this way;
lists and maps cannot):

```shell
mvn org.eclipse.daanse:org.eclipse.daanse.cwm.maven.ecore:0.0.1-SNAPSHOT:generate \
    -Dcwm-ecore.modelFile=src/main/cwm/objects.xmi -Dcwm-ecore.genModel=true
```

With `<pluginGroup>org.eclipse.daanse</pluginGroup>` in `~/.m2/settings.xml` the short form
`mvn cwm-ecore:generate …` works too.

## What you get

| Output | When | Content |
| --- | --- | --- |
| `<package>.ecore` | always, one per root package | the EPackage with its classes, data types, enumerations, sub-packages (unless split), and the GenModel annotations: `prefix` on every package, `basePackage` and the `genModelAnnotations` on every root package |
| a file per package | `splitPackages=true` | every converted package as a root EPackage of its own; names from `fileNames` |
| `<first file>.genmodel` | `genModel=true` | one GenModel over all written packages, `operationReflection` on, compliance level 17, root class `MinimalEObjectImpl$Container` |

The files refer to each other by relative `href`s, so the directory can be moved as a whole.

What a converted model contains, in short:

| CWM | Ecore |
| --- | --- |
| package, model, subsystem with classes | `EPackage`; `nsURI` = `nsUriBase` + the lower-case path of the package in the model |
| class, interface | `EClass` (abstract, interface), super types from the generalizations |
| data type, enumeration | `EDataType` with a Java class, `EEnum` |
| attribute | `EAttribute`; typed by a class: containment `EReference` |
| binary association | `EReference` per navigable end, `eOpposite` when both ends are navigable, containment from `ak_composite` |
| operation, parameter | `EOperation`, `EParameter` |
| method in OCL / Java | the operation's body: OCL invocation delegate / GenModel `body` |
| constraint in OCL / Java | invariant (Ecore `constraints` + OCL validation delegate) / invariant operation with GenModel `body` (unless `constraints=false`) |
| description `remark` or `documentation` | GenModel `documentation`, becomes Javadoc (unless `documentation=false`) |
| every element | annotation `http://daanse.eclipse.org/cwm/Element` with `id` (the `xmi:id`) and `path` (qualified name), the trace back to CWM |
| tag `daanse.ecore.<property>` | that Ecore property, e.g. `transient`, `serializable`, `lowerBound`, a map entry class |
| tag `derived=true` plus a constraint on the feature | derived feature with a Java getter (GenModel `get`) or an OCL `derivation` |
| tag `daanse.ecore.eClassifier=<nsURI>#//<name>` | a classifier of another metamodel (e.g. Ecore's `EObject`) as super type or type |

## All settings

Set in `<configuration>`; the property in the second column sets the same value from the command
line or from `<properties>`.

### Input

| Parameter | Property | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `modelFile` | `cwm-ecore.modelFile` | file | — (required) | The CWM XMI. Files it references by relative `href` (catalogs, other models) load with it. |
| `packages` | — | list of strings | all packages with class model content | Which packages to convert: qualified names, dot separated (`model.classes.shop`), or a trailing part (`shop`); a named package with everything below it. Without entries: every package with a class, an interface, an enumeration, or a data type a class uses; catalogs, record files, OLAP and other models stay out. |
| `extraPackages` | — | list of strings | — | Fully qualified names of generated EPackage interfaces to register before loading, for models that use further metamodels, e.g. `org.eclipse.daanse.rolap.mapping.model.RolapMappingPackage`. Their jars go into the `<dependencies>` of the plugin. |

### Output

| Parameter | Property | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `outputDirectory` | `cwm-ecore.outputDirectory` | file | `${project.build.directory}/generated-ecore` | Where the `.ecore` and `.genmodel` files are written. |
| `splitPackages` | `cwm-ecore.splitPackages` | boolean | `false` | `true`: every converted package becomes a root EPackage with a file of its own, instead of a sub-package inside the file of the package around it. Names, namespace URIs and the Java packages of the genmodel stay what they are when nested. |
| `fileNames` | — | map | — | File name per written package. Key: the package's qualified name or a trailing part of it, or the name of the root EPackage; value: the file name, `.ecore` is appended when it has no extension. Packages without entry are named `<package name>.ecore`. A key that names no written package or several, or one file name for two packages, fails the build. |
| `attachResource` | `cwm-ecore.attachResource` | boolean | `true` | Add `outputDirectory` as a resource directory, so the files land in the jar. `false` for a second, private execution. |

### Types and names

| Parameter | Property | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `dataTypes` | — | map | see below | CWM data type name → Java class, e.g. `<Text>java.lang.String</Text>`. Entries here win over the defaults; a TaggedValue `daanse.ecore.instanceClassName` on the data type wins over both. A data type without a Java class fails the build. |
| `reuseEcoreDataTypes` | `cwm-ecore.reuseEcoreDataTypes` | boolean | `false` | Use the `EcorePackage` data types (`EString`, `ELongObject`, …) with the same Java class instead of generating EDataTypes; a package that then holds nothing is not written. |
| `nsUriBase` | `cwm-ecore.nsUriBase` | string | `http://daanse.eclipse.org/cwm/ecore` | Prefix of every namespace URI; the lower-case path of the package in the model follows. |
| `oclDelegateUri` | `cwm-ecore.oclDelegateUri` | string | `http://www.eclipse.org/emf/2002/Ecore/OCL` | Annotation source and delegate URI of OCL invariants and OCL operation bodies. The default is classic Eclipse OCL, as in the CWM metamodels of this repository; `http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot` writes for Pivot OCL. |
| `documentation` | `cwm-ecore.documentation` | boolean | `true` | Write the CWM descriptions (type `remark` or `documentation`) as GenModel `documentation`; `false` leaves them out. |
| `constraints` | `cwm-ecore.constraints` | boolean | `true` | Convert the OCL and Java constraints into invariants; `false` leaves them out, and no validation delegate is declared. Method bodies are converted either way. |

Default data types: `Integer`, `Long` → `java.lang.Long` (CWM integers are not bounded to 32 bit);
`String`; `Boolean`; `Double`, `Float`, `Short`, `Byte`, `Character` → `java.lang.*`; `Date` →
`java.util.Date`; `Time`, `Timestamp` → `java.time.LocalTime`, `LocalDateTime`; `Decimal`,
`BigDecimal` → `java.math.BigDecimal`; `Binary`, `byte[]` → `byte[]`; `LocalDate`, `LocalTime`,
`LocalDateTime`, `OffsetDateTime`, `Instant`, `UUID`, `BigInteger` → their Java classes; the Java
primitive names stay primitive.

### GenModel

| Parameter | Property | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `basePackage` | `cwm-ecore.basePackage` | string | — | Java base package of the generated code, written as GenModel `basePackage` onto every root EPackage and into the `.genmodel`; a package taken out of its parent by `splitPackages` gets `<basePackage>.<parent path>`, so the Java packages are the same as nested. |
| `genModelAnnotations` | — | map | — | Further GenModel properties for every root EPackage, e.g. `oSGiCompatible`, `copyrightText`, `loadInitialization`, `literalsInterface`, `fileExtensions`, as `<oSGiCompatible>true</oSGiCompatible>`. `basePackage` and `prefix` are not taken from here; `prefix` is the capitalized package name. |
| `genModel` | `cwm-ecore.genModel` | boolean | `false` | Also write a `.genmodel`, named after the first `.ecore`. |

A code generator that takes the `.ecore` alone reads the annotations; an annotation usually wins
over the generator's own setting, which is why the goal writes only `prefix`, `basePackage` and
what `genModelAnnotations` names. The genmodel's model directory is `/<artifactId>/src/main/java`
and its plugin id the artifact id.

### Build control

| Parameter | Property | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `failOnNotes` | `cwm-ecore.failOnNotes` | boolean | `false` | Fail on warnings too. Errors always fail the build. |

## Recipes

### Choose packages

```xml
<configuration>
  <modelFile>${project.basedir}/src/main/cwm/model.xmi</modelFile>
  <packages>
    <package>classes.types</package>
    <package>classes.shop</package>
  </packages>
</configuration>
```

A class whose attribute, parameter, association end or super class lies in a package that is not
chosen fails with a message naming that package — add it to `packages`, or, for data types, set
`reuseEcoreDataTypes`.

### Split packages into named files

```xml
<configuration>
  <modelFile>${project.basedir}/src/main/cwm/model.xmi</modelFile>
  <packages>
    <package>classes.types</package>
    <package>classes.shop</package>
  </packages>
  <splitPackages>true</splitPackages>
  <fileNames>
    <types>shop-types</types>
    <shop>shop-classes</shop>
    <shop.enumerations>shop-enums</shop.enumerations>
  </fileNames>
</configuration>
```

Result: `shop-types.ecore`, `shop-classes.ecore`, `shop-enums.ecore`, each with one root package,
referring to each other by relative `href`. Without `splitPackages`, `shop.enumerations` would be a
sub-package inside `shop-classes.ecore`. `fileNames` also
works without splitting, for the root packages.

### Own data types

```xml
<configuration>
  <modelFile>${project.basedir}/src/main/cwm/model.xmi</modelFile>
  <dataTypes>
    <Text>java.lang.String</Text>
    <Amount>java.math.BigDecimal</Amount>
  </dataTypes>
</configuration>
```

or on the data type in the model: TaggedValue `daanse.ecore.instanceClassName` =
`java.lang.String`. `<reuseEcoreDataTypes>true</reuseEcoreDataTypes>` maps them onto the Ecore
data types instead of writing a data type package.

### A model that uses further metamodels

```xml
<plugin>
  <groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.cwm.maven.ecore</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <executions>
    <execution>
      <goals>
        <goal>generate</goal>
      </goals>
      <configuration>
        <modelFile>${project.basedir}/src/main/cwm/model.xmi</modelFile>
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

### Generate Java code from the result

The Daanse EMF code generator takes the `.ecore` alone and reads the GenModel annotations in it.
Both goals run in `generate-sources`; goals of one phase run in POM order, so declare `cwm-ecore`
first.

```xml
<plugin>
  <groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.cwm.maven.ecore</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <executions>
    <execution>
      <goals>
        <goal>generate</goal>
      </goals>
      <configuration>
        <modelFile>${project.basedir}/src/main/cwm/model.xmi</modelFile>
        <basePackage>org.example.model</basePackage>
        <genModelAnnotations>
          <copyrightText>Copyright (c) 2026 Example</copyrightText>
        </genModelAnnotations>
      </configuration>
    </execution>
  </executions>
</plugin>
<plugin>
  <groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.tooling.emf.codegen.maven</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <executions>
    <execution>
      <goals>
        <goal>generate</goal>
      </goals>
      <configuration>
        <ecoreFile>${project.build.directory}/generated-ecore/shop.ecore</ecoreFile>
        <outputDirectory>target/generated-sources/emf</outputDirectory>
      </configuration>
    </execution>
  </executions>
  <dependencies>
    <dependency>
      <groupId>biz.aQute.bnd</groupId>
      <artifactId>biz.aQute.bndlib</artifactId>
      <version>7.2.0</version>
    </dependency>
  </dependencies>
</plugin>
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>build-helper-maven-plugin</artifactId>
  <version>3.6.0</version>
  <executions>
    <execution>
      <phase>generate-sources</phase>
      <goals>
        <goal>add-source</goal>
      </goals>
      <configuration>
        <sources>
          <source>target/generated-sources/emf</source>
        </sources>
      </configuration>
    </execution>
  </executions>
</plugin>
```

A generator that needs a `.genmodel` gets one with `<genModel>true</genModel>`; it is named after
the first `.ecore` and carries the same base package.

The generated code needs `org.eclipse.emf:org.eclipse.emf.ecore` and `…ecore.xmi` and, for the
annotations the generator writes, `org.eclipse.fennec.emf:org.eclipse.fennec.emf.osgi.api:0.1.1`
and `org.osgi:org.osgi.annotation.versioning:1.1.2` (scope `provided` is enough). A method in Java
is the body of the generated method; a constraint in Java is an invariant operation the
generated validator calls; OCL methods and constraints run through the OCL delegates (see
[Using the result](#using-the-result)).

### Pivot OCL instead of classic OCL

```xml
<oclDelegateUri>http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot</oclDelegateUri>
```

### Two layouts from one model

Several executions with their own `id`, `outputDirectory` and `attachResource=false` for the extra
ones, e.g. the nested layout for code generation and a split one for another consumer:

```xml
<execution>
  <id>split</id>
  <goals>
    <goal>generate</goal>
  </goals>
  <configuration>
    <modelFile>${project.basedir}/src/main/cwm/model.xmi</modelFile>
    <outputDirectory>${project.build.directory}/generated-ecore-split</outputDirectory>
    <splitPackages>true</splitPackages>
    <attachResource>false</attachResource>
  </configuration>
</execution>
```

### Together with the EORM goal

The Fennec EORM goal [`fennec-eorm:generate`](../eorm/README.md) reads these
`.ecore` files (`ecoreFile`, or `ecoreFiles` after a split) and runs in `generate-resources`,
after this goal.

## Messages and failures

The goal logs every note of the conversion with its rule, the qualified name of the CWM element
and a message. Errors fail the build; warnings fail it only with `failOnNotes`.

| Rule | Severity | Typical cause |
| --- | --- | --- |
| `TYPE` | error | data type without Java class; a type in a package that is not converted (the message names the package) |
| `UNRESOLVED` | error | a super class or association end type outside the converted classes |
| `N_ARY`, `ASSOCIATION_CLASS`, `AGGREGATION`, `CYCLE` | error | association with more than two ends, association class, composite end with upper bound > 1 or aggregation at both ends, cyclic inheritance |
| `METHOD` | error | method of an operation that is not converted or no operation of its class or an ancestor (C-4-10, C-4-11), two methods of one language for one operation and class (C-4-12) |
| `ECORE` | error | the EMF `Diagnostician` rejects the result |
| `NAME` | warning | a name that is no Java identifier was adjusted; a second invariant of the same name got a suffix |
| `CONSTRAINT`, `CONSTRAINT_LANGUAGE` | warning | constraint without a place for an invariant (on an operation, an association, several classifiers), Java on a data type, a language other than OCL or Java |
| `METHOD`, `METHOD_LANGUAGE` | warning | OCL body of an operation without result, OCL and Java method for one operation, a language other than OCL or Java |
| `ENUM_VALUE`, `MULTIPLICITY`, `SCOPE`, `FEATURE` | warning | duplicate literal value, upper bound 0, classifier scope, a feature kind without Ecore form |
| `TYPE_ALIAS` | error / warning | an alias of itself / of a class or enumeration |
| `DEFAULT`, `TAG`, `EXTERNAL` | warning | an initial value that is no literal; a `daanse.ecore.*` tag without property or with a wrong value; what a classifier of another metamodel cannot get |
| `NOT_NAVIGABLE`, `IMPORT` | info | association without navigable end; a package that imports elements |

Wrong `fileNames` (a key naming no or several packages, one file name twice) fail the build
before anything is written.

## Using the result

Load the files like any `.ecore`; the trace annotation `http://daanse.eclipse.org/cwm/Element`
(`id`, `path`) leads back to the CWM element.

OCL invariants and OCL operation bodies need the OCL delegates of the chosen implementation. For
classic Eclipse OCL outside Eclipse (dependencies `org.eclipse.ocl:ecore`, `org.eclipse:ocl`,
`org.eclipse.ocl:common`, `lpg.runtime:java`):

```java
OCLDelegateDomain.initialize(null);
DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG,
        new OCLDelegateDomainFactory());

Diagnostic d = Diagnostician.INSTANCE.validate(object);   // invariants
Object r = object.eInvoke(operation, arguments);           // OCL bodies, dynamic or generated
```

Java bodies and Java invariants run in generated code only; on dynamic instances EMF skips
invariant operations without OCL body.

## Troubleshooting

| Symptom | Cause and remedy |
| --- | --- |
| `type … is not among the converted classifiers; its package … is not converted` | add that package to `packages`, or `reuseEcoreDataTypes` for data types |
| `data type 'X' has no Java instance class` | add `<X>java.…</X>` to `dataTypes` or the TaggedValue on the data type |
| `file name entry 'x' names 0 of the written packages` | the key matches no package: use its qualified name or a trailing part; with `splitPackages=false` only root packages can be named |
| the model does not load: `Package with uri … not found` | a metamodel outside CWM/ORM: `extraPackages` plus the jar as plugin dependency |
| the code generator runs before the `.ecore` exists | both in `generate-sources`: declare `cwm-ecore` before the generator in the POM |
| OCL invariants are not checked at runtime | register the OCL delegates (above); classic and Pivot need different URIs (`oclDelegateUri`) |

## Tests of this module

`GenerateEcoreMojoTest` runs the goal in JUnit — the Mojo instantiated, its parameters set as a
POM sets them — on the test projects under `src/test/resources/it`:

- `library` (the class side of the many-to-one ORM tutorial, package `library`) → `library.ecore`
  with `Book` and `Publisher` as opposite references; with `splitPackages`, `genModel` and
  `fileNames` → `library-classes.ecore` and `library-classes.genmodel`, and a `fileNames` key
  that names no package fails the build;
- the same with its data type `String` renamed to `Text` → fails until `dataTypes` maps it, then
  writes `library.ecore` with the GenModel annotations `basePackage`, `prefix` and those of
  `genModelAnnotations`, and `library.genmodel` with the base package;
- with a description and an OCL constraint added to `Book`: documentation and invariant in the
  `.ecore`, both gone with `documentation=false` and `constraints=false`;
- a missing `modelFile` fails;
- the plugin descriptor binds the goal to `generate-sources`, before the `generate-resources` of
  `fennec-eorm:generate`.
