/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.sensinact.fennec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceFactoryImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLHelper;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIHelperImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.fennec.event.atlas.model.mapping.MappingPackage;

/**
 * Writes and reads Fennec event.atlas mapping and profile files.
 *
 * <p>A reference to an element of the source Ecore model is written as
 * {@code <nsURI of its EPackage>#//Class/feature}: Fennec finds mappings by the identity of the
 * source EClass and resolves these proxies against the EPackages registered by namespace URI (only
 * the fragments {@code /}, {@code //Class} and {@code //Class/feature}); a file-relative
 * {@code href} would load a second copy of the package. A reference to a profile stays relative
 * ({@code ../profiles/<provider>-profile.xmi#<profileId>}).</p>
 */
public final class AtlasWriter {

    /** The directory of the mapping files below the output directory. */
    public static final String MAPPINGS = "mappings";
    /** The directory of the profile files below the output directory. */
    public static final String PROFILES = "profiles";

    private AtlasWriter() {
    }

    /** A resource set that reads {@code .ecore} files as Ecore and every other file as an event.atlas XMI. */
    public static ResourceSet newResourceSet() {
        ResourceSet rs = new ResourceSetImpl();
        rs.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
        rs.getPackageRegistry().put(MappingPackage.eNS_URI, MappingPackage.eINSTANCE);
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new ResourceFactoryImpl() {
            @Override
            public Resource createResource(URI uri) {
                return new AtlasResource(uri);
            }
        });
        return rs;
    }

    /** Saves {@code root} as {@code file} in {@code rs}; the resource stays in {@code rs}. */
    public static Path save(ResourceSet rs, EObject root, Path file) {
        try {
            Path target = file.toAbsolutePath().normalize();
            Files.createDirectories(target.getParent());
            Resource r = rs.createResource(URI.createFileURI(target.toString()));
            r.getContents().add(root);
            Map<String, Object> options = new HashMap<>();
            options.put(XMLResource.OPTION_ENCODING, "UTF-8");
            options.put(XMLResource.OPTION_LINE_WIDTH, 120);
            r.save(options);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Loads the root object of {@code file} into {@code rs}. */
    public static EObject load(ResourceSet rs, Path file) {
        return rs.getResource(URI.createFileURI(file.toAbsolutePath().normalize().toString()), true).getContents().get(0);
    }

    /**
     * The {@code href} of an element of a source Ecore model by namespace URI, or {@code null} for
     * anything else (event.atlas objects, profiles).
     */
    static String nsHref(EObject object) {
        if (object instanceof EPackage p) {
            return p.getNsURI() + "#/";
        }
        if (object instanceof EClassifier c && c.getEPackage() != null && c.getEPackage() != MappingPackage.eINSTANCE) {
            return c.getEPackage().getNsURI() + "#//" + c.getName();
        }
        if (object instanceof EStructuralFeature f && f.getEContainingClass() != null
                && f.getEContainingClass().getEPackage() != null
                && f.getEContainingClass().getEPackage() != MappingPackage.eINSTANCE) {
            return f.getEContainingClass().getEPackage().getNsURI() + "#//" + f.getEContainingClass().getName() + "/"
                    + f.getName();
        }
        return null;
    }

    /** An XMI resource whose references into Ecore models go by namespace URI. */
    static final class AtlasResource extends XMIResourceImpl {

        AtlasResource(URI uri) {
            super(uri);
        }

        @Override
        protected XMLHelper createXMLHelper() {
            return new XMIHelperImpl(this) {
                @Override
                public String getHREF(EObject obj) {
                    String href = obj.eResource() == AtlasResource.this ? null : nsHref(obj);
                    return href != null ? href : super.getHREF(obj);
                }
            };
        }
    }
}
