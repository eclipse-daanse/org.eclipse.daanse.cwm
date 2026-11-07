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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.model.cwm.resource.relational.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.sql.Types;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Parameter;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.util.Parameters;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.DataTypes;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Models;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.QueryColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLParameter;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.junit.jupiter.api.Test;

class CreateHelpersTest {

    @Test
    void catalogSchemaTableColumns_andKeys() {
        Model model = Models.create("Database 1");
        Catalog shop = Catalogs.create(model, "SHOP");
        SQLSimpleType bigint = SQLSimpleTypes.ownedIn(shop, "BIGINT");
        SQLSimpleType varchar = SQLSimpleTypes.ownedIn(shop, "VARCHAR");
        Schema sales = Schemas.create(shop, "SALES");
        Table customer = Tables.create(sales, "CUSTOMER");
        Column id = Columns.create(customer, "ID", bigint);
        Column name = Columns.create(customer, "NAME", varchar, NullableType.COLUMN_NULLABLE, 100, 0, 0);
        PrimaryKey pk = PrimaryKeys.create(customer, "PK_CUSTOMER", id);
        UniqueConstraint uq = UniqueConstraints.create(customer, "UQ_NAME", name);
        Table orders = Tables.create(sales, "ORDERS");
        Column customerId = Columns.create(orders, "CUSTOMER_ID", bigint, NullableType.COLUMN_NULLABLE);
        ForeignKey fk = ForeignKeys.create(orders, "FK_ORDERS_CUSTOMER", pk, customerId);
        View view = Views.create(sales, "V_CUSTOMER", "SELECT ID FROM SALES.CUSTOMER");

        assertThat(model.getOwnedElement()).containsExactly(shop);
        assertThat(varchar.getName()).isEqualTo("CHARACTER VARYING");
        assertThat(SQLSimpleTypes.ownedIn(shop, "CHARACTER VARYING")).isSameAs(varchar);
        assertThat(shop.getOwnedElement()).containsExactly(bigint, varchar, sales);
        assertThat(Schemas.tables(sales)).containsExactly(customer, orders);
        assertThat(customer.getFeature()).containsExactly(id, name);
        assertThat(id.getIsNullable()).isEqualTo(NullableType.COLUMN_NO_NULLS);
        assertThat(name.getLength()).isEqualTo(100);
        assertThat(name.eIsSet(name.eClass().getEStructuralFeature("precision"))).isFalse();
        assertThat(Tables.findPrimaryKey(customer)).contains(pk);
        assertThat(UniqueConstraints.columns(uq)).containsExactly(name);
        assertThat(fk.getUniqueKey()).isSameAs(pk);
        assertThat(pk.getKeyRelationship()).containsExactly(fk);
        assertThat(Views.queryBody(view)).contains("SELECT ID FROM SALES.CUSTOMER");
    }

    @Test
    void sqlSimpleTypes_unknownName_isRejected_vendorTypeByNumber() {
        Catalog c = Catalogs.create("C");
        assertThatIllegalArgumentException().isThrownBy(() -> SQLSimpleTypes.create(c, "HUGEINT"));
        SQLSimpleType huge = SQLSimpleTypes.create(c, "HUGEINT", Types.OTHER);
        assertThat(huge.getTypeNumber()).isEqualTo(Types.OTHER);
    }

    @Test
    void queryParameters_plainAndSql_areBothDeclared() {
        Model model = Models.create("m");
        Catalog shop = Catalogs.create(model, "SHOP");
        Schema sales = Schemas.create(shop, "SALES");
        DataType localDate = DataTypes.create(model, "java.time.LocalDate");
        SQLSimpleType date = SQLSimpleTypes.ownedIn(shop, "DATE");
        QueryColumnSet q = QueryColumnSets.create(sales, "ORDERS_SINCE",
                "SELECT ID FROM SALES.ORDERS WHERE ORDER_DATE >= :fromDate AND ORDER_DATE < :toDate");
        Parameter from = Parameters.create(q, "fromDate", localDate);
        SQLParameter to = SQLParameters.create(q, "toDate", date);

        assertThat(sales.getOwnedElement()).containsExactly(q);
        assertThat(QueryColumnSets.parametersOf(q)).containsExactly(from, to);
        assertThat(QueryColumnSets.isParameterized(q)).isTrue();
        assertThat(QueryColumnSets.checkConvention(q)).isEmpty();
    }
}
