<?xml version="1.0" encoding="ASCII"?>
<!--
/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
-->
<sqlselect:QueryExpression xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:sql="https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.sql" xmlns:sqlselect="https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.sql/select">
  <body xsi:type="sqlselect:QuerySpecification">
    <selectList xsi:type="sqlselect:DerivedColumn">
      <expression xsi:type="sql:ColumnReference">
        <qualifiedName>
          <parts>region</parts>
        </qualifiedName>
      </expression>
      <aliasDefinition/>
    </selectList>
    <selectList xsi:type="sqlselect:DerivedColumn">
      <expression xsi:type="sql:AggregateFunction" kind="COUNT_STAR"/>
      <aliasDefinition alias="n"/>
    </selectList>
    <from>
      <tableReferences xsi:type="sqlselect:UnresolvedTableReference">
        <qualifiedName>
          <parts>sales</parts>
        </qualifiedName>
      </tableReferences>
    </from>
    <where xsi:type="sql:ComparisonPredicate" operator="GREATER_THAN">
      <left xsi:type="sql:ColumnReference">
        <qualifiedName>
          <parts>amount</parts>
        </qualifiedName>
      </left>
      <right xsi:type="sql:NumericLiteral" value="0"/>
    </where>
    <groupBy>
      <elements xsi:type="sqlselect:OrdinaryGroupingSet">
        <elements xsi:type="sql:ColumnReference">
          <qualifiedName>
            <parts>region</parts>
          </qualifiedName>
        </elements>
      </elements>
    </groupBy>
  </body>
  <orderBy>
    <sortSpecifications ordering="DESC">
      <sortKey xsi:type="sqlselect:ProjectionReference" projection="//@body/@selectList.1"/>
    </sortSpecifications>
  </orderBy>
</sqlselect:QueryExpression>
