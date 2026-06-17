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
package org.eclipse.daanse.cwm.model.cwmx.eorm.converter;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import java.util.Map;

import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil.Copier;
import org.eclipse.fennec.persistence.eorm.EAccessor;
import org.eclipse.fennec.persistence.eorm.EClassObject;
import org.eclipse.fennec.persistence.eorm.EFeatureObject;
import org.eclipse.fennec.persistence.eorm.EORMElement;
import org.eclipse.fennec.persistence.eorm.EORMFactory;
import org.eclipse.fennec.persistence.eorm.EORMPackage;
import org.eclipse.fennec.persistence.eorm.EntityMappings;

/**
 * Converts a Common Warehouse EORM model (Eclipse Daanse, nsURI
 * {@code https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.cwmx.eorm}) into the
 * structurally equivalent Fennec Persistence EORM model
 * ({@link EORMPackage#eNS_URI}).
 * <p>
 * The two metamodels are JPA-ORM models that line up almost one-to-one by class and feature
 * name. The conversion is therefore implemented on top of EMF's {@link Copier}, which already
 * provides a robust two-phase deep copy with cross-reference resolution. The only specialisation
 * needed is:
 * <ul>
 *   <li><b>class retargeting</b> &mdash; {@link CwmEormCopier#getTarget(EClass)} maps each CWM
 *       class onto the Fennec class with the same name; the inherited
 *       {@code getTarget(EStructuralFeature)} then resolves features by name and skips any feature
 *       that has no counterpart on the Fennec side. This is what causes the extra CWM-only
 *       information to be dropped (as intended).</li>
 *   <li><b>enum remapping</b> &mdash; enum literals are remapped by name, because the generated CWM
 *       and Fennec enums are distinct Java types.</li>
 *   <li><b>accessor binding</b> &mdash; the CWM model stores its EMF binding as a {@code feature}
 *       reference on {@code ENamedBase} and a {@code class} reference on {@code Entity}, whereas
 *       Fennec wraps these in an {@link EAccessor} ({@link EFeatureObject} / {@link EClassObject})
 *       held by {@link EORMElement#getAccessibleObject()}. The post-processing pass rebuilds that
 *       wrapper, mirroring the behaviour of the ORM processors.</li>
 * </ul>
 * The converter deliberately depends only on the Fennec EORM API and EMF core &mdash; it reads the
 * source model purely through its {@link EObject#eClass() metamodel}, so it works whether the CWM
 * instance is backed by generated classes or a dynamic package.
 *
 * @see Copier
 */
public class CwmToEormConverter {

	/**
	 * Converts a Common Warehouse EORM object graph into the equivalent Fennec EORM graph.
	 *
	 * @param source the root of the CWM EORM model (typically an {@code EntityMappings}); must not
	 *               be {@code null}
	 * @return the converted Fennec EORM root object
	 */
	public EObject convert(EObject source) {
		requireNonNull(source, "Source CWM EORM object must not be null");
		CwmEormCopier copier = new CwmEormCopier();
		EObject result = copier.copy(source);
		copier.copyReferences();
		copier.rebuildAccessors();
		return result;
	}

	/**
	 * Convenience variant that converts and casts the result to {@link EntityMappings}, the usual
	 * root of an EORM document.
	 *
	 * @param source the root of the CWM EORM model; must not be {@code null}
	 * @return the converted Fennec {@link EntityMappings}
	 */
	public EntityMappings convertEntityMappings(EObject source) {
		return (EntityMappings) convert(source);
	}

	/**
	 * The {@link Copier} specialisation that performs the cross-metamodel copy.
	 */
	static class CwmEormCopier extends Copier {

		private static final long serialVersionUID = 1L;

		private final EORMPackage target = EORMPackage.eINSTANCE;

		/**
		 * Maps a source (CWM) class to the Fennec class with the same name, so the copier
		 * instantiates Fennec objects. Classes without a Fennec counterpart (e.g. Ecore helper
		 * types such as map entries) are copied as-is.
		 */
		@Override
		protected EClass getTarget(EClass eClass) {
			EClassifier counterpart = target.getEClassifier(eClass.getName());
			if (counterpart instanceof EClass tc) {
				return tc;
			}
			return eClass;
		}

		/**
		 * Resolves the target feature by name on the retargeted (Fennec) class. Returns {@code null}
		 * when the Fennec class has no feature of that name, which is how CWM-only features are
		 * dropped. (EMF's {@link Copier} does not remap features across metamodels on its own; its
		 * default returns the source feature unchanged, so this override is required for the
		 * cross-metamodel copy.)
		 */
		@Override
		protected EStructuralFeature getTarget(EStructuralFeature eStructuralFeature) {
			EClass sourceClass = eStructuralFeature.getEContainingClass();
			EClass targetClass = getTarget(sourceClass);
			if (targetClass == sourceClass) {
				return eStructuralFeature;
			}
			return targetClass.getEStructuralFeature(eStructuralFeature.getName());
		}

		/**
		 * Copies a single attribute, remapping enum literals by name (the CWM and Fennec enums are
		 * distinct Java types, so the raw value cannot be set directly).
		 */
		@Override
		protected void copyAttribute(EAttribute eAttribute, EObject eObject, EObject copyEObject) {
			if (!eObject.eIsSet(eAttribute)) {
				return;
			}
			EStructuralFeature targetFeature = getTarget(eAttribute);
			if (isNull(targetFeature)) {
				return;
			}
			if (targetFeature.getEType() instanceof EEnum targetEnum && !eAttribute.isMany()) {
				Object remapped = remapEnum(eObject.eGet(eAttribute), targetEnum);
				if (nonNull(remapped)) {
					copyEObject.eSet(targetFeature, remapped);
				}
				return;
			}
			super.copyAttribute(eAttribute, eObject, copyEObject);
		}

		private static Object remapEnum(Object value, EEnum targetEnum) {
			if (!(value instanceof Enumerator enumerator)) {
				return value;
			}
			EEnumLiteral literal = targetEnum.getEEnumLiteral(enumerator.getName());
			if (isNull(literal)) {
				literal = targetEnum.getEEnumLiteralByLiteral(enumerator.getLiteral());
			}
			return nonNull(literal) ? literal.getInstance() : null;
		}

		/**
		 * Rebuilds the Fennec {@link EAccessor} binding that the base copy cannot produce: the CWM
		 * model carries a {@code feature}/{@code class} reference directly on the element, while
		 * Fennec wraps it in {@link EORMElement#getAccessibleObject()}.
		 */
		void rebuildAccessors() {
			for (Map.Entry<EObject, EObject> entry : entrySet()) {
				EObject source = entry.getKey();
				if (!(entry.getValue() instanceof EORMElement element) || nonNull(element.getAccessibleObject())) {
					continue;
				}
				EObject feature = referenced(source, "feature");
				if (feature instanceof EStructuralFeature esf) {
					EFeatureObject accessor = EORMFactory.eINSTANCE.createEFeatureObject();
					accessor.setFeature(esf);
					accessor.setName(esf.getName());
					element.setAccessibleObject(accessor);
					continue;
				}
				EObject mapped = referenced(source, "class");
				if (mapped instanceof EClass eClass) {
					EClassObject accessor = EORMFactory.eINSTANCE.createEClassObject();
					accessor.setEclass(eClass);
					String name = eClass.getInstanceClassName();
					if (isNull(name)) {
						name = eClass.getEPackage().getName() + "." + eClass.getName();
					}
					accessor.setName(name);
					element.setAccessibleObject(accessor);
				}
			}
		}

		/** Returns the object referenced by the named reference, or {@code null} if absent/unset. */
		private static EObject referenced(EObject source, String referenceName) {
			EStructuralFeature feature = source.eClass().getEStructuralFeature(referenceName);
			if (feature instanceof EReference && source.eIsSet(feature)) {
				Object value = source.eGet(feature);
				if (value instanceof EObject eObject) {
					return eObject;
				}
			}
			return null;
		}
	}
}
