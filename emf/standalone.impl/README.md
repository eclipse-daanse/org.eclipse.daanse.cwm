# org.eclipse.daanse.cwm.emf.standalone.impl

Reads and writes CWM XMI outside OSGi. Generated EMF packages register themselves only when
their class is touched; `CwmPackages` registers every package of CWM 1.1 by name (`KNOWN`, the
modules under `model/cwm`, which cannot announce themselves), and then every model on the class
path that announces itself: a Daanse model names its `EPackageProvider`
([`emf/standalone.api`](../standalone.api/README.md)) in `META-INF/services`, and
`java.util.ServiceLoader` finds it without this module naming it. This module brings the CWM 1.1
packages at run time; a Daanse model is there where the caller brings it, and one that is not on
the class path is simply absent. A model that is announced but cannot be loaded, its provider or
a class of its package missing, is a class path that lacks part of the model: `registerKnown`
fails and names the provider.

`CwmPackages.loadModel` reads a model the way a tool reads its input, `CwmPackages.save` writes
model elements as XMI with ids built from their name path and leaves them where they are: it
writes a copy, so the model stays in its own file, or in none, however often it is saved. `EcoreIndex` finds the Ecore element
that was made from a CWM class model element — by names alone, so any Ecore model with the CWM
names works; `CwmPath.of` gives the dotted names of a CWM element and its containers.

## Relations

- Brings the generated packages of CWM 1.1; depends on `emf/standalone.api` for the interface by
  which every other model announces itself, and on no single Daanse model.
- Plain Java only: under OSGi each model registers itself through its generated configurator.
- Used by the converters and Maven plugins that read or write CWM XMI outside OSGi; every plugin
  in `maven/` loads its model file with `loadModel`.

## Example

```java
// the CWM 1.1 packages, every announced model and the extra ones registered, the file and those it
// refers to loaded
Resource model = CwmPackages.loadModel(Path.of("shop.xmi"), loader, List.of("org.example.MyPackage"));

CwmPackages.registerKnown();                               // CWM 1.1 by name, then every provider
ResourceSet rs = CwmPackages.newResourceSet();             // .ecore as Ecore, anything else as XMI
Resource same = CwmPackages.load(rs, Path.of("shop.xmi"));

CwmPackages.save(Path.of("target/shop.xmi"), List.of(catalog));   // UTF-8, ids from the name path;
                                                           // catalog itself stays where it was

EcoreIndex index = EcoreIndex.of(ecorePackages);
EClass book = index.eClass(cwmBookClass);                  // the EClass named like the CWM class
EStructuralFeature title = index.feature(cwmTitleAttribute);
```
