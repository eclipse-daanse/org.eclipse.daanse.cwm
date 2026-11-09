/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.json.build;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

/**
 * Programmatic builder for the {@code json} metamodel. The JSON package describes the structure of data in the JSON format.
 *
 * <p>Every classifier, feature, literal and annotation is created in the order of
 * {@code model/json.ecore}; the references into CWM are resolved in the CWM Ecores next to the
 * module. Construction and serialization follow the builders of the other Daanse models.</p>
 */
public final class JsonEcoreBuilder {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    /** The namespace URI of the package. */
    public static final String NS_URI = "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.resource.json";
    private static final String CONSTRAINTS_NS = NS_URI + "/constraints";
    private static final String CORE = "http://www.omg.org/spec/CWM/1.1/objectmodel/core";
    private static final String INSTANCE = "http://www.omg.org/spec/CWM/1.1/objectmodel/instance";

    private static final String EPL_HEADER = String.join("\n",
            "Copyright (c) 2026 Contributors to the Eclipse Foundation.",
            "",
            "This program and the accompanying materials are made",
            "available under the terms of the Eclipse Public License 2.0",
            "which is available at https://www.eclipse.org/legal/epl-2.0/",
            "",
            "SPDX-License-Identifier: EPL-2.0");

    private final EcoreFactory ef = EcoreFactory.eINSTANCE;
    private final Map<String, EPackage> cwm;

    /** A builder over the CWM packages it refers to, by namespace URI. */
    public JsonEcoreBuilder(Map<String, EPackage> cwm) {
        this.cwm = cwm;
    }

    /**
     * Builds the package from the CWM Ecores next to {@code moduleDir} (module directory
     * {@code model/daanse/resource.json}) and writes it to {@code outFile}.
     */
    public static void generate(File moduleDir, File outFile) throws IOException {
        File cwmDir = new File(moduleDir, "../../cwm").getCanonicalFile();
        ResourceSet rs = newResourceSet();
        Map<String, EPackage> cwm = new HashMap<>();
        load(rs, cwm, cwmDir, "objectmodel.core/model/core.ecore");
        load(rs, cwm, cwmDir, "objectmodel.instance/model/instance.ecore");
        outFile.getParentFile().mkdirs();
        save(new JsonEcoreBuilder(cwm).build(), rs, URI.createFileURI(outFile.getAbsolutePath()));
    }

    /** Creates the EPackage {@code json}. */
    public EPackage build() {
        EPackage p = ef.createEPackage();
        p.setName("json");
        p.setNsURI(NS_URI);
        p.setNsPrefix("json");
        ann(p, ECORE,
                "invocationDelegates", ECORE_OCL,
                "settingDelegates", ECORE_OCL,
                "validationDelegates", ECORE_OCL);
        ann(p, ECORE_OCL,
                "hiddenOpposites", "true");
        ann(p, GENMODEL,
                "documentation", "The JSON package describes the structure of data in the JSON format. It sits "
                    + "alongside resource/xml and resource/record: XML describes marked-up text with element "
                    + "types and content models, Record describes record-oriented streams with delimiters, "
                    + "JSON describes nested values built from objects and arrays.\n\nValues. Per RFC 8259 "
                    + "JSON has four primitive types - string, number, boolean and null - and two structured "
                    + "ones: the object as an unordered collection of name/value pairs and the array as an "
                    + "ordered sequence of values. Two subtleties matter for a metamodel. First, the names "
                    + "within an object SHOULD be unique but need not be; parsers differ in how they handle "
                    + "duplicates. Second, the object is defined as unordered, yet some libraries make the "
                    + "order of the pairs visible - so one may neither rely on it nor discard it carelessly. "
                    + "The order of array elements, by contrast, is significant and is carried here through "
                    + "ordered features.\n\nSchemas. The descriptive side follows JSON Schema 2020-12. There "
                    + "prefixItems and items replace the former items and additionalItems, definitions live "
                    + "under $defs, references run through $ref, and schemas may declare the vocabularies "
                    + "they use. The basic stance of the standard matters: a schema constrains, it does not "
                    + "prescribe. Without a type keyword any value is admissible, and unknown members are "
                    + "allowed unless additionalProperties rules them out.\n\nFile forms. Besides the single "
                    + "JSON text, the line-oriented forms are widespread, carrying one complete JSON text "
                    + "per line - JSON Lines, also known as NDJSON. That is the record-oriented shape of "
                    + "JSON and the reason this package sits next to resource/record: the same body of data "
                    + "appears once as a nested tree and once as a stream of records.\n\nMapping onto CWM. An "
                    + "object type is an objectmodel/core::Class and a property is an "
                    + "objectmodel/core::Attribute - just like xml::Attribute and record::Field. The type of "
                    + "a property is held by the inherited reference StructuralFeature::type and may point "
                    + "at any Classifier of this package; a dedicated abstract root type would be "
                    + "superfluous, because Classifier already fills that role.\n\nSources: RFC 8259 (The "
                    + "JavaScript Object Notation Data Interchange Format), JSON Schema Draft 2020-12, RFC "
                    + "6901 (JSON Pointer), RFC 7464 (JSON Text Sequences).",
                "basePackage", "org.eclipse.daanse.cwm.model.daanse.resource",
                "prefix", "Json",
                "oSGiCompatible", "true",
                "literalsInterface", "true",
                "loadInitialization", "false",
                "copyrightText", EPL_HEADER);

        // the classifiers first: they refer to each other
        EEnum jsonValueKind = enm(p, "JsonValueKind");
        EEnum compositionKind = enm(p, "CompositionKind");
        EEnum documentForm = enm(p, "DocumentForm");
        EClass schema = cls(p, "Schema", false);
        EClass objectType = cls(p, "ObjectType", false);
        EClass arrayType = cls(p, "ArrayType", false);
        EClass primitiveType = cls(p, "PrimitiveType", false);
        EClass compositeType = cls(p, "CompositeType", false);
        EClass schemaReference = cls(p, "SchemaReference", false);
        EClass property = cls(p, "Property", false);
        EClass patternProperty = cls(p, "PatternProperty", false);
        EClass itemDef = cls(p, "ItemDef", false);
        EClass document = cls(p, "Document", false);
        EClass node = cls(p, "Node", false);
        EClass scalarValue = cls(p, "ScalarValue", false);
        EStructuralFeature f;
        EEnumLiteral l;

        // JsonValueKind
        doc(jsonValueKind, "The value types of JSON per RFC 8259. The standard itself has no separate integer "
                + "type - json_integer is a refinement of json_number that JSON Schema carries as a type "
                + "value of its own.");
        l = literal(jsonValueKind, "json_object", 0);
        doc(l, "An object: an unordered collection of name/value pairs.");
        l = literal(jsonValueKind, "json_array", 1);
        doc(l, "An array: an ordered sequence of values.");
        l = literal(jsonValueKind, "json_string", 2);
        doc(l, "A string.");
        l = literal(jsonValueKind, "json_number", 3);
        doc(l, "A number.");
        l = literal(jsonValueKind, "json_integer", 4);
        doc(l, "A number without a fractional part.");
        l = literal(jsonValueKind, "json_boolean", 5);
        doc(l, "true or false.");
        l = literal(jsonValueKind, "json_null", 6);
        doc(l, "null.");

        // CompositionKind
        doc(compositionKind, "The composing keywords of JSON Schema. They combine subschemas rather than describing "
                + "a value type of their own.");
        l = literal(compositionKind, "json_allOf", 0);
        doc(l, "allOf - every subschema must hold.");
        l = literal(compositionKind, "json_anyOf", 1);
        doc(l, "anyOf - at least one must hold.");
        l = literal(compositionKind, "json_oneOf", 2);
        doc(l, "oneOf - exactly one must hold.");
        l = literal(compositionKind, "json_not", 3);
        doc(l, "not - the one subschema must not hold.");
        l = literal(compositionKind, "json_ifThenElse", 4);
        doc(l, "if/then/else - conditional application. The subschemas appear in that order in member.");

        // DocumentForm
        doc(documentForm, "The shape in which one or more JSON texts are held in a file.");
        l = literal(documentForm, "json_single", 0);
        doc(l, "A single JSON text as defined in RFC 8259.");
        l = literal(documentForm, "json_lines", 1);
        doc(l, "JSON Lines, also known as NDJSON: one complete JSON text per line, separated by line "
                + "feeds. The record-oriented form.");
        l = literal(documentForm, "json_sequence", 2);
        doc(l, "JSON Text Sequences per RFC 7464: each text is preceded by a separator (0x1E).");
        l = literal(documentForm, "json_concatenated", 3);
        doc(l, "Texts placed directly one after another without a separator; readable only by "
                + "streaming parsers.");

        // Schema
        doc(schema, "A JSON Schema document. It is the namespace of the types declared in it; those are "
                + "the inherited ownedElement. What sits under $defs does not differ in the model from a "
                + "type declared elsewhere - the difference lies solely in whether anything refers to it.");
        schema.getESuperTypes().add((EClass) cwm(CORE, "Package"));
        f = attr(schema, "dialect", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The value of $schema, that is, the URI of the underlying dialect, for example "
                + "https://json-schema.org/draft/2020-12/schema.");
        f = attr(schema, "identifier", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The value of $id: the base URI against which every reference of this schema is "
                + "resolved.");
        f = attr(schema, "vocabulary", cwm(CORE, "String"), 0, -1);
        f.setOrdered(false);
        doc(f, "The vocabulary URIs declared in the $vocabulary keyword. A schema can thereby state "
                + "which sets of keywords a validator must understand.");
        f = attr(schema, "title", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The value of title: a short designation.");

        // ObjectType
        doc(objectType, "An object type: a JSON object with described properties. The properties are the "
                + "inherited features. Note that JSON Schema constrains rather than prescribes: without "
                + "additionalProperties an object may carry arbitrary further pairs, and a described "
                + "property is demanded only when its name appears in required.");
        objectType.getESuperTypes().add((EClass) cwm(CORE, "Class"));
        f = attr(objectType, "allowsAdditionalProperties", cwm(CORE, "Boolean"), 1, 1);
        f.setDefaultValueLiteral("true");
        doc(f, "True as long as additionalProperties is not set to false. That is the default of JSON "
                + "Schema.");
        f = attr(objectType, "additionalPropertiesTypeName", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The name of the type that undescribed pairs must satisfy, when additionalProperties "
                + "is a schema rather than a boolean.");
        f = attr(objectType, "minProperties", cwm(CORE, "Integer"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The minimum number of pairs from minProperties.");
        f = attr(objectType, "maxProperties", cwm(CORE, "Integer"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The maximum number of pairs from maxProperties.");
        f = attr(objectType, "propertyNamePattern", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The regular expression from propertyNames that every name in this object must satisfy.");

        // ArrayType
        ann(arrayType, ECORE,
                "constraints", "MaxItemsNotBelowMinItems");
        ann(arrayType, ECORE_OCL,
                "MaxItemsNotBelowMinItems", "self.maxItems >= self.minItems or self.maxItems = 0");
        ann(arrayType, CONSTRAINTS_NS,
                "MaxItemsNotBelowMinItems.name", "Max Items Not Below Min Items",
                "MaxItemsNotBelowMinItems.description", "The maximum length of an array is not below its minimum length.",
                "MaxItemsNotBelowMinItems.level", "error");
        doc(arrayType, "An array type. JSON Schema has two shapes that share this class: the uniform array, "
                + "in which every element satisfies the same type (itemType), and the tuple, in which "
                + "the leading positions each have their own type (prefixItems, here the inherited "
                + "ordered features). The two can be combined - itemType then describes what may follow "
                + "beyond the fixed positions.");
        arrayType.getESuperTypes().add((EClass) cwm(CORE, "Class"));
        f = ref(arrayType, "itemType", cwm(CORE, "Classifier"), 0, 1);
        oppositeRole(f, "itemArrayType");
        doc(f, "The type of the uniform elements or, in a tuple, the type of every element beyond the "
                + "positions fixed by prefixItems. The opposite end is not a feature: Classifier belongs "
                + "to the CWM core, to which an extension may not add anything.");
        f = attr(arrayType, "allowsAdditionalItems", cwm(CORE, "Boolean"), 1, 1);
        f.setDefaultValueLiteral("true");
        doc(f, "True as long as items does not forbid a tuple further elements.");
        f = attr(arrayType, "minItems", cwm(CORE, "Integer"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The minimum length from minItems.");
        f = attr(arrayType, "maxItems", cwm(CORE, "Integer"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The maximum length from maxItems.");
        f = attr(arrayType, "uniqueItems", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when uniqueItems is set and the elements must therefore be pairwise distinct.");

        // PrimitiveType
        ann(primitiveType, ECORE,
                "constraints", "LengthsAreConsistent");
        ann(primitiveType, ECORE_OCL,
                "LengthsAreConsistent", "self.maxLength >= self.minLength or self.maxLength = 0");
        ann(primitiveType, CONSTRAINTS_NS,
                "LengthsAreConsistent.name", "Lengths Are Consistent",
                "LengthsAreConsistent.description", "The maximum length of a string is not below its minimum length.",
                "LengthsAreConsistent.level", "error");
        doc(primitiveType, "A primitive value type together with its constraints. Numeric bounds are deliberately "
                + "held as strings: RFC 8259 fixes neither precision nor range for numbers, and parsers "
                + "differ considerably. Keeping the written form loses nothing; converting it into a "
                + "floating-point format may.");
        primitiveType.getESuperTypes().add((EClass) cwm(CORE, "DataType"));
        f = attr(primitiveType, "valueKind", jsonValueKind, 1, 1);
        doc(f, "Which of the primitive value types is described.");
        f = attr(primitiveType, "format", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The value of format, for example date-time, uuid or email. In the basic vocabulary "
                + "format is an annotation rather than a check.");
        f = attr(primitiveType, "pattern", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The regular expression from pattern that a string must satisfy.");
        f = attr(primitiveType, "minLength", cwm(CORE, "Integer"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The minimum length of a string.");
        f = attr(primitiveType, "maxLength", cwm(CORE, "Integer"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The maximum length of a string.");
        f = attr(primitiveType, "minimum", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The lower bound from minimum, in the notation of the schema.");
        f = attr(primitiveType, "maximum", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The upper bound from maximum, in the notation of the schema.");
        f = attr(primitiveType, "exclusiveMinimum", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The lower bound from exclusiveMinimum; the bound itself is not admissible.");
        f = attr(primitiveType, "exclusiveMaximum", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The upper bound from exclusiveMaximum; the bound itself is not admissible.");
        f = attr(primitiveType, "multipleOf", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The divisor from multipleOf, in the notation of the schema.");
        f = attr(primitiveType, "allowedValue", cwm(CORE, "String"), 0, -1);
        doc(f, "The admissible values from enum, each in its JSON notation. Deliberately strings "
                + "rather than a foundation/datatypes::Enumeration: the values of enum may be arbitrary "
                + "JSON values, including objects and arrays, whereas an Enumeration carries named "
                + "literals.");
        f = attr(primitiveType, "constantValue", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The single admissible value from const, in its JSON notation.");

        // CompositeType
        doc(compositeType, "A type composed of subschemas: allOf, anyOf, oneOf, not or if/then/else. These "
                + "keywords describe no value type of their own but combine others - hence a class of "
                + "its own rather than a feature on the value types.");
        compositeType.getESuperTypes().add((EClass) cwm(CORE, "Class"));
        f = attr(compositeType, "composition", compositionKind, 1, 1);
        doc(f, "The kind of combination.");
        f = ref(compositeType, "member", cwm(CORE, "Classifier"), 1, -1);
        oppositeRole(f, "compositeType");
        doc(f, "The combined subschemas in the order in which they appear in the schema. For "
                + "json_ifThenElse the order is significant: if, then, else. The opposite end is not a "
                + "feature, because Classifier belongs to the CWM core.");

        // SchemaReference
        doc(schemaReference, "A reference made with $ref or $dynamicRef. It stands in the model as a Classifier of "
                + "its own, so that a schema can be represented in full even when the target lies "
                + "elsewhere or has not been read yet.");
        schemaReference.getESuperTypes().add((EClass) cwm(CORE, "Class"));
        f = attr(schemaReference, "pointer", cwm(CORE, "String"), 1, 1);
        doc(f, "The value of $ref: a URI reference whose fragment is a JSON Pointer per RFC 6901, for "
                + "example #/$defs/address.");
        f = attr(schemaReference, "isDynamic", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True for $dynamicRef. Such a reference is resolved only at validation time against "
                + "the surrounding $dynamicAnchor; it replaces the former $recursiveRef and "
                + "$recursiveAnchor.");
        f = ref(schemaReference, "target", cwm(CORE, "Classifier"), 0, 1);
        oppositeRole(f, "schemaReference");
        doc(f, "The resolved target type, where it is available. Left unset for a reference not yet "
                + "resolved or pointing outside. The opposite end is not a feature, because Classifier "
                + "belongs to the CWM core.");

        // Property
        doc(property, "A named property of an object type, that is, an entry under properties. The type is "
                + "held by the inherited reference StructuralFeature::type, the owner by Feature::owner.");
        property.getESuperTypes().add((EClass) cwm(CORE, "Attribute"));
        f = attr(property, "isRequired", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when the name of the pair appears in required. In JSON Schema that is stated on "
                + "the object rather than on the property; here it sits on the property, because that is "
                + "where it is asked for.");
        f = attr(property, "defaultValue", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The value of default in JSON notation. It is an annotation for tools and is not "
                + "substituted during validation.");
        f = attr(property, "isDeprecated", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when deprecated is set.");
        f = attr(property, "isReadOnly", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when readOnly is set.");
        f = attr(property, "isWriteOnly", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when writeOnly is set.");
        f = attr(property, "description", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The value of description.");

        // PatternProperty
        doc(patternProperty, "A property whose name is not fixed but must satisfy a regular expression - an entry "
                + "under patternProperties. Where several patterns match the same name, the value must "
                + "satisfy all the corresponding subschemas.");
        patternProperty.getESuperTypes().add((EClass) property);
        f = attr(patternProperty, "namePattern", cwm(CORE, "String"), 1, 1);
        doc(f, "The regular expression selecting the names. The inherited mandatory attribute name "
                + "then carries only a designation for tools.");

        // ItemDef
        doc(itemDef, "A fixed position of a tuple, that is, an entry under prefixItems. The type is held by "
                + "the inherited reference StructuralFeature::type; the order follows from the ordered "
                + "containment reference Classifier::feature.");
        itemDef.getESuperTypes().add((EClass) cwm(CORE, "Attribute"));
        f = attr(itemDef, "position", cwm(CORE, "Integer"), 1, 1);
        doc(f, "The zero-based position in the array.");

        // Document
        doc(document, "A JSON file or stream, that is, the totality of the values held in it.");
        document.getESuperTypes().add((EClass) cwm(INSTANCE, "Extent"));
        f = attr(document, "form", documentForm, 1, 1);
        doc(f, "The shape in which the texts are held in the file.");
        f = attr(document, "charset", cwm(CORE, "String"), 1, 1);
        f.setDefaultValueLiteral("UTF-8");
        doc(f, "The encoding. RFC 8259 prescribes UTF-8 for exchange between systems outside a closed "
                + "ecosystem.");
        f = attr(document, "mediaType", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The media type, that is, application/json, application/jsonl or application/json-seq.");

        // Node
        doc(node, "An object or array actually present. The describing type is held by the inherited "
                + "reference Instance::classifier.");
        node.getESuperTypes().add((EClass) cwm(INSTANCE, "Object"));
        f = attr(node, "pointer", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The location of the node in the document as a JSON Pointer per RFC 6901, for example "
                + "/orders/0/customer. The empty string denotes the root.");

        // ScalarValue
        doc(scalarValue, "A primitive value actually present.");
        scalarValue.getESuperTypes().add((EClass) cwm(INSTANCE, "DataValue"));
        f = attr(scalarValue, "valueKind", jsonValueKind, 1, 1);
        doc(f, "Which of the primitive value types is present. For json_null the inherited attribute "
                + "value carries the string null - JSON distinguishes the value null from an absent pair.");

        return p;
    }

    private EClassifier cwm(String nsUri, String name) {
        EClassifier c = cwm.get(nsUri).getEClassifier(name);
        if (c == null) {
            throw new IllegalStateException("CWM classifier not found: " + name + " in " + nsUri);
        }
        return c;
    }

    private EClass cls(EPackage pkg, String name, boolean isAbstract) {
        EClass c = ef.createEClass();
        c.setName(name);
        c.setAbstract(isAbstract);
        pkg.getEClassifiers().add(c);
        return c;
    }

    private EEnum enm(EPackage pkg, String name) {
        EEnum e = ef.createEEnum();
        e.setName(name);
        pkg.getEClassifiers().add(e);
        return e;
    }

    private EEnumLiteral literal(EEnum e, String name, int value) {
        EEnumLiteral l = ef.createEEnumLiteral();
        l.setName(name);
        l.setValue(value);
        e.getELiterals().add(l);
        return l;
    }

    private EAttribute attr(EClass owner, String name, EClassifier type, int lower, int upper) {
        EAttribute a = ef.createEAttribute();
        a.setName(name);
        a.setEType(type);
        a.setLowerBound(lower);
        a.setUpperBound(upper);
        owner.getEStructuralFeatures().add(a);
        return a;
    }

    private EReference ref(EClass owner, String name, EClassifier type, int lower, int upper) {
        EReference r = ef.createEReference();
        r.setName(name);
        r.setEType(type);
        r.setLowerBound(lower);
        r.setUpperBound(upper);
        owner.getEStructuralFeatures().add(r);
        return r;
    }

    /** An annotation with its details, given as key and value in turn. */
    private EAnnotation ann(EModelElement el, String source, String... keysAndValues) {
        EAnnotation a = ef.createEAnnotation();
        a.setSource(source);
        for (int i = 0; i < keysAndValues.length; i += 2) {
            a.getDetails().put(keysAndValues[i], keysAndValues[i + 1]);
        }
        el.getEAnnotations().add(a);
        return a;
    }

    private void doc(EModelElement el, String documentation) {
        ann(el, GENMODEL, "documentation", documentation);
    }

    /** {@code Property.oppositeRoleName} pair (convention for hidden association ends). */
    private void oppositeRole(EModelElement el, String roleName) {
        ann(el, EMOF, "Property.oppositeRoleName", roleName);
        ann(el, EMOF + "#Property.oppositeRoleName", "body", roleName);
    }

    /** A resource set reading {@code .ecore} files. */
    public static ResourceSet newResourceSet() {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
        return rs;
    }

    /**
     * Loads a CWM ecore; its resource URI becomes the nsURI, so that references serialize as
     * {@code <nsURI>#//Class}.
     */
    private static void load(ResourceSet rs, Map<String, EPackage> cwm, File cwmDir, String path) {
        File f = new File(cwmDir, path);
        if (!f.isFile()) {
            throw new IllegalStateException("CWM ecore not found: " + f);
        }
        Resource res = rs.getResource(URI.createFileURI(f.getAbsolutePath()), true);
        EPackage pkg = (EPackage) res.getContents().get(0);
        res.setURI(URI.createURI(pkg.getNsURI()));
        rs.getPackageRegistry().put(pkg.getNsURI(), pkg);
        cwm.put(pkg.getNsURI(), pkg);
    }

    /** Writes the package as UTF-8 with the EPL header after the XML declaration. */
    public static void save(EPackage pkg, ResourceSet rs, URI outUri) throws IOException {
        Resource out = rs.createResource(outUri);
        out.getContents().add(pkg);
        Map<Object, Object> opts = new HashMap<>();
        opts.put(XMLResource.OPTION_ENCODING, "UTF-8");
        opts.put(XMLResource.OPTION_LINE_WIDTH, Integer.valueOf(96));
        out.save(opts);
        Path file = Path.of(outUri.toFileString());
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String comment = "<!--\n"
                + "/*********************************************************************\n"
                + "* Copyright (c) 2026 Contributors to the Eclipse Foundation.\n"
                + "*\n"
                + "* This program and the accompanying materials are made\n"
                + "* available under the terms of the Eclipse Public License 2.0\n"
                + "* which is available at https://www.eclipse.org/legal/epl-2.0/\n"
                + "*\n"
                + "* SPDX-License-Identifier: EPL-2.0\n"
                + "**********************************************************************/\n"
                + "-->\n";
        int nl = content.indexOf('\n');
        Files.writeString(file, content.substring(0, nl + 1) + comment + content.substring(nl + 1),
                StandardCharsets.UTF_8);
    }
}
