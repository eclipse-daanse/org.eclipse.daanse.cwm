# org.eclipse.daanse.cwm.emf.standalone

Reads CWM XMI outside OSGi. Generated EMF packages register themselves only when their class is
touched; `CwmPackages` registers every package of CWM 1.1 and of the Daanse models by name, and
this module brings all of them at run time, so any CWM XMI loads. `EcoreIndex` finds the Ecore
element that was made from a CWM class model element — by names alone, so any Ecore model with
the CWM names works; `CwmPath.of` gives the dotted names of a CWM element and its containers.

## Relations

- Brings the generated packages of CWM 1.1 and the ORM model.
- Used by the converters and Maven plugins that read CWM XMI outside OSGi.

## Example

```java
CwmPackages.registerKnown();                               // every known EPackage
ResourceSet rs = CwmPackages.newResourceSet();             // .ecore as Ecore, anything else as XMI
Resource model = CwmPackages.load(rs, Path.of("shop.xmi"));

EcoreIndex index = EcoreIndex.of(ecorePackages);
EClass book = index.eClass(cwmBookClass);                  // the EClass named like the CWM class
EStructuralFeature title = index.feature(cwmTitleAttribute);
```
