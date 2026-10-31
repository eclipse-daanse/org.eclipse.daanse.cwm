/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.orm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.BehavioralPackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.RelationshipsFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.RelationshipsPackage;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalPackage;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.daanse.orm.util.OrmBindings;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Structural checks for the plain CWM ORM mapping model: the cwm* references are the
 * single truth, nothing that follows from them is a feature, every topic has one place, no
 * XML heritage, no derivation OCL, ModelElement anchoring.
 */
class OrmModelTest {

    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final String EMOF_ROLE = EMOF + "#Property.oppositeRoleName";

    @Test
    void packageDeclaresHiddenOpposites() {
        EAnnotation ocl = CWMORMPackage.eINSTANCE.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore/OCL");
        assertThat(ocl).isNotNull();
        assertThat(ocl.getDetails().get("hiddenOpposites")).isEqualTo("true");
    }

    @Test
    void everyNonContainmentReferenceCarriesOppositeRoleNamePairAndNoOpposite() {
        for (EClassifier c : CWMORMPackage.eINSTANCE.getEClassifiers()) {
            if (!(c instanceof EClass eClass)) {
                continue;
            }
            for (EReference ref : eClass.getEReferences()) {
                if (ref.isContainment()) {
                    continue;
                }
                EAnnotation a = ref.getEAnnotation(EMOF);
                EAnnotation b = ref.getEAnnotation(EMOF_ROLE);
                assertThat(a).as("%s.%s misses emof annotation", eClass.getName(), ref.getName()).isNotNull();
                assertThat(b).as("%s.%s misses body annotation", eClass.getName(), ref.getName()).isNotNull();
                String role = a.getDetails().get("Property.oppositeRoleName");
                assertThat(role).isNotBlank();
                assertThat(b.getDetails().get("body")).isEqualTo(role);
                assertThat(ref.getEOpposite()).as("%s.%s must not have an eOpposite", eClass.getName(), ref.getName())
                        .isNull();
            }
        }
    }

    @Test
    void oppositeRoleNamesUseOrmPrefix() {
        for (EClassifier c : CWMORMPackage.eINSTANCE.getEClassifiers()) {
            if (!(c instanceof EClass eClass)) {
                continue;
            }
            for (EReference ref : eClass.getEReferences()) {
                EAnnotation a = ref.getEAnnotation(EMOF);
                if (a == null) {
                    continue;
                }
                String role = a.getDetails().get("Property.oppositeRoleName");
                assertThat(role).as("%s.%s role '%s'", eClass.getName(), ref.getName(), role)
                        .startsWith("orm").doesNotStartWith("eorm");
            }
        }
    }

    @Test
    void noReferenceTargetsTheEcoreMetamodel() {
        for (EClassifier c : CWMORMPackage.eINSTANCE.getEClassifiers()) {
            if (!(c instanceof EClass eClass)) {
                continue;
            }
            for (EReference ref : eClass.getEReferences()) {
                assertThat(ref.getEReferenceType().getEPackage())
                        .as("%s.%s still targets Ecore", eClass.getName(), ref.getName())
                        .isNotEqualTo(EcorePackage.eINSTANCE);
            }
        }
    }

    @Test
    void noExtendedMetaData() {
        assertNoAnnotation(CWMORMPackage.eINSTANCE, "http:///org/eclipse/emf/ecore/util/ExtendedMetaData");
    }

    @Test
    void noDerivationAnnotationsAndNoOcl() {
        assertNoAnnotation(CWMORMPackage.eINSTANCE, "http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot");
        EAnnotation delegates = CWMORMPackage.eINSTANCE.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore");
        assertThat(delegates).as("no OCL delegate annotation on the package").isNull();
        for (EClassifier c : CWMORMPackage.eINSTANCE.getEClassifiers()) {
            if (!(c instanceof EClass eClass)) {
                continue;
            }
            EAnnotation constraints = eClass.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore");
            assertThat(constraints).as("%s declares constraints", eClass.getName()).isNull();
            for (EStructuralFeature f : eClass.getEStructuralFeatures()) {
                assertThat(f.isDerived()).as("%s.%s must not be derived", eClass.getName(), f.getName()).isFalse();
            }
        }
    }

    @Test
    void noXmlTypeDataTypes() {
        for (EClassifier c : CWMORMPackage.eINSTANCE.getEClassifiers()) {
            if (!(c instanceof EClass eClass)) {
                continue;
            }
            for (EAttribute a : eClass.getEAttributes()) {
                EPackage typePackage = a.getEAttributeType().getEPackage();
                assertThat(typePackage)
                        .as("%s.%s is typed by %s", eClass.getName(), a.getName(), typePackage.getNsURI())
                        .isIn(CWMORMPackage.eINSTANCE, CorePackage.eINSTANCE);
            }
        }
    }

    @Test
    void modelElementAnchoring() {
        EClass modelElement = CorePackage.eINSTANCE.getModelElement();
        for (EClassifier c : CWMORMPackage.eINSTANCE.getEClassifiers()) {
            if (!(c instanceof EClass eClass) || eClass.isAbstract()) {
                continue;
            }
            assertThat(eClass.getEAllSuperTypes())
                    .as("%s must be a core::ModelElement", eClass.getName())
                    .contains(modelElement);
        }
        // no local re-declaration of inherited ModelElement features
        for (EClassifier c : CWMORMPackage.eINSTANCE.getEClassifiers()) {
            if (!(c instanceof EClass eClass)) {
                continue;
            }
            for (EStructuralFeature f : eClass.getEStructuralFeatures()) {
                assertThat(f.getName()).as("%s re-declares %s", eClass.getName(), f.getName())
                        .isNotIn("name", "visibility");
                if (!"OrmElement".equals(eClass.getName())) {
                    assertThat(f.getName()).as("%s re-declares description", eClass.getName())
                            .isNotEqualTo("description");
                }
            }
        }
    }

    @Test
    void objectSideIsTypedByCwmCore() {
        assertThat(CWMORMPackage.eINSTANCE.getManagedType_CwmClass().getEReferenceType())
                .isEqualTo(CorePackage.eINSTANCE.getClass_());
        assertThat(CWMORMPackage.eINSTANCE.getValueMapping_CwmAttribute().getEReferenceType())
                .isEqualTo(CorePackage.eINSTANCE.getAttribute());
        assertThat(CWMORMPackage.eINSTANCE.getEntity_CwmTable().getEReferenceType())
                .isEqualTo(RelationalPackage.eINSTANCE.getNamedColumnSet());
        assertThat(CWMORMPackage.eINSTANCE.getColumnRef_CwmColumn().getEReferenceType())
                .isEqualTo(RelationalPackage.eINSTANCE.getColumn());
    }

    @Test
    void newAnchorsAreTyped() {
        assertThat(CWMORMPackage.eINSTANCE.getJoin_CwmForeignKey().getEReferenceType())
                .isEqualTo(RelationalPackage.eINSTANCE.getForeignKey());
        assertThat(CWMORMPackage.eINSTANCE.getTransient_CwmFeature().getEReferenceType())
                .isEqualTo(CorePackage.eINSTANCE.getStructuralFeature());
        assertThat(CWMORMPackage.eINSTANCE.getLifecycleCallback_CwmOperation().getEReferenceType())
                .isEqualTo(BehavioralPackage.eINSTANCE.getOperation());
        assertThat(CWMORMPackage.eINSTANCE.getNamedNativeQuery_CwmQuery().getEReferenceType())
                .isEqualTo(RelationalPackage.eINSTANCE.getQueryColumnSet());
        assertThat(CWMORMPackage.eINSTANCE.getNamedStoredProcedureQuery_CwmProcedure().getLowerBound())
                .isEqualTo(1);
        assertThat(CWMORMPackage.eINSTANCE.getAttributeOverride_CwmPath().isOrdered()).isTrue();
        assertThat(CWMORMPackage.eINSTANCE.getConverter_CwmClass().getEReferenceType())
                .isEqualTo(CorePackage.eINSTANCE.getClass_());
        assertThat(CWMORMPackage.eINSTANCE.getEntity_CwmDiscriminatorColumn().getEReferenceType())
                .isEqualTo(RelationalPackage.eINSTANCE.getColumn());
    }

    @Test
    void featuresThatFollowFromAnAnchorAreNotStored() {
        List<String> derivable = List.of("cwmObjectPackage", "cwmCatalog", "cwmPrimaryKey", "discriminatorType",
                "optional", "constraintMode", "temporal", "lob", "metadataComplete", "xmlMappingMetadataComplete",
                "attributeName", "methodName", "entityClass", "resultClass", "targetClass", "class");
        for (EClass eClass : classes()) {
            for (EStructuralFeature f : eClass.getEStructuralFeatures()) {
                assertThat(f.getName()).as("%s.%s", eClass.getName(), f.getName()).isNotIn(derivable);
            }
        }
        EClass entityMappings = CWMORMPackage.eINSTANCE.getEntityMappings();
        assertThat(entityMappings.getEAllStructuralFeatures()).extracting(EStructuralFeature::getName)
                .doesNotContain("cwmSchema");
        assertThat(CWMORMPackage.eINSTANCE.getNamedNativeQuery().getEAllStructuralFeatures())
                .extracting(EStructuralFeature::getName).doesNotContain("query");
        for (EClass eClass : classes()) {
            for (EAttribute a : eClass.getEAttributes()) {
                if (List.of("converter", "mapsId").contains(a.getName())) {
                    assertThat(a.getEAttributeType()).as("%s.%s", eClass.getName(), a.getName())
                            .isNotEqualTo(CorePackage.eINSTANCE.getString());
                }
            }
        }
        assertThat(CWMORMPackage.eINSTANCE.getManyToOne_MapsId().getEAttributeType().getInstanceClass())
                .isEqualTo(boolean.class);
    }

    @Test
    void joinsHaveExactlyOnePlace() {
        assertThat(classes()).filteredOn(c -> c.getName().equals("Join")).hasSize(1);
        assertThat(classes()).extracting(EClass::getName).doesNotContain("ForeignKey", "DiscriminatorColumn",
                "StoredProcedureParameter");
        EClass joinColumn = CWMORMPackage.eINSTANCE.getJoinColumn();
        assertThat(joinColumn.getEAllStructuralFeatures())
                .filteredOn(f -> f.getEContainingClass().getEPackage() == CWMORMPackage.eINSTANCE
                        && f.getEContainingClass() != CWMORMPackage.eINSTANCE.getOrmElement())
                .extracting(EStructuralFeature::getName)
                .containsExactlyInAnyOrder("cwmColumn", "cwmReferencedColumn");
        for (EClass eClass : classes()) {
            for (EReference r : eClass.getEReferences()) {
                if (r.isContainment() && r.getEReferenceType() == joinColumn) {
                    assertThat(eClass).as("only a Join holds join columns: %s.%s", eClass.getName(), r.getName())
                            .isEqualTo(CWMORMPackage.eINSTANCE.getJoin());
                }
            }
        }
    }

    @Test
    void unitWideDefinitionsLiveOnlyAtTheRoot() {
        EClass generator = CWMORMPackage.eINSTANCE.getGenerator();
        EClass query = CWMORMPackage.eINSTANCE.getQuery();
        EClass rsm = CWMORMPackage.eINSTANCE.getSqlResultSetMapping();
        EClass listener = CWMORMPackage.eINSTANCE.getEntityListener();
        for (EClass eClass : classes()) {
            for (EReference r : eClass.getEAllReferences()) {
                if (!r.isContainment()) {
                    continue;
                }
                EClass type = r.getEReferenceType();
                boolean unitWide = generator.isSuperTypeOf(type) || query.isSuperTypeOf(type)
                        || rsm == type || listener == type;
                if (unitWide) {
                    assertThat(r.getEContainingClass()).as("%s.%s", eClass.getName(), r.getName())
                            .isEqualTo(CWMORMPackage.eINSTANCE.getEntityMappings());
                }
            }
        }
    }

    /** Checklist 3: strings only where no model element can stand in. */
    @Test
    void stringAttributesOnlyWhereNoModelElementExists() {
        List<String> allowed = List.of("Entity.discriminatorValue", "TableGenerator.pkColumnValue",
                "SequenceGenerator.sequenceName", "NamedQuery.query", "QueryHint.value",
                "OrmElement.description");
        List<String> strings = classes().stream().flatMap(c -> c.getEAttributes().stream()
                .filter(a -> a.getEAttributeType().getInstanceClass() == String.class)
                .map(a -> c.getName() + "." + a.getName())).toList();
        assertThat(strings).containsExactlyInAnyOrderElementsOf(allowed);
    }

    @Test
    void associationSideIsTyped() {
        assertThat(CWMORMPackage.eINSTANCE.getRelationshipMapping_CwmAssociationEnd().getEReferenceType())
                .isEqualTo(RelationshipsPackage.eINSTANCE.getAssociationEnd());
    }

    @Test
    void miniMappingResolvesAcrossThreeResourcesAndBackNavigates(@TempDir Path tmp) throws Exception {
        ResourceSet set = newResourceSet();

        // object model: com.example.Employee with firstName + association to Department
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package pkgCom = CoreFactory.eINSTANCE.createPackage();
        pkgCom.setName("com");
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package pkgExample = CoreFactory.eINSTANCE.createPackage();
        pkgExample.setName("example");
        pkgCom.getOwnedElement().add(pkgExample);
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class employee = CoreFactory.eINSTANCE.createClass();
        employee.setName("Employee");
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class department = CoreFactory.eINSTANCE.createClass();
        department.setName("Department");
        pkgExample.getOwnedElement().add(employee);
        pkgExample.getOwnedElement().add(department);
        Attribute firstName = CoreFactory.eINSTANCE.createAttribute();
        firstName.setName("firstName");
        employee.getFeature().add(firstName);
        org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Association worksIn = RelationshipsFactory.eINSTANCE
                .createAssociation();
        worksIn.setName("WorksIn");
        AssociationEnd endEmployee = RelationshipsFactory.eINSTANCE.createAssociationEnd();
        endEmployee.setName("employee");
        endEmployee.setType(employee);
        AssociationEnd endDepartment = RelationshipsFactory.eINSTANCE.createAssociationEnd();
        endDepartment.setName("department");
        endDepartment.setType(department);
        worksIn.getFeature().add(endEmployee);
        worksIn.getFeature().add(endDepartment);
        pkgExample.getOwnedElement().add(worksIn);

        // catalog: HR.EMPLOYEE with FIRST_NAME
        Catalog catalog = RelationalFactory.eINSTANCE.createCatalog();
        catalog.setName("DB");
        Schema schema = RelationalFactory.eINSTANCE.createSchema();
        schema.setName("HR");
        catalog.getOwnedElement().add(schema);
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Table empTable = RelationalFactory.eINSTANCE.createTable();
        empTable.setName("EMPLOYEE");
        schema.getOwnedElement().add(empTable);
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column firstNameCol = RelationalFactory.eINSTANCE
                .createColumn();
        firstNameCol.setName("FIRST_NAME");
        empTable.getFeature().add(firstNameCol);

        // mapping: no wrappers, no name strings — the cwm references are the truth
        EntityMappings mappings = CWMORMFactory.eINSTANCE.createEntityMappings();
        mappings.setName("example");
        Entity entity = CWMORMFactory.eINSTANCE.createEntity();
        entity.setCwmClass(employee);
        entity.setCwmTable(empTable);
        Basic basic = CWMORMFactory.eINSTANCE.createBasic();
        basic.setCwmAttribute(firstName);
        Column ormColumn = CWMORMFactory.eINSTANCE.createColumn();
        ormColumn.setCwmColumn(firstNameCol);
        basic.setColumn(ormColumn);
        entity.getBasic().add(basic);
        ManyToOne manyToOne = CWMORMFactory.eINSTANCE.createManyToOne();
        manyToOne.setCwmAssociationEnd(endDepartment);
        entity.getManyToOne().add(manyToOne);
        mappings.getEntity().add(entity);

        // three side-car resources
        Resource rObjects = set.createResource(URI.createFileURI(tmp.resolve("objects.xmi").toString()));
        rObjects.getContents().add(pkgCom);
        Resource rCatalog = set.createResource(URI.createFileURI(tmp.resolve("catalog.xmi").toString()));
        rCatalog.getContents().add(catalog);
        Resource rMapping = set.createResource(URI.createFileURI(tmp.resolve("mapping.cwmorm").toString()));
        rMapping.getContents().add(mappings);
        rObjects.save(null);
        rCatalog.save(null);
        rMapping.save(null);

        // reload into a fresh set
        ResourceSet fresh = newResourceSet();
        Resource loaded = fresh.getResource(URI.createFileURI(tmp.resolve("mapping.cwmorm").toString()), true);
        EcoreUtil.resolveAll(fresh);
        EntityMappings loadedMappings = (EntityMappings) loaded.getContents().get(0);
        Entity loadedEntity = loadedMappings.getEntity().get(0);
        assertThat(loadedEntity.getCwmClass().getName()).isEqualTo("Employee");
        assertThat(loadedEntity.getCwmClass().eIsProxy()).isFalse();
        assertThat(loadedEntity.getCwmTable().getName()).isEqualTo("EMPLOYEE");
        Basic loadedBasic = loadedEntity.getBasic().get(0);
        assertThat(loadedBasic.getCwmAttribute().getName()).isEqualTo("firstName");
        assertThat(loadedBasic.getColumn().getCwmColumn().getName()).isEqualTo("FIRST_NAME");
        ManyToOne loadedManyToOne = loadedEntity.getManyToOne().get(0);
        AssociationEnd loadedEnd = loadedManyToOne.getCwmAssociationEnd();
        assertThat(loadedEnd.getType().getName()).isEqualTo("Department");

        // package, schema and catalog follow from the anchors of the types
        assertThat(OrmBindings.objectPackage(loadedMappings).getName()).isEqualTo("example");
        assertThat(OrmBindings.schema(loadedEntity)).isEqualTo("HR");
        assertThat(OrmBindings.catalog(loadedEntity)).isEqualTo("DB");

        // hidden opposite: silent-empty without adapter, found with adapter
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column loadedCol = loadedBasic.getColumn().getCwmColumn();
        assertThat(InverseReferences.isIndexed(loadedCol)).isFalse();
        InverseReferences.install(fresh);
        List<Column> users = InverseReferences.referencingList(loadedCol,
                CWMORMPackage.eINSTANCE.getColumnRef_CwmColumn(), Column.class);
        assertThat(users).hasSize(1).first().isSameAs(loadedBasic.getColumn());
    }

    private static List<EClass> classes() {
        return CWMORMPackage.eINSTANCE.getEClassifiers().stream().filter(EClass.class::isInstance)
                .map(EClass.class::cast).toList();
    }

    private static void assertNoAnnotation(EPackage pkg, String source) {
        for (var it = pkg.eAllContents(); it.hasNext();) {
            var next = it.next();
            if (next instanceof EModelElement el) {
                assertThat(el.getEAnnotation(source))
                        .as("annotation %s on %s", source, el)
                        .isNull();
            }
        }
        assertThat(pkg.getEAnnotation(source)).isNull();
    }

    private static ResourceSet newResourceSet() {
        ResourceSet set = new ResourceSetImpl();
        set.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
        set.getPackageRegistry().put(CWMORMPackage.eNS_URI, CWMORMPackage.eINSTANCE);
        set.getPackageRegistry().put(CorePackage.eNS_URI, CorePackage.eINSTANCE);
        set.getPackageRegistry().put(RelationalPackage.eNS_URI, RelationalPackage.eINSTANCE);
        set.getPackageRegistry().put(RelationshipsPackage.eNS_URI, RelationshipsPackage.eINSTANCE);
        set.getPackageRegistry().put(BehavioralPackage.eNS_URI, BehavioralPackage.eINSTANCE);
        return set;
    }
}
