# Maven plugins

All Maven plugins of this repository live here, one module per plugin. A plugin is a thin Maven
front end: it turns the POM settings into calls of **one core library** and depends on nothing
else but that core and the Maven API. The cores stay where their subject is and can be used
without Maven, from Java, a CLI or OSGi.

| Module | Coordinates (`org.eclipse.daanse:…:0.0.1-SNAPSHOT`) | Goal | Default phase | Core | Does |
| --- | --- | --- | --- | --- | --- |
| [`ecore`](ecore/README.md) | `org.eclipse.daanse.cwm.maven.ecore` | `cwm-ecore:generate` | `generate-sources` | [`emf/ecore`](../emf/ecore/README.md) | CWM class model → `.ecore` (+ `.genmodel`), constraints and methods as OCL/Java |
| [`eorm`](eorm/README.md) | `org.eclipse.daanse.cwm.maven.eorm` | `fennec-eorm:generate` | `generate-resources` | [`orm/fennec.eorm.core`](../orm/fennec.eorm.core/README.md) | CWM-ORM mapping set → Fennec `.eorm` next to the `.ecore` |
| [`atlas`](atlas/README.md) | `org.eclipse.daanse.cwm.maven.atlas` | `fennec-atlas:generate` | `generate-resources` | [`sensinact/fennec.atlas.core`](../sensinact/fennec.atlas.core/README.md) | CWM sensiNact mappings with lifecycle rules → Fennec event.atlas provider mappings and profiles |
| [`ddl`](ddl/README.md) | `org.eclipse.daanse.cwm.maven.ddl` | `cwm-ddl:generate` | `generate-resources` | [`resource/relational/ddl.render`](../resource/relational/ddl.render) | CWM relational catalog → `CREATE`/`DROP` scripts, one per SQL dialect, what to write chosen by rules |
| [`jdbc`](jdbc/README.md) | `org.eclipse.daanse.cwm.maven.jdbc` | `cwm-jdbc:import` | `generate-resources` (meant for explicit runs) | [`resource/relational/jdbc.standalone`](../resource/relational/jdbc.standalone/README.md) | live database over JDBC → CWM relational catalog XMI with roles, users and privileges, what to read chosen by rules |
| [`jdbc`](jdbc/README.md#writing-a-model-into-a-database-cwm-jdbcexport) | `org.eclipse.daanse.cwm.maven.jdbc` | `cwm-jdbc:export` | `pre-integration-test` | [`resource/relational/jdbc.standalone`](../resource/relational/jdbc.standalone/README.md) | CWM relational catalog XMI → live database over JDBC, the DDL of its dialect run, with `dropFirst` again and again, what to write chosen by rules |

The cores that load or write CWM XMI share [`emf/standalone`](../emf/standalone/README.md):
`CwmPackages` registers every CWM and Daanse package by name, so any CWM XMI loads in every
plugin; `loadModel` loads a model with the files it refers to, and `save` writes XMI with ids
built from the name path.

## How the plugins fit together

```text
            cwm-jdbc:import                cwm-ddl:generate
 database ──────────────────▶ catalog.xmi ──────────────────▶ *.sql (per dialect)
          ◀──────────────────
            cwm-jdbc:export

 class model ── cwm-ecore:generate ──▶ *.ecore ── EMF codegen ──▶ Java
 (CWM XMI)                               │
                                         ▼
 mapping (CWM-ORM) ──────── fennec-eorm:generate ──▶ *.eorm  (Fennec Persistence)
 twin mapping (sensiNact) ─ fennec-atlas:generate ─▶ mappings/, profiles/  (Fennec event.atlas)
```

`cwm-ecore:generate` runs in `generate-sources`, `fennec-eorm:generate` and
`fennec-atlas:generate` in `generate-resources`, so in one build the `.ecore` files exist before
the `.eorm` and the provider mappings are written. `cwm-jdbc:import` reads a database and is
meant to be run on purpose; its catalog is the input of `cwm-ddl:generate` and of CWM-ORM
mappings. `cwm-jdbc:export` writes a catalog into a database: it runs over JDBC the statements
`cwm-ddl:generate` writes into its scripts, by `CwmJdbcExport` of
[`resource/relational/jdbc.standalone`](../resource/relational/jdbc.standalone/README.md), and runs
in `pre-integration-test`, where a build sets up the database of its integration tests. All three
choose their part of a catalog by the same [selection rules](#selection-rules).

Whether a database holds what was written is answered by reading it back as the export says
(`Exported.readBack()`) and comparing with `CatalogDiffer` of
[`resource/relational/diff`](../resource/relational/diff), with the tolerances
`Tolerance.roundTrip(dialect)` for what each database spells its own way.

## Selection rules

`cwm-jdbc:import` (what is read from a database), `cwm-ddl:generate` (what of a model is written
as DDL) and `cwm-jdbc:export` (what of a model is written into a database) take the same rules, in
the same words, as `<selection><rule>…</rule></selection>`:

```xml
<selection>
  <rule>schema:sales</rule>
  <rule>!table:tmp_*</rule>
  <rule>!is:documentary</rule>
</selection>
```

On the command line the rules are comma-separated: `-Dcwm-jdbc.selection=schema:sales,!is:index`.

A rule is `<selector>:<pattern>`; with a leading `!` it leaves out what it matches. Without rules
everything is taken. With include rules (those without `!`) only what one of them matches is
taken, together with what it holds; the schemas and the catalog stay as its containers. An exclude
rule wins over every include rule and reaches everything below what it matches. What a taken
element needs comes along (the SQL type of a column); what loses something it needs goes (a
foreign key whose table or key is left out, a key or index that loses a column, a privilege on a
table left out).

| Rule | Matches |
| --- | --- |
| `schema:sales`, `schema:shop.sales` | the schema of that name (in the catalog of that name) |
| `table:orders`, `table:sales.order*`, `table:shop.sales.orders` | the table of that name, with the indexes and triggers on it |
| `view:v_*`, `view:sales.v_orders` | the view of that name, materialized ones included |
| `name:tmp_*` | everything of that name, of any kind |
| `path:shop.sales.orders.qty` | the element of that dotted path; `*` within a segment, `**` over segments |
| `kind:Trigger` | elements of that CWM class or a subclass |
| `tag:materialized=true`, `tag:x` | elements with that tagged value |
| `stereotype:databaseUser` | elements with that stereotype |
| `is:table`, `is:view`, `is:system`, `is:temporary`, `is:materialized` | tables, views, system tables, temporary tables, materialized views |
| `is:primaryKey`, `is:unique`, `is:check`, `is:foreignKey` | the keys and constraints by kind; `is:unique` does not take the primary keys |
| `is:constraint` | unique constraints, checks and foreign keys, not the primary keys |
| `is:index`, `is:trigger`, `is:procedure` | indexes, triggers, procedures and functions |
| `is:comment` | the comments (descriptions) |
| `is:privilege`, `is:role`, `is:user` | privileges and memberships; roles; users |
| `is:documentary` | what carries `daanse.ddl.documentary=true`: relations that are documentation only |

Patterns are case-sensitive globs: `*` any run of characters, `?` one. A `#` after a blank starts
a comment. An unknown selector or `is:` name fails the goal and lists the known ones.

The roles and users are owned by the catalog, not by a schema: `schema:sales` alone takes no
party, `schema:sales` with `is:role` takes the roles with their privileges on what is taken of
`sales`. `!is:privilege` keeps the parties without their privileges; `!kind:ResponsibleParty`
takes none of them.

An include rule that matches nothing fails the goal, naming the rule: it is usually a mistyped
name. An exclude rule that matches nothing does not, so one selection serves a model and a
database that holds less of it. Each plugin's README says what is particular to its side.

In Java the same rules are parsed with `Selection.parse(PrivilegeRules.VOCABULARY, rules)` (or
`CwmJdbcImport.selection(rules)`, `CwmJdbcExport.selection(rules)`, `CwmDdlScripts.selection(rules)`),
or built in code:
`Selection.builder().include(RelationalRules.inSchema("sales")).exclude(RelationalRules.table("tmp_*"))
.include(PrivilegeRules.ROLES).build()`.

## Building

```sh
mvn install -pl maven/ecore,maven/eorm,maven/atlas,maven/ddl,maven/jdbc -am
```

Each module's tests run its goal in JUnit (the Mojo instantiated, its parameters set as a POM
sets them) on small projects under `src/test/resources/it` or, for `jdbc`, an in-memory database;
see the "Tests of this module" section of each README.

## Conventions for a plugin

- One module `maven/<name>`, artifact and Java package `org.eclipse.daanse.cwm.maven.<name>`,
  parent `org.eclipse.daanse.cwm.maven`; the Mojo class is `<Goal><Name>Mojo`
  (`GenerateEcoreMojo`, `ImportJdbcMojo`).
- Dependencies: the core (compile), `maven-plugin-api`, `maven-plugin-annotations` and
  `maven-core` (provided), test libraries. Anything the core needs at run time is the core's
  dependency, not the plugin's.
- The Mojo only maps settings to core calls, core messages to the Maven log and core exceptions
  to `MojoFailureException` (wrong input) or `MojoExecutionException` (I/O, unexpected). What a
  Java program would need too belongs into the core; what is Maven's (credentials from
  `settings.xml`, artifacts resolved from repositories, resource directories) stays in the Mojo.
- A model file is loaded with `CwmPackages.loadModel`, so every plugin reads the same CWM XMI the
  same way, `extraPackages` included.
- Properties are prefixed with the goal prefix (`cwm-ecore.genModel`); every goal has `skip`
  (`cwm-ecore.skip`); outputs go below `${project.build.directory}` and, with `attachResource`,
  into the jar; a rebuild writes the same bytes (no timestamps).
- The README is a user guide: requirements, quick start, what you get, every setting with
  property, type, default and meaning, recipes, messages, troubleshooting, tests.
