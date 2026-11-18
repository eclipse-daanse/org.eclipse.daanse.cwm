# org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature

Collections of features, as OGC API Features names them, as CWM classes. A `FeatureCollection` is
a `core::Class`, owned by a `core::Package`; its features are its `FeatureProperty`s, each a
`core::Attribute` typed by a data type. The names become the ids of the API
(`/collections/{name}`, the property names of a feature and of the queryables). The collection is
described once, independent of where its rows come from: a table or view, an SQL statement, or the
members of a cube. The queryables are the same for every origin; a mapping model, not this one,
binds a collection to its origin.

## Relations

- Builds on CWM `core`, `businessinformation` (the description of a collection or a property: a
  `Description` of type `documentation`) and `spatial` (what a geometry property holds, the
  reference systems). `spatial` stays orthogonal; this module is its first user. The validator
  reports in the form of `validation`.
- Knows no origin: which table, statement or level of a cube gives the rows of a collection is
  said by a mapping model that refers to it.
- Holds no extent, no paging, no encodings and no CQL2: those are the server's.

## Classes

### FeatureCollection (`core::Class`)

| Feature | Type | Mult. | Meaning |
| --- | --- | --- | --- |
| `displayName` | `core::String` | 0..1 | the title; unset, the name |
| `featureKey` | `FeatureProperty` | 1 | the property whose value is the feature id (`/items/{featureId}`); its uniqueness is a key of the origin |
| `geometry` | `FeatureProperty` | 0..1 | the primary geometry: the one `bbox` is evaluated on; unset, a collection without geometry |
| `instant` | `FeatureProperty` | 0..1 | the instant of a feature (primary-instant of OGC API Features Part 5), the property `datetime` is evaluated on |
| `intervalStart`, `intervalEnd` | `FeatureProperty` | 0..1 | the start and the end of the interval of a feature (primary-interval-start, primary-interval-end); a start alone is open to the future, an end alone open to the past; a null value in a row is an open side |
| `timeZone` | `core::String` | 0..1 | the zone (IANA name or offset) a timestamp without zone is read in; unset, UTC |
| `endInclusive` | `core::Boolean` | 0..1, default true | whether an end equal to the start of a requested interval meets it |
| `referenceSystems` | `spatial::ReferenceSystem` | 0..* ordered | the systems offered besides the default and the storage system |
| `openProperties` | `core::Boolean` | 0..1, default false | false: only the named properties may be filtered on (`additionalProperties: false`) |

A collection names an instant or the ends of an interval, not both (`InstantOrInterval`);
`FeatureCollections.instant(collection, property)` and `FeatureCollections.interval(collection,
start, end)` set the one and clear the other.

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

What a property does not carry, because it stands elsewhere: its type (the attribute's), whether
it may be null (the attribute's multiplicity), its unit (a `units::Quantity` that names it), its
description (`businessinformation`), what a geometry holds (the `spatial::GeometryFeature` that
names it). A geometry property is of the data type `Geometry` that
`SpatialElements.geometryType(namespace)` puts into the model once; one `GeometryFeature` may name
the property and its column together.

## The queryables

`Queryables.document(collection, id)` gives the queryables as a structure (maps, lists, texts,
numbers, truth values, in order), a JSON Schema of draft 2020-12; the server writes the text.
`title` and `description` come from the collection, `properties` are the queryable properties in
order, `additionalProperties` is `openProperties`. A property gives `title`, `description`, `type`
and `format` by its `TypeFamily`, `enum` (an enumeration's literals, else `allowedValues`),
`minimum`, `maximum`, `maxLength` (an SQL type's length) and `pattern`; a geometry has no `type`
and the `format` `geometry-<kind>` of its geometry feature.

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

A JDBC type code (`typeNumber` of an SQL type) decides before the name. `Queryables.simple` says
which queryables are query parameters of `/items` too.

`FeatureCollections.crs(collection)` gives the list `crs` of Part 2: the default (CRS84, or CRS84h
for a geometry with a height), the storage system (`storageCrs`, the system of the primary
geometry's feature), then the offered systems; `storageCrsCoordinateEpoch` the epoch the primary
geometry's coordinates hold for in a dynamic storage system, as its geometry feature names it.

## Rules

`FeatureModelValidator` gives its findings in the one record of the models (`Finding` of
`validation`, module `feature`); errors make a collection unusable for a server, warnings do not. A reference system
itself is checked by the spatial model's validator. `OnlyFeatureProperties`, `KeyIsOwnProperty`,
`InstantOrInterval` (the time of a feature is an instant or an interval, not both) and
`OwnedByCollection` stand in the Ecore as OCL invariants as well.

| Rule | Severity | Says |
| --- | --- | --- |
| FC1 | error | a collection stands in a package and owns only feature properties, each named uniquely and typed |
| FC2 | warning | names are plain ids: letters, digits, `_`, `.`, `-` |
| FC3 | error | no property is named like a query parameter (`limit`, `bbox`, `datetime`, `crs`, `bbox-crs`, `filter`, `filter-lang`, `filter-crs`, `profile`, `properties`, `exclude-properties`, `zoom-level`, `sortby`, `q`, and `f`, `offset`) |
| FC4 | error | the feature key is a property of the collection, never null (1..1), a string or an integer (Part 5) |
| FC5 | error | the primary geometry and the time properties are properties of the collection |
| FC6 | error | the primary geometry is named by exactly one geometry feature |
| FC7 | error | a property named by a geometry feature is of the data type Geometry, and one of that type is named by one |
| FC8 | error | a geometry is not the key nor a time, carries no allowed values, bounds or pattern, is not sortable |
| FC10 | warning | a time zone is a valid IANA name or offset, and only where a time is a timestamp without zone |
| FC11 | error | minimum not above maximum; bounds on numbers, a valid pattern on text, allowed values on text and numbers and fitting the type |
| FC12 | warning | allowed values beside an enumeration type: a copy that can drift |
| FC13 | error | a reference system of three axes is offered only where the primary geometry holds a height |
| FC14 | warning | the default (CRS84, CRS84h) or the storage system offered again |
| FC15 | error | a collection with a geometry property names its primary geometry; one without offers no systems |
| FC16 | warning | a collection has a queryable property besides its key |
| FC17 | warning | a reference that does not resolve |
| FC18 | error | the time of a feature is an instant or an interval, and each time property is of a date or a timestamp type |

There is no FC9; the code is not used.

## Example

`src/test/.../examples/City`: `districts` (polygons, valid from a day until a day, offered also in
EPSG 4258) and `schools` (points, opened on a day, a kind of three values), stored in EPSG 25832.
Its schools give these queryables (as a structure; shown here as JSON):

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

## The Ecore

`model/feature.ecore` is written by `FeatureEcoreBuilder` (test sources, package `build`).
`FeatureEcoreUpToDateTest` fails while the checked-in file differs from what the builder writes;
after a change to the builder run it once with `-Dregenerate.ecore=true`, then build the module
with `mvn clean install` (the code generator merges into files that exist).
