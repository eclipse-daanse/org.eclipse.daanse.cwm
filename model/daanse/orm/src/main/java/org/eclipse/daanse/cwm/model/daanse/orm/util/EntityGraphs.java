/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.orm.util;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.Entity;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedAttributeNode;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedEntityGraph;
import org.eclipse.daanse.cwm.model.daanse.orm.NamedSubgraph;

/** Creates named entity graphs; nodes are named after their feature. */
public final class EntityGraphs {

    private EntityGraphs() {
    }

    /** A named entity graph rooted at {@code entity}. */
    public static NamedEntityGraph create(Entity entity, String name) {
        NamedEntityGraph g = CWMORMFactory.eINSTANCE.createNamedEntityGraph();
        g.setName(name);
        entity.getNamedEntityGraph().add(g);
        return g;
    }

    /** A node fetching {@code feature} of the root entity. */
    public static NamedAttributeNode node(NamedEntityGraph graph, StructuralFeature feature) {
        NamedAttributeNode n = node(feature);
        graph.getNamedAttributeNode().add(n);
        return n;
    }

    /** A node fetching {@code feature} of the subgraph's type. */
    public static NamedAttributeNode node(NamedSubgraph subgraph, StructuralFeature feature) {
        NamedAttributeNode n = node(feature);
        subgraph.getNamedAttributeNode().add(n);
        return n;
    }

    /** A subgraph of {@code graph} for the type {@code cls}, with a graph-local name. */
    public static NamedSubgraph subgraph(NamedEntityGraph graph, String name, Class cls) {
        NamedSubgraph s = CWMORMFactory.eINSTANCE.createNamedSubgraph();
        s.setName(name);
        s.setCwmClass(cls);
        graph.getSubgraph().add(s);
        return s;
    }

    private static NamedAttributeNode node(StructuralFeature feature) {
        NamedAttributeNode n = CWMORMFactory.eINSTANCE.createNamedAttributeNode();
        n.setName(feature.getName());
        n.setCwmFeature(feature);
        return n;
    }
}
