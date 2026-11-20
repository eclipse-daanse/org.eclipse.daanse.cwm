/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.semantictype.base.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.SemanticType;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.TypeLibrary;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.Typing;
import org.junit.jupiter.api.Test;

/** Typings: how they are made, how an element finds its type, and how the typings of a model are filtered. */
class TypingsTest {

    private final Register r = new Register();

    @Test
    void aTypingNamesItsElementsAndTheirType() {
        Typing town = Typings.create(r.types, "town", r.harbourTown, r.town, r.name);
        assertThat(town.getNamespace()).as("the owner of the typing").isSameAs(r.types);
        assertThat(town.getType()).as("the type").isSameAs(r.harbourTown);
        assertThat(town.getModelElement()).as("the elements").containsExactlyInAnyOrder(r.town, r.name);
        assertThatThrownBy(() -> Typings.create(r.types, "none", r.place)).as("a typing without elements")
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** One type types the table (its rows are harbours) and a column (its values are harbours). */
    @Test
    void anElementFindsItsType() {
        Typings.create(r.types, "harbour", r.harbour, r.port, r.name);
        assertThat(Typings.own(r.port)).as("the typings of the table").hasSize(1);
        assertThat(Typings.typeOf(r.port)).as("the type of the table").contains(r.harbour);
        assertThat(Typings.typeOf(r.name)).as("the type of the column").contains(r.harbour);
        assertThat(Typings.typeOf(r.opened)).as("an untyped column").isEmpty();
        Model atlas = Register.atlas();
        TypeLibrary coast = TypeLibraries.byUri(atlas, "https://types.example.org/coast").orElseThrow();
        SemanticType coastType = SemanticTypes.named(coast, "Coast").orElseThrow();
        Typings.create(r.types, "coast", coastType, r.port);
        assertThat(Typings.typesOf(r.port)).as("typed in two vocabularies").containsExactlyInAnyOrder(r.harbour,
                coastType);
    }

    @Test
    void theTypingsOfAModelAreFilteredByTypeOrNarrower() {
        Typing town = Typings.create(r.types, "town", r.townType, r.town);
        Typing name = Typings.create(r.types, "name", r.harbourTown, r.name);
        Typing postcode = Typings.create(r.types, "postcode", r.postalCode, r.postcode);
        Typing harbour = Typings.create(r.types, "harbour", r.harbour, r.port);
        assertThat(Typings.all(r.model)).as("all typings").containsExactly(town, name, postcode, harbour);
        assertThat(Typings.ofType(r.model, r.place)).as("a place or narrower").containsExactly(town, name);
        assertThat(Typings.ofType(r.model, r.harbourTown)).as("a harbour town").containsExactly(name);
        assertThat(Typings.ofType(r.types, r.harbour)).as("within the package of typings").containsExactly(harbour);
    }

    @Test
    void theTypingsOfAModelAreFilteredByLibrary() {
        Model atlas = Register.atlas();
        TypeLibrary coast = TypeLibraries.byUri(atlas, "https://types.example.org/coast").orElseThrow();
        Typing town = Typings.create(r.types, "town", r.townType, r.town);
        Typing name = Typings.create(r.types, "name", SemanticTypes.named(coast, "Lighthouse").orElseThrow(),
                r.name);
        Typing quays = Typings.create(r.types, "quays", r.asset, r.quays);
        assertThat(Typings.inLibrary(r.model, r.library)).as("types of the harbour library").containsExactly(town,
                quays);
        assertThat(Typings.inLibrary(r.model, "https://types.example.org/coast")).as("by the URI of a library")
                .containsExactly(name);
        assertThat(Typings.inLibrary(r.model, "https://types.example.org/none")).as("a library not used").isEmpty();
        assertThat(Typings.typeOf(r.quays)).as("the type of a row").contains(r.asset);
    }
}
