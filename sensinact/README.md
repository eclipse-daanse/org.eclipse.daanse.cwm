# org.eclipse.daanse.cwm.sensinact

Turns CWM sensiNact mappings (`model/daanse/mapping.sensinact`, onto the providers of
`model/daanse/resource.sensinact`) into the files of Eclipse Fennec event.atlas, which builds Eclipse
sensiNact digital twins from instances of an EMF model.

| Module | |
| --- | --- |
| [`fennec.atlas.core`](fennec.atlas.core/README.md) | the converter |

The Maven goal `fennec-atlas:generate` that runs it is in [`maven/atlas`](../maven/atlas/README.md).
