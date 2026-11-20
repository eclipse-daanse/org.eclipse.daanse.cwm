# org.eclipse.daanse.cwm.model.cwm.resource.relational

The Relational package of CWM 1.1 as an EMF model, generated from `model/relational.ecore`, with the helpers of
`util` (`Catalogs`, `Tables`, `Columns`, `SQLSimpleTypes` and more).

## Standard set: the SQL types

`StandardSqlTypes` holds the SQL types as a standard set: a `typemapping::TypeSystem` `SQL` (version
`SQL:1999, BIGINT of SQL:2003`) with the 23 SQL-99 types the CWM specification lists (section 19.5) and `BIGINT`,
which came with SQL:2003: 24 types, each made by `SQLSimpleTypes`. A type of one product (`TINYINT`, `TEXT`) is not
in the set. Sizes stay off the type; a column carries its own.

CWM gives no type numbers. The numbers are those of `java.sql.Types`, as CWM's section 19.6 suggests; three are a
choice without a basis in CWM or SQL. `TIME WITH TIMEZONE` and `TIMESTAMP WITH TIMEZONE` keep CWM's spelling, where
SQL writes `TIME ZONE`.

| Type | Number |
| --- | --- |
| `BIT` | -7 (`BIT`), a choice: the BIT of JDBC is one bit, that of SQL-99 a string of bits |
| `BIT VARYING` | -3 (`VARBINARY`), a choice: JDBC has no string of bits |
| `BINARY LARGE OBJECT` | 2004 |
| `CHARACTER`, `CHARACTER VARYING`, `CHARACTER LARGE OBJECT` | 1, 12, 2005 |
| `NATIONAL CHARACTER`, `NATIONAL CHARACTER VARYING`, `NATIONAL CHARACTER LARGE OBJECT` | -15, -9, 2011 |
| `NUMERIC`, `DECIMAL` (radix 10) | 2, 3 |
| `INTEGER`, `SMALLINT` | 4, 5 |
| `FLOAT`, `REAL`, `DOUBLE PRECISION` (radix 2) | 6, 7, 8 |
| `BOOLEAN` | 16 |
| `DATE`, `TIME`, `TIME WITH TIMEZONE`, `TIMESTAMP`, `TIMESTAMP WITH TIMEZONE` | 91, 92, 2013, 93, 2014 |
| `INTERVAL` | 1111 (`OTHER`), a choice: JDBC has no interval |
| `BIGINT` | -5 |

CWM gives `INTEGER` and `SMALLINT` the scale 0. The file shows no scale for any type: the scale is a number whose
default in the Ecore is 0, and a value equal to the default is not written.

A column of a model file may refer to its type in the set; a catalog may as well own its types
(`SQLSimpleTypes.ownedIn`). Both placements stay.

The set is the file `sql-types.xmi` in the jar beside `StandardSqlTypes` under the URI
`https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.cwm.resource.relational/sql-types.xmi`
(`StandardSqlTypes.SET_URI`). The id of every element is the UUID stated beside it in `StandardSqlTypes`; how a set is
written, kept equal to the code, mapped and loaded is said in `emf/standard`.

```java
TypeSystem sql = StandardSqlTypes.load(resourceSet);                        // mapped and read from the jar
SQLSimpleType varchar = StandardSqlTypes.type(sql, "VARCHAR").orElseThrow(); // CHARACTER VARYING
StandardSqlTypes.map(otherResourceSet);  // only mapped: a column's href to the set is read from the jar when followed
```

The set needs `typemapping` and `emf/standard`; under OSGi both are imported optionally, so the model resolves
without them.
