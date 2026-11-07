/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.governance.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleValidator;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Limits;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.PeriodStarts;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.RetentionSchedules;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Stages;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.ENamedElement;
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

/** The shape of the lifecycle metamodel, its OCL invariants, and schedules in an XMI file. */
class LifecycleModelTest {

    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    private static final LifecyclePackage P = LifecyclePackage.eINSTANCE;

    @BeforeAll
    static void oclDelegates() {
        OCLDelegateDomain.initialize(null);
        // outside Eclipse no plugin.xml registers the delegate domain; generated packages look it up globally
        DelegateDomain.Factory.Registry.INSTANCE.put(OCLConstants.OCL_DELEGATE_URI_LPG, new OCLDelegateDomainFactory());
    }

    @Test
    void classesAndTheirSupertypes() {
        List<EClass> classes = P.getEClassifiers().stream().filter(EClass.class::isInstance).map(EClass.class::cast)
                .toList();
        assertThat(classes).hasSize(19);
        assertThat(classes).filteredOn(EClass::isAbstract).extracting(ENamedElement::getName).containsExactlyInAnyOrder(
                "LifecycleRule", "PeriodStart", "Stage", "DestructiveStage", "TerminalStage", "Limit");
        assertThat(P.getLifecycleRule().getESuperTypes()).containsExactly(CorePackage.Literals.CONSTRAINT);
        assertThat(P.getRetentionSchedule().getESuperTypes()).containsExactly(P.getLifecycleRule());
        for (EClass start : List.of(P.getValueTime(), P.getFeatureTime(), P.getEventTime())) {
            assertThat(start.getESuperTypes()).as(start.getName()).containsExactly(P.getPeriodStart());
        }
        assertThat(P.getPeriodStart().getESuperTypes()).containsExactly(CorePackage.Literals.ELEMENT);
        assertThat(P.getStage().getESuperTypes()).containsExactly(CorePackage.Literals.MODEL_ELEMENT);
        assertThat(P.getDestructiveStage().getESuperTypes()).containsExactly(P.getStage());
        assertThat(P.getTerminalStage().getESuperTypes()).containsExactly(P.getDestructiveStage());
        assertThat(P.getDelete().getESuperTypes()).containsExactly(P.getTerminalStage());
        for (EClass destructive : List.of(P.getAggregate(), P.getTransfer())) {
            assertThat(destructive.getESuperTypes()).as(destructive.getName()).containsExactly(P.getDestructiveStage());
        }
        for (EClass kept : List.of(P.getTier(), P.getRestrict(), P.getReview(), P.getNotify())) {
            assertThat(kept.getESuperTypes()).as(kept.getName()).containsExactly(P.getStage());
        }
        assertThat(P.getLimit().getESuperTypes()).containsExactly(CorePackage.Literals.ELEMENT);
        assertThat(P.getCountLimit().getESuperTypes()).containsExactly(P.getLimit());
        assertThat(P.getSizeLimit().getESuperTypes()).containsExactly(P.getLimit());
    }

    @Test
    void enumerationsDataTypesAndDefaults() {
        assertThat(P.getEClassifiers()).filteredOn(EEnum.class::isInstance).extracting(ENamedElement::getName)
                .containsExactly("DataScope", "Alignment", "AggregateFunction");
        assertThat(P.getDataScope().getELiterals()).extracting(ENamedElement::getName).containsExactly("value", "row");
        assertThat(P.getAlignment().getELiterals()).extracting(ENamedElement::getName)
                .containsExactly("none", "endOfMonth", "endOfQuarter", "endOfYear");
        assertThat(P.getAggregateFunction().getELiterals()).hasSize(7);
        assertThat(P.getDuration().getInstanceClassName()).isEqualTo("java.lang.String");
        assertThat(P.getPeriod().getInstanceClassName()).isEqualTo("java.lang.String");
        assertThat(P.getLifecycleRule_Scope().getDefaultValue()).isEqualTo(DataScope.VALUE);
        assertThat(P.getPeriodStart_Alignment().getDefaultValue()).isEqualTo(Alignment.NONE);
    }

    @Test
    void packageDeclaresHiddenOppositesAndOclDelegates() {
        assertThat(P.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore/OCL").getDetails().get("hiddenOpposites"))
                .isEqualTo("true");
        assertThat(P.getEAnnotation("http://www.eclipse.org/emf/2002/Ecore").getDetails().get("validationDelegates"))
                .isEqualTo("http://www.eclipse.org/emf/2002/Ecore/OCL");
    }

    @Test
    void aScheduleOwnsWhatIsItsOwnAndPointsOneWayElsewhere() {
        List<String> containments = List.of("RetentionSchedule.start", "RetentionSchedule.stage", "RetentionSchedule.limit",
                "Stage.start");
        int oneWay = 0;
        for (EClassifier c : P.getEClassifiers()) {
            if (c instanceof EClass cls) {
                for (EReference r : cls.getEReferences()) {
                    String name = cls.getName() + "." + r.getName();
                    assertThat(r.getEOpposite()).as(name).isNull();
                    if (r.isContainment()) {
                        assertThat(containments).as(name).contains(name);
                        continue;
                    }
                    oneWay++;
                    assertThat(r.getEAnnotation(EMOF).getDetails().get("Property.oppositeRoleName"))
                            .as(name).startsWith("lifecycle");
                    assertThat(r.getEAnnotation(EMOF + "#Property.oppositeRoleName").getDetails().get("body"))
                            .as(name).startsWith("lifecycle");
                    assertThat(!r.isMany() || !r.isOrdered()).as(name + " is unordered").isTrue();
                }
            }
        }
        assertThat(oneWay).isEqualTo(8);
        assertThat(P.getRetentionSchedule_Stage().isOrdered()).isTrue();
        assertThat(P.getRetentionSchedule_Limit().isOrdered()).isFalse();
    }

    @Test
    void attributesUseCwmDataTypesOrTheirOwn() {
        for (EClassifier c : P.getEClassifiers()) {
            if (c instanceof EClass cls) {
                for (EAttribute a : cls.getEAttributes()) {
                    assertThat(a.getEAttributeType().getEPackage()).as(a.getName()).isIn(CorePackage.eINSTANCE, P);
                }
            }
        }
    }

    @Test
    void constrainsAnElement() {
        Shop s = new Shop();
        RetentionSchedule schedule = RetentionSchedules.values(s.rules, "keep", "P1Y", s.temperature);
        assertThat(severity((v, d) -> v.validateLifecycleRule_ConstrainsAnElement(schedule, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        schedule.getConstrainedElement().clear();
        assertThat(severity((v, d) -> v.validateLifecycleRule_ConstrainsAnElement(schedule, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void rowsNotOnFeature() {
        Shop s = new Shop();
        RetentionSchedule schedule = RetentionSchedules.rows(s.rules, "rows", "P1Y", PeriodStarts.valueTime(), s.station);
        assertThat(severity((v, d) -> v.validateLifecycleRule_RowsNotOnFeature(schedule, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        schedule.getConstrainedElement().add(s.temperature);
        assertThat(severity((v, d) -> v.validateLifecycleRule_RowsNotOnFeature(schedule, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
        schedule.setScope(DataScope.VALUE);
        assertThat(severity((v, d) -> v.validateLifecycleRule_RowsNotOnFeature(schedule, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
    }

    @Test
    void stageOrLimit() {
        Shop s = new Shop();
        RetentionSchedule byAge = RetentionSchedules.values(s.rules, "keep-1y", "P1Y", s.temperature);
        RetentionSchedule byCount = RetentionSchedules.keeping(s.rules, "keep-1000", 1000, s.temperature);
        RetentionSchedule neither = RetentionSchedules.create(s.rules, "neither", PeriodStarts.valueTime(), s.temperature);
        assertThat(severity((v, d) -> v.validateRetentionSchedule_StageOrLimit(byAge, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        assertThat(severity((v, d) -> v.validateRetentionSchedule_StageOrLimit(byCount, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        assertThat(severity((v, d) -> v.validateRetentionSchedule_StageOrLimit(neither, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void stagesHaveAStart() {
        Shop s = new Shop();
        RetentionSchedule schedule = RetentionSchedules.keeping(s.rules, "keep", 10, s.temperature);
        Stage delete = Stages.delete(schedule, "delete", "P1Y");
        assertThat(severity((v, d) -> v.validateRetentionSchedule_StagesHaveAStart(schedule, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
        Stages.from(delete, PeriodStarts.valueTime());
        assertThat(severity((v, d) -> v.validateRetentionSchedule_StagesHaveAStart(schedule, d, new HashMap<>())))
                .as("each stage its own").isEqualTo(Diagnostic.OK);
        delete.setStart(null);
        RetentionSchedules.startAt(schedule, PeriodStarts.valueTime());
        assertThat(severity((v, d) -> v.validateRetentionSchedule_StagesHaveAStart(schedule, d, new HashMap<>())))
                .as("the schedule's").isEqualTo(Diagnostic.OK);
    }

    @Test
    void atMostOneTerminalStage() {
        Shop s = new Shop();
        RetentionSchedule schedule = RetentionSchedules.values(s.rules, "keep", "P1Y", s.temperature);
        assertThat(severity((v, d) -> v.validateRetentionSchedule_AtMostOneTerminalStage(schedule, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        Stages.delete(schedule, "again", "P2Y");
        assertThat(severity((v, d) -> v.validateRetentionSchedule_AtMostOneTerminalStage(schedule, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
    }

    @Test
    void alignmentNeedsZone() {
        PeriodStart aligned = PeriodStarts.aligned(PeriodStarts.valueTime(), Alignment.END_OF_YEAR, "Europe/Berlin");
        assertThat(severity((v, d) -> v.validatePeriodStart_AlignmentNeedsZone(aligned, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
        aligned.setZone(null);
        assertThat(severity((v, d) -> v.validatePeriodStart_AlignmentNeedsZone(aligned, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
        aligned.setZone("");
        assertThat(severity((v, d) -> v.validatePeriodStart_AlignmentNeedsZone(aligned, d, new HashMap<>())))
                .isEqualTo(Diagnostic.ERROR);
        PeriodStart plain = PeriodStarts.valueTime();
        assertThat(severity((v, d) -> v.validatePeriodStart_AlignmentNeedsZone(plain, d, new HashMap<>())))
                .isEqualTo(Diagnostic.OK);
    }

    @Test
    void schedulesTravelInXmiAndAreFoundFromTheElementWithoutAnIndex(@TempDir Path dir) throws Exception {
        Shop s = new Shop();
        RetentionSchedule readings = RetentionSchedules.create(s.rules, "readings",
                PeriodStarts.aligned(PeriodStarts.feature(s.takenAt), Alignment.END_OF_MONTH, "Europe/Berlin"),
                s.station);
        Stages.tier(readings, "cold", "P30D", "cold", true);
        Stages.delete(readings, "delete", "P1Y");
        Limits.count(readings, 1000, s.temperature);
        RetentionSchedules.rows(s.rules, "rows", "P2Y", PeriodStarts.valueTime(), s.station);

        URI uri = URI.createFileURI(dir.resolve("weather.xmi").toString());
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
        Resource out = rs.createResource(uri);
        out.getContents().add(s.model);
        out.save(Map.of());
        String xmi = Files.readString(dir.resolve("weather.xmi"), StandardCharsets.UTF_8);
        assertThat(xmi).contains("xsi:type=\"lifecycle:RetentionSchedule\"", "<start xsi:type=\"lifecycle:FeatureTime\"",
                "alignment=\"endOfMonth\"", "<stage xsi:type=\"lifecycle:Tier\"", "<stage xsi:type=\"lifecycle:Delete\"",
                "<limit xsi:type=\"lifecycle:CountLimit\"", "scope=\"row\"");

        ResourceSet fresh = new ResourceSetImpl();
        fresh.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
        Model loaded = (Model) fresh.getResource(uri, true).getContents().get(0);
        Class station = (Class) loaded.getOwnedElement().stream().filter(Class.class::isInstance).findFirst().orElseThrow();
        Attribute t = (Attribute) station.getFeature().get(0);

        RetentionSchedule values = RetentionSchedules.effective(t, DataScope.VALUE).orElseThrow();
        assertThat(values.getName()).isEqualTo("readings");
        assertThat(values.getStage()).extracting(Stage::getName).containsExactly("cold", "delete");
        assertThat(((FeatureTime) values.getStart()).getFeature().getName()).isEqualTo("takenAt");
        assertThat(((CountLimit) values.getLimit().get(0)).getKey()).extracting(k -> k.getName())
                .containsExactly("temperature");
        assertThat(values.getBody().getBody()).isEqualTo("from takenAt, end of month in Europe/Berlin: tier cold "
                + "compressed after P30D, delete after P1Y; keep the newest 1000 per temperature");
        assertThat(RetentionSchedules.effective(t, DataScope.ROW).orElseThrow().getName()).isEqualTo("rows");
    }

    /** Model {@code weather} › class {@code Station} (temperature, takenAt), rules in the package {@code rules}. */
    private static final class Shop {
        final Model model = CoreFactory.eINSTANCE.createModel();
        final Package rules = CoreFactory.eINSTANCE.createPackage();
        final Class station = CoreFactory.eINSTANCE.createClass();
        final Attribute temperature = CoreFactory.eINSTANCE.createAttribute();
        final Attribute takenAt = CoreFactory.eINSTANCE.createAttribute();

        Shop() {
            model.setName("weather");
            rules.setName("rules");
            station.setName("Station");
            temperature.setName("temperature");
            takenAt.setName("takenAt");
            model.getOwnedElement().add(station);
            model.getOwnedElement().add(rules);
            station.getFeature().add(temperature);
            station.getFeature().add(takenAt);
        }
    }

    private static int severity(BiPredicate<LifecycleValidator, BasicDiagnostic> check) {
        BasicDiagnostic diagnostic = new BasicDiagnostic();
        check.test(LifecycleValidator.INSTANCE, diagnostic);
        return diagnostic.getSeverity();
    }
}
