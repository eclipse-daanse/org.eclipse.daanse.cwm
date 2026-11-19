# org.eclipse.daanse.cwm.model.daanse.notation

How a value of an attribute is written where it is stored or exchanged: the lexical form beside the type. A
`Notation` names the attributes it holds for (columns, record fields, measures, resources), parameters or data
types, and holds at most one form, the words that mean no value and how a text is trimmed. The class of the form says
the kind of value: `TemporalText`, `TemporalNumber`, `NumberText`, `BooleanText`, `PatternText`. `Notations`
builds them, `Codec` reads a value from its text and writes the text of a value, `NotationModelValidator` checks
the rules NT1 to NT18.

The shape follows W3C "CSV on the Web" (the tabular data model, the parsing of cells), with three additions it lacks:
a zone for values written without one, time as a number of units since an epoch, and names of months and days in a
language.

## What it is not

- Not the display format for people: an OLAP `formatString` (MDX `FORMAT_STRING`) says how a cell is shown to a
  reader in the reader's language, one way only. A notation says how a value is stored, both ways, the same for
  every reader.
- No scale: a percent, a per-mille or a thousand is a unit (`units::ScaledUnit`). `12 %` reads as 12; the quantity of
  the attribute says it is percent. An unquoted `%` or `‰` in a number pattern is an error (NT15).
- No geometry: how a geometry column stores its value (WKT, WKB, GeoJSON) is said by `spatial::GeometryFeature.storage`.
- No list in a cell (CSVW `separator`), no duration, no default value, no length or value range: other models say them
  or they are open.

## Relations

- Builds on CWM `core` and on `units` (the unit of a `TemporalNumber` is a `units::Unit` of time).
- Knows no OLAP, no relational model, no sensiNact; no model depends on it. A tool or a connector resolves the
  notation of an element (`Notations.of`) and reads its values with a `Codec`.

## Classes

Every class but the forms extends `core::ModelElement`; a form extends `core::Element` (it has no name and no life of
its own) and is owned by its notation. References are one-way with an EMOF `Property.oppositeRoleName` (prefix
`notation`); texts are CWM `String`, flags CWM `Boolean`.

| Class | Anchors to | Purpose |
| --- | --- | --- |
| `Notation` | `modelElement : core::ModelElement` 1..* (attributes, parameters, data types; NT1) | how the values of the elements are written: one form at most, missing words, trimming |
| `Form` | abstract, owned by `Notation.form` (0..1) | the lexical form of a value |
| `TemporalText` | a form | a date, a time or both as text: a named ISO 8601 form or an LDML date pattern |
| `TemporalNumber` | a form; `unit : units::Unit` | a date, a time or an instant as a count of a unit of time since an epoch |
| `NumberText` | a form | a number as text: decimal and group character, an optional LDML number pattern |
| `BooleanText` | a form | a truth value as one of the true or false words |
| `PatternText` | a form | a value of any other type as text the whole of which matches a regular expression |

An element's own notation comes before that of its data type: a data type `GermanDate` with a notation
gives every attribute of that type its form, an attribute's own one wins (as `units` does with quantities).

### Notation

| Feature | Type | Mult. | Meaning |
| --- | --- | --- | --- |
| `modelElement` | `core::ModelElement` | 1..* | the attributes, parameters and data types whose values are written so |
| `form` | `Form` | 0..1 | the form; without one the value is the text itself |
| `missingWords` | `core::String` | 0..* ordered | texts that mean no value, compared exactly after trimming; the first is what a writer writes for no value; the empty text is one only where it is named |
| `trim` | `Trim` | 1 | which white space a reader removes first: `both` (first literal, the default), `start`, `end`, `none` |

### TemporalText

| Feature | Type | Mult. | Meaning |
| --- | --- | --- | --- |
| `named` | `TemporalForm` | 0..1, unsettable | an ISO 8601 form: `date`, `basicDate`, `time`, `basicTime`, `timeWithOffset`, `basicTimeWithOffset`, `dateTime`, `basicDateTime`, `dateTimeWithOffset`, `basicDateTimeWithOffset`; exactly one of `named` and `pattern` (NT3) |
| `pattern` | `core::String` | 0..1 | a date pattern of Unicode LDML (UTS #35): `dd.MM.yyyy HH:mm`, `d. MMMM yyyy`, `yyyyMMdd`; text in single quotes; a lone `T` is a literal, as CSV on the Web writes it |
| `zone` | `core::String` | 0..1 | the zone of a text that holds none: an IANA zone (`Europe/Berlin`) or a fixed offset (`+01:00`, `Z`); the text's own offset or zone wins |
| `language` | `core::String` | 0..1 | a BCP 47 tag (`de`, `en-GB`) for names of months, days, AM and PM; needed where the pattern writes words (NT6) |
| `lenient` | `core::Boolean` | 1, default false | reads texts that are close: fewer digits than the pattern writes, names and literals in any case, an abbreviated name with or without its dot, a name by three or more of its first letters where that is one name, a local time in a gap moved forward; values out of range stay errors |

The named forms write seconds always and a fraction of a second where it is not zero (fewest digits; a reader takes 1
to 9 digits after a point or a comma); an extended offset is `Z` or `+hh:mm`, a basic one `Z` or `+hhmm`.

### TemporalNumber

| Feature | Type | Mult. | Meaning |
| --- | --- | --- | --- |
| `epoch` | `core::String` | 1 | where the count starts, ISO 8601: an instant (`1970-01-01T00:00:00Z`) or, without an offset, a local date or date and time (`1899-12-30`) |
| `unit` | `units::Unit` | 1 | the unit of the count, a unit of time (dimension T1) that counts from zero (NT9): second, millisecond, day |
| `zone` | `core::String` | 0..1 | the zone of a local epoch counted in days or longer units (NT14): it makes the local date and time an instant |

`Notations.UNIX_EPOCH` and `SPREADSHEET_EPOCH` name the two common epochs: Unix seconds are
`temporalNumber(r, UNIX_EPOCH, second)`, spreadsheet serial dates `temporalNumber(r, SPREADSHEET_EPOCH, day)`.

### NumberText, BooleanText, PatternText

| Class | Feature | Type | Mult. | Meaning |
| --- | --- | --- | --- | --- |
| `NumberText` | `decimalChar` | `core::String` | 1, default `.` | one character between the whole part and the fraction |
| | `groupChar` | `core::String` | 0..1 | one character between groups of the whole part, other than the decimal character (NT10); none: no grouping |
| | `pattern` | `core::String` | 0..1 | an LDML number pattern written with these two characters (`#,##0.00`, `#.##0,0##`, `0.###E0`), text in quotes, a negative subpattern after `;` (`#,##0.00;(#,##0.00)`) |
| | `bare` | `core::Boolean` | 1, default true | the text is the number alone; where false, white space, currency signs, `%`, `‰`, `‱` and letters around the number are stripped on reading (`€ 12,50`, `12,5 %`, `EUR -3,00`), a pattern's own prefix and suffix are optional |
| | `lenient` | `core::Boolean` | 1, default false | a group character may stand between any two digits of the whole part, as CSV on the Web reads it, whatever groups the pattern states; where false, the groups are those of the pattern or, without one, the usual ones (below) |
| `BooleanText` | `trueWords` | `core::String` | 1..* ordered | the words for true; the first is written |
| | `falseWords` | `core::String` | 1..* ordered | the words for false; the first is written |
| | `ignoreCase` | `core::Boolean` | 1, default false | reads the words in any case |
| `PatternText` | `pattern` | `core::String` | 1 | a regular expression in the syntax of ECMAScript; the whole text matches |

## Reading and writing

`Codec.of(notation)` makes a reader and writer once; it is immutable. `read(text)` trims, gives `null` for
`null` and for a missing word, then reads by the form; `write(value)` writes `null` as the first missing word (or
`null` where there is none) and refuses a value whose text would be a missing word. Plain Java, `java.time`,
`java.util.regex` and own code; no dependency beyond the build's EMF and CWM.

| Form | Reads as | Writes |
| --- | --- | --- |
| `TemporalText` | the kind the text holds: `LocalDate`, `YearMonth`, `Year`, `MonthDay`, `LocalTime`, `OffsetTime`, `LocalDateTime`; `OffsetDateTime` with an offset in the text or a fixed `zone`; `ZonedDateTime` with a zone id in the text or a `zone` whose offset changes | any of these and `Instant`, moved into the `zone` where the form names one |
| `TemporalNumber` | `Instant` from an instant epoch; `LocalDateTime` from a local one, `ZonedDateTime` with a `zone` | instants, `LocalDateTime`, `LocalDate`; the shortest decimal of the count |
| `NumberText` | `BigDecimal` exactly as written; `Double` for `NaN`, `INF`, `-INF` | any `Number`, rounded half to even to the pattern's fraction digits |
| `BooleanText` | `Boolean` | the first true or false word |
| `PatternText`, no form | the text | a text that matches |

A text that does not fit fails with an `UnfitTextException`: the position from 0 in the text as given (before
trimming) and the reason, e.g. `'19.13.2026' does not fit at position 3: 13 is no month (1 to 12)`.

The date fields read are `y`, `u` (`yy` is 2000 to 2099), `M`, `L` (numbers, abbreviated and full names), `d`, `D`,
`E` (abbreviated and full names; checked against the date unless lenient), `a`, `h`, `H`, `K`, `k`, `m`, `s`, `S`
(`S` written n times reads 1 to n digits), `X` and `x` (1 to 5), `Z` (1 to 3 and 5), `VV`. Other fields of LDML (era,
quarter, week, narrow names, zone names, a date with an offset) are well-formed but refused by `Codec.of` with an
`UnsupportedOperationException`. A number pattern with `@`, `*`, `¤` or an engineering exponent is refused the same
way.

A number text read strictly (the default) wants its groups where it has group characters: without a pattern the
usual ones, a first group of one to three digits, then groups of three (`1.234.567,5`); with a pattern those the
pattern states (`#,##,##0` reads `12,34,567`). A text that breaks them fails at the group that does not fit:
`12.5` with the decimal character `,` and the group character `.` is no 125 but an error at position 3, the last
group has 1 digit, not 3. A text without group characters fits either way (`1234567,5`). Read leniently, a group
character may stand between any two digits, as CSV on the Web reads it, never twice in a row and never at the end
of the whole part. A writer writes the proper groups in both cases.

## Rules

`NotationModelValidator` gives its findings in the one record of the models (`Finding`, module
`notation`); an error makes `Codec.of` refuse the form, a warning does not. `DecimalCharIsOneCharacter`,
`GroupCharIsOneCharacter`, `DecimalAndGroupDiffer` (number text), `NoWordTrueAndFalse` (boolean text),
`NoMissingWordIsTrueOrFalse` (notation) and `UnitOfTime` (temporal number) stand in the Ecore as OCL invariants
as well. NT3 is not: OCL reads an unset `named` as its first literal.

| Rule | Severity | Says |
| --- | --- | --- |
| NT1 | error | a notation names attributes, parameters or data types, not a class, a package or an association end; no data type owns one (CWM 1.1, C-3-2) |
| NT2 | error | an element carries at most one notation |
| NT3 | error | a temporal text names exactly one of `named` and `pattern` |
| NT4 | error | a date pattern is well-formed LDML: quotes close, every unquoted letter is a field of LDML, written a number of times the field takes (`t` is no field, `ddd` is too long, `j` belongs to skeletons) |
| NT5 | warning | a date or number pattern outside the set CSV on the Web obliges every reader to know: not every reader can read it |
| NT6 | error | a pattern with fields written in words (`MMM`, `MMMM`, `EEE`, `a`, eras, zone names) names a language; a language is a well-formed BCP 47 tag |
| NT7 | error | a time of day meant as an instant (the element's type holds an instant: `TIMESTAMP WITH TIME ZONE`, `Instant`, `OffsetDateTime`) has a zone in its text or names a zone; a local count of such an element names a zone |
| NT8 | warning | a time of day with neither a zone in its text nor a zone named, where the type does not say it is local (a text column says nothing): it is read as a local time |
| NT9 | error | the unit of a temporal number is a unit of time that counts from zero |
| NT10 | error | the decimal and the group character are one character each, neither a digit, a sign nor `E`, and differ |
| NT11 | error | no word is true and false, and no missing word is a true or a false word (in any case where the words are read in any case) |
| NT12 | error | the form fits the type of the element where the type is known (below) |
| NT13 | error | a zone is an IANA zone or a fixed offset, an epoch an ISO 8601 date or date and time, and a time without a date names a fixed offset, not a zone whose offset changes |
| NT14 | error | a temporal number names a zone only where its unit is a day or longer and its epoch is local |
| NT15 | error | a number pattern is well-formed LDML and holds no `%` or `‰` outside quotes (a scale is a unit) |
| NT16 | error | the regular expression of a pattern text compiles |
| NT17 | warning | a reference that does not resolve |
| NT18 | warning | a regular expression holds constructs ECMAScript does not know (`\A`, `\Z`, `\z`, `\G`, `\Q`, possessive quantifiers, atomic groups, inline flags such as `(?i)`, class intersection) |

An error and a warning never share a code. `validate` gives the errors as lines; `check` gives every finding.

### What fits a type (NT12)

A type is known by its name (SQL, CWM, Ecore: `VARCHAR(40)`, `String`, `EString`, `TIMESTAMP WITH TIME ZONE`), else
by its JDBC type code (an SQL type of the relational model), else by the Java class the tag
`daanse.ecore.instanceClassName` names. An unknown type is no finding. An SQL type is a stored type: it holds what
the form writes. Any other type is the type of a value: it holds what the form writes or the value.

| Form | Stored as | Value |
| --- | --- | --- |
| `TemporalText` | text; a number where the form is digits only, starting with the year (`basicDate`, `yyyyMMdd`, `yyyyMM`, `yyyy`: a key of a day) | a time |
| `TemporalNumber` | a number (a text column is an error) | a time |
| `NumberText` | text | a number |
| `BooleanText` | text; a number where every word is a whole number (`1`, `0`) | a truth value |
| `PatternText`, no form | text | text |

## Relation to CSV on the Web

| CSV on the Web | Here |
| --- | --- |
| column `datatype` with `base` | the type of the element (CWM, SQL); the notation stands beside it |
| datatype `format` for dates and times (a UAX #35 pattern from a fixed set) | `TemporalText.pattern`; the set is checked by NT5, any well-formed LDML is allowed |
| XML Schema lexical form where no format is given | `TemporalText.named` (ISO 8601 forms) |
| — | `TemporalText.zone` (CSVW leaves a value without a zone without one), `language` (CSVW has no names of months), `TemporalNumber` |
| datatype `format` for numbers: `decimalChar`, `groupChar`, `pattern` | `NumberText` with the same names; `%` and `‰` scale in CSVW, here they are units (NT15, `bare`) |
| a number without a pattern takes a group character between any digits | `NumberText.lenient`; read strictly, the groups are the usual ones |
| datatype `format` for booleans: `true\|false` | `BooleanText` with one or more words each, `ignoreCase` |
| datatype `format` for other types: a regular expression (ECMAScript) | `PatternText.pattern` |
| column `null` (default the empty string) | `Notation.missingWords` (default none: name `""` for CSVW's behaviour) |
| dialect `trim` (true, false, start, end) | `Notation.trim` (`both`, `none`, `start`, `end`) |
| `separator`, `default`, `required`, length and value constraints, durations | not here |

## Relation to JSON Schema

| JSON Schema | Here |
| --- | --- |
| `"format": "date"` | `named = date` |
| `"format": "time"` (RFC 3339 full-time, with an offset) | `named = timeWithOffset` |
| `"format": "date-time"` (RFC 3339) | `named = dateTimeWithOffset` |
| `"pattern"` (ECMA-262) | `PatternText.pattern` |
| `"type": "number"` written as a JSON number | no notation: the number is not text |

## Example

A text column with dates of the stations in Thuringia, a flag and a count of seconds (ids shortened):

```xml
<ownedElement xsi:type="notation:Notation" name="observed" modelElement="_readings.observed">
  <missingWords>-</missingWords>
  <form xsi:type="notation:TemporalText" pattern="dd.MM.yyyy HH:mm" zone="Europe/Berlin"/>
</ownedElement>
<ownedElement xsi:type="notation:Notation" name="dry" modelElement="_readings.dry">
  <form xsi:type="notation:BooleanText">
    <trueWords>J</trueWords>
    <falseWords>N</falseWords>
  </form>
</ownedElement>
<ownedElement xsi:type="notation:Notation" name="logged" modelElement="_readings.logged">
  <form xsi:type="notation:TemporalNumber" epoch="1970-01-01T00:00:00Z" unit="_second"/>
</ownedElement>
```

```java
Notation observed = Notations.create(notes, "observed", observedColumn);
Notations.missing(observed, "-");
Notations.zone(Notations.temporalText(observed, "dd.MM.yyyy HH:mm"), "Europe/Berlin");
Codec codec = Codec.of(observed);
codec.read("19.03.2026 10:15");                       // 2026-03-19T10:15+01:00[Europe/Berlin]
codec.write(Instant.parse("2026-07-01T08:00:00Z"));   // 01.07.2026 10:00
codec.read("-");                                       // null
```

## The Ecore

`model/notation.ecore` is written by `NotationEcoreBuilder` (test sources, package `build`).
`NotationEcoreUpToDateTest` fails while the checked-in file differs from what the builder writes; after a change
to the builder run it once with `-Dregenerate.ecore=true`, then build the module with `mvn clean install` (the code
generator merges into files that exist).
