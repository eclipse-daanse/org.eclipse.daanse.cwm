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

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.DataTypes;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Models;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Row;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RowSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.SemanticType;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.TypeLibrary;

/**
 * A harbour register of the Baltic coast: model {@code register} with the catalog {@code ports} (table {@code port}
 * with the columns {@code name}, {@code town}, {@code postcode}, {@code opened}; table {@code ledger} with its rows
 * {@code quays} and {@code dues}), the data type {@code Postcode}, the package {@code types} for the typings, and the
 * library {@code harbour} ({@code https://types.example.org/harbour}) with the types {@code Place}, {@code Town}
 * (narrower than Place), {@code HarbourTown} (narrower than Town), {@code PostalCode}, {@code Harbour}, {@code Asset}
 * and {@code Revenue}.
 */
final class Register {

    static final String HARBOUR = "https://types.example.org/harbour";

    final Model model = Models.create("register");
    final Package types = Packages.create(model, "types");
    final Catalog ports = Catalogs.create(model, "ports");
    final Schema schema = Schemas.create(ports, "public");
    final SQLSimpleType varchar = SQLSimpleTypes.ownedIn(ports, "VARCHAR");
    final Table port = Tables.create(schema, "port");
    final Column name = Columns.create(port, "name", varchar);
    final Column town = Columns.create(port, "town", varchar);
    final Column postcode = Columns.create(port, "postcode", varchar);
    final Column opened = Columns.create(port, "opened", SQLSimpleTypes.ownedIn(ports, "DATE"));
    final Table ledger = Tables.create(schema, "ledger");
    final RowSet ledgerRows = rowSet(schema, "ledger_rows");
    final Row quays = row(ledgerRows, ledger, "quays");
    final Row dues = row(ledgerRows, ledger, "dues");
    final DataType postcodeType = DataTypes.create(model, "Postcode");

    final TypeLibrary library = TypeLibraries.create(model, "harbour", HARBOUR);
    final SemanticType place = SemanticTypes.create(library, "Place");
    final SemanticType townType = SemanticTypes.broader(SemanticTypes.create(library, "Town"), place);
    final SemanticType harbourTown = SemanticTypes.broader(SemanticTypes.create(library, "HarbourTown"), townType);
    final SemanticType postalCode = SemanticTypes.create(library, "PostalCode");
    final SemanticType harbour = SemanticTypes.create(library, "Harbour");
    final SemanticType asset = SemanticTypes.create(library, "Asset");
    final SemanticType revenue = SemanticTypes.create(library, "Revenue");

    /** A second model {@code atlas} with the library {@code coast} ({@code https://types.example.org/coast}). */
    static Model atlas() {
        Model atlas = Models.create("atlas");
        TypeLibrary coast = TypeLibraries.create(atlas, "coast", "https://types.example.org/coast");
        SemanticTypes.create(coast, "Lighthouse");
        SemanticTypes.create(coast, "Coast");
        return atlas;
    }

    private static RowSet rowSet(Schema owner, String name) {
        RowSet rows = RelationalFactory.eINSTANCE.createRowSet();
        rows.setName(name);
        owner.getOwnedElement().add(rows);
        return rows;
    }

    private static Row row(RowSet owner, Table table, String name) {
        Row row = RelationalFactory.eINSTANCE.createRow();
        row.setName(name);
        row.setClassifier(table);
        owner.getOwnedElement().add(row);
        return row;
    }

    /** A class that is no relational column set, as an OLAP attribute or dimension is. */
    org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class plainClass(String name) {
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class c = CoreFactory.eINSTANCE.createClass();
        c.setName(name);
        model.getOwnedElement().add(c);
        return c;
    }
}
