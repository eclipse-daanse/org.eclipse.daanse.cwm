# org.eclipse.daanse.cwm.emf.standalone

Reads CWM XMI outside OSGi. Generated EMF packages register themselves only when their
class is touched; inside OSGi the service registry does that, in a plain Java program or a Maven
plugin someone has to. `CwmPackages` does it by name for every package of CWM 1.1 and of the
Daanse extensions, and this module brings all of them at run time, so any CWM XMI loads.

```java
CwmPackages.registerKnown();                               // every CWM EPackage
ResourceSet rs = CwmPackages.newResourceSet();
Resource model = CwmPackages.load(rs, Path.of("shop.xmi"));
EcoreUtil.resolveAll(rs);

CwmPackages.register("org.eclipse.daanse.rolap.mapping.model.RolapMappingPackage", loader);
```

| Method | Does |
| --- | --- |
| `KNOWN` | the interface names of the generated EPackages: CWM 1.1 (object model, foundation, resource, analysis, management) and the Daanse ORM package |
| `registerKnown()`, `registerKnown(ClassLoader)` | registers every known package the class loader finds; returns them |
| `register(String, ClassLoader)` | registers one further package by the name of its `EPackage` interface; fails with `IllegalArgumentException` when it is missing |
| `newResourceSet()` | a resource set reading `.ecore` as Ecore and every other extension as XMI (`.xmi`, `.cwm`, `.cwmorm` …) |
| `load(ResourceSet, Path)` | loads a file; files it refers to by relative `href` load on demand |

Used wherever CWM XMI is read outside OSGi, in particular by the cores behind the Maven
plugins in `maven/`.
