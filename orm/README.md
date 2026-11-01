# org.eclipse.daanse.cwm.orm

Object/Relational mapping tooling on top of the CWM-ORM mapping model (`model/daanse/orm`).

| Module | Input | Output |
| --- | --- | --- |
| [`fennec.eorm.core`](fennec.eorm.core/README.md) | a **CWM-ORM** mapping set (`model/daanse/orm`, explicit profile) + the `.ecore` files of its class model | Fennec **EORM** `EntityMappings` (`.eorm`) next to the `.ecore` |
| [`../maven/eorm`](../maven/eorm/README.md) | CWM XMI + `.ecore` | `.eorm`, goal `fennec-eorm:generate`: one chosen mapping set, one `.eorm`, over one or several `.ecore` files — the README is the full user guide with all settings |

The Fennec core takes the `.ecore` files of the class model as input and finds classes and features
in them by names, so any Ecore model with the CWM names works. It needs the Fennec EORM model from
the Sonatype Central snapshot repository (see the core README).

```shell
mvn install -pl orm/fennec.eorm.core,maven/eorm -am
```
