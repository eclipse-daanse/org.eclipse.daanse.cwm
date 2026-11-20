# org.eclipse.daanse.cwm.model.daanse.semantictype.xmla

The types of an Analysis Services server as libraries of semantic types of `semantictype.base`, their
XMLA codes and the rules of typings that use them. Plain Java, no model of its own.

`ServerTypes.create` builds a package with four libraries into a namespace, as `units` builds the SI.
The server keeps three lists of types, and each is a library of its own under
`https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.semantictype.xmla`:

| Library | URI | Types |
| --- | --- | --- |
| attribute types | `.../attribute` | 240: what the values of an attribute are |
| dimension types | `.../dimension` | 15: what the members of a dimension stand for |
| account types | `.../account` | 7: the standard account types |
| groups | `.../groups` | 30: the groups of the attribute types, not the server's |

Each type of the three lists has its name there (the spelling of the Analysis Services Scripting
Language) and its number there (its place in the schema's enumeration). A name in two lists is two
types in two libraries: the attribute type `Channel` and the dimension type `Channel`. Every attribute
type is narrower than its group; the fiscal, reporting, manufacturing, ISO 8601 and day-flag groups are
narrower than `Time`, so that a reader finds all time types at once. The server's list of account types
can be extended: a model adds an account type of its own in a library of its own, narrower than or the
same as one of the seven.

`XmlaCodes` gives the codes as the server reports them, chosen by the list a type lies in: a type of the
attribute list has LEVEL_TYPE and PROPERTY_CONTENT_TYPE, one of the dimension list DIMENSION_TYPE, one of
the account list none. Where the specification [MS-SSAS] names another code (two property codes), the
server's is the one given. A type of another library takes, in each list, the first type of the server
it reaches: through its same types, then the external types that name a list of the server, then its
broader types; else the codes of a regular level, property or dimension. One type of one's own may so
give a column a LEVEL_TYPE and a table a DIMENSION_TYPE.

`XmlaTypesValidator` checks the typings whose types are or reach the server's types: T1, T2, T3 an
element fits one of the lists its type reaches (attributes, columns, fields, data types or classes
that are no table or view for the attribute list; tables, views or dimensions for the dimension list;
elements that stand for one account type for the account list); T4 the typings of an element reach at
most one type of each list; T6 (warning) a typing of `Regular` says nothing; T7 (warning) the history
types come from the history rules of a model; T15 an external type that names a library of the server
names one of its three lists and a type in it.

| Table | Columns |
| --- | --- |
| `attribute-types.tsv` | `name`, `number`, `level_type`, `level_type_spec`, `property_content_type`, `property_content_type_spec`, `other_spelling` (another spelling a server takes, such as `ProductSku`), `group` |
| `dimension-types.tsv` | `name`, `number`, `dimension_type` |
| `account-types.tsv` | `name`, `number` |

## Relations

- Builds on `semantictype.base` and CWM `core`; reports in `model/daanse/validation`.
- A model holds the libraries it uses, as it holds the SI, or names the types of libraries delivered
  beside it: `ServerTypes.LIBRARIES` are the URIs a program passes to
  `SemanticTypeModelValidator.check(root, delivered)`.
