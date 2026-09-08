/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.orm.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Multiplicity;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.MultiplicityRange;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Association;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.RelationshipsFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.Entity;
import org.eclipse.daanse.cwm.model.daanse.orm.EntityMappings;
import org.eclipse.daanse.cwm.model.daanse.orm.ManyToOne;
import org.eclipse.daanse.cwm.model.daanse.orm.OneToMany;
import org.junit.jupiter.api.Test;

class OrmModelValidatorTest {

    @Test
    void validBidirectionalMappingHasNoIssues() {
        Fixture f = new Fixture();
        f.oneToMany.setOwningSide(false);
        assertThat(OrmModelValidator.validate(f.mappings)).isEmpty();
    }

    @Test
    void twoOwningSidesAreReported() {
        Fixture f = new Fixture();
        // ManyToOne is always owning; the OneToMany defaults to owning too -> 2 owners
        assertThat(OrmModelValidator.validate(f.mappings))
                .anySatisfy(msg -> assertThat(msg).contains("Owning-Side"));
    }

    @Test
    void toOneAgainstManyEndIsReported() {
        Fixture f = new Fixture();
        f.oneToMany.setOwningSide(false);
        // give the ManyToOne target end an upper bound of -1 (many) -> violation
        setMultiplicity(f.endDepartment, -1);
        assertThat(OrmModelValidator.validate(f.mappings))
                .anySatisfy(msg -> assertThat(msg).contains("To-One"));
    }

    @Test
    void nameDriftIsReported() {
        Fixture f = new Fixture();
        f.oneToMany.setOwningSide(false);
        f.basic.setName("somethingElse");
        assertThat(OrmModelValidator.validate(f.mappings))
                .anySatisfy(msg -> assertThat(msg).contains("weicht vom CWM-Anker"));
    }

    private static void setMultiplicity(AssociationEnd end, long upper) {
        Multiplicity m = CoreFactory.eINSTANCE.createMultiplicity();
        MultiplicityRange r = CoreFactory.eINSTANCE.createMultiplicityRange();
        r.setLower(0);
        r.setUpper(upper);
        m.getRange().add(r);
        end.setMultiplicity(m);
    }

    /** Employee -(WorksIn)- Department, gemappt als ManyToOne + inverse OneToMany. */
    private static final class Fixture {
        final EntityMappings mappings = CWMORMFactory.eINSTANCE.createEntityMappings();
        final AssociationEnd endDepartment;
        final org.eclipse.daanse.cwm.model.daanse.orm.Basic basic;
        final ManyToOne manyToOne;
        final OneToMany oneToMany;

        Fixture() {
            var employee = CoreFactory.eINSTANCE.createClass();
            employee.setName("Employee");
            var department = CoreFactory.eINSTANCE.createClass();
            department.setName("Department");
            Attribute firstName = CoreFactory.eINSTANCE.createAttribute();
            firstName.setName("firstName");
            employee.getFeature().add(firstName);

            Association worksIn = RelationshipsFactory.eINSTANCE.createAssociation();
            worksIn.setName("WorksIn");
            AssociationEnd endEmployee = RelationshipsFactory.eINSTANCE.createAssociationEnd();
            endEmployee.setName("employees");
            endEmployee.setType(employee);
            endDepartment = RelationshipsFactory.eINSTANCE.createAssociationEnd();
            endDepartment.setName("department");
            endDepartment.setType(department);
            worksIn.getFeature().add(endEmployee);
            worksIn.getFeature().add(endDepartment);

            Entity employeeEntity = CWMORMFactory.eINSTANCE.createEntity();
            employeeEntity.setCwmClass(employee);
            basic = CWMORMFactory.eINSTANCE.createBasic();
            basic.setCwmAttribute(firstName);
            employeeEntity.getBasic().add(basic);
            manyToOne = CWMORMFactory.eINSTANCE.createManyToOne();
            manyToOne.setCwmAssociationEnd(endDepartment);
            employeeEntity.getManyToOne().add(manyToOne);

            Entity departmentEntity = CWMORMFactory.eINSTANCE.createEntity();
            departmentEntity.setCwmClass(department);
            oneToMany = CWMORMFactory.eINSTANCE.createOneToMany();
            oneToMany.setCwmAssociationEnd(endEmployee);
            departmentEntity.getOneToMany().add(oneToMany);

            mappings.getEntity().add(employeeEntity);
            mappings.getEntity().add(departmentEntity);
        }
    }
}
