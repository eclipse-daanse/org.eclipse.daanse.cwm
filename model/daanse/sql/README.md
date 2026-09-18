<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
SPDX-License-Identifier: EPL-2.0
-->
# daanse `sql` — semantisches Datenmodell für SQL-Anfragen

Ein dialekt-neutrales **Ecore-Metamodell** für SQL-SELECT-Anfragen, als eigene Erweiterung
des CWM-1.1-Metamodells. Es bildet eine Anfrage **inhaltlich/semantisch** ab — nicht eine
bestimmte SQL-Syntax. Die konkrete SQL-Form erzeugt später ein dialektspezifischer
Serializer (separates Modul).

## Leitideen

- **ANSI-SQL als Begriffsbasis** (ISO/IEC 9075): `QueryExpression` / `QueryExpressionBody` /
  `QuerySpecification`, `SetOperation` (UNION/INTERSECT/EXCEPT, inkl. CORRESPONDING),
  `TableReference`, `DerivedColumn`, `GroupingElement`, `SortSpecification` usw. Keine
  vendor-spezifischen Begriffe (kein LIMIT/TOP/MINUS; stattdessen OFFSET/FETCH, EXCEPT).
- **Vereinheitlichter Ausdrucksbaum:** eine Wurzel `Expression`; AND/OR/NOT und alle
  Prädikate sind Subtypen (ANSI: *search condition* = *boolean value expression* =
  *value expression*).
- **Semantischer `Join` (Überbegriff):** zwei Quellen + `joinType`
  (INNER/LEFT/RIGHT/FULL/CROSS) + allgemeine `condition`. Entkoppelt von der SQL-Schreibweise —
  der Serializer entscheidet FROM-`JOIN … ON` vs. Komma-Quellen + WHERE-Prädikat;
  NATURAL/USING sind reine Render-Optimierungen.
- **Anonyme Aliase:** `AliasDefinition` ist eine Identität mit optionalem Namen. Fehlt der
  Name, erzeugt ihn der Serializer. `AliasReference` verweist ausschließlich per Objekt
  (`target`).
- **CWM-Vererbung:** `SqlElement extends CWM ModelElement` (daher trägt jeder Knoten —
  auch `Join` — Name/Visibility und die mehrwertige `description`-Referenz);
  `QuerySpecification extends CWM ColumnSet`.

## Aufbau

- `model/sql.ecore` — das Metamodell (zwei EPackages: `sql` + Subpaket `select`).
- `src/test/java/.../build/SqlEcoreBuilder.java` — baut die `.ecore` programmatisch via
  `EcoreFactory` (lädt `cwm.ecore` für Cross-Referenzen).
- `src/main/java/.../util/SqlModelValidator.java` — programmatische Invarianten-Prüfung
  (spiegelt die deklarativen Pivot-OCL-Constraints; ohne OCL-Runtime auswertbar).
- `example/*.sql` — Beispiel-Instanzen (eine `all-features` plus je eine pro Konstrukt:
  `setop-union`/`-union-all`/`-except`/`-intersect`, `join-inner`/`-left`/`-right`/`-full`/`-cross`,
  `cte`/`cte-recursive`, `subselect-derived`/`-scalar`/`-in`/`-exists`, `function-in-join`,
  `where-count-order`). Gebaut von `SqlExamples`, validiert von `SqlExamplesTest`.
- Java-Code für das Modell wird zur Build-Zeit aus der `.ecore` generiert
  (`target/generated-sources/emf`).

## Workflows

`.ecore` aus dem Java-Builder neu erzeugen (bewusst gated):

`
mvn -pl model/daanse/sql test -Dtest=SqlEcoreBuilderTest -Dregenerate.ecore=true
git diff model/daanse/sql/model/sql.ecore
`

Beispiel-Instanzen neu schreiben (Satz unter `example/`):

`
mvn -pl model/daanse/sql test -Dtest=SqlExampleWriterTest -Dwrite.example=true
`

Bauen und testen (JDK 21):

`
mvn -pl model/daanse/sql clean install
`

## Hinweise

- Instanzen werden in Tests ausschließlich über die generierten Factories
  (`SqlFactory`/`SelectFactory`) gebaut und per XMI-Round-Trip geprüft.
- `INHERIT_CWM` (Konstante in `SqlEcoreBuilderTest`) schaltet die CWM-`eSuperTypes`; auf
  `false` fällt das Modell auf eine referenzbasierte Variante zurück.
- Erweiterte Gruppierung (ROLLUP/CUBE/GROUPING SETS) ist bewusst nicht enthalten (gilt als
  OLAP/multidimensional) und käme ggf. in einem separaten Paket.
