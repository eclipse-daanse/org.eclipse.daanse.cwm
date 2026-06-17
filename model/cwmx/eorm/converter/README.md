# Daanse CWM-EORM → Fennec EORM Converter

This module converts a **Common Warehouse EORM** model (Eclipse Daanse,
`org.eclipse.daanse.cwm.model.cwmx.eorm`) into the equivalent **Fennec
Persistence EORM** model (`org.eclipse.fennec.persistence.eorm`), so a
warehouse-defined object/relational mapping can be consumed by the Fennec
persistence stack.

| | Model | nsURI | Artifact |
|---|---|---|---|
| **Source** | CWM-EORM | `https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.cwmx.eorm` | `org.eclipse.daanse:org.eclipse.daanse.cwm.model.cwmx.eorm` |
| **Target** | Fennec EORM | `https://eclipse.org/fennec/persistence/eorm/1.0.0` | `org.eclipse.fennec.persistence.jpa:org.eclipse.fennec.persistence.orm` |

## Intent

Both models are JPA-ORM descriptions expressed in Ecore, and they line up almost one-to-one by class
and feature name. The CWM-EORM, however, carries **more information** than the Fennec EORM needs.
The goal of the converter is to take a CWM-EORM instance (typically an `EntityMappings`) and return
the structurally equivalent Fennec EORM instance, **dropping the extra warehouse-only information**
that Fennec has no place for.

```java
EntityMappings fennec = new CwmToEormConverter().convertEntityMappings(cwmEntityMappings);
// or, for any root object:
EObject fennec = new CwmToEormConverter().convert(cwmRoot);
```

## How it works

The converter is built on EMF's [`EcoreUtil.Copier`][copier]. `Copier` already provides a robust
two-phase deep copy (create + copy containments/attributes, then resolve cross-references), so the
converter only specialises the parts that differ between the two metamodels. See
`CwmToEormConverter` / `CwmToEormConverter.CwmEormCopier`.

### 1. Class & feature retargeting (the core of the mapping)

`Copier` copies *within* one metamodel by default. Two overrides turn it into a cross-metamodel copy:

- **`getTarget(EClass)`** — maps each CWM class onto the Fennec class with the **same name**
  (`EORMPackage.eINSTANCE.getEClassifier(name)`). Classes with no Fennec counterpart (e.g. Ecore
  helper types) are copied as-is.
- **`getTarget(EStructuralFeature)`** — resolves each feature **by name** on the retargeted Fennec
  class, returning `null` when no such feature exists.

A `null` feature is simply skipped, which is precisely how **CWM-only information is dropped** — no
explicit blocklist is needed.

### 2. Enum remapping

`AccessType`, `FetchType`, etc. are generated as **distinct Java enums** in each model, so the raw
value cannot be `eSet` across them. `copyAttribute` is overridden to remap enum literals by name
(falling back to literal string) to the target `EEnum`.

### 3. Rebuilding the EMF binding (`accessibleObject`)

This is the one place the two metamodels diverge structurally, and the only hand-written mapping:

| Concept | CWM-EORM | Fennec EORM |
|---|---|---|
| feature binding | `feature : EStructuralFeature` directly on `ENamedBase` | wrapped in `EORMElement.accessibleObject` as an `EFeatureObject` |
| entity binding | `class : EClassifier` directly on `Entity` | `class` ref **plus** `accessibleObject` as an `EClassObject` |

The `class` reference copies straight across (same name on both sides). The CWM `feature` reference
has no Fennec counterpart, so the base copy drops it; a post-processing pass `rebuildAccessors()` then
reconstructs the Fennec `accessibleObject` (`EFeatureObject` / `EClassObject`).

### References to the domain model

References that point at the user's domain Ecore (e.g. `Entity.class` → `EClass`,
`EFeatureObject.feature` → `EStructuralFeature`) are **preserved by identity**, not copied — they
must keep pointing at the same domain-model elements.

## Dependencies

Only `org.eclipse.fennec.persistence.orm` (the Fennec EORM types) and EMF core. The converter does
**not** import any CWM-EORM Java type — it reads the source purely through its Ecore metamodel
(`eClass()` / `eGet`), so it works whether the source instance is backed by generated classes or a
dynamic package.

## Tests

`CwmToEormConverterTest` builds a small CWM-EORM `EntityMappings` bound to a throwaway domain Ecore,
converts it, and asserts the Fennec result: class-reference identity, `EClassObject` / `EFeatureObject`
rebuild, enum remapping (`AccessType`, `FetchType`), scalar copy, and that a CWM-only feature is
absent on the Fennec side.

[copier]: https://download.eclipse.org/modeling/emf/emf/javadoc/2.11/org/eclipse/emf/ecore/util/EcoreUtil.Copier.html
