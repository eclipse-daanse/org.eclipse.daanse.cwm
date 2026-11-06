/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.units.build;

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
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
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
 * Programmatic builder for the {@code units} metamodel: quantities, units, dimensions and systems
 * of units for any CWM attribute, parameter or data type.
 *
 * <p>The vocabulary follows QUDT (quantity kind, unit, dimension vector, system of units, prefix)
 * and JSR 385 ({@code javax.measure}: unit, dimension, prefix, system of units, the scale of a
 * quantity). A unit is abstract and its subclass says how it is defined: a base unit, a product
 * of powers of units, or a scaled, prefixed, alternate or logarithmic form of another unit. A
 * {@code Quantity} attaches a unit to the elements whose values are measured in it, the way a
 * {@code businessinformation::Description} attaches text.</p>
 *
 * <p>Every reference into CWM and between the classes of the package is one-way with an EMOF
 * {@code Property.oppositeRoleName} (prefix {@code units}); construction and serialization follow
 * {@code model/daanse/governance/lifecycle/.../LifecycleEcoreBuilder}.</p>
 */
public final class UnitsEcoreBuilder {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    /** The namespace URI of the package. */
    public static final String NS_URI = "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.units";

    private static final String EPL_HEADER = String.join("\n",
            "Copyright (c) 2026 Contributors to the Eclipse Foundation.",
            "",
            "This program and the accompanying materials are made",
            "available under the terms of the Eclipse Public License 2.0",
            "which is available at https://www.eclipse.org/legal/epl-2.0/",
            "",
            "SPDX-License-Identifier: EPL-2.0");

    private final EcoreFactory ef = EcoreFactory.eINSTANCE;
    private final CwmRefs cwm;

    /** The classifiers of {@code objectmodel/core} the metamodel refers to. */
    public static final class CwmRefs {
        final EClass element;
        final EClass modelElement;
        final EClass cwmPackage;
        final EDataType stringType;
        final EDataType floatType;
        final EDataType integerType;

        CwmRefs(EPackage core) {
            this.element = eClass(core, "Element");
            this.modelElement = eClass(core, "ModelElement");
            this.cwmPackage = eClass(core, "Package");
            this.stringType = eDataType(core, "String");
            this.floatType = eDataType(core, "Float");
            this.integerType = eDataType(core, "Integer");
        }
    }

    public UnitsEcoreBuilder(CwmRefs cwm) {
        this.cwm = cwm;
    }

    /**
     * Builds the metamodel from the CWM Ecores next to {@code moduleDir} (module directory
     * {@code model/daanse/units}) and writes it to {@code outFile}.
     */
    public static void generate(File moduleDir, File outFile) throws IOException {
        File cwmDir = new File(moduleDir, "../../cwm").getCanonicalFile();
        ResourceSet rs = newResourceSet();
        EPackage core = loadPackage(rs, ecoreUri(cwmDir, "objectmodel.core/model/core.ecore"));
        outFile.getParentFile().mkdirs();
        save(new UnitsEcoreBuilder(new CwmRefs(core)).build(), rs, URI.createFileURI(outFile.getAbsolutePath()));
    }

    /** Creates the EPackage {@code units}: 14 classes (2 abstract), 1 enumeration. */
    public EPackage build() {
        EPackage p = ef.createEPackage();
        p.setName("units");
        p.setNsURI(NS_URI);
        p.setNsPrefix("units");
        EAnnotation pa = annotate(p,
                "Quantities, units, dimensions and systems of units for any CWM attribute, parameter or "
                        + "data type, after QUDT and JSR 385. A SystemOfUnits (a core::Package) owns dimensions, "
                        + "quantity kinds, prefixes and units. A Dimension states the exponents of the seven base "
                        + "dimensions; a QuantityKind (length, temperature, pressure) has a dimension and may be "
                        + "narrower than another kind. A Unit has a symbol and a dimension, and its subclass says "
                        + "how it is defined: a BaseUnit, a DerivedUnit as a product of powers of units, or a "
                        + "ReferringUnit defined through another unit — scaled by a multiplier and an offset, "
                        + "prefixed, an alternate with its own symbol, or logarithmic. A Quantity says that the "
                        + "values of the elements it names are measured in a unit: an attribute (a column, a "
                        + "record field, a measure, a resource), a parameter, or a data type whose attributes "
                        + "all are; an element's own quantity comes before that of its type. Quantity is used "
                        + "as in ISO 80000: the property that is measured, not a value with a unit. "
                        + "Non-containment references are hidden association ends (Property.oppositeRoleName, "
                        + "prefix units).");
        pa.getDetails().put("modelName", "Units");
        pa.getDetails().put("copyrightText", EPL_HEADER);
        pa.getDetails().put("oSGiCompatible", "true");
        pa.getDetails().put("basePackage", "org.eclipse.daanse.cwm.model.daanse");
        pa.getDetails().put("resource", "XMI");
        pa.getDetails().put("prefix", "Units");

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

        EClass system = cls(p, "SystemOfUnits", false,
                "A system of units (the SI, a project's own units): a package that owns the dimensions, "
                        + "quantity kinds, prefixes and units it defines. Units of other systems that are "
                        + "accepted here are named in allowedUnit.");
        system.getESuperTypes().add(cwm.cwmPackage);
        EClass dimension = cls(p, "Dimension", false,
                "The dimension of a quantity: the exponents of the seven base dimensions of the SI, in "
                        + "the order length L, mass M, time T, electric current I, thermodynamic temperature, "
                        + "amount of substance N, luminous intensity J. Speed is L1 T-1, pressure L-1 M1 T-2; a "
                        + "dimensionless quantity has all exponents zero.");
        dimension.getESuperTypes().add(cwm.modelElement);
        EClass kind = cls(p, "QuantityKind", false,
                "A kind of quantity: length, temperature, pressure, speed. Every kind has a dimension; "
                        + "different kinds may share one (energy and torque, frequency and radioactivity). A "
                        + "kind may be narrower than another of the same dimension (width than length).");
        kind.getESuperTypes().add(cwm.modelElement);
        EClass prefix = cls(p, "Prefix", false,
                "A prefix that scales a unit by base to the power of exponent: kilo is 10^3, milli 10^-3, "
                        + "kibi 2^10.");
        prefix.getESuperTypes().add(cwm.modelElement);
        EClass unit = cls(p, "Unit", true,
                "A unit of measurement with a symbol and a dimension. The subclass says how the unit is "
                        + "defined. Units of the same dimension are compatible: a value converts from one to "
                        + "the other.");
        unit.getESuperTypes().add(cwm.modelElement);
        EClass baseUnit = cls(p, "BaseUnit", false,
                "A unit defined by itself, of one base dimension: metre, kilogram, second, ampere, kelvin, "
                        + "mole, candela.");
        baseUnit.getESuperTypes().add(unit);
        EClass derived = cls(p, "DerivedUnit", false,
                "A unit defined as the product of powers of other units, in the order of its symbol: metre "
                        + "per second is m^1 s^-1, newton kg^1 m^1 s^-2. Without factors it is the dimensionless "
                        + "unit one. A factor unit has no offset.");
        derived.getESuperTypes().add(unit);
        EClass factor = cls(p, "UnitFactor", false,
                "One factor of a derived unit: a unit to an integer power.");
        factor.getESuperTypes().add(cwm.element);
        EClass referring = cls(p, "ReferringUnit", true,
                "A unit defined through another unit, its reference; it has the dimension of the reference.");
        referring.getESuperTypes().add(unit);
        EClass scaled = cls(p, "ScaledUnit", false,
                "A unit whose values convert to the reference as (value + offset) * multiplier: a degree "
                        + "Celsius is kelvin with multiplier 1 and offset 273.15, an hour second with multiplier "
                        + "3600, a percent the unit one with multiplier 0.01. Multipliers that are not finite "
                        + "decimals (a degree is pi / 180 radian) are decimal approximations.");
        scaled.getESuperTypes().add(referring);
        EClass prefixed = cls(p, "PrefixedUnit", false,
                "A unit that is the reference scaled by a prefix: hectopascal, millimetre, kilojoule.");
        prefixed.getESuperTypes().add(referring);
        EClass alternate = cls(p, "AlternateUnit", false,
                "A unit equal to its reference with a symbol of its own, for another kind of quantity of "
                        + "the same dimension: radian and steradian are the unit one, becquerel is hertz, "
                        + "sievert is gray.");
        alternate.getESuperTypes().add(referring);
        EClass logarithmic = cls(p, "LogarithmicUnit", false,
                "A unit on a logarithmic scale: a value converts to the reference as "
                        + "base ^ (value / divisor). Decibel-milliwatt is milliwatt with base 10 and divisor 10; "
                        + "a decibel of a power ratio is the unit one with base 10 and divisor 10, of a field "
                        + "ratio with divisor 20; a neper has base e and divisor 1.");
        logarithmic.getESuperTypes().add(referring);
        EClass quantity = cls(p, "Quantity", false,
                "States that the values of the elements it names are measured in a unit: an attribute (a "
                        + "column, a record field, a measure, a resource), a parameter, or a data type whose "
                        + "attributes all are. An element's own quantity comes before that of its type. The "
                        + "quantity kind names the kind where the unit alone does not (a joule measures energy, "
                        + "work or heat); the scale tells a measured value from a difference of values "
                        + "(a temperature from a temperature difference). Quantity is used as in ISO 80000: "
                        + "the property that is measured, not a value with a unit.");
        quantity.getESuperTypes().add(cwm.modelElement);
        EEnum scale = enm(p, "Scale",
                "Whether values are measured from the zero of the unit (absolute) or are differences "
                        + "of values (relative); a difference converts without the offset of a unit.",
                "absolute", "relative");

        attr(system, "abbreviation", cwm.stringType, 0, 1, "A short name of the system, e.g. SI.");
        EReference allowed = ref(system, "allowedUnit", unit, 0, -1,
                "Units of other systems that are accepted in this system.");
        oppositeRole(allowed, "unitsAllowingSystem");

        for (String exponent : new String[] { "length", "mass", "time", "electricCurrent",
                "thermodynamicTemperature", "amountOfSubstance", "luminousIntensity" }) {
            attr(dimension, exponent, cwm.integerType, 1, 1, "The exponent of " + words(exponent) + ".");
        }

        attr(kind, "symbol", cwm.stringType, 0, 1, "The symbol of the kind, e.g. l for length.");
        EReference kindDimension = ref(kind, "dimension", dimension, 1, 1, "The dimension of the kind.");
        oppositeRole(kindDimension, "unitsQuantityKind");
        EReference broader = ref(kind, "broader", kind, 0, 1,
                "The broader kind of the same dimension this kind is a special case of.");
        oppositeRole(broader, "unitsNarrower");
        constrain(kind, "BroaderNotSelf", "self.broader.oclIsUndefined() or self.broader <> self");

        attr(prefix, "symbol", cwm.stringType, 1, 1, "The symbol of the prefix, e.g. k for kilo.");
        attr(prefix, "base", cwm.integerType, 1, 1, "The base of the factor: 10 or 2.");
        attr(prefix, "exponent", cwm.integerType, 1, 1, "The exponent of the factor: 3 for kilo, -3 for milli.");
        constrain(prefix, "BaseTwoOrTen", "self.base = 2 or self.base = 10");
        constrain(prefix, "ExponentNotZero", "self.exponent <> 0");

        attr(unit, "symbol", cwm.stringType, 1, 1, "The symbol of the unit, e.g. m/s or °C.");
        attr(unit, "ucumCode", cwm.stringType, 0, 1,
                "The code of the unit in the Unified Code for Units of Measure, e.g. Cel for degree Celsius.");
        EReference unitDimension = ref(unit, "dimension", dimension, 1, 1,
                "The dimension of the unit; it follows from the definition and is stated for readers "
                        + "and queries.");
        oppositeRole(unitDimension, "unitsUnit");
        EReference unitKinds = ref(unit, "quantityKind", kind, 0, -1, "The kinds of quantity the unit measures.");
        oppositeRole(unitKinds, "unitsApplicableUnit");

        cont(derived, "factor", factor, 0, -1, "The factors of the unit, in the order of its symbol.");
        constrain(derived, "FactorExponentNotZero", "self.factor->forAll(f | f.exponent <> 0)");
        EReference factorUnit = ref(factor, "unit", unit, 1, 1, "The unit of the factor.");
        oppositeRole(factorUnit, "unitsUnitFactor");
        attr(factor, "exponent", cwm.integerType, 1, 1, "The power of the unit, not zero.");

        EReference reference = ref(referring, "reference", unit, 1, 1, "The unit this unit is defined through.");
        oppositeRole(reference, "unitsReferringUnit");
        constrain(referring, "NotSelfReferring", "self.reference.oclIsUndefined() or self.reference <> self");

        attr(scaled, "multiplier", cwm.floatType, 1, 1,
                "The factor from a value plus offset to the reference, a decimal number other than zero.");
        attr(scaled, "offset", cwm.floatType, 0, 1, "Added to a value before the multiplier, a decimal number.");

        EReference prefixRef = ref(prefixed, "prefix", prefix, 1, 1, "The prefix.");
        oppositeRole(prefixRef, "unitsPrefixedUnit");

        attr(logarithmic, "base", cwm.floatType, 1, 1, "The base of the logarithm, a decimal number above zero.");
        attr(logarithmic, "divisor", cwm.floatType, 1, 1,
                "The divisor of a value before it becomes the exponent, a decimal number other than zero.");

        EReference measured = ref(quantity, "modelElement", cwm.modelElement, 1, -1,
                "The elements whose values are measured in the unit: attributes, parameters, data types.");
        oppositeRole(measured, "unitsQuantity");
        EReference quantityUnit = ref(quantity, "unit", unit, 1, 1, "The unit of the values.");
        oppositeRole(quantityUnit, "unitsQuantityOfUnit");
        EReference quantityKind = ref(quantity, "quantityKind", kind, 0, 1,
                "The kind of quantity, where the unit alone does not name it.");
        oppositeRole(quantityKind, "unitsQuantityOfKind");
        attr(quantity, "scale", scale, 1, 1,
                "Whether the values are measured from the zero of the unit or are differences of values.");

        return p;
    }

    private static String words(String camelCase) {
        return camelCase.replaceAll("([A-Z])", " $1").toLowerCase();
    }

    /** Adds an OCL invariant: Ecore constraints list plus the OCL expression of the same name. */
    private void constrain(EClass c, String name, String ocl) {
        EAnnotation constraints = c.getEAnnotation(ECORE);
        if (constraints == null) {
            constraints = ef.createEAnnotation();
            constraints.setSource(ECORE);
            c.getEAnnotations().add(0, constraints);
        }
        String names = constraints.getDetails().get("constraints");
        constraints.getDetails().put("constraints", names == null ? name : names + " " + name);
        EAnnotation expressions = c.getEAnnotation(ECORE_OCL);
        if (expressions == null) {
            expressions = ef.createEAnnotation();
            expressions.setSource(ECORE_OCL);
            c.getEAnnotations().add(1, expressions);
        }
        expressions.getDetails().put(name, ocl);
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

    private EClass cls(EPackage pkg, String name, boolean isAbstract, String doc) {
        EClass c = ef.createEClass();
        c.setName(name);
        c.setAbstract(isAbstract);
        pkg.getEClassifiers().add(c);
        annotate(c, doc);
        return c;
    }

    private EEnum enm(EPackage pkg, String name, String doc, String... literals) {
        EEnum e = ef.createEEnum();
        e.setName(name);
        int value = 0;
        for (String literal : literals) {
            EEnumLiteral l = ef.createEEnumLiteral();
            l.setName(literal);
            l.setValue(value++);
            e.getELiterals().add(l);
        }
        pkg.getEClassifiers().add(e);
        annotate(e, doc);
        return e;
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
        if (upper != 1) {
            r.setOrdered(false);
        }
        owner.getEStructuralFeatures().add(r);
        annotate(r, doc);
        return r;
    }

    /** A containment reference; it keeps the order of its elements. */
    private EReference cont(EClass owner, String name, EClassifier type, int lower, int upper, String doc) {
        EReference r = ef.createEReference();
        r.setName(name);
        r.setEType(type);
        r.setLowerBound(lower);
        r.setUpperBound(upper);
        r.setContainment(true);
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
     * Loads a CWM ecore; its resource URI becomes the nsURI, so that references serialize as
     * {@code <nsURI>#//Class}.
     */
    private static EPackage loadPackage(ResourceSet rs, URI ecoreUri) {
        Resource res = rs.getResource(ecoreUri, true);
        EPackage pkg = (EPackage) res.getContents().get(0);
        res.setURI(URI.createURI(pkg.getNsURI()));
        rs.getPackageRegistry().put(pkg.getNsURI(), pkg);
        return pkg;
    }

    private static URI ecoreUri(File cwmDir, String path) {
        File f = new File(cwmDir, path);
        if (!f.isFile()) {
            throw new IllegalStateException("CWM ecore not found: " + f);
        }
        return URI.createFileURI(f.getAbsolutePath());
    }

    private static EClass eClass(EPackage pkg, String name) {
        if (pkg.getEClassifier(name) instanceof EClass c) {
            return c;
        }
        throw new IllegalStateException("CWM EClass not found: " + name + " in " + pkg.getName());
    }

    private static EDataType eDataType(EPackage pkg, String name) {
        if (pkg.getEClassifier(name) instanceof EDataType d) {
            return d;
        }
        throw new IllegalStateException("CWM EDataType not found: " + name + " in " + pkg.getName());
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
