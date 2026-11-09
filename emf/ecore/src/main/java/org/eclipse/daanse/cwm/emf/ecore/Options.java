/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.emf.ecore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Settings of a {@link CwmToEcore} conversion. Immutable; every {@code with…} returns a copy.
 *
 * <ul>
 * <li>{@link #nsUriBase()} — prefix of every generated {@code nsURI}; the package path follows,
 * lower case, one segment per CWM namespace from the model root.</li>
 * <li>{@link #dataTypes()} — CWM {@code DataType} name to Java instance class name; the
 * {@linkplain #defaultDataTypes() defaults} come first, entries given here win. A TaggedValue
 * {@code daanse.ecore.instanceClassName} on the data type wins over both.</li>
 * <li>{@link #reuseEcoreDataTypes()} — map a data type to the {@code EcorePackage} data type with
 * the same instance class instead of generating an {@code EDataType}.</li>
 * <li>{@link #packages()} — qualified names (dot separated, or a trailing part of them) of the
 * packages to convert; empty means every package that holds classes, data types or
 * enumerations.</li>
 * <li>{@link #validate()} — run the {@code Diagnostician} over the result and report its errors as
 * notes.</li>
 * <li>{@link #splitPackages()} — every converted package becomes a root EPackage of its own, so
 * {@link EcoreWriter} writes it into a file of its own, instead of a sub-package in the file of
 * the package around it; namespace URIs and names stay the same.</li>
 * <li>{@link #oclDelegateUri()} — the annotation source and delegate URI under which OCL
 * invariants and OCL operation bodies are written; the default is the classic Eclipse OCL
 * delegate the CWM metamodels of this repository use, {@value #DEFAULT_OCL_DELEGATE_URI}; Pivot
 * OCL reads {@code http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot}.</li>
 * <li>{@link #documentation()} — write the CWM descriptions as GenModel {@code documentation}
 * annotations, which the EMF generator copies into the Javadoc.</li>
 * <li>{@link #constraints()} — make the OCL and Java constraints of the class model invariants of
 * the classifiers they constrain.</li>
 * <li>{@link #basePackage()} — the Java base package of the generated code, written as GenModel
 * {@code basePackage} on every root EPackage.</li>
 * <li>{@link #genModelAnnotations()} — further GenModel properties (e.g. {@code oSGiCompatible},
 * {@code copyrightText}, {@code loadInitialization}) written onto every root EPackage, where EMF
 * code generation reads them.</li>
 * </ul>
 */
public final class Options {

    /** Default prefix of the generated namespace URIs. */
    public static final String DEFAULT_NS_URI_BASE = "http://daanse.eclipse.org/cwm/ecore";
    /** Default OCL delegate URI: classic (LPG) Eclipse OCL. */
    public static final String DEFAULT_OCL_DELEGATE_URI = "http://www.eclipse.org/emf/2002/Ecore/OCL";

    private static final Map<String, String> DEFAULT_DATA_TYPES = builtInDataTypes();
    private static final Set<String> DERIVED_GENMODEL_KEYS = Set.of("basePackage", "prefix");

    private String nsUriBase = DEFAULT_NS_URI_BASE;
    private Map<String, String> dataTypes = Map.of();
    private boolean reuseEcoreDataTypes;
    private List<String> packages = List.of();
    private boolean validate = true;
    private String oclDelegateUri = DEFAULT_OCL_DELEGATE_URI;
    private boolean splitPackages;
    private boolean documentation = true;
    private boolean constraints = true;
    private String basePackage;
    private Map<String, String> genModelAnnotations = Map.of();

    private Options() {
    }

    private Options copy() {
        Options o = new Options();
        o.nsUriBase = nsUriBase;
        o.dataTypes = dataTypes;
        o.reuseEcoreDataTypes = reuseEcoreDataTypes;
        o.packages = packages;
        o.validate = validate;
        o.oclDelegateUri = oclDelegateUri;
        o.splitPackages = splitPackages;
        o.documentation = documentation;
        o.constraints = constraints;
        o.basePackage = basePackage;
        o.genModelAnnotations = genModelAnnotations;
        return o;
    }

    /** The default settings. */
    public static Options defaults() {
        return new Options();
    }

    /** Prefix of the generated namespace URIs. */
    public String nsUriBase() {
        return nsUriBase;
    }

    /** The data type mapping in effect: the defaults overlaid with the entries given. */
    public Map<String, String> dataTypes() {
        Map<String, String> all = new LinkedHashMap<>(DEFAULT_DATA_TYPES);
        all.putAll(dataTypes);
        return all;
    }

    /** Whether data types map onto {@code EcorePackage} data types. */
    public boolean reuseEcoreDataTypes() {
        return reuseEcoreDataTypes;
    }

    /** The packages to convert; empty means all suitable packages. */
    public List<String> packages() {
        return packages;
    }

    /** Whether the result is validated. */
    public boolean validate() {
        return validate;
    }

    /** The OCL delegate URI of the invariants and operation bodies written. */
    public String oclDelegateUri() {
        return oclDelegateUri;
    }

    /** Whether every converted package becomes a root EPackage, written into a file of its own. */
    public boolean splitPackages() {
        return splitPackages;
    }

    /** Whether the CWM descriptions become GenModel {@code documentation} annotations. */
    public boolean documentation() {
        return documentation;
    }

    /** Whether the CWM constraints become invariants. */
    public boolean constraints() {
        return constraints;
    }

    /** The Java base package written as GenModel {@code basePackage}, or {@code null}. */
    public String basePackage() {
        return basePackage;
    }

    /**
     * The further GenModel properties written onto every root EPackage, sorted by key; without
     * {@code basePackage} and {@code prefix}, which come from their own settings.
     */
    public Map<String, String> genModelAnnotations() {
        Map<String, String> written = new TreeMap<>(genModelAnnotations);
        written.keySet().removeAll(DERIVED_GENMODEL_KEYS);
        return written;
    }

    /** The keys given among the further GenModel properties that are not written. */
    public List<String> ignoredGenModelAnnotations() {
        return DERIVED_GENMODEL_KEYS.stream().filter(genModelAnnotations::containsKey).sorted().toList();
    }

    /** A copy with another namespace URI prefix. */
    public Options withNsUriBase(String base) {
        Options o = copy();
        o.nsUriBase = base == null || base.isBlank() ? DEFAULT_NS_URI_BASE : base;
        return o;
    }

    /** A copy with additional data type entries (name to Java instance class name). */
    public Options withDataTypes(Map<String, String> entries) {
        Map<String, String> merged = new LinkedHashMap<>(dataTypes);
        if (entries != null) {
            merged.putAll(entries);
        }
        Options o = copy();
        o.dataTypes = Map.copyOf(merged);
        return o;
    }

    /** A copy with one additional data type entry. */
    public Options withDataType(String name, String instanceClassName) {
        return withDataTypes(Map.of(name, instanceClassName));
    }

    /** A copy that reuses (or not) the {@code EcorePackage} data types. */
    public Options withReuseEcoreDataTypes(boolean reuse) {
        Options o = copy();
        o.reuseEcoreDataTypes = reuse;
        return o;
    }

    /** A copy that converts only the named packages. */
    public Options withPackages(List<String> names) {
        Options o = copy();
        o.packages = names == null ? List.of() : List.copyOf(names);
        return o;
    }

    /** A copy that validates (or not) the result. */
    public Options withValidate(boolean on) {
        Options o = copy();
        o.validate = on;
        return o;
    }

    /** A copy that writes OCL under another delegate URI; blank means the default. */
    public Options withOclDelegateUri(String uri) {
        Options o = copy();
        o.oclDelegateUri = uri == null || uri.isBlank() ? DEFAULT_OCL_DELEGATE_URI : uri;
        return o;
    }

    /** A copy that makes every converted package a root EPackage (or nests sub-packages again). */
    public Options withSplitPackages(boolean split) {
        Options o = copy();
        o.splitPackages = split;
        return o;
    }

    /** A copy that writes (or leaves out) the CWM descriptions as GenModel documentation. */
    public Options withDocumentation(boolean on) {
        Options o = copy();
        o.documentation = on;
        return o;
    }

    /** A copy that converts (or leaves out) the CWM constraints. */
    public Options withConstraints(boolean on) {
        Options o = copy();
        o.constraints = on;
        return o;
    }

    /** A copy with a Java base package for the generated code; blank means none. */
    public Options withBasePackage(String base) {
        Options o = copy();
        o.basePackage = base == null || base.isBlank() ? null : base;
        return o;
    }

    /**
     * A copy with further GenModel properties for every root EPackage, e.g.
     * {@code oSGiCompatible=true}. {@code basePackage} and {@code prefix} are not taken from here:
     * the base package has its own setting and the prefix is derived per package.
     */
    public Options withGenModelAnnotations(Map<String, String> entries) {
        Options o = copy();
        o.genModelAnnotations = entries == null ? Map.of() : Map.copyOf(entries);
        return o;
    }

    /**
     * The built-in data type mapping: CWM data type names and the usual Java type names to Java
     * instance class names. A CWM {@code Integer} becomes {@code java.lang.Long} (CWM integers are
     * not bounded to 32 bit); primitive names stay primitive.
     */
    public static Map<String, String> defaultDataTypes() {
        return DEFAULT_DATA_TYPES;
    }

    private static Map<String, String> builtInDataTypes() {
        Map<String, String> m = new LinkedHashMap<>();
        // CWM and SQL names
        m.put("Integer", "java.lang.Long");
        m.put("String", "java.lang.String");
        m.put("Boolean", "java.lang.Boolean");
        m.put("Date", "java.util.Date");
        m.put("Decimal", "java.math.BigDecimal");
        m.put("Double", "java.lang.Double");
        m.put("Float", "java.lang.Float");
        m.put("Long", "java.lang.Long");
        m.put("Short", "java.lang.Short");
        m.put("Byte", "java.lang.Byte");
        m.put("Character", "java.lang.Character");
        m.put("Time", "java.time.LocalTime");
        m.put("Timestamp", "java.time.LocalDateTime");
        m.put("Binary", "byte[]");
        // Java names
        m.put("BigDecimal", "java.math.BigDecimal");
        m.put("BigInteger", "java.math.BigInteger");
        m.put("LocalDate", "java.time.LocalDate");
        m.put("LocalTime", "java.time.LocalTime");
        m.put("LocalDateTime", "java.time.LocalDateTime");
        m.put("OffsetDateTime", "java.time.OffsetDateTime");
        m.put("Instant", "java.time.Instant");
        m.put("UUID", "java.util.UUID");
        m.put("int", "int");
        m.put("long", "long");
        m.put("short", "short");
        m.put("byte", "byte");
        m.put("float", "float");
        m.put("double", "double");
        m.put("boolean", "boolean");
        m.put("char", "char");
        m.put("byte[]", "byte[]");
        return Map.copyOf(m);
    }
}
