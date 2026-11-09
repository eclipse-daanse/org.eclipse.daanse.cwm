# org.eclipse.daanse.cwm.emf.ecore

Converts a CWM class model into Ecore, in plain EMF Java, and writes the result as `.ecore` files.
The EPackages carry the GenModel annotations EMF code generation reads, so a code generator that
takes an `.ecore` alone (such as `org.eclipse.daanse.tooling.emf.codegen.maven`) needs no
`.genmodel`.

```java
CwmPackages.registerKnown();                       // CWM EPackages on the class path
ResourceSet rs = CwmPackages.newResourceSet();
Resource model = CwmPackages.load(rs, Path.of("library.xmi"));
EcoreUtil.resolveAll(rs);

Result r = CwmToEcore.convert(CwmToEcore.packagesOf(model), Options.defaults()
        .withDataType("Text", "java.lang.String")
        .withBasePackage("org.example"));
if (!r.hasErrors()) {
    EcoreWriter.save(r, Path.of("target/generated-ecore"), null);   // GenModelSettings: also a .genmodel
}
```

| Type | Role |
| --- | --- |
| `CwmToEcore` | `convert(Collection<core::Package> roots, Options)` → `Result` |
| `Options` | `nsUriBase`, `dataTypes`, `reuseEcoreDataTypes`, `packages`, `validate`, `oclDelegateUri`, `splitPackages`, `documentation`, `constraints`, `basePackage`, `genModelAnnotations` |
| `Result` | root `EPackage`s, the trace `ModelElement → EModelElement`, the `Note`s |
| `Note` | `severity` (ERROR, WARNING, INFO), `rule`, qualified name of the element, message |
| `EcoreWriter` | one `.ecore` per root EPackage into one directory, named after the package or by a file name map, relative `href`s between them, optional `.genmodel` named after the first file (`GenModelSettings`: base package, prefix, model directory, plugin id) |
| `CwmAnnotations` | the annotation sources and readers for the trace back to CWM |

Loading the XMI (`CwmPackages`: registering the CWM EPackages, resource sets) comes from
[`emf/standalone`](../standalone/README.md), which also brings every CWM package at run time. The
Maven goal is [`cwm-ecore:generate`](../../maven/ecore/README.md).

## Which packages are converted

- A `core::Package`, `core::Model` or `core::Subsystem` that owns a class, an interface, an
  enumeration, or a data type that an attribute or operation parameter of a class uses.
- Packages nested in a converted package, if anything convertible lies below them (they become
  `eSubpackages`).
- Packages that only contain packages are passed through, not mapped. Relational catalogs and
  schemas, record files, OLAP, and every other `core::Package` subtype stay out, as do packages of
  data types that only record fields or columns use.
- With `Options.packages` (qualified names, dot separated, or a trailing part of them) only the
  named packages and what they contain are converted. A type, super class or association end type
  in a package that is not converted is an error whose message names that package (for data types
  `reuseEcoreDataTypes` is the other way out).
- With `Options.splitPackages` every converted package becomes a root EPackage instead of a
  sub-package of the converted package around it. Name and namespace URI stay the same; the
  annotation `…/cwm/Package` `superPackage` keeps the dot separated names of the packages around it,
  from which `EcoreWriter` derives the Java base package of the genmodel, so the generated Java
  packages are those of the nested layout. `EcoreWriter.save(result.ePackages(), dir, genModel, fileNames)`
  writes each root into its own file; `fileNames` maps a package — by its qualified name, a trailing
  part of it, or the root EPackage name — to a file name (`.ecore` appended when missing). A key that
  names no or several packages, or one file name for two packages, is an `IllegalArgumentException`.

## Mapping

| CWM | Ecore | Rule |
| --- | --- | --- |
| `core::Package` / `Model` / `Subsystem` | `EPackage` | `name` = package name, `nsPrefix` = name, `nsURI` = `nsUriBase` + `/` + the lower-case names of all namespaces from the model root (e.g. `http://daanse.eclipse.org/cwm/ecore/model/shop/enumerations`) |
| `core::Class` | `EClass` | `abstract` = `isAbstract` |
| `behavioral::Interface` | `EClass` | `interface` and `abstract`; its operations become `EOperation`s like those of a class |
| `relationships::Generalization` | `eSuperTypes` of the child | in the order of the generalizations; several parents allowed; a generalization that would make a class its own super type is an error (`CYCLE`) |
| a classifier tagged `daanse.ecore.eClassifier` = `<nsURI>#//<name>` | the registered EClassifier of another metamodel, e.g. `http://www.eclipse.org/emf/2002/Ecore#//EObject` | not converted; used wherever it is a super type, attribute, end or parameter type; not registered: error `TYPE`; its own features, a super type for it and a reference on it are left out (warning `EXTERNAL`) |
| `core::DataType` | `EDataType` | `instanceClassName` from the TaggedValue `daanse.ecore.instanceClassName`, else `Options.dataTypes` (defaults below), else the name when it is a qualified Java name; none: error `TYPE`; a parameterized Java type becomes the `instanceTypeName`. `reuseEcoreDataTypes` uses the `EcorePackage` data type of that class instead |
| `datatypes::TypeAlias` (5.4.1.4) | `EDataType` | the Java class of the aliased data type (also through an alias of an alias); an alias of a class or an enumeration: warning `TYPE_ALIAS`, not converted; an alias of itself (C-4-1): error `TYPE_ALIAS` |
| `datatypes::Enumeration` / `EnumerationLiteral` | `EEnum` / `EEnumLiteral` | `value` = the integer in `literal.value.body`, else the position; a value already taken (warning `ENUM_VALUE`) or a taken position moves on to the next free value; `literal` = name; `isOrdered` → annotation `…/cwm/Enumeration` |
| `core::Attribute` typed by a data type or enumeration | `EAttribute` | bounds, order, changeability as below; `iD` with TaggedValue `daanse.ecore.id=true` |
| `Attribute.initialValue` | `defaultValueLiteral` | a body without language is the literal; an expression in a language, or on a class-typed attribute: warning `DEFAULT` |
| TaggedValue `derived=true` (the UML standard tag) on an attribute or end | `derived`, `volatile`, `transient`, `changeable=false` | a constraint on the feature is its derivation (below) |
| `core::Attribute` typed by a class | containment `EReference`, `resolveProxies=false`, no opposite | the value belongs to the owner (an embedded value object, e.g. `Customer.address: Address`) |
| binary `relationships::Association`, ends `a` (type A) and `b` (type B) | `EReference` on A named `b.name`, typed B — iff `b.isNavigable`; symmetrically on B named `a.name`, typed A — iff `a.isNavigable` | the reference for an end lives at the class of the other end; bounds from the end's multiplicity (how many instances at that end belong to one at the other end) |
| both ends navigable | the two references are `eOpposite`s | symmetric |
| one end navigable | only that reference | the name of the hidden end goes into the EMOF annotation `http://schema.omg.org/spec/MOF/2.0/emof.xml` `Property.oppositeRoleName` on it — the form the repository's own Ecore models use for hidden opposites |
| `aggregation = ak_composite` at end `a` | the reference on A towards the parts is a containment (`resolveProxies=false`); one on B towards A is the container | aggregation describes the classifier at the end carrying it (OMG CWM 1.1, 4.5.1); the composite end needs upper bound 1, as a part belongs to at most one whole (C-5-8) and EMF containers are single-valued; else error `AGGREGATION` |
| `aggregation = ak_aggregate` at end `a` | no containment; annotation `…/cwm/Aggregation` `kind=shared` on the reference on A towards the parts | Ecore has no shared aggregation |
| aggregation at both ends | — | error `AGGREGATION` (C-5-3: at most one end aggregates) |
| reflexive association (A = B) | two references on the same EClass | same rules |
| association with ≠ 2 ends | — | error `N_ARY` |
| association owning features besides its ends (association class) | — | error `ASSOCIATION_CLASS` |
| no end navigable | — | annotation `…/cwm/Association` on the EClass of end `a`, note `NOT_NAVIGABLE` |
| `Multiplicity` | `lowerBound` / `upperBound` | first range; `upper = -1` → unbounded; further ranges → annotation `…/cwm/Multiplicity/Ranges` `extra=5..*,…`; no multiplicity → Ecore's `0..1` |
| `ordering` | `ordered` | on many-valued features: `ok_ordered` → `true`, `ok_unordered` (CWM default) → `false`; `unique` stays `true` |
| `changeability` | `changeable` | `ck_frozen` → `false`; `ck_addOnly` → `true` plus annotation `…/cwm/Changeability` |
| `ownerScope` / `targetScope` = `sk_classifier` | — | Ecore has no static features: annotation `…/cwm/Scope`, warning `SCOPE` |
| `behavioral::Operation` / `Parameter` | `EOperation` / `EParameter` | the `pdk_return` parameter gives the operation's type; the others become parameters with annotation `…/cwm/Parameter` `kind` and `default` (the default value); `isAbstract` and `isQuery` → annotation `…/cwm/Operation` |
| `behavioral::Method`, body language `OCL` | `body` under `oclDelegateUri` on the EOperation of the realized operation; the package lists the delegate in `invocationDelegates` | a method implements an operation of its class or an ancestor (C-4-10); the invocation delegate evaluates it. An operation without `pdk_return` parameter gets no OCL body (warning `METHOD`) |
| `behavioral::Method`, body language `Java` | GenModel `body` on that EOperation | the EMF generator writes it as the body of the generated method and prefers it over a delegate |
| method realizing an inherited operation | an `EOperation` of the method's class with the name, type and parameter types of the inherited one, carrying the body | `EClass.getOverride` finds it; it is traced to the method. A realized operation that is no operation of the class or an ancestor (C-4-10), or one the class redeclares itself (C-4-11), is an error `METHOD` |
| two methods for one operation and class | both bodies when one is OCL and one is Java (warning `METHOD`, C-4-12); two of the same language: error `METHOD` | — |
| method in another language (SQL, a dialect, …) | — | warning `METHOD_LANGUAGE` |
| `core::Constraint`, body language `OCL` | an invariant of the classifier it constrains: its name in Ecore `constraints`, the expression under `oclDelegateUri`; the package lists the delegate in `validationDelegates` | on an attribute or navigable end: the class holding it is the context; of a `Stereotype`: each extended element; a duplicate name gets `_2` (warning `NAME`) |
| constraint on an operation, a parameter, an association, a non-navigable end or a package of the class model | — | warning `CONSTRAINT`: Ecore has invariants on classifiers only |
| constraint on several classifiers | — | warning `CONSTRAINT`: an OCL invariant has exactly one context |
| `core::Constraint`, body language `Java` | an invariant operation of the class (`EcoreUtil.isInvariant`) with `DiagnosticChain` and context map; GenModel `body` tests the Java expression on `this` and reports an error diagnostic | the generated validator calls it; dynamic instances skip it; `…/cwm/Constraint` on the class; on a data type: warning `CONSTRAINT` |
| constraint in another language | — | warning `CONSTRAINT_LANGUAGE` |
| constraint on elements outside the class model (tables, columns, other models) | — | left alone, no note |
| constraint on a derived feature, language `Java` / `OCL` | GenModel `get` (the body of the generated getter) / `derivation` under `oclDelegateUri`, the package lists the delegate in `settingDelegates` | the derivation instead of an invariant; a second one: warning `CONSTRAINT` |
| `Package.importedElement` | — | note `IMPORT`: each element is converted where it is owned |
| `businessinformation::Description` pointing at the element (`modelElement`), type `remark` or `documentation` | GenModel `documentation` | `remark` wins when both exist; several descriptions are joined by a blank line; left out with `documentation=false` |
| `TaggedValue` | annotation `…/cwm/TaggedValue`, detail key = tag | except the control tags `daanse.ecore.*` |
| TaggedValue `daanse.ecore.<property>` | the Ecore property of that name on the element made from the tagged one | see [Control tags](#control-tags) |
| `Stereotype.extendedElement` | annotation `…/cwm/Stereotype` `name` on each extended element | several names comma separated |
| `visibility` | annotation `…/cwm/Visibility` `value` | only for `vk_private`, `vk_protected`, `vk_package`; `vk_public` (CWM's default) and `vk_notapplicable` are what Ecore elements are anyway |
| every converted element | annotation `…/cwm/Element` | `id` = `xmi:id` of the CWM source (its URI fragment when it has none), `path` = qualified name: the trace back to the CWM source |

The annotation `…/cwm/Constraint` on the classifier maps each invariant name to the id of its CWM
constraint.

With `constraints=false` no constraint becomes an invariant or a derivation and no validation or
setting delegate is declared; the method bodies stay.

Names that are no Java identifiers are adjusted (`byte[]` → `byteArray`, invalid characters
dropped, the next letter upper-cased) with warning `NAME`; the original stays in `…/cwm/Element`
`path`. The Fennec EORM converter finds Ecore elements by the same rule.

Left alone, without note: `core::Dependency`, `behavioral::Event`, `CallAction` and `Argument`,
`datatypes::Union`, instances (`objectmodel::instance`), and every element of other models
(relational, record, OLAP, …).

### Control tags

A TaggedValue `daanse.ecore.<property>` sets the Ecore property of that name on the element made
from the tagged one, after the mapping, so it wins over what the mapping derived. The value is read
as the property's type. Examples:

| Tag on | Tag | Ecore |
| --- | --- | --- |
| data type | `daanse.ecore.serializable=false` | `EDataType.serializable` |
| data type | `daanse.ecore.instanceClassName=java.util.function.Function<java.lang.String, java.lang.Integer>` | `instanceTypeName` (read by the mapping, see above) |
| class | `daanse.ecore.instanceClassName=java.util.Map$Entry` | a map entry class; an attribute typed by it becomes an EMap |
| attribute, end | `daanse.ecore.transient=true`, `unsettable`, `volatile`, `resolveProxies` | the feature's property |
| attribute | `daanse.ecore.id=true` | `iD` |
| parameter | `daanse.ecore.lowerBound=1` | `EParameter.lowerBound` (CWM parameters have no multiplicity) |

A tag that names no settable property of the target, or the name (other tools find Ecore elements
by the CWM name), and a value of the wrong type are reported as warning `TAG`.

### Not derivable from CWM

Ecore holds some properties CWM has no counterpart for: generic types and type parameters,
`eExceptions`, `eKeys`, the multiplicity of an operation result. They are not written; where a
single property is needed, a control tag sets it.

### Default data types

| CWM name | Java class | | CWM name | Java class |
| --- | --- | --- | --- | --- |
| `Integer`, `Long` | `java.lang.Long` | | `Date` | `java.util.Date` |
| `String` | `java.lang.String` | | `Time` / `Timestamp` | `java.time.LocalTime` / `LocalDateTime` |
| `Boolean` | `java.lang.Boolean` | | `Decimal`, `BigDecimal` | `java.math.BigDecimal` |
| `Double`, `Float`, `Short`, `Byte`, `Character` | `java.lang.*` | | `Binary`, `byte[]` | `byte[]` |
| `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetDateTime`, `Instant` | `java.time.*` | | `UUID`, `BigInteger` | `java.util.UUID`, `java.math.BigInteger` |
| `int`, `long`, `short`, `byte`, `float`, `double`, `boolean`, `char` | the primitive | | | |

A CWM `Integer` becomes `java.lang.Long` because CWM integers are not bounded to 32 bit.

## GenModel annotations

EMF code generation reads GenModel properties from annotations of the source
`http://www.eclipse.org/emf/2002/GenModel` on the EPackages. The converter writes:

| Key | On | Value |
| --- | --- | --- |
| `prefix` | every EPackage | the package name, capitalized (EMF's own default, written so the generated class names are fixed in the file) |
| `basePackage` | every root EPackage, when `Options.basePackage` is set | the base package; for a package taken out of the packages around it (`splitPackages`), followed by their names, so its Java package is the one it had nested |
| further keys | every root EPackage | the entries of `Options.genModelAnnotations`, e.g. `oSGiCompatible`, `copyrightText`, `loadInitialization`, `literalsInterface`, `fileExtensions`, `rootExtendsClass`; the keys `basePackage` and `prefix` are not taken from there (warning `GENMODEL`) |
| `documentation` | every converted element with a description | see the mapping table |
| `body` | operations with a Java method, Java invariant operations | see the mapping table |

Nothing else is written: a code generator reads an annotation in preference to its own settings,
so properties the conversion cannot know stay with the generator's configuration.

## Annotation sources

All below `http://daanse.eclipse.org/cwm/`, constants in `CwmAnnotations`:
`Element` (id, path), `Visibility`, `Association` (name, endA, endB), `Aggregation` (kind),
`TaggedValue`, `Multiplicity/Ranges` (extra), `Parameter` (kind, default), `Operation` (isAbstract,
isQuery), `Enumeration` (isOrdered), `Stereotype` (name), `Changeability`, `Scope`, `Constraint`
(invariant or derivation name → id of the CWM constraint), `Method` (`OCL` or `Java` → id of the
CWM method), `Package` (`superPackage` of a split package); plus the EMOF source
`http://schema.omg.org/spec/MOF/2.0/emof.xml` (`Property.oppositeRoleName`).

Invariants and bodies use the sources Ecore and EMF define: `http://www.eclipse.org/emf/2002/Ecore`
(`constraints` on classifiers; `invocationDelegates`, `validationDelegates`, `settingDelegates` on
packages), the OCL delegate source (`oclDelegateUri`, default
`http://www.eclipse.org/emf/2002/Ecore/OCL`, the classic Eclipse OCL the CWM metamodels of this
repository use; `…/Ecore/OCL/Pivot` for Pivot OCL) with one detail per invariant, `body` on
operations and `derivation` on derived features, and `http://www.eclipse.org/emf/2002/GenModel`
`body` and `get` for Java. To evaluate the OCL, register the delegates of the chosen OCL implementation before the
package is used (classic OCL standalone: `OCLDelegateDomain.initialize(null)` and the delegate domain
factory for `OCLConstants.OCL_DELEGATE_URI_LPG`).

## Notes

| Rule | Severity | Meaning |
| --- | --- | --- |
| `TYPE` | ERROR | data type without Java class, or a feature typed by something outside the converted classifiers |
| `UNRESOLVED` | ERROR | a generalization parent or an association end type outside the converted classes |
| `N_ARY`, `ASSOCIATION_CLASS`, `AGGREGATION`, `CYCLE` | ERROR | see the mapping table |
| `ECORE` | ERROR | the `Diagnostician` rejects the result (only when `Options.validate`, the default) |
| `METHOD` | ERROR | a method whose operation is not converted, is no operation of its class or an ancestor (C-4-10), is redeclared by its class (C-4-11), or a second method of the same language for one operation and class (C-4-12) |
| `NAME`, `ENUM_VALUE`, `MULTIPLICITY`, `SCOPE`, `FEATURE` | WARNING | adjusted name (also a second invariant of the same name), duplicate literal value, upper bound 0, classifier scope, a feature kind that is not converted |
| `GENMODEL` | WARNING | `basePackage` or `prefix` given among the further GenModel annotations |
| `METHOD`, `METHOD_LANGUAGE` | WARNING | method without body, OCL body of an operation without result, an OCL and a Java method for one operation and class; a method in a language other than OCL or Java |
| `CONSTRAINT`, `CONSTRAINT_LANGUAGE` | WARNING | a constraint without a place for an invariant, on several classifiers, without body, in Java on a data type; a constraint in a language other than OCL or Java |
| `TYPE_ALIAS` | ERROR | an alias that leads back to itself (C-4-1) |
| `TYPE_ALIAS`, `DEFAULT`, `TAG`, `EXTERNAL` | WARNING | an alias of a class or enumeration; an initial value that is no literal; a control tag without property or with a value of the wrong type; what a classifier of another metamodel cannot get |
| `NOT_NAVIGABLE`, `IMPORT` | INFO | an association without a navigable end; a package that imports elements |

## Design decisions

- **One way, CWM is the source.** Nothing converts an Ecore model back to CWM.
- **Navigable ends only.** An association end becomes an `EReference` only when it is navigable;
  a hidden end is named by `Property.oppositeRoleName`, never a feature. This keeps the Ecore
  model free of opposites the CWM model does not have.
- **CWM `Integer` is `java.lang.Long`**, primitive names stay primitive; an unknown data type is an
  error, not a silent `String`.
- **Public is not annotated.** `vk_public` is CWM's default and Ecore's nature; only other
  visibilities are recorded.
- **Data types belong to the class model only when classes use them.** A package of data types
  that only record fields or columns use is not converted.
- **Namespace URIs from the model path**, lower case, below a configurable base, so two packages
  with the same name in different places stay distinct.
- **Names are made valid, the original kept** in the `…/cwm/Element` `path`.
- **`remark` before `documentation`** as GenModel documentation: the short remark of a feature or
  class is what a generated Javadoc should show.
- **A composite end with upper bound > 1 is an error**, not a warning: Ecore cannot hold a part in
  two containers, and CWM forbids it (C-5-8).
- **OCL constraints become inline invariants, OCL and Java methods operation bodies.** They are
  written where EMF's validation and invocation delegates and its generator look for them, so a
  dynamic instance and the generated code behave alike. The classic OCL delegate URI is the default
  because the repository's CWM metamodels use it; the source is configurable for Pivot OCL.
- **A constraint has one context.** Ecore places invariants on classifiers; a constraint on a feature
  constrains the objects of the class holding that feature. What has no such place is reported,
  not moved somewhere it might mean something else.
- **An inherited operation is realized by overriding it.** The body of a method in a subclass goes
  onto an EOperation of that subclass with the same signature, which EMF dispatches to.
- **Java constraints are invariant operations.** EMF's generated validator calls an operation with
  the invariant signature; its GenModel body is the place for Java, as for Java methods.
- **Splitting is a layout, not a change of the model.** A split package keeps its name and
  namespace URI and, through `superPackage`, its Java package; only the containment in Ecore and
  the file differ.
- **One file per root EPackage**, named after the package unless named otherwise.
- **Ecore-only properties by tag, not by guess.** What CWM cannot say (transient, serializable, a
  parameterized type, a map entry class) comes from a `daanse.ecore.*` tag on the element; derived
  features use the UML standard tag `derived`, and their derivation is a constraint on them.
- **Other metamodels by reference.** A classifier that stands for one of another metamodel is
  looked up in the EPackage registry, never copied, so the class model can extend and refer to it.
- **Only the GenModel properties the conversion knows.** `prefix` and the base package follow from
  the packages and the options; everything else a code generator decides is left to its
  configuration unless given explicitly.

## Tests

- `CorpusTest` — the corpus (`src/test/resources/corpus`, see its README): thirteen small models,
  one per group of mapping rules, converted in twenty cases (options as split, documentation or
  constraints off, a genmodel), the notes and the written files compared with the expected output
  character for character, then reloaded: no unresolved references, valid by the
  `Diagnostician`, opposites symmetric, containers single-valued.
- `CorpusUpToDateTest` — the corpus files are what `CorpusModels` writes.
- `FeatureCoverageTest` — every Ecore form of the mapping and every note rule occurs in the expected
  output at least once.
- `OclDelegatesTest` — invariants, OCL bodies (also of an overriding operation) and an OCL
  derivation evaluated by classic Eclipse OCL, on a model built in memory and on the written corpus
  files.
- `ConversionRulesTest` — what no corpus file shows: a model outside any resource, options the cases
  do not use, failures no file holds, the name rule.
- `EcoreWriterTest` — file per root package, relative `href`s, genmodel; split packages in named
  files, the genmodel keeping the Java package of nested packages, the base package of the
  conversion reaching the genmodel, and file name entries that name no package or one file twice
  rejected.
