/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.emf.standalone;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.SpeakingIds;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.InternalEList;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

/**
 * Makes CWM XMI loadable and writable without OSGi: registers the generated EPackages that are on
 * the class path, creates resource sets that read XMI under any file extension, and saves model
 * elements as XMI with readable ids.
 *
 * <p>The known packages are looked up by name, so a caller decides by its dependencies which of
 * them exist; a missing one is skipped. Further packages (ROLAP, own extensions) are registered
 * by the name of their generated {@code EPackage} interface via {@link #register(String, ClassLoader)}.</p>
 */
public final class CwmPackages {

    /** The generated EPackage interfaces of CWM 1.1 and of the Daanse CWM extensions. */
    public static final List<String> KNOWN = List.of(
            "org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage",
            "org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.BehavioralPackage",
            "org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.RelationshipsPackage",
            "org.eclipse.daanse.cwm.model.cwm.objectmodel.instance.InstancePackage",
            "org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.DatatypesPackage",
            "org.eclipse.daanse.cwm.model.cwm.foundation.expressions.ExpressionsPackage",
            "org.eclipse.daanse.cwm.model.cwm.foundation.keysindexes.KeysindexesPackage",
            "org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationPackage",
            "org.eclipse.daanse.cwm.model.cwm.foundation.softwaredeployment.SoftwaredeploymentPackage",
            "org.eclipse.daanse.cwm.model.cwm.foundation.typemapping.TypemappingPackage",
            "org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalPackage",
            "org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EnumerationsPackage",
            "org.eclipse.daanse.cwm.model.cwm.resource.record.RecordPackage",
            "org.eclipse.daanse.cwm.model.cwm.resource.multidimensional.MultidimensionalPackage",
            "org.eclipse.daanse.cwm.model.cwm.resource.xml.XmlPackage",
            "org.eclipse.daanse.cwm.model.cwm.analysis.transformation.TransformationPackage",
            "org.eclipse.daanse.cwm.model.cwm.analysis.olap.OlapPackage",
            "org.eclipse.daanse.cwm.model.cwm.analysis.businessnomenclature.BusinessnomenclaturePackage",
            "org.eclipse.daanse.cwm.model.cwm.analysis.informationvisualization.InformationvisualizationPackage",
            "org.eclipse.daanse.cwm.model.cwm.management.warehouseprocess.WarehouseprocessPackage",
            "org.eclipse.daanse.cwm.model.cwm.management.warehouseprocess.events.EventsPackage",
            "org.eclipse.daanse.cwm.model.cwm.management.warehouseprocess.datatype.DatatypePackage",
            "org.eclipse.daanse.cwm.model.cwm.management.warehouseoperation.WarehouseoperationPackage",
            "org.eclipse.daanse.cwm.model.daanse.mapping.relational.CWMORMPackage",
            "org.eclipse.daanse.cwm.model.daanse.resource.sensinact.SensinactPackage",
            "org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.SensinactMappingPackage",
            "org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.LifecyclePackage",
            "org.eclipse.daanse.cwm.model.daanse.governance.recording.RecordingPackage",
            "org.eclipse.daanse.cwm.model.daanse.governance.obligations.ObligationsPackage",
            "org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationPackage",
            "org.eclipse.daanse.cwm.model.daanse.governance.gdpr.processing.ProcessingPackage",
            "org.eclipse.daanse.cwm.model.daanse.governance.gdpr.assessment.AssessmentPackage",
            "org.eclipse.daanse.cwm.model.daanse.governance.transparency.TransparencyPackage",
            "org.eclipse.daanse.cwm.model.daanse.governance.criticality.CriticalityPackage",
            "org.eclipse.daanse.cwm.model.daanse.units.UnitsPackage");

    private CwmPackages() {
    }

    /** Registers every {@link #KNOWN} package found by this class's loader; returns those found. */
    public static List<EPackage> registerKnown() {
        return registerKnown(CwmPackages.class.getClassLoader());
    }

    /** Registers every {@link #KNOWN} package found by {@code loader}; returns those found. */
    public static List<EPackage> registerKnown(ClassLoader loader) {
        List<EPackage> found = new ArrayList<>();
        for (String name : KNOWN) {
            try {
                found.add(register(name, loader));
            } catch (IllegalArgumentException e) {
                // not on the class path: the caller does not need it
            }
        }
        return found;
    }

    /**
     * Registers the generated EPackage whose interface is {@code interfaceName}: reading its
     * {@code eINSTANCE} initialises it, which puts it into the global package registry.
     *
     * @throws IllegalArgumentException if the class is missing or no EPackage interface
     */
    public static EPackage register(String interfaceName, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(interfaceName.trim(), true, loader);
            Object instance = type.getField("eINSTANCE").get(null);
            if (instance instanceof EPackage p) {
                EPackage.Registry.INSTANCE.putIfAbsent(p.getNsURI(), p);
                return p;
            }
            throw new IllegalArgumentException(interfaceName + " is no EPackage");
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalArgumentException("EPackage " + interfaceName + " is not available: " + e, e);
        }
    }

    /** A resource set reading {@code .ecore} as Ecore and everything else as XMI. */
    public static ResourceSet newResourceSet() {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
        return rs;
    }

    /** Loads {@code file} into {@code rs}; sibling files referenced by {@code href} load on demand. */
    public static Resource load(ResourceSet rs, Path file) {
        return rs.getResource(URI.createFileURI(file.toAbsolutePath().normalize().toString()), true);
    }

    /**
     * Loads a CWM model the way a tool reads its input: registers every {@link #KNOWN} package and
     * the {@code extraPackages} (interface names of further generated EPackages) found by
     * {@code loader}, loads {@code file} into a new resource set and resolves every reference, so
     * the files it refers to by {@code href} are loaded too. The resource set gets the
     * cross-reference index of {@link InverseReferences}, so the reverse lookups of the CWM
     * utilities (descriptions of an element, indexes spanning a table, the hidden opposite ends)
     * find their answer without scanning the model.
     *
     * @param extraPackages may be {@code null}
     * @throws IllegalArgumentException if an extra package is missing
     * @throws RuntimeException         if the file or a file it refers to cannot be read
     */
    public static Resource loadModel(Path file, ClassLoader loader, Collection<String> extraPackages) {
        registerKnown(loader);
        if (extraPackages != null) {
            for (String name : extraPackages) {
                register(name, loader);
            }
        }
        ResourceSet rs = newResourceSet();
        Resource model = load(rs, file);
        EcoreUtil.resolveAll(rs);
        InverseReferences.install(rs);
        return model;
    }

    /**
     * Writes {@code roots} as UTF-8 XMI to {@code file}, creating missing directories; every object
     * gets an id built from its name path below the roots ({@link SpeakingIds}), so other files can
     * refer to it by a readable {@code href}.
     *
     * <p>The model is written as it is and left as it was. An object lives in one resource, so the
     * roots themselves cannot go into the resource that is written without leaving the resource
     * they are in, or the one an earlier save wrote them to: a copy of them is written instead,
     * made in one go, so what the roots refer to among each other stays between the copies. What
     * they refer to outside them is written as a reference into the file it lives in; it has to lie
     * in one. Over a reference with an opposite end the copy refers to that element as the
     * original does, without the element learning of the copy.</p>
     *
     * @return the resource written, holding the copies
     * @throws UncheckedIOException when the file cannot be written, also for a reference to an
     *                              element that lies in no file
     */
    public static Resource save(Path file, Collection<? extends EObject> roots) {
        Path target = file.toAbsolutePath().normalize();
        Resource resource = newResourceSet().createResource(URI.createFileURI(target.toString()));
        resource.getContents().addAll(copy(roots));
        SpeakingIds.assign(resource);
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            resource.save(Map.of(XMLResource.OPTION_ENCODING, "UTF-8", XMLResource.OPTION_LINE_WIDTH, 120,
                    XMLResource.OPTION_SCHEMA_LOCATION, true));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + target, e);
        }
        return resource;
    }

    /**
     * A copy of {@code roots}, made by one copier, so their references among each other point to
     * the copies. A reference to an element outside the roots points to the element itself. The
     * copier of EMF leaves such a reference out where it has an opposite end, as setting it would
     * add the copy to the element outside; it is set here on the copy's side alone.
     */
    private static Collection<EObject> copy(Collection<? extends EObject> roots) {
        EcoreUtil.Copier copier = new EcoreUtil.Copier() {

            private static final long serialVersionUID = 1L;

            @Override
            protected void copyReference(EReference reference, EObject original, EObject copy) {
                super.copyReference(reference, original, copy);
                if (reference.getEOpposite() == null || !original.eIsSet(reference)) {
                    return;
                }
                if (reference.isMany()) {
                    List<?> values = (List<?>) original.eGet(reference);
                    @SuppressWarnings("unchecked")
                    InternalEList<EObject> target = (InternalEList<EObject>) copy.eGet(reference);
                    for (int i = 0; i < values.size(); i++) {
                        EObject value = (EObject) values.get(i);
                        if (get(value) == null) {
                            target.basicAdd(value, null); // without the inverse
                            target.move(i, value);
                        }
                    }
                } else if (original.eGet(reference) instanceof EObject value && get(value) == null) {
                    // sets the copy's end and leaves the other one as it is
                    ((InternalEObject) copy).eInverseAdd((InternalEObject) value, copy.eClass().getFeatureID(reference),
                            null, null);
                }
            }
        };
        Collection<EObject> copies = copier.copyAll(roots);
        copier.copyReferences();
        return copies;
    }
}
