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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.QueryColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalPackage;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLParameter;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.junit.jupiter.api.Test;

class QueryColumnSetsTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;

    @Test
    void create_setsSqlQueryExpression() {
        QueryColumnSet qcs = QueryColumnSets.create("q", "select 1");
        assertThat(qcs.getName()).isEqualTo("q");
        assertThat(qcs.getQuery().getLanguage()).isEqualTo("SQL");
        assertThat(qcs.getQuery().getBody()).isEqualTo("select 1");
        assertThat(QueryColumnSets.queryBody(qcs)).contains("select 1");
        assertThat(QueryColumnSets.isParameterized(qcs)).isFalse();
    }

    @Test
    void parameters_keepDeclarationOrder() {
        QueryColumnSet qcs = QueryColumnSets.create("q", "select 1 where a = ? and b = ? and c = ?");
        QueryColumnSets.addParameter(qcs, QueryColumnSets.inParameter("first", SQLSimpleTypes.Sql99.integerType()));
        QueryColumnSets.addParameter(qcs, QueryColumnSets.inParameter("second", SQLSimpleTypes.varcharType(10)));
        QueryColumnSets.addParameter(qcs, QueryColumnSets.inParameter("third", SQLSimpleTypes.Sql99.dateType()));

        List<SQLParameter> params = QueryColumnSets.parametersOf(qcs);
        assertThat(params).extracting(SQLParameter::getName).containsExactly("first", "second", "third");
        assertThat(params).allMatch(p -> p.getKind() == ParameterDirectionKind.PDK_IN);
        assertThat(QueryColumnSets.isParameterized(qcs)).isTrue();
        assertThat(QueryColumnSets.checkConvention(qcs)).isEmpty();
    }

    @Test
    void checkConvention_flagsDrift() {
        QueryColumnSet qcs = QueryColumnSets.create("q", "select 1");
        SQLParameter unnamedUntyped = RF.createSQLParameter();
        QueryColumnSets.addParameter(qcs, unnamedUntyped);
        QueryColumnSets.addParameter(qcs, QueryColumnSets.inParameter("dup", SQLSimpleTypes.Sql99.integerType()));
        QueryColumnSets.addParameter(qcs, QueryColumnSets.inParameter("dup", SQLSimpleTypes.Sql99.integerType()));
        SQLParameter out = QueryColumnSets.inParameter("outish", SQLSimpleTypes.Sql99.integerType());
        out.setKind(ParameterDirectionKind.PDK_OUT);
        QueryColumnSets.addParameter(qcs, out);

        List<String> warnings = QueryColumnSets.checkConvention(qcs);
        assertThat(warnings).anyMatch(w -> w.contains("no type"));
        assertThat(warnings).anyMatch(w -> w.contains("duplicate parameter name"));
        assertThat(warnings).anyMatch(w -> w.contains("PDK_IN"));
        assertThat(warnings).anyMatch(w -> w.contains("mix of named and unnamed"));
    }

    /**
     * Pins the ordering convention: {@code ownedElement} is unordered per spec,
     * but the parameter order must survive an XMI round-trip.
     */
    @Test
    void parameterOrder_survivesXmiRoundTrip() throws IOException {
        Schema schema = RF.createSchema();
        schema.setName("sales");
        // parameter types are non-containment references — own them in the schema
        SQLSimpleType intType = SQLSimpleTypes.Sql99.integerType();
        SQLSimpleType varcharType = SQLSimpleTypes.varcharType(50);
        SQLSimpleType dateType = SQLSimpleTypes.Sql99.dateType();
        schema.getOwnedElement().add(intType);
        schema.getOwnedElement().add(varcharType);
        schema.getOwnedElement().add(dateType);

        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package lib = CoreFactory.eINSTANCE.createPackage();
        lib.setName("often used queries");
        schema.getOwnedElement().add(lib);

        QueryColumnSet qcs = QueryColumnSets.create("topCustomers",
                "select name from customer where region = ? and since > ? and tier = ?");
        QueryColumnSets.addParameter(qcs, QueryColumnSets.inParameter("region", varcharType));
        QueryColumnSets.addParameter(qcs, QueryColumnSets.inParameter("since", dateType));
        QueryColumnSets.addParameter(qcs, QueryColumnSets.inParameter("tier", intType));
        lib.getOwnedElement().add(qcs);

        RelationalPackage.eINSTANCE.eClass(); // ensure global package registration
        Resource out = new XMIResourceImpl(URI.createURI("mem:/queries.xmi"));
        out.getContents().add(schema);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        out.save(bytes, Map.of());

        Resource in = new XMIResourceImpl(URI.createURI("mem:/queries.xmi"));
        in.load(new ByteArrayInputStream(bytes.toByteArray()), Map.of());
        Schema loaded = (Schema) in.getContents().get(0);

        List<QueryColumnSet> found = Schemas.queryColumnSetsDeep(loaded);
        assertThat(found).hasSize(1);
        List<SQLParameter> params = QueryColumnSets.parametersOf(found.get(0));
        assertThat(params).extracting(SQLParameter::getName).containsExactly("region", "since", "tier");
        assertThat(params.get(1).getType()).isInstanceOf(SQLSimpleType.class);
        assertThat(((SQLSimpleType) params.get(2).getType()).getTypeNumber())
                .isEqualTo(intType.getTypeNumber());
        assertThat(found.get(0).getQuery().getBody()).contains("region = ?");
    }
}
