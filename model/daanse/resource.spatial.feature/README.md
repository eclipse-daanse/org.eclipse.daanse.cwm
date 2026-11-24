# org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature

Collections of features, as OGC API Features names them, as CWM classes. A `FeatureCollection` is
a `core::Class`, owned by a `core::Package`; its features are its `FeatureProperty`s, each a
`core::Attribute` typed by a data type. The names become the ids of the API
(`/collections/{name}`, the property names of a feature and of the queryables). The collection is
described once, independent of where its rows come from: a table or view, an SQL statement, or the
members of a cube.

## The concepts

- **The collection** says what a client sees: the properties of a feature, the one whose value is
  the feature id, the primary geometry (the one `bbox` is evaluated on), the time of a feature, the
  reference systems it offers, which properties are queryable, returned, sortable. It is not a
  table: it names no column.
- **The geometry** is said by `spatial`, not here. The collection names its primary geometry, a
  `spatial::GeometryFeature`; that feature says what the geometry is (kind, height, reference
  system) and its holdings say which properties hold it: a property of the type `Geometry`, or
  numeric properties such as `east` and `north`. A geometry and its envelope are two geometry
  features; the collection names the one, the other is derived from it.
- **Where the rows come from** is said by a mapping model that refers to the collection (a table, a
  statement, the members of a cube). The collection and its queryables never change with the
  origin.
- **What a server does** is not held: the extent (computed from the rows), paging, encodings, the
  filter language. The collection says what may be filtered on, not how a filter is written.

## Overview

| Concept | Class or helper | The question it answers | Example |
| --- | --- | --- | --- |
| a collection | `FeatureCollection`, `FeatureCollections.create` | which features, under which id and title? | [A collection and its key](#a-collection-and-its-key) |
| a property | `FeatureProperty`, `FeatureProperties` | which values does a feature carry, and what may a client do with them? | [What a property says of its values](#what-a-property-says-of-its-values) |
| the key | `FeatureCollection.featureKey` | which property is the feature id? | [A collection and its key](#a-collection-and-its-key) |
| the primary geometry | `FeatureCollection.geometry`, a `spatial::GeometryFeature` | which geometry is `bbox` evaluated on, and which properties hold it? | [A geometry in a property](#a-geometry-in-a-property), [A point as two numbers, with its envelope](#a-point-as-two-numbers-with-its-envelope) |
| the time | `instant`, `intervalStart`, `intervalEnd` | which property is `datetime` evaluated on? | [The time of a feature](#the-time-of-a-feature) |
| the queryables | `Queryables`, `TypeFamily` | what does `/collections/{id}/queryables` answer? | [The queryables](#the-queryables) |
| the reference systems | `referenceSystems`, `FeatureCollections.crs` | which systems does the list `crs` name? | [The reference systems of a collection](#the-reference-systems-of-a-collection) |

## The running example

`src/test/.../examples/City` is the open geography of a city in four collections: `districts`
(polygons in a property `outline`, valid from a day until a day), `schools` (points in a property
`location`, opened on a day), `stops` (the bus stops of the transit operator, points held as the
numbers `east` and `north`, with the envelope of each in four numbers) and `trees` (the trees of the
tree register, each point held as the property `position` and as the numbers `longitude` and
`latitude`). The districts and schools are stored in ETRS89 / UTM zone 32N (`utm32`, EPSG 25832),
the stops and trees in WGS 84 (`crs84`). The types `string`, `integer`, `decimal` and `date` stand
in the package `types`, the geometry features in `geodata`, the collections in `features`. Every
snippet below is code of `City` or of `FeatureExamplesTest`, which runs it. More examples in other
settings, each a small story, stand in `src/test/.../examples/FeatureCollectionsAcrossCwmTest`.

## A collection and its key

For the features a client asks for under one id, with the property that identifies each.

```java
FeatureCollection districts = FeatureCollections.create(features, "districts");
FeatureProperty districtId = FeatureProperties.required(districts, "district_id", string);
FeatureProperty districtLabel = FeatureProperties.create(districts, "label", string);
FeatureProperty residents = FeatureProperties.create(districts, "residents", integer);
districts.setDisplayName("City districts");
FeatureCollections.describe(districts, "The districts of the city, each valid from a day until a day.");
FeatureCollections.key(districts, districtId);
districtId.setQueryable(false);
FeatureCollections.properties(districts);                   // district_id, label, residents, ... in order
FeatureCollections.property(districts, "label");            // districtLabel
FeatureCollections.title(districts);                        // City districts
FeatureCollections.description(districts);                  // The districts of the city, ...
FeatureProperties.required(districtId);                     // true: multiplicity 1..1
FeatureProperties.title(districtLabel);                     // label: no display name, the name
```

A server answers `/collections/districts` with the title and the description and
`/collections/districts/items/{district_id}` with one district. Rules: FC1 to FC4, FC16.

## A geometry in a property

For a collection whose geometry is one property that holds it whole.

The schools are points in the property `location` of the type `Geometry`:

```java
GeometryFeature schoolPoint = SpatialElements.feature(geodata, "schoolPoint", GeometryKind.POINT, utm32);
schoolPoint.setMultiplicity(GeometryMultiplicity.SINGLE);
FeatureProperty location = FeatureProperties.geometry(schools, "location", types, schoolPoint);
FeatureCollections.geometry(schools, schoolPoint);
FeatureCollections.holding(schoolPoint, schools);           // the WholeHolding of location, NATIVE
FeatureCollections.geometryFeature(location);               // schoolPoint
FeatureProperties.isGeometry(location);                     // true
```

`FeatureProperties.geometry` types the property with the data type `Geometry` of `types` and makes
it a whole holding of `schoolPoint`. The server writes the geometry of a school from `location`.
More examples: a collection over a class of a logical model with a geometry property,
`FeatureCollectionsAcrossCwmTest.aCollectionOverAClassWithAGeometryProperty`.
Rules: FC7 (a property held whole is of the type `Geometry`), FC8 (a geometry is no key, no time,
has no values), FC19 (a holding of the primary geometry lies in the collection's properties).

## A point as two numbers, with its envelope

For a collection whose points are two numeric properties, and whose store keeps the envelope of
each row in four more, to answer a `bbox` by four comparisons.

```java
FeatureProperty east = FeatureProperties.create(stops, "east", decimal);
FeatureProperty north = FeatureProperties.create(stops, "north", decimal);
FeatureProperty minEast = FeatureProperties.create(stops, "min_east", decimal);
FeatureProperty minNorth = FeatureProperties.create(stops, "min_north", decimal);
FeatureProperty maxEast = FeatureProperties.create(stops, "max_east", decimal);
FeatureProperty maxNorth = FeatureProperties.create(stops, "max_north", decimal);
GeometryFeature stopPoint = SpatialElements.point(geodata, "stopPoint", crs84, east, north);
GeometryFeature stopBox = SpatialElements.envelope(geodata, "stopBox", stopPoint, minEast, minNorth,
        maxEast, maxNorth);
FeatureCollections.key(stops, stopId);
FeatureCollections.geometry(stops, stopPoint);
for (FeatureProperty edge : List.of(minEast, minNorth, maxEast, maxNorth)) {
    FeatureProperties.filterOnly(edge);
}
FeatureCollections.holding(stopPoint, stops);               // the PartsHolding of east and north
FeatureCollections.envelope(stops);                         // stopBox
```

`east` and `north` keep their numeric type: they are numbers a client may filter on, and together
the point the server writes as the geometry of a stop. More examples: a collection over a relational
table with `lon` and `lat`, the same point held in the table and in the collection,
`FeatureCollectionsAcrossCwmTest.aCollectionOverATableWithLongitudeAndLatitude`; the lakes of a state
with the bounding box of each outline, `readingTheGeometryOfACollection`. The envelope is a geometry derived from
`stopPoint`; the edges are only filtered on, never returned. For a point the envelope's edges equal
the point; a store that answers every `bbox` from four columns keeps them all the same.

## One point, a property and two numbers

For a collection that holds its geometry twice: as a geometry property and as two numbers.

The trees of the tree register carry their position as a geometry and as `longitude` and
`latitude`, which a client without a geometry library reads:

```java
GeometryFeature treePoint = SpatialElements.feature(geodata, "treePoint", GeometryKind.POINT, crs84);
FeatureProperty position = FeatureProperties.geometry(trees, "position", types, treePoint);
SpatialElements.pointParts(treePoint, longitude, latitude);
FeatureCollections.geometry(trees, treePoint);
FeatureCollections.holding(treePoint, trees);               // the WholeHolding of position: whole first
FeatureCollections.holdings(treePoint, trees);              // position, then longitude and latitude
```

One geometry feature, two holdings; the server reads the whole one where there is one.
`holdings` gives every holding within the collection, the whole ones first. More examples: bike racks, `FeatureCollectionsAcrossCwmTest.aCollectionWhoseGeometryIsHeldTwice`.

## A point as one text

For a collection whose point is one text property such as `'50.9271,11.5892'`, latitude first.

```java
FeatureCollection stations = FeatureCollections.create(features, "stations");
FeatureProperty coordinates = FeatureProperties.create(stations, "coordinates", string);
GeometryFeature stationPoint = SpatialElements.feature(geodata, "stationPoint", GeometryKind.POINT, crs84);
SpatialElements.list(stationPoint, coordinates, ",", Ordinate.NORTH, Ordinate.EAST);
FeatureCollections.geometry(stations, stationPoint);
```

`coordinates` stays a text property; the list holding says which number is which.

## The time of a feature

For the property `datetime` is evaluated on: an instant, or the start and the end of an interval
(the roles of OGC API Features Part 5).

```java
FeatureCollections.interval(districts, validFrom, validTo);
FeatureCollections.instant(schools, opened);
TypeFamily.of(opened.getType()).temporal();                 // true: a date
```

`interval` and `instant` set the one and clear the other; a start or an end alone is half-bounded.
Rules: FC5 (time properties are properties of the collection), FC10 (a time zone), FC18.

## What a property says of its values

For what a client may do with a property, and what its values may be.

```java
kind.getAllowedValues().addAll(List.of("primary", "secondary", "vocational"));
pupils.setMinimum("0");
schoolLabel.setSortable(true);
schoolId.setQueryable(false);
FeatureCollections.describe(schoolDistrict, "The district the school lies in.");
FeatureProperties.family(pupils);                           // INTEGER
TypeFamily.isEnumeration(pupils.getType());                 // false
```

What a property does not carry, because it stands elsewhere: its type (the attribute's), whether
it may be null (the attribute's multiplicity), its unit (a `units::Quantity` that names it), its
description (`businessinformation`), what a geometry is (the `spatial::GeometryFeature` whose
holding names it). Rules: FC8, FC11, FC12.

## The queryables

For the answer of `/collections/{id}/queryables` (OGC API Features Part 3), a JSON Schema.

```java
Map<String, Object> document = Queryables.document(schools,
        "https://example.org/collections/schools/queryables");
Queryables.property(location);                              // {title=location, format=geometry-point}
Queryables.format(schoolPoint);                             // geometry-point
Queryables.simple(location);                                // false: a geometry is no parameter of /items
Queryables.simple(pupils);                                  // true
TypeFamily.of(pupils.getType());                            // INTEGER
TypeFamily.DATE.jsonType();                                 // string
TypeFamily.DATE.format();                                   // date
TypeFamily.INTEGER.numeric();                               // true
```

The document is a structure (maps, lists, texts, numbers, truth values, in order); the server
writes the text. `title` and `description` come from the collection, `properties` are the queryable
properties in order, `additionalProperties` is `openProperties`. A property gives `title`,
`description`, `type` and `format` by its `TypeFamily`, `enum` (an enumeration's literals, else
`allowedValues`), `minimum`, `maximum`, `maxLength` (an SQL type's length) and `pattern`; a
property held whole has no `type` and the `format` `geometry-<kind>` of its geometry feature. The
numbers of a geometry held in parts are numbers; a primary geometry held only as numbers gives no
geometry property. The schools give:

```json
{ "$schema": "https://json-schema.org/draft/2020-12/schema", "type": "object", "title": "Schools",
  "properties": {
    "label": { "title": "label", "type": "string" },
    "kind": { "title": "kind", "type": "string", "enum": ["primary", "secondary", "vocational"] },
    "pupils": { "title": "pupils", "type": "integer", "minimum": 0 },
    "opened": { "title": "opened", "type": "string", "format": "date" },
    "district_id": { "title": "district_id", "description": "The district the school lies in.", "type": "string" },
    "location": { "title": "location", "format": "geometry-point" } },
  "additionalProperties": false }
```

| Type family | Names (any case, without length) | JSON Schema |
| --- | --- | --- |
| `STRING` | string, varchar, char, text, clob, nvarchar, uuid, an enumeration | `string` |
| `INTEGER` | integer, int, bigint, smallint, tinyint, long, short | `integer` |
| `NUMBER` | float, double, real, decimal, numeric, number, money | `number` |
| `BOOLEAN` | boolean, bool, bit | `boolean` |
| `DATE` | date | `string`, `date` |
| `TIME` | time | `string`, `time` |
| `TIMESTAMP` | timestamp, datetime (read in `timeZone`) | `string`, `date-time` |
| `TIMESTAMP_WITH_ZONE` | timestamptz, timestamp with time zone | `string`, `date-time` |
| `GEOMETRY` | the data type `Geometry` | no type; `geometry-point`, `geometry-multipolygon`, `geometry-any`, ... |

A JDBC type code (`typeNumber` of an SQL type) decides before the name.

## The reference systems of a collection

For the list `crs` of OGC API Features Part 2: the default, the storage system, the offered ones.

```java
districts.getReferenceSystems().add(etrs89);
FeatureCollections.crs(districts);                          // CRS84, then EPSG 25832 and 4258 as URIs
FeatureCollections.storageCrs(districts);                   // http://www.opengis.net/def/crs/EPSG/0/25832
FeatureCollections.storageSystem(districts);                // utm32, the system of the primary geometry
FeatureCollections.storageCrsCoordinateEpoch(districts);    // empty: a static system
FeatureCollections.hasHeight(districts);                    // false: the default is CRS84, not CRS84h
```

The default is `FeatureCollections.CRS84`, or `CRS84H` for a geometry with a height; the storage
system is the one of the primary geometry, its epoch the one its feature names in a dynamic system.
Rules: FC13, FC14, FC15.

## Which form do I use

| The properties of the collection | Model |
| --- | --- |
| one property holds the geometry | `FeatureProperties.geometry(collection, name, types, feature)`: [A geometry in a property](#a-geometry-in-a-property) |
| two numeric properties hold a point | numeric properties and `SpatialElements.point(owner, name, system, east, north)`: [A point as two numbers, with its envelope](#a-point-as-two-numbers-with-its-envelope) |
| four numeric properties hold the envelope of the geometry | `SpatialElements.envelope(owner, name, primaryGeometry, ...)`: [A point as two numbers, with its envelope](#a-point-as-two-numbers-with-its-envelope) |
| a geometry property and two numbers hold one point | one feature, `FeatureProperties.geometry` and `SpatialElements.pointParts`: [One point, a property and two numbers](#one-point-a-property-and-two-numbers) |
| one text property holds a point | a text property and `SpatialElements.list`: [A point as one text](#a-point-as-one-text) |
| no property holds a geometry | no primary geometry, no reference systems (FC15) |

## Reference

### Relations

- Builds on CWM `core`, `businessinformation` (the description of a collection or a property: a
  `Description` of type `documentation`) and `spatial` (what a geometry is and which properties
  hold it, the reference systems). The validator reports in the form of `validation`.
- Knows no origin: which table, statement or level of a cube gives the rows of a collection is
  said by a mapping model that refers to it.
- Holds no extent, no paging, no encodings and no CQL2: those are the server's.

### FeatureCollection (`core::Class`)

| Feature | Type | Mult. | Meaning |
| --- | --- | --- | --- |
| `displayName` | `core::String` | 0..1 | the title; unset, the name |
| `featureKey` | `FeatureProperty` | 1 | the property whose value is the feature id (`/items/{featureId}`); its uniqueness is a key of the origin |
| `geometry` | `spatial::GeometryFeature` | 0..1 | the primary geometry: the one `bbox` is evaluated on, held in properties of the collection (FC19); unset, a collection without geometry |
| `instant` | `FeatureProperty` | 0..1 | the instant of a feature (primary-instant of OGC API Features Part 5) |
| `intervalStart`, `intervalEnd` | `FeatureProperty` | 0..1 | the start and the end of the interval of a feature; a start alone is open to the future, an end alone open to the past; a null value in a row is an open side |
| `timeZone` | `core::String` | 0..1 | the zone (IANA name or offset) a timestamp without zone is read in; unset, UTC |
| `endInclusive` | `core::Boolean` | 0..1, default true | whether an end equal to the start of a requested interval meets it |
| `referenceSystems` | `spatial::ReferenceSystem` | 0..* ordered | the systems offered besides the default and the storage system |
| `openProperties` | `core::Boolean` | 0..1, default false | false: only the named properties may be filtered on (`additionalProperties: false`) |

A collection names an instant or the ends of an interval, not both (`InstantOrInterval`).

### FeatureProperty (`core::Attribute`)

| Feature | Type | Mult. | Meaning |
| --- | --- | --- | --- |
| `displayName` | `core::String` | 0..1 | the title in the queryables; unset, the name |
| `queryable` | `core::Boolean` | 0..1, default true | in the queryables, may be used in a filter |
| `returnable` | `core::Boolean` | 0..1, default true | in a feature: the returnables of Part 5, which Part 6 selects from |
| `sortable` | `core::Boolean` | 0..1, default false | may be named in a sort: the sortables of Part 5, which Part 8 sorts by |
| `allowedValues` | `core::String` | 0..* ordered | the closed list of values, where the type does not list them |
| `minimum`, `maximum` | `core::Float` | 0..1 | numeric bounds |
| `pattern` | `core::String` | 0..1 | a regular expression for text |

A property that holds a whole geometry is of the data type `Geometry` (`spatial::GeometryType`),
which `SpatialElements.geometryType(namespace)` puts into the model once, and is a whole holding of
a geometry feature. A property that holds a number of a geometry keeps its numeric type.

### Rules

`FeatureModelValidator` gives its findings in the one record of the models (`Finding` of
`validation`, module `feature`); errors make a collection unusable for a server, warnings do not. A
reference system and a geometry are checked by the spatial model's validator.

```java
FeatureModelValidator.check(model);                         // every finding, errors and warnings
FeatureModelValidator.validate(model);                      // the errors as lines, "FC19 ..."
```

`OnlyFeatureProperties`, `KeyIsOwnProperty`, `InstantOrInterval` and `OwnedByCollection` stand in
the Ecore as OCL invariants as well.

| Rule | Severity | Says |
| --- | --- | --- |
| FC1 | error | a collection stands in a package and owns only feature properties, each named uniquely and typed |
| FC2 | warning | names are plain ids: letters, digits, `_`, `.`, `-` |
| FC3 | error | no property is named like a query parameter (`limit`, `bbox`, `datetime`, `crs`, `bbox-crs`, `filter`, `filter-lang`, `filter-crs`, `profile`, `properties`, `exclude-properties`, `zoom-level`, `sortby`, `q`, and `f`, `offset`) |
| FC4 | error | the feature key is a property of the collection, never null (1..1), a string or an integer (Part 5) |
| FC5 | error | the time properties are properties of the collection |
| FC7 | error | a property held whole by a geometry feature is of the data type Geometry, and one of that type is held whole by one |
| FC8 | error | a geometry is not the key nor a time, carries no allowed values, bounds or pattern, is not sortable |
| FC10 | warning | a time zone is a valid IANA name or offset, and only where a time is a timestamp without zone |
| FC11 | error | minimum not above maximum; bounds on numbers, a valid pattern on text, allowed values on text and numbers and fitting the type |
| FC12 | warning | allowed values beside an enumeration type: a copy that can drift |
| FC13 | error | a reference system of three axes is offered only where the primary geometry holds a height |
| FC14 | warning | the default (CRS84, CRS84h) or the storage system offered again |
| FC15 | error | a collection that holds a geometry in its properties names its primary geometry; one without offers no systems |
| FC16 | warning | a collection has a queryable property besides its key |
| FC17 | warning | a reference that does not resolve |
| FC18 | error | the time of a feature is an instant or an interval, and each time property is of a date or a timestamp type |
| FC19 | error | at least one holding of the primary geometry lies entirely in properties of the collection: a property of the type Geometry, or the numbers of a point |

A code keeps its meaning, and a check that vanished retires its code. There is no FC6: the
collection names its primary geometry, a geometry feature, itself, where it named a property that
exactly one geometry feature had to name. There is no FC9. FC5 checked the primary geometry as well
while it was a property; where it is held is FC19.

### The Ecore

`model/feature.ecore` is written by `FeatureEcoreBuilder` (test sources, package `build`).
`FeatureEcoreUpToDateTest` fails while the checked-in file differs from what the builder writes;
after a change to the builder run it once with `-Dregenerate.ecore=true`, then build the module
with `mvn clean install` (the code generator merges into files that exist).
