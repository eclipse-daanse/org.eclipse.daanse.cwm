/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.orm.fennec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.fennec.persistence.eorm.EORMPackage;
import org.eclipse.fennec.persistence.eorm.EntityMappings;
import org.eclipse.fennec.persistence.eorm.util.EORMResourceFactoryImpl;
import org.eclipse.fennec.persistence.epersistence.EPersistencePackage;

/**
 * Writes and reads {@code .eorm} files the way Fennec does without OSGi: a resource set with the
 * Ecore, EORM and EPersistence packages registered and {@code EORMResourceFactoryImpl} for the
 * extension {@code eorm}. References into the Ecore model are written as {@code href}s relative
 * to the {@code .eorm} file, so the {@code .ecore} files belong next to it.
 */
public final class EormWriter {

    /** The file extension Fennec registers for EORM resources. */
    public static final String EXTENSION = "eorm";

    private EormWriter() {
    }

    /** A resource set that reads {@code .eorm}, {@code .ecore} and any other file as XMI. */
    public static ResourceSet newResourceSet() {
        ResourceSet rs = new ResourceSetImpl();
        rs.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
        rs.getPackageRegistry().put(EORMPackage.eNS_URI, EORMPackage.eINSTANCE);
        rs.getPackageRegistry().put(EPersistencePackage.eNS_URI, EPersistencePackage.eINSTANCE);
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put(EXTENSION, new EORMResourceFactoryImpl());
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
        return rs;
    }

    /**
     * Saves a mapping set as {@code file}. The Ecore elements it refers to must already live in
     * saved resources (the {@code .ecore} files of the class model), so that their {@code href}s can be made
     * relative to {@code file}.
     */
    public static Path save(EntityMappings eorm, Path file) {
        try {
            Path target = file.toAbsolutePath().normalize();
            Files.createDirectories(target.getParent());
            Resource r = newResourceSet().createResource(URI.createFileURI(target.toString()));
            r.getContents().add(eorm);
            Map<String, Object> options = new HashMap<>();
            options.put(XMLResource.OPTION_ENCODING, "UTF-8");
            options.put(XMLResource.OPTION_LINE_WIDTH, 120);
            r.save(options);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Loads the mapping set saved in {@code file} into {@code rs}. */
    public static EntityMappings load(ResourceSet rs, Path file) {
        Resource r = rs.getResource(URI.createFileURI(file.toAbsolutePath().normalize().toString()), true);
        return (EntityMappings) r.getContents().get(0);
    }
}
