/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.build;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.util.FeatureModelValidator;
import org.eclipse.daanse.cwm.model.daanse.validation.Findings;
import org.eclipse.daanse.cwm.model.daanse.validation.Rule;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

/**
 * Programmatic builder for the {@code feature} metamodel of the resource layer: a collection of features, as OGC API
 * Features names it, described once as a logical resource, independent of where its rows come from.
 *
 * <p>{@code FeatureCollection} is a {@code core::Class}, its features are {@code FeatureProperty}s, each a
 * {@code core::Attribute}; the names become ids in the API. What a geometry property holds is said by the
 * {@code spatial::GeometryFeature} that names it; where the rows come from is the matter of a mapping model.
 * Construction and serialization follow {@code model/daanse/spatial/.../SpatialEcoreBuilder}.</p>
 */
public final class FeatureEcoreBuilder {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    /** The namespace URI of the package. */
    public static final String NS_URI =
            "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature";
    /** The source of the metadata of the invariants of a class: name, description and level per rule. */
    public static final String CONSTRAINTS = NS_URI + Findings.CONSTRAINTS;

    private static final String EPL_HEADER = String.join("\n",
            "Copyright (c) 2026 Contributors to the Eclipse Foundation.",
            "",
            "This program and the accompanying materials are made",
            "available under the terms of the Eclipse Public License 2.0",
            "which is available at https://www.eclipse.org/legal/epl-2.0/",
            "",
            "SPDX-License-Identifier: EPL-2.0");

    private final EcoreFactory ef = EcoreFactory.eINSTANCE;
    private final Refs cwm;

    /** The classifiers of CWM {@code core} and of {@code spatial} the metamodel refers to. */
    public static final class Refs {
        final EClass cwmClass;
        final EClass attribute;
        final EDataType stringType;
        final EDataType booleanType;
        final EDataType floatType;
        final EClass referenceSystem;
        final EClass geometryFeature;

        Refs(EPackage core, EPackage spatial) {
            this.cwmClass = eClass(core, "Class");
            this.attribute = eClass(core, "Attribute");
            this.stringType = eDataType(core, "String");
            this.booleanType = eDataType(core, "Boolean");
            this.floatType = eDataType(core, "Float");
            this.referenceSystem = eClass(spatial, "ReferenceSystem");
            this.geometryFeature = eClass(spatial, "GeometryFeature");
        }
    }

    public FeatureEcoreBuilder(Refs cwm) {
        this.cwm = cwm;
    }

    /**
     * Builds the package from the CWM Ecores and the {@code spatial} Ecore next to {@code moduleDir} (module directory
     * {@code model/daanse/resource.spatial.feature}) and writes it to {@code outFile}.
     */
    public static void generate(File moduleDir, File outFile) throws IOException {
        File cwmDir = new File(moduleDir, "../../cwm").getCanonicalFile();
        File spatialDir = new File(moduleDir, "../spatial").getCanonicalFile();
        ResourceSet rs = newResourceSet();
        EPackage core = loadPackage(rs, ecoreUri(cwmDir, "objectmodel.core/model/core.ecore"));
        EPackage spatial = loadPackage(rs, ecoreUri(spatialDir, "model/spatial.ecore"));
        outFile.getParentFile().mkdirs();
        save(new FeatureEcoreBuilder(new Refs(core, spatial)).build(), rs,
                URI.createFileURI(outFile.getAbsolutePath()));
    }

    /** Creates the EPackage {@code feature}: 2 classes, no data types of its own. */
    public EPackage build() {
        EPackage p = ef.createEPackage();
        p.setName("feature");
        p.setNsURI(NS_URI);
        p.setNsPrefix("feature");
        EAnnotation pa = annotate(p,
                "Collections of features, as OGC API Features names them, as CWM classes: a FeatureCollection (a "
                        + "core::Class, owned by a core::Package) has its FeatureProperties (core::Attributes, typed "
                        + "by a data type) as features; the names become the ids of the collection and of its "
                        + "properties. The collection names the property that is the feature id, its primary geometry "
                        + "(a spatial::GeometryFeature, which says what the geometry is and whose holdings name the "
                        + "properties that hold it: a property of the type spatial::GeometryType, or numeric "
                        + "properties such as east and north), the properties of its time (an instant, or the start "
                        + "and the end of an interval, the roles of OGC API Features Part 5) and the reference "
                        + "systems it offers; a property says whether it is queryable. "
                        + "Where the rows come from (a table, an SQL statement, the members of a cube) is described by "
                        + "a mapping model; the collection and its queryables never change with the origin. What this "
                        + "package leaves open is checked by FeatureModelValidator, not filled in. Non-containment "
                        + "references are hidden association ends (Property.oppositeRoleName, prefix feature).");
        pa.getDetails().put("modelName", "Feature");
        pa.getDetails().put("copyrightText", EPL_HEADER);
        pa.getDetails().put("oSGiCompatible", "true");
        pa.getDetails().put("basePackage", "org.eclipse.daanse.cwm.model.daanse.resource.spatial");
        pa.getDetails().put("resource", "XMI");
        pa.getDetails().put("prefix", "Feature");

        EAnnotation delegates = ef.createEAnnotation();
        delegates.setSource(ECORE);
        delegates.getDetails().put("invocationDelegates", ECORE_OCL);
        delegates.getDetails().put("settingDelegates", ECORE_OCL);
        delegates.getDetails().put("validationDelegates", ECORE_OCL);
        p.getEAnnotations().add(delegates);
        EAnnotation ho = ef.createEAnnotation();
        ho.setSource(ECORE_OCL);
        ho.getDetails().put("hiddenOpposites", "true");
        p.getEAnnotations().add(ho);

        EClass collection = cls(p, "FeatureCollection",
                "A collection of features: its name is the collection id (/collections/{name}), a Description of "
                        + "type documentation its description, displayName its title. Its features are its "
                        + "properties. It names the property whose value is the feature id, the primary geometry "
                        + "(the one bbox is evaluated on, held in properties of the collection), the time of a "
                        + "feature (an instant, or the start and the end of an interval, either of which may stand "
                        + "alone), and the reference systems it offers besides the default and the storage system. "
                        + "The schools of a city are a collection with the key school_id, the primary geometry "
                        + "schoolPoint held in the property location, and the instant opened. The extent is not "
                        + "held: it is computed from the rows.");
        collection.getESuperTypes().add(cwm.cwmClass);
        EClass property = cls(p, "FeatureProperty",
                "A property of the features of a collection: a core::Attribute typed by a data type, a "
                        + "spatial::GeometryType where it holds a whole geometry (a holding of a "
                        + "spatial::GeometryFeature names it and says what the geometry is); a number of a geometry "
                        + "held in parts, such as east, keeps its numeric type. Its name is the property name in the "
                        + "features and in the queryables; "
                        + "it says whether it is queryable and gives what the type does not say: a closed list of "
                        + "values, numeric bounds, a pattern. Nullability is the attribute's multiplicity, the unit a "
                        + "units::Quantity that names the property, the description a Description of type "
                        + "documentation.");
        property.getESuperTypes().add(cwm.attribute);

        attr(collection, "displayName", cwm.stringType, 0, 1, "The title of the collection; unset, its name.");
        EReference key = ref(collection, "featureKey", property, 1, 1,
                "The property whose value is the feature id (/items/{featureId}). Its uniqueness is a key of the "
                        + "origin; the collection says only which property it is.");
        oppositeRole(key, "featureKeyOfCollection");
        EReference geometry = ref(collection, "geometry", cwm.geometryFeature, 0, 1,
                "The primary geometry: the one bbox is evaluated on and the extent is about. A "
                        + "spatial::GeometryFeature, at least one of whose holdings lies entirely in properties of "
                        + "this collection: the property location of the type Geometry, or the properties east and "
                        + "north that hold a point in parts; the server writes the geometry of a feature from it. "
                        + "Unset, a collection without geometry.");
        oppositeRole(geometry, "featureGeometryOfCollection");
        EReference instant = ref(collection, "instant", property, 0, 1,
                "The instant of a feature (the role primary-instant of OGC API Features Part 5): the property "
                        + "datetime is evaluated on. A collection names an instant or the ends of an interval, not "
                        + "both.");
        oppositeRole(instant, "featureInstantOfCollection");
        EReference start = ref(collection, "intervalStart", property, 0, 1,
                "The start of the interval of a feature (primary-interval-start). Alone, without an end, the "
                        + "interval is half-bounded: from the start on, open to the future. A value that is null in "
                        + "a row is an open start.");
        oppositeRole(start, "featureIntervalStartOfCollection");
        EReference end = ref(collection, "intervalEnd", property, 0, 1,
                "The end of the interval of a feature (primary-interval-end). Alone, without a start, the "
                        + "interval is half-bounded: open to the past, up to the end. A value that is null in a row "
                        + "is an open end.");
        oppositeRole(end, "featureIntervalEndOfCollection");
        attr(collection, "timeZone", cwm.stringType, 0, 1,
                "The time zone (an IANA name or an offset) in which the values of a time property without a zone "
                        + "are read; unset, UTC.");
        attr(collection, "endInclusive", cwm.booleanType, 0, 1,
                "Whether a feature whose end equals the start of a requested interval still meets it.")
                .setDefaultValueLiteral("true");
        EReference systems = ref(collection, "referenceSystems", cwm.referenceSystem, 0, -1,
                "The reference systems offered besides the default (CRS84, or CRS84h for a geometry with a "
                        + "height) and the storage system, which is the one of the primary geometry; in this order "
                        + "they follow the default and the storage system in the list crs.");
        systems.setOrdered(true);
        oppositeRole(systems, "featureCollectionOffering");
        attr(collection, "openProperties", cwm.booleanType, 0, 1,
                "False: only the properties the collection names may be filtered on (the queryables say "
                        + "additionalProperties false); true: a property it does not name may be, and the origin "
                        + "decides whether it exists.")
                .setDefaultValueLiteral("false");

        attr(property, "displayName", cwm.stringType, 0, 1, "The title in the queryables; unset, the name.");
        attr(property, "queryable", cwm.booleanType, 0, 1,
                "Whether the property appears in the queryables and may be used in a filter.")
                .setDefaultValueLiteral("true");
        attr(property, "returnable", cwm.booleanType, 0, 1,
                "Whether the property appears in a feature; false for a property used only to filter. The "
                        + "returnables of OGC API Features Part 5 (Schemas), which Part 6 (Property Selection) "
                        + "selects from.")
                .setDefaultValueLiteral("true");
        attr(property, "sortable", cwm.booleanType, 0, 1,
                "Whether the property may be named in a sort parameter: the sortables of OGC API Features Part 5 "
                        + "(Schemas), which Part 8 (Sorting) sorts by.")
                .setDefaultValueLiteral("false");
        EAttribute values = attr(property, "allowedValues", cwm.stringType, 0, -1,
                "The closed list of values, as text; stated only where the type does not list them.");
        values.setOrdered(true);
        attr(property, "minimum", cwm.floatType, 0, 1, "The smallest value, a decimal number; numeric types only.");
        attr(property, "maximum", cwm.floatType, 0, 1, "The largest value, a decimal number; numeric types only.");
        attr(property, "pattern", cwm.stringType, 0, 1,
                "A regular expression every value matches; text types only.");
        invariants(collection, property);
        return p;
    }

    /**
     * The rules of {@link FeatureModelValidator} the metamodel states, each an OCL invariant of the class it is about,
     * named by the code of the rule; the other rules ({@link FeatureModelValidator#CHECKED_IN_JAVA}) cannot be stated
     * in OCL and stay with the validator.
     */
    private void invariants(EClass collection, EClass property) {
        String plainId = "self.name.oclIsUndefined() or self.name.matches('[A-Za-z0-9_.-]+')";
        constrain(collection, FeatureModelValidator.FC1, "StandsInAPackageAndOwnsProperties",
                "not self.namespace.oclIsUndefined() and self.namespace.oclIsKindOf(core::Package) "
                        + "and self.feature->forAll(f | f.oclIsKindOf(FeatureProperty)) "
                        + "and self.feature->select(f | not f.name.oclIsUndefined())->isUnique(f | f.name)");
        constrain(collection, FeatureModelValidator.FC2, "PlainId", plainId);
        constrain(collection, FeatureModelValidator.FC5, "TimeOfItsOwnProperties",
                "(self.instant.oclIsUndefined() or self.instant.owner = self) "
                        + "and (self.intervalStart.oclIsUndefined() or self.intervalStart.owner = self) "
                        + "and (self.intervalEnd.oclIsUndefined() or self.intervalEnd.owner = self)");
        constrain(collection, FeatureModelValidator.FC13, "ThreeAxesOnlyWithAHeight", "self.geometry.oclIsUndefined() "
                + "or self.geometry.hasHeight or self.referenceSystems->forAll(s | s.axes <> 3)");
        constrain(collection, FeatureModelValidator.FC14, "NeitherTheDefaultNorTheStorageSystemOffered",
                "self.geometry.oclIsUndefined() or self.referenceSystems->forAll(s | "
                        + "not (s.authority = spatial::ReferenceSystemAuthority::OGC and not s.code.oclIsUndefined() "
                        + "and Set{'CRS84', 'CRS84h'}->includes(s.code.trim())) "
                        + "and (self.geometry.referenceSystem.oclIsUndefined() or not "
                        + sameSystem("s", "self.geometry.referenceSystem") + "))");
        constrain(collection, FeatureModelValidator.FC16, "AQueryableBesideTheKey",
                "self.feature->exists(f | f.oclIsKindOf(FeatureProperty) and f <> self.featureKey "
                        + "and f.oclAsType(FeatureProperty).queryable)");
        constrain(collection, FeatureModelValidator.FC19, "ThePrimaryGeometryHeldInItsProperties",
                "self.geometry.oclIsUndefined() or self.geometry.holding->exists(h | "
                        + "let attributes : Set(core::Attribute) = " + attributes("h") + " in "
                        + "attributes->notEmpty() and attributes->forAll(a | a.owner = self))");

        constrain(property, FeatureModelValidator.FC1, "TypedAndInACollection", "not self.type.oclIsUndefined() "
                + "and (self.owner.oclIsUndefined() or self.owner.oclIsKindOf(FeatureCollection))");
        constrain(property, FeatureModelValidator.FC2, "PlainId", plainId);
        constrain(property, FeatureModelValidator.FC3, "NotAQueryParameter", "self.name.oclIsUndefined() or Set{"
                + String.join(", ", FeatureModelValidator.RESERVED.stream().sorted().map(n -> "'" + n + "'").toList())
                + "}->excludes(self.name)");
    }

    /** OCL: the attributes the holding {@code holding} names, as a set; a part that names none adds none. */
    private static String attributes(String holding) {
        return "(if " + holding + ".oclIsKindOf(spatial::WholeHolding) then " + holding
                + ".oclAsType(spatial::WholeHolding).attribute->asSet() else if " + holding
                + ".oclIsKindOf(spatial::ListHolding) then " + holding
                + ".oclAsType(spatial::ListHolding).attribute->asSet() else " + holding
                + ".oclAsType(spatial::PartsHolding).part->collect(p | p.attribute)"
                + "->select(a | not a.oclIsUndefined())->asSet() endif endif)";
    }

    /**
     * OCL: whether the reference systems {@code a} and {@code b} are one: the same element, or one authority, version
     * and code, as their URIs are; a system without a version has the version 0.
     */
    private static String sameSystem(String a, String b) {
        return "(" + a + " = " + b + " or (" + a + ".authority = " + b + ".authority and " + code(a) + " = " + code(b)
                + " and " + version(a) + " = " + version(b) + "))";
    }

    private static String code(String system) {
        return "(if " + system + ".code.oclIsUndefined() then '' else " + system + ".code.trim() endif)";
    }

    private static String version(String system) {
        return "(if " + system + ".version.oclIsUndefined() or " + system + ".version.trim() = '' then '0' else "
                + system + ".version.trim() endif)";
    }

    /**
     * Adds the rule {@code rule} as an OCL invariant of {@code c}, named by the code of the rule: the Ecore constraints
     * list, the OCL expression, and its metadata in {@link #CONSTRAINTS}: a short name, the sentence of the rule as its
     * description, its severity as its level.
     */
    private void constrain(EClass c, Rule rule, String name, String ocl) {
        String code = rule.code();
        EAnnotation constraints = c.getEAnnotation(ECORE);
        if (constraints == null) {
            constraints = ef.createEAnnotation();
            constraints.setSource(ECORE);
            c.getEAnnotations().add(0, constraints);
        }
        String names = constraints.getDetails().get("constraints");
        constraints.getDetails().put("constraints", names == null ? code : names + " " + code);
        EAnnotation expressions = c.getEAnnotation(ECORE_OCL);
        if (expressions == null) {
            expressions = ef.createEAnnotation();
            expressions.setSource(ECORE_OCL);
            c.getEAnnotations().add(1, expressions);
        }
        expressions.getDetails().put(code, ocl);
        EAnnotation metadata = c.getEAnnotation(CONSTRAINTS);
        if (metadata == null) {
            metadata = ef.createEAnnotation();
            metadata.setSource(CONSTRAINTS);
            c.getEAnnotations().add(2, metadata);
        }
        metadata.getDetails().put(code + ".name", name);
        metadata.getDetails().put(code + ".description", rule.says());
        metadata.getDetails().put(code + ".level", rule.severity().name().toLowerCase(Locale.ROOT));
    }

    /** {@code Property.oppositeRoleName} pair (convention for hidden association ends). */
    private void oppositeRole(EReference r, String roleName) {
        EAnnotation a1 = ef.createEAnnotation();
        a1.setSource(EMOF);
        a1.getDetails().put("Property.oppositeRoleName", roleName);
        r.getEAnnotations().add(a1);
        EAnnotation a2 = ef.createEAnnotation();
        a2.setSource(EMOF + "#Property.oppositeRoleName");
        a2.getDetails().put("body", roleName);
        r.getEAnnotations().add(a2);
    }

    private EClass cls(EPackage pkg, String name, String doc) {
        EClass c = ef.createEClass();
        c.setName(name);
        pkg.getEClassifiers().add(c);
        annotate(c, doc);
        return c;
    }

    private EAttribute attr(EClass owner, String name, EClassifier type, int lower, int upper, String doc) {
        EAttribute a = ef.createEAttribute();
        a.setName(name);
        a.setEType(type);
        a.setLowerBound(lower);
        a.setUpperBound(upper);
        owner.getEStructuralFeatures().add(a);
        annotate(a, doc);
        return a;
    }

    private EReference ref(EClass owner, String name, EClassifier type, int lower, int upper, String doc) {
        EReference r = ef.createEReference();
        r.setName(name);
        r.setEType(type);
        r.setLowerBound(lower);
        r.setUpperBound(upper);
        owner.getEStructuralFeatures().add(r);
        annotate(r, doc);
        return r;
    }

    private EAnnotation annotate(EModelElement el, String doc) {
        EAnnotation a = ef.createEAnnotation();
        a.setSource(GENMODEL);
        a.getDetails().put("documentation", doc);
        el.getEAnnotations().add(a);
        return a;
    }

    /** A resource set reading {@code .ecore} files. */
    public static ResourceSet newResourceSet() {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
        return rs;
    }

    /**
     * Loads an Ecore; its resource URI becomes the nsURI, so that references serialize as {@code <nsURI>#//Class}.
     */
    private static EPackage loadPackage(ResourceSet rs, URI ecoreUri) {
        Resource res = rs.getResource(ecoreUri, true);
        EPackage pkg = (EPackage) res.getContents().get(0);
        res.setURI(URI.createURI(pkg.getNsURI()));
        rs.getPackageRegistry().put(pkg.getNsURI(), pkg);
        return pkg;
    }

    private static URI ecoreUri(File dir, String path) {
        File f = new File(dir, path);
        if (!f.isFile()) {
            throw new IllegalStateException("Ecore not found: " + f);
        }
        return URI.createFileURI(f.getAbsolutePath());
    }

    private static EClass eClass(EPackage pkg, String name) {
        if (pkg.getEClassifier(name) instanceof EClass c) {
            return c;
        }
        throw new IllegalStateException("EClass not found: " + name + " in " + pkg.getName());
    }

    private static EDataType eDataType(EPackage pkg, String name) {
        if (pkg.getEClassifier(name) instanceof EDataType d) {
            return d;
        }
        throw new IllegalStateException("EDataType not found: " + name + " in " + pkg.getName());
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
