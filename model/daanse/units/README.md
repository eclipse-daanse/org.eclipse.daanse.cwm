# org.eclipse.daanse.cwm.model.daanse.units

Quantity kinds, units, dimensions and systems of units for any CWM element. A `Quantity` says in
which unit the values of an attribute, a column, a record field, a measure, a parameter or every
attribute of a data type are measured; the units themselves are defined in a `SystemOfUnits`,
from base units through products, scales, prefixes and logarithms, so that values convert between
units of one dimension. The concepts follow QUDT 3.5 and JSR 385 2.2 with its reference
implementation Indriya; no file of either is copied.

```java
Model plant = …;                                              // a core::Model
SystemOfUnits si = SI.create(plant);                          // units, prefixes and kinds of the SI
Package quantities = …;                                       // a core::Package of plant

Quantities.create(quantities, "pressure", SI.unit(si, SI.HECTOPASCAL), pressure);    // one attribute
Quantities.create(quantities, "celsius", SI.unit(si, SI.DEGREE_CELSIUS), celsius);   // every attribute of a data type

Quantities.unitOf(pressure);                                  // hPa
Units.convert(new BigDecimal("1013.25"), SI.unit(si, SI.HECTOPASCAL), SI.unit(si, SI.PASCAL));   // 101325
```

The model depends on `objectmodel.core` only, and no other model depends on it: a model that
describes values, such as a mapping or a set of recording rules, stays free of units, and a tool
that needs both resolves the units of an element.

## A unit, six ways

`Unit` is abstract; its class says how a unit is defined. Every unit has a `symbol`, an optional
`ucumCode` (the code of the Unified Code for Units of Measure), its `dimension` and the kinds of
quantity it measures (`quantityKind`).

| Class | Defined by | A value converts to the reference as | Examples |
| --- | --- | --- | --- |
| `BaseUnit` | one base dimension to the power 1 | — | m, kg, s, A, K, mol, cd |
| `DerivedUnit` | `factor: UnitFactor[*]`, ordered as in the symbol — a product of powers of units; no factor is the unit one | the product of the factors | N = kg·m·s⁻², km/h = km·h⁻¹, `1` |
| `ScaledUnit` | `reference`, `multiplier`, `offset[0..1]` | `(value + offset) × multiplier` | °C (K, 1, 273.15), h (s, 3600), % (one, 0.01) |
| `PrefixedUnit` | `reference`, `prefix` | `value × base^exponent` of the prefix | hPa, km, mW |
| `AlternateUnit` | `reference` | the value itself — the same unit for another kind of quantity | rad and sr (one), Bq (Hz), Sv (Gy) |
| `LogarithmicUnit` | `reference`, `base`, `divisor` | `base ^ (value / divisor)` | dBm (mW, 10, 10) |

The last four are `ReferringUnit`s (abstract, `reference: Unit[1]`). The other classes:

| Class | Supertype | Features |
| --- | --- | --- |
| `SystemOfUnits` | `core::Package` | `abbreviation[0..1]`, `allowedUnit: Unit[*]` (units of other systems that hold here); owns its dimensions, kinds, prefixes and units |
| `Dimension` | `core::ModelElement` | the exponents `length`, `mass`, `time`, `electricCurrent`, `thermodynamicTemperature`, `amountOfSubstance`, `luminousIntensity` (`Integer`, 0 unless set) |
| `QuantityKind` | `core::ModelElement` | `dimension[1]`, `broader[0..1]` (width is narrower than length), `symbol[0..1]` |
| `Prefix` | `core::ModelElement` | `symbol`, `base` (2 or 10), `exponent` — kilo is 10³, kibi 2¹⁰ |
| `UnitFactor` | `core::Element` | `unit[1]`, `exponent` (not 0) |
| `Quantity` | `core::ModelElement` | `modelElement: ModelElement[1..*]`, `unit[1]`, `quantityKind[0..1]`, `scale` (`absolute`, `relative`) |

A dimension is named by its QUDT dimension vector: the exponents of amount of substance (A),
electric current (E), length (L), luminous intensity (I), mass (M), thermodynamic temperature (H)
and time (T), and `D1` for a dimensionless quantity — speed is `A0E0L1I0M0H0T-1D0`.
`Dimensions.ownedIn(namespace, exponents)` finds a dimension or creates it under that name, so a
namespace holds each dimension once. A `QuantityKind` names what a unit measures where the unit
alone does not: a joule measures energy, work or heat; s⁻¹ is a frequency or a radioactivity.

Every reference is one-way and carries an EMOF opposite role name starting with `units`; the
package declares hidden opposites. `Float` and `Integer` are the CWM data types: multipliers,
offsets, bases and divisors are decimal strings, exponents are `long`s.

OCL invariants: `Prefix.BaseTwoOrTen`, `Prefix.ExponentNotZero`,
`DerivedUnit.FactorExponentNotZero`, `ReferringUnit.NotSelfReferring`,
`QuantityKind.BroaderNotSelf`.

## Which unit applies

A `Quantity` names the elements whose values its unit measures, the way a `Description` names the
elements it describes. `Quantities.of(element)` returns the quantity naming the element, else —
for an attribute or a parameter whose type is a data type — the quantity naming that data type:
a data type with a quantity is a domain type, and every attribute of it has its unit. Nothing
further: a class or a package holds no values. `Quantities.unitOf(element)` returns the unit.

The quantity lives in a namespace of one's choosing, not in the element: an attribute is no
namespace, and a data type may own nothing (CWM 1.1, C-3-2). The quantities of an element are
found through `InverseReferences` — by a scan of the resource set, or at once after
`InverseReferences.install(resourceSet)`.

`scale` tells a value from a difference: 25 °C is 298.15 K, but a difference of 5 °C is 5 K. A
`Quantity` here is what ISO 80000 calls a quantity — the kind of value an element holds — and
carries no number; the quantities of JSR 385 and the quantity values of QUDT carry one.

## Converting values

`Units.convert(value, from, to)` converts between units of one dimension in `BigDecimal` with
`MathContext.DECIMAL128`. Every unit but a logarithmic one is linear on its system unit,
`factor × value + term`, composed along its definition; a product multiplies the conversions of
its factors and needs factors without offset — exact, so km/h is 1000 / 3600 m/s, not a rounded
multiplier. `convert(value, from, to, scale)` with `Scale.RELATIVE` drops the offsets. A
logarithmic unit converts through its reference in `double`. Units of different dimensions are
refused with both dimension vectors in the message.

`Units.systemUnit(unit)` follows scales, prefixes and logarithms to the unit the conversion ends
at; `Units.compatible(a, b)` compares dimensions.

## The SI

`SI.create(owner)` builds the SI as a `SystemOfUnits` named `SI`:

- the 33 quantity kinds of JSR 385 with their dimensions;
- the 24 decimal prefixes from quetta (10³⁰) to quecto (10⁻³⁰), micro with the micro sign `µ`;
- the seven base units and the unit one;
- the 22 coherent derived units with special names, from radian to katal, the degree Celsius as
  kelvin with multiplier 1 and offset 273.15;
- the units accepted for use with the SI — minute, hour, day, degree, litre, tonne, hectare — the
  percent and the gram;
- combinations for sensors, weather and energy: m², m³, m/s, m/s², km, cm, mm, km/h, mm/h, hPa,
  kPa, kJ, kW, kWh, W/m², mA, mV, ms, mg, kg/m².

59 units with unique symbols, each with its UCUM code. The names are constants (`SI.METRE`,
`SI.DEGREE_CELSIUS`); `SI.unit(si, name)`, `SI.kind(si, name)` and `SI.prefix(si, name)` find
them. A degree is π / 180 radian as a decimal approximation, as in QUDT. Logarithmic units belong
to the system of a project.

A symbol names a unit only within its system: data sources write units their own way (`F` for
degree Fahrenheit, `kg/m2`), so a project defines its units in a system of its own, with the
symbols of its sources. `F` is then degree Fahrenheit there and farad in the SI; a system may
allow units of another one (`allowedUnit`), and `Units.bySymbol(system, symbol)` searches the
owned units first.

## Validation

`UnitsModelValidator.check(root)` returns findings (`Finding(severity, rule, element, message)`);
`validate(root)` only the errors.

| Rule | Checks |
| --- | --- |
| `U1` | a quantity names attributes (columns, fields, measures), parameters and data types — not classes, association ends or packages; no data type owns a quantity |
| `U2` | a base unit has one base dimension with exponent 1; the dimension a unit states is the one of its definition |
| `U3` | at most one quantity names an element; its kind has the dimension of its unit; (warning) a kind the unit does not measure, neither directly nor through a broader kind |
| `U4` | decimal numbers parse, a multiplier is not zero, a logarithm has a base above zero and not one and a divisor not zero; prefixes have base 2 or 10 and an exponent; no unit is defined through itself, no kind is narrower than itself, a broader kind has the same dimension; a factor of a product has no offset and is not logarithmic |
| `U4` (warning) | two units of a system with one symbol, an allowed unit with a symbol the system already uses, a prefix on a prefixed unit, a scale by 1 without offset |
| `U5` (warning) | a quantity on an attribute or parameter whose type holds no numbers — by name, JDBC type code or tag `daanse.ecore.instanceClassName` — unless that type has a unit of its own |
| `U6` (warning) | an own quantity of another dimension than the quantity of the element's data type |
| `U7` (warning) | a named element, a unit, a reference, a factor unit or a dimension that does not resolve |

## Helpers

- `SystemsOfUnits.create(owner, name)`, `abbreviation`, `allow`.
- `Dimensions.create(owner, name, length, mass, time, electricCurrent, temperature,
  amountOfSubstance, luminousIntensity)`, `ownedIn(owner, exponents…)`, `find`, `base(position,
  exponent)`, `none`, `exponents`, `plus`, `same`, `isDimensionless`, `code`.
- `QuantityKinds.create(owner, name, dimension)`, `broader`, `symbol`, `units` (the units that
  measure a kind), `isA` (along `broader`).
- `Prefixes.create(owner, name, symbol, base, exponent)`, `factor` (exact).
- `Units.base`, `derived` with `factor(unit, exponent)`, `one`, `scaled`, `prefixed`, `alternate`,
  `logarithmic`, `ucum`, `applicableTo`; reading: `definedDimension`, `systemUnit`, `toBase`,
  `compatible`, `convert`, `bySymbol`, `byName`, `of`. A helper that defines a unit through other
  units sets its dimension from the definition.
- `Quantities.create(owner, name, unit, elements…)`, `kind`, `relative`, `own`, `of`, `unitOf`,
  `typeOf`.
- `SI.create`, `unit`, `kind`, `prefix`.

## Where units apply

Every attribute of CWM takes a unit the same way, since they are all `core::Attribute`s: an
attribute of a class, a relational column, a record field, an OLAP measure; also a behavioral
parameter and a data type, for every attribute of that type. A class, an association end and a
package hold no values of their own and take no unit; `UnitsAcrossCwmTest` shows each place.

## Where it comes from

| QUDT 3.5 | JSR 385 2.2, Indriya | `units` |
| --- | --- | --- |
| `SystemOfUnits` (`hasBaseUnit`, `hasDefinedUnit`, `hasAllowedUnit`, `prefix`) | `SystemOfUnits` | `SystemOfUnits ⊂ core::Package`: owned units and prefixes, `allowedUnit` |
| `QuantityKindDimensionVector` (A E L I M H T D) | `Dimension`, seven base dimensions | `Dimension` with seven exponents, named by the QUDT vector |
| `QuantityKind` (`hasDimensionVector`, `applicableUnit`, `skos:broader`, `symbol`) | the interfaces of `javax.measure.quantity` | `QuantityKind` (`dimension`, `broader`, `symbol`); the applicable units are `Unit.quantityKind` read backwards |
| `Unit` (`symbol`, `ucumCode`, `hasDimensionVector`, `hasQuantityKind`) | `Unit` (symbol, name, dimension) | `Unit.symbol`, `ucumCode`, `name`, `dimension`, `quantityKind` |
| `hasFactorUnit` (unit, exponent) | `ProductUnit` | `DerivedUnit.factor: UnitFactor{unit, exponent}` |
| `conversionMultiplier`, `conversionOffset`, `scalingOf` | `TransformedUnit` | `ScaledUnit{reference, multiplier, offset}`, with QUDT's formula |
| `prefix`, `Prefix` (symbol, multiplier) | `Prefix` (symbol, value, exponent), `Unit.prefix` | `Prefix{symbol, base, exponent}`, `PrefixedUnit{reference, prefix}` |
| — (a unit of its own) | `AlternateUnit` | `AlternateUnit{reference}` |
| decibel units: no multiplier, no UCUM code | `LogConverter`, `ExpConverter` | `LogarithmicUnit{reference, base, divisor}` |
| the unitless unit of the SI | `ONE`, an empty product | `one`, a `DerivedUnit` without factors |
| `Quantity`, `QuantityValue` (kind, value, unit), `isDeltaQuantity` | `Quantity` (value, unit, `Scale`) | `Quantity{modelElement, unit, quantityKind, scale}` — without a value |

## Not carried

The codes of IEC 61360, UNECE and eCl@ss, `siExactMatch` and LaTeX symbols of QUDT; currencies
and counting units as classes of their own; fractional exponents; binary prefixes in the SI
(`Prefix` allows base 2, the SI catalogue has none); Indriya's annotated units and the unit
formats of JSR 385; a
value together with its unit (CWM has `warehouseoperation::Measurement` for that, and its `unit`
stays text).

## Design decisions

- A unit is no data type: the type of an attribute stays the type of its values (`Float`,
  `DECIMAL`), the unit says what the values mean.
- A quantity is owned by a namespace given explicitly, never by the element it names; a quantity
  owned by a data type breaks C-3-2 and is refused (`U1`).
- The unit of an element is its own quantity's, else its data type's — one step, no chain through
  classes and packages.
- Every unit states its dimension, as in QUDT; the helpers compute it from the definition, and
  `U2` finds a dimension changed by hand.

## Building

The `.ecore` is written by `UnitsEcoreBuilder` (`src/test`). `UnitsEcoreUpToDateTest` checks that
it is up to date; with `-Dregenerate.ecore=true` it rewrites `model/units.ecore`, after which the
module needs a `clean` build (the code generator merges into existing files).

The test-jar holds `examples/ExampleUnits`: the system `fennec-units` with the symbols sensor
files write — `F`, `%`, `mm/h`, `°C`, `dBm`, `kg/m2`, `kJ/m2` and the units they are defined
through — for the tests of modules that attach units to their examples, and `examples/Elements`,
which creates the CWM elements the tests are built of.

## Tests

- `UnitsModelTest` — supertypes and abstract classes, hidden opposites and OCL delegates on the
  package, references one-way with an opposite role name, CWM data types; each OCL invariant
  positive and negative; quantities written to XMI and found again from the element, with and
  without an installed index.
- `DimensionsTest` — dimension vectors, find or create, products.
- `UnitsTest` — the dimension of every definition, system units, conversions (°C, hPa, km/h, kWh,
  %, L, mg, °), Fahrenheit after QUDT, differences, dBm, refused conversions, lookup by symbol.
- `QuantitiesTest` — own quantity, quantity of the data type, precedence, one quantity for several
  elements.
- `SITest` — the content of the catalogue; it validates without finding.
- `UnitsModelValidatorTest` — one positive and at least one negative case per rule.
- `UnitsAcrossCwmTest` — units defined six ways, which unit applies, every place of CWM and the
  places a unit does not apply, and the XMI a model with quantities serializes to.
