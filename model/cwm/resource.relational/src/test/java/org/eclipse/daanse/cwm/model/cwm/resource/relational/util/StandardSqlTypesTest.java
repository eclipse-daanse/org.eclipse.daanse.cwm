/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.cwm.resource.relational.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.emf.standard.StandardSets;
import org.eclipse.daanse.cwm.model.cwm.foundation.typemapping.TypeSystem;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The SQL types as a standard set: the checked-in file is what the code writes, every element has a UUID of its own,
 * the set keeps the rules of the CWM model, and the columns of a model file refer to its types by URI and UUID.
 */
class StandardSqlTypesTest {

    private static final Path DIR = Path.of(System.getProperty("user.dir"),
            "src/main/resources/org/eclipse/daanse/cwm/model/cwm/resource/relational/util");

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    /** Run with -Ddaanse.standard.write=true to write the file anew after a change to the code. */
    @Test
    void theCheckedInFileIsWhatTheCodeWrites() {
        assertThat(StandardSets.upToDate(StandardSqlTypes.standardSet(), DIR.resolve(StandardSqlTypes.SET_FILE)))
                .as(StandardSqlTypes.SET_FILE + " is what the code writes; run the test with -D" + StandardSets.WRITE
                        + "=true to write it again")
                .isTrue();
    }

    @Test
    void everyElementHasAUuidOfItsOwn() {
        Resource set = StandardSqlTypes.load(new ResourceSetImpl()).eResource();
        List<String> ids = new ArrayList<>();
        set.getAllContents().forEachRemaining(e -> ids.add(set.getURIFragment(e)));
        assertThat(ids).as("the type system and its 24 types").hasSize(25).doesNotHaveDuplicates()
                .allMatch(StandardSets.SHORT_UUID.asMatchPredicate());
    }

    @Test
    void theFileInTheJarHoldsTheTypesOfTheStandardWithTheirNumbers() {
        TypeSystem sql = StandardSqlTypes.load(new ResourceSetImpl());
        Resource set = sql.eResource();
        assertThat(set.getURI().toString()).as("the URI of the set")
                .isEqualTo("https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.cwm.resource.relational/sql-types.xmi");
        assertThat(sql.getName()).as("the type system").isEqualTo("SQL");
        assertThat(sql.getOwnedElement()).as("the types with their numbers").extracting(ModelElement::getName,
                e -> ((SQLSimpleType) e).getTypeNumber()).containsExactly(
                        tuple("BIT", (long) Types.BIT), tuple("BIT VARYING", (long) Types.VARBINARY),
                        tuple("BINARY LARGE OBJECT", (long) Types.BLOB), tuple("CHARACTER", (long) Types.CHAR),
                        tuple("CHARACTER VARYING", (long) Types.VARCHAR),
                        tuple("CHARACTER LARGE OBJECT", (long) Types.CLOB),
                        tuple("NATIONAL CHARACTER", (long) Types.NCHAR),
                        tuple("NATIONAL CHARACTER VARYING", (long) Types.NVARCHAR),
                        tuple("NATIONAL CHARACTER LARGE OBJECT", (long) Types.NCLOB),
                        tuple("NUMERIC", (long) Types.NUMERIC), tuple("DECIMAL", (long) Types.DECIMAL),
                        tuple("INTEGER", (long) Types.INTEGER), tuple("SMALLINT", (long) Types.SMALLINT),
                        tuple("FLOAT", (long) Types.FLOAT), tuple("REAL", (long) Types.REAL),
                        tuple("DOUBLE PRECISION", (long) Types.DOUBLE), tuple("BOOLEAN", (long) Types.BOOLEAN),
                        tuple("DATE", (long) Types.DATE), tuple("TIME", (long) Types.TIME),
                        tuple("TIME WITH TIMEZONE", (long) Types.TIME_WITH_TIMEZONE),
                        tuple("TIMESTAMP", (long) Types.TIMESTAMP),
                        tuple("TIMESTAMP WITH TIMEZONE", (long) Types.TIMESTAMP_WITH_TIMEZONE),
                        tuple("INTERVAL", (long) Types.OTHER), tuple("BIGINT", (long) Types.BIGINT));
        assertThat(StandardSqlTypes.type(sql, "varchar").orElseThrow().getName()).as("an alias")
                .isEqualTo("CHARACTER VARYING");
        assertThat(StandardSqlTypes.type(sql, "TINYINT")).as("no type of one product").isEmpty();
        assertThat(StandardSqlTypes.type(sql, "DECIMAL").orElseThrow().getNumericPrecisionRadix())
                .as("a decimal radix").isEqualTo(10);
    }

    @Test
    void theSetKeepsTheRulesOfTheModel() {
        TypeSystem sql = StandardSqlTypes.load(new ResourceSetImpl());
        assertThat(Diagnostician.INSTANCE.validate(sql).getChildren()).as("findings of the EMF check, OCL included")
                .isEmpty();
    }

    /**
     * The columns of a catalog refer to two types of the set as {@code href="<uri>#<uuid>"}; read in a fresh resource
     * set where the set is only mapped, the columns reach the types of the set in the jar.
     */
    @Test
    void aCatalogRefersToTwoTypesOfTheSetByUriAndUuid(@TempDir Path dir) throws Exception {
        ResourceSet writing = new ResourceSetImpl();
        TypeSystem sql = StandardSqlTypes.load(writing);
        Path file = dir.resolve("harbour.xmi");
        Resource model = new XMIResourceImpl(URI.createFileURI(file.toString()));
        writing.getResources().add(model);
        Catalog harbour = Catalogs.create("harbour");
        model.getContents().add(harbour);
        Table berths = Tables.create(Schemas.create(harbour, "port"), "berths");
        SQLSimpleType integer = StandardSqlTypes.type(sql, "INTEGER").orElseThrow();
        SQLSimpleType varchar = StandardSqlTypes.type(sql, "VARCHAR").orElseThrow();
        Columns.create(berths, "id", integer);
        Columns.create(berths, "quay", varchar);
        model.save(Map.of());
        String integerId = sql.eResource().getURIFragment(integer);
        String varcharId = sql.eResource().getURIFragment(varchar);
        assertThat(Files.readString(file)).as("the references by the URI of the set and the UUIDs").contains(
                "href=\"" + StandardSqlTypes.SET_URI + "#" + integerId + "\"",
                "href=\"" + StandardSqlTypes.SET_URI + "#" + varcharId + "\"");

        ResourceSet reading = new ResourceSetImpl();
        StandardSqlTypes.map(reading);
        Resource again = new XMIResourceImpl(URI.createFileURI(file.toString()));
        reading.getResources().add(again);
        again.load(Map.of());
        Schema port = (Schema) ((Catalog) again.getContents().get(0)).getOwnedElement().get(0);
        Table read = (Table) port.getOwnedElement().get(0);
        Resource set = reading.getResource(URI.createURI(StandardSqlTypes.SET_URI), false);
        assertThat(((Column) read.getFeature().get(0)).getType()).as("id is an INTEGER of the set")
                .isSameAs(set.getEObject(integerId));
        assertThat(((Column) read.getFeature().get(1)).getType()).as("quay is a CHARACTER VARYING of the set")
                .isSameAs(set.getEObject(varcharId));
    }
}
