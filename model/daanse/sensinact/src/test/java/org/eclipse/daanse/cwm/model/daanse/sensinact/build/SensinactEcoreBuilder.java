/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.sensinact.build;

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
 * Programmatic builder for the {@code sensinact} metamodel: the structure of Eclipse sensiNact
 * digital-twin providers as CWM classes, and the mapping of a CWM class model onto them.
 *
 * <p>Target side: {@code Provider} and {@code Service} are {@code core::Class}es, a provider owns
 * its services in {@code ownedElement}, a service has its resources as {@code feature}s;
 * {@code Resource} is a {@code core::Attribute}. Mapping side: {@code ProviderMapping} ›
 * {@code ServiceMapping} › {@code ResourceMapping}, each value read through a {@code ValueSource}
 * whose subclass says how: {@code PathSource} (a typed feature path into the source class, an
 * element picked from a collection, a fallback), {@code LiteralSource}, {@code ArrivalTimeSource}.
 * Every reference into CWM is one-way with an EMOF {@code Property.oppositeRoleName}
 * (prefix {@code sensinact}).</p>
 */
public final class SensinactEcoreBuilder {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    /** The namespace URI of the package. */
    public static final String NS_URI = "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.sensinact";

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
        final EClass element;
        final EClass modelElement;
        final EClass namespace;
        final EClass cwmClass;
        final EClass attribute;
        final EClass structuralFeature;
        final EClass booleanExpression;
        final EDataType stringType;
        final EDataType booleanType;
        final EDataType integerType;

        CwmRefs(EPackage core) {
            this.element = eClass(core, "Element");
            this.modelElement = eClass(core, "ModelElement");
            this.namespace = eClass(core, "Namespace");
            this.cwmClass = eClass(core, "Class");
            this.attribute = eClass(core, "Attribute");
            this.structuralFeature = eClass(core, "StructuralFeature");
            this.booleanExpression = eClass(core, "BooleanExpression");
            this.stringType = eDataType(core, "String");
            this.booleanType = eDataType(core, "Boolean");
            this.integerType = eDataType(core, "Integer");
        }
    }

    public SensinactEcoreBuilder(CwmRefs cwm) {
        this.cwm = cwm;
    }

    /**
     * Builds the package from the CWM Ecores next to {@code moduleDir} (module directory
     * {@code model/daanse/sensinact}) and writes it to {@code outFile}.
     */
    public static void generate(File moduleDir, File outFile) throws IOException {
        File cwmDir = new File(moduleDir, "../../cwm").getCanonicalFile();
        ResourceSet rs = newResourceSet();
        EPackage core = loadPackage(rs, ecoreUri(cwmDir, "objectmodel.core/model/core.ecore"));
        outFile.getParentFile().mkdirs();
        save(new SensinactEcoreBuilder(new CwmRefs(core)).build(), rs, URI.createFileURI(outFile.getAbsolutePath()));
    }

    /** Creates the EPackage {@code sensinact}: 8 classes, no data types of its own. */
    public EPackage build() {
        EPackage p = ef.createEPackage();
        p.setName("sensinact");
        p.setNsURI(NS_URI);
        p.setNsPrefix("sensinact");
        EAnnotation pa = annotate(p,
                "The structure of Eclipse sensiNact digital-twin providers as CWM classes, and the "
                        + "mapping of a CWM class model onto them. A Provider (a core::Class) owns its Services "
                        + "(core::Classes) in ownedElement; the features of a Service are its Resources "
                        + "(core::Attributes, typed by a data type). A ProviderMapping says which source classes "
                        + "become instances of a provider and where the instance id comes from; a ServiceMapping "
                        + "per mapped service may pick the element the service reads from; a ResourceMapping per "
                        + "resource names the source of its value. Every value is read through a ValueSource, "
                        + "and its subclass says how: a PathSource is a typed feature path into the source class "
                        + "(an element picked from a collection by index or filter, a fallback where the path "
                        + "yields nothing), a LiteralSource a fixed value, an ArrivalTimeSource the time a source "
                        + "instance arrives. Nothing is implicit: what a mapping omits is checked by "
                        + "SensinactModelValidator, not filled in. Recording and retention rules for resources "
                        + "come from the lifecycle model. Non-containment references into CWM are hidden "
                        + "association ends (Property.oppositeRoleName, prefix sensinact).");
        pa.getDetails().put("modelName", "Sensinact");
        pa.getDetails().put("copyrightText", EPL_HEADER);
        pa.getDetails().put("oSGiCompatible", "true");
        pa.getDetails().put("basePackage", "org.eclipse.daanse.cwm.model.daanse");
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

        // target structure
        EClass provider = cls(p, "Provider",
                "The structure of a sensiNact provider: its services, owned in ownedElement. The name is "
                        + "the name of the provider model in the twin. A Provider has no features of its own; "
                        + "the admin service (friendly name, description, location) is fixed by sensiNact and "
                        + "filled by the AdminMapping of a ProviderMapping.");
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

        // mapping
        EClass providerMapping = cls(p, "ProviderMapping",
                "Makes instances of the source classes instances of the provider: the id of a provider "
                        + "instance, its timestamp, its admin values, and one ServiceMapping per mapped service "
                        + "(owned in ownedElement). The name is the mapping id.");
        providerMapping.getESuperTypes().add(cwm.namespace);
        EClass serviceMapping = cls(p, "ServiceMapping",
                "Maps one service: optionally the element of the source instance the service reads from "
                        + "(selector), the timestamp of its values, and one ResourceMapping per mapped resource. "
                        + "The name is the service name. The ResourceMappings are owned in ownedElement.");
        serviceMapping.getESuperTypes().add(cwm.namespace);
        EClass resourceMapping = cls(p, "ResourceMapping",
                "Maps one resource: where its value comes from. The name is the resource name.");
        resourceMapping.getESuperTypes().add(cwm.modelElement);
        EClass adminMapping = cls(p, "AdminMapping",
                "The values of the admin service of a provider instance: friendly name, description and "
                        + "location; each read from the source instance or given as a literal. A location needs "
                        + "both latitude and longitude.");
        adminMapping.getESuperTypes().add(cwm.modelElement);
        EClass valueSource = cls(p, "ValueSource",
                "Where a value comes from; the subclass says how: a PathSource reads it from the source "
                        + "instance, a LiteralSource gives it, an ArrivalTimeSource is the time a source "
                        + "instance arrives.");
        valueSource.setAbstract(true);
        valueSource.getESuperTypes().add(cwm.element);
        EClass pathSource = cls(p, "PathSource",
                "A value read along a feature path into the context class (the source class, or the "
                        + "element a service selector picks), each step a feature of the type of the step "
                        + "before; at the one step that holds many values the element is picked by index or "
                        + "by filter. Where the path yields nothing, the fallback gives the value.");
        pathSource.getESuperTypes().add(valueSource);
        EClass literalSource = cls(p, "LiteralSource", "A fixed value, given as text.");
        literalSource.getESuperTypes().add(valueSource);
        EClass arrivalTimeSource = cls(p, "ArrivalTimeSource",
                "The time a source instance arrives; a timestamp only.");
        arrivalTimeSource.getESuperTypes().add(valueSource);

        EReference pmProvider = ref(providerMapping, "provider", provider, 1, 1,
                "The provider the source instances become instances of.");
        oppositeRole(pmProvider, "sensinactProviderMapping");
        EReference pmSource = ref(providerMapping, "sourceClass", cwm.cwmClass, 1, -1,
                "The source classes: an instance of any of them becomes a provider instance.");
        oppositeRole(pmSource, "sensinactSourceOfMapping");
        cont(providerMapping, "id", valueSource, 1, 1,
                "The id of a provider instance: read along a path (a literal as its fallback where the "
                        + "path yields nothing), or a literal.");
        cont(providerMapping, "timestamp", valueSource, 0, 1,
                "The time of the values of a source instance: read along a path, a literal instant, or "
                        + "the arrival time; unset: the arrival time.");
        cont(providerMapping, "admin", adminMapping, 0, 1,
                "The values of the admin service.");

        EReference smService = ref(serviceMapping, "service", service, 1, 1,
                "The mapped service, a service of the provider of the ProviderMapping.");
        oppositeRole(smService, "sensinactServiceMapping");
        cont(serviceMapping, "selector", pathSource, 0, 1,
                "The element of the source instance the service reads from, e.g. one element of a "
                        + "collection; the paths of the service then start at its type. Unset: the source "
                        + "instance itself.");
        cont(serviceMapping, "timestamp", valueSource, 0, 1,
                "The time of the values of the service; unset: the time of the ProviderMapping.");

        EReference rmResource = ref(resourceMapping, "resource", resource, 1, 1,
                "The mapped resource, a resource of the service of the ServiceMapping.");
        oppositeRole(rmResource, "sensinactResourceMapping");
        cont(resourceMapping, "value", valueSource, 1, 1,
                "Where the value of the resource comes from.");

        cont(adminMapping, "friendlyName", valueSource, 0, 1, "The friendly name of the provider instance.");
        cont(adminMapping, "description", valueSource, 0, 1, "The description of the provider instance.");
        cont(adminMapping, "latitude", valueSource, 0, 1, "The latitude of the provider instance.");
        cont(adminMapping, "longitude", valueSource, 0, 1, "The longitude of the provider instance.");

        EReference vsPath = ref(pathSource, "path", cwm.structuralFeature, 1, -1,
                "The feature path, in order: the first step a feature of the context class, every "
                        + "further step a feature of the type of the step before (an attribute of the class, or "
                        + "a navigable association end seen from the other end).");
        vsPath.setOrdered(true);
        oppositeRole(vsPath, "sensinactValueSource");
        EAttribute index = attr(pathSource, "index", cwm.integerType, 0, 1,
                "The position of the element picked at the step that holds many values, from 0.");
        index.setUnsettable(true);
        cont(pathSource, "filter", cwm.booleanExpression, 0, 1,
                "Picks the element at the step that holds many values by its content, in the language "
                        + "event.atlas.collectionFilter: a dotted path of feature names, '=', a literal "
                        + "(key=line_5). Wins over index.");
        attr(pathSource, "format", cwm.stringType, 0, 1,
                "A DateTimeFormatter pattern for a timestamp read as text; read as UTC unless the "
                        + "pattern carries a zone.");
        cont(pathSource, "fallback", valueSource, 0, 1,
                "The value where the path yields none.");
        attr(literalSource, "literal", cwm.stringType, 1, 1, "The value.");

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
        if (upper != 1) {
            r.setOrdered(false);
        }
        owner.getEStructuralFeatures().add(r);
        annotate(r, doc);
        return r;
    }

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
