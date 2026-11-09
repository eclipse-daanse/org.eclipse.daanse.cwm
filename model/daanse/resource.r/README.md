# org.eclipse.daanse.cwm.model.daanse.resource.r

Data in the native serialization formats of R on CWM: an `RdsFile` holds exactly one object, an
`RDataFile` the named `RBinding`s of a workspace; both are `RFile`s (`core::Package`s) with
serialization format, version, encoding and compression. An `RType` is a `core::Classifier`
carrying its SEXP type and class: `RVectorType`, `RArrayType` (with `dim`), `RFactorType` (with a
`datatypes::Enumeration` of levels), `RListType`, `RDataFrameType` (equally long columns,
row names, tibble), `RS4Type` and `ROpaqueType`. The components of a list and the columns of a
data frame are `RComponent`s (`core::Attribute`s), in order; `RAttribute` describes the special
attributes (`names`, `dim`, `levels`, `row.names`, ...). The values are CWM instance data:
`RWorkspace` (an `instance::Extent`), `RObject` and `RValue`. 2 OCL invariants check the
dimensions of arrays and the column types of data frames.

## Relations

- Builds on CWM `core`, `instance` and `datatypes`.
- Deliberately not on `resource.record`: a binary R object has no field or text delimiters.

## Example

An RDS file whose root object is a data frame with an integer and a double column (namespace
declarations and ids left out):

```xml
<r:RdsFile name="survey" serializationVersion="3" compression="r_gzip" rootType="_survey.survey">
  <ownedElement xsi:type="r:RVectorType" name="integer" sexpType="r_integer"/>
  <ownedElement xsi:type="r:RVectorType" name="double" sexpType="r_real"/>
  <ownedElement xsi:type="r:RDataFrameType" name="survey" sexpType="r_list" rowCount="120">
    <feature xsi:type="r:RComponent" name="age" type="_integer"/>
    <feature xsi:type="r:RComponent" name="score" type="_double" position="1"/>
    <rClass>data.frame</rClass>
  </ownedElement>
</r:RdsFile>
```
