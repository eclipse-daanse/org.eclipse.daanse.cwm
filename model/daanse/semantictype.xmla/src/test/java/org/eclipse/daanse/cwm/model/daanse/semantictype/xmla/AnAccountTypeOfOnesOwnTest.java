/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.semantictype.xmla;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Models;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Row;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RowSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.SemanticType;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.TypeLibrary;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.Typing;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.SemanticTypeModelValidator;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.SemanticTypes;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.TypeLibraries;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.Typings;
import org.junit.jupiter.api.Test;

/**
 * The server's list of account types can be extended: a harbour authority adds its own account types in its own
 * library, one narrower than the server's Asset, one the same as its Liability, and types the rows of its ledger with
 * them. The server's libraries are delivered beside the model, recognised by their URIs.
 */
class AnAccountTypeOfOnesOwnTest {

    private final Model delivered = Models.create("delivered");
    private final TypeLibrary accounts;
    private final Model authority = Models.create("authority");
    private final TypeLibrary ledgerTypes = TypeLibraries.create(authority, "ledger",
            "https://types.example.org/harbour-ledger");
    private final SemanticType quayDues;
    private final SemanticType deposits;

    AnAccountTypeOfOnesOwnTest() {
        ServerTypes.create(delivered);
        accounts = ServerTypes.accounts(delivered);
        quayDues = SemanticTypes.broader(SemanticTypes.create(ledgerTypes, "QuayDues"),
                ServerTypes.type(accounts, "Asset"));
        deposits = SemanticTypes.sameAs(SemanticTypes.create(ledgerTypes, "Deposits"),
                ServerTypes.type(accounts, "Liability"));
    }

    @Test
    void anAccountTypeOfOnesOwnIsNarrowerThanOrTheSameAsOneOfTheSeven() {
        Table ledger = Tables.create(Schemas.create(Catalogs.create(authority, "accounts"), "public"), "ledger");
        RowSet rows = RelationalFactory.eINSTANCE.createRowSet();
        rows.setName("ledger_rows");
        authority.getOwnedElement().add(rows);
        Row dues = row(rows, ledger, "dues");
        Row bonds = row(rows, ledger, "bonds");
        Typing duesTyping = Typings.create(Packages.create(authority, "types"), "dues", quayDues, dues);
        Typings.create(Packages.create(authority, "moreTypes"), "bonds", deposits, bonds);

        assertThat(SemanticTypeModelValidator.check(authority, ServerTypes.LIBRARIES))
                .as("findings with the server's libraries delivered").isEmpty();
        assertThat(XmlaTypesValidator.check(authority)).as("findings of the server's rules").isEmpty();
        assertThat(SemanticTypeModelValidator.check(authority)).as("findings without them")
                .extracting(f -> f.rule()).containsOnly("ST8");
        assertThat(XmlaCodes.serverName(quayDues, ServerTypes.ACCOUNT_URI)).as("the server's account type of the dues")
                .contains("Asset");
        assertThat(XmlaCodes.serverName(deposits, ServerTypes.ACCOUNT_URI)).as("the server's account type of bonds")
                .contains("Liability");
        assertThat(XmlaCodes.levelType(quayDues)).as("an account type has no level code").isZero();
        assertThat(Typings.ofType(authority, ServerTypes.type(accounts, "Asset")))
                .as("the typings of an asset or narrower").containsExactly(duesTyping);
        assertThat(Typings.inLibrary(authority, ServerTypes.ACCOUNT_URI)).as("no typing names the server's type")
                .isEmpty();
    }

    @Test
    void anAccountTypeTypesNoColumn() {
        Table ledger = Tables.create(Schemas.create(Catalogs.create(authority, "accounts"), "public"), "ledger");
        Column kind = Columns.create(ledger, "kind", SQLSimpleTypes.ownedIn(ledger.getNamespace(), "VARCHAR"));
        Typings.create(Packages.create(authority, "types"), "kind", quayDues, kind);
        assertThat(XmlaTypesValidator.check(authority)).as("an own account type on a column").singleElement()
                .satisfies(f -> assertThat(f.rule()).isEqualTo("T3"))
                .satisfies(f -> assertThat(f.message()).contains("reaches the server's account type Asset"));
    }

    private static Row row(RowSet owner, Table table, String name) {
        Row row = RelationalFactory.eINSTANCE.createRow();
        row.setName(name);
        row.setClassifier(table);
        owner.getOwnedElement().add(row);
        return row;
    }
}
