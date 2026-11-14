# Examples

Programs that show how the libraries of this repository work together, in plain Java, without
OSGi and without the Maven plugins. Each example is a module that starts from the IDE and has
tests that run it.

| Module | Shows |
| --- | --- |
| [`jdbc.roundtrip`](jdbc.roundtrip/README.md) | a CWM relational model held in Java code → DDL → PostgreSQL (with roles, a user and privileges) and DuckDB (a part chosen by rules, in Java and as text) → read back into CWM → compared with what was written |
