# org.eclipse.daanse.cwm.model.daanse.semantictype.base

What the elements of a model are, beyond their data type: that the values of a column are cities, that
the rows of a table are harbours, that a row of a ledger stands for assets. The types are instances, as
units are instances of `units::Unit`: a `TypeLibrary` is the vocabulary of one source, identified by its
URI, and owns its `SemanticType`s. A `Typing` names the elements of a type, as a `units::Quantity` names
the elements measured in a unit. The model holds no list of types; the types of an Analysis Services
server are delivered beside it in `semantictype.xmla`.

| Class | Role |
| --- | --- |
| `TypeLibrary` | a `core::Package`: the vocabulary of one source; `uri` (required, absolute, what a reader filters by), `sourceSystem` and `sourceVersion` (where the types come from) |
| `SemanticType` | a `core::ModelElement` owned by a library, a meaning: `name` (ours), `sourceName` and `sourceNumber` (what it is in the system of its library, the name and the value of its literal there; both optional for a type of one's own), `uri` (stated, else derived), `description`, `broader` (the types it is narrower than), `sameAs` (the same type in other libraries), `sameAsExternal` |
| `ExternalType` | the same type in a system that has no library in the model: `libraryUri`, and the type's `sourceName` or `sourceNumber` there |
| `Typing` | a `core::ModelElement` in any namespace, never in the element: `modelElement` (the elements typed) and `type` |

A type says a meaning and nothing about where it may stand: one type types a column (its values are
that) and a table (its rows are that). What a type is for follows from the library it lies in and its
broader types: a vocabulary of one's own gathers its types under root types (a place, an identifier),
the server's lists are libraries of their own. A type's URI, where none is stated, is the URI of its
library and its name, after a slash.

`TypeLibraries`, `SemanticTypes` and `Typings` build libraries, types and typings and find them: a
library by its URI; a type by its URI, by its library and name, by its name there or its number there;
the type or types of an element. They filter: the types of a library, the types narrower than a type
(transitively), the typings of a model whose type is a type or narrower than it, or lies in a library
(by the library or its URI).

`SemanticTypeModelValidator` checks the rules that hold for any vocabulary: ST1 absolute, well-formed
URIs; ST2 no two libraries or types of one URI; ST3 a type lies in a library; ST4 names, names there
and numbers there unique within a library; ST5 no cycle of broader types; ST7 the same type lies in
another library, an external type outside the model; ST8 the types a typing or type names lie in the
model or in a library delivered beside it, recognised by its URI. Where a rule can be said in OCL it
stands in the Ecore as well (ST3, ST5 for the direct case, ST7 for `sameAs`). What the types of one
vocabulary may type is checked by that vocabulary's rules.

## Relations

- Builds on CWM `core` only; reports in `model/daanse/validation`.
- No model depends on it: a reader resolves the type of an element, on the element itself or on the
  column or table the mapping reads it from.
- `semantictype.xmla` delivers the types of an Analysis Services server as libraries, their XMLA codes
  and the rules of those types.

## Example

A library of one's own with a harbour town narrower than a town, the same as a type of a gazetteer
that has no library in the model, and a column typed by it (ids left out):

```xml
<ownedElement xsi:type="semantictype:TypeLibrary" name="harbour" uri="https://types.example.org/harbour">
  <ownedElement xsi:type="semantictype:SemanticType" name="Town"/>
  <ownedElement xsi:type="semantictype:SemanticType" name="HarbourTown" broader="_Town">
    <sameAsExternal libraryUri="https://types.example.org/gazetteer" sourceName="PortTown" sourceNumber="12"/>
  </ownedElement>
</ownedElement>
<ownedElement xsi:type="semantictype:Typing" name="town" type="_HarbourTown" modelElement="_port.town"/>
```
