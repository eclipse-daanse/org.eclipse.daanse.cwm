# org.eclipse.daanse.cwm.model.daanse.spatial

Geodata for CWM attributes: what a geometry is, where and how its values are held, and the rules
by which keys are derived from geometry. PostGIS and DuckDB `spatial` can derive from geometries
what a foreign key says elsewhere: a point lies in a district, a district in a state. Geometry does
not always give a tree: areas overlap, points lie on borders or nowhere, a commune has an exclave, a
river runs through several countries.

The principle: **a spatial relation is resolved once into an ordinary relational one; whoever reads
the result, an OLAP cube for instance, reads only keys.** The model does not describe a join an
engine performs per statement. It describes how the assignment of child rows to parent rows arises
from geometry; a resolver evaluates it in the database and leaves a key pair (a foreign key) or a
bridge with a share (a many-to-many), as a table or a view. This module is the model only: it holds
no resolver and no coordinates.

## The concepts

- **A geometry is not where it is held.** A `GeometryFeature` says what a geometry is: its kind
  (point, line, polygon), single or multi, with a height or without, its reference system, the epoch
  of its coordinates, its accuracy. It names no column. Where and how the values lie says each of
  its **holdings**, which it owns.
- **Three ways to hold a geometry.** A `WholeHolding`: one attribute holds the whole geometry, in
  the database's geometry type or as WKB, WKT or GeoJSON. A `PartsHolding`: one number per
  attribute, each with its role (`EAST` in `lon`, `NORTH` in `lat`). A `ListHolding`: several
  numbers in one attribute, an array or a text with a separator. Numbers hold only a point or an
  envelope; anything else is held whole. One geometry may have several holdings, which all hold the
  same value.
- **A geometry of its own, or one derived from another.** Without a derivation a geometry stands
  for itself, as the shape of a commune does. With one it is the `ENVELOPE`, a corner of the
  envelope, the `CENTROID` or a `POINT_ON_SURFACE` of the geometry it names as `of`, stored beside
  it so that a reader need not compute it. A derived geometry is a geometry feature like any other
  and may be held in any form.
- **The reference system and two axis orders, against the roles of numbers.** A `ReferenceSystem`
  states what its register defines, the order of its axes among it (EPSG 4326: latitude first). A
  whole holding states the order its stored coordinates hold (a PostGIS column of EPSG 4326:
  longitude first). Numbers have no order of axes: their roles (`EAST`, `NORTH`, the edges
  `MIN_EAST` to `MAX_NORTH`) say which number is which axis, in any system: east is the longitude
  in a geographic one, the easting in a projected one.
- **A statement about geometry, against the rules that derive keys from it.** A geometry feature
  states facts. A `SpatialRelation`, a `GridLevel` and a `DistanceBand` are rules a resolver
  evaluates: which parent a child row belongs to, in which cell a point lies, how far the nearest
  reference is. They refer to geometry features and to the key columns of their tables.
- **What belongs elsewhere.** The coverage of a catalog is `dcat::Location`. A collection of
  features, as an OGC API serves it, is `resource.spatial.feature`, which builds on this module.
  Units are not modelled: distances, bounds and accuracies are metres. Addresses and place names are
  `businessinformation::Location` or a gazetteer IRI of `dcat::Location`.

## Overview

| Concept | Class | The question it answers | Example |
| --- | --- | --- | --- |
| a geometry | `GeometryFeature`, `GeometryKind`, `GeometryMultiplicity` | what is it: kind, single or multi, height, system, epoch, accuracy? | [A geometry in a column](#a-geometry-in-a-column) |
| held whole | `WholeHolding`, `GeometryStorage`, `AxisOrder` | which attribute holds it whole, in which form, in which stored order? | [A geometry in a column](#a-geometry-in-a-column), [A geometry as text](#a-geometry-as-text) |
| held in parts | `PartsHolding`, `CoordinatePart`, `Ordinate` | which attribute holds which number? | [A point as two numbers](#a-point-as-two-numbers) |
| held as a list | `ListHolding` | which attribute holds the numbers, in which order, with which separator? | [A point in one attribute](#a-point-in-one-attribute) |
| several holdings | `GeometryFeature.holding` | where else is the same geometry held? | [One geometry, several holdings](#one-geometry-several-holdings) |
| derived | `GeometryDerivation`, `GeometryFeature.of` | is it the envelope, a corner, the centroid of another geometry? | [An envelope as four numbers](#an-envelope-as-four-numbers), [Two corners](#two-corners) |
| reference system | `ReferenceSystem`, `ReferenceSystemAuthority`, `ReferenceSystemKind` | in which system, as its register defines it? | [Reference systems](#reference-systems) |
| logical type | `GeometryType` | of which type is a property that holds a whole geometry? | [A property of the type Geometry](#a-property-of-the-type-geometry) |
| keys from geometry | `SpatialRelation`, `SpatialPredicate`, `SpatialAssignment`, `SpatialWeight`, `UnmatchedPolicy`, `TieBreak`, `ResultForm` | which parent row does a child row belong to, and with which share? | [Keys from geometry](#keys-from-geometry) |
| cells | `GridLevel`, `GridSystem` | in which cell of a grid does a point lie? | [Grid cells](#grid-cells) |
| distance classes | `DistanceBand` | how far is the nearest row of a reference geometry? | [Distance classes](#distance-classes) |

## The running example

A land office keeps its geodata in the schema `geo` of a PostGIS database; the spatial model
stands in the package `geodata` beside it. The sections add the tables they need: `commune`,
`county`, `station`, `mast`, `well`, `stop`, `sensor`, `district`, `state`, `building`, `sheet`,
`tile`, `trail`, `river`. The reference systems are made as in [Reference systems](#reference-systems):
`utm32` (ETRS89 / UTM zone 32N, EPSG 25832), `wgs84` (EPSG 4326), `crs84` and `crs84h`. Every
snippet below is a test of `SpatialExamplesTest` that builds it and finds no rule broken. More
examples in other settings, each a small story that asks of the model what a reader of the data
asks, stand in `src/test/.../examples/SpatialAcrossCwmTest`: relational tables, JSON documents
(`resource.json`), record files (`resource.record`), tile indexes, and the rules fed by numbers.

## A geometry in a column

For a table whose geometry lies in one column of the database's geometry type.

The communes lie in `commune(id, name, shape)`, `shape` of PostGIS type `GEOMETRY` in EPSG 25832:

```java
GeometryFeature communes = SpatialElements.feature(geodata, "communes", GeometryKind.POLYGON, utm32,
        GeometryStorage.NATIVE, communeShape);
communes.setMultiplicity(GeometryMultiplicity.MULTI);       // a commune with an exclave is a multi-polygon
SpatialElements.features(communeShape);                     // [communes]
SpatialElements.holdings(communeShape);                     // the one WholeHolding of communes
SpatialElements.attributes(communes.getHolding().get(0));   // [shape]
```

A reader gets a feature of multi-polygons (unless said, `EITHER`: single or multi) with one
`WholeHolding`: the attribute `shape`, storage `NATIVE`, the coordinates stored east first. A
resolver reads the column as it is. Rules: SP2, SP3, SP30 to SP33, SP42.

## A geometry as text

For a geometry written as well-known text or GeoJSON into a text column.

Hiking trails lie in `trail(id, name, route)`, `route` a `VARCHAR` with a GeoJSON `LineString` in
WGS 84, longitude first:

```java
GeometryFeature trails = SpatialElements.feature(geodata, "trails", GeometryKind.LINE, crs84,
        GeometryStorage.GEOJSON, route);
```

A resolver converts the text first (`ST_GeomFromGeoJSON`); `WKT` and `WKB` are read the same way.
GeoJSON is longitude and latitude of WGS 84, east first: SP28 holds a GeoJSON holding to east
first, SP29 warns of another system. SP3 warns where the type of the column does not fit the
storage (text for `WKT` and `GEOJSON`, binary for `WKB`). More examples: a GeoJSON member of a JSON
object and a WKT field of a CSV file, `SpatialAcrossCwmTest.jsonDocumentsWithAGeoJsonMemberABboxAndACoordinatePair`
and `recordFilesWithWellKnownTextAndALatLonText`.

## A point as two numbers

For a point whose longitude and latitude (or easting and northing) lie in two numeric columns.

The weather stations lie in `station(id, name, lon, lat)`, a row such as
`(2444, 'Jena (Sternwarte)', 11.5830, 50.9251)`, in WGS 84:

```java
GeometryFeature stations = SpatialElements.point(geodata, "stations", crs84, lon, lat);
PartsHolding parts = (PartsHolding) stations.getHolding().get(0);
CoordinatePart first = parts.getPart().get(0);              // the role EAST, the attribute lon
SpatialElements.attribute(parts, Ordinate.EAST);            // lon
```

`point` makes a single point held by a `PartsHolding` with two `CoordinatePart`s: `EAST` in `lon`,
`NORTH` in `lat`. A resolver builds the point (`ST_Point(lon, lat)`). The same in two steps, for a
feature that has another holding already:

```java
GeometryFeature stations = SpatialElements.feature(geodata, "stations", GeometryKind.POINT, crs84);
SpatialElements.pointParts(stations, lon, lat);
```

Rules: SP43 (numbers hold a single point or an envelope), SP44 (each role once), SP45 (a point has
`EAST` and `NORTH`), SP47 (the columns are numbers), SP48 (the parts lie in one table). More
examples: `SpatialAcrossCwmTest.weatherStationsByLongitudeAndLatitude`, with masts and their height.

## A point with a height

For a point with a third number, the height.

Radio masts lie in `mast(id, lon, lat, height)`, the height ellipsoidal in metres; the system is
OGC CRS84h, WGS 84 with three axes:

```java
GeometryFeature masts = SpatialElements.point(geodata, "masts", crs84h, lon, lat, height);
```

The feature has a height (`hasHeight`) and a third part, `HEIGHT`. Rules: SP23 (a height needs a
system of three axes), SP46 (`HEIGHT` exactly where the geometry has a height).

## Easting and northing

For a point in a projected system, the coordinates in metres.

Wells lie in `well(id, easting, northing)`, a row such as `(17, 681234.5, 5644321.0)` in ETRS89 /
UTM zone 32N:

```java
GeometryFeature wells = SpatialElements.point(geodata, "wells", utm32, easting, northing);
```

`EAST` is the easting, `NORTH` the northing; the reference system says which, the roles stay the
same. More examples: `SpatialAcrossCwmTest.addressCoordinatesAsNumbersAndAsAGeometryColumn`.

## A point in one attribute

For a point whose numbers lie together in one attribute: a text with a separator, or an array.

The bus stops hold their position as text, latitude first: `stop(id, name, position)` with
`'50.9271,11.5892'`. The sensors hold theirs as an array, longitude first:
`sensor(id, location)` with `location DOUBLE PRECISION[]` and `{11.5892, 50.9271}`:

```java
GeometryFeature stops = SpatialElements.feature(geodata, "stops", GeometryKind.POINT, crs84);
ListHolding text = SpatialElements.list(stops, position, ",", Ordinate.NORTH, Ordinate.EAST);
GeometryFeature sensors = SpatialElements.feature(geodata, "sensors", GeometryKind.POINT, crs84);
SpatialElements.list(sensors, location, null, Ordinate.EAST, Ordinate.NORTH);
```

A `ListHolding` names the attribute, the roles in the order they are held and, for a text, the
separator; without one the attribute holds several values. Rules: SP44, SP45, and SP47 (a text has
a separator, an array or a multi-valued attribute of numbers has none; a JSON array type is an
array, a JSON primitive type is judged by its value kind). More examples: a pair of a JSON document
and a text field of a CSV file, `SpatialAcrossCwmTest.jsonDocumentsWithAGeoJsonMemberABboxAndACoordinatePair`
and `recordFilesWithWellKnownTextAndALatLonText`.

## One geometry, several holdings

For a geometry held twice: in one table in two forms, or in two tables with one meaning.

The stations of [A point as two numbers](#a-point-as-two-numbers) get a geometry column `position`
beside `lon` and `lat`, a second holding of the same geometry; the districts lie in
`district(id, shape)` and in its archive
`district_archive(id, shape)`:

```java
WholeHolding column = SpatialElements.whole(stations, position, GeometryStorage.NATIVE);
List<GeometryHolding> holdings = stations.getHolding();     // the parts lon and lat, the whole position
GeometryFeature districts = SpatialElements.feature(geodata, "districts", GeometryKind.POLYGON, utm32,
        GeometryStorage.NATIVE, districtShape, archiveShape);
```

All holdings of one feature hold the same geometry. A reader takes any: a resolver the whole one
where the table of its key has one, else it builds the geometry from the numbers; a reader without
spatial functions the numbers. An attribute belongs to one holding (SP2); a geometry held whole may
lie in several tables, a holding in parts lies in one (SP48). More examples: address coordinates as
numbers in one table and as a geometry column in another,
`SpatialAcrossCwmTest.addressCoordinatesAsNumbersAndAsAGeometryColumn`.

## An envelope as four numbers

For the bounding box of a geometry, stored in four columns so that a search by area need not
compute it.

Each commune keeps its box beside its shape: `commune(id, name, shape, min_x, min_y, max_x, max_y)`:

```java
GeometryFeature communeBoxes = SpatialElements.envelope(geodata, "communeBoxes", communes, minX, minY, maxX,
        maxY);
SpatialElements.derivations(communes);                      // [communeBoxes]
```

`envelope` makes a single polygon of the derivation `ENVELOPE` of `communes`, in its reference
system, held by four parts: `MIN_EAST` (west), `MIN_NORTH` (south), `MAX_EAST` (east), `MAX_NORTH`
(north). Rules: SP45 (the four edges, no axis of a point), SP49 to SP52 (a derivation with `of`,
not of itself, in the same system, a single polygon). More examples: the box columns of building
footprints for an index, `SpatialAcrossCwmTest.buildingFootprintsWithTheirBoxCentroidAndLabelPoint`.

## An envelope as a list

For a bounding box in one attribute, as GeoJSON writes `bbox`: `[west, south, east, north]`.

The counties keep theirs as an array: `county(id, name, shape, bbox)` with `bbox` of type
`DOUBLE PRECISION[]`:

```java
GeometryFeature countyBoxes = SpatialElements.derived(geodata, "countyBoxes", GeometryDerivation.ENVELOPE,
        counties);
SpatialElements.list(countyBoxes, bbox, null, Ordinate.MIN_EAST, Ordinate.MIN_NORTH, Ordinate.MAX_EAST,
        Ordinate.MAX_NORTH);
```

`derived` makes the geometry derived from another, of the kind of its derivation, single, in the
other's system, without a holding; the holding is added as to any feature. A text `'w s e n'` is
the same list with the separator `" "`. More examples: the `bbox` of a JSON object,
`SpatialAcrossCwmTest.jsonDocumentsWithAGeoJsonMemberABboxAndACoordinatePair`.

## An envelope as one geometry

For a bounding box held as a polygon.

The states keep their extent as well-known text: `state(id, shape, extent)`:

```java
GeometryFeature stateExtents = SpatialElements.derived(geodata, "stateExtents", GeometryDerivation.ENVELOPE,
        states);
SpatialElements.whole(stateExtents, extent, GeometryStorage.WKT);
```

## An envelope with a height

For a box with a lowest and a highest height: six numbers.

The buildings lie in `building(id, volume, min_x, min_y, min_z, max_x, max_y, max_z)` in CRS84h:

```java
GeometryFeature buildingBoxes = SpatialElements.derived(geodata, "buildingBoxes", GeometryDerivation.ENVELOPE,
        buildings);
buildingBoxes.setHasHeight(true);
PartsHolding box = SpatialElements.parts(buildingBoxes);
SpatialElements.part(box, Ordinate.MIN_EAST, minX);
SpatialElements.part(box, Ordinate.MIN_NORTH, minY);
SpatialElements.part(box, Ordinate.MIN_HEIGHT, minZ);
SpatialElements.part(box, Ordinate.MAX_EAST, maxX);
SpatialElements.part(box, Ordinate.MAX_NORTH, maxY);
SpatialElements.part(box, Ordinate.MAX_HEIGHT, maxZ);
```

`parts` adds an empty holding in parts, `part` one number to it. The order of the parts carries no
meaning. Rule SP46: `MIN_HEIGHT` and `MAX_HEIGHT` exactly where the box has a height.

## Two corners

For a box given by two opposite corners: the upper left and the lower right of a map, or the least
and the greatest corner.

The map sheets keep their corners as points, `sheet(id, area, upper_left, lower_right)`; the tiles
of a cache keep the least and the greatest corner as four numbers,
`tile(id, shape, min_x, min_y, max_x, max_y)`:

```java
GeometryFeature upperLeft = SpatialElements.derived(geodata, "sheetUpperLeft",
        GeometryDerivation.CORNER_NORTH_WEST, sheets);
SpatialElements.whole(upperLeft, upperLeftCorner, GeometryStorage.NATIVE);
GeometryFeature lowerRight = SpatialElements.derived(geodata, "sheetLowerRight",
        GeometryDerivation.CORNER_SOUTH_EAST, sheets);
SpatialElements.whole(lowerRight, lowerRightCorner, GeometryStorage.NATIVE);
GeometryFeature least = SpatialElements.derived(geodata, "tileLeast", GeometryDerivation.CORNER_SOUTH_WEST,
        tiles);
SpatialElements.pointParts(least, minX, minY);
GeometryFeature greatest = SpatialElements.derived(geodata, "tileGreatest",
        GeometryDerivation.CORNER_NORTH_EAST, tiles);
SpatialElements.pointParts(greatest, maxX, maxY);
```

A corner is a single point. Two corners of one geometry in one table are opposite: north-west and
south-east, or south-west and north-east (SP53); a corner held in another table is not compared.
More examples: indexes of orthophoto and elevation tiles, `SpatialAcrossCwmTest.tileIndexesByTwoCorners`.

## A stored centroid

For a point computed once from an area: its centroid, or a point that lies on it for certain.

Each commune keeps its centre and the point its label is placed at:
`commune(id, name, shape, centre, label_point)`:

```java
GeometryFeature centres = SpatialElements.derived(geodata, "communeCentres", GeometryDerivation.CENTROID,
        communes);
SpatialElements.whole(centres, centre, GeometryStorage.NATIVE);
GeometryFeature labels = SpatialElements.derived(geodata, "communeLabels",
        GeometryDerivation.POINT_ON_SURFACE, communes);
SpatialElements.whole(labels, labelPoint, GeometryStorage.NATIVE);
```

The centroid of a crescent lies outside it; a point on the surface never does. Both may be held in
any form, two numbers among them. More examples:
`SpatialAcrossCwmTest.buildingFootprintsWithTheirBoxCentroidAndLabelPoint`.

## Keys from geometry

For the assignment of child rows to parent rows by geometry, resolved once into a key pair or a
bridge with shares.

Each commune lies in one county; `commune.county_id` is the county its administration keeps, which
wins over the geometry (an exclave belongs where it is administered). The result is the table
`commune_county(commune_id, county_id)`:

```java
SpatialRelation r = SpatialElements.relation(geodata, "communesInCounties", communes, communeId, counties,
        countyId, SpatialPredicate.CENTROID_WITHIN, SpatialAssignment.EXACTLY_ONE, UnmatchedPolicy.KEEP_NULL);
SpatialElements.explicitKey(r, communeCountyKey);
r.setTieBreak(TieBreak.LOWEST_PARENT_KEY);                  // an exact tie goes to the lowest county id
SpatialElements.result(r, pairs);
SpatialElements.asChild(communes);                          // [r]
SpatialElements.asParent(counties);                         // [r]
SpatialElements.isDeclaredKey(communeId);                   // true: the primary key of the column alone
```

A commune crossing electoral districts gets a share of each by area, parts below 1 % dropped; the
districts are in WGS 84, the relation is measured in UTM. A station is assigned every commune within
5 km:

```java
SpatialRelation shares = SpatialElements.relation(geodata, "communesInElectoralDistricts", communes,
        communeId, electoralDistricts, electoralId, SpatialPredicate.INTERSECTS, SpatialAssignment.ALL_WEIGHTED,
        UnmatchedPolicy.KEEP_NULL);
SpatialElements.weight(shares, SpatialWeight.AREA, "0.01");
SpatialElements.workingSystem(shares, utm32);
SpatialElements.withinDistance(near, "5000");
```

The stations are points held as numbers: a resolver builds them in the table of their key. Every
predicate and weight reads such a geometry, which is a point or an envelope (SP43), so no rule of a
relation asks more of numbers. More examples: measuring points held only as numbers, assigned to
districts, put into cells and classed by distance, `SpatialAcrossCwmTest.keysFromAGeometryHeldAsNumbers`.
What a resolver leaves is said in
[What a resolver leaves](#what-a-resolver-leaves); the rules are SP5 to SP12, SP14 to SP17, SP20,
SP21, SP25 to SP27.

## Grid cells

For the cell of a grid a point lies in, as a key: a quadkey tile, an H3 hexagon, a geohash.

The stations go into quadkey tiles of zoom 12 and, below them, 15, and into H3 cells of resolution
7; the result `station_cells(station_id, tile_12, tile_15)` holds the source key and a cell per
level, the coarsest first; the H3 cells are left as a view:

```java
GridSystem quadkey = SpatialElements.quadkey(geodata);
GridLevel tiles = SpatialElements.grid(geodata, "stationTiles", stations, stationId, quadkey, 12, null);
GridLevel cells = SpatialElements.grid(geodata, "stationCells", stations, stationId, quadkey, 15, tiles);
GridLevel hexagons = SpatialElements.grid(geodata, "stationHexagons", stations, stationId,
        SpatialElements.h3(geodata), 7, null);
hexagons.setResultForm(ResultForm.VIEW);                    // the H3 cells as a view, computed when read
GridSystem geohash = SpatialElements.geohash(geodata);
GridSystem s2 = SpatialElements.gridSystem(geodata, "https://s2geometry.io/", 0, 30, true);
SpatialElements.result(cells, stationCells);
SpatialElements.chain(cells);                               // [stationTiles, stationCells]
SpatialElements.gridSystem(station, "quadkey");             // the quadkey system of the model
SpatialElements.gridSystems(model);                         // quadkey, h3, geohash, s2
```

A grid system is made once per model and found again by its identifier; `gridSystem` with
resolutions makes one of another identifier and refuses one the model holds otherwise. Rules: SP18,
SP22, SP38 to SP41.

## Distance classes

For the distance to the nearest row of a reference geometry, put into classes.

How far each station lies from the nearest river, in classes up to 500 m, 2 km, 10 km and above;
the result is `station_river_band(station_id, band)`:

```java
DistanceBand toTheRiver = SpatialElements.band(geodata, "toTheRiver", stations, stationId, rivers, "500",
        "2000", "10000");
toTheRiver.setWorkingSystem(utm32);
SpatialElements.result(toTheRiver, classes);
```

Rule SP19: the bounds are decimal numbers above zero, strictly ascending.

## Reference systems

For the coordinate reference system of a geometry, named as an OGC URI names it.

```java
ReferenceSystem utm32 = SpatialElements.epsg(geodata, 25832, ReferenceSystemKind.PROJECTED, AxisOrder.EAST_NORTH);
ReferenceSystem wgs84 = SpatialElements.epsg(geodata, 4326, ReferenceSystemKind.GEOGRAPHIC, AxisOrder.NORTH_EAST);
ReferenceSystem crs84 = SpatialElements.crs84(geodata);
ReferenceSystem crs84h = SpatialElements.crs84h(geodata);
ReferenceSystem utm32Again = SpatialElements.referenceSystem(geodata, "utm32", ReferenceSystemAuthority.EPSG,
        "25832", ReferenceSystemKind.PROJECTED, AxisOrder.EAST_NORTH);
ReferenceSystems.uri(utm32);                                // http://www.opengis.net/def/crs/EPSG/0/25832
ReferenceSystems.epsg(utm32);                               // 25832, the number ST_Transform takes
ReferenceSystems.same(utm32, utm32Again);                   // true: one authority, version and code
ReferenceSystems.name(wgs84);                               // EPSG 4326
```

The axis order of a system copies its register and has no default (SP37): EPSG 4326 defines
latitude first. How a column stores its coordinates is said by its whole holding. A survey stores
its points as WKB of EPSG 4326 latitude first, `survey(id, position)`:

```java
GeometryFeature surveys = SpatialElements.feature(geodata, "surveys", GeometryKind.POINT, wgs84);
SpatialElements.whole(surveys, position, GeometryStorage.WKB).setAxisOrder(AxisOrder.NORTH_EAST);
```

Numbers have no stored order: their roles say it.

## The standard set

The reference systems, grid systems and the geometry type everybody uses are a standard set, the file
`spatial-standards.xmi` in the jar beside `SpatialStandards` under the URI
`https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.spatial/spatial-standards.xmi`
(`SpatialStandards.SET_URI`): the package `Spatial` with 13 reference systems, the grid systems
geohash (1 to 12, nested), h3 (0 to 15) and quadkey (1 to 23, nested) and the type `Geometry`.

```java
ResourceSet resourceSet = new ResourceSetImpl();
Package spatial = SpatialStandards.load(resourceSet);              // mapped and read from the jar
ReferenceSystem utm32 = SpatialStandards.epsg(spatial, 25832).orElseThrow();
ReferenceSystem crs84 = SpatialStandards.referenceSystem(spatial, ReferenceSystemAuthority.OGC, "CRS84")
        .orElseThrow();
GeometryType geometryType = SpatialElements.geometryType(spatial);
ResourceSet reading = new ResourceSetImpl();
SpatialStandards.map(reading);         // a resource set that reads model files finds the set in the jar
```

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

A model file may refer to a system of the set; a model may as well make its own. Both stay: a
system of the set and one a model makes itself with the same authority, version and code are one
system (SP12). The id of every element is the UUID stated beside it in `SpatialStandards`; how a set
is written, kept equal to the code, mapped and loaded is said in `emf/standard`.

## A property of the type Geometry

For a property of a logical model (a class, a feature collection) that holds a whole geometry.

The weather stations of [A point as two numbers](#a-point-as-two-numbers) are a class
`WeatherStation` of a logical model as well, its property `location` of the type `Geometry`:

```java
location.setType(SpatialElements.geometryType(logical));
SpatialElements.whole(stations, location, GeometryStorage.NATIVE);
SpatialElements.isGeometryType(location.getType());         // true
```

`geometryType` puts the data type `Geometry` (a `GeometryType`) into a namespace once, as the SQL
types stand in a schema. The type says nothing of the geometry: the feature whose holding names the
property says what it is. A property of this type is held `NATIVE`; SP3 warns of another storage.

## Which form do I use

| The data looks like | Model |
| --- | --- |
| a geometry column (`GEOMETRY`, `GEOGRAPHY`, `SDO_GEOMETRY`) | `feature(..., GeometryStorage.NATIVE, column)`: [A geometry in a column](#a-geometry-in-a-column) |
| WKT, GeoJSON or WKB in a text or binary column | `feature(..., GeometryStorage.WKT, column)`: [A geometry as text](#a-geometry-as-text) |
| two columns lon and lat, or easting and northing | `point(owner, name, system, east, north)`: [A point as two numbers](#a-point-as-two-numbers), [Easting and northing](#easting-and-northing) |
| three columns with a height | `point(owner, name, system, east, north, height)`, a system of three axes: [A point with a height](#a-point-with-a-height) |
| one column `'50.93,11.59'` or an array `{11.59, 50.93}` | `feature(owner, name, POINT, system)` and `list(...)`: [A point in one attribute](#a-point-in-one-attribute) |
| a bounding box in four columns | `envelope(owner, name, of, minEast, minNorth, maxEast, maxNorth)`: [An envelope as four numbers](#an-envelope-as-four-numbers) |
| a bounding box in one array or text | `derived(..., ENVELOPE, of)` and `list(...)`: [An envelope as a list](#an-envelope-as-a-list) |
| a bounding box as a polygon | `derived(..., ENVELOPE, of)` and `whole(...)`: [An envelope as one geometry](#an-envelope-as-one-geometry) |
| a bounding box with heights in six columns | `derived(..., ENVELOPE, of)`, `parts` and `part`: [An envelope with a height](#an-envelope-with-a-height) |
| two corners, upper left and lower right, or least and greatest | two `derived(..., CORNER_..., of)`: [Two corners](#two-corners) |
| a stored centroid, or a point for a label | `derived(..., CENTROID, of)` or `POINT_ON_SURFACE`: [A stored centroid](#a-stored-centroid) |
| the same point as a geometry column and as lon and lat | one feature, two holdings: [One geometry, several holdings](#one-geometry-several-holdings) |
| the same meaning in two tables | one feature, a whole holding each: [One geometry, several holdings](#one-geometry-several-holdings) |
| a property of a logical class that holds a geometry | the type `Geometry`, a whole holding: [A property of the type Geometry](#a-property-of-the-type-geometry) |
| a line or an area as numbers | not possible: numbers hold a point or an envelope (SP43); hold it whole |

`SpatialExamplesTest.everyRowOfWhichFormDoIUse` builds every row into one model.

## Reference

### Relations

- Builds on CWM `core` only (the attributes it names are columns, record fields or properties;
  keys are read from the `keysindexes` unique keys a table owns, without a dependency); the
  validator reports in the form of `validation`; the standard set builds on `emf/standard`.
- Knows no OLAP class; the feature collections (`resource.spatial.feature`) build on it. No
  dependency on `units`: distances and bounds are metres whatever the reference system (a
  geographic system measured on the spheroid); areas and lengths are measured in metres on the
  spheroid in a geographic system, else in the plane of the working reference system; accuracy in
  metres.
- Holds no coordinates: geometries stay in the attributes that hold them.
- The coverage of a catalog is `dcat::Location`, not this model.

### Classes

`GeometryFeature`, `SpatialRelation`, `GridLevel`, `DistanceBand`, `ReferenceSystem` and
`GridSystem` extend `core::ModelElement` and stand in any namespace. The holdings and the parts of
a holding extend `core::Element`: they have no name and are owned by their feature, their holding.
`GeometryType` is a `core::DataType`. Non-containment references are one-way with an EMOF
`Property.oppositeRoleName` (prefix `spatial`); decimal numbers are CWM `Float` (decimal text),
whole numbers CWM `Integer`.

| Class | Owned by | Purpose |
| --- | --- | --- |
| `GeometryFeature` | a namespace | what a geometry is: kind, single or multi, height, reference system, coordinate epoch, accuracy, derivation |
| `GeometryHolding` (abstract) | its feature | where and how the geometry is held |
| `WholeHolding` | its feature | one attribute holds the whole geometry, in a storage and a stored axis order |
| `PartsHolding` | its feature | one number per attribute, a `CoordinatePart` each |
| `CoordinatePart` | its holding | one number: its role and the attribute that holds it |
| `ListHolding` | its feature | several numbers in one attribute, in an order, with a separator for text |
| `ReferenceSystem` | a namespace | a coordinate reference system by authority and code |
| `GeometryType` | a namespace | the logical type of a property that holds a whole geometry, named `Geometry` |
| `SpatialRelation` | a namespace | the rule that assigns child rows to parent rows |
| `GridLevel` | a namespace | the key of the cell of a grid system and resolution |
| `GridSystem` | a namespace, once per identifier | a system of grid cells, its resolutions, whether its cells nest |
| `DistanceBand` | a namespace | the class of the distance to a reference geometry |

#### GeometryFeature

| Feature | Type | Mult. | Meaning |
| --- | --- | --- | --- |
| `kind` | `GeometryKind` | 1 | `POINT`, `LINE`, `POLYGON`, `MIXED` (row by row); a multi form belongs to its kind; a derived geometry has the kind of its derivation |
| `multiplicity` | `GeometryMultiplicity` | 1 | `EITHER` (first literal: what an unset value reads), `SINGLE`, `MULTI`; numbers and derived geometries are single |
| `hasHeight` | `core::Boolean` | 1, default false | the coordinates hold a height; needs a system of three axes (SP23); held as numbers, `HEIGHT` or `MIN_HEIGHT` and `MAX_HEIGHT` (SP46) |
| `referenceSystem` | `ReferenceSystem` | 1 | the system of the geometry, the same in every holding; a derived geometry has the system of its geometry (SP51) |
| `coordinateEpoch` | `core::Float` | 0..1 | the decimal year the coordinates hold for, only where the system is dynamic (SP32, SP33, SP24) |
| `accuracy` | `core::Float` | 0..1 | positional accuracy in metres, for diagnostics |
| `derivation` | `GeometryDerivation` | 0..1, unsettable | unset: a geometry of its own; `ENVELOPE`, `CORNER_SOUTH_WEST`, `CORNER_NORTH_EAST`, `CORNER_NORTH_WEST`, `CORNER_SOUTH_EAST`, `CENTROID`, `POINT_ON_SURFACE`; exactly with `of` (SP49) |
| `of` | `GeometryFeature` | 0..1 | the geometry this one is derived from; not itself, nor through others (SP50) |
| `holding` | `GeometryHolding` | 1..*, owned | where and how the geometry is held; each holds the same geometry (SP42) |

#### The holdings

| Class | Feature | Type | Mult. | Meaning |
| --- | --- | --- | --- | --- |
| `WholeHolding` | `attribute` | `core::Attribute` | 1 | the attribute that holds the whole geometry |
| | `storage` | `GeometryStorage` | 1 | `NATIVE` (a geometry type: the database's, or `Geometry` of a logical property), `WKB`, `WKT`, `GEOJSON` |
| | `axisOrder` | `AxisOrder` | 1 | the order the stored coordinates hold the first two axes in: `EAST_NORTH` (first literal) or `NORTH_EAST`; `GEOJSON` holds east first (SP28) |
| `PartsHolding` | `part` | `CoordinatePart` | 1..*, owned, ordered | one number per attribute; the order carries no meaning |
| `CoordinatePart` | `role` | `Ordinate` | 1 | which number the attribute holds |
| | `attribute` | `core::Attribute` | 1 | the numeric attribute that holds it |
| `ListHolding` | `attribute` | `core::Attribute` | 1 | the attribute that holds the numbers |
| | `ordinate` | `Ordinate` | 1..*, ordered | the roles in the order they are held |
| | `separator` | `core::String` | 0..1 | the text between two numbers of a text; unset, the attribute holds several values |

`Ordinate` names a number by the axis: `EAST` (the longitude in a geographic system, the easting
in a projected one), `NORTH` (the latitude, the northing), `HEIGHT` for a point; `MIN_EAST` (west),
`MIN_NORTH` (south), `MAX_EAST` (east), `MAX_NORTH` (north), `MIN_HEIGHT`, `MAX_HEIGHT` for an
envelope. An envelope across the antimeridian has its west edge east of its east edge, as a GeoJSON
`bbox` writes it.

#### SpatialRelation

| Feature | Type | Mult. | Meaning |
| --- | --- | --- | --- |
| `child`, `parent` | `GeometryFeature` | 1 | the geometry assigned, the geometry assigned to; each read from a holding in the table of its key |
| `childKey`, `parentKey` | `core::Attribute` | 1 | a key column of a table that holds the child, the parent geometry; where the table declares no unique key of the column alone, the rows of one key are the parts of one geometry and are united first |
| `predicate` | `SpatialPredicate` | 1 | `WITHIN`, `COVERED_BY` (the only one a point on a border meets), `INTERSECTS`, `CENTROID_WITHIN` (the child's centroid where it lies on the child, else a point on its surface), `NEAREST`, `WITHIN_DISTANCE` |
| `distance` | `core::Float` | 0..1 | the distance of `WITHIN_DISTANCE` in metres, whatever the reference system |
| `assignment` | `SpatialAssignment` | 1 | `EXACTLY_ONE`, `LARGEST_SHARE`, `ALL_WEIGHTED` (parts divided by their sum), `ALL_EQUAL` (1/n) |
| `weight` | `SpatialWeight` | 0..1, unsettable | `AREA` or `LENGTH` of the intersection relative to the child |
| `unmatched` | `UnmatchedPolicy` | 1 | `KEEP_NULL` (the child stays with a null parent key), `ERROR`, `DROP` |
| `minimumShare` | `core::Float` | 0..1, default 0 | the smallest share of the child by weight that counts as a match, from 0 below 1 |
| `tieBreak` | `TieBreak` | 0..1 | `ERROR` (as none) or `LOWEST_PARENT_KEY` on an exact tie of `EXACTLY_ONE` |
| `explicitKey` | `core::Attribute` | 0..1 | a column of the child table that already holds a parent key; it wins over the geometry where not null |
| `workingSystem` | `ReferenceSystem` | 0..1 | the system predicate and measure are evaluated in |
| `result` | `core::Class` | 0..1 | the table or view a resolver writes, declared in advance or after the run |
| `resultForm` | `ResultForm` | 1 | `TABLE` (written once, any database reads it) or `VIEW` (needs the spatial functions) |

#### ReferenceSystem

A system is named as an OGC URI names it, `http://www.opengis.net/def/crs/{authority}/{version}/{code}`.

| Feature | Type | Mult. | Meaning |
| --- | --- | --- | --- |
| `authority` | `ReferenceSystemAuthority` | 1 | `EPSG`, `OGC` |
| `code` | `core::String` | 1 | the code as text: `25832`, `4326`, `CRS84`, `CRS84h` (SP34); an EPSG code is a positive whole number (SP35) |
| `version` | `core::String` | 0..1, default `0` | the version of the definition; none is `0`, as the URI writes it |
| `kind` | `ReferenceSystemKind` | 1 | `GEOGRAPHIC` (degrees), `PROJECTED` (easting and northing in the unit of its axes), `GEOCENTRIC` (metres from the earth's centre) |
| `axes` | `core::Integer` | 1, default 2 | 2 or 3 (SP36) |
| `axisOrder` | `AxisOrder` | 1, unsettable, no default | the order of the first two axes as the register defines them (SP37) |
| `dynamic` | `core::Boolean` | 1, default false | the system moves with a plate; a coordinate holds for one epoch, which the geometry feature names |

#### GridLevel, DistanceBand and GridSystem

| Class | Feature | Type | Mult. | Meaning |
| --- | --- | --- | --- | --- |
| `GridLevel` | `source` | `GeometryFeature` | 1 | the points put into cells, held in any form |
| | `sourceKey` | `core::Attribute` | 1 | a key column of the points' table; the first column of the result |
| | `system` | `GridSystem` | 1 | the system of the cells |
| | `resolution` | `core::Integer` | 1, unsettable | within the system's `minResolution` to `maxResolution` (SP38); unset is none |
| | `parent` | `GridLevel` | 0..1 | the coarser level above, same system (SP41), same points and source key |
| | `result`, `resultForm`, `unmatched` | | | as of a relation: source key, then a cell key per level of the chain |
| `DistanceBand` | `source`, `reference` | `GeometryFeature` | 1 | the geometry measured, the one measured to (its nearest row) |
| | `sourceKey` | `core::Attribute` | 1 | a key column of the source's table |
| | `bound` | `core::Float` | 1..* ordered | upper bounds of the classes in metres; a class holds the distances up to and including its bound |
| | `workingSystem`, `result`, `resultForm`, `unmatched` | | | as of a relation: source key and class |
| `GridSystem` | `name` | `core::String` | 1 (SP40) | the identifier: `geohash`, `h3`, `quadkey`, or a URI |
| | `minResolution`, `maxResolution` | `core::Integer` | 1 | the coarsest and the finest resolution (SP39) |
| | `nested` | `core::Boolean` | 1, default false | the coarser cell holds its finer cells strictly (geohash, quadkey) |

### What a resolver leaves

The result's columns are the child key, the parent key and, for shares, the share, in this order;
of a grid level the source key and the cell key of each level of its chain (the parents from the
top, then the level), of a distance band the source key and the class. A resolver never alters a
source table: `EXACTLY_ONE` too writes a key pair. A geometry is read from a holding in the table
of the key: a whole one where there is one, else the point or the envelope built from the numbers.

A `VIEW` is computed anew whenever it is read and cannot stop: a rule that would have to stop
(`EXACTLY_ONE` without the tie break `LOWEST_PARENT_KEY`, `unmatched` `ERROR`) writes a `TABLE`
(SP26).

| `assignment` | Result | Unique | Read as |
| --- | --- | --- | --- |
| `EXACTLY_ONE`, `LARGEST_SHARE` | child key, parent key (null for `KEEP_NULL`) | child key | a foreign key: a snowflake step between the child's table and the parent's table |
| `ALL_WEIGHTED`, `ALL_EQUAL` | child key, parent key, share; the shares of a child add up to 1 | child key and parent key | a many-to-many bridge; a measure divided along it is the stored one multiplied by the share |

An unmatched child keeps one row with a null parent key (with shares: share 1). How a missing parent
shows is the policy of the model that reads the result. A grid level and a band take the same
`unmatched`: `KEEP_NULL` a row with null cells or class, `ERROR` stops a resolver, `DROP` leaves
them out.

### Rules

`SpatialModelValidator` gives its findings in the one record of the models (`Finding` of
`validation`, module `spatial`); errors stop a resolver, warnings do not.

```java
SpatialModelValidator.check(model);                         // every finding, errors and warnings
SpatialModelValidator.validate(model);                      // the errors as lines, "SP43 ..."
```

`HeightNeedsThreeAxes`, `EpochOnlyWhenDynamic`,
`NotDerivedFromItself`, `KindFollowsDerivation` (geometry feature), `GeoJsonEastFirst` (whole
holding), `EachRoleOnce` (parts and list), `MinimumShareInRange`, `DistanceOnlyForWithinDistance`
(relation), `ParentNotSelf`, `ResolutionInRange`, `ParentOfTheSameSystem` (grid level),
`ResolutionsInOrder` (grid system) and `AxesTwoOrThree` (reference system) stand in the Ecore as
OCL invariants as well; the axis order of a reference system is required and unsettable, so that
EMF's own multiplicity check finds it unset, as SP37 does.

| Rule | Severity | Says |
| --- | --- | --- |
| SP2 | error | an attribute is named once, by one holding (in one of its parts) |
| SP3 | warning | the type of the attribute of a whole holding fits its storage: `NATIVE` a geometry type (the data type `Geometry` among them), `WKT` and `GEOJSON` text, `WKB` binary (an unknown type is no finding) |
| SP5 | error | `WITHIN`, `COVERED_BY`, `INTERSECTS`, `CENTROID_WITHIN` need a parent of kind `POLYGON` or `MIXED` |
| SP6 | error | `AREA` weighs a polygon, `LENGTH` a line; a point has no weight |
| SP7 | error | `EXACTLY_ONE` carries a weight only with a minimum share above zero |
| SP8 | error | `ALL_WEIGHTED` and `LARGEST_SHARE` need a weight; `ALL_EQUAL` may have one (for the minimum share only) |
| SP9 | error | the minimum share is from 0 below 1, above 0 only with a weight |
| SP10 | error | a distance goes with `WITHIN_DISTANCE` only, and is above zero |
| SP11 | error | `NEAREST` assigns `EXACTLY_ONE` (a tie break is allowed) |
| SP12 | error | child and parent (a band's source and reference) share a reference system (one URI), or a working one is named |
| SP14 | error | a relation, a grid level or a band names its geometries and keys; each key is a column of a table that holds its geometry |
| SP15 | warning | `DROP` of a relation, a grid level or a band: facts that reach the parent, cell or class through the result vanish from the sums |
| SP16 | error | a declared result has the shape of its rule: the keys' types, a numeric share; a grid's source key and one cell key per level, text (or a number for a system that does not nest); a band's class a number |
| SP17 | error | following child to parent never leads back: no place is its own ancestor |
| SP18 | error | a grid takes points into cells of a system and at a resolution it names, below a coarser level over the same points and source key |
| SP19 | error | the bounds of a band are decimal numbers above zero, strictly ascending |
| SP20 | warning | a reference that does not resolve |
| SP21 | warning | an explicit key with `NEAREST`: the key, not the geometry, decides the rows that carry it |
| SP22 | error | a grid takes single points: a source of multiplicity `MULTI` has no one cell |
| SP23 | error | a feature with a height has a reference system of three axes |
| SP24 | warning | a geometry feature in a dynamic reference system names the epoch its coordinates hold for |
| SP25 | warning | a child, parent or source key is not a declared unique key of one column |
| SP26 | error | a `VIEW` result where the rule would have to stop |
| SP27 | warning | an area, a length or a distance measured in a system of kind `GEOCENTRIC`, of which the planar functions read x and y only |
| SP28 | error | a whole holding stored as `GEOJSON` holds its coordinates east first (RFC 7946) |
| SP29 | warning | a whole holding stored as `GEOJSON` holds longitude and latitude of WGS 84: CRS84, CRS84h, EPSG 4326 or 4979 stored east first; another system only by prior arrangement |
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
| SP42 | error | a geometry feature has a holding, and each holding names its attributes: a whole and a list holding one, a holding in parts a part at least, each part one |
| SP43 | error | a geometry held as numbers (in parts or as a list) is a single point, or the envelope of another geometry |
| SP44 | error | a holding of numbers names each role once |
| SP45 | error | the roles fit the shape: a point has `EAST` and `NORTH` and no edge of an envelope; an envelope its four edges `MIN_EAST`, `MIN_NORTH`, `MAX_EAST`, `MAX_NORTH` and no axis of a point |
| SP46 | error | a geometry held as numbers has its height roles exactly where it has a height: `HEIGHT` for a point, `MIN_HEIGHT` and `MAX_HEIGHT` for an envelope |
| SP47 | warning | the attributes of numbers are numeric: a part's a number; a list's text where it has a separator, else several numbers (an array, a JSON array type, or a multi-valued attribute of a number type); a JSON primitive type is judged by its value kind; a type not known, or a number without a stated multiplicity, is no finding |
| SP48 | error | the attributes of a holding in parts belong to one classifier: one row holds one value |
| SP49 | error | a geometry names a derivation exactly where it names what it is derived from |
| SP50 | error | a geometry is not derived from itself, directly or through others |
| SP51 | error | a derived geometry is in the reference system of the geometry it is derived from (one URI) |
| SP52 | error | the kind follows the derivation: an envelope is a single polygon; a corner, a centroid and a point on the surface single points |
| SP53 | error | two corners derived from one geometry and held in one classifier are opposite: south-west and north-east, or north-west and south-east |

A code keeps its meaning, and a check that vanished retires its code. There is no SP1: a holding
names attributes by its type, so that no class, package or association end can stand where an
attribute does. There is no SP4: its checks have a code each (SP30 to SP36). There is no SP13: the
warning it gave is SP27. An error and a warning never share a code.

### Not modelled

- **Validity over time**: the relation holds no time. A territory that changes (a county reform)
  gets one result per state.
- **Shares that change over time**: one result holds one set of shares.
- **The unit of a projected system**: a reference system holds no unit. Distances and bounds are
  metres in every system; a projected system in feet is not told apart.
- **Lines and areas as numbers**: numbers hold a point or an envelope; a line or an area is held
  whole.
- **A cell key as the source of a position**: a grid level derives a cell from a point, not a point
  from a cell. A circle of a point and a radius, and a measured value at a coordinate (M), are not
  held either.

### The Ecore

`model/spatial.ecore` is written by `SpatialEcoreBuilder` (test sources, package `build`); every
class, feature, enumeration and literal carries its documentation there.
`SpatialEcoreUpToDateTest` fails while the checked-in file differs from what the builder writes;
after a change to the builder run it once with `-Dregenerate.ecore=true`, then build the module
with `mvn clean install` (the code generator merges into files that exist).
