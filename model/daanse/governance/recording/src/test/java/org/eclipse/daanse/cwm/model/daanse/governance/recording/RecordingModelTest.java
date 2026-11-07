/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.recording;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiPredicate;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.LifecyclePackage;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.RetentionSchedules;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.util.HistorisationRules;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.util.RecordingRules;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.util.RecordingValidator;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.ocl.common.OCLConstants;
import org.eclipse.ocl.ecore.delegate.DelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomain;
import org.eclipse.ocl.ecore.delegate.OCLDelegateDomainFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The shape of the recording metamodel, its OCL invariants, and rules in an XMI file. */
class RecordingModelTest {

    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final RecordingPackage P = RecordingPackage.eINSTANCE;
    private static final LifecyclePackage L = LifecyclePackage.eINSTANCE;

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void everyRuleIsALifecycleRule() {
        List<EClass> recording = List.of(P.getAbsoluteChangeRule(), P.getRelativeChangeRule(), P.getRangeChangeRule(),
                P.getSamplingRule(), P.getThrottleRule(), P.getChangedValueRule(), P.getSwingingDoorRule());
        for (EClass rule : recording) {
            assertThat(rule.getESuperTypes()).as(rule.getName()).containsExactly(P.getRecordingRule());
            assertThat(CorePackage.Literals.CONSTRAINT.isSuperTypeOf(rule)).as(rule.getName()).isTrue();
            assertThat(rule.isAbstract()).as(rule.getName()).isFalse();
        }
        assertThat(P.getRecordingRule().isAbstract()).isTrue();
        assertThat(P.getRecordingRule().getESuperTypes()).containsExactly(L.getLifecycleRule());
        assertThat(P.getHistorisationRule().getESuperTypes()).containsExactly(L.getLifecycleRule());
        assertThat(P.getEClassifiers()).filteredOn(EClass.class::isInstance).hasSize(9);
        assertThat(P.getRecordingRule_LateValues().getDefaultValue()).isEqualTo(LateValueHandling.COMPARE);
        assertThat(P.getRecordingRule_Interpolation().getDefaultValue()).isEqualTo(Interpolation.LINEAR);
    }

    @Test
    void packageDeclaresHiddenOppositesAndOclDelegates() {
        assertThat(P.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore/OCL").getDetails().get("hiddenOpposites"))
                .isEqualTo("true");
        assertThat(P.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore").getDetails().get("validationDelegates"))
                .isEqualTo("http://www.eclipse.org/emf/2002/Ecore/OCL");
    }

    @Test
    void ownReferencesAreOneWayWithAnOppositeRoleName() {
        int references = 0;
        for (EClassifier c : P.getEClassifiers()) {
            if (c instanceof EClass cls) {
                for (EReference r : cls.getEReferences()) {
                    references++;
                    assertThat(r.getEOpposite()).as(r.getName()).isNull();
                    assertThat(r.isContainment()).as(r.getName()).isFalse();
                    assertThat(r.getEAnnotation(EMOF).getDetails().get("Property.oppositeRoleName"))
                            .as(r.getName()).startsWith("recording");
                    assertThat(r.getEAnnotation(EMOF + "#Property.oppositeRoleName").getDetails().get("body"))
                            .as(r.getName()).startsWith("recording");
                }
            }
        }
        assertThat(references).isEqualTo(7);
    }

    @Test
    void attributesUseCwmDataTypesTheDurationOrOwnEnumerations() {
        for (EClassifier c : P.getEClassifiers()) {
            if (c instanceof EClass cls) {
                for (EAttribute a : cls.getEAttributes()) {
                    EClassifier type = a.getEAttributeType();
                    assertThat(type == L.getDuration() || type.getEPackage() == CorePackage.eINSTANCE
                            || type.getEPackage() == P).as(a.getName()).isTrue();
                }
            }
        }
    }

    @Test
    void valuesOnly() {
        Attribute temperature = attribute(model());
        RecordingRule rule = RecordingRules.changedValue(temperature.getOwner().getNamespace(), "changes", temperature);
        assertThat(severity((v, d) -> v.validateRecordingRule_ValuesOnly(rule, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        rule.setScope(DataScope.ROW);
        assertThat(severity((v, d) -> v.validateRecordingRule_ValuesOnly(rule, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void hasMinimumInterval() {
        Attribute temperature = attribute(model());
        ThrottleRule rule = RecordingRules.throttle(temperature.getOwner().getNamespace(), "every-10m",
                Duration.ofMinutes(10), temperature);
        assertThat(severity((v, d) -> v.validateThrottleRule_HasMinimumInterval(rule, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        rule.setMinimumInterval(null);
        assertThat(severity((v, d) -> v.validateThrottleRule_HasMinimumInterval(rule, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void kindHasItsAnchors() {
        Attribute temperature = attribute(model());
        Class station = (Class) temperature.getOwner();
        Attribute from = feature(station, "validFrom");
        HistorisationRule rows = HistorisationRules.rows(station.getNamespace(), "versions",
                HistorisationKind.NEW_VERSION, station);
        assertThat(anchors(rows)).as("new versions of rows without validity").isEqualTo(Diagnostic.ERROR);
        HistorisationRules.validity(rows, from, null);
        assertThat(anchors(rows)).isEqualTo(Diagnostic.OK);

        HistorisationRule values = HistorisationRules.values(station.getNamespace(), "temperature-versions",
                HistorisationKind.NEW_VERSION, temperature);
        assertThat(anchors(values)).as("a new version on change of a value").isEqualTo(Diagnostic.OK);
        values.setKind(HistorisationKind.PREVIOUS_VALUE);
        assertThat(anchors(values)).isEqualTo(Diagnostic.ERROR);
        values.setPreviousValue(feature(station, "previousCity"));
        assertThat(anchors(values)).isEqualTo(Diagnostic.OK);
        values.setKind(HistorisationKind.VERSION_AND_CURRENT);
        assertThat(anchors(values)).isEqualTo(Diagnostic.ERROR);
        values.setCurrentValue(feature(station, "currentCity"));
        assertThat(anchors(values)).isEqualTo(Diagnostic.OK);
        values.setKind(HistorisationKind.HISTORY_TABLE);
        assertThat(anchors(values)).isEqualTo(Diagnostic.ERROR);
        values.setHistory(station);
        assertThat(anchors(values)).isEqualTo(Diagnostic.OK);
        values.setKind(HistorisationKind.SNAPSHOT);
        assertThat(anchors(values)).isEqualTo(Diagnostic.ERROR);
        values.setValidFrom(from);
        assertThat(anchors(values)).isEqualTo(Diagnostic.OK);
    }

    @Test
    void rulesTravelInXmiAndAreFoundFromTheElementWithoutAnIndex(@TempDir Path dir) throws Exception {
        Model model = model();
        Attribute temperature = attribute(model);
        Class station = (Class) temperature.getOwner();
        RecordingRules.status(RecordingRules.heartbeat(RecordingRules.relative(model, "pct-5", 5.0, temperature),
                Duration.ofHours(1)), feature(station, "previousCity"));
        RecordingRules.swingingDoor(model, "door", 0.5, feature(station, "validFrom"));
        HistorisationRules.validity(HistorisationRules.rows(model, "versions", HistorisationKind.NEW_VERSION, station),
                feature(station, "validFrom"), null);
        RetentionSchedules.values(model, "keep-1y", "P1Y", station);

        URI uri = URI.createFileURI(dir.resolve("weather.xmi").toString());
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
        Resource out = rs.createResource(uri);
        out.getContents().add(model);
        out.save(null);
        String xmi = Files.readString(dir.resolve("weather.xmi"), StandardCharsets.UTF_8);
        assertThat(xmi).contains("xsi:type=\"recording:RelativeChangeRule\"", "xsi:type=\"recording:SwingingDoorRule\"",
                "xsi:type=\"recording:HistorisationRule\"", "xsi:type=\"lifecycle:RetentionSchedule\"", "scope=\"row\"");

        ResourceSet fresh = new ResourceSetImpl();
        fresh.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
        Model loaded = (Model) fresh.getResource(uri, true).getContents().get(0);
        Class s = (Class) loaded.getOwnedElement().stream().filter(Class.class::isInstance).findFirst().orElseThrow();
        Attribute t = (Attribute) s.getFeature().get(0);

        RecordingRule recording = RecordingRules.effective(t).orElseThrow();
        assertThat(recording).isInstanceOf(RelativeChangeRule.class);
        assertThat(recording.getMaximumInterval()).isEqualTo("PT1H");
        assertThat(recording.getStatusFeature().getName()).isEqualTo("previousCity");
        assertThat(recording.getBody().getBody()).isEqualTo("(abs(value - last) >= 5.0 % of abs(last)) "
                + "or time - lastTime >= PT1H or previousCity changed");
        assertThat(HistorisationRules.effective(t, DataScope.ROW).orElseThrow().getValidFrom().getName())
                .isEqualTo("validFrom");
        RetentionSchedule retention = RetentionSchedules.effective(t, DataScope.VALUE).orElseThrow();
        assertThat(retention.getStage().get(0).getAfter()).isEqualTo("P1Y");
    }

    private static Model model() {
        Model model = CoreFactory.eINSTANCE.createModel();
        model.setName("m");
        return model;
    }

    private static Attribute attribute(Model model) {
        Class station = CoreFactory.eINSTANCE.createClass();
        station.setName("Station");
        model.getOwnedElement().add(station);
        Attribute temperature = null;
        for (String name : List.of("temperature", "validFrom", "previousCity", "currentCity")) {
            Attribute a = CoreFactory.eINSTANCE.createAttribute();
            a.setName(name);
            station.getFeature().add(a);
            temperature = temperature == null ? a : temperature;
        }
        return temperature;
    }

    private static Attribute feature(Class owner, String name) {
        return (Attribute) owner.getFeature().stream().filter(f -> name.equals(f.getName())).findFirst().orElseThrow();
    }

    private static int anchors(HistorisationRule rule) {
        return severity((v, d) -> v.validateHistorisationRule_KindHasItsAnchors(rule, d, new HashMap<>()));
    }

    private static int severity(BiPredicate<RecordingValidator, BasicDiagnostic> check) {
        BasicDiagnostic diagnostic = new BasicDiagnostic();
        check.test(RecordingValidator.INSTANCE, diagnostic);
        return diagnostic.getSeverity();
    }
}
