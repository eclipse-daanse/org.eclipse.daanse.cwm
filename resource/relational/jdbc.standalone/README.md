# org.eclipse.daanse.cwm.resource.relational.jdbc.standalone

A live database and a CWM relational model, both ways, in plain Java without an OSGi runtime:
`CwmJdbcImport` reads a database into a `relational::Catalog`, `CwmJdbcExport` writes a model into
a database. Both choose their part by the same [selection rules](../../../maven/README.md#selection-rules).
The module wires by hand what OSGi wires as services: the `CwmLoader` with the `PrivilegeLoader` as
its part on the reading side, the DDL generator of [`ddl.render`](../ddl.render) on the writing
side. It is the core of the Maven goals [`cwm-jdbc:import`](../../../maven/jdbc/README.md) and
[`cwm-jdbc:export`](../../../maven/jdbc/README.md#writing-a-model-into-a-database-cwm-jdbcexport).

## Relations

- Reads with [`load.jdbc`](../load.jdbc) and [`load.privilege`](../load.privilege) on the metadata
  snapshot of the Daanse SQL library, through the metadata provider of the database product.
- Writes with [`ddl.render`](../ddl.render): the statements are those `cwm-ddl:generate` writes
  into its script files for the same model, selection and settings.
- Brings [`emf/standalone`](../../../emf/standalone/README.md) for reading and writing the XMI.
- What a database spells its own way after a round trip is let pass by the tolerances of the
  [`diff`](../diff) module, `Tolerance.roundTrip(dialect)`.

## Example

```java
Selection part = CwmJdbcExport.selection(List.of("schema:sales", "is:role", "!is:documentary"));

// write: cut a copy of the model, write its DDL in the database's dialect, run it
Exported done = CwmJdbcExport.write(connection, model, part, ExportSettings.defaults().withDropFirst(true));
done.selected().leftOut();       // what the selection left out, each with the rule or consequence
done.executed();                 // the statements that ran, in their order
done.skipped();                  // the statements of the drop script whose element was not there

// read back what the database holds of what was written, and compare
Catalog back = CwmJdbcImport.load(connection, done.readBack());
CatalogDiff diff = CatalogDiffer.create().diff(done.selected().model(), back,
        DiffSettings.defaults().withTolerances(Tolerance.roundTrip(done.dialect())));

// or read a database by rules of its own
Catalog sales = CwmJdbcImport.load(connection, JdbcToCwmConfig.defaults().withCatalogName("shop")
        .withSelection(CwmJdbcImport.selection(List.of("schema:sales", "is:role"))));
CwmPackages.save(Path.of("target", CwmJdbcImport.fileName(sales)), List.of(sales));
```

Each of `load` and `write` takes a `Connection` (left open), a `DataSource` or a URL with user,
password, driver class and class loader; `Connections.connect` opens a connection by URL the same
way for any other use: the driver class when given, otherwise the first driver of the class loader
that accepts the URL, finally `DriverManager`.

## Writing

- **Dialect.** `Dialects.forConnection` maps the product name the database reports (the URL as
  fallback) to a dialect id: `postgresql`, `mysql`, `mariadb`, `oracle`, `sqlserver`, `h2`,
  `duckdb`; another database is an error that names the known ids. `ExportSettings.withDialect`
  names one instead.
- **Drop first.** With `dropFirst` the drop script of the same model runs before the create script,
  written with `IF EXISTS`, so an export runs again and again. `IF EXISTS` alone does not do it:
  `REVOKE` and `DROP ROLE` have no such clause, nor have `DROP CONSTRAINT` and `DROP INDEX` in
  MySQL, an `ALTER TABLE` that drops a constraint fails without its table, and H2 fails a
  `DROP … IF EXISTS` without its schema. Before it runs, the database is asked what it holds of the
  model: the schemas the model is written into are read in one go as the import reads them, their
  tables and views with the keys, unique constraints, checks, foreign keys, indexes and triggers;
  the routines through the JDBC metadata; roles and users through the metadata provider of the
  database. A `DROP SCHEMA`, `DROP TABLE` or `DROP VIEW` runs where its element is there, a
  `DROP MATERIALIZED VIEW` where the view is there materialized, a `DROP VIEW` where it is there
  plain; what
  drops a unique constraint, check, foreign key, index or trigger runs where its table holds that
  element under the name the statement gives it, so a constraint the model gained since the last
  export is skipped; a `DROP FUNCTION` or `DROP PROCEDURE` runs where a routine of its name is
  there; a `DROP ROLE` or `DROP USER` runs where the role or the user is there; a
  `REVOKE` where the grantee and the table, routine or role it is on are there. Each statement is
  kept with the element it is written for, so this is decided by the element, not by the text, and
  no failure of a statement that runs is passed over. Where the metadata provider does not report
  roles and users (H2, DuckDB), the statements on them run as written.
- **Names in the drop script.** An element without a name is dropped under the name
  `RelationalNames` gives it, by which the import also names what a database reports without one.
  A primary key has no statement of its own, it goes with its table, so the name a database gives
  it (`PRIMARY` in MariaDB and MySQL, `<table>_<columns>_pkey` in DuckDB) does not count; nor do
  the names DuckDB gives the constraints it declares in `CREATE TABLE`. A constraint, index or
  trigger the database holds under another name than the model's (made by another tool, renamed,
  or named by an earlier model) is not dropped by a statement that names the model's name: that
  statement is skipped, and the element goes with the `DROP TABLE` of its table. A foreign key
  left this way refers to its table until then; where the script drops the referenced table
  first, that `DROP TABLE` fails, unless `cascade` is set and the dialect writes `CASCADE`.
- **Transaction.** Where the metadata say the database runs DDL in transactions
  (`supportsDataDefinitionAndDataManipulationTransactions`, and DDL neither commits nor is ignored),
  both scripts run in one transaction, which a failure rolls back; PostgreSQL and DuckDB do.
  Elsewhere, H2 for one, each statement is committed as it runs, and a failure leaves what ran
  before it. The auto-commit state of the connection is restored afterwards, a connection passed
  in stays open, and a transaction the caller has open on it is committed with the export.
- **What the dialect cannot write.** A model can hold more than the dialect writes, a
  materialized view where the database has none, a descending index column where it keeps every
  column ascending, a user where it has no users; the cases are listed in the README of
  [`cwm-ddl`](../../../maven/ddl/README.md#what-is-not-written). By default such an element is
  written plainer or left out, and the log names each case as a warning. With
  `ExportSettings.defaults().withDdl(DdlSettings.defaults().withStrict(true))` the export fails
  before anything runs, with an `UnwritableException` that names every case: the element, what the
  dialect cannot write, the dialect.
- **Failure.** A statement the database rejects ends the export with a `StatementFailedException`:
  the statement, its position in its script, the element it is written for by kind and qualified
  name, whether it was rolled back and what ran before it, with the database's `SQLException` as
  cause.
- **Reading back.** `Exported.readBack()` is the configuration that reads back what the export
  wrote, for `CwmJdbcImport.load`: the catalog under the name of the model, the comments as
  written, and the rules the model was cut by. A database holds more than any model written into
  it: the schemas it keeps for itself (`public`, `information_schema`, `main`), its login users and
  predefined roles, what other models put there; rules without an include rule, such as
  `Selection.all()`, reach all of it. So the read back also leaves out every schema and every role
  or user the written model does not hold, by name. Within the schemas and parties it holds, the
  database is read as the rules select, so a table there beyond the written ones shows as a
  difference.

## Tests

`CwmJdbcImportTest` connects through a class loader, loads by URL and from a `DataSource`, reads the
roles and privileges as the rules select them (PostgreSQL in a container) and fails on an include
rule that matches nothing. `CwmJdbcExportTest` writes into H2 and DuckDB in the process, again and
again with `dropFirst`, reads back with `readBack()` and compares with `CatalogDiffer` under
`Tolerance.roundTrip`: no differences; read back by the rules alone, H2's own schemas come too,
while `readBack()` reads the written schemas and shows a table added there; DuckDB gets one schema
by a selection, which the result reports; a rejected statement on H2 is named, and what ran before
it remains; a model that gained a check without a name, a unique constraint, an index and a foreign
key since its last export goes in again with `dropFirst`, the statements that would drop them
skipped, and comes back as written. `CwmJdbcExportPgTest` does the round trip
of everything, roles and privileges included, on PostgreSQL, with `dropFirst` twice in a row and
once more after the schemas went and the roles stayed; the read back holds neither `public` nor
the login user. A failure there leaves nothing behind. A model that gained constraints, an index
and a trigger goes in again there too, its new drop statements skipped. `CwmJdbcExportMySqlTest`
does the same on MySQL, where `DROP CONSTRAINT` and `DROP INDEX` have no `IF EXISTS` and a
statement that names what the database does not hold would fail.
