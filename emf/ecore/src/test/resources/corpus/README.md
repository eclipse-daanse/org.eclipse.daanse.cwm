# Test corpus

Small CWM models, one per group of mapping rules, and the output their conversion is expected to
give. Every rule of the module README occurs in at least one of them (`FeatureCoverageTest`).

The `.xmi` files are written by `CorpusModels` (test sources), never by hand: `CorpusUpToDateTest`
fails when a file differs from what the builder writes. The expected output lies in
`expected/<case>`: the notes of the conversion in `notes.txt`, one per line, and, when the
conversion has no errors, the written `.ecore` (and `.genmodel`) files, compared character for
character by `CorpusTest`. After a change of the models or of the mapping, write both anew and
review the diff:

```shell
mvn -o install -pl emf/ecore -Dcwm.ecore.corpus.update=true
```

| File | Rules | Cases (options) |
| --- | --- | --- |
| `packages.xmi` | model passed through; sub-package, subsystem; a package holding only a package; an import (`IMPORT`); left out: catalog, data types only a column uses, dependency, event, union, instance | `packages`; `packages-split` (`splitPackages`, `basePackage`, file names); `packages-genmodel` (GenModel properties, a `.genmodel`) |
| `classifiers.xmi` | abstract class, interface, multiple inheritance; data types by default mapping, by tag, by qualified name; type aliases and an alias of an alias; ordered enumeration with given and positional values, a value taken twice | `classifiers` |
| `classifiers-invalid.xmi` | data type without Java class, cyclic generalization, alias of an enumeration, alias of itself | `classifiers-invalid` |
| `attributes.xmi` | bounds, ordered list, two multiplicity ranges, upper bound 0, frozen, add-only, classifier scope, `iD`, initial values (data type, enumeration, OCL), class-typed attribute, no multiplicity | `attributes` |
| `relationships.xmi` | both ends navigable, one end navigable, composite at either end, shared aggregate, reflexive, ordered end, no navigable end, ends in two packages | `relationships`; `relationships-split` (one file per package, references between the files) |
| `relationships-invalid.xmi` | three ends, association class, aggregation at both ends, composite end with upper bound `*`, end type in a package that is not chosen | `relationships-invalid` (`packages=sales`) |
| `behaviour.xmi` | query operation of an interface, realized by an OCL method (override), abstract operation, parameter directions and a default value, Java method, OCL override in a subclass, OCL and Java method for one operation | `behaviour` |
| `behaviour-invalid.xmi` | second OCL method, method of an unrelated class (C-4-10), of a redeclared operation (C-4-11), in SQL, without body, OCL without result | `behaviour-invalid` |
| `constraints.xmi` | OCL invariants on a class, an attribute, a navigable end, of a stereotype on two classes; Java invariant; a duplicate name; constraints without invariant form, a constraint on a table | `constraints`; `constraints-off` (`constraints=false`) |
| `documentation.xmi` | descriptions (remark before documentation, two joined, other types left out, on a literal), tagged values, visibilities, two stereotypes | `documentation`; `documentation-off` (`documentation=false`) |
| `names.xmi` | names with a space, a leading digit, brackets | `names` |
| `selection.xmi` | a class using a class and a data type of other packages | `selection-missing` (`packages=a`); `selection` (`packages=a,b,types`); `selection-reuse` (`reuseEcoreDataTypes`) |
| `ecore-forms.xmi` | control tags (`serializable`, `transient`, `lowerBound`, map entry class, parameterized type, unknown and name tags); derived attributes with Java and OCL derivation; classifiers of the Ecore metamodel as super type, end and parameter type | `ecore-forms` |

The cases with errors do not validate their result, so their notes are those of the conversion
alone.
