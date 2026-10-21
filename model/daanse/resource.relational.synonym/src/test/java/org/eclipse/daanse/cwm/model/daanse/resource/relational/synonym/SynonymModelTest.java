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
 *   SmartCity Jena - initial
 */
package org.eclipse.daanse.cwm.model.daanse.resource.relational.synonym;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.synonym.util.Synonyms;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Checks the {@code synonym} model: conventions (hidden opposites,
 * {@code Property.oppositeRoleName} pairs), the synonym as an element of its schema,
 * the XMI round trip within the catalog and chain resolution via {@link Synonyms#resolveFinal}.
 */
class SynonymModelTest {

    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final SynonymFactory SF = SynonymFactory.eINSTANCE;
    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;

    @Test
    void packageDeclaresHiddenOppositesAndRoleNamePairs() throws Exception {
        // Check against the checked-in .ecore, not against the generated code.
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
        File ecore = new File(System.getProperty("user.dir"), "model/synonym.ecore");
        Resource res = rs.getResource(URI.createFileURI(ecore.getAbsolutePath()), true);
        EPackage pkg = (EPackage) res.getContents().get(0);

        EAnnotation ocl = pkg.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore/OCL");
        assertThat(ocl).isNotNull();
        assertThat(ocl.getDetails().get("hiddenOpposites")).isEqualTo("true");

        for (var classifier : pkg.getEClassifiers()) {
            if (!(classifier instanceof EClass eClass)) {
                continue;
            }
            for (EReference ref : eClass.getEReferences()) {
                assertThat(ref.isContainment()).as("%s.%s must not be a containment", eClass.getName(),
                        ref.getName()).isFalse();
                assertThat(ref.getEOpposite()).as("%s.%s must not have an eOpposite", eClass.getName(),
                        ref.getName()).isNull();
                EAnnotation emof = ref.getEAnnotation(EMOF);
                assertThat(emof).as("%s.%s needs Property.oppositeRoleName", eClass.getName(), ref.getName())
                        .isNotNull();
                String roleName = emof.getDetails().get("Property.oppositeRoleName");
                assertThat(roleName).isNotBlank();
                EAnnotation body = ref.getEAnnotation(EMOF + "#Property.oppositeRoleName");
                assertThat(body).isNotNull();
                assertThat(body.getDetails().get("body")).isEqualTo(roleName);
            }
        }
    }

    @Test
    void synonymIsOwnedBySchemaButNotAColumnSet() {
        Schema hr = schema(catalog(), "HR");
        Table employee = table(hr, "EMPLOYEE");
        Synonym emp = synonym(hr, "EMP", employee);

        assertThat(emp.getNamespace()).isSameAs(hr);
        assertThat(Synonyms.synonyms(hr)).containsExactly(emp);
        assertThat(Synonyms.find(hr, "EMP")).contains(emp);
        // Existing consumers see only tables/views — the synonym does not show up there.
        assertThat(Schemas.columnSets(hr)).containsExactly(employee);
        assertThat(emp.isIsPublic()).isFalse();
    }

    @Test
    void miniModelRoundTrips(@TempDir Path tmp) throws Exception {
        Catalog catalog = catalog();
        Schema hr = schema(catalog, "HR");
        Schema pub = schema(catalog, "PUBLIC");
        Table employee = table(hr, "EMPLOYEE");
        synonym(hr, "EMP", employee);
        Synonym publicEmp = synonym(pub, "EMPLOYEE", employee);
        publicEmp.setIsPublic(true);
        Synonym remote = SF.createSynonym();
        remote.setName("EMP_REMOTE");
        remote.setTargetSchemaName("HR");
        remote.setTargetName("EMPLOYEE");
        remote.setDbLink("HQ.EXAMPLE.COM");
        hr.getOwnedElement().add(remote);

        URI uri = URI.createFileURI(tmp.resolve("catalog.xmi").toString());
        Resource out = resourceSet().createResource(uri);
        out.getContents().add(catalog);
        out.save(Map.of());

        ResourceSet in = resourceSet();
        Resource loadedRes = in.getResource(uri, true);
        EcoreUtil.resolveAll(in);
        Map<EObject, ?> unresolved = EcoreUtil.UnresolvedProxyCrossReferencer.find(in);
        assertThat(unresolved).isEmpty();

        Catalog loaded = (Catalog) loadedRes.getContents().get(0);
        Schema loadedHr = (Schema) loaded.getOwnedElement().get(0);
        Schema loadedPub = (Schema) loaded.getOwnedElement().get(1);
        Table loadedEmployee = Schemas.findTable(loadedHr, "EMPLOYEE").orElseThrow();

        Synonym loadedEmp = Synonyms.find(loadedHr, "EMP").orElseThrow();
        assertThat(loadedEmp.getTarget()).isSameAs(loadedEmployee);
        assertThat(loadedEmp.getTargetSchemaName()).isEqualTo("HR");
        assertThat(loadedEmp.getTargetName()).isEqualTo("EMPLOYEE");
        assertThat(loadedEmp.getTargetObjectType()).isEqualTo("TABLE");

        Synonym loadedPublic = Synonyms.find(loadedPub, "EMPLOYEE").orElseThrow();
        assertThat(loadedPublic.isIsPublic()).isTrue();
        assertThat(loadedPublic.getTarget()).isSameAs(loadedEmployee);

        Synonym loadedRemote = Synonyms.find(loadedHr, "EMP_REMOTE").orElseThrow();
        assertThat(loadedRemote.getTarget()).isNull();
        assertThat(loadedRemote.getDbLink()).isEqualTo("HQ.EXAMPLE.COM");
    }

    @Test
    void resolveFinalFollowsChain() {
        Schema hr = schema(catalog(), "HR");
        Table employee = table(hr, "EMPLOYEE");
        Synonym first = synonym(hr, "EMP", employee);
        Synonym second = synonym(hr, "EMP2", first);
        Synonym third = synonym(hr, "EMP3", second);

        assertThat(Synonyms.resolveFinal(first)).contains(employee);
        assertThat(Synonyms.resolveFinal(third)).contains(employee);
    }

    @Test
    void resolveFinalReachesProcedure() {
        Schema hr = schema(catalog(), "HR");
        Procedure fn = RF.createProcedure();
        fn.setName("FN_ANSWER");
        hr.getOwnedElement().add(fn);

        assertThat(Synonyms.resolveFinal(synonym(hr, "ANSWER", fn))).contains(fn);
    }

    @Test
    void resolveFinalIsEmptyForUnresolvedTarget() {
        Schema hr = schema(catalog(), "HR");
        Synonym remote = synonym(hr, "EMP_REMOTE", null);
        Synonym viaRemote = synonym(hr, "EMP_VIA", remote);

        assertThat(Synonyms.resolveFinal(remote)).isEmpty();
        assertThat(Synonyms.resolveFinal(viaRemote)).isEmpty();
    }

    @Test
    void resolveFinalIsEmptyForCycle() {
        Schema hr = schema(catalog(), "HR");
        Synonym a = synonym(hr, "A", null);
        Synonym b = synonym(hr, "B", a);
        a.setTarget(b);

        assertThat(Synonyms.resolveFinal(a)).isEmpty();
        assertThat(Synonyms.resolveFinal(synonym(hr, "C", a))).isEmpty();
    }

    private static Catalog catalog() {
        Catalog catalog = RF.createCatalog();
        catalog.setName("HR_DB");
        return catalog;
    }

    private static Schema schema(Catalog catalog, String name) {
        Schema schema = RF.createSchema();
        schema.setName(name);
        catalog.getOwnedElement().add(schema);
        return schema;
    }

    private static Table table(Schema schema, String name) {
        Table table = RF.createTable();
        table.setName(name);
        schema.getOwnedElement().add(table);
        return table;
    }

    private static Synonym synonym(Schema schema, String name,
            org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement target) {
        Synonym synonym = SF.createSynonym();
        synonym.setName(name);
        synonym.setTarget(target);
        if (target != null) {
            Schema targetSchema = (Schema) target.getNamespace();
            synonym.setTargetSchemaName(targetSchema.getName());
            synonym.setTargetName(target.getName());
            synonym.setTargetObjectType(target instanceof Synonym ? "SYNONYM"
                    : target instanceof Table ? "TABLE" : "FUNCTION");
        } else {
            synonym.setTargetName("UNKNOWN");
        }
        schema.getOwnedElement().add(synonym);
        return synonym;
    }

    private static ResourceSet resourceSet() {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
        return rs;
    }
}
