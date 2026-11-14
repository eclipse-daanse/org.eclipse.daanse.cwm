# Relational DDL from a CWM model — Maven plugin `cwm-ddl`

`org.eclipse.daanse:org.eclipse.daanse.cwm.maven.ddl` writes the SQL DDL of a CWM XMI
(`relational::Catalog`, `Schema`, `Table`, `View`, keys, indexes, triggers, comments, and the roles
and grants of the privilege model): one `CREATE` script per SQL dialect, and on request the
matching `DROP` script. Rules say which part of the model is written. The plugin has one goal,
**`cwm-ddl:generate`**, bound by default to the phase `generate-resources`.

The statements come from the relational DDL generator
([`resource/relational/ddl`](../../resource/relational/ddl)) through its offline front end
[`resource/relational/ddl.render`](../../resource/relational/ddl.render), the plugin's only
dependency. This page is about using the plugin.

- [Requirements](#requirements)
- [Quick start](#quick-start)
- [What you get](#what-you-get)
- [All settings](#all-settings)
- [Selection rules](#selection-rules)
- [Dialects](#dialects)
- [What is not written](#what-is-not-written)
- [Recipes](#recipes)
- [Messages and failures](#messages-and-failures)
- [Troubleshooting](#troubleshooting)
- [Tests of this module](#tests-of-this-module)

## Requirements

| What | Version / value |
| --- | --- |
| Java | 21 or newer (the plugin is compiled for Java 21) |
| Maven | 3.9 or newer (built against the 3.9 plugin API; this repository builds with Maven 4) |
| Plugin coordinates | `org.eclipse.daanse:org.eclipse.daanse.cwm.maven.ddl:0.0.1-SNAPSHOT` |
| Where it comes from | built from this repository: `mvn install -pl maven/ddl -am` puts it and its core into the local repository |
| Dependencies | only its core `org.eclipse.daanse.cwm.resource.relational.ddl.render` (plus the Maven API) |
| Database | none: the dialects are built offline, no JDBC connection is opened |

Every CWM 1.1 package and the Daanse ORM package are on the plugin's class path and registered
before the model is loaded, so the relational part may sit in a larger model (with class model,
mapping, OLAP and so on). A model that uses further metamodels needs
[`extraPackages`](#a-model-with-further-metamodels).

## Quick start

```xml
<plugin>
  <groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.cwm.maven.ddl</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <executions>
    <execution>
      <goals>
        <goal>generate</goal>
      </goals>
      <configuration>
        <modelFile>${basedir}/src/main/cwm/catalog.xmi</modelFile>
        <dialects>
          <dialect>postgresql</dialect>
          <dialect>h2</dialect>
        </dialects>
        <selection>
          <rule>schema:sales</rule>
          <rule>!is:documentary</rule>
        </selection>
        <drop>true</drop>
      </configuration>
    </execution>
  </executions>
</plugin>
```

`mvn generate-resources` writes the schema `sales` without what is documentation only as
`catalog-postgresql.sql`, `catalog-h2.sql`, `catalog-postgresql-drop.sql` and
`catalog-h2-drop.sql` to `target/generated-resources/ddl`, and the directory is added as resource
directory, so the scripts land in the jar. Without `<selection>` the whole model is written.

From the command line, without touching the POM:

```sh
mvn org.eclipse.daanse:org.eclipse.daanse.cwm.maven.ddl:0.0.1-SNAPSHOT:generate \
    -Dcwm-ddl.modelFile=src/main/cwm/catalog.xmi \
    -Dcwm-ddl.dialects=postgresql,oracle,sqlserver
```

## What you get

| File | Content |
| --- | --- |
| `<scriptName>-<dialect>.sql` | the `CREATE` script of the selected part of the model for that dialect |
| `<scriptName>-<dialect>-drop.sql` | with `drop`: the `DROP` script, the exact reverse of the `CREATE` script |

`<scriptName>` defaults to the model file's name without extension, `<dialect>` is the dialect's
id, also for an alias (`pg` writes `…-postgresql.sql`). A script starts with a comment line naming
the dialect, the schemas (`catalog.schema`) and the model file, and with rules a second one naming
them; there is no timestamp, so a rebuild writes the same bytes.

One script covers all written schemas, in dependency order, so it runs from top to bottom also
where a foreign key or a view refers into a schema the model lists later:

1. `CREATE SCHEMA` for every schema
2. `CREATE TABLE` for every table, with the primary key inline
3. table and column comments, from the descriptions of type `JDBC-REMARKS` (the type
   [`cwm-jdbc:import`](../jdbc/README.md) gives the database's comments)
4. unique and check constraints
5. `CREATE INDEX`, a column `DESC` where the model sorts it descending (`isAscending = false`)
   and the dialect keeps the order, with `WHERE` for an index with a `filterCondition` where the
   dialect has partial indexes (PostgreSQL, SQL Server)
6. foreign keys, added to the existing tables with `ALTER TABLE … ADD`, `DEFERRABLE INITIALLY
   DEFERRED` or `INITIALLY IMMEDIATE` as the model says where the dialect has deferrable keys
   (PostgreSQL, Oracle)
7. `CREATE VIEW`, each view after the views it reads: by the model's `core::Dependency` between
   the views where it has them, otherwise by the names of the other views in its query; a view
   tagged `materialized=true` as `CREATE MATERIALIZED VIEW` where the dialect writes one
   (PostgreSQL, Oracle), dropped with `DROP MATERIALIZED VIEW`; then the comments on the views and
   their columns, where the dialect keeps comments on views
8. `CREATE FUNCTION` and `CREATE PROCEDURE` for the routines whose body is in a language the
   dialect writes routines in (`plpgsql` and `sql` for PostgreSQL, `plsql` for Oracle, `tsql` for
   SQL Server, `sql` for MySQL and MariaDB; the language of the body's expression, `sql` where it
   names none): the parameters with their direction and their types by the names of the types,
   with the size the type gives, as a database tells overloads apart by them (`text` and `varchar`
   name two routines in PostgreSQL), the type a function returns, the body as it is. A grant on
   one of several overloads names it by the same types. The drop script drops a routine by its
   name and the types of its parameters, which name one of several of that name.
   The routines come after the tables and views in a fixed order, without looking into what calls
   what: a view or a column default that calls a function of the model is created before the
   function and fails; create such a function in a script of its own first.
9. triggers, each with a function of its own where the dialect keeps the body apart
   (`<trigger>_fn`); a routine of that name in the model comes with its trigger only
10. `CREATE ROLE` for the roles in the model (parties marked `databaseRole`, model
   [`resource.relational.privilege`](../../model/daanse/resource.relational.privilege)),
   `CREATE USER` for its users (parties marked `databaseUser`) where the dialect has users, and
   `GRANT` for the privileges and role memberships of its parties. A user is created without
   credentials: it exists and receives its roles and privileges, and logs in once an administrator
   gives it a way to. PostgreSQL makes it a role that may log in, without a password; Oracle from
   18 `NO AUTHENTICATION`; SQL Server `WITHOUT LOGIN`; MySQL and MariaDB lock the account; H2
   takes a hash no password has. DuckDB and the ANSI dialect have no users: those are left out
   ([What is not written](#what-is-not-written))

Where a dialect cannot add a constraint to an existing table, `CREATE TABLE` declares it, and a
table comes after the tables it refers to, across all schemas; tables that refer to each other in
a cycle then fail the goal, naming them. Keys, constraints, indexes and triggers without a name
get one: `pk_`, `uc_`, `ck_`, `fk_`, `idx_` or `trg_` and the table name, numbered from the second
on where the name is taken in the schema (`RelationalNames`, the same rule
[`cwm-jdbc:import`](../jdbc/README.md) names by). Each statement ends with the
[`delimiter`](#output) and a blank line. The `DROP` script undoes the `CREATE` script statement by
statement, backwards: `REVOKE` and `DROP ROLE` first, `DROP SCHEMA` last.

## All settings

Every setting is an element inside `<configuration>`; those with a property can also be set on the
command line as `-D<property>=<value>` (lists comma-separated).

### Input

| Setting | Property | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `modelFile` | `cwm-ddl.modelFile` | file | — (required) | The CWM XMI with the relational catalogs and schemas. Files it references by relative `href` load with it. |
| `selection` | `cwm-ddl.selection` | list of rules | everything | What of the model is written, one [rule](../README.md#selection-rules) per `<rule>` element (on the command line comma-separated): `schema:LIB`, `!is:index`, `!is:privilege`, ... An include rule that matches nothing fails the build. |
| `extraPackages` | — | list | — | Interfaces of further generated EPackages the model uses, e.g. `org.eclipse.daanse.rolap.mapping.model.RolapMappingPackage`; their jars come as `<dependencies>` of the plugin. |

### Dialects and statements

| Setting | Property | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `dialects` | `cwm-ddl.dialects` | list | `ansi` | One script per dialect, see [Dialects](#dialects). An unknown id fails the build and lists the known ones. |
| `includeSchema` | `cwm-ddl.includeSchema` | boolean | `true` | Qualify tables, views, indexes, triggers and grant targets with their schema (`"LIB"."BOOK"`). `false` writes bare names where the connection selects the schema: one schema per script (several fail the build), no `CREATE SCHEMA`; a table in another schema that a foreign key refers to stays qualified. The catalog is never written. |
| `createSchema` | `cwm-ddl.createSchema` | boolean | `true` | Write `CREATE SCHEMA` (and `DROP SCHEMA` in the drop script). `false` where the schemas exist already, as an Oracle schema, which is a user. |
| `ifNotExists` | `cwm-ddl.ifNotExists` | boolean | `true` | `CREATE … IF NOT EXISTS` for schemas, tables and indexes and `DROP … IF EXISTS` where the dialect has them. |
| `cascade` | `cwm-ddl.cascade` | boolean | `false` | `CASCADE` on dropped tables and schemas where the dialect has it. |
| `strict` | `cwm-ddl.strict` | boolean | `false` | Fail where a dialect cannot write what the model holds as the model has it, naming every case, and write no script; `false` writes it plainer or leaves it out and logs each case as a warning. See [What is not written](#what-is-not-written). |
| `drop` | `cwm-ddl.drop` | boolean | `false` | Also write the `DROP` script. |

### Output

| Setting | Property | Type | Default | Meaning |
| --- | --- | --- | --- | --- |
| `outputDirectory` | `cwm-ddl.outputDirectory` | directory | `${project.build.directory}/generated-resources/ddl` | Where the scripts are written. |
| `scriptName` | `cwm-ddl.scriptName` | string | model file name without extension | First part of every script name. |
| `delimiter` | `cwm-ddl.delimiter` | string | `;` | Written after every statement (not twice when the statement already ends with it). An empty value writes the bare statements, for tools that take one statement at a time. |
| `attachResource` | `cwm-ddl.attachResource` | boolean | `true` | Add `outputDirectory` as resource directory, so the scripts are packaged. |
| `skip` | `cwm-ddl.skip` | boolean | `false` | Do nothing, e.g. `-Dcwm-ddl.skip` for one build. |

## Dialects

| Id (aliases) | Dialect | Notes |
| --- | --- | --- |
| `ansi` (`sql99`) | ANSI SQL | standard types, double-quoted identifiers |
| `postgresql` (`postgres`, `pg`) | PostgreSQL | |
| `mysql` | MySQL | backtick identifiers |
| `mariadb` | MariaDB | backtick identifiers |
| `oracle` | Oracle | |
| `sqlserver` (`mssql`, `mssqlserver`) | Microsoft SQL Server | identifiers in double quotes (needs `QUOTED_IDENTIFIER ON`, the default) |
| `h2` | H2 | |
| `duckdb` | DuckDB | DuckDB cannot add a foreign key or check to an existing table, nor a unique constraint another table refers to: these go into `CREATE TABLE`, after the tables they refer to, across all schemas; tables in a reference cycle cannot be created. A foreign key into another schema fails when the script runs. Of delete and update rules only `NO ACTION` and `RESTRICT` are written |

The dialects decide type names (a CWM `VARCHAR(100)` column, `BIGINT`, `TIMESTAMP` …), identifier
quoting and which optional clauses (`IF NOT EXISTS`, `CASCADE`) they write. Ids are
case-insensitive. A `GRANT EXECUTE` names a routine by its name; where the model holds overloads of
it (several of one name in one schema, which only PostgreSQL among these keeps), the types of its
parameters follow the name, `"s"."f"(integer)`, so the grant reaches the one meant.

## Selection rules

The rules are those of every plugin of this repository, described on the shared page
[Selection rules](../README.md#selection-rules); [`cwm-jdbc:import`](../jdbc/README.md) reads a
database with the same ones. What is particular to writing:

- The rules are applied to a copy of the model; the model file is not changed. What the selection
  leaves out is counted in the log, and listed with `mvn -X`.
- An include rule on a schema does not reach the roles: `schema:sales` writes no role and no grant,
  `schema:sales` with `is:role` writes the roles with their grants on what is written of `sales`.
- What loses what it needs goes with it: a foreign key whose referenced table is left out, a grant
  on a table left out, a membership in a role left out.
- A view refers to its tables only in its query text, so it stays when they are left out; leave it
  out too (`!view:...`), or model the need as a `core::Dependency`.

## What is not written

The model can say more than the dialects of the Daanse SQL library can write. One rule holds for
all of it, in the create and the drop script and in a migration alike: by default what a dialect
cannot write is written plainer or left out, and the build log names each case as a warning,

```text
[WARNING] db=duckdb — index LIBRARYDB.LIB.IX_BOOK_PUBLISHER: cannot write DESC on column PUBLISHER_ID; written ascending
```

With [`strict`](#dialects-and-statements) the goal fails instead, before any script is written,
and names every case of the run with its element, what the dialect cannot write and the dialect:

```text
strict: the dialect cannot write what the model holds, in 1 case:
  index LIBRARYDB.LIB.IX_BOOK_PUBLISHER: duckdb cannot write DESC on column PUBLISHER_ID (not strict: written ascending)
```

| In the model | Not strict, written as | Why |
| --- | --- | --- |
| a materialized view (tagged `materialized=true`), except for PostgreSQL and Oracle | an ordinary `CREATE VIEW` | the database has no materialized views |
| the comment on a materialized view or its columns, for PostgreSQL and Oracle | left out | the dialect API has no `COMMENT ON MATERIALIZED VIEW` |
| an index with a `filterCondition` (a partial index), except for PostgreSQL and SQL Server | left out, not written over all rows | the database has no partial indexes (DuckDB rejects `WHERE`); without the filter a unique index would hold for more rows than the model says |
| a descending index column (`isAscending = false`), for DuckDB and ANSI, MySQL before 8.0, MariaDB before 10.8 | ascending; the index holds the same rows, only a scan in its order differs | the database keeps no order in an index (DuckDB takes `DESC` and ignores it) |
| a deferrable foreign key (`deferrability` other than `notDeferrable`), except for PostgreSQL and Oracle | not deferrable, the key is checked at once | the database checks every key at once (H2 rejects `DEFERRABLE`) |
| the comment on a view or its columns, for MySQL and MariaDB | left out | the database keeps no comment on a view; the others write the comments after the view, and a view a migration creates comes with them |
| the comment on a column of a view, for H2 | written, and H2 rejects it | the dialect says it writes comments on views; H2 keeps one on a view, but none on a column of a view: leave such comments out for H2 (`!is:comment`, or a selection without them) |
| an index, where the dialect has no index DDL (ClickHouse) | left out | |
| a user (party marked `databaseUser`), for DuckDB and ANSI | left out | the dialect has no users |
| a row trigger (`actionOrientation = ROW`), for SQL Server | a statement trigger | the dialect writes no `FOR EACH ROW`, and a SQL Server trigger fires once per statement |
| a `BEFORE` trigger, for SQL Server | an `AFTER` trigger | SQL Server has `AFTER` and `INSTEAD OF` triggers only |
| the `WHEN` condition of a trigger, for SQL Server, MySQL and MariaDB | left out, the trigger fires for every row | the dialect writes no condition for a trigger |
| a function or procedure in a language the dialect writes no routines in (every routine for H2, DuckDB and ANSI) | left out | the dialect writes routines in its own languages only |
| a function the model gives no result type (a `RETURN` parameter), as one read from PostgreSQL | left out | the dialect needs the type a function returns; PostgreSQL's metadata do not report it |
| the comment on a function or procedure | left out | the dialect API has no `COMMENT ON FUNCTION` |

One more thing is no case of the rule, as it is never written:

| In the model | Written as | Why |
| --- | --- | --- |
| an `INSTEAD OF` trigger | cannot be modelled | CWM 1.1 knows triggers before and after a change of a table only; `cwm-jdbc:import` leaves such a trigger out with a warning |

A drop script is not idempotent: `REVOKE` and `DROP ROLE` have no `IF EXISTS`, nor have
`DROP CONSTRAINT` and `DROP INDEX` in MySQL, an `ALTER TABLE` that drops a constraint fails without
its table, and H2 fails a `DROP … IF EXISTS` without its schema, so on a database without the
objects such statements fail; run the file where the create script ran. Writing a model into a
database from Java with `CwmJdbcExport` and `dropFirst`
([`resource/relational/jdbc.standalone`](../../resource/relational/jdbc.standalone/README.md)), or
from a build with the goal [`cwm-jdbc:export`](../jdbc/README.md#writing-a-model-into-a-database-cwm-jdbcexport),
runs the same drop script idempotently: it reads first what the database holds of the model, the
schemas, tables and views with their keys, constraints, indexes and triggers, the roles and the
routines, and runs each statement only where the element it is written for is there, a constraint,
index or trigger under the name the statement gives it.

## Recipes

### Several schemas, one script per dialect

```xml
<selection>
  <rule>schema:DWH.CORE</rule>
  <rule>schema:DWH.MART</rule>
</selection>
```

The scripts hold both schemas in one dependency order, whatever their order in the model; the
`DROP` script is its exact reverse.

### Structure only, for a database that already has the schema

```xml
<selection>
  <rule>schema:LIB</rule>
  <rule>!is:index</rule>
  <rule>!is:trigger</rule>
  <rule>!is:comment</rule>
</selection>
<includeSchema>false</includeSchema>
```

Tables with their keys and constraints, bare names, for the schema the connection selects; no
roles, as the include rule on the schema does not reach them.

### Everything but the rights and what is documentation only

```xml
<selection>
  <rule>!is:privilege</rule>
  <rule>!is:role</rule>
  <rule>!is:documentary</rule>
</selection>
```

### Scripts for a migration tool

Write each dialect into the directory a tool such as Flyway or Liquibase reads, under a versioned
name:

```xml
<outputDirectory>${project.build.directory}/classes/db/migration</outputDirectory>
<scriptName>V1__library</scriptName>
<dialects><dialect>postgresql</dialect></dialects>
<ifNotExists>false</ifNotExists>
```

This writes `V1__library-postgresql.sql`.

### A model with further metamodels

A model that also holds, for example, a Daanse ROLAP mapping loads once its package is on the
plugin's class path and named:

```xml
<plugin>
  <groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.cwm.maven.ddl</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <dependencies>
    <dependency>
      <groupId>org.eclipse.daanse</groupId>
      <artifactId>org.eclipse.daanse.rolap.mapping.model</artifactId>
      <version>0.0.1-SNAPSHOT</version>
    </dependency>
  </dependencies>
  <configuration>
    <modelFile>${basedir}/src/main/cwm/warehouse.xmi</modelFile>
    <extraPackages>
      <extraPackage>org.eclipse.daanse.rolap.mapping.model.RolapMappingPackage</extraPackage>
    </extraPackages>
    <selection><rule>schema:dwh.mart</rule></selection>
  </configuration>
</plugin>
```

### Database to CWM and back

[`cwm-jdbc:import`](../jdbc/README.md) reads a live database into a CWM catalog XMI; this goal
turns such a file into DDL for another dialect — for example to move a schema from Oracle to
PostgreSQL (from Java, `CwmJdbcExport` writes it into the database directly):

```sh
mvn …maven.jdbc:0.0.1-SNAPSHOT:import -Dcwm-jdbc.url=jdbc:oracle:thin:@//db:1521/FREE \
    -Dcwm-jdbc.serverId=ora -Dcwm-jdbc.selection=schema:SHOP -Dcwm-jdbc.catalogName=shop
mvn …maven.ddl:0.0.1-SNAPSHOT:generate -Dcwm-ddl.modelFile=target/generated-cwm/shop.xmi \
    -Dcwm-ddl.dialects=postgresql
```

Whether the target database holds what the script describes is answered by reading it back with
`cwm-jdbc:import` and comparing the two catalogs with `CatalogDiffer` of
[`resource/relational/diff`](../../resource/relational/diff), with `Tolerance.roundTrip(dialect)`
for what the database spells its own way.

## Messages and failures

| Situation | Result |
| --- | --- |
| `modelFile` missing or not a file | build fails: `modelFile does not exist` |
| unknown dialect id | build fails, listing the known ids |
| a rule with an unknown selector or `is:` name | build fails, naming the rule and the known words |
| an include rule that matches nothing, e.g. a mistyped schema | build fails: `This rule matches nothing in <catalog>: schema:salse` |
| no relational schema in the model | build fails: `no relational schema in …` |
| `includeSchema=false` with more than one schema selected | build fails, naming the schemas |
| tables refer to each other in a cycle where the dialect declares foreign keys in `CREATE TABLE`, or views read each other in a cycle | build fails, naming them |
| `strict` and a dialect cannot write what the model holds | build fails, naming every case of every dialect; no script is written |
| a dialect cannot write what the model holds, not `strict` | warning per case: `db=<dialect> — <element>: cannot write <what>; <what is written instead>` |
| the model does not load (unknown package, broken `href`) | build error with the EMF message |

On success the goal logs how many elements the selection left out and one line per written script
with its statement count; `-X` lists what was left out, each with the rule or consequence that left
it out.

## Troubleshooting

| Symptom | Cause and remedy |
| --- | --- |
| `Package with uri '…' not found` | The model uses a metamodel outside CWM: add its jar as plugin dependency and its EPackage interface to `extraPackages`. |
| The script fails on a database because a table exists | Keep `ifNotExists=true`, or run the `DROP` script first. A constraint added with `ALTER TABLE` has no `IF NOT EXISTS`: a second run fails there. |
| A rule matches nothing | Names are written as the model spells them (`LIB`, not `lib`); a pattern is a glob, not a regular expression. |
| No `CREATE ROLE` or `GRANT` in the script | An include rule on a schema does not reach the parties; add `is:role`. |
| SQL Server rejects quoted names | Run the script with `QUOTED_IDENTIFIER ON` (the default of current drivers and tools). |

## Without Maven

The goal is a thin front end of `CwmDdlScripts` in
[`resource/relational/ddl.render`](../../resource/relational/ddl.render); a Java program writes the
same scripts with the same names, and gets their statements back to run them:

```java
Resource model = CwmPackages.loadModel(Path.of("src/main/cwm/catalog.xmi"), getClass().getClassLoader(), null);
CwmDdlScripts scripts = new CwmDdlScripts(List.of("postgresql", "h2"), DdlSettings.defaults(), ";");
Written<ModelElement> written = scripts.write(model, CwmDdlScripts.selection(List.of("schema:LIB")),
        Path.of("target/ddl"), null, true);
// catalog-postgresql.sql, catalog-h2.sql, catalog-postgresql-drop.sql, catalog-h2-drop.sql
List<String> create = written.scripts(Kind.CREATE).get(0).sql();      // the PostgreSQL statements
List<LeftOut> leftOut = written.selected().leftOut();                  // what the selection left out
```

`CwmDdlRenderer` below it cuts a model to a selection (`select`), gives the scripts of a model per
dialect (`scripts`), each statement with the element it is written for, and the statements of a
single table, view, index or constraint (`render`).

## Tests of this module

`GenerateDdlMojoTest` runs the goal in JUnit — the Mojo instantiated, its parameters set as a POM
sets them — on `src/test/resources/it/library` (the catalog of the many-to-one ORM tutorial:
schema `LIB` with `PUBLISHER`, `BOOK`, their primary keys, the foreign key and the index):

- PostgreSQL and H2 with `drop`: four scripts; the PostgreSQL one creates the referenced table
  before the foreign key; the H2 script runs against an in-memory H2 database (2 tables in `LIB`)
  and the drop script removes them again;
- `selection` `schema:LIBRARYDB.LIB`, `!is:foreignKey`, `!is:index`, `includeSchema=false`,
  `ifNotExists=false` and `scriptName` shape the statements, and the script names its rules;
- `createSchema=false` writes neither `CREATE SCHEMA` nor `DROP SCHEMA`;
- an unknown dialect, an unknown selector, an include rule that matches nothing and a missing model
  file fail the build with the known values or the rule in the message.

The core's tests (`resource/relational/ddl.render`) cover the selection of what is written, the
names and the first lines of the written scripts, the roles and grants with their `REVOKE`s, one
order over several schemas, the statements of one element, and run the written H2 script; those of
`resource/relational/ddl` the order, the names of unnamed elements, the settings and what is not
written, offline and against H2, DuckDB, PostgreSQL, MariaDB, MySQL, SQL Server and Oracle.
