/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.transparency;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.daanse.governance.deidentification.DeidentificationPackage;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.junit.jupiter.api.Test;

/** The shape of the transparency metamodel after its port to a builder. */
class TransparencyModelTest {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final TransparencyPackage P = TransparencyPackage.eINSTANCE;

    @Test
    void theNamespaceStaysAndThePackageDeclaresItsDelegates() {
        assertThat(P.getNsURI())
                .isEqualTo("https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.governance.transparency");
        assertThat(P.getNsPrefix()).isEqualTo("transparency");
        assertThat(P.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore/OCL").getDetails().get("hiddenOpposites"))
                .isEqualTo("true");
    }

    @Test
    void cwmDataTypesOneWayReferencesAndCaptions() {
        int captioned = 0;
        for (EClassifier c : P.getEClassifiers()) {
            assertThat(captioned(c)).as(c.getName()).isTrue();
            captioned++;
            if (c instanceof EClass cls) {
                for (EStructuralFeature f : cls.getEStructuralFeatures()) {
                    assertThat(captioned(f)).as(f.getName()).isTrue();
                    captioned++;
                    if (f instanceof EAttribute a) {
                        EClassifier type = a.getEAttributeType();
                        assertThat(type.getEPackage() == CorePackage.eINSTANCE || type.getEPackage() == P)
                                .as(f.getName()).isTrue();
                    } else if (f instanceof EReference r && !r.isContainment()) {
                        assertThat(r.getEOpposite()).as(r.getName()).isNull();
                        assertThat(r.getEAnnotation(EMOF).getDetails().get("Property.oppositeRoleName")).as(r.getName())
                                .startsWith("transparency");
                    }
                }
            }
        }
        assertThat(captioned).as("every classifier and feature; the literals carry none").isEqualTo(24);
    }

    @Test
    void retentionAnonymizationAndLegalBasisMovedToTheGovernanceModels() {
        assertThat(P.getEClassifier("AnonymizationLevel")).isNull();
        assertThat(P.getTransparencyClassRating().getEStructuralFeature("retentionYears")).isNull();
        assertThat(P.getTransparencyClassRating().getEStructuralFeature("anonymizationLevel")).isNull();
        assertThat(P.getTransparencyFieldRating().getEStructuralFeature("anonymizationLevel")).isNull();
        assertThat(P.getTransparencyAssessment().getEStructuralFeature("legalBasis")).isNull();
        assertThat(P.getTransparencyClassRating_Policy().getEReferenceType())
                .isSameAs(DeidentificationPackage.Literals.DEIDENTIFICATION_POLICY);
        assertThat(P.getTransparencyClassRating().getEStructuralFeature("legalReference")).isNotNull();
    }

    private static boolean captioned(EModelElement el) {
        EAnnotation a = el.getEAnnotation(GENMODEL);
        return a != null && a.getDetails().containsKey("caption.de") && a.getDetails().containsKey("caption.en")
                && ((ENamedElement) el).getName() != null;
    }
}
