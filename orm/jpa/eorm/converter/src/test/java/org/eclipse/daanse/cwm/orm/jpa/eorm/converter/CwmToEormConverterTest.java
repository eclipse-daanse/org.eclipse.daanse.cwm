/********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Data In Motion Consulting - initial implementation
 ********************************************************************/
package org.eclipse.daanse.cwm.orm.jpa.eorm.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwmx.eorm.CWMORMFactory;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.fennec.persistence.eorm.AccessType;
import org.eclipse.fennec.persistence.eorm.Basic;
import org.eclipse.fennec.persistence.eorm.EClassObject;
import org.eclipse.fennec.persistence.eorm.EFeatureObject;
import org.eclipse.fennec.persistence.eorm.Entity;
import org.eclipse.fennec.persistence.eorm.EntityMappings;
import org.eclipse.fennec.persistence.eorm.FetchType;
import org.eclipse.fennec.persistence.eorm.Id;
import org.eclipse.fennec.persistence.eorm.ManyToOne;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for {@link CwmToEormConverter}: build a small Common Warehouse EORM model bound to
 * a throwaway domain Ecore, convert it, and assert the resulting Fennec EORM model.
 */
class CwmToEormConverterTest {

	private final CwmToEormConverter converter = new CwmToEormConverter();

	// --- throwaway domain model the EORM mappings bind to ---
	private EClass customerEClass;
	private EAttribute idAttr;
	private EAttribute nameAttr;
	private EReference parentRef;

	// --- the CWM EORM source model ---
	private org.eclipse.daanse.cwm.model.cwmx.eorm.EntityMappings cwmMappings;

	@BeforeEach
	void setup() {
		EcoreFactory ecore = EcoreFactory.eINSTANCE;

		EPackage shop = ecore.createEPackage();
		shop.setName("shop");
		shop.setNsPrefix("shop");
		shop.setNsURI("http://example.org/shop");

		customerEClass = ecore.createEClass();
		customerEClass.setName("Customer");
		shop.getEClassifiers().add(customerEClass);

		idAttr = ecore.createEAttribute();
		idAttr.setName("id");
		idAttr.setEType(EcorePackage.Literals.ESTRING);
		customerEClass.getEStructuralFeatures().add(idAttr);

		nameAttr = ecore.createEAttribute();
		nameAttr.setName("name");
		nameAttr.setEType(EcorePackage.Literals.ESTRING);
		customerEClass.getEStructuralFeatures().add(nameAttr);

		parentRef = ecore.createEReference();
		parentRef.setName("parent");
		parentRef.setEType(customerEClass);
		customerEClass.getEStructuralFeatures().add(parentRef);

		CWMORMFactory f = CWMORMFactory.eINSTANCE;

		org.eclipse.daanse.cwm.model.cwmx.eorm.Entity entity = f.createEntity();
		entity.setName("Customer");
		entity.setClass(customerEClass);
		entity.setAccess(org.eclipse.daanse.cwm.model.cwmx.eorm.AccessType.FIELD);

		org.eclipse.daanse.cwm.model.cwmx.eorm.Attributes attributes = f.createAttributes();

		org.eclipse.daanse.cwm.model.cwmx.eorm.Id id = f.createId();
		id.setName("id");
		id.setFeature(idAttr);
		attributes.getId().add(id);

		org.eclipse.daanse.cwm.model.cwmx.eorm.Basic basic = f.createBasic();
		basic.setName("name");
		basic.setFeature(nameAttr);
		basic.setOptional(false);
		attributes.getBasic().add(basic);

		org.eclipse.daanse.cwm.model.cwmx.eorm.ManyToOne parent = f.createManyToOne();
		parent.setName("parent");
		parent.setFeature(parentRef);
		parent.setFetch(org.eclipse.daanse.cwm.model.cwmx.eorm.FetchType.LAZY);
		attributes.getManyToOne().add(parent);

		entity.setAttributes(attributes);

		cwmMappings = f.createEntityMappings();
		cwmMappings.getEntity().add(entity);
	}

	@Test
	@DisplayName("converts into a Fennec EntityMappings with the same entity")
	void convertsRoot() {
		EntityMappings result = converter.convertEntityMappings(cwmMappings);

		assertThat(result).isInstanceOf(EntityMappings.class);
		assertThat(result.getEntity()).hasSize(1);
		assertThat(result.getEntity().get(0).getName()).isEqualTo("Customer");
	}

	@Nested
	@DisplayName("Entity binding")
	class EntityBinding {

		private Entity entity() {
			return converter.convertEntityMappings(cwmMappings).getEntity().get(0);
		}

		@Test
		@DisplayName("preserves the domain EClass reference by identity")
		void preservesClassReference() {
			assertThat(entity().getClass_()).isSameAs(customerEClass);
		}

		@Test
		@DisplayName("rebuilds the accessibleObject as an EClassObject for the entity class")
		void rebuildsEClassObject() {
			assertThat(entity().getAccessibleObject())
					.isInstanceOf(EClassObject.class)
					.extracting(a -> ((EClassObject) a).getEclass())
					.isSameAs(customerEClass);
		}

		@Test
		@DisplayName("remaps the AccessType enum across the two metamodels")
		void remapsAccessEnum() {
			assertThat(entity().getAccess()).isEqualTo(AccessType.FIELD);
		}
	}

	@Nested
	@DisplayName("Attribute binding")
	class AttributeBinding {

		private Entity entity() {
			return converter.convertEntityMappings(cwmMappings).getEntity().get(0);
		}

		@Test
		@DisplayName("maps the Id and wraps its feature in an EFeatureObject")
		void mapsId() {
			Id id = entity().getAttributes().getId().get(0);
			assertThat(id.getName()).isEqualTo("id");
			assertThat(id.getAccessibleObject()).isInstanceOf(EFeatureObject.class);
			assertThat(((EFeatureObject) id.getAccessibleObject()).getFeature()).isSameAs(idAttr);
		}

		@Test
		@DisplayName("maps the Basic, copies scalar attributes and wraps its feature")
		void mapsBasic() {
			Basic basic = entity().getAttributes().getBasic().get(0);
			assertThat(basic.getName()).isEqualTo("name");
			assertThat(basic.isOptional()).isFalse();
			assertThat(basic.getAccessibleObject()).isInstanceOf(EFeatureObject.class);
			assertThat(((EFeatureObject) basic.getAccessibleObject()).getFeature()).isSameAs(nameAttr);
		}

		@Test
		@DisplayName("maps the ManyToOne and remaps the FetchType enum")
		void mapsManyToOne() {
			ManyToOne parent = entity().getAttributes().getManyToOne().get(0);
			assertThat(parent.getName()).isEqualTo("parent");
			assertThat(parent.getFetch()).isEqualTo(FetchType.LAZY);
			assertThat(((EFeatureObject) parent.getAccessibleObject()).getFeature()).isSameAs(parentRef);
		}
	}

	@Test
	@DisplayName("drops CWM-only information that has no Fennec counterpart")
	void dropsCwmOnlyFeatures() {
		Id id = converter.convertEntityMappings(cwmMappings).getEntity().get(0).getAttributes().getId().get(0);

		// CWM carries the EMF binding as a 'feature' reference directly on ENamedBase; the Fennec
		// metamodel has no such feature (it lives under accessibleObject instead), so the conversion
		// has no place to keep it on the element itself.
		assertThat(id.eClass().getEStructuralFeature("feature")).isNull();
	}
}
