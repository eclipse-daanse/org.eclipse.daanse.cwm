# org.eclipse.daanse.cwm.orm.fennec.eorm.core

Converts a CWM-ORM mapping set (`org.eclipse.daanse.cwm.model.daanse.orm`) into the
`EntityMappings` model of [Eclipse Fennec Persistence](https://github.com/eclipse-fennec/emf.persistence-jpa)
and writes it as a `.eorm` file — the file Fennec loads instead of deriving a mapping from the
Ecore model by its naming rules.

```java
EntityMappings cwm = CwmOrmToEorm.mappingsIn(resource).get(0);

// the .ecore files of the class model given: .eorm written next to (copies of) them
EormGenerator.Output out = EormGenerator.withEcore(cwm, Path.of("model/library.ecore"), dir, "library.eorm", false);

// or step by step
CwmOrmToEorm.Result r = CwmOrmToEorm.convert(cwm, EcoreIndex.of(ecorePackages));
EormWriter.save(r.eorm(), dir.resolve("library.eorm"));
```

| Type | Role |
| --- | --- |
| `CwmOrmToEorm` | `convert(EntityMappings, EcoreIndex[, lenient])` → `Result{eorm, notes}`; `requireExplicit`, `mappingsIn(Resource)` |
| `EcoreIndex` | finds the Ecore element of a CWM element **by names**: package (by name, by its containers when several share it, or an explicit package map), classifier name, feature name; a navigable association end at the class of the other end. No annotation is read, so any Ecore model with the CWM names works |
| `EormWriter` | resource set as Fennec sets it up without OSGi (Ecore, EORM, EPersistence registered; `EORMResourceFactoryImpl` for `.eorm`), `save`, `load` |
| `EormGenerator` | the whole way to files: `withEcore` takes the `.ecore` files of the class model — one file and those it references, or a list of files (e.g. one per package), optionally a package map — and copies them next to the `.eorm`; one mapping set always gives one `.eorm` |
| `Note` | a finding: severity, rule, CWM element, message |
| `EormNotes` | the rules of the notes |
| `InvalidMappingException` | a mapping set that breaks the explicit profile, with the validator findings |

## Dependency

Fennec Persistence is published as snapshot only:

```xml
<dependency>
  <groupId>org.eclipse.fennec.persistence</groupId>
  <artifactId>org.eclipse.fennec.persistence.orm</artifactId>
  <version>0.1.0-SNAPSHOT</version>   <!-- ${fennec.persistence.version} in ../pom.xml -->
  <exclusions>…</exclusions>
</dependency>
```

from `https://central.sonatype.com/repository/maven-snapshots/` (repository id `ossrh`). Only the
generated EMF models in that jar are used — `org.eclipse.fennec.persistence.eorm` (`EORMPackage`,
`EORMFactory`, `util.EORMResourceFactoryImpl`) and `org.eclipse.fennec.persistence.epersistence`
— so EclipseLink (`org.eclipse.persistence:*`), the Fennec runtime
(`org.eclipse.fennec.persistence:org.eclipse.fennec.persistence`), the OSGi service jars
(`org.osgi:*`), `org.eclipse.fennec.emf:*` and Fennec's EMF versions are excluded; the tests run
the full conversion and reload without them. A fresh machine needs one online build (or
`mvn dependency:get -Dartifact=org.eclipse.fennec.persistence:org.eclipse.fennec.persistence.orm:0.1.0-SNAPSHOT`)
before offline builds work.

## Validation first

Before converting, `OrmModelValidator.check` runs the explicit profile of the CWM-ORM model
(rules `MULTIPLICITY`, `OWNING_SIDE`, `E1`–`E11`). Errors stop the conversion with an
`InvalidMappingException` — `EormGenerator` checks before it writes anything. `lenient` converts
anyway and reports the findings as notes (rule `VALIDATION`); that is for reports, the result may
rely on defaults. Every derived fact (table of a subclass, object package, schema, catalog,
discriminator type, optionality, join column pairs of a foreign key, feature paths, procedure
parameters) comes from `OrmBindings`.

## Mapping

| EORM | from CWM-ORM (and the Ecore index) |
| --- | --- |
| `EntityMappings.name`, `description`, `version` | mapping set name, description, `3.1` |
| `EntityMappings.package` | `OrmBindings.objectPackage` → its EPackage, names dot separated (e.g. `library`, `shop.sales`) |
| `EntityMappings.schema` / `catalog` | only when every entity table lies in the same schema / catalog (`OrmBindings.schema/catalog`) |
| `EntityMappings.access` | `access` when set |
| `persistenceUnitMetadata.xmlMappingMetadataComplete` | always — the mapping is complete, nothing comes from annotations |
| `persistenceUnitDefaults` | `delimitedIdentifiers`, `cascadePersist`, `entityListeners` (copies of the default listeners) |
| `Entity.name`, `class` (EClass), `accessibleObject` (`EClassObject{eclass, name}`) | entity name; `cwmClass` → index; name = qualified class name |
| `Entity.table{name, schema, catalog}` | `OrmBindings.tableOf` — a single-table subclass names the hierarchy's table; an abstract table-per-class root without table gets none |
| `Entity.cacheable`, `access` | when set |
| `Entity.inheritance.strategy`, `discriminatorColumn{name, discriminatorType, length}`, `discriminatorValue` | `inheritanceStrategy`; `cwmDiscriminatorColumn`, type from `OrmBindings.discriminatorType` (integer types → `INTEGER`, `CHAR(1)` → `CHAR`, else `STRING` with the column length); `discriminatorValue` |
| `Entity.primaryKeyJoinColumn*{name, referencedColumnName}`, `primaryKeyForeignKey` | `primaryKeyJoin` |
| `Entity.secondaryTable*{name, schema, catalog, primaryKeyJoinColumn*, primaryKeyForeignKey}` | `secondaryTable`, table = `OrmBindings.tableOf` |
| `Entity/MappedSuperclass.idClass.class` (EClass) | `cwmIdClass` → index |
| `…entityListeners`, `prePersist` … `postLoad`, `excludeDefaultListeners`, `excludeSuperclassListeners` | `listener` (one copy of the listener per use: `class` EClass + its callbacks), own `lifecycleCallback`s (`methodName` = `cwmOperation.name`), the flags |
| `Entity.attributeOverride/associationOverride/convert` | `OverrideHost`, `name` = `OrmBindings.path(cwmPath)` |
| `MappedSuperclass.class`, `Embeddable.class` (EClass) | `cwmClass` → index |
| `Attributes` / `EmbeddableAttributes`: `id`, `embeddedId`, `version`, `basic`, the relationships, `elementCollection`, `embedded`, `transient` | the typed containments; `name` = name of the Ecore feature; `accessibleObject` = `EFeatureObject{feature, name}` where EORM has one — attribute → `EAttribute` (containment `EReference` if class-typed), navigable end → `EReference` |
| `Column{name, table, nullable, insertable, updatable, length, precision, scale}` | `column.cwmColumn`; `table` only when the column lies outside the entity's primary table (secondary table); `nullable` from `isNullable` (`columnNoNulls` → `false`); flags when set; facets when > 0 |
| `Basic.optional` | `OrmBindings.optional` (lower bound 0, or no multiplicity) |
| `Base.temporal` | `DATE`/`TIME`/`TIMESTAMP` from the SQL type — only for `java.util.Date`/`Calendar` attributes (`java.time` types take none) |
| `SimpleBase.lob` | SQL type `BLOB`, `CLOB`, `NCLOB` |
| `SimpleBase.enumerated` (EEnum) | the EEnum of the attribute type whenever it is an enumeration; EORM has no ORDINAL/STRING beside it, `enumerated=STRING` is reported (`ENUM_STRING`) |
| `SimpleBase.fetch`, `convert{converter}` | `fetch`; `convert`, converter = qualified name of the converter class |
| `Id.generatedValue{strategy, generator}` | `generatedValue`; generator name |
| `*Ref.targetEntity` | the type of `cwmAssociationEnd` → its EClass, qualified name |
| `*Ref.fetch`, `cascade{cascadeAll…}`, `optional` (to-one), `orphanRemoval` | flags of the relationship mapping; `optional` from `OrmBindings.optional` |
| `MappedByRef.mappedBy` | inverse side (`owningSide=false`): the name of the other end of the association |
| `joinColumn*{name, referencedColumnName, nullable, insertable, updatable, table}`, `foreignKey{name, constraintMode}` | `OrmBindings.joinColumns`: with `cwmForeignKey` its columns paired with the unique key's columns and `CONSTRAINT` + FK name; bare column pairs → `NO_CONSTRAINT`; `table` for to-one joins in a secondary table |
| `joinTable{name, schema, catalog, joinColumn*, foreignKey, inverseJoinColumn*, inverseForeignKey}`, `collectionTable{…}` | `JoinTable` / `CollectionTable`, table = `OrmBindings.tableOf` |
| `ManyToOne/OneToOne.id`, `mapsId` | `id`; `mapsId` = `""` for the whole id, else the name of `cwmMapsIdAttribute` |
| `OneToOne.primaryKeyJoinColumn*`, `primaryKeyForeignKey` | `primaryKeyJoin` |
| `orderBy` | `Ordering*`: `OrmBindings.path`, followed by `DESC` when descending, comma separated |
| `orderColumn{name, nullable, insertable, updatable}` | `orderColumn` |
| `mapKey{name}`, `mapKeyClass{class}`, `mapKeyEnumerated`, `mapKeyColumn`, `mapKeyJoinColumn*`, `mapKeyForeignKey`, `mapKeyAttributeOverride*`, `mapKeyConvert*` | `MapKey` (a key classifier that is no class: `MAP_KEY_CLASS`) |
| `ElementCollection.targetClass`, `collectionTable`, `column`, overrides, `convert` | qualified name of the element type; one convert (more: `CONVERT`) |
| `EntityMappings.converter{class, autoApply}` | `converter`, `cwmClass` → index |
| `sequenceGenerator{name, sequenceName, schema, catalog, initialValue, allocationSize}` | `SequenceGenerator`, schema/catalog from `cwmSchema` |
| `tableGenerator{name, table, schema, catalog, pkColumnName, valueColumnName, pkColumnValue, …}` | `TableGenerator`, table = owner of `cwmPkColumn` |
| `namedQuery{name, query, lockMode, hint*}` | `NamedQuery` |
| `namedNativeQuery{name, query, resultClass, resultSetMapping, hint*}` | `cwmQuery.query.body`; result class qualified name; result-set mapping name |
| `namedStoredProcedureQuery{name, procedureName, parameter*{name, mode}, resultClass*, resultSetMapping*}` | `cwmProcedure` and `OrmBindings.parameters` (`pdk_in` → `IN`, `pdk_inout` → `INOUT`, `pdk_out`/`pdk_return` → `OUT`) |
| `sqlResultSetMapping{entityResult*{entityClass, discriminatorColumn, fieldResult*{name, column}}, constructorResult*{targetClass, column*}, columnResult*{name, class}}` | class names qualified; column names of the query column set's columns |
| `namedEntityGraph{name, includeAllAttributes, namedAttributeNode*{name, subgraph, keySubgraph}, subgraph*{name, class, …}}` | `NamedEntityGraph`; names of features and subgraphs; subgraph class qualified |
| `description` (EntityMappings, Entity, MappedSuperclass, Embeddable, listeners, callbacks, overrides, converts, foreign keys, generators, queries, hints, result-set mappings) | `OrmElement.description`; a join's description goes to its foreign key |

A *qualified name* is the instance class name of the classifier when it has one, else the names
of its EPackages and its own name, dot separated (`shop.Customer`); `Entity.accessibleObject`
uses the same, so `targetEntity` and the entity's class name match.

## Notes (`EormNotes`)

| Rule | Severity | Meaning |
| --- | --- | --- |
| `UNRESOLVED` | ERROR | a CWM class, feature or enumeration without Ecore counterpart in the index |
| `FENNEC_EMBEDDED_ID` | WARNING | an embedded id is written, but Fennec does not deploy `embedded-id` (one `id` per key attribute instead) |
| `FENNEC_ID_CLASS` | WARNING | an id class is written, but Fennec does not deploy `id-class` |
| `FENNEC_TABLE_GENERATOR` | WARNING | a table generator is written; Fennec reports it and leaves the stored values alone |
| `ENUM_STRING` | WARNING | `enumerated=STRING` cannot be expressed in EORM |
| `CONVERT`, `CALLBACK` | WARNING | an element collection with several converts, two callbacks of one kind: EORM keeps one |
| `DESCRIPTION` | INFO | descriptions of elements without an EORM `description` (attribute mappings, columns, join tables, orderings, results), counted |
| `PK_JOIN_FLAGS` | INFO | `insertable`/`updatable` of a primary-key join: EORM primary-key join columns have neither |
| `PARAMETER_CLASS` | INFO | stored procedure parameters without `class` — EORM types it as EClass, the parameters have SQL types |
| `MAP_KEY_CLASS` | INFO | a map key type that is no class |
| `VALIDATION` | ERROR/WARNING | a validator finding, in lenient mode |

Fennec reads neither `embedded-id` nor `id-class` and warns on `table-generator` and `index`;
these are written as the CWM mapping states them and only reported, not converted into something
else. The converter writes no `index`.

## Loading the result in Fennec

The `.eorm` refers to the Ecore model by relative `href`s (`library.ecore#//Book`), so the
`.ecore` files belong next to it — `EormGenerator` writes or copies them there. Without OSGi:

```java
ResourceSet rs = EormWriter.newResourceSet();
EPackage library = (EPackage) rs.getResource(URI.createFileURI(".../library.ecore"), true).getContents().get(0);
rs.getPackageRegistry().put(library.getNsURI(), library);
EntityMappings mappings = EormWriter.load(rs, Path.of(".../library.eorm"));   // hrefs resolve to `library`
```

With OSGi, Fennec's `fennec.jpa.EORMLoader` reads the file
(`fennec.jpa.eorm.uri=file:…/library.eorm`, `fennec.jpa.eorm.model.target=(emf.name=library)`);
the EPackage it registers must be the one the `href`s resolve to (the `.ecore` next to the
`.eorm`, or a URI mapping from `library.ecore` to the registered package).

## Design decisions

- **Modules and coordinates.** `orm/fennec.eorm.core`,
  `org.eclipse.daanse:org.eclipse.daanse.cwm.orm.fennec.eorm.core`, Java package
  `org.eclipse.daanse.cwm.orm.fennec`; the plugin `maven/eorm`
  (`org.eclipse.daanse:org.eclipse.daanse.cwm.maven.eorm`) depends on this core only.
- **Fennec's model from the snapshot repository**, not vendored: the jar is the reference for
  `eorm.ecore`; only its EMF models are used.
- **One direction.** CWM-ORM → EORM; an `.eorm` is not read back into CWM.
- **Ecore given, bound by names.** The EORM model refers to Ecore elements, so the Ecore model of
  the class model is an input. It is matched by names only — the `.eorm` itself refers to it by
  name paths — so this module does not depend on how the Ecore model was written; any Ecore model
  with the CWM names works, packages named differently through a package map.
- **`.eorm` next to the `.ecore`, relative `href`s.** The output directory is self-contained:
  `withEcore` copies the `.ecore` files there when they were written elsewhere.
- **Fennec's limits are reported, not worked around.** Embedded ids, id classes and table
  generators are written as the CWM mapping states them, with a warning each.
- **Everything explicit.** Values JPA would default are written: columns with nullability, join
  columns with referenced columns, foreign keys with constraint mode (`NO_CONSTRAINT` for joins
  without a catalog foreign key), table names of single-table subclasses, `optional` of every
  basic and to-one, `metadata-complete`, version `3.1`.
- **Class names are qualified Ecore names** (EPackage path + class name, or the instance class);
  `EntityMappings.package` is the dotted path of the object package's EPackage.
- **`temporal` only for `java.util.Date`/`Calendar`**, `lob` only for LOB SQL types, `enumerated`
  whenever the type is an enumeration.
- **Entity listeners are copied per use**: EORM has no unit-wide listener list, so each entity
  (and the defaults) carries its own copy.

## Tests

- `ConversionFormsTest` (32) — one test per conversion form, inputs built with the CWM-ORM create
  helpers on a small library (`Fixture`), its Ecore model from `src/test/resources/ecore/fixture`.
- `EcoreIndexTest` (4) — the name rules on a hand-written Ecore model: same class name in two
  packages, a name that is no Java identifier, an end at the class of the other end, enumeration
  literals, a renamed package found by unique class names and through the package map, equal
  package names told apart by their super packages.
- `ExplicitOutputTest` (23) — no default left in the converted corpus: entity tables, columns of
  basic/id/version mappings (or overrides of every user), join columns with referenced columns,
  join tables, primary-key joins or mapsId on every owning side, mappedBy on every inverse side,
  target entities, single-table discriminators and values, foreign key constraint modes,
  metadata-complete.
- `CorpusTest` (23) — the corpus (`src/test/resources/corpus`, see its README) with the Ecore
  models of its class models (`src/test/resources/ecore/corpus`, names only, see its README):
  `.eorm` written next to copies of the `.ecore` files, reloaded with `EORMResourceFactoryImpl`, no
  unresolved proxy, every
  `href` into the `.ecore` files next to it, reload equal to the written model, entity and
  attribute mapping counts equal to the CWM mapping, only `EormNotes` rules and no error.
- `RejectsImplicitModelTest` (5) — unmapped attribute (E1) and missing column (E2) are rejected,
  lenient mode reports, the generator writes nothing for a rejected mapping.
