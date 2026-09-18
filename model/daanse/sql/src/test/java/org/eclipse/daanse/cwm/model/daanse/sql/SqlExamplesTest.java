/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Map;

import org.eclipse.daanse.cwm.model.daanse.sql.select.QueryExpression;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Validiert das gesamte Beispiel-Set bei jedem Build: jede Instanz wird gebaut, als XMI
 * serialisiert, wieder geladen und auf einen nicht-leeren Body geprueft.
 */
class SqlExamplesTest {

    @Test
    void everyExampleBuildsAndRoundTrips(@TempDir File tmp) throws Exception {
        Map<String, QueryExpression> examples = SqlExamples.all();
        assertThat(examples).isNotEmpty();

        for (Map.Entry<String, QueryExpression> e : examples.entrySet()) {
            String name = e.getKey();

            ResourceSet rs = newXmi();
            File file = new File(tmp, name + ".sql");
            Resource res = rs.createResource(URI.createFileURI(file.getAbsolutePath()));
            res.getContents().add(e.getValue());

            // Beispiele wie typed() verweisen auf CWM-Elemente (Table, Column), die
            // keinen Container haben. XMI kann eine Referenz auf ein nicht in einer
            // Ressource stehendes Objekt nicht schreiben, deshalb bekommen sie eine
            // Nachbarressource - dasselbe Vorgehen wie in TabularModelRoundtripTest.
            Resource side = rs.createResource(
                    URI.createFileURI(new File(tmp, name + "-cwm.sql").getAbsolutePath()));
            for (java.util.List<org.eclipse.emf.ecore.EObject> more = danglingTargets(res);
                    !more.isEmpty(); more = danglingTargets(res, side)) {
                side.getContents().addAll(more);
            }
            if (!side.getContents().isEmpty()) {
                side.save(null);
            }

            res.save(null);
            assertThat(file).as("written: %s", name).isFile();

            ResourceSet rs2 = newXmi();
            Resource loaded = rs2.getResource(URI.createFileURI(file.getAbsolutePath()), true);
            assertThat(loaded.getContents()).as("reloaded: %s", name).hasSize(1);
            QueryExpression qe = (QueryExpression) loaded.getContents().get(0);
            assertThat(qe.getBody()).as("body: %s", name).isNotNull();
        }
    }

    /**
     * Sammelt die Wurzeln aller von {@code res} aus referenzierten Objekte, die in
     * keiner Ressource stehen - in der Reihenfolge des Auftretens und ohne
     * Doppelungen.
     */
    private static java.util.List<org.eclipse.emf.ecore.EObject> danglingTargets(
            Resource... resources) {
        java.util.LinkedHashSet<org.eclipse.emf.ecore.EObject> roots = new java.util.LinkedHashSet<>();
        for (Resource res : resources) {
            for (java.util.Iterator<org.eclipse.emf.ecore.EObject> it = res.getAllContents(); it
                    .hasNext();) {
                org.eclipse.emf.ecore.EObject o = it.next();
                for (org.eclipse.emf.ecore.EObject target : o.eCrossReferences()) {
                    if (target.eResource() == null) {
                        roots.add(org.eclipse.emf.ecore.util.EcoreUtil.getRootContainer(target));
                    }
                }
            }
        }
        return java.util.List.copyOf(roots);
    }

    private static ResourceSet newXmi() {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("sql", new XMIResourceFactoryImpl());
        return rs;
    }
}
