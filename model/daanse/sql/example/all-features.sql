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
  <withClause>
    <elements>
      <queryName alias="recent"/>
      <query>
        <body xsi:type="sqlselect:QuerySpecification">
          <selectList xsi:type="sqlselect:Asterisk"/>
          <from>
            <tableReferences xsi:type="sqlselect:UnresolvedTableReference">
              <qualifiedName>
                <parts>orders</parts>
              </qualifiedName>
            </tableReferences>
          </from>
        </body>
      </query>
    </elements>
  </withClause>
  <body xsi:type="sqlselect:QuerySpecification">
    <selectList xsi:type="sqlselect:DerivedColumn">
      <expression xsi:type="sql:AggregateFunction" kind="COUNT_STAR"/>
      <aliasDefinition alias="cnt"/>
    </selectList>
    <selectList xsi:type="sqlselect:DerivedColumn">
      <expression xsi:type="sql:RoutineInvocation">
        <functionName>
          <parts>upper</parts>
        </functionName>
        <arguments xsi:type="sql:ColumnReference">
          <qualifiedName>
            <parts>c</parts>
            <parts>name</parts>
          </qualifiedName>
        </arguments>
      </expression>
      <aliasDefinition/>
    </selectList>
    <selectList xsi:type="sqlselect:DerivedColumn">
      <expression xsi:type="sql:CaseExpression">
        <whenClauses>
          <condition xsi:type="sql:ComparisonPredicate" operator="GREATER_THAN">
            <left xsi:type="sql:ColumnReference">
              <qualifiedName>
                <parts>o</parts>
                <parts>amount</parts>
              </qualifiedName>
            </left>
            <right xsi:type="sql:NumericLiteral" value="100"/>
          </condition>
          <result xsi:type="sql:CharacterStringLiteral" value="big"/>
        </whenClauses>
        <elseResult xsi:type="sql:CharacterStringLiteral" value="small"/>
      </expression>
      <aliasDefinition alias="bucket"/>
    </selectList>
    <selectList xsi:type="sqlselect:DerivedColumn">
      <expression xsi:type="sql:CastSpecification">
        <operand xsi:type="sql:ColumnReference">
          <qualifiedName>
            <parts>o</parts>
            <parts>amount</parts>
          </qualifiedName>
        </operand>
        <targetType kind="INTEGER"/>
      </expression>
      <aliasDefinition alias="amount_i"/>
    </selectList>
    <selectList xsi:type="sqlselect:DerivedColumn">
      <expression xsi:type="sql:WindowFunction">
        <window>
          <partitionBy xsi:type="sql:ColumnReference">
            <qualifiedName>
              <parts>c</parts>
              <parts>region</parts>
            </qualifiedName>
          </partitionBy>
          <orderBy ordering="ASC">
            <sortKey xsi:type="sql:ColumnReference">
              <qualifiedName>
                <parts>o</parts>
                <parts>ts</parts>
              </qualifiedName>
            </sortKey>
          </orderBy>
        </window>
      </expression>
      <aliasDefinition alias="rn"/>
    </selectList>
    <selectList xsi:type="sqlselect:DerivedColumn">
      <expression xsi:type="sql:ScalarSubquery">
        <query>
          <body xsi:type="sqlselect:QuerySpecification">
            <selectList xsi:type="sqlselect:DerivedColumn">
              <expression xsi:type="sql:AggregateFunction" kind="MAX">
                <arguments xsi:type="sql:ColumnReference">
                  <qualifiedName>
                    <parts>amount</parts>
                  </qualifiedName>
                </arguments>
              </expression>
              <aliasDefinition/>
            </selectList>
            <from>
              <tableReferences xsi:type="sqlselect:UnresolvedTableReference">
                <qualifiedName>
                  <parts>orders</parts>
                </qualifiedName>
              </tableReferences>
            </from>
          </body>
        </query>
      </expression>
      <aliasDefinition alias="max_amount"/>
    </selectList>
    <from>
      <tableReferences xsi:type="sqlselect:Join">
        <left xsi:type="sqlselect:UnresolvedTableReference">
          <qualifiedName>
            <parts>orders</parts>
          </qualifiedName>
          <aliasDefinition alias="o"/>
        </left>
        <right xsi:type="sqlselect:DerivedTable">
          <query>
            <body xsi:type="sqlselect:QuerySpecification">
              <selectList xsi:type="sqlselect:DerivedColumn">
                <expression xsi:type="sql:ColumnReference">
                  <qualifiedName>
                    <parts>oid</parts>
                  </qualifiedName>
                </expression>
                <aliasDefinition/>
              </selectList>
              <from>
                <tableReferences xsi:type="sqlselect:UnresolvedTableReference">
                  <qualifiedName>
                    <parts>lines</parts>
                  </qualifiedName>
                </tableReferences>
              </from>
            </body>
          </query>
          <aliasDefinition alias="d"/>
        </right>
        <condition xsi:type="sql:ComparisonPredicate">
          <left xsi:type="sql:ColumnReference">
            <qualifiedName>
              <parts>o</parts>
              <parts>id</parts>
            </qualifiedName>
          </left>
          <right xsi:type="sql:ColumnReference">
            <qualifiedName>
              <parts>d</parts>
              <parts>oid</parts>
            </qualifiedName>
          </right>
        </condition>
      </tableReferences>
    </from>
    <where xsi:type="sql:BinaryLogicalExpression">
      <left xsi:type="sql:ComparisonPredicate" operator="GREATER_THAN">
        <left xsi:type="sql:ColumnReference">
          <qualifiedName>
            <parts>o</parts>
            <parts>amount</parts>
          </qualifiedName>
        </left>
        <right xsi:type="sql:NumericLiteral" value="100"/>
      </left>
      <right xsi:type="sql:InPredicate">
        <value xsi:type="sql:ColumnReference">
          <qualifiedName>
            <parts>o</parts>
            <parts>id</parts>
          </qualifiedName>
        </value>
        <subquery>
          <body xsi:type="sqlselect:QuerySpecification">
            <selectList xsi:type="sqlselect:DerivedColumn">
              <expression xsi:type="sql:ColumnReference">
                <qualifiedName>
                  <parts>id</parts>
                </qualifiedName>
              </expression>
              <aliasDefinition/>
            </selectList>
            <from>
              <tableReferences xsi:type="sqlselect:UnresolvedTableReference">
                <qualifiedName>
                  <parts>vip</parts>
                </qualifiedName>
              </tableReferences>
            </from>
          </body>
        </subquery>
      </right>
    </where>
    <groupBy>
      <elements xsi:type="sqlselect:OrdinaryGroupingSet">
        <elements xsi:type="sql:ColumnReference">
          <qualifiedName>
            <parts>c</parts>
            <parts>region</parts>
          </qualifiedName>
        </elements>
      </elements>
    </groupBy>
    <having xsi:type="sql:ComparisonPredicate" operator="GREATER_THAN">
      <left xsi:type="sql:AggregateFunction" kind="COUNT_STAR"/>
      <right xsi:type="sql:NumericLiteral" value="1"/>
    </having>
  </body>
  <orderBy>
    <sortSpecifications ordering="DESC">
      <sortKey xsi:type="sqlselect:ProjectionReference" projection="//@body/@selectList.0"/>
    </sortSpecifications>
    <offsetFetch offsetRows="ROWS">
      <offset xsi:type="sql:NumericLiteral" value="0"/>
      <fetchFirst xsi:type="sql:NumericLiteral" value="10"/>
    </offsetFetch>
  </orderBy>
</sqlselect:QueryExpression>
