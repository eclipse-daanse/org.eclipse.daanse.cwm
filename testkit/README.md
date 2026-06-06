# Daanse CWM — Test Kit (aggregator)

CWM-side test-kit: SPIs for describing the desired database state via
CWM, plus executors that materialise that state into a live JDBC
database and assert it back.

## Why it exists

Daanse tests have two concerns that overlap but are not the same:

1. **What database structure should exist** — tables, columns, types,
   indexes, foreign keys, views, triggers. This is naturally
   expressed as a CWM `Schema` (the EMF model already in
   `org.eclipse.daanse.cwm.model.cwm`).
2. **How to drive a real JDBC database** to that state and to load
   data into it. This was previously hand-rolled in every test
   (CREATE TABLE strings + raw INSERTs + JDBC metadata walks).

This module group bridges the two: a test declares the desired state
once as a CWM `Schema` plus optional `DataSupplier`/`DatabaseCheckSuiteSupplier`,
and the executors here do the rest. All dialect-specific DDL/SQL
emission goes through the existing `cwm.sql.gen` + `jdbc.db.dialect.*`
chain, so the same CWM declaration works across H2 / PG / MySQL /
MSSQL / MariaDB / Oracle without changes.

## What ships here

| Module | Artifact | Purpose |
|--------|----------|---------|
| `api` | `org.eclipse.daanse.cwm.testkit.api` | SPIs: `DatabaseSupplier`, `DataSupplier`, `DatabaseCheckSuiteSupplier`, `CsvAutoDetect`, and the `dbcheck.*` record family |
| `database` | `org.eclipse.daanse.cwm.testkit.database` | `DatabaseLayer.apply(...)` — CWM Schema → DDL → JDBC. `DatabaseCheckExecutor` — diff live DB metadata against expectations |
| `data` | `org.eclipse.daanse.cwm.testkit.data` | `DataLayer.apply(...)` — load CSV resources into the tables created by `DatabaseLayer`, typed by the CWM Schema's columns |

## Composition

The three modules layer cleanly:

```
DatabaseSupplier     →  DatabaseLayer.apply(ds, dialect, schema)
DataSupplier         →  DataLayer.apply(ds, dialect, schema, supplier)
DatabaseCheckSuite   →  DatabaseCheckExecutor.execute(breadcrumb, ds, suite)
```

Each layer is optional and independently usable. A test that just
wants tables-but-no-data uses Layer 1 only; one that wants tables +
CSV-loaded data uses Layers 1+2; one that wants tables + data + a
post-load assertion uses all three.

## Position in the daanse stack

```
            ┌─────────────────────────────────────┐
            │  cwm.testkit.api   (SPIs + records) │
            └────┬───────────┬─────────────┬──────┘
                 │           │             │
        ┌────────▼──┐  ┌─────▼──┐  ┌───────▼──────┐
        │ database  │  │ data   │  │ (consumer:   │
        │ Layer +   │  │ Layer  │  │  rolap       │
        │ Check Exec│  │        │  │  testkit)    │
        └───────────┘  └────────┘  └──────────────┘
                 │           │
                 ▼           ▼
       cwm.sql.gen      cwm.csv.read + cwm.sink.jdbc
       (DDL emission)   (CSV → JDBC pipeline)
```

## Related modules

- `jdbc.datasource.testkit` — provides the `(DataSource, Dialect)` pair the executors consume.
- `rolap.testkit.core.CatalogTestHarness` — top-level composer that wires `DatabaseLayer + DataLayer + DatabaseCheckExecutor + olap.testkit.OlapCheckSuiteRunner + TestContext`.
- `cwm.csv.read` + `cwm.sink.jdbc` — the underlying CSV→DB pipeline `DataLayer` drives.
