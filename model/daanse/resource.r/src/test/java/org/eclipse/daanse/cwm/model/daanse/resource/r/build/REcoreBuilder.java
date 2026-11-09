/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.r.build;

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
 * Programmatic builder for the {@code r} metamodel. The R package describes the structure of data held in the native serialization formats of the R language: RDS (exactly one serialized object, without a name) and RData/.rda (a set of named bindings taken from an R environment, that is, a workspace image).
 *
 * <p>Every classifier, feature, literal and annotation is created in the order of
 * {@code model/r.ecore}; the references into CWM are resolved in the CWM Ecores next to the
 * module. Construction and serialization follow the builders of the other Daanse models.</p>
 */
public final class REcoreBuilder {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    /** The namespace URI of the package. */
    public static final String NS_URI = "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.resource.r";
    private static final String CONSTRAINTS_NS = NS_URI + "/constraints";
    private static final String DATATYPES = "http://www.omg.org/spec/CWM/1.1/foundation/datatypes";
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
    public REcoreBuilder(Map<String, EPackage> cwm) {
        this.cwm = cwm;
    }

    /**
     * Builds the package from the CWM Ecores next to {@code moduleDir} (module directory
     * {@code model/daanse/resource.r}) and writes it to {@code outFile}.
     */
    public static void generate(File moduleDir, File outFile) throws IOException {
        File cwmDir = new File(moduleDir, "../../cwm").getCanonicalFile();
        ResourceSet rs = newResourceSet();
        Map<String, EPackage> cwm = new HashMap<>();
        load(rs, cwm, cwmDir, "foundation.datatypes/model/datatypes.ecore");
        load(rs, cwm, cwmDir, "objectmodel.core/model/core.ecore");
        load(rs, cwm, cwmDir, "objectmodel.instance/model/instance.ecore");
        outFile.getParentFile().mkdirs();
        save(new REcoreBuilder(cwm).build(), rs, URI.createFileURI(outFile.getAbsolutePath()));
    }

    /** Creates the EPackage {@code r}. */
    public EPackage build() {
        EPackage p = ef.createEPackage();
        p.setName("r");
        p.setNsURI(NS_URI);
        p.setNsPrefix("r");
        ann(p, ECORE,
                "invocationDelegates", ECORE_OCL,
                "settingDelegates", ECORE_OCL,
                "validationDelegates", ECORE_OCL);
        ann(p, ECORE_OCL,
                "hiddenOpposites", "true");
        ann(p, GENMODEL,
                "documentation", "The R package describes the structure of data held in the native serialization "
                    + "formats of the R language: RDS (exactly one serialized object, without a name) and "
                    + "RData/.rda (a set of named bindings taken from an R environment, that is, a workspace "
                    + "image). Both use the same stream layout; they differ only in whether a single value "
                    + "or a list of names is written.\n\nStream layout. The stream opens with a format byte - "
                    + "'X' for the default binary XDR form (integers and doubles in big-endian order; byte "
                    + "quantities such as the contents of CHARSXP and RAWSXP are written as-is and are not "
                    + "padded to a multiple of four, which is why the format is not fully XDR as defined in "
                    + "RFC 1832), 'A' for the textual form, 'B' for native byte order - followed by three "
                    + "integers: the serialization version, the writing R version and the minimum R version "
                    + "able to read the file. Version 2 is the form used by R 1.4.0 through R 3.5.3; version "
                    + "3 (from R 3.5.0) extends it with custom serialization of ALTREP objects and "
                    + "additionally records the native encoding in force at write time, so that unflagged "
                    + "strings can be converted when read under a different native encoding. A compression "
                    + "layer is normally wrapped around the whole stream; R detects it from magic bytes and "
                    + "supports gzip (the default), bzip2 and xz.\n\nType system. Every value in R is a SEXP "
                    + "carrying one of 24 SEXPTYPEs in use; codes 0 to 10 and 13 to 25 are occupied, while "
                    + "codes 11 and 12 were assigned to internal factors and have been withdrawn. From this "
                    + "small set of base types R builds every higher structure exclusively by means of "
                    + "attributes. The attributes names, dim, dimnames, class, levels, row.names, tsp and "
                    + "comment receive special treatment from the interpreter; all others are free-form. A "
                    + "matrix is therefore an atomic vector carrying dim, a factor is an integer vector "
                    + "carrying levels and class=\"factor\", and a data.frame is a generic vector (list) of "
                    + "equally long columns carrying names, row.names and class=\"data.frame\".\n\nMapping onto "
                    + "CWM. An RType is an objectmodel/core::Classifier, and a list component or data.frame "
                    + "column is an objectmodel/core::Attribute - exactly as resource/record::Field and "
                    + "resource/relational::Column are. Component order is carried by the inherited, ordered "
                    + "containment reference Classifier::feature; membership in a file is carried by "
                    + "Namespace::ownedElement. Deliberately, nothing inherits from "
                    + "resource/record::RecordDef: its mandatory attributes fieldDelimiter and textDelimiter "
                    + "describe a character stream and have no counterpart in a binary serialized R "
                    + "object.\n\nSources: R Internals (R Core Team), sections 'R Internal Structures' and "
                    + "'Serialization Formats'.",
                "basePackage", "org.eclipse.daanse.cwm.model.daanse.resource",
                "prefix", "R",
                "oSGiCompatible", "true",
                "literalsInterface", "true",
                "loadInitialization", "false",
                "copyrightText", EPL_HEADER);

        // the classifiers first: they refer to each other
        EEnum sexpType = enm(p, "SexpType");
        EEnum serializationFormat = enm(p, "SerializationFormat");
        EEnum compressionKind = enm(p, "CompressionKind");
        EEnum rowNamesKind = enm(p, "RowNamesKind");
        EEnum stringEncoding = enm(p, "StringEncoding");
        EEnum specialAttributeKind = enm(p, "SpecialAttributeKind");
        EClass rType = cls(p, "RType", true);
        EClass rVectorType = cls(p, "RVectorType", false);
        EClass rArrayType = cls(p, "RArrayType", false);
        EClass rFactorType = cls(p, "RFactorType", false);
        EClass rListType = cls(p, "RListType", false);
        EClass rDataFrameType = cls(p, "RDataFrameType", false);
        EClass rS4Type = cls(p, "RS4Type", false);
        EClass rOpaqueType = cls(p, "ROpaqueType", false);
        EClass rComponent = cls(p, "RComponent", false);
        EClass rAttribute = cls(p, "RAttribute", false);
        EClass rFile = cls(p, "RFile", true);
        EClass rdsFile = cls(p, "RdsFile", false);
        EClass rDataFile = cls(p, "RDataFile", false);
        EClass rBinding = cls(p, "RBinding", false);
        EClass rObject = cls(p, "RObject", false);
        EClass rValue = cls(p, "RValue", false);
        EClass rWorkspace = cls(p, "RWorkspace", false);
        EStructuralFeature f;
        EEnumLiteral l;

        // SexpType
        doc(sexpType, "The SEXPTYPEs R uses internally. The numeric values are exactly the codes the "
                + "serialization stream carries in bits 0:7 of the header integer. Codes 11 and 12 are "
                + "missing: they were assigned to internal factors and ordered factors and have since "
                + "been withdrawn.");
        l = literal(sexpType, "r_nil", 0);
        doc(l, "NILSXP - NULL.");
        l = literal(sexpType, "r_symbol", 1);
        doc(l, "SYMSXP - a symbol (name).");
        l = literal(sexpType, "r_pairlist", 2);
        doc(l, "LISTSXP - a pairlist.");
        l = literal(sexpType, "r_closure", 3);
        doc(l, "CLOSXP - a closure.");
        l = literal(sexpType, "r_environment", 4);
        doc(l, "ENVSXP - an environment.");
        l = literal(sexpType, "r_promise", 5);
        doc(l, "PROMSXP - a promise (deferred evaluation).");
        l = literal(sexpType, "r_language", 6);
        doc(l, "LANGSXP - a language object (an unevaluated call).");
        l = literal(sexpType, "r_special", 7);
        doc(l, "SPECIALSXP - a special function.");
        l = literal(sexpType, "r_builtin", 8);
        doc(l, "BUILTINSXP - a builtin function.");
        l = literal(sexpType, "r_char", 9);
        doc(l, "CHARSXP - an internal character string; carries the encoding flag.");
        l = literal(sexpType, "r_logical", 10);
        doc(l, "LGLSXP - a logical vector.");
        l = literal(sexpType, "r_integer", 13);
        doc(l, "INTSXP - an integer vector.");
        l = literal(sexpType, "r_real", 14);
        doc(l, "REALSXP - a numeric (double) vector.");
        l = literal(sexpType, "r_complex", 15);
        doc(l, "CPLXSXP - a complex vector.");
        l = literal(sexpType, "r_character", 16);
        doc(l, "STRSXP - a character vector whose elements are CHARSXPs.");
        l = literal(sexpType, "r_dots", 17);
        doc(l, "DOTSXP - the ... object.");
        l = literal(sexpType, "r_any", 18);
        doc(l, "ANYSXP - a placeholder that makes 'any' arguments work.");
        l = literal(sexpType, "r_list", 19);
        doc(l, "VECSXP - a generic vector, seen in R as a list.");
        l = literal(sexpType, "r_expression", 20);
        doc(l, "EXPRSXP - an expression vector.");
        l = literal(sexpType, "r_bytecode", 21);
        doc(l, "BCODESXP - byte code.");
        l = literal(sexpType, "r_externalptr", 22);
        doc(l, "EXTPTRSXP - an external pointer.");
        l = literal(sexpType, "r_weakref", 23);
        doc(l, "WEAKREFSXP - a weak reference.");
        l = literal(sexpType, "r_raw", 24);
        doc(l, "RAWSXP - a raw byte vector.");
        l = literal(sexpType, "r_object", 25);
        doc(l, "OBJSXP - an object not of a simple type (formerly S4SXP).");

        // SerializationFormat
        doc(serializationFormat, "The form in which the serialization stream is written; identified by the first byte "
                + "of the stream.");
        l = literal(serializationFormat, "r_xdr", 0);
        doc(l, "'X' - binary in big-endian order. The default of saveRDS and save.");
        l = literal(serializationFormat, "r_ascii", 1);
        doc(l, "'A' - a textual form, readable independently of the platform.");
        l = literal(serializationFormat, "r_binary", 2);
        doc(l, "'B' - binary in the native byte order of the writing machine.");

        // CompressionKind
        doc(compressionKind, "The compression wrapped around the whole serialization stream. R detects it from "
                + "magic bytes when reading, so it is not part of the serialization header.");
        l = literal(compressionKind, "r_none", 0);
        doc(l, "No compression.");
        l = literal(compressionKind, "r_gzip", 1);
        doc(l, "gzip - the default of saveRDS and save.");
        l = literal(compressionKind, "r_bzip2", 2);
        doc(l, "bzip2.");
        l = literal(compressionKind, "r_xz", 3);
        doc(l, "xz.");

        // RowNamesKind
        doc(rowNamesKind, "The form in which a data.frame carries its row.names attribute.");
        l = literal(rowNamesKind, "r_automatic", 0);
        doc(l, "The compact form c(NA, -n): the rows are simply numbered 1 to n and are not written "
                + "out at all.");
        l = literal(rowNamesKind, "r_integer", 1);
        doc(l, "An integer vector written out in full.");
        l = literal(rowNamesKind, "r_character", 2);
        doc(l, "A character vector carrying meaningful row names.");

        // StringEncoding
        doc(stringEncoding, "The encoding flag of a CHARSXP. R keeps it in bits of the gp field. ASCII strings "
                + "never carry a flag, because any flag would be ignored when the string is entered into "
                + "the CHARSXP cache.");
        l = literal(stringEncoding, "r_native", 0);
        doc(l, "Unflagged - to be read in the native encoding.");
        l = literal(stringEncoding, "r_latin1", 1);
        doc(l, "Flagged as Latin-1.");
        l = literal(stringEncoding, "r_utf8", 2);
        doc(l, "Flagged as UTF-8.");
        l = literal(stringEncoding, "r_bytes", 3);
        doc(l, "Flagged as bytes, with no encoding implied.");
        l = literal(stringEncoding, "r_ascii", 4);
        doc(l, "Pure ASCII.");

        // SpecialAttributeKind
        doc(specialAttributeKind, "Distinguishes the attributes the interpreter treats specially from the free-form "
                + "ones. Only these eight are inspected and used deliberately by R's internal functions; "
                + "every other attribute is a mere annotation.");
        l = literal(specialAttributeKind, "r_names", 0);
        doc(l, "names - the element names of a vector or list.");
        l = literal(specialAttributeKind, "r_dim", 1);
        doc(l, "dim - the extents; turns a vector into a matrix or array.");
        l = literal(specialAttributeKind, "r_dimnames", 2);
        doc(l, "dimnames - names per extent; requires dim.");
        l = literal(specialAttributeKind, "r_class", 3);
        doc(l, "class - the S3 class vector; sets the object bit.");
        l = literal(specialAttributeKind, "r_levels", 4);
        doc(l, "levels - the levels of a factor, a character vector.");
        l = literal(specialAttributeKind, "r_rowNames", 5);
        doc(l, "row.names - the row names of a data.frame.");
        l = literal(specialAttributeKind, "r_tsp", 6);
        doc(l, "tsp - start, end and frequency of a time series.");
        l = literal(specialAttributeKind, "r_comment", 7);
        doc(l, "comment - a remark suppressed when printing.");
        l = literal(specialAttributeKind, "r_other", 8);
        doc(l, "A free-form attribute the interpreter does not treat specially.");

        // RType
        doc(rType, "Common superclass of every described R structure. An RType describes the shape of an "
                + "R value, not the value itself. Because every higher R structure consists of a base "
                + "type with attributes attached, this superclass already carries the SEXPTYPE and the "
                + "S3 class vector.");
        rType.getESuperTypes().add((EClass) cwm(CORE, "Classifier"));
        f = attr(rType, "sexpType", sexpType, 1, 1);
        doc(f, "The base type of the described value.");
        f = attr(rType, "rClass", cwm(CORE, "String"), 0, -1);
        doc(f, "The content of the class attribute, in the order R keeps it: from the most specific "
                + "to the most general class. Empty when the value carries no class attribute and its "
                + "implicit class therefore applies. The order matters, because S3 method dispatch works "
                + "through it from left to right.");
        f = attr(rType, "isObject", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when the object bit is set. R sets it exactly when a class attribute has been "
                + "assigned; the serialization stream carries it as a separate bit in the header integer.");

        // RVectorType
        doc(rVectorType, "An atomic vector - logical, integer, double, complex, character or raw. R has no "
                + "scalar: a single value is a vector of length one.");
        rVectorType.getESuperTypes().add((EClass) rType);
        f = attr(rVectorType, "length", cwm(CORE, "Integer"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The number of elements, where it is known at description time. Left unset when the "
                + "length only arises at run time.");
        f = attr(rVectorType, "hasNames", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when the vector carries a names attribute and its elements can therefore be "
                + "addressed by name.");
        f = attr(rVectorType, "encoding", stringEncoding, 0, 1);
        f.setUnsettable(true);
        doc(f, "The encoding flag of the elements. Meaningful only for sexpType r_character; "
                + "irrelevant for every other vector type.");
        f = attr(rVectorType, "allowsNA", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when the vector may contain missing values. R has a distinct NA value per base "
                + "type; NA_real_ is a NaN with a special payload, which is what keeps it "
                + "distinguishable from an ordinary NaN.");

        // RArrayType
        ann(rArrayType, ECORE,
                "constraints", "PositiveDimensions");
        ann(rArrayType, ECORE_OCL,
                "PositiveDimensions", "self.dim->forAll(d | d >= 0)");
        ann(rArrayType, CONSTRAINTS_NS,
                "PositiveDimensions.name", "Positive Dimensions",
                "PositiveDimensions.description", "Every extent of an array is non-negative.",
                "PositiveDimensions.level", "error");
        doc(rArrayType, "An atomic vector carrying a dim attribute. Two extents make a matrix in R, more make "
                + "an array; there is no separate matrix type. The elements are laid out column-major in "
                + "the underlying vector.");
        rArrayType.getESuperTypes().add((EClass) rVectorType);
        f = attr(rArrayType, "dim", cwm(CORE, "Integer"), 1, -1);
        doc(f, "The extents in the order of the dim attribute. The order matters: it determines which "
                + "index varies fastest.");
        f = attr(rArrayType, "hasDimnames", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when a dimnames attribute is present. It is a list with as many elements as dim "
                + "has entries; individual elements may be NULL.");

        // RFactorType
        doc(rFactorType, "A factor: an integer vector whose values are positions into a character vector "
                + "levels, carrying class=\"factor\" or class=c(\"ordered\",\"factor\"). On the R side a "
                + "factor is not a base type of its own but exactly this combination.");
        rFactorType.getESuperTypes().add((EClass) rVectorType);
        f = attr(rFactorType, "isOrdered", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True for an ordered factor, whose class vector additionally carries \"ordered\" and "
                + "whose levels are comparable.");
        f = ref(rFactorType, "levelSet", cwm(DATATYPES, "Enumeration"), 1, 1);
        oppositeRole(f, "factorType");
        doc(f, "The levels of the factor as a foundation/datatypes::Enumeration. The integer values "
                + "of the vector are the positions of its literals. The Enumeration carries no opposite "
                + "feature: it belongs to the CWM core, to which an extension may not add anything.");

        // RListType
        doc(rListType, "A generic vector (VECSXP). Its elements may be of any type and of differing types, "
                + "which is what makes the list the carrier of every composite structure in R. The "
                + "elements are the inherited, ordered features.");
        rListType.getESuperTypes().add((EClass) rType);
        f = attr(rListType, "hasNames", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when the list carries a names attribute. A list may be partially named; unnamed "
                + "positions then carry an empty string.");

        // RDataFrameType
        ann(rDataFrameType, ECORE,
                "constraints", "ColumnsAreRTypes");
        ann(rDataFrameType, ECORE_OCL,
                "ColumnsAreRTypes", "self.feature->select(f | f.oclIsKindOf(core::StructuralFeature))->forAll(f | "
                    + "f.oclAsType(core::StructuralFeature).type.oclIsKindOf(RType))");
        ann(rDataFrameType, CONSTRAINTS_NS,
                "ColumnsAreRTypes.name", "Columns Are R Types",
                "ColumnsAreRTypes.description", "Every column of a data.frame is typed with an RType.",
                "ColumnsAreRTypes.level", "error");
        doc(rDataFrameType, "A data.frame: a generic list of equally long columns carrying the attributes names, "
                + "row.names and class=\"data.frame\". R has no base type of its own for it - length "
                + "yields the number of columns and names the column names, precisely because a "
                + "data.frame is a list. This is the R counterpart of what resource/relational calls a "
                + "Table and resource/record a RecordDef.");
        rDataFrameType.getESuperTypes().add((EClass) rListType);
        f = attr(rDataFrameType, "rowCount", cwm(CORE, "Integer"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The number of rows, where known. All columns necessarily have the same length.");
        f = attr(rDataFrameType, "rowNamesKind", rowNamesKind, 1, 1);
        doc(f, "The form in which the row.names attribute is present.");
        f = attr(rDataFrameType, "isTibble", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when the class vector additionally carries \"tbl_df\" and \"tbl\". A tibble remains "
                + "a data.frame but changes the behaviour of printing and subsetting.");

        // RS4Type
        doc(rS4Type, "An S4 class. Its slots are the inherited features; unlike S3 they are declared with "
                + "their types and are checked. In the stream such a value appears with the code OBJSXP.");
        rS4Type.getESuperTypes().add((EClass) rType);
        f = attr(rS4Type, "className", cwm(CORE, "String"), 1, 1);
        doc(f, "The class name given to setClass.");
        f = attr(rS4Type, "packageName", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The package holding the class definition. R keeps it as a package attribute on the "
                + "class attribute, so that classes of the same name from different packages remain "
                + "distinguishable.");

        // ROpaqueType
        doc(rOpaqueType, "A value whose internal structure is deliberately left undescribed here: closures, "
                + "environments, promises, language objects, byte code, external pointers and weak "
                + "references. They do occur in RDS files, but they are program text or run-time state "
                + "rather than data; their base type stays visible through sexpType.");
        rOpaqueType.getESuperTypes().add((EClass) rType);

        // RComponent
        doc(rComponent, "An element of a list, a column of a data.frame or a slot of an S4 class. The type is "
                + "held by the inherited reference StructuralFeature::type, the owner by Feature::owner; "
                + "the order follows from the ordered containment reference Classifier::feature.");
        rComponent.getESuperTypes().add((EClass) cwm(CORE, "Attribute"));
        f = attr(rComponent, "position", cwm(CORE, "Integer"), 1, 1);
        doc(f, "The zero-based position within the owner. R addresses list elements through [[i]] "
                + "one-based; converting is left to the reading tool.");
        f = attr(rComponent, "isNamed", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when the position carries a non-empty name in the names attribute. If it is "
                + "unnamed, the inherited mandatory attribute name carries a substitute derived from the "
                + "position.");

        // RAttribute
        doc(rAttribute, "An attribute attached to an R value. Attributes are the only extension point of R's "
                + "type system: everything beyond the base types - matrix, factor, data.frame, time "
                + "series - arises solely from attaching attributes to a vector.");
        rAttribute.getESuperTypes().add((EClass) cwm(CORE, "Attribute"));
        f = attr(rAttribute, "kind", specialAttributeKind, 1, 1);
        doc(f, "Whether this is one of the attributes the interpreter treats specially, and which one.");
        f = attr(rAttribute, "attributeValue", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The value of the attribute in printed form, where it is short enough to be held here. "
                + "Larger values are described through the inherited type reference instead.");

        // RFile
        doc(rFile, "Common superclass of the two file forms. It carries what both have in common: the "
                + "layout of the serialization stream. The described types are the inherited "
                + "ownedElement.");
        rFile.getESuperTypes().add((EClass) cwm(CORE, "Package"));
        f = attr(rFile, "serializationFormat", serializationFormat, 1, 1);
        doc(f, "The form, identified by the first byte of the stream.");
        f = attr(rFile, "serializationVersion", cwm(CORE, "Integer"), 1, 1);
        f.setDefaultValueLiteral("2");
        doc(f, "The serialization version from the header: 2 for the form used by R 1.4.0 through "
                + "3.5.3, 3 for the form from R 3.5.0 on with ALTREP support.");
        f = attr(rFile, "writerVersion", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The R version that wrote the file, from the second header integer.");
        f = attr(rFile, "minimumReaderVersion", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The oldest R version still able to read the file, from the third header integer.");
        f = attr(rFile, "nativeEncoding", cwm(CORE, "String"), 0, 1);
        f.setUnsettable(true);
        doc(f, "The native encoding in force at write time. Only version 3 records it, so that "
                + "unflagged strings can be converted when read under a different native encoding.");
        f = attr(rFile, "compression", compressionKind, 1, 1);
        doc(f, "The compression wrapped around the stream.");

        // RdsFile
        doc(rdsFile, "An RDS file written by saveRDS. It contains exactly one serialized object and no name "
                + "- the reader assigns one when calling readRDS. That is the difference from RData and "
                + "the reason RDS suits the exchange of individual datasets.");
        rdsFile.getESuperTypes().add((EClass) rFile);
        f = ref(rdsFile, "rootType", rType, 1, 1);
        oppositeRole(f, "rdsFile");
        doc(f, "The type of the one stored object. It is at the same time one of the ownedElement of "
                + "this file. The opposite end is not a feature: navigating from a type to its file is "
                + "needed nowhere.");

        // RDataFile
        doc(rDataFile, "An RData or .rda file written by save or save.image. It contains named bindings taken "
                + "from an R environment; load restores them under exactly those names and silently "
                + "overwrites objects of the same name in the target. The bindings are the inherited "
                + "ownedElement.");
        rDataFile.getESuperTypes().add((EClass) rFile);

        // RBinding
        doc(rBinding, "A named binding in an RData file: a symbol together with the value bound to it. The "
                + "name is held by the inherited mandatory attribute name.");
        rBinding.getESuperTypes().add((EClass) cwm(CORE, "ModelElement"));
        f = ref(rBinding, "type", rType, 0, 1);
        oppositeRole(f, "binding");
        doc(f, "The type of the bound value.");
        f = attr(rBinding, "isHidden", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when the name begins with a dot. ls() hides such objects by default, but they "
                + "are saved and loaded along with the rest.");

        // RObject
        doc(rObject, "An actual R value. The describing RType is held by the inherited reference "
                + "Instance::classifier.");
        rObject.getESuperTypes().add((EClass) cwm(INSTANCE, "Object"));

        // RValue
        doc(rValue, "A single element of an atomic vector.");
        rValue.getESuperTypes().add((EClass) cwm(INSTANCE, "DataValue"));
        f = attr(rValue, "isNA", cwm(CORE, "Boolean"), 1, 1);
        doc(f, "True when the element is the NA value of its base type. R has a distinct one per "
                + "type: NA, NA_integer_, NA_real_, NA_character_. Because NA_real_ is a NaN with its "
                + "own payload, the distinction is lost on a plain comparison and has to be carried "
                + "along.");

        // RWorkspace
        doc(rWorkspace, "The totality of values of a loaded RData file, that is, the restored image of an R "
                + "environment.");
        rWorkspace.getESuperTypes().add((EClass) cwm(INSTANCE, "Extent"));

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
