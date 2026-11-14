# CWM and a database connection — Maven plugin `cwm-jdbc`

`org.eclipse.daanse:org.eclipse.daanse.cwm.maven.jdbc` reads the structure of a live database
over JDBC and writes it as a CWM relational catalog XMI: schemas, tables and columns with their
SQL types, primary and unique keys, check constraints, foreign keys, indexes, views, materialized
views, triggers, procedures and functions, table and column comments, and the roles, users and
their privileges. And it writes such a catalog into a database. Rules say which part is read or
written. The plugin has two goals, **`cwm-jdbc:import`** (database → XMI) and
**[`cwm-jdbc:export`](#writing-a-model-into-a-database-cwm-jdbcexport)** (XMI → database). Both
run inside a project or without one, straight from the command line.

Both directions live in [`resource/relational/jdbc.standalone`](../../resource/relational/jdbc.standalone/README.md),
the plugin's only dependency: for reading, the JDBC metadata snapshot of the Daanse SQL library,
the `CwmLoader` that turns it into CWM and the `PrivilegeLoader` for the privileges; for writing,
`CwmJdbcExport`, which runs the DDL [`cwm-ddl:generate`](../ddl/README.md) writes for the same
model, rules and settings. This page is about using the plugin.

- [Requirements](#requirements)
- [Quick start](#quick-start)
- [What you get](#what-you-get)
- [All settings](#all-settings)
- [Writing a model into a database: `cwm-jdbc:export`](#writing-a-model-into-a-database-cwm-jdbcexport)
- [Selection rules](#selection-rules)
- [The JDBC driver](#the-jdbc-driver)
- [Credentials](#credentials)
- [Recipes](#recipes)
- [Messages and failures](#messages-and-failures)
- [Troubleshooting](#troubleshooting)
- [Tests of this module](#tests-of-this-module)

## Requirements

| What | Version / value |
| --- | --- |
| Java | 21 or newer (the plugin is compiled for Java 21) |
| Maven | 3.9 or newer (built against the 3.9 plugin API; this repository builds with Maven 4) |
| Plugin coordinates | `org.eclipse.daanse:org.eclipse.daanse.cwm.maven.jdbc:0.0.1-SNAPSHOT` |
| Where it comes from | built from this repository: `mvn install -pl maven/jdbc -am` puts it and its core into the local repository |
| Dependencies | only its core `org.eclipse.daanse.cwm.resource.relational.jdbc.standalone` (plus the Maven API) |
| JDBC driver | yours: as dependency of the plugin declaration, or as Maven coordinates in `drivers` |
| Database | reachable from the build, with a user that may read the catalog metadata, and for the export create and drop what the model holds |

## Quick start

On the command line, with the driver by its Maven coordinates:

```sh
mvn org.eclipse.daanse:org.eclipse.daanse.cwm.maven.jdbc:0.0.1-SNAPSHOT:import \
    -Dcwm-jdbc.url=jdbc:postgresql://localhost:5432/shop \
    -Dcwm-jdbc.user=reader -Dcwm-jdbc.password=secret \
    -Dcwm-jdbc.selection=schema:sales \
    -Dcwm-jdbc.drivers=org.postgresql:postgresql:42.7.4
```

This writes `target/generated-cwm/shop.xmi` (in the current directory when there is no project).

In a POM, with the driver as plugin dependency and the credentials in `settings.xml`:

```xml
<plugin>
  <groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.cwm.maven.jdbc</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <dependencies>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <version>42.7.4</version>
    </dependency>
  </dependencies>
  <configuration>
    <url>jdbc:postgresql://localhost:5432/shop</url>
    <serverId>shop-db</serverId>
    <catalogName>shop</catalogName>
    <selection>
      <rule>schema:sales</rule>
      <rule>is:role</rule>
    </selection>
    <outputDirectory>${basedir}/src/main/cwm</outputDirectory>
  </configuration>
</plugin>
```

`mvn cwm-jdbc:import` (the prefix resolves through the plugin declared in the POM) then refreshes
`src/main/cwm/shop.xmi`. The goal is not bound to a phase in this example on purpose:
reading a database is an explicit step, not part of every build. To bind it, add an
`<execution>` with `<goals><goal>import</goal></goals>` (default phase `generate-resources`) and
use [`skip`](#build-control) where the database is not reachable.

## What you get

One XMI file with one `relational::Catalog` as root:

| CWM | from the database |
| --- | --- |
| `Catalog` | named `catalogName`, or the name the database reports |
| `Schema` | every read schema; the tables of a database without schemas lie in a schema without name |
| `Table`, `View` | tables and views with their columns; a materialized view is a read-only `View` with the tagged value `materialized=true`; a system or temporary table has `isSystem` or `isTemporary` set |
| `Column` | name, SQL type with its size, length, scale, nullability, default value |
| `SQLSimpleType` | the column and parameter types, owned by the catalog, one per distinct type |
| `PrimaryKey`, `UniqueConstraint`, `CheckConstraint` | the table's keys and constraints |
| `ForeignKey` | with its columns, the referenced key, update and delete rules, deferrability |
| `SQLIndex` | with its columns and uniqueness |
| `Trigger` | event, timing, orientation and body |
| `Procedure` | procedures and functions with their parameters; overloads (several of one name in a schema, PostgreSQL) each a routine of its own, which keeps the specific name the database gives it, where it differs from its name, as the tagged value `daanse.jdbc.specificName` (`RelationalNames.SPECIFIC_NAME_TAG`): it is the database's own name, not written as DDL and not compared |
| `Description` (type `JDBC-REMARKS`) | the comments of tables, views, columns and routines, where the database keeps them (MySQL and MariaDB keep none on a view) |
| `ResponsibleParty` with `TablePrivilege`, `ProcedurePrivilege`, `RolePrivilege` | roles and users, marked by the stereotypes `databaseRole` and `databaseUser`, owning their privileges on the read tables, columns and routines (a privilege on one of several overloads on that one, by its specific name) and their role memberships (model [`resource.relational.privilege`](../../model/daanse/resource.relational.privilege)); read where no include rule is given, or where one reaches them (`is:role`, `is:user`) |

Every element carries an `xmi:id` built from its name path, e.g.
`Table_shop.sales.ORDERS` or `Column_shop.sales.ORDERS.CUSTOMER_ID`, so a mapping file (for
example a CWM-ORM mapping, see [`fennec-eorm`](../eorm/README.md)) can refer to it by a
readable `href`, and a later import of the same database keeps the ids.

How much the database reports depends on its metadata provider: PostgreSQL, Oracle, SQL Server,
MySQL, MariaDB, H2 and DuckDB have their own, every other database is read through standard JDBC
metadata. A model written as DDL and read back is the model that was written, keys, constraints,
foreign keys, indexes, views and comments included, on H2, DuckDB, PostgreSQL, MariaDB, MySQL, SQL
Server and Oracle, under the tolerances of `Tolerance.roundTrip` in
[`resource/relational/diff`](../../resource/relational/diff) for what each spells its own way; MySQL
reports a string default without its quotes (`DEFAULT 'NEW'` as `NEW`), so such a default does not
read back as written. A trigger whose body the DDL put into a procedure of its own (PostgreSQL,
MySQL, MariaDB) reads back with that body; the procedure is not read as a routine. The index H2,
MySQL and MariaDB make themselves for a foreign key whose columns have none is not read as an index
of the catalog: the metadata say it is the database's own. On MySQL and MariaDB they say so by its
name, the foreign key's: an index declared under that name over exactly the key's columns is taken
for the database's own as well, and the index that stays when its foreign key is dropped reads as an
ordinary index. Roles and users are reported by PostgreSQL, Oracle, SQL Server, MySQL and MariaDB,
the privileges by the first three fully and by MySQL and MariaDB in part; H2 and DuckDB report no
roles.

## All settings

Every setting is an element inside `<configuration>` or a command-line property
`-D<property>=<value>` (lists comma-separated). The connection settings, `selection` and `skip`
are those of both goals; the settings of the export alone are in
[its section](#settings-of-the-export).

### Connection

| Setting | Property | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `url` | `cwm-jdbc.url` | string | — (required) | The JDBC URL. |
| `user` | `cwm-jdbc.user` | string | — | The database user; wins over the one of `serverId`. |
| `password` | `cwm-jdbc.password` | string | — | The password; wins over the one of `serverId`. |
| `serverId` | `cwm-jdbc.serverId` | string | — | Id of a `<server>` in `settings.xml` that holds user and password (encrypted passwords too). An unknown id fails the build. |
| `driverClass` | `cwm-jdbc.driverClass` | string | — | The driver class; unset, the driver that accepts the URL is used. |
| `drivers` | `cwm-jdbc.drivers` | list | — | JDBC drivers as Maven coordinates `groupId:artifactId:version`, resolved with their dependencies from the build's repositories; for the command line, where the driver cannot be a plugin dependency. |

### What is read

| Setting | Property | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `catalogName` | `cwm-jdbc.catalogName` | string | the database's name | Name of the CWM catalog (and of the file). |
| `selection` | `cwm-jdbc.selection` | list of rules | everything | What is read, one [rule](../README.md#selection-rules) per `<rule>` element (on the command line comma-separated). An include rule that matches nothing fails the goal. |

### Output

| Setting | Property | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `outputDirectory` | `cwm-jdbc.outputDirectory` | directory | `${project.build.directory}/generated-cwm` | Where the XMI is written; missing directories are created. |
| `fileName` | `cwm-jdbc.fileName` | string | `<catalog name>.xmi` | File name of the XMI. An existing file is overwritten. |
| `attachResource` | `cwm-jdbc.attachResource` | boolean | `true` | Add `outputDirectory` as a resource directory, so the XMI lands in the jar. |

### Build control

| Setting | Property | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `skip` | `cwm-jdbc.skip` | boolean | `false` | Do nothing, e.g. `-Dcwm-jdbc.skip` in builds without the database. |

## Writing a model into a database: `cwm-jdbc:export`

`cwm-jdbc:export` loads a CWM XMI (with `CwmPackages.loadModel`, as every goal of this repository,
`extraPackages` included), takes its relational catalog, cuts a copy of it to the `selection`,
writes the DDL of the copy in the dialect of the database and runs it over JDBC: statement for
statement what `cwm-ddl:generate` writes for the same model, rules and settings. The model file
stays as it is.

```sh
mvn org.eclipse.daanse:org.eclipse.daanse.cwm.maven.jdbc:0.0.1-SNAPSHOT:export \
    -Dcwm-jdbc.modelFile=src/main/cwm/shop.xmi \
    -Dcwm-jdbc.url=jdbc:postgresql://localhost:5432/shop -Dcwm-jdbc.serverId=shop-db \
    -Dcwm-jdbc.selection=schema:sales,is:role -Dcwm-jdbc.dropFirst \
    -Dcwm-jdbc.drivers=org.postgresql:postgresql:42.7.4
```

In a POM, for example to set up the database of the integration tests (the default phase is
`pre-integration-test`):

```xml
<execution>
  <id>test-database</id>
  <goals>
    <goal>export</goal>
  </goals>
  <configuration>
    <modelFile>${basedir}/src/main/cwm/shop.xmi</modelFile>
    <url>jdbc:postgresql://localhost:5432/shop_it</url>
    <serverId>shop-it</serverId>
    <selection>
      <rule>!is:documentary</rule>
    </selection>
    <dropFirst>true</dropFirst>
  </configuration>
</execution>
```

- **Dialect.** The dialect is found by the product name the database reports (the URL as
  fallback): `postgresql`, `mysql`, `mariadb`, `oracle`, `sqlserver`, `h2`, `duckdb`; `dialect`
  names one instead. Another database fails the goal and names the known ids.
- **Drop first.** With `dropFirst` the drop script of the same model runs first, written with
  `IF EXISTS`, so the goal runs again and again. The database is asked before what it holds of
  the model; a statement runs only where the database holds the element it is written for, a
  constraint, index or trigger under the name the statement gives it. The others are skipped, as
  the constraints the model gained since the last export, the `REVOKE`s of roles that are not
  there yet, or everything on a database that is still empty.
- **Transaction.** Where the database runs DDL in transactions (PostgreSQL, DuckDB) both scripts
  run in one, which a failure rolls back; elsewhere each statement is committed as it runs.
- **Failure.** A statement the database rejects fails the build with the statement, its position
  in its script, the element it is written for and what remains in the database:
  `statement 7 of the CREATE script, for CheckConstraint shop.sales.orders.ck_orders_qty, failed:
  ALTER TABLE … — <the database's message> (not rolled back, the 6 statements before it remain)`.
- **Log.** The goal logs what the selection left out and retained, each with its reason, how many
  statements ran and how many of the drop script were skipped; with `-X` each skipped statement.

What a dialect cannot write as the model has it (a materialized view where the database has
none, a descending index column where it keeps every column ascending, …) is written plainer or
left out, and the build log names each case; with `strict` the goal fails before anything runs and
names every case. The cases are listed in the README of
[`cwm-ddl`](../ddl/README.md#what-is-not-written); whether the database holds what was written is
answered by reading it back with `cwm-jdbc:import` and comparing, see
[`resource/relational/jdbc.standalone`](../../resource/relational/jdbc.standalone/README.md).

### Settings of the export

Besides the [connection settings](#connection), `selection` (what is written, everything without
rules) and `skip`:

| Setting | Property | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `modelFile` | `cwm-jdbc.modelFile` | file | — (required) | The CWM XMI with the relational catalog; files it refers to are loaded with it. |
| `catalogName` | `cwm-jdbc.catalogName` | string | the one catalog of the file | Which catalog of the file is written; needed where the file holds several. |
| `extraPackages` | — | list | — | Interfaces of further generated EPackages the model uses; their jars as plugin dependencies. |
| `includeSchema` | `cwm-jdbc.includeSchema` | boolean | `true` | Qualify tables with their schema; off to write one schema into the schema the connection selects. |
| `createSchema` | `cwm-jdbc.createSchema` | boolean | `true` | Write `CREATE SCHEMA` (and drop it with `dropFirst`); off where the schemas exist. |
| `ifNotExists` | `cwm-jdbc.ifNotExists` | boolean | `true` | `CREATE … IF NOT EXISTS` where the dialect has it. The drop script is written with `IF EXISTS` either way. |
| `cascade` | `cwm-jdbc.cascade` | boolean | `false` | `CASCADE` on the tables and schemas `dropFirst` drops, where the dialect has it. |
| `commentType` | `cwm-jdbc.commentType` | string | `JDBC-REMARKS` | The type of the descriptions written as table and column comments; leave comments out with `!is:comment`. |
| `strict` | `cwm-jdbc.strict` | boolean | `false` | Fail before anything runs where the dialect cannot write what the model holds as the model has it, naming every case; `false` writes it plainer or leaves it out and logs each case as a warning. |
| `dropFirst` | `cwm-jdbc.dropFirst` | boolean | `false` | Drop first what the model creates, each statement only where the database holds its element. |
| `dialect` | `cwm-jdbc.dialect` | string | the database's | `ansi`, `postgresql`, `mysql`, `mariadb`, `oracle`, `sqlserver`, `h2`, `duckdb` or an alias. |

`includeSchema`, `createSchema`, `ifNotExists`, `cascade` and `strict` mean what they mean for
`cwm-ddl:generate`.

## Selection rules

The rules and their words are those of every plugin of this repository; they are described on
the shared page [Selection rules](../README.md#selection-rules). What is particular to reading:

- Names are written as the database reports them (`PUBLIC` in H2, `public` in PostgreSQL).
- A kind an exclude rule leaves out as a whole (`!is:index`, `!is:trigger`, `!is:privilege`, ...)
  is not read at all, and a schema or table an exclude rule matches is left out before anything
  in it is read.
- Without include rules the roles, users and privileges are read; `schema:sales` alone reads no
  party, add `is:role` (or `is:user`) for them. Oracle, PostgreSQL and SQL Server report privileges
  fully, MySQL and MariaDB in part; a user that may not read the privilege catalog gets none.
- What the database does not name (a primary key, a unique or check constraint, a foreign key) is
  named as the DDL generator names it: `pk_`, `uc_`, `ck_`, `fk_` and the table name, numbered
  from the second on (`RelationalNames`), so a catalog written as DDL and read back keeps its names.
- An `INSTEAD OF` trigger is not read: CWM knows triggers before and after a change only. The log
  names it.

## The JDBC driver

The driver is found in this order:

1. `driverClass`, when set, loaded from the plugin's class path plus the jars of `drivers`;
2. otherwise the first JDBC driver there that accepts the URL (drivers announce themselves through
   `META-INF/services/java.sql.Driver`, all current ones do);
3. finally `java.sql.DriverManager`.

In a POM the driver is a `<dependency>` of the `<plugin>`, nothing else is needed. On the command
line a plugin cannot get dependencies; there `drivers` names the driver by its coordinates, and
Maven resolves it (and what it depends on) from the local repository or the remote ones, as for
any dependency:

```sh
-Dcwm-jdbc.drivers=org.postgresql:postgresql:42.7.4
```

Several drivers, or a driver and a jar it needs at run time, are separated by commas.

## Credentials

The explicit `user`/`password` win; what they leave open comes from the `settings.xml` server
named by `serverId`:

```xml
<!-- ~/.m2/settings.xml -->
<servers>
  <server>
    <id>shop-db</id>
    <username>reader</username>
    <password>{COQLCE6DU6GtcS5P=}</password>   <!-- mvn --encrypt-password -->
  </server>
</servers>
```

Avoid `-Dcwm-jdbc.password` on shared machines: the command line shows up in process lists and
shell histories.

## Recipes

### One schema, without staging tables

```xml
<selection>
  <rule>schema:dwh</rule>
  <rule>!table:tmp_*</rule>
  <rule>!table:*_old</rule>
  <rule>!table:*_bak</rule>
</selection>
```

### Only the star schema

```xml
<selection>
  <rule>table:dwh.fact_*</rule>
  <rule>table:dwh.dim_*</rule>
  <rule>!is:trigger</rule>
</selection>
```

### Structure without the rights

```xml
<selection>
  <rule>!is:privilege</rule>
  <rule>!kind:ResponsibleParty</rule>
  <rule>!is:system</rule>
</selection>
```

### Keep the model in the sources

Write into `src/main/cwm` and commit the file: a later import shows the changes of the database as
a diff of the XMI, element by element, since the ids follow the names.

```xml
<outputDirectory>${basedir}/src/main/cwm</outputDirectory>
<fileName>database.xmi</fileName>
```

### DDL for another database

The written catalog is the input of [`cwm-ddl:generate`](../ddl/README.md), which writes it as
DDL for another dialect:

```sh
mvn …maven.jdbc:0.0.1-SNAPSHOT:import -Dcwm-jdbc.url=jdbc:mysql://db/shop -Dcwm-jdbc.serverId=shop \
    -Dcwm-jdbc.drivers=com.mysql:mysql-connector-j:9.1.0
mvn …maven.ddl:0.0.1-SNAPSHOT:generate -Dcwm-ddl.modelFile=target/generated-cwm/shop.xmi \
    -Dcwm-ddl.dialects=postgresql
```

### A DuckDB file

```sh
mvn …maven.jdbc:0.0.1-SNAPSHOT:import -Dcwm-jdbc.url=jdbc:duckdb:/data/warehouse.duckdb \
    -Dcwm-jdbc.drivers=org.duckdb:duckdb_jdbc:2.0.0.1 -Dcwm-jdbc.catalogName=warehouse
```

A remote DuckDB server (the `quack:` protocol) is no JDBC URL: it is attached by the DuckDB
`DataSource` of `org.eclipse.daanse.jdbc.datasource.duckdb`, which
`CwmJdbcImport.load(DataSource, …)` takes (see [Without Maven](#without-maven)).

## Messages and failures

| Situation | Result |
| --- | --- |
| `url` missing | build fails: `url is required` |
| a rule with an unknown selector or `is:` name | build fails, naming the rule and the known words |
| an include rule that matches nothing, e.g. a mistyped schema | build fails: `This rule matches nothing in <catalog>: schema:salse` |
| `serverId` names no `<server>` | build fails |
| a `drivers` entry is no `groupId:artifactId:version` | build fails |
| a driver of `drivers` cannot be resolved | build fails with the resolver's message |
| `driverClass` cannot be loaded | build error: `JDBC driver … is not available` |
| no driver accepts the URL, wrong credentials, database down | build error with the JDBC message |
| the XMI cannot be written | build error |
| export: `modelFile` missing | build fails: `modelFile does not exist` |
| export: the file holds no catalog, or several and `catalogName` names none of them | build fails, naming the catalogs of the file |
| export: an unknown `dialect`, or a database no dialect is known for | build fails, naming the known ids |
| export: a statement the database rejects | build fails: `statement <n> of the CREATE script, for <kind> <element>, failed: <statement> — <message> (<what remains>)` |
| export: `strict` and the dialect cannot write what the model holds | build fails before anything runs, naming every case: `<element>: <dialect> cannot write <what> (not strict: <what is written instead>)` |

On success the import logs the catalog name, the URL and the written file; the export the catalog,
the URL, the dialect, what the selection left out and retained, and how many statements ran and
were skipped.

## Troubleshooting

| Symptom | Cause and remedy |
| --- | --- |
| `No suitable driver found for jdbc:…` | The driver is not on the plugin's class path: add it as plugin dependency or in `drivers`, or set `driverClass`. |
| A rule matches nothing | Names must match the database's spelling (`PUBLIC` vs `public`); a pattern is a glob, not a regular expression. |
| No roles or privileges in the catalog | An include rule on schemas does not reach them; add `is:role` (or `is:user`). |
| Keys or indexes are missing | The user lacks the rights to read the system catalog, or the database's metadata provider does not report them (see [What you get](#what-you-get)). |
| The build hangs at the import | The database is not reachable; most drivers take a connect timeout in the URL (e.g. `?connectTimeout=10` for PostgreSQL). |
| The export fails at a `CREATE` of something that is there | It was written before without `dropFirst`; set `dropFirst`, or drop it by hand where the model no longer holds it. |

## Without Maven

The import is a thin front end of `CwmJdbcImport` in
[`resource/relational/jdbc.standalone`](../../resource/relational/jdbc.standalone/README.md) and of `CwmPackages.save` in
[`emf/standalone`](../../emf/standalone/README.md); a Java program does the same in three calls:

```java
JdbcToCwmConfig config = JdbcToCwmConfig.defaults().withCatalogName("shop")
        .withSelection(CwmJdbcImport.selection(List.of("schema:sales", "!table:tmp_*", "is:role")));
Catalog catalog = CwmJdbcImport.load("jdbc:postgresql://localhost:5432/shop", "reader", "secret",
        null, getClass().getClassLoader(), config);              // or load(dataSource, config)
CwmPackages.save(Path.of("target/generated-cwm", CwmJdbcImport.fileName(catalog)),   // shop.xmi
        List.of(catalog));
```

The export is a thin front end of `CwmJdbcExport` in the same module:

```java
Catalog model = (Catalog) CwmPackages.loadModel(Path.of("src/main/cwm/shop.xmi"),
        getClass().getClassLoader(), List.of()).getContents().get(0);
Exported done = CwmJdbcExport.write("jdbc:postgresql://localhost:5432/shop", "owner", "secret", null,
        getClass().getClassLoader(), model, CwmJdbcExport.selection(List.of("schema:sales", "is:role")),
        ExportSettings.defaults().withDropFirst(true));
done.executed();   // the statements that ran; done.skipped(), done.selected().leftOut(), ...
```

The same selection can be built in code ([Selection rules](../README.md#selection-rules)). What
the goals add is Maven's: the credentials of a `settings.xml` server and the drivers resolved from
coordinates; see the [README](../../resource/relational/jdbc.standalone/README.md) of the module
for both directions.

## Tests of this module

`ImportJdbcMojoTest` runs the goal in JUnit — the Mojo instantiated, its parameters set as a POM or the
command line sets them — against an in-memory H2 database with the schemas `SHOP` and `OTHER`:

- `selection` `schema:SHOP`, `!table:*_OLD`, `catalogName=shop` → `shop.xmi` with the ids
  `Table_shop.SHOP.CUSTOMER` and `Column_shop.SHOP.CUSTOMER.NAME`, without the excluded table and
  the other schema; the file loads again;
- `table:SHOP.ORDERS` with `!is:primaryKey` reads that table alone, without its key;
- an include rule that matches nothing (`schema:shop` for `SHOP`) and an unknown selector fail
  the goal;
- user and password from a `settings.xml` server and a chosen `fileName`; an unknown `serverId`
  fails;
- an unreachable database fails, `skip` writes nothing;
- `drivers` given as coordinates are handed to Maven's resolver and their jars used; a malformed
  coordinate fails.

`ExportJdbcMojoTest` runs the export the same way: a model built in code is saved as XMI and
written into in-memory H2 databases, which are then asked what they hold:

- `table:SHOP.ORDERS` with `!is:index` writes the orders alone: not the customers, nor the foreign
  key to them, which goes with them, nor the index, the other tables or the other schema; the log
  names what the selection left out and the SQL type it retained, each with its reason, the dialect
  and the number of statements that ran;
- with `dropFirst` the goal runs twice in a row: the first time the drop script is skipped, as
  the database is empty, the second time none of it; the comment of a table arrives;
- a check the database rejects fails the build with the statement, its position in the
  `CREATE` script and its element;
- `includeSchema=false`, `createSchema=false`, another `commentType` and a named `dialect` write
  into the connection's schema without the comments; an unknown dialect fails;
- an include rule that matches nothing and an unknown selector fail before anything runs;
- a file with two catalogs needs `catalogName`; an unknown one and a missing file fail;
- credentials from a `settings.xml` server; an unreachable database fails, `skip` does nothing.

The core's tests (`resource/relational/jdbc.standalone`) cover connecting through a class loader,
loading by URL and from a `DataSource`, roles and privileges as the rules select them (PostgreSQL),
an include rule that matches nothing, the rule words and the file name of the catalog's XMI, and
the export and its round trip on H2, DuckDB and PostgreSQL, also of a model that gained
constraints since its last export;
those of `resource/relational/load.jdbc` the selection on snapshots built in code (what is read
equals the whole catalog cut to the same rules, excluded kinds are not read), the catalog owning
one SQL type per distinct type and its triggers, and the round trip DDL → database → CWM on H2,
PostgreSQL and Oracle.
