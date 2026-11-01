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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

/**
 * Makes CWM XMI loadable without OSGi: registers the generated EPackages that are on the class
 * path and creates resource sets that read XMI under any file extension.
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
            "org.eclipse.daanse.cwm.model.daanse.orm.CWMORMPackage");

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
}
