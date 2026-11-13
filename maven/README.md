# Maven plugins

All Maven plugins of this repository live here, one module per plugin. A plugin is a thin Maven
front end: it turns the POM settings into calls of **one core library** and depends on nothing
else but that core and the Maven API. The cores stay where their subject is and can be used
without Maven, from Java, a CLI or OSGi.

| Module | Coordinates (`org.eclipse.daanse:…:0.0.1-SNAPSHOT`) | Goal | Default phase | Core | Does |
|---|---|---|---|---|---|
| [`ecore`](ecore/README.md) | `org.eclipse.daanse.cwm.maven.ecore` | `cwm-ecore:generate` | `generate-sources` | [`emf/ecore`](../emf/ecore/README.md) | CWM class model → `.ecore` (+ `.genmodel`), constraints and methods as OCL/Java |
| [`eorm`](eorm/README.md) | `org.eclipse.daanse.cwm.maven.eorm` | `fennec-eorm:generate` | `generate-resources` | [`orm/fennec.eorm.core`](../orm/fennec.eorm.core/README.md) | CWM-ORM mapping set → Fennec `.eorm` next to the `.ecore` |
| [`atlas`](atlas/README.md) | `org.eclipse.daanse.cwm.maven.atlas` | `fennec-atlas:generate` | `generate-resources` | [`sensinact/fennec.atlas.core`](../sensinact/fennec.atlas.core/README.md) | CWM sensiNact mappings with lifecycle rules → Fennec event.atlas provider mappings and profiles |

The cores that load or write CWM XMI share [`emf/standalone`](../emf/standalone/README.md):
`CwmPackages` registers every CWM and Daanse package by name, so any CWM XMI loads in every
plugin; `loadModel` loads a model with the files it refers to, and `save` writes XMI with ids
built from the name path.

## How the plugins fit together

```
 class model ── cwm-ecore:generate ──▶ *.ecore ── EMF codegen ──▶ Java
 (CWM XMI)                               │
                                         ▼
 mapping (CWM-ORM) ──────── fennec-eorm:generate ──▶ *.eorm  (Fennec Persistence)
 twin mapping (sensiNact) ─ fennec-atlas:generate ─▶ mappings/, profiles/  (Fennec event.atlas)
```

`cwm-ecore:generate` runs in `generate-sources`, `fennec-eorm:generate` and
`fennec-atlas:generate` in `generate-resources`, so in one build the `.ecore` files exist before
the `.eorm` and the provider mappings are written.

## Building

```sh
mvn install -pl maven/ecore,maven/eorm,maven/atlas -am
```

Each module's tests run its goal in JUnit (the Mojo instantiated, its parameters set as a POM
sets them) on small projects under `src/test/resources/it`;
see the "Tests of this module" section of each README.

## Conventions for a plugin

- One module `maven/<name>`, artifact and Java package `org.eclipse.daanse.cwm.maven.<name>`,
  parent `org.eclipse.daanse.cwm.maven`; the Mojo class is `<Goal><Name>Mojo`
  (`GenerateEcoreMojo`, `GenerateAtlasMojo`).
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
