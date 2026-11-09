# org.eclipse.daanse.cwm.model.daanse.resource.json

JSON data on CWM, after RFC 8259 and JSON Schema 2020-12. A `Schema` is a `core::Package` with
its dialect and vocabularies. An `ObjectType` is a `core::Class` whose `Property`s
(`core::Attribute`s, also as `PatternProperty`) name their type; an `ArrayType` names its item
type or its positional `ItemDef`s; a `PrimitiveType` is a `core::DataType` with the value kind and
the constraints of JSON Schema (length, range, pattern, format); a `CompositeType` combines types
(`allOf`, `anyOf`, `oneOf`, `not`, `if`/`then`/`else`); a `SchemaReference` is a `$ref`. The
values are CWM instance data: a `Document` (an `instance::Extent`) in one of the file forms
single text, JSON Lines, JSON text sequence or concatenated texts, its `Node`s and `ScalarValue`s.
2 OCL invariants check the bounds of arrays and strings.

## Relations

- Builds on CWM `core` and `instance`.
- Sits next to `resource.xml` and `resource.record`: the same data appears once as a nested tree
  and, as JSON Lines, once as a stream of records.

## Example

A schema with an object type `Order`, a required integer `id` and a list of at most ten unique
tags (namespace declarations and ids left out):

```xml
<json:Schema name="order" dialect="https://json-schema.org/draft/2020-12/schema" identifier="https://example.org/order.schema.json">
  <ownedElement xsi:type="json:PrimitiveType" name="OrderId" valueKind="json_integer" minimum="1"/>
  <ownedElement xsi:type="json:PrimitiveType" name="Tag" valueKind="json_string" maxLength="20"/>
  <ownedElement xsi:type="json:ArrayType" name="Tags" itemType="_Tag" maxItems="10" uniqueItems="true"/>
  <ownedElement xsi:type="json:ObjectType" name="Order" allowsAdditionalProperties="false">
    <feature xsi:type="json:Property" name="id" type="_OrderId" isRequired="true"/>
    <feature xsi:type="json:Property" name="tags" type="_Tags"/>
  </ownedElement>
</json:Schema>
```
