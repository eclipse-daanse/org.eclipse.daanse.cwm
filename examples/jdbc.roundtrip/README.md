# CWM → DDL → database → CWM

A CWM relational model held in Java code goes into a database and comes back out of it, and the
two are compared. On the way the program chooses what of the model a database gets, by rules
written in Java or as text, and reports what the rules left out and why. Every step is one call of
the libraries of this repository; plain Java, no OSGi, no Maven plugin, started from the IDE. From
a build, the goal [`cwm-jdbc:export`](../../maven/jdbc/README.md#writing-a-model-into-a-database-cwm-jdbcexport)
writes a model file into a database the same way.

## The classes

| Class | Does |
| --- | --- |
| `ShopModel` | `build()` gives the shop as a CWM catalog, made with the helpers of the model modules (`Catalogs`, `SQLSimpleTypes`, `Schemas`, `Tables`, `Columns`, `PrimaryKeys`, `UniqueConstraints`, `ForeignKeys`, `SQLIndexes`, `Views`, `Descriptions`, `TaggedValues`, `Expressions`, `PrivilegeStereotypes`). It holds more than a single database gets. |
| `CwmRoundTrip` | `roundTrip(model, target, selection, settings, directory, log)` does one round trip; `main` does three. A `Target` names a database (name, URL, user, password); a `Result` holds what the export wrote, the catalog read back and their `CatalogDiff`. `DUCKDB_PART` and `DUCKDB_RULES` are the rules of the DuckDB runs, in Java and as text. |

## The model

```text
shop
  sales                                          stock
    customer           PK, email UNIQUE,           product      PK
                       comments,                   warehouse    PK
                       referred_by → customer (d)  stock_level  PK (product_id, warehouse_id),
    orders             PK, → customer,                          → product, → warehouse
                       → stock.product, CHECK qty > 0,
                       index, trigger (PL/pgSQL)
    order_log          order_id → orders (d)
    v_top_customers    reads v_customer_orders
    v_customer_orders  reads customer and orders
    orders_of(who)     a function in PL/pgSQL: how many orders a customer placed
  role shop_reader     SELECT on customer, orders, order_log and stock.product, EXECUTE on orders_of
  role shop_writer     INSERT on orders WITH GRANT OPTION, UPDATE (qty) on orders,
                       member of shop_reader WITH ADMIN OPTION
  user shop_clerk      member of shop_writer, INSERT on order_log
```

`(d)` marks a foreign key that is documentation only: it carries the tagged value
`daanse.ddl.documentary = true` (`RelationalRules.DOCUMENTARY_TAG`), and `is:documentary` leaves it
out where a database shall not enforce it. Two things stand in an order a script could not run in,
on purpose: the schema `sales` comes before `stock`, which `orders` refers to, and
`v_top_customers` before the view it reads. The DDL generator orders the statements by what they
refer to, across all schemas: every table first, then the foreign keys, then the views, each after
the views it reads.

## One round trip

`CwmRoundTrip.roundTrip` takes the model, a `Target`, a `Selection` and `ExportSettings`, and
prints five steps:

1. **Cut.** `CwmJdbcExport.write(connection, model, selection, settings)` cuts a copy of the model
   to the selection; the original stays as it is. It prints what the cut left out and what it
   retained, each with its reason (see [below](#left-out-and-retained)).
2. **Write.** The same call writes the DDL of the copy in the dialect of the database
   (`Dialects.forConnection` finds it) and runs it. With `dropFirst` the drop script of the same
   model runs first, each statement only where the database holds what it drops, so the program
   runs again and again; where the database runs DDL in transactions (PostgreSQL, DuckDB) both
   scripts run in one. It prints how many statements ran and how many of the drop script were
   skipped. A statement the database rejects ends the run with a `StatementFailedException` that
   names it.
3. **Files.** `CwmDdlScripts.write` writes the create and the drop script of the same cut into the
   directory of the run, `shop-<dialect>.sql` and `shop-<dialect>-drop.sql`, and `CwmPackages.save`
   writes the cut model as `written.xmi`, to look at.
4. **Read back.** `CwmJdbcImport.load(connection, exported.readBack())` reads the database back
   into a catalog, saved as `read.xmi`. `readBack()` reads by the rules the model was cut by, and
   leaves out the schemas and the roles and users the written model does not hold: the schemas a
   database keeps for itself (`public`, `information_schema`, `main`), its login user and its
   predefined roles.
5. **Compare.** `CatalogDiffer.create().diff(written, readBack, DiffSettings.defaults()
   .withTolerances(Tolerance.roundTrip(dialect)))` compares the two in both directions, with the
   tolerances of the database (see [below](#tolerances)), and prints one line per difference or
   `no differences`.

What a dialect cannot write as the model has it is written plainer or left out, and the log names
each case as a warning (`[WARN] db=<dialect> — <element>: cannot write <what>; <instead>`; the
module logs through `slf4j-simple`, set to warnings by `src/main/resources/simplelogger.properties`).
With `DdlSettings.strict()` in the `ExportSettings` the export fails before anything runs instead.

## The three runs

`main` writes the same model three times:

1. **PostgreSQL, everything** (`Selection.all()`): both schemas, every key, constraint, index,
   view, the function and the trigger, the documentary foreign keys, the roles, the user's grants
   and the memberships.
2. **DuckDB, a part chosen in Java** (`DUCKDB_PART`), into the file
   `target/cwm-roundtrip/shop.duckdb`.
3. **DuckDB, the same part chosen as text** (`DUCKDB_RULES`, parsed by
   `CwmJdbcExport.selection`). It prints the rules first; the run then reports exactly what the
   second one did.

The part holds the function of `sales`, and DuckDB writes no routines: the export leaves it out,
the log says so, and the comparison names it as the one difference of the DuckDB runs.

The files of each run go to `target/cwm-roundtrip/postgresql`, `…/duckdb-java` and
`…/duckdb-text`.

## Choosing what is written

The DuckDB part, built in Java with `RelationalRules`, `ElementRules` and `PrivilegeRules`:

```java
Selection.builder()
        .include(RelationalRules.inSchema("sales"), RelationalRules.table("stock.product"))
        .exclude(RelationalRules.table("*_log"), RelationalRules.DOCUMENTARY, RelationalRules.CONSTRAINTS,
                ElementRules.kind(Trigger.class), PrivilegeRules.PRIVILEGES)
        .build();
```

and the same as text, one rule each, as `cwm-jdbc:import` and `cwm-ddl:generate` take them in
`<selection><rule>…</rule></selection>`:

```text
schema:sales           # the schema sales with all it holds
table:stock.product    # and the table product of stock
!table:*_log           # but no log table
!is:documentary        # nothing that is documentation only
!is:constraint         # no unique constraint, check or foreign key; primary keys stay
!kind:Trigger          # no trigger
!is:privilege          # no grant
```

A rule is `<selector>:<pattern>`; with a leading `!` it leaves out what it matches, and `#` after
a blank starts a comment. Three rules decide:

- An element is left out when it or one of its owners matches an exclude rule. Exclude wins.
- Otherwise it is taken when there is no include rule, or when it or one of its owners matches
  one: `schema:sales` takes the schema with everything in it.
- The owners of what is taken stay as its containers: `table:stock.product` keeps the schema
  `stock`, without its other tables.

What a taken element needs comes along, and what loses something it needs goes. The roles and the
user are owned by the catalog, not by a schema, so the include rules do not reach them: DuckDB,
which has none, gets no party and no grant; `!is:privilege` says so outright. DuckDB refuses a
foreign key into another schema, which `!is:constraint` leaves out with the other constraints. The
full table of selectors and `is:` names is on the shared page
[Selection rules](../../maven/README.md#selection-rules).

## Left out and retained

The result of a cut, `Selected`, lists in `leftOut()` every element the copy leaves out in its own
right, as `<kind> <qualified name> (<reason>)`; what it contains goes with it and is not listed.
The reason is the rule that left it out, as written (`table:*_log`, `is:constraint`);
`not included` where include rules exist and none reaches it; or a consequence, the reference the
element lost and the element it referred to, as for a foreign key whose key is left out. An exclude
rule is reported, where several match, in the order of the rules: `!is:documentary` comes before
`!is:constraint`, so the documentary foreign key of `customer` says `is:documentary`.

`retained()` lists what the copy holds although no rule selects it, because something that stays
needs it: the SQL types of the columns, which the catalog owns outside every schema
(`needed by shop.sales.customer.id.type`).

## Running it from the IDE

Run `CwmRoundTrip.main` with this module as working directory. DuckDB needs nothing. The first run
needs a PostgreSQL whose user may create roles; the export creates the roles and the user
`shop_clerk` the shop grants to, the user without credentials, a role that may log in once it is
given a password:

```shell
podman run --rm -d --name cwm-shop -p 5432:5432 -e POSTGRES_USER=shop -e POSTGRES_PASSWORD=shop postgres:16-alpine
```

(`docker run` takes the same arguments.) The program connects to
`jdbc:postgresql://localhost:5432/shop` as `shop` with password `shop`; the system properties
`shop.postgres.url`, `shop.postgres.user` and `shop.postgres.password`, or the environment
variables `SHOP_POSTGRES_URL`, `SHOP_POSTGRES_USER` and `SHOP_POSTGRES_PASSWORD`, name another
database. Where none answers, it prints the line above and stops. Before it creates anything it
drops the schemas `sales` and `stock`, the roles `shop_reader` and `shop_writer` and the user
`shop_clerk`: do not point it at a database you need. Stop the container with
`podman stop cwm-shop`.

The console of a first run into an empty database, shortened; a second run drops what the first
created (`ran 73 statements in postgresql, skipped 0 of the drop script`) and ends the same. The
two DuckDB runs write the same files; each names the function it leaves out twice, once as the
export writes the scripts it runs and once as the run writes the script files. DuckDB loads a native library, which Java warns about unless
the VM option `--enable-native-access=ALL-UNNAMED` is set.

```text
== postgresql jdbc:postgresql://localhost:5432/shop
rules: none, everything is selected
1. cut: 0 left out, 0 retained
2. ran 38 statements in postgresql, skipped 35 of the drop script
3. wrote target/cwm-roundtrip/postgresql: shop-postgresql.sql, shop-postgresql-drop.sql, written.xmi
4. read back the schemas [sales, stock] into read.xmi
5. no differences

== duckdb jdbc:duckdb:target/cwm-roundtrip/shop.duckdb
rules: Selection[schema:sales, table:stock.product, !table:*_log, !is:documentary, !is:constraint, !kind:Trigger, !is:privilege]
[WARN] db=duckdb — function shop.sales.orders_of: cannot write a function in plpgsql; left out
1. cut: 14 left out, 4 retained
   left out UniqueConstraint shop.sales.customer.uq_customer_email (is:constraint)
   left out ForeignKey shop.sales.customer.fk_customer_referred_by (is:documentary)
   left out ForeignKey shop.sales.orders.fk_orders_customer (is:constraint)
   ...
   left out Trigger shop.sales.trg_orders_ordered_on (kind:Trigger)
   left out Table shop.sales.order_log (table:*_log)
   left out Table shop.stock.warehouse (not included)
   left out Table shop.stock.stock_level (not included)
   left out Stereotype shop.databaseRole (not included)
   left out Stereotype shop.databaseUser (not included)
   ...
   left out ResponsibleParty shop.shop_clerk (not included)
   retained SQLSimpleType shop.INTEGER (needed by shop.sales.customer.id.type)
   ...
   retained SQLSimpleType shop.DECIMAL (needed by shop.stock.product.price.type)
2. ran 11 statements in duckdb, skipped 8 of the drop script
[WARN] db=duckdb — function shop.sales.orders_of: cannot write a function in plpgsql; left out
3. wrote target/cwm-roundtrip/duckdb-java: shop-duckdb.sql, shop-duckdb-drop.sql, written.xmi
4. read back the schemas [sales, stock] into read.xmi
5. 1 difference:
   function shop.sales.orders_of(INTEGER): only left

The rules of DuckDB as text:
  schema:sales           # the schema sales with all it holds
  ...
  !is:privilege          # no grant

== duckdb jdbc:duckdb:target/cwm-roundtrip/shop.duckdb
rules: Selection[schema:sales, table:stock.product, !table:*_log, !is:documentary, !is:constraint, !kind:Trigger, !is:privilege]
[WARN] db=duckdb — function shop.sales.orders_of: cannot write a function in plpgsql; left out
1. cut: 14 left out, 4 retained
   ... the same as above
2. ran 19 statements in duckdb, skipped 0 of the drop script
[WARN] db=duckdb — function shop.sales.orders_of: cannot write a function in plpgsql; left out
3. wrote target/cwm-roundtrip/duckdb-text: shop-duckdb.sql, shop-duckdb-drop.sql, written.xmi
4. read back the schemas [sales, stock] into read.xmi
5. 1 difference:
   function shop.sales.orders_of(INTEGER): only left
```

## Tolerances

A database keeps the structure, but spells some of it its own way. `Tolerance.roundTrip(dialect)`
of the `diff` module lets exactly that pass:

| Tolerance | PostgreSQL | DuckDB | Why |
| --- | --- | --- | --- |
| `TYPE_BY_JDBC_CODE` | yes | yes | type names come back as the database spells them: `INTEGER` as `int4`, `VARCHAR` as `CHARACTER VARYING` |
| `IGNORE_EXPRESSION_TEXT` | yes | yes | checks, view queries and trigger bodies come back as the database parsed them: `qty > 0` as `(qty > 0)` |
| `IGNORE_GRANTOR` | yes | yes | the model names no grantor, the database the user who granted |
| `IGNORE_EXTRA_PARTIES` | yes | yes | parties only the database holds; `readBack()` leaves them out already |
| `IGNORE_CHARACTER_LENGTH` | | yes | DuckDB's `VARCHAR` has no length |
| `IGNORE_PRIMARY_KEY_NAMES` | | yes | DuckDB names a primary key itself |
| `IGNORE_CONSTRAINT_NAMES`, `IGNORE_FK_RULES` | | yes | DuckDB names unique constraints, checks and foreign keys itself and does not keep the rules of a foreign key |

The Javadoc of `Tolerance` gives the sets of the other databases, H2, MariaDB, MySQL, SQL Server
and Oracle.

## What a database does not get

What the dialects cannot write is listed in
[What is not written](../../maven/ddl/README.md#what-is-not-written) of `cwm-ddl`: a materialized
view is written as a view, an index with a filter is not written, a descending index column is
written ascending, the deferrability of a foreign key is left out, and so are comments on views
except on SQL Server. DuckDB has no roles, no users and no triggers and refuses a foreign key into
another schema.

## Tests

| Test | Needs | Checks |
| --- | --- | --- |
| `PostgresRoundTripTest` | Docker or Podman (`postgres:16-alpine`); skipped without | the first run of `main`, twice: no differences, nothing left out; the documentary key, the function, the trigger, `UPDATE (qty)`, the user with its membership come back; `public` and the login user are not read |
| `DuckDbRoundTripTest` | nothing | the second and third run: the function, which DuckDB does not write, the one difference either way, the same `leftOut` for both, the expected entries (`table:*_log`, `is:documentary`, `is:constraint`, `kind:Trigger`, `not included`); `sales` and `stock.product` with their primary keys, the index and both views, no constraint or trigger |
| `SelectionAsTextTest` | nothing | `DUCKDB_RULES` cut the shop exactly as `DUCKDB_PART` does: the same elements, the same `leftOut` and `retained`, the same rules as text |
