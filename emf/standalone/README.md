# org.eclipse.daanse.cwm.emf.standalone

Reads and writes CWM XMI outside OSGi. Generated EMF packages register themselves only when
their class is touched; `CwmPackages` registers every package of CWM 1.1 and of the Daanse models
by name, and this module brings all of them at run time, so any CWM XMI loads.
`CwmPackages.loadModel` reads a model the way a tool reads its input, `CwmPackages.save` writes
model elements as XMI with ids built from their name path and leaves them where they are: it
writes a copy, so the model stays in its own file, or in none, however often it is saved. `EcoreIndex` finds the Ecore element
that was made from a CWM class model element — by names alone, so any Ecore model with the CWM
names works; `CwmPath.of` gives the dotted names of a CWM element and its containers.

## Relations

- Brings the generated packages of CWM 1.1, the ORM, sensiNact, governance and units models.
- Used by the converters and Maven plugins that read or write CWM XMI outside OSGi; every plugin
  in `maven/` loads its model file with `loadModel`.

## Example

```java
// every known EPackage and the extra ones registered, the file and those it refers to loaded
Resource model = CwmPackages.loadModel(Path.of("shop.xmi"), loader, List.of("org.example.MyPackage"));

CwmPackages.registerKnown();                               // step by step, references on demand
ResourceSet rs = CwmPackages.newResourceSet();             // .ecore as Ecore, anything else as XMI
Resource same = CwmPackages.load(rs, Path.of("shop.xmi"));

CwmPackages.save(Path.of("target/shop.xmi"), List.of(catalog));   // UTF-8, ids from the name path;
                                                           // catalog itself stays where it was

EcoreIndex index = EcoreIndex.of(ecorePackages);
EClass book = index.eClass(cwmBookClass);                  // the EClass named like the CWM class
EStructuralFeature title = index.feature(cwmTitleAttribute);
```
