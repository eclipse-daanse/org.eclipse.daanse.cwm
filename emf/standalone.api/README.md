# org.eclipse.daanse.cwm.emf.standalone.api

The one interface by which a model module announces its generated EMF package to a plain Java
program. A generated package registers itself only when its class is touched; outside OSGi a
program would have to name every model it may meet. With `EPackageProvider` a model module says
it is there, and `java.util.ServiceLoader` finds every model on the class path.

| Type | Role |
| --- | --- |
| `EPackageProvider` | `ePackage()` gives the generated `EPackage` of a model; a model module implements it once per package and names the implementation in `META-INF/services/org.eclipse.daanse.cwm.emf.standalone.api.EPackageProvider` |

```java
public final class UnitsPackageProvider implements EPackageProvider {
    @Override
    public EPackage ePackage() {
        return UnitsPackage.eINSTANCE;
    }
}
```

## Relations

- Depends on EMF Ecore only; holds nothing but the interface.
- Implemented by the Daanse models under `model/daanse`; the CWM originals under `model/cwm` stay
  as they are and are named by `emf/standalone.impl`, which registers them and then every
  provider `ServiceLoader` finds.
- Under OSGi a model registers itself through its generated configurator; this interface is not
  used there, and a model bundle imports its package optionally.
