/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.sensinact.build;

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
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

/**
 * Programmatic builder for the {@code sensinact} metamodel of the resource layer: the structure of
 * Eclipse sensiNact digital-twin providers as CWM classes.
 *
 * <p>{@code Provider} and {@code Service} are {@code core::Class}es, a provider owns its services in
 * {@code ownedElement}, a service has its resources as {@code feature}s; {@code Resource} is a
 * {@code core::Attribute}. Where the values of a resource come from is the matter of the mapping
 * model {@code mapping.sensinact}.</p>
 */
public final class SensinactEcoreBuilder {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    /** The namespace URI of the package. */
    public static final String NS_URI =
            "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.resource.sensinact";

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

    /** The classifiers of CWM the metamodel refers to. */
    public static final class CwmRefs {
        final EClass cwmClass;
        final EClass attribute;
        final EDataType stringType;
        final EDataType booleanType;

        CwmRefs(EPackage core) {
            this.cwmClass = eClass(core, "Class");
            this.attribute = eClass(core, "Attribute");
            this.stringType = eDataType(core, "String");
            this.booleanType = eDataType(core, "Boolean");
        }
    }

    public SensinactEcoreBuilder(CwmRefs cwm) {
        this.cwm = cwm;
    }

    /**
     * Builds the package from the CWM Ecores next to {@code moduleDir} (module directory
     * {@code model/daanse/resource.sensinact}) and writes it to {@code outFile}.
     */
    public static void generate(File moduleDir, File outFile) throws IOException {
        File cwmDir = new File(moduleDir, "../../cwm").getCanonicalFile();
        ResourceSet rs = newResourceSet();
        EPackage core = loadPackage(rs, ecoreUri(cwmDir, "objectmodel.core/model/core.ecore"));
        outFile.getParentFile().mkdirs();
        save(new SensinactEcoreBuilder(new CwmRefs(core)).build(), rs, URI.createFileURI(outFile.getAbsolutePath()));
    }

    /** Creates the EPackage {@code sensinact}: 3 classes, no data types of its own. */
    public EPackage build() {
        EPackage p = ef.createEPackage();
        p.setName("sensinact");
        p.setNsURI(NS_URI);
        p.setNsPrefix("sensinact");
        EAnnotation pa = annotate(p,
                "The structure of Eclipse sensiNact digital-twin providers as CWM classes. A Provider (a "
                        + "core::Class) owns its Services (core::Classes) in ownedElement; the features of a "
                        + "Service are its Resources (core::Attributes, typed by a data type). The names become "
                        + "the ids of the twin. Where the values of a resource come from is described by the "
                        + "mapping model mapping.sensinact; what this package leaves open is checked by "
                        + "SensinactModelValidator, not filled in.");
        pa.getDetails().put("modelName", "Sensinact");
        pa.getDetails().put("copyrightText", EPL_HEADER);
        pa.getDetails().put("oSGiCompatible", "true");
        pa.getDetails().put("basePackage", "org.eclipse.daanse.cwm.model.daanse.resource");
        pa.getDetails().put("resource", "XMI");
        pa.getDetails().put("prefix", "Sensinact");

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

        EClass provider = cls(p, "Provider",
                "The structure of a sensiNact provider: its services, owned in ownedElement. The name is "
                        + "the name of the provider model in the twin. A Provider has no features of its own; "
                        + "the admin service (friendly name, description, location) is fixed by sensiNact and "
                        + "filled by a mapping.");
        provider.getESuperTypes().add(cwm.cwmClass);
        EClass service = cls(p, "Service",
                "A service of a provider: a group of resources, its features. The name is the service id.");
        service.getESuperTypes().add(cwm.cwmClass);
        EClass resource = cls(p, "Resource",
                "A data point of a service: a core::Attribute typed by a data type. The name is the "
                        + "resource id; initialValue gives the value before the first reading, changeability "
                        + "frozen makes it not changeable, tagged values become extra metadata, a Description "
                        + "of type documentation becomes the description. A units::Quantity that names the "
                        + "resource, or its data type, gives its unit.");
        resource.getESuperTypes().add(cwm.attribute);

        attr(provider, "locationRequired", cwm.booleanType, 0, 1,
                "Every mapping onto this provider gives a location (latitude and longitude).");
        attr(provider, "friendlyNameRequired", cwm.booleanType, 0, 1,
                "Every mapping onto this provider gives a friendly name.");
        constrain(provider, "NoDirectFeatures", "self.feature->isEmpty()");

        attr(service, "displayName", cwm.stringType, 0, 1, "The display name of the service.");
        attr(service, "required", cwm.booleanType, 0, 1,
                "Every mapping onto the provider maps this service.").setDefaultValueLiteral("true");
        constrain(service, "OwnedByProvider", "self.namespace.oclIsKindOf(Provider)");
        constrain(service, "OnlyResources", "self.feature->forAll(f | f.oclIsKindOf(Resource))");

        attr(resource, "displayName", cwm.stringType, 0, 1, "The display name of the resource.");
        attr(resource, "required", cwm.booleanType, 0, 1,
                "Every mapping of the service maps this resource.").setDefaultValueLiteral("true");
        constrain(resource, "TypedByDataType", "self.type.oclIsKindOf(core::DataType)");

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
