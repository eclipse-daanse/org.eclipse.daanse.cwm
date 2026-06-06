/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.cwm.testkit.api;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Finds CSV files packaged as classpath resources beside an anchor class.
 */
public final class CsvAutoDetect {

    private CsvAutoDetect() {
    }

    /**
     * Returns the {@code .csv} resources under
     * {@code <anchor package>/<subFolder>/}, keyed by file name without extension.
     * The map keeps alphabetical order, so name files to load parents before
     * children when foreign keys require it.
     */
    public static Map<String, URL> detect(Class<?> anchor, String subFolder) {
        String pkgPath = anchor.getPackageName().replace('.', '/');
        String prefix = pkgPath + "/" + subFolder + "/";

        TreeSet<String> sortedNames = new TreeSet<>();
        Map<String, URL> byName = new LinkedHashMap<>();

        ClassLoader cl = anchor.getClassLoader();
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }

        try {
            Enumeration<URL> roots = cl.getResources(prefix);
            for (URL root : Collections.list(roots)) {
                collectFrom(root, prefix, sortedNames, byName);
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to scan classpath for " + prefix + " (anchor=" + anchor.getName() + ")", e);
        }

        LinkedHashMap<String, URL> ordered = new LinkedHashMap<>();
        for (String name : sortedNames) {
            ordered.put(name, byName.get(name));
        }
        return ordered;
    }

    private static void collectFrom(URL root, String prefix, TreeSet<String> sortedNames, Map<String, URL> byName)
            throws IOException {
        switch (root.getProtocol()) {
        case "file" -> collectFromFile(root, sortedNames, byName);
        case "jar" -> collectFromJar(root, prefix, sortedNames, byName);
        default -> collectFromGenericFs(root, prefix, sortedNames, byName);
        }
    }

    private static void collectFromFile(URL root, TreeSet<String> sortedNames, Map<String, URL> byName)
            throws IOException {
        Path dir = Paths.get(URI.create(root.toString()));
        try (Stream<Path> walk = Files.list(dir)) {
            walk.filter(p -> p.toString().endsWith(".csv"))
                    .forEach(p -> add(p.getFileName().toString(), pathToUrl(p), sortedNames, byName));
        }
    }

    private static void collectFromJar(URL root, String prefix, TreeSet<String> sortedNames, Map<String, URL> byName)
            throws IOException {
        String spec = root.getFile();
        int bang = spec.indexOf("!/");
        String jarPath = spec.substring("file:".length(), bang);
        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                if (e.isDirectory()) {
                    continue;
                }
                String name = e.getName();
                if (!name.startsWith(prefix) || !name.endsWith(".csv")) {
                    continue;
                }
                String fileName = name.substring(prefix.length());
                if (fileName.contains("/")) {
                    continue;
                }
                URL entryUrl = new URL("jar:file:" + jarPath + "!/" + name);
                add(fileName, entryUrl, sortedNames, byName);
            }
        }
    }

    private static void collectFromGenericFs(URL root, String prefix, TreeSet<String> sortedNames,
            Map<String, URL> byName) throws IOException {
        URI uri;
        try {
            uri = root.toURI();
        } catch (Exception e) {
            throw new IOException("Bad URI: " + root, e);
        }
        try (FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
            Path dir = fs.getPath("/" + prefix);
            try (Stream<Path> walk = Files.list(dir)) {
                walk.filter(p -> p.toString().endsWith(".csv"))
                        .forEach(p -> add(p.getFileName().toString(), pathToUrl(p), sortedNames, byName));
            }
        }
    }

    private static void add(String fileName, URL url, TreeSet<String> sortedNames, Map<String, URL> byName) {
        String key = fileName.substring(0, fileName.length() - ".csv".length());
        sortedNames.add(key);
        byName.put(key, url);
    }

    private static URL pathToUrl(Path p) {
        try {
            return p.toUri().toURL();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
