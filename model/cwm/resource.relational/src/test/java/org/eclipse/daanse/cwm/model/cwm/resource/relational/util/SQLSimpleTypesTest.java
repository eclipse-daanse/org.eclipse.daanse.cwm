/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.model.cwm.resource.relational.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.junit.jupiter.api.Test;

class SQLSimpleTypesTest {

    @Test
    void sql99Defaults_haveNameAndTypeNumber() {
        SQLSimpleType t = SQLSimpleTypes.Sql99.integerType();
        assertThat(t.getName()).isEqualTo("INTEGER");
        assertThat(t.getTypeNumber()).isEqualTo(Types.INTEGER);
        assertThat(t.getNumericScale()).isEqualTo(0);
    }

    @Test
    void sql99_allCategories() {
        assertThat(SQLSimpleTypes.Sql99.bitType().getTypeNumber()).isEqualTo(Types.BIT);
        assertThat(SQLSimpleTypes.Sql99.characterVaryingType().getTypeNumber()).isEqualTo(Types.VARCHAR);
        assertThat(SQLSimpleTypes.Sql99.numericType().getNumericPrecisionRadix()).isEqualTo(10);
        assertThat(SQLSimpleTypes.Sql99.floatType().getNumericPrecisionRadix()).isEqualTo(2);
        assertThat(SQLSimpleTypes.Sql99.smallintType().getNumericScale()).isEqualTo(0);
        assertThat(SQLSimpleTypes.Sql99.dateType().getTypeNumber()).isEqualTo(Types.DATE);
        assertThat(SQLSimpleTypes.Sql99.booleanType().getTypeNumber()).isEqualTo(Types.BOOLEAN);
    }

    @Test
    void varchar_parametrized_setsLength() {
        SQLSimpleType t = SQLSimpleTypes.varcharType(128);
        assertThat(t.getName()).isEqualTo("CHARACTER VARYING");
        assertThat(t.getTypeNumber()).isEqualTo(Types.VARCHAR);
        assertThat(t.getCharacterMaximumLength()).isEqualTo(128);
    }

    @Test
    void decimal_parametrized_setsPrecisionAndScale() {
        SQLSimpleType t = SQLSimpleTypes.decimalType(10, 2);
        assertThat(t.getName()).isEqualTo("DECIMAL");
        assertThat(t.getTypeNumber()).isEqualTo(Types.DECIMAL);
        assertThat(t.getNumericPrecision()).isEqualTo(10);
        assertThat(t.getNumericScale()).isEqualTo(2);
        assertThat(t.getNumericPrecisionRadix()).isEqualTo(10);
    }

    @Test
    void factories_returnFreshInstances() {
        SQLSimpleType a = SQLSimpleTypes.varcharType(10);
        SQLSimpleType b = SQLSimpleTypes.varcharType(20);
        assertThat(a).isNotSameAs(b);
        assertThat(a.getCharacterMaximumLength()).isEqualTo(10);
        assertThat(b.getCharacterMaximumLength()).isEqualTo(20);
    }

    @Test
    void byName_canonicalAndAliases() {
        assertThat(SQLSimpleTypes.byName("INTEGER")).isPresent();
        assertThat(SQLSimpleTypes.byName("integer")).isPresent();
        assertThat(SQLSimpleTypes.byName("VARCHAR"))
                .hasValueSatisfying(t -> assertThat(t.getTypeNumber()).isEqualTo(Types.VARCHAR));
        assertThat(SQLSimpleTypes.byName("CHARACTER  VARYING")).isPresent();
        assertThat(SQLSimpleTypes.byName("INT")).isPresent();
        assertThat(SQLSimpleTypes.byName("BIGINT"))
                .hasValueSatisfying(t -> assertThat(t.getTypeNumber()).isEqualTo(Types.BIGINT));
        assertThat(SQLSimpleTypes.byName("FROBNICATE")).isEmpty();
        assertThat(SQLSimpleTypes.byName(null)).isEmpty();
    }

    @Test
    void inspectionClassification() {
        assertThat(SQLSimpleTypes.isNumeric(SQLSimpleTypes.Sql99.integerType())).isTrue();
        assertThat(SQLSimpleTypes.isNumeric(SQLSimpleTypes.decimalType(10, 2))).isTrue();
        assertThat(SQLSimpleTypes.isNumeric(SQLSimpleTypes.varcharType(10))).isFalse();

        assertThat(SQLSimpleTypes.isText(SQLSimpleTypes.varcharType(255))).isTrue();
        assertThat(SQLSimpleTypes.isText(SQLSimpleTypes.characterType(10))).isTrue();
        assertThat(SQLSimpleTypes.isText(SQLSimpleTypes.Sql99.integerType())).isFalse();

        assertThat(SQLSimpleTypes.isTemporal(SQLSimpleTypes.Sql99.dateType())).isTrue();
        assertThat(SQLSimpleTypes.isTemporal(SQLSimpleTypes.Sql99.timestampType())).isTrue();
        assertThat(SQLSimpleTypes.isTemporal(SQLSimpleTypes.Sql99.integerType())).isFalse();
    }

    @Test
    void describe_formats() {
        assertThat(SQLSimpleTypes.describe(SQLSimpleTypes.varcharType(255))).isEqualTo("CHARACTER VARYING(255)");
        assertThat(SQLSimpleTypes.describe(SQLSimpleTypes.decimalType(10, 2))).isEqualTo("DECIMAL(10,2)");
        assertThat(SQLSimpleTypes.describe(SQLSimpleTypes.Sql99.integerType())).isEqualTo("INTEGER");
        assertThat(SQLSimpleTypes.describe(null)).isNull();
    }
}
