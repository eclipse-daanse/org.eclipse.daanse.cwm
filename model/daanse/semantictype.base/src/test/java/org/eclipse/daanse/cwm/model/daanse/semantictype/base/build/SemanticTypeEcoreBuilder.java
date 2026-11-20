/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.semantictype.base.build;

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
 * Programmatic builder for the {@code semantictype.base} metamodel: libraries of semantic types, the types as
 * instances, and the typing that says what an element is.
 *
 * <p>A {@code TypeLibrary} is the vocabulary of one source, identified by its URI; its {@code SemanticType}s are
 * instances, as units are instances of {@code units::Unit}. A type says a meaning, nothing about where it may stand:
 * the same type types a column (its values are that) or a table (its rows are that). A {@code Typing} names the
 * elements it types and their type, as a {@code units::Quantity} names the elements it measures and their unit.</p>
 */
public final class SemanticTypeEcoreBuilder {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    /** The namespace URI of the package. */
    public static final String NS_URI =
            "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.semantictype.base";

    private static final String EPL_HEADER = String.join("\n",
            "Copyright (c) 2026 Contributors to the Eclipse Foundation.",
            "",
            "This program and the accompanying materials are made",
            "available under the terms of the Eclipse Public License 2.0",
            "which is available at https://www.eclipse.org/legal/epl-2.0/",
            "",
            "SPDX-License-Identifier: EPL-2.0");

    private final EcoreFactory ef = EcoreFactory.eINSTANCE;
    private final EClass element;
    private final EClass modelElement;
    private final EClass cwmPackage;
    private final EDataType string;
    private final EDataType integer;

    SemanticTypeEcoreBuilder(EPackage core) {
        this.element = (EClass) core.getEClassifier("Element");
        this.modelElement = (EClass) core.getEClassifier("ModelElement");
        this.cwmPackage = (EClass) core.getEClassifier("Package");
        this.string = (EDataType) core.getEClassifier("String");
        this.integer = (EDataType) core.getEClassifier("Integer");
    }

    /**
     * Builds the metamodel from the CWM core next to {@code moduleDir} (module directory
     * {@code model/daanse/semantictype.base}) and writes it to {@code outFile}.
     */
    public static void generate(File moduleDir, File outFile) throws IOException {
        File cwmDir = new File(moduleDir, "../../cwm").getCanonicalFile();
        ResourceSet rs = newResourceSet();
        EPackage core = loadPackage(rs,
                URI.createFileURI(new File(cwmDir, "objectmodel.core/model/core.ecore").getAbsolutePath()));
        outFile.getParentFile().mkdirs();
        save(new SemanticTypeEcoreBuilder(core).build(), rs, URI.createFileURI(outFile.getAbsolutePath()));
    }

    /** Creates the EPackage {@code base}: 4 classes. */
    public EPackage build() {
        EPackage p = ef.createEPackage();
        p.setName("base");
        p.setNsURI(NS_URI);
        p.setNsPrefix("semantictype");
        EAnnotation pa = annotate(p,
                "What the elements of a model are, beyond their data type, as instances of semantic types. A "
                        + "TypeLibrary (a core::Package) is the vocabulary of one source, identified by its URI, "
                        + "and owns its types. A SemanticType says a meaning: it has our name, its name and number "
                        + "in the system it comes from, a URI of its own (stated, or derived from the library's URI "
                        + "and its name), broader types and the same type in other libraries. The same type may "
                        + "type a column (its values are that) or a table (its rows are that); what a type is for "
                        + "follows from its library and its broader types. A Typing names "
                        + "the elements it types and their type, as a units::Quantity names the elements it "
                        + "measures and their unit; it is owned by any namespace, never by the element. An "
                        + "element without a typing is untyped. The package holds no list of types: a library "
                        + "of the types of a server is delivered beside it. Non-containment references are "
                        + "hidden association ends (Property.oppositeRoleName, prefix semantictype).");
        pa.getDetails().put("modelName", "SemanticType");
        pa.getDetails().put("copyrightText", EPL_HEADER);
        pa.getDetails().put("oSGiCompatible", "true");
        pa.getDetails().put("basePackage", "org.eclipse.daanse.cwm.model.daanse.semantictype");
        pa.getDetails().put("resource", "XMI");
        pa.getDetails().put("prefix", "SemanticType");

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

        EClass library = cls(p, "TypeLibrary", false,
                "The vocabulary of one source: a package that owns its semantic types. Its URI is its identity, "
                        + "what a reader filters by; the system and version say where the types come from.");
        library.getESuperTypes().add(cwmPackage);
        attr(library, "uri", string, 1, 1, false,
                "The URI of the vocabulary, absolute and unique among the libraries of a model (rules ST1, ST2).");
        attr(library, "sourceSystem", string, 0, 1, false, "The name of the system the types come from.");
        attr(library, "sourceVersion", string, 0, 1, false, "The version of that system.");

        EClass type = cls(p, "SemanticType", false,
                "A semantic type: a meaning beyond the data type, an instance owned by a library (rule ST3). The "
                        + "name is ours; the name and number there are what the type is in the system the library "
                        + "comes from, both optional for a type of one's own. It types any element: the values of "
                        + "a column, the rows of a table, the members of a dimension.");
        type.getESuperTypes().add(modelElement);
        attr(type, "uri", string, 0, 1, false,
                "The URI of the type where it is stated; else the URI of its library and the name, after a "
                        + "slash (rules ST1, ST2).");
        attr(type, "sourceName", string, 0, 1, false,
                "The name of the type in the system its library comes from, as that system spells it.");
        attr(type, "sourceNumber", integer, 0, 1, true,
                "The number of the type in that system: the value of its literal there.");
        attr(type, "description", string, 0, 1, false, "What the type means, for a person.");
        oppositeRole(ref(type, "broader", type, 0, -1, false,
                "The types this type is a special case of: a city is narrower than a place. Without a cycle "
                        + "(rule ST5)."), "semantictypeNarrower");
        oppositeRole(ref(type, "sameAs", type, 0, -1, false,
                "The same type in other libraries (rule ST7): their names and numbers are what this type is "
                        + "there."), "semantictypeSameAsOf");
        constrain(type, "ST3", "not self.namespace.oclIsUndefined() and self.namespace.oclIsKindOf(TypeLibrary)");
        constrain(type, "ST5", "not self.broader->includes(self)");
        constrain(type, "ST7", "self.sameAs->forAll(t | t.namespace <> self.namespace)");

        EClass external = cls(p, "ExternalType", false,
                "The same type in a system that has no library in the model: the URI of its library there and "
                        + "the type's name or number there (rule ST7).");
        external.getESuperTypes().add(element);
        attr(external, "libraryUri", string, 1, 1, false, "The URI of the library the type lies in, absolute.");
        attr(external, "sourceName", string, 0, 1, false, "The name of the type there.");
        attr(external, "sourceNumber", integer, 0, 1, true, "The number of the type there.");
        ref(type, "sameAsExternal", external, 0, -1, true,
                "The same type in systems that have no library in the model.");

        EClass typing = cls(p, "Typing", false,
                "Says what the elements it names are: their type, the meaning of the values of a column or "
                        + "field, of the rows of a table, of the members of a dimension. Owned by any namespace, "
                        + "never by the element: a data type owns nothing (CWM 1.1, C-3-2).");
        typing.getESuperTypes().add(modelElement);
        oppositeRole(ref(typing, "modelElement", modelElement, 1, -1, false, "The elements typed."),
                "semantictypeTyping");
        oppositeRole(ref(typing, "type", type, 1, 1, false,
                "The type of the elements, in a library of the same model or a delivered one (rule ST8)."),
                "semantictypeTypingOfType");
        return p;
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
    private EReference oppositeRole(EReference r, String roleName) {
        EAnnotation a1 = ef.createEAnnotation();
        a1.setSource(EMOF);
        a1.getDetails().put("Property.oppositeRoleName", roleName);
        r.getEAnnotations().add(a1);
        EAnnotation a2 = ef.createEAnnotation();
        a2.setSource(EMOF + "#Property.oppositeRoleName");
        a2.getDetails().put("body", roleName);
        r.getEAnnotations().add(a2);
        return r;
    }

    private EClass cls(EPackage pkg, String name, boolean isAbstract, String doc) {
        EClass c = ef.createEClass();
        c.setName(name);
        c.setAbstract(isAbstract);
        pkg.getEClassifiers().add(c);
        annotate(c, doc);
        return c;
    }

    private EAttribute attr(EClass owner, String name, EClassifier type, int lower, int upper, boolean unsettable,
            String doc) {
        EAttribute a = ef.createEAttribute();
        a.setName(name);
        a.setEType(type);
        a.setLowerBound(lower);
        a.setUpperBound(upper);
        a.setUnsettable(unsettable);
        owner.getEStructuralFeatures().add(a);
        annotate(a, doc);
        return a;
    }

    private EReference ref(EClass owner, String name, EClassifier type, int lower, int upper, boolean containment,
            String doc) {
        EReference r = ef.createEReference();
        r.setName(name);
        r.setEType(type);
        r.setLowerBound(lower);
        r.setUpperBound(upper);
        r.setContainment(containment);
        if (upper != 1) {
            r.setOrdered(false);
        }
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

    /** Writes the package with the license header after the XML declaration. */
    private static void save(EPackage pkg, ResourceSet rs, URI uri) throws IOException {
        Resource out = rs.createResource(uri);
        out.getContents().add(pkg);
        Map<Object, Object> opts = new HashMap<>();
        opts.put(XMLResource.OPTION_ENCODING, "UTF-8");
        opts.put(XMLResource.OPTION_LINE_WIDTH, Integer.valueOf(120));
        out.save(opts);
        Path file = Path.of(uri.toFileString());
        String content = Files.readString(file, StandardCharsets.UTF_8);
        int nl = content.indexOf('\n');
        String header = "<!--\n/*********************************************************************\n* "
                + EPL_HEADER.replace("\n", "\n* ").replace("* \n", "*\n")
                + "\n**********************************************************************/\n-->\n";
        Files.writeString(file, content.substring(0, nl + 1) + header + content.substring(nl + 1),
                StandardCharsets.UTF_8);
    }
}
