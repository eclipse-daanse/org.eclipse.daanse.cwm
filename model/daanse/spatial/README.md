# org.eclipse.daanse.cwm.model.daanse.spatial

Geodata for CWM attributes: what a geometry column holds, and the rules by which keys are derived
from geometry. PostGIS and DuckDB `spatial` can derive from geometries what a foreign key says
elsewhere: a point lies in a district, a district in a state. Geometry does not always give a tree:
areas overlap, points lie on borders or nowhere, a commune has an exclave, a river runs through
several countries.

The principle: **a spatial relation is resolved once into an ordinary relational one; whoever reads
the result, an OLAP cube for instance, reads only keys.** The model does not describe a join an
engine performs per statement (it could not be grouped or cached, and would run only on databases
with spatial functions). It describes how the assignment of child rows to parent rows arises from
geometry; a resolver evaluates it in the database and leaves

- a key pair (every child at most one parent): a foreign key, or
- a bridge with a share (a child in several parents, a part each): a many-to-many,

as a table or a view. From there on it is plain schema: a model that reads it sees a key or a bridge
table like any other and never refers to this model.

This module is the model only: it holds no resolver. The rules say what a resolver has to leave, so
that any implementation gives the same keys and shares.

## Relations

- Builds on CWM `core` only (the columns it names are attributes; keys are read from the
  `keysindexes` unique keys a table owns, without a dependency); the validator reports in the form
  of `validation`; the standard set builds on `emf/standard`.
- Knows no OLAP class; the feature collections (`resource.spatial.feature`) build on it. No
  dependency on `units` (distances and bounds are metres whatever the reference system, a geographic
  system measured on the spheroid; areas and lengths are measured in metres on the spheroid in a
  geographic system, else in the plane of the working reference system; accuracy in metres).
- Holds no coordinates: geometries stay in database columns.
- The coverage of a catalog is `dcat::Location`, not this model.

## Classes

Every class but `GeometryType` extends `core::ModelElement` and stands in any namespace (a package
of geodata beside the relational model). References are one-way with an EMOF
`Property.oppositeRoleName` (prefix `spatial`); decimal numbers are CWM `Float` (decimal text),
whole numbers CWM `Integer`.

| Class | Anchors to | Purpose |
| --- | --- | --- |
| `GeometryFeature` | `modelElement : core::ModelElement` 1..* (attributes only, SP1) | what the columns hold: kind, single or multi, height, reference system, coordinate epoch, accuracy, storage, stored axis order |
| `ReferenceSystem` | owned by the model; features and working systems refer to it | a coordinate reference system by authority and code, as an OGC URI names it |
| `GeometryType` | a `core::DataType` with no features | the logical type of a property that holds a geometry, named `Geometry` by convention |
| `SpatialRelation` | two `GeometryFeature`s, two key attributes, an optional result class | the rule that assigns child rows to parent rows |
| `GridLevel` | a `GeometryFeature` of points, its key attribute, a `GridSystem`, an optional result class | the key of the cell of a grid system and resolution |
| `GridSystem` | owned by the model, once per identifier; grid levels refer to it | a system of grid cells, known to a resolver by its name, with its resolutions and whether its cells nest |
| `DistanceBand` | two `GeometryFeature`s, the source's key attribute, an optional result class | the class of the distance to a reference geometry |

### GeometryFeature

| Feature | Type | Mult. | Meaning |
| --- | --- | --- | --- |
| `modelElement` | `core::ModelElement` | 1..* | the columns that hold the geometry; columns of one meaning in two tables share one feature, and a logical property of the type `Geometry` (below) may stand beside its column |
| `kind` | `GeometryKind` | 1 | `POINT`, `LINE`, `POLYGON`, `MIXED` (a collection column; a resolver takes each row by its own type); multi forms belong to their kind |
| `multiplicity` | `GeometryMultiplicity` | 1 | `EITHER` (first literal: what an unset value reads), `SINGLE` (one point, line, polygon per row), `MULTI` (a multi form per row) |
| `hasHeight` | `core::Boolean` | 1, default false | the coordinates hold a height as a third value; needs a system of three axes (SP23) |
| `referenceSystem` | `ReferenceSystem` | 1 | the coordinate reference system of the geometries |
| `coordinateEpoch` | `core::Float` | 0..1 | the decimal year the coordinates hold for, only where the system is dynamic (SP32, SP33, SP24); a fact of the coordinates (OGC Topic 2), so two columns of one dynamic system may hold two epochs |
| `accuracy` | `core::Float` | 0..1 | positional accuracy in metres, for diagnostics |
| `storage` | `GeometryStorage` | 1 | `NATIVE` (the database's geometry type), `WKB`, `WKT`, `GEOJSON` (converted by a resolver first) |
| `axisOrder` | `AxisOrder` | 1 | the order in which the stored coordinates hold the first two axes: `EAST_NORTH` (first literal: x, longitude first, as PostGIS, DuckDB and GeoJSON hold it) or `NORTH_EAST`; two columns of one system may store two orders; `GEOJSON` storage holds east first always (`GeoJsonEastFirst`, SP28) |

### SpatialRelation

| Feature | Type | Mult. | Meaning |
| --- | --- | --- | --- |
| `child`, `parent` | `GeometryFeature` | 1 | the geometry assigned, the geometry assigned to |
| `childKey`, `parentKey` | `core::Attribute` | 1 | a key column of the child's table, of the parent's table; where the table declares no unique key of the column alone, the rows of one key are the parts of one geometry and are united first |
| `predicate` | `SpatialPredicate` | 1 | `WITHIN`, `COVERED_BY` (the only one a point on a border meets), `INTERSECTS`, `CENTROID_WITHIN` (the child's centroid where it lies on the child, else a point on its surface), `NEAREST`, `WITHIN_DISTANCE` |
| `distance` | `core::Float` | 0..1 | the distance of `WITHIN_DISTANCE` in metres, whatever the reference system; in a geographic system it is measured on the spheroid |
| `assignment` | `SpatialAssignment` | 1 | `EXACTLY_ONE`, `LARGEST_SHARE`, `ALL_WEIGHTED` (parts divided by their sum), `ALL_EQUAL` (1/n) |
| `weight` | `SpatialWeight` | 0..1, unsettable | `AREA` or `LENGTH` of the intersection relative to the child |
| `unmatched` | `UnmatchedPolicy` | 1 | `KEEP_NULL` (the child stays with a null parent key), `ERROR`, `DROP` |
| `minimumShare` | `core::Float` | 0..1, default 0 | the smallest share of the child by weight that counts as a match, from 0 below 1 (0.01 is 1 %); a share, not a distance |
| `tieBreak` | `TieBreak` | 0..1 | `ERROR` (as none) or `LOWEST_PARENT_KEY` on an exact tie of `EXACTLY_ONE` |
| `explicitKey` | `core::Attribute` | 0..1 | a column of the child table that already holds a parent key; it wins over the geometry where not null (an exclave belongs where it is administered) |
| `workingSystem` | `ReferenceSystem` | 0..1 | the system predicate and measure are evaluated in; where it (else the features' own) is geographic, areas and lengths are measured in metres on the spheroid, and a distance is metres in every system |
| `result` | `core::Class` | 0..1 | the table or view a resolver writes, declared in advance or after the run |
| `resultForm` | `ResultForm` | 1 | `TABLE` (written once, any database reads it) or `VIEW` (needs the spatial functions) |

### ReferenceSystem

A system is named as an OGC URI names it, `http://www.opengis.net/def/crs/{authority}/{version}/{code}`:
EPSG 25832 is `http://www.opengis.net/def/crs/EPSG/0/25832`, OGC CRS84 (WGS 84, longitude first)
`http://www.opengis.net/def/crs/OGC/1.3/CRS84`. Two elements with the same authority, version and
code are one system (SP12).

| Feature | Type | Mult. | Meaning |
| --- | --- | --- | --- |
| `authority` | `ReferenceSystemAuthority` | 1 | `EPSG`, `OGC` |
| `code` | `core::String` | 1 | the code as text: `25832`, `4326`, `CRS84`, `CRS84h` (SP34); an EPSG code is a positive whole number (SP35) |
| `version` | `core::String` | 0..1, default `0` | the version of the definition; none is `0`, as the URI writes it |
| `kind` | `ReferenceSystemKind` | 1 | `GEOGRAPHIC` (degrees), `PROJECTED` (easting and northing on a map projection in the unit of its axes, metres for most and feet for some), `GEOCENTRIC` (metres from the earth's centre) |
| `axes` | `core::Integer` | 1, default 2 | 2 or 3 (SP36) |
| `axisOrder` | `AxisOrder` | 1, unsettable, no default | the order of the first two axes as the register defines them: `EAST_NORTH` (CRS84, EPSG 25832) or `NORTH_EAST` (EPSG 4326 and 3035); the order of a request or an answer in this system. A default would be wrong for half the systems, so an unset order is an error (SP37). A column's stored order is said by its `GeometryFeature` |
| `dynamic` | `core::Boolean` | 1, default false | the system moves with a plate; a coordinate holds for one epoch, which the geometry feature names |

`ReferenceSystems.uri` gives the URI, `ReferenceSystems.epsg` the number the spatial functions of a
database take (an EPSG system only), `ReferenceSystems.same` whether two elements are one system,
whatever order each column stores its coordinates in. `SpatialElements.referenceSystem` and
`SpatialElements.epsg` ask for the axis order; `SpatialElements.crs84` makes OGC CRS84 (version
1.3), `SpatialElements.crs84h` OGC CRS84h (version 0, three axes), both east first.

### GridLevel and DistanceBand

| Class | Feature | Type | Mult. | Meaning |
| --- | --- | --- | --- | --- |
| `GridLevel` | `source` | `GeometryFeature` | 1 | the points put into cells |
| | `sourceKey` | `core::Attribute` | 1 | a key column of the points' table; the first column of the result; a point has one row, a key held in several rows is refused by a resolver |
| | `system` | `GridSystem` | 1 | the system of the cells, an instance of the model (below) |
| | `resolution` | `core::Integer` | 1, unsettable | resolution, precision or zoom, within the system's `minResolution` to `maxResolution` (SP38, `ResolutionInRange`); unset is none (0 is a resolution of h3) |
| | `parent` | `GridLevel` | 0..1 | the coarser level above: same system (SP41, `ParentOfTheSameSystem`), same points and source key; in a nested grid its cell is the head of this one's |
| | `result` | `core::Class` | 0..1 | the table or view a resolver writes: source key, then the cell key of each level from the top of the chain down to this one |
| | `resultForm` | `ResultForm` | 1 | `TABLE` or `VIEW` |
| | `unmatched` | `UnmatchedPolicy` | 1 | a point without a cell (no geometry): `KEEP_NULL` (a row with null cells), `ERROR`, `DROP` |
| `DistanceBand` | `source`, `reference` | `GeometryFeature` | 1 | the geometry measured, the one measured to (its nearest row) |
| | `sourceKey` | `core::Attribute` | 1 | a key column of the source's table; the first column of the result; the rows of one key are the parts of one source, the nearest counts |
| | `bound` | `core::Float` | 1..* ordered | upper bounds of the classes in metres, whatever the reference system (in a geographic system on the spheroid); a class holds the distances up to and including its bound (a distance on a bound belongs to the class below it), one class above the last |
| | `workingSystem` | `ReferenceSystem` | 0..1 | as in a relation |
| | `result` | `core::Class` | 0..1 | the table or view a resolver writes: source key, class (1 up to and including the first bound; none without a distance) |
| | `resultForm` | `ResultForm` | 1 | `TABLE` or `VIEW` |
| | `unmatched` | `UnmatchedPolicy` | 1 | a source without a class (no geometry, or the reference has no row): `KEEP_NULL` (a row with a null class), `ERROR`, `DROP` |

### GridSystem

A grid system is an element of the model, as a reference system is: owned by a namespace of the
model, made once per identifier (SP40) and referred to by the grid levels. Its `name` is the
identifier a resolver knows it by.

| Feature | Type | Mult. | Meaning |
| --- | --- | --- | --- |
| `name` | `core::String` | 1 (SP40) | the identifier: `geohash`, `h3`, `quadkey`, or a URI for another system |
| `minResolution` | `core::Integer` | 1 | the coarsest resolution: 1 for geohash and quadkey, 0 for h3 |
| `maxResolution` | `core::Integer` | 1 | the finest resolution: 12 for geohash, 15 for h3, 23 for quadkey; the minimum is at most the maximum, both from zero up (SP39, `ResolutionsInOrder`) |
| `nested` | `core::Boolean` | 1, default false | the coarser cell holds its finer cells strictly, so that the parent's key is the head of the cell's (geohash, quadkey); false where the parent is a function of the cell (h3) |

`SpatialElements.geohash`, `h3` and `quadkey` make the three systems with their resolutions, or find
the one the model already has; `SpatialElements.gridSystem(owner, identifier, min, max, nested)`
does the same for any other identifier and refuses one the model holds with other resolutions;
`SpatialElements.gridSystem(element, identifier)` finds a system by its identifier. Naming a system
is not resolving it: which systems a resolver computes is for each resolver to say.

## Standard set

The reference systems, grid systems and the geometry type everybody uses are a standard set, the file
`spatial-standards.xmi` in the jar beside `SpatialStandards` under the URI
`https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.spatial/spatial-standards.xmi`
(`SpatialStandards.SET_URI`): the package `Spatial` with 13 reference systems, the grid systems geohash (1 to 12,
nested), h3 (0 to 15) and quadkey (1 to 23, nested) and the type `Geometry`. Each reference system has the kind, the
axes and the axis order its register (EPSG, OGC) defines; all are static, as their registers' ensembles are used.

| System | Kind | Axes, order |
| --- | --- | --- |
| OGC CRS84 (WGS 84, version 1.3) | geographic | 2, east first |
| OGC CRS84h (WGS 84 with height) | geographic | 3, east first |
| EPSG 4326 WGS 84 | geographic | 2, north first |
| EPSG 4979 WGS 84 with height | geographic | 3, north first |
| EPSG 3857 Web Mercator | projected | 2, east first |
| EPSG 4258 ETRS89 | geographic | 2, north first |
| EPSG 3035 ETRS89 LAEA Europe | projected | 2, north first |
| EPSG 25832, 25833 ETRS89 / UTM zones 32N, 33N | projected | 2, east first |
| EPSG 31466 to 31469 DHDN / 3-degree Gauss-Kruger zones 2 to 5 | projected | 2, north first |

A model file may refer to a system of the set; a model may as well make its own (`SpatialElements.epsg`). Both stay:
a system of the set and one a model makes itself with the same authority, version and code are one system (SP12).

The id of every element is the UUID stated beside it in `SpatialStandards`; how a set is written, kept equal to the
code, mapped and loaded is said in `emf/standard`.

```java
Package spatial = SpatialStandards.load(resourceSet);              // mapped and read from the jar
ReferenceSystem utm32 = SpatialStandards.epsg(spatial, 25832).orElseThrow();
```

## What a resolver leaves

The result's columns are the child key, the parent key and, for shares, the share, in this order;
of a grid level the source key and the cell key of each level of its chain (`SpatialElements.chain`:
the parents from the top, then the level), of a distance band the source key and the class.
A resolver never alters a source table: `EXACTLY_ONE` too writes a key pair.

A `VIEW` is computed anew whenever it is read and cannot stop: a rule that would have to stop
(`EXACTLY_ONE` without the tie break `LOWEST_PARENT_KEY`, `unmatched` `ERROR` of a relation, a grid
level or a band) writes a `TABLE`, checked when it is written (SP26).

| `assignment` | Result | Unique | Read as |
| --- | --- | --- | --- |
| `EXACTLY_ONE`, `LARGEST_SHARE` | child key, parent key (null for `KEEP_NULL`); `LARGEST_SHARE` keeps no share | child key | a foreign key: a snowflake step between the child's table and the parent's table |
| `ALL_WEIGHTED`, `ALL_EQUAL` | child key, parent key, share; the shares of a child add up to 1 | child key and parent key | a many-to-many bridge; a measure divided along it is the stored one multiplied by the share |

An unmatched child keeps one row with a null parent key (with shares: share 1). How a missing parent
shows is the policy of the model that reads the result, not of this one. A grid level and a band
take the same `unmatched` with the same default: a point without a cell keeps a row with null cells,
a source without a class a row with a null class (`KEEP_NULL`); `ERROR` stops a resolver and names
them; `DROP` leaves them out.

## Rules

`SpatialModelValidator` gives its findings in the one record of the models (`Finding` of
`validation`, module `spatial`); errors stop a resolver, warnings do not. `HeightNeedsThreeAxes`, `MinimumShareInRange`,
`DistanceOnlyForWithinDistance`, `ParentNotSelf`, `ResolutionInRange`, `ParentOfTheSameSystem`
(grid level), `ResolutionsInOrder` (grid system), `AxesTwoOrThree` (reference system),
`EpochOnlyWhenDynamic` and `GeoJsonEastFirst` (geometry feature) stand in the Ecore as OCL
invariants as well; the axis order of a reference system is required and unsettable, so that EMF's
own multiplicity check finds it unset, as SP37 does.

| Rule | Severity | Says |
| --- | --- | --- |
| SP1 | error | a geometry feature names attributes, not a class, a package or an association end |
| SP2 | error | a column is named by at most one geometry feature |
| SP3 | warning | the type of a column fits the storage: `NATIVE` a geometry type, `WKT` and `GEOJSON` text, `WKB` binary (an unknown type is no finding) |
| SP5 | error | `WITHIN`, `COVERED_BY`, `INTERSECTS`, `CENTROID_WITHIN` need a parent of kind `POLYGON` or `MIXED` |
| SP6 | error | `AREA` weighs a polygon, `LENGTH` a line; a point has no weight |
| SP7 | error | `EXACTLY_ONE` carries a weight only with a minimum share above zero (matches below it are dropped first, then at most one may remain) |
| SP8 | error | `ALL_WEIGHTED` and `LARGEST_SHARE` need a weight; `ALL_EQUAL` may have one (for the minimum share only) |
| SP9 | error | the minimum share is from 0 below 1, above 0 only with a weight |
| SP10 | error | a distance goes with `WITHIN_DISTANCE` only, and is above zero |
| SP11 | error | `NEAREST` assigns `EXACTLY_ONE` (a tie break is allowed) |
| SP12 | error | child and parent (a band's source and reference) share a reference system (one URI, whatever order each column stores its coordinates in), or a working one is named |
| SP14 | error | a relation, a grid level or a band names its geometries and keys; child key and explicit key are columns of a table that holds the child geometry, the parent key of one that holds the parent geometry; the source key of a grid level or a band is a column of a table that holds its source geometry |
| SP15 | warning | `DROP` of a relation, a grid level or a band: facts that reach the parent, cell or class through the result vanish from the sums |
| SP16 | error | a declared result has the shape of its rule: the keys' types, a numeric share; a grid's source key and one cell key per level of its chain, text (text or a number for a system that does not nest, such as h3); a band's class a number |
| SP17 | error | following child to parent never leads back: no place is its own ancestor |
| SP18 | error | a grid takes points into cells of a system and at a resolution it names, below a coarser level over the same points and source key |
| SP19 | error | the bounds of a band are decimal numbers above zero, strictly ascending |
| SP20 | warning | a reference that does not resolve |
| SP21 | warning | an explicit key with `NEAREST`: the key, not the geometry, decides the rows that carry it |
| SP22 | error | a grid takes single points: a source of multiplicity `MULTI` has no one cell (`EITHER` is checked per row by a resolver) |
| SP23 | error | a feature with a height has a reference system of three axes |
| SP24 | warning | a geometry feature in a dynamic reference system names the epoch its coordinates hold for |
| SP25 | warning | a child, parent or source key is not a declared unique key of one column (a schema read without declared keys still resolves: the rows of a relation's key are the parts of one geometry, united first; the rows of a band's key the parts of one source, the nearest counts; a grid level's key held in several rows is refused by a resolver, a point has one row) |
| SP26 | error | a `VIEW` result where the rule would have to stop: `EXACTLY_ONE` without the tie break `LOWEST_PARENT_KEY`, or `unmatched` `ERROR` (of a relation, a grid level or a band) |
| SP27 | warning | an area, a length or a distance of a relation or a band measured in a system of kind `GEOCENTRIC`, of which the planar functions read x and y only (in a `GEOGRAPHIC` system they are measured on the spheroid, in a `PROJECTED` one in its plane: no finding) |
| SP28 | error | a geometry stored as `GEOJSON` holds its coordinates east first (RFC 7946) |
| SP29 | warning | a geometry stored as `GEOJSON` holds longitude and latitude of WGS 84: CRS84, CRS84h, EPSG 4326 or 4979 stored east first; another system only by prior arrangement (RFC 7946) |
| SP30 | error | a geometry feature names its reference system |
| SP31 | error | the accuracy of a geometry feature is a decimal number of metres from zero up |
| SP32 | error | a geometry feature names a coordinate epoch only where its reference system is dynamic |
| SP33 | error | a coordinate epoch is a decimal year |
| SP34 | error | a reference system has a code |
| SP35 | error | an EPSG code is a positive whole number |
| SP36 | error | a reference system has 2 or 3 axes |
| SP37 | error | a reference system states the order of its first two axes as its register defines them; there is no default |
| SP38 | error | the resolution of a grid level lies within the resolutions of its grid system |
| SP39 | error | the resolutions of a grid system run from zero up, its minimum at most its maximum |
| SP40 | error | a grid system has an identifier, once in its model |
| SP41 | error | a chain of grid levels stays within one grid system |

There is no SP4 and no SP13: a code keeps its meaning. The checks of SP4 have a code each (SP30 to
SP36), the warning of SP13 is SP27. The range of a resolution is SP38 and the system of the level
above SP41; the validator reads the ranges from the grid system.

An error and a warning never share a code. SP3 reads the SQL type of a column; a logical property
of the type `Geometry` says nothing of storage and is no finding.

`validate` gives the errors as lines; `check` gives every finding.

## Helpers

`SpatialElements` makes the elements, hangs them on their owner and finds them backwards:

```java
ReferenceSystem utm32 = SpatialElements.epsg(geodata, 25832, ReferenceSystemKind.PROJECTED, AxisOrder.EAST_NORTH);
GeometryFeature communes = SpatialElements.feature(geodata, "communes", GeometryKind.POLYGON, utm32,
        GeometryStorage.NATIVE, communeShape);
GeometryFeature counties = SpatialElements.feature(geodata, "counties", GeometryKind.POLYGON, utm32,
        GeometryStorage.NATIVE, countyShape);
SpatialRelation r = SpatialElements.relation(geodata, "communesInCounties", communes, communeId, counties,
        countyId, SpatialPredicate.CENTROID_WITHIN, SpatialAssignment.EXACTLY_ONE, UnmatchedPolicy.KEEP_NULL);
SpatialElements.explicitKey(r, communeCountyKey);           // administration beats position
SpatialElements.features(communeShape);                     // [communes]
ReferenceSystems.uri(utm32);                                // http://www.opengis.net/def/crs/EPSG/0/25832
GridSystem quadkey = SpatialElements.quadkey(geodata);       // made once, found again by "quadkey"
GridLevel tiles = SpatialElements.grid(geodata, "stationTiles", stations, stationId, quadkey, 12, null);
GridLevel cells = SpatialElements.grid(geodata, "stationCells", stations, stationId, quadkey, 15, tiles);
SpatialElements.chain(cells);                               // [stationTiles, stationCells]
SpatialElements.isDeclaredKey(communeId);                   // a primary or unique key of this column alone
SpatialElements.geometryType(features);                     // the data type Geometry of a package
```

`geometryType` puts the data type `Geometry` into a namespace once, as the SQL types stand in a
schema: the type of a logical property (a feature collection's) that holds a geometry. What it
holds is said by the `GeometryFeature` that names the property, which may name its column too.

## Not modelled

- **Validity over time**: the relation holds no time. A territory that changes (a county reform)
  gets one result per state.
- **Shares that change over time**: one result holds one set of shares.
- **The unit of a projected system**: a reference system holds no unit. Distances and bounds are
  metres in every system; a projected system in feet is not told apart.

## The Ecore

`model/spatial.ecore` is written by `SpatialEcoreBuilder` (test sources, package `build`).
`SpatialEcoreUpToDateTest` fails while the checked-in file differs from what the builder writes;
after a change to the builder run it once with `-Dregenerate.ecore=true`, then build the module
with `mvn clean install` (the code generator merges into files that exist).
