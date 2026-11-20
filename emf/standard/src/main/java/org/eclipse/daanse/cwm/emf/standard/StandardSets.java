/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.emf.standard;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.URIHandlerImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;

/**
 * Standard sets of instances. A set is one XMI resource under a URI of its own. Every element of it has an
 * {@code xmi:id} that the code building the set states beside the element: a UUID in the short form EMF gives
 * ({@link EcoreUtil#generateUUID()}), generated once and written into the source. The URI of the set and the id of an
 * element are the element's published address, {@code href="<uri>#<id>"}; neither is ever changed, a new element gets
 * a new UUID, and an element is not removed.
 *
 * <p>{@link #text} writes a set the same way on every run: UTF-8, lines ended by a line feed, lines of at most 120
 * characters, the licence header after the XML declaration, a reference to another set by its absolute URI.
 * {@link #map} maps the URI of a set in a resource set to its file on the class path, so that the references of a
 * model file to it resolve from there and never from the network; {@link #load} reads it there at once.</p>
 */
public final class StandardSets {

    /** The system property that makes {@link #upToDate} write the file anew: {@code -Ddaanse.standard.write=true}. */
    public static final String WRITE = "daanse.standard.write";

    /** The short form of a UUID that EMF generates: an underscore and 22 characters. */
    public static final Pattern SHORT_UUID = Pattern.compile("_[A-Za-z0-9_-]{22}");

    private static final String HEADER = """
            <!--
            /*********************************************************************
            * Copyright (c) 2026 Contributors to the Eclipse Foundation.
            *
            * This program and the accompanying materials are made
            * available under the terms of the Eclipse Public License 2.0
            * which is available at https://www.eclipse.org/legal/epl-2.0/
            *
            * SPDX-License-Identifier: EPL-2.0
            **********************************************************************/
            -->
            """;

    private StandardSets() {
    }

    /** An empty set under {@code uri}, in a resource set of its own. */
    public static XMIResource create(String uri) {
        XMIResource set = new XMIResourceImpl(URI.createURI(Objects.requireNonNull(uri, "uri")));
        new ResourceSetImpl().getResources().add(set);
        return set;
    }

    /**
     * Gives {@code element} the id {@code id} in the XML resource it lies in, and returns it. An element in no XML
     * resource gets none, nor does an element whose resource has given the id to another element (a second copy
     * built into one model).
     *
     * @throws IllegalArgumentException where {@code id} is not of the form {@link #SHORT_UUID}
     */
    public static <T extends EObject> T id(T element, String id) {
        if (!SHORT_UUID.matcher(id).matches()) {
            throw new IllegalArgumentException("the id '" + id + "' is no UUID in the short form of EMF");
        }
        if (element.eResource() instanceof XMLResource resource && !resource.getIDToEObjectMap().containsKey(id)) {
            resource.setID(element, id);
        }
        return element;
    }

    /** The set as XMI text, the same on every run. */
    public static String text(Resource set) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            set.save(out, Map.of(XMLResource.OPTION_ENCODING, "UTF-8", XMLResource.OPTION_LINE_WIDTH, 120,
                    Resource.OPTION_LINE_DELIMITER, "\n", XMLResource.OPTION_URI_HANDLER, new Absolute()));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + set.getURI(), e);
        }
        String xml = out.toString(StandardCharsets.UTF_8);
        int declaration = xml.indexOf('\n') + 1;
        return xml.substring(0, declaration) + HEADER + xml.substring(declaration);
    }

    /**
     * Whether {@code file}, the checked-in file of {@code set}, holds its {@link #text}. With the system property
     * {@value #WRITE} set to {@code true} the file is written anew first.
     *
     * @throws UncheckedIOException where the file cannot be read or written
     */
    public static boolean upToDate(Resource set, Path file) {
        String text = text(set);
        try {
            if (Boolean.getBoolean(WRITE)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, text, StandardCharsets.UTF_8);
            }
            return Files.isRegularFile(file) && Files.readString(file, StandardCharsets.UTF_8).equals(text);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read or write " + file, e);
        }
    }

    /**
     * Maps the set under {@code uri} in {@code resourceSet} to {@code source}, its file on the class path (a set in a
     * jar: {@code Anchor.class.getResource("name.xmi")}), and registers the {@code packages} its elements are
     * instances of. A reference of a model file in the resource set to the set then reads it from there when it is
     * first followed, never from the network.
     *
     * @throws IllegalStateException where {@code source} is {@code null}: the set is not on the class path
     */
    public static void map(ResourceSet resourceSet, String uri, URL source, EPackage... packages) {
        if (source == null) {
            throw new IllegalStateException("the set " + uri + " is not on the class path");
        }
        for (EPackage p : packages) {
            resourceSet.getPackageRegistry().put(p.getNsURI(), p);
        }
        URI at = URI.createURI(uri);
        resourceSet.getURIConverter().getURIMap().put(at, URI.createURI(source.toString()));
        if (resourceSet.getResource(at, false) == null) {
            resourceSet.getResources().add(new XMIResourceImpl(at));
        }
    }

    /**
     * The root of the set under {@code uri} in {@code resourceSet}, an instance of {@code rootType}, read now where it
     * is not read yet; the set is {@link #map mapped} there.
     *
     * @throws IllegalStateException where the set is not mapped in the resource set or has no root of
     *                               {@code rootType}
     * @throws UncheckedIOException  where the set cannot be read
     */
    public static <T extends EObject> T load(ResourceSet resourceSet, String uri, Class<T> rootType) {
        Resource set = resourceSet.getResource(URI.createURI(uri), false);
        if (set == null) {
            throw new IllegalStateException("the set " + uri + " is not mapped in the resource set");
        }
        if (set.getContents().isEmpty()) {
            set.unload();
            try {
                set.load(resourceSet.getLoadOptions());
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read the set " + uri, e);
            }
        }
        if (set.getContents().isEmpty() || !rootType.isInstance(set.getContents().get(0))) {
            throw new IllegalStateException(uri + " holds no " + rootType.getSimpleName() + " as its root");
        }
        return rootType.cast(set.getContents().get(0));
    }

    /** Writes a reference to another set by its absolute URI, not relative to the set that refers. */
    private static final class Absolute extends URIHandlerImpl {

        @Override
        public URI deresolve(URI uri) {
            return uri;
        }
    }
}
