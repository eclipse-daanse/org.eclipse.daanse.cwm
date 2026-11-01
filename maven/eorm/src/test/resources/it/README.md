# Test projects

Inputs of `GenerateEormMojoTest`, which runs the goal on them the way a build would.

| Project | Files | Content |
| --- | --- | --- |
| `library` | `src/main/cwm/objects.xmi`, `catalog.xmi`, `mapping.cwmorm` | the many-to-one ORM tutorial (bidirectional n:1 Book–Publisher, class package `library`) |
| `library` | `src/main/ecore/library.ecore` | the Ecore of `objects.xmi`, written by a CWM-to-Ecore conversion, CWM annotations stripped (a copy of the `fennec.eorm.core` test data) |

The `.editorconfig` here gives `*.cwmorm` the two-space indentation of the XMI files.
