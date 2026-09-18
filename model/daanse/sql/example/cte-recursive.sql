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
<sqlselect:QueryExpression xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:sqlselect="https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.sql/select">
  <withClause recursive="true">
    <elements>
      <queryName alias="t"/>
      <query>
        <body xsi:type="sqlselect:QuerySpecification">
          <selectList xsi:type="sqlselect:Asterisk"/>
          <from>
            <tableReferences xsi:type="sqlselect:UnresolvedTableReference">
              <qualifiedName>
                <parts>base</parts>
              </qualifiedName>
            </tableReferences>
          </from>
        </body>
      </query>
    </elements>
  </withClause>
  <body xsi:type="sqlselect:QuerySpecification">
    <selectList xsi:type="sqlselect:Asterisk"/>
    <from>
      <tableReferences xsi:type="sqlselect:UnresolvedTableReference">
        <qualifiedName>
          <parts>t</parts>
        </qualifiedName>
      </tableReferences>
    </from>
  </body>
</sqlselect:QueryExpression>
