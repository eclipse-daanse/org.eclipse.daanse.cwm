# Test projects

Inputs of `GenerateDdlMojoTest`, which runs the goal on them the way a build would.

| Project | File | Content |
| --- | --- | --- |
| `library` | `src/main/cwm/catalog.xmi` | the many-to-one ORM tutorial (catalog `LIBRARYDB`, schema `LIB`: tables `PUBLISHER` and `BOOK` with primary keys, the foreign key `FK_BOOK_PUBLISHER` and the index `IX_BOOK_PUBLISHER`) |
