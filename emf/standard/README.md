# org.eclipse.daanse.cwm.emf.standard

How a standard set of model instances is held so that other model files can refer to it: the SQL types of CWM
`relational`, the SI of `units`, the reference systems of `spatial`, the notations of `notation`, the server's types
of `semantictype.xmla`. Each set is built by code in the module of its model, written as an XMI file into that
module's jar under a URI of its own, and read from the jar into a resource set under that URI. Plain Java on EMF,
beside `standalone.api`: the CWM originals and the Daanse models may both depend on it. One class, `StandardSets`.

## The id of an element

The id of an element of a set is a UUID in the short form of EMF (`EcoreUtil.generateUUID()`: an underscore and 22
characters), generated once and stated in the source beside the element: as an argument of the call that builds it,
or as a column of the table the set is built from. It is never generated at run time and never computed from a name.

The id is the published address of the element together with the URI of the set: a model file refers to it as
`href="<URI of the set>#<id>"`. Neither the URI nor an id is ever changed. A new element gets a new UUID; an element
is not removed.

## A set in code, in its file and in a program

- **Built.** `StandardSets.create(uri)` makes the empty set, an XMI resource under its URI; the code adds the root,
  and `StandardSets.id(element, uuid)` gives each element its UUID once it lies in the set.
- **Written.** `StandardSets.text(set)` is the same on every run: UTF-8, lines ended by a line feed, the licence
  header after the XML declaration, a reference to another set by its absolute URI.
- **Checked in.** The XMI file lies in the jar beside the class of the set. A test of the module asserts
  `StandardSets.upToDate(set, file)`: it fails while the file differs from what the code writes. After a change to the
  code of a set, run that test once with `-Ddaanse.standard.write=true` to write the file anew. No test reads a
  hand-written XMI file.
- **Mapped.** `StandardSets.map(resourceSet, uri, source, packages)` maps the URI of a set to its file on the class
  path in the URI converter of the resource set. A reference of a model file there to the set is read from the jar
  when it is first followed; EMF never opens the URI itself, so nothing is fetched from the network. A set that refers
  to another set (the notations to the SI) maps that one too.
- **Loaded.** `StandardSets.load(resourceSet, uri, Root.class)` reads a mapped set at once and gives its root. The
  class of each set offers both: `map(resourceSet)` and `load(resourceSet)`.

A set is one way to hold such instances, not the only one. A model may as well own them: a catalog its SQL types
(`SQLSimpleTypes.ownedIn`), a model the SI (`SI.create`) or the server's types (`ServerTypes.create`). Both ways
stay. The code that builds a set builds the same elements into a model of one's own; where that model lies in an XMI
resource, its elements carry the UUIDs of the set.

## Example

```java
XMIResource set = StandardSets.create("https://types.example.org/kinds.xmi");
EPackage kinds = EcoreFactory.eINSTANCE.createEPackage();
set.getContents().add(kinds);
StandardSets.id(kinds, "_LdtskOUIEfGl4IEUgfF4xA");
StandardSets.id(type(kinds, "Text"), "_LdtskeUIEfGl4IEUgfF4xA");   // <eClassifiers ... xmi:id="_LdtskeUIEfGl4IEUgfF4xA" name="Text"/>

StandardSets.map(resourceSet, "https://types.example.org/kinds.xmi", Anchor.class.getResource("kinds.xmi"),
        EcorePackage.eINSTANCE);
// a model file in resourceSet with href="https://types.example.org/kinds.xmi#_LdtskeUIEfGl4IEUgfF4xA" resolves to Text
```

## Relations

- Builds on EMF only, like `standalone.api`.
- Used by the modules that deliver a standard set: `model/cwm/resource.relational` (imported optionally under OSGi),
  `units`, `spatial`, `notation`, `semantictype.xmla`.
