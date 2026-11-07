# org.eclipse.daanse.cwm.model.daanse.units

Units of measurement for CWM elements, after QUDT 3.5 and JSR 385 2.2 (no file of either is
copied). A `Quantity` says in which `Unit` the values of attributes — columns, record fields and
measures among them — of parameters or of data types are measured. The class of a unit says how it
is defined: `BaseUnit`, `DerivedUnit` (a product of powers of units), `ScaledUnit` (multiplier and
offset), `PrefixedUnit`, `AlternateUnit`, `LogarithmicUnit`; every unit has a symbol, a UCUM code,
its `Dimension` and the `QuantityKind`s it measures. A `SystemOfUnits` owns units, prefixes, kinds
and dimensions; `SI.create` builds the SI. `Units.convert` converts between units of one
dimension; `UnitsModelValidator` checks the rules U1 to U7.

## Relations

- Builds on CWM `core` only.
- No model depends on it: a tool resolves the unit of an element. The Fennec converter of
  `sensinact` writes the symbol of that unit as the unit of a resource.

## Example

Degrees Celsius defined on kelvin, metres per second as a product, hectopascal as a prefixed
pascal, and the quantity of an attribute (ids left out):

```xml
<ownedElement xsi:type="units:Dimension" name="A0E0L0I0M0H1T0D0" thermodynamicTemperature="1"/>
<ownedElement xsi:type="units:QuantityKind" name="Temperature" dimension="_A0E0L0I0M0H1T0D0"/>
<ownedElement xsi:type="units:BaseUnit" name="kelvin" symbol="K" ucumCode="K" dimension="_A0E0L0I0M0H1T0D0" quantityKind="_Temperature"/>
<ownedElement xsi:type="units:ScaledUnit" name="degreeCelsius" symbol="°C" ucumCode="Cel" dimension="_A0E0L0I0M0H1T0D0" quantityKind="_Temperature" reference="_kelvin" multiplier="1" offset="273.15"/>
<ownedElement xsi:type="units:DerivedUnit" name="metrePerSecond" symbol="m/s" ucumCode="m/s" dimension="_A0E0L1I0M0H0T-1D0" quantityKind="_Speed">
  <factor unit="_metre" exponent="1"/>
  <factor unit="_second" exponent="-1"/>
</ownedElement>
<ownedElement xsi:type="units:PrefixedUnit" name="hectopascal" symbol="hPa" ucumCode="hPa" dimension="_A0E0L-1I0M1H0T-2D0" quantityKind="_Pressure" reference="_pascal" prefix="_hecto"/>
<ownedElement xsi:type="units:Quantity" name="temperature" modelElement="_Boiler.temperature" unit="_degreeCelsius" quantityKind="_Temperature"/>
```
