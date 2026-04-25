/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.util.resource.relational;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.junit.jupiter.api.Test;

class SqlSimpleTypesTest {

    @Test
    void sql99Defaults_haveNameAndTypeNumber() {
        SQLSimpleType t = SqlSimpleTypes.Sql99.integerType();
        assertThat(t.getName()).isEqualTo("INTEGER");
        assertThat(t.getTypeNumber()).isEqualTo(Types.INTEGER);
        assertThat(t.getNumericScale()).isEqualTo(0);
    }

    @Test
    void sql99_allCategories() {
        assertThat(SqlSimpleTypes.Sql99.bitType().getTypeNumber()).isEqualTo(Types.BIT);
        assertThat(SqlSimpleTypes.Sql99.characterVaryingType().getTypeNumber()).isEqualTo(Types.VARCHAR);
        assertThat(SqlSimpleTypes.Sql99.numericType().getNumericPrecisionRadix()).isEqualTo(10);
        assertThat(SqlSimpleTypes.Sql99.floatType().getNumericPrecisionRadix()).isEqualTo(2);
        assertThat(SqlSimpleTypes.Sql99.smallintType().getNumericScale()).isEqualTo(0);
        assertThat(SqlSimpleTypes.Sql99.dateType().getTypeNumber()).isEqualTo(Types.DATE);
        assertThat(SqlSimpleTypes.Sql99.booleanType().getTypeNumber()).isEqualTo(Types.BOOLEAN);
    }

    @Test
    void varchar_parametrized_setsLength() {
        SQLSimpleType t = SqlSimpleTypes.varcharType(128);
        assertThat(t.getName()).isEqualTo("CHARACTER VARYING");
        assertThat(t.getTypeNumber()).isEqualTo(Types.VARCHAR);
        assertThat(t.getCharacterMaximumLength()).isEqualTo(128);
    }

    @Test
    void decimal_parametrized_setsPrecisionAndScale() {
        SQLSimpleType t = SqlSimpleTypes.decimalType(10, 2);
        assertThat(t.getName()).isEqualTo("DECIMAL");
        assertThat(t.getTypeNumber()).isEqualTo(Types.DECIMAL);
        assertThat(t.getNumericPrecision()).isEqualTo(10);
        assertThat(t.getNumericScale()).isEqualTo(2);
        assertThat(t.getNumericPrecisionRadix()).isEqualTo(10);
    }

    @Test
    void factories_returnFreshInstances() {
        SQLSimpleType a = SqlSimpleTypes.varcharType(10);
        SQLSimpleType b = SqlSimpleTypes.varcharType(20);
        assertThat(a).isNotSameAs(b);
        assertThat(a.getCharacterMaximumLength()).isEqualTo(10);
        assertThat(b.getCharacterMaximumLength()).isEqualTo(20);
    }

    @Test
    void byName_canonicalAndAliases() {
        assertThat(SqlSimpleTypes.byName("INTEGER")).isPresent();
        assertThat(SqlSimpleTypes.byName("integer")).isPresent();
        assertThat(SqlSimpleTypes.byName("VARCHAR"))
                .hasValueSatisfying(t -> assertThat(t.getTypeNumber()).isEqualTo(Types.VARCHAR));
        assertThat(SqlSimpleTypes.byName("CHARACTER  VARYING")).isPresent();
        assertThat(SqlSimpleTypes.byName("INT")).isPresent();
        assertThat(SqlSimpleTypes.byName("BIGINT"))
                .hasValueSatisfying(t -> assertThat(t.getTypeNumber()).isEqualTo(Types.BIGINT));
        assertThat(SqlSimpleTypes.byName("FROBNICATE")).isEmpty();
        assertThat(SqlSimpleTypes.byName(null)).isEmpty();
    }

    @Test
    void inspectionClassification() {
        assertThat(SqlSimpleTypes.isNumeric(SqlSimpleTypes.Sql99.integerType())).isTrue();
        assertThat(SqlSimpleTypes.isNumeric(SqlSimpleTypes.decimalType(10, 2))).isTrue();
        assertThat(SqlSimpleTypes.isNumeric(SqlSimpleTypes.varcharType(10))).isFalse();

        assertThat(SqlSimpleTypes.isText(SqlSimpleTypes.varcharType(255))).isTrue();
        assertThat(SqlSimpleTypes.isText(SqlSimpleTypes.characterType(10))).isTrue();
        assertThat(SqlSimpleTypes.isText(SqlSimpleTypes.Sql99.integerType())).isFalse();

        assertThat(SqlSimpleTypes.isTemporal(SqlSimpleTypes.Sql99.dateType())).isTrue();
        assertThat(SqlSimpleTypes.isTemporal(SqlSimpleTypes.Sql99.timestampType())).isTrue();
        assertThat(SqlSimpleTypes.isTemporal(SqlSimpleTypes.Sql99.integerType())).isFalse();
    }

    @Test
    void describe_formats() {
        assertThat(SqlSimpleTypes.describe(SqlSimpleTypes.varcharType(255))).isEqualTo("CHARACTER VARYING(255)");
        assertThat(SqlSimpleTypes.describe(SqlSimpleTypes.decimalType(10, 2))).isEqualTo("DECIMAL(10,2)");
        assertThat(SqlSimpleTypes.describe(SqlSimpleTypes.Sql99.integerType())).isEqualTo("INTEGER");
        assertThat(SqlSimpleTypes.describe(null)).isNull();
    }
}
