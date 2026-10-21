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
 *   SmartCity Jena - initial
 */
package org.eclipse.daanse.cwm.model.daanse.resource.relational.synonym.util;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Namespaces;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.synonym.Synonym;

/**
 * Access to the {@link Synonym}s of a {@link Schema} and resolution of synonym chains.
 */
public final class Synonyms {

    private Synonyms() {
    }

    /** The synonyms {@code schema} owns directly. */
    public static List<Synonym> synonyms(Schema schema) {
        return synonymStream(schema).toList();
    }

    /** Stream of the synonyms {@code schema} owns directly. */
    public static Stream<Synonym> synonymStream(Schema schema) {
        return Namespaces.ownedElementStream(schema, Synonym.class);
    }

    /** The synonym {@code name} in {@code schema}. */
    public static Optional<Synonym> find(Schema schema, String name) {
        return Namespaces.findOwnedByName(schema, Synonym.class, name);
    }

    /**
     * Follows {@link Synonym#getTarget()} along synonym chains to the first element that is
     * not a synonym (table, view, procedure, ...).
     *
     * @return empty when a link of the chain has no target in the model or the chain forms a
     *         cycle (Oracle allows creating one; ORA-01775 is raised only on use)
     */
    public static Optional<ModelElement> resolveFinal(Synonym synonym) {
        Set<Synonym> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ModelElement current = synonym;
        while (current instanceof Synonym s) {
            if (!visited.add(s)) {
                return Optional.empty();
            }
            current = s.getTarget();
        }
        return Optional.ofNullable(current);
    }
}
