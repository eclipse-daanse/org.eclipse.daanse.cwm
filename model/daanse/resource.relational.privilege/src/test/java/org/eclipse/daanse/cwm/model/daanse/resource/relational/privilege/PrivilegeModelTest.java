/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeModelValidator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
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
 * Prüft das {@code privilege}-Modell: Konventionen (hidden opposites,
 * {@code Property.oppositeRoleName}-Paare), den Grantee als
 * {@code businessinformation::ResponsibleParty} (direkt, ohne eigene Rollenklasse und ohne
 * eigenen Wurzelcontainer — die Parties liegen per {@code ownedElement} in einem
 * {@code core::Package} oder im Catalog), den XMI-Rundlauf als Side-Car neben einer
 * Katalog-Resource, die Rückwärtsnavigation über {@code InverseReferences} und den
 * programmatischen Validator.
 */
class PrivilegeModelTest {

    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final PrivilegeFactory PF = PrivilegeFactory.eINSTANCE;
    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;
    private static final BusinessinformationFactory BF = BusinessinformationFactory.eINSTANCE;

    // ------------------------------------------------------------------
    // Konventionen
    // ------------------------------------------------------------------

    @Test
    void packageDeclaresHiddenOppositesAndRoleNamePairs() throws Exception {
        // Gegen die eingecheckte .ecore pruefen, nicht gegen den generierten Code.
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
        File ecore = new File(System.getProperty("user.dir"), "model/privilege.ecore");
        Resource res = rs.getResource(URI.createFileURI(ecore.getAbsolutePath()), true);
        EPackage pkg = (EPackage) res.getContents().get(0);

        EAnnotation ocl = pkg.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore/OCL");
        assertThat(ocl).isNotNull();
        assertThat(ocl.getDetails().get("hiddenOpposites")).isEqualTo("true");

        for (var classifier : pkg.getEClassifiers()) {
            if (!(classifier instanceof org.eclipse.emf.ecore.EClass eClass)) {
                continue;
            }
            for (EReference ref : eClass.getEReferences()) {
                assertThat(ref.isContainment()).as("%s.%s darf kein Containment sein", eClass.getName(),
                        ref.getName()).isFalse();
                assertThat(ref.getEOpposite()).as("%s.%s darf kein eOpposite haben", eClass.getName(),
                        ref.getName()).isNull();
                EAnnotation emof = ref.getEAnnotation(EMOF);
                assertThat(emof).as("%s.%s braucht Property.oppositeRoleName", eClass.getName(), ref.getName())
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
    void granteeIsAPlainResponsibleParty() {
        // Kein eigener Rollentyp und kein Wurzelcontainer: der Grantee ist direkt die
        // CWM ResponsibleParty, die als Namespace ihre Privilegien besitzt.
        ResponsibleParty grantee = responsibleParty("app_reader");
        TablePrivilege privilege = PF.createTablePrivilege();
        privilege.setAction(TablePrivilegeAction.SELECT);
        privilege.setTable(RF.createTable());
        grantee.getOwnedElement().add(privilege);

        assertThat(privilege.getNamespace()).isSameAs(grantee);
    }

    // ------------------------------------------------------------------
    // Rundlauf + Rückwärtsnavigation
    // ------------------------------------------------------------------

    @Test
    void miniModelRoundTripsAndBackNavigates(@TempDir Path tmp) throws Exception {
        // Katalogseite: HR.EMPLOYEE (Tabelle) + HR.FN_ANSWER (Funktion).
        Catalog catalog = RF.createCatalog();
        catalog.setName("HR_DB");
        Schema schema = RF.createSchema();
        schema.setName("HR");
        catalog.getOwnedElement().add(schema);
        Table employee = RF.createTable();
        employee.setName("EMPLOYEE");
        schema.getOwnedElement().add(employee);
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column email =
                RF.createColumn();
        email.setName("EMAIL");
        employee.getFeature().add(email);
        Procedure fnAnswer = RF.createProcedure();
        fnAnswer.setName("FN_ANSWER");
        schema.getOwnedElement().add(fnAnswer);

        // Privilegienseite ohne eigenen Wurzelcontainer: die Parties liegen per
        // ownedElement in einem gewoehnlichen core::Package (alternativ: im Catalog).
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package container =
                CoreFactory.eINSTANCE.createPackage();
        container.setName("HR_DB privileges");

        ResponsibleParty readonly = responsibleParty("readonly");
        ResponsibleParty admin = responsibleParty("admin");
        ResponsibleParty reporting = responsibleParty("reporting");
        container.getOwnedElement().add(readonly);
        container.getOwnedElement().add(admin);
        container.getOwnedElement().add(reporting);

        TablePrivilege select = PF.createTablePrivilege();
        select.setName("SELECT ON HR.EMPLOYEE");
        select.setAction(TablePrivilegeAction.SELECT);
        select.setTable(employee);
        select.setGrantable(Boolean.FALSE);
        select.setGrantor(admin);
        readonly.getOwnedElement().add(select);

        // Aktion implizit EXECUTE — die Klasse ist die Aktion.
        ProcedurePrivilege execute = PF.createProcedurePrivilege();
        execute.setName("EXECUTE ON HR.FN_ANSWER");
        execute.setProcedure(fnAnswer);
        readonly.getOwnedElement().add(execute);

        // Spalteneingeschraenktes Privileg: GRANT UPDATE (EMAIL) ON HR.EMPLOYEE.
        TablePrivilege updateEmail = PF.createTablePrivilege();
        updateEmail.setName("UPDATE (EMAIL) ON HR.EMPLOYEE");
        updateEmail.setAction(TablePrivilegeAction.UPDATE);
        updateEmail.setTable(employee);
        updateEmail.getColumn().add(email);
        readonly.getOwnedElement().add(updateEmail);

        // Rollenmitgliedschaft wie die anderen Grants: GRANT readonly TO reporting.
        RolePrivilege membership = PF.createRolePrivilege();
        membership.setName("MEMBER OF readonly");
        membership.setRole(readonly);
        membership.setGrantable(Boolean.FALSE);
        reporting.getOwnedElement().add(membership);

        assertThat(PrivilegeModelValidator.validate(container)).isEmpty();

        // Side-Car-Serialisierung: Katalog und Privilegien-Package als getrennte Ressourcen.
        ResourceSet out = resourceSet();
        Resource catalogRes = out.createResource(URI.createFileURI(tmp.resolve("catalog.xmi").toString()));
        catalogRes.getContents().add(catalog);
        Resource partiesRes = out.createResource(URI.createFileURI(tmp.resolve("hr.cwmprivilege").toString()));
        partiesRes.getContents().add(container);
        catalogRes.save(Map.of());
        partiesRes.save(Map.of());

        // Frisch laden und Proxys aufloesen.
        ResourceSet in = resourceSet();
        Resource loadedRes = in.getResource(URI.createFileURI(tmp.resolve("hr.cwmprivilege").toString()), true);
        EcoreUtil.resolveAll(in);
        Map<EObject, ?> unresolved = EcoreUtil.UnresolvedProxyCrossReferencer.find(in);
        assertThat(unresolved).isEmpty();

        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package loaded =
                (org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package) loadedRes.getContents().get(0);
        ResponsibleParty loadedReadonly = (ResponsibleParty) loaded.getOwnedElement().get(0);
        TablePrivilege loadedSelect = (TablePrivilege) loadedReadonly.getOwnedElement().get(0);
        Table loadedEmployee = (Table) loadedSelect.getTable();
        assertThat(loadedEmployee.getName()).isEqualTo("EMPLOYEE");
        assertThat(loadedSelect.getGrantor().getName()).isEqualTo("admin");

        // Hidden opposites: ohne Index bleibt die Rueckrichtung stumm leer ...
        assertThat(InverseReferences.isIndexed(loadedEmployee)).isFalse();
        // ... mit InverseReferences findet die Tabelle ihre Privilegien.
        InverseReferences.install(in);
        List<TablePrivilege> tablePrivileges = InverseReferences.referencingList(loadedEmployee,
                PrivilegePackage.eINSTANCE.getTablePrivilege_Table(), TablePrivilege.class);
        assertThat(tablePrivileges).hasSize(2);
        assertThat(tablePrivileges.get(0).getNamespace().getName()).isEqualTo("readonly");

        // Spalteneinschraenkung uebersteht den Rundlauf; von der Spalte zurueck zum Privileg.
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column loadedEmail =
                (org.eclipse.daanse.cwm.model.cwm.resource.relational.Column) loadedEmployee.getFeature().get(0);
        List<TablePrivilege> columnRestricted = InverseReferences.referencingList(loadedEmail,
                PrivilegePackage.eINSTANCE.getTablePrivilege_Column(), TablePrivilege.class);
        assertThat(columnRestricted).hasSize(1);
        assertThat(columnRestricted.get(0).getAction()).isEqualTo(TablePrivilegeAction.UPDATE);

        Procedure loadedFn = ((ProcedurePrivilege) loadedReadonly.getOwnedElement().get(1)).getProcedure();
        List<ProcedurePrivilege> procedurePrivileges = InverseReferences.referencingList(loadedFn,
                PrivilegePackage.eINSTANCE.getProcedurePrivilege_Procedure(), ProcedurePrivilege.class);
        assertThat(procedurePrivileges).hasSize(1);

        // Von der verliehenen Rolle zu ihren Mitgliedern (RolePrivilege-Kanten).
        List<RolePrivilege> memberships = InverseReferences.referencingList(loadedReadonly,
                PrivilegePackage.eINSTANCE.getRolePrivilege_Role(), RolePrivilege.class);
        assertThat(memberships).hasSize(1);
        assertThat(memberships.get(0).getNamespace().getName()).isEqualTo("reporting");
    }

    // ------------------------------------------------------------------
    // Validator
    // ------------------------------------------------------------------

    @Test
    void validatorFlagsForeignNamespaceSelfMembershipAndCycle() {
        ResponsibleParty a = responsibleParty("a");
        ResponsibleParty b = responsibleParty("b");

        // Zyklus: a erbt b, b erbt a.
        RolePrivilege aInheritsB = PF.createRolePrivilege();
        aInheritsB.setRole(b);
        a.getOwnedElement().add(aInheritsB);
        RolePrivilege bInheritsA = PF.createRolePrivilege();
        bInheritsA.setRole(a);
        b.getOwnedElement().add(bInheritsA);

        // Selbstmitgliedschaft.
        ResponsibleParty c = responsibleParty("c");
        RolePrivilege self = PF.createRolePrivilege();
        self.setRole(c);
        c.getOwnedElement().add(self);

        // Privileg ausserhalb einer ResponsibleParty: Grantee-Regel verletzt.
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package pkg =
                CoreFactory.eINSTANCE.createPackage();
        TablePrivilege stray = PF.createTablePrivilege();
        stray.setName("stray");
        stray.setAction(TablePrivilegeAction.SELECT);
        stray.setTable(RF.createTable());
        pkg.getOwnedElement().add(stray);

        // Spalte einer fremden Tabelle als Einschraenkung.
        ResponsibleParty d = responsibleParty("d");
        TablePrivilege foreignColumn = PF.createTablePrivilege();
        foreignColumn.setAction(TablePrivilegeAction.UPDATE);
        Table t1 = RF.createTable();
        t1.setName("t1");
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column other = RF.createColumn();
        other.setName("other");
        Table t2 = RF.createTable();
        t2.getFeature().add(other);
        foreignColumn.setTable(t1);
        foreignColumn.getColumn().add(other);
        d.getOwnedElement().add(foreignColumn);

        List<String> issues = PrivilegeModelValidator.validateAll(List.of(a, b, c, pkg, d));
        assertThat(issues).anySatisfy(i -> assertThat(i).contains("Zyklus"));
        assertThat(issues).anySatisfy(i -> assertThat(i).contains("selbst erben"));
        assertThat(issues).anySatisfy(i -> assertThat(i).contains("namespace muss eine ResponsibleParty"));
        assertThat(issues).anySatisfy(i -> assertThat(i).contains("gehoert nicht zur Zieltabelle"));
    }

    // ------------------------------------------------------------------
    // Hilfen
    // ------------------------------------------------------------------

    private static ResponsibleParty responsibleParty(String name) {
        ResponsibleParty party = BF.createResponsibleParty();
        party.setName(name);
        party.setResponsibility("database role");
        return party;
    }

    private static ResourceSet resourceSet() {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
        return rs;
    }
}
