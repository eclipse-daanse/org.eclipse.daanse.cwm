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
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.ExternalType;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.SemanticType;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.TypeLibrary;
import org.junit.jupiter.api.Test;

/** Libraries and types: how they are made, what their URI is, how they are found and filtered. */
class SemanticTypesTest {

    private final Register r = new Register();

    @Test
    void aLibraryOwnsItsTypesAndSaysWhereTheyComeFrom() {
        TypeLibraries.source(r.library, "Harbour Register", "2");
        assertThat(r.library.getUri()).as("the URI of the library").isEqualTo(Register.HARBOUR);
        assertThat(r.library.getSourceSystem()).as("the system").isEqualTo("Harbour Register");
        assertThat(r.library.getSourceVersion()).as("the version").isEqualTo("2");
        assertThat(SemanticTypes.of(r.library)).as("the types of the library, in order").containsExactly(r.place,
                r.townType, r.harbourTown, r.postalCode, r.harbour, r.asset, r.revenue);
        assertThat(SemanticTypes.libraryOf(r.place)).as("the library of a type").contains(r.library);
        assertThat(TypeLibraries.byUri(r.model, Register.HARBOUR)).as("the library by its URI").contains(r.library);
        assertThat(TypeLibraries.byUri(r.model, "https://types.example.org/none")).as("no library").isEmpty();
        assertThat(TypeLibraries.all(r.model)).as("the libraries of the model").containsExactly(r.library);
    }

    @Test
    void aTypeHasItsNameAndNumberThere() {
        SemanticTypes.description(SemanticTypes.source(r.harbour, "Port", 0), "A harbour of the coast.");
        assertThat(r.harbour.getSourceName()).as("the name there").isEqualTo("Port");
        assertThat(r.harbour.isSetSourceNumber()).as("the number there is set, even as 0").isTrue();
        assertThat(r.harbour.getSourceNumber()).as("the number there").isZero();
        assertThat(r.harbour.getDescription()).as("the description").isEqualTo("A harbour of the coast.");
        assertThat(r.place.isSetSourceNumber()).as("a type of one's own has no number there").isFalse();
        assertThat(r.place.getSourceName()).as("nor a name there").isNull();
    }

    @Test
    void theUriIsStatedOrDerivedFromTheLibraryAndTheName() {
        assertThat(SemanticTypes.uriOf(r.townType)).as("derived URI").contains(Register.HARBOUR + "/Town");
        assertThat(SemanticTypes.uriOf(SemanticTypes.create(r.library, "Quay Side"))).as("a name with a blank")
                .contains(Register.HARBOUR + "/Quay%20Side");
        assertThat(SemanticTypes.derivedUri("urn:example:harbour#", "Dues")).as("after a hash")
                .isEqualTo("urn:example:harbour#Dues");
        assertThat(SemanticTypes.derivedUri("https://types.example.org/harbour/", "Kai Süd"))
                .as("after a slash, a letter beyond ASCII encoded in UTF-8")
                .isEqualTo("https://types.example.org/harbour/Kai%20S%C3%BCd");
        SemanticTypes.uri(r.place, "https://types.example.org/place");
        assertThat(SemanticTypes.uriOf(r.place)).as("stated URI").contains("https://types.example.org/place");
        assertThat(SemanticTypes.byUri(r.model, "https://types.example.org/place")).as("found by stated URI")
                .contains(r.place);
        assertThat(SemanticTypes.byUri(r.model, Register.HARBOUR + "/Asset")).as("found by derived URI")
                .contains(r.asset);
    }

    @Test
    void aTypeIsFoundByNameByNameThereAndByNumber() {
        SemanticTypes.source(r.harbour, "Port", 7);
        assertThat(SemanticTypes.named(r.library, "Town")).as("by name").contains(r.townType);
        assertThat(SemanticTypes.named(r.library, "Port")).as("by name, which is ours").isEmpty();
        assertThat(SemanticTypes.byNameThere(r.library, "Port")).as("by name there").contains(r.harbour);
        assertThat(SemanticTypes.byNumber(r.library, 7)).as("by number there").contains(r.harbour);
        assertThat(SemanticTypes.byNumber(r.library, 0)).as("an unset number is no 0").isEmpty();
    }

    @Test
    void aTypeIsNarrowerThanItsBroaderTypesTransitively() {
        assertThat(SemanticTypes.isA(r.harbourTown, r.place)).as("harbour town is a place").isTrue();
        assertThat(SemanticTypes.isA(r.harbourTown, r.harbourTown)).as("a type is itself").isTrue();
        assertThat(SemanticTypes.isA(r.place, r.townType)).as("a place is no town").isFalse();
        assertThat(SemanticTypes.broaderClosure(r.harbourTown)).as("nearest first").containsExactly(r.harbourTown,
                r.townType, r.place);
        assertThat(SemanticTypes.narrowerThan(r.place)).as("narrower than place, in the model")
                .containsExactly(r.townType, r.harbourTown);
        SemanticTypes.broader(r.place, r.harbourTown);
        assertThat(SemanticTypes.broaderClosure(r.townType)).as("a cycle ends the walk").containsExactly(r.townType,
                r.place, r.harbourTown);
    }

    @Test
    void narrowerTypesOfAnotherModelAreFoundWhenItIsAScope() {
        Model atlas = Register.atlas();
        TypeLibrary coast = TypeLibraries.byUri(atlas, "https://types.example.org/coast").orElseThrow();
        SemanticType lighthouse = SemanticTypes.named(coast, "Lighthouse").orElseThrow();
        SemanticTypes.broader(lighthouse, r.place);
        assertThat(SemanticTypes.narrowerThan(r.place)).as("only the model of the type").doesNotContain(lighthouse);
        assertThat(SemanticTypes.narrowerThan(r.place, r.model, atlas)).as("both models")
                .containsExactly(r.townType, r.harbourTown, lighthouse);
        assertThat(SemanticTypes.all(atlas)).as("the types of the atlas").hasSize(2);
    }

    @Test
    void theSameTypeElsewhereIsAReferenceOrANameAndNumberThere() {
        Model atlas = Register.atlas();
        TypeLibrary coast = TypeLibraries.byUri(atlas, "https://types.example.org/coast").orElseThrow();
        SemanticType coastType = SemanticTypes.named(coast, "Coast").orElseThrow();
        SemanticTypes.sameAs(r.harbour, coastType);
        ExternalType gazetteer = SemanticTypes.sameAsExternal(r.townType, "https://types.example.org/gazetteer",
                "Town", 12L);
        assertThat(r.harbour.getSameAs()).as("the same type in another library").containsExactly(coastType);
        assertThat(r.townType.getSameAsExternal()).as("the external type is owned by the type")
                .containsExactly(gazetteer);
        assertThat(gazetteer.getSourceNumber()).as("its number there").isEqualTo(12);
        assertThatThrownBy(() -> SemanticTypes.sameAsExternal(r.place, "https://types.example.org/gazetteer", null,
                null)).as("neither name nor number").isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a name or a number");
    }
}
