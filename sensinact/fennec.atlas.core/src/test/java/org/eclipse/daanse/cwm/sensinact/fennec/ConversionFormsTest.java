/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.sensinact.fennec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.cwm.emf.standalone.EcoreIndex;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ChangeableKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Expressions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AggregationKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Association;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.RelationshipsFactory;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Limits;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.PeriodStarts;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.RetentionSchedules;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Stages;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.RetentionHold;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.LegalSources;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.RetentionHolds;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.util.RecordingRules;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.PathSource;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.ClassModels;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.examples.Examples;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.AdminMappings;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.ProviderMappings;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.ResourceMappings;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.SensinactBindings;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.ServiceMappings;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.ValueSources;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Provider;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Resource;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Service;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.Providers;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.Resources;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.Services;
import org.eclipse.daanse.cwm.model.daanse.units.Quantity;
import org.eclipse.daanse.cwm.model.daanse.units.SystemOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.examples.ExampleUnits;
import org.eclipse.daanse.cwm.model.daanse.units.util.Quantities;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.fennec.event.atlas.model.mapping.AbsoluteChangeRule;
import org.eclipse.fennec.event.atlas.model.mapping.DurationUnit;
import org.eclipse.fennec.event.atlas.model.mapping.MappingPackage;
import org.eclipse.fennec.event.atlas.model.mapping.ProfileResource;
import org.eclipse.fennec.event.atlas.model.mapping.ProfileService;
import org.eclipse.fennec.event.atlas.model.mapping.ProviderMapping;
import org.eclipse.fennec.event.atlas.model.mapping.ResourceMapping;
import org.eclipse.fennec.event.atlas.model.mapping.TimeThrottleChangeRule;
import org.eclipse.fennec.event.atlas.model.mapping.TimestampStrategy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * One test per form of the conversion that the corpus does not show, and every rule of the notes:
 * timestamps from literals and formats, profiles, units from quantities, initial value and
 * changeability, extra metadata and description, association ends in paths, admin description, the
 * data type rules, the lifecycle rules Fennec cannot carry, literals, validation.
 */
class ConversionFormsTest {

    private static final Set<String> SEEN = new HashSet<>();

    private final Examples.Case battery = Corpus.BATTERY.build();
    private final List<EPackage> packages = AtlasGenerator.loadEcore(Corpus.BATTERY.ecoreFiles());

    @AfterAll
    static void everyRuleOfTheNotesIsReached() {
        assertThat(SEEN).containsAll(AtlasNotes.ALL);
    }

    private CwmToAtlas.Result convert(Options options) {
        return CwmToAtlas.convert(battery.mappings(), EcoreIndex.of(packages), options);
    }

    private static List<Note> notes(CwmToAtlas.Result result, String rule) {
        List<Note> notes = result.notes().stream().filter(n -> n.rule().equals(rule)).toList();
        if (!notes.isEmpty()) {
            SEEN.add(rule);
        }
        return notes;
    }

    private ResourceMapping level(CwmToAtlas.Result result) {
        return result.mappings().get(0).getServices().get(0).getResources().get(0);
    }

    private Resource levelResource() {
        return battery.resource("battery-sensor", "battery", "level");
    }

    @Test
    void timestampsFromALiteralAndAFormat() {
        var dragino = battery.mapping("dragino-battery-sensor");
        dragino.setTimestamp(ValueSources.literal("2026-11-06T08:00:00Z"));
        var em310 = battery.mapping("em310udl-battery-sensor");
        ValueSources.format((PathSource) em310.getTimestamp(), "yyyy-MM-dd'T'HH:mm:ssX");
        CwmToAtlas.Result result = convert(Options.defaults());
        var literal = result.mappings().get(0).getTimestamp();
        assertThat(literal.getTimestamp()).isEqualTo("2026-11-06T08:00:00Z");
        assertThat(literal.getFeaturePath()).isEmpty();
        assertThat(literal.eIsSet(MappingPackage.Literals.TIMESTAMP_MAPPING__STRATEGY)).isFalse();
        var formatted = result.mappings().get(1).getTimestamp();
        assertThat(formatted.getStrategy()).isEqualTo(TimestampStrategy.FEATURE);
        assertThat(formatted.getHint()).isEqualTo("yyyy-MM-dd'T'HH:mm:ssX");
        assertThat(level(result).getTimestamp()).isSameAs(literal);
    }

    @Test
    void withoutAnyTimestampEveryResourceRefersToANowTimestamp() {
        battery.mapping("dragino-battery-sensor").setTimestamp(null);
        CwmToAtlas.Result result = convert(Options.defaults());
        ProviderMapping dragino = result.mappings().get(0);
        assertThat(dragino.getTimestamp().getStrategy()).isEqualTo(TimestampStrategy.NOW);
        assertThat(level(result).getTimestamp()).isSameAs(dragino.getTimestamp());
    }

    @Test
    void oneUnifiedProfilePerProviderWithItsStructure() {
        Service status = Services.optional(battery.provider("battery-sensor"), "status");
        Resources.optional(status, "signal", (DataType) levelResource().getType());
        CwmToAtlas.Result result = convert(Options.defaults());
        assertThat(result.profiles()).hasSize(1);
        var profile = result.profiles().get(0);
        assertThat(result.mappings()).allMatch(m -> m.getProfile() == profile);
        assertThat(profile.getProfileId()).isEqualTo("battery-sensor");
        ProfileService service = profile.getProvider().getServices().get(0);
        assertThat(service.getServiceId()).isEqualTo("battery");
        assertThat(service.getServiceName()).isEqualTo("Battery");
        assertThat(service.isRequired()).isTrue();
        ProfileResource resource = service.getResources().get(0);
        assertThat(resource.getResourceId()).isEqualTo("level");
        assertThat(resource.getExpectedType()).isSameAs(EcorePackage.Literals.EDOUBLE);
        assertThat(resource.getExpectedUnit()).isEqualTo("V");
        assertThat(profile.getProvider().getServices().get(1).isRequired()).isFalse();
        assertThat(profile.getProvider().getServices().get(1).getResources().get(0).isRequired()).isFalse();
        assertThat(profile.getProvider().getAdmin().isRequiresFriendlyName()).isTrue();
        assertThat(profile.getProvider().getAdmin().isRequiresLocation()).isFalse();
    }

    @Test
    void theUnitIsTheSymbolOfTheUnitOfTheQuantity() {
        CwmToAtlas.Result result = convert(Options.defaults());
        assertThat(level(result).getUnit()).isEqualTo("V");
        assertThat(result.mappings().get(1).getServices().get(0).getResources().get(0).getUnit()).isEqualTo("V");
        assertThat(Quantities.of(levelResource())).get().extracting(q -> q.getUnit().getName()).isEqualTo("volt");
    }

    @Test
    void aResourceWithoutAQuantityOfItsOwnHasTheUnitOfItsDataType() {
        Examples.Case plain = Examples.battery();
        SystemOfUnits units = Corpus.units(plain);
        Resource level = plain.resource("battery-sensor", "battery", "level");
        Quantities.create(units.getNamespace(), "doubles", ExampleUnits.unit(units, "mW"), level.getType());
        CwmToAtlas.Result result = CwmToAtlas.convert(plain.mappings(), EcoreIndex.of(packages), Options.defaults());
        assertThat(level(result).getUnit()).isEqualTo("mW");
        assertThat(expectedUnit(result)).isEqualTo("mW");
    }

    @Test
    void aResourceWithoutAnyQuantityHasNoUnit() {
        Examples.Case plain = Examples.battery();
        CwmToAtlas.Result result = CwmToAtlas.convert(plain.mappings(), EcoreIndex.of(packages), Options.defaults());
        assertThat(level(result).getUnit()).isNull();
        assertThat(expectedUnit(result)).isNull();
    }

    @Test
    void aFindingOfTheUnitsValidatorIsANoteWhenLenient() {
        Quantity volts = Quantities.of(levelResource()).orElseThrow();
        Quantities.create(volts.getNamespace(), "levelAgain", volts.getUnit(), levelResource());
        CwmToAtlas.Result result = CwmToAtlas.convert(battery.mappings(), EcoreIndex.of(packages), Options.defaults(),
                true);
        assertThat(notes(result, AtlasNotes.VALIDATION)).filteredOn(n -> n.message().startsWith("U"))
                .singleElement().satisfies(n -> {
                    assertThat(n.severity()).isEqualTo(Note.Severity.ERROR);
                    assertThat(n.message()).startsWith("U3 ");
                });
        assertThat(level(result).getUnit()).isEqualTo("V");
    }

    private static String expectedUnit(CwmToAtlas.Result result) {
        return result.profiles().get(0).getProvider().getServices().get(0).getResources().get(0).getExpectedUnit();
    }

    @Test
    void withoutProfilesEveryMappingMakesItsOwnModel() {
        CwmToAtlas.Result result = convert(Options.defaults().withProfiles(false));
        assertThat(result.profiles()).isEmpty();
        assertThat(result.mappings()).allMatch(m -> m.getProfile() == null);
        assertThat(notes(result, AtlasNotes.PROFILE)).singleElement().asString().contains("2 mappings onto provider");
    }

    @Test
    void aSelectorPicksAndGeneratesNothing() {
        Examples.Case weather = Examples.weather();
        var result = CwmToAtlas.convert(weather.mappings(), EcoreIndex.of(AtlasGenerator.loadEcore(Corpus.WEATHER.ecoreFiles())),
                Options.defaults().withProfiles(false));
        var selector = result.mappings().get(0).getServices().get(1).getReferencedResource();
        assertThat(selector.isExclude()).isFalse();
        assertThat(selector.getFilter()).isEmpty();
        assertThat(selector.getCollectionIndex()).isEqualTo(1);
        assertThat(selector.getFeaturePath()).extracting(f -> f.getName()).containsExactly("reports");
    }

    @Test
    void initialValueChangeabilityMetadataAndDescription() {
        Resource level = levelResource();
        level.setInitialValue(Expressions.expression("literal", "3.3"));
        level.setChangeability(ChangeableKind.CK_FROZEN);
        TaggedValues.set(level, "sensorthings.unit.name", "volt");
        TaggedValues.set(level, "daanse.note", "not exported");
        Descriptions.describe(level, "documentation", "en", "Battery voltage");
        ResourceMapping out = level(convert(Options.defaults()));
        assertThat(out.getDefaultValueLiteral()).isEqualTo("3.3");
        assertThat(out.isChangeable()).isFalse();
        assertThat(out.getExtraMetadata().map()).containsExactly(Map.entry("sensorthings.unit.name", "volt"));
        assertThat(EcoreUtil.getAnnotation(out, "sensinact.mapping", "sensinact.mapping.description"))
                .isEqualTo("Battery voltage");
    }

    @Test
    void aLiteralValueBecomesTheInitialValue() {
        var sm = SensinactBindings.serviceMappings(battery.mapping("dragino-battery-sensor")).get(0);
        SensinactBindings.resourceMappings(sm).get(0).setValue(ValueSources.literal("3.0"));
        CwmToAtlas.Result result = convert(Options.defaults());
        assertThat(level(result).getValueFeature()).isEmpty();
        assertThat(level(result).getDefaultValueLiteral()).isEqualTo("3.0");
        assertThat(notes(result, AtlasNotes.LITERAL)).singleElement()
                .satisfies(n -> assertThat(n.severity()).isEqualTo(Note.Severity.INFO));
    }

    @Test
    void whatFennecCannotPickOrFallBackToIsNoted() {
        var sm = SensinactBindings.serviceMappings(battery.mapping("dragino-battery-sensor")).get(0);
        var rm = SensinactBindings.resourceMappings(sm).get(0);
        ValueSources.orElse((PathSource) rm.getValue(), "0.0");
        var admin = battery.mapping("em310udl-battery-sensor").getAdmin();
        AdminMappings.location(admin, ValueSources.literal("north"), ValueSources.literal("8.5"));
        CwmToAtlas.Result result = convert(Options.defaults());
        assertThat(notes(result, AtlasNotes.LITERAL)).extracting(Note::message).anySatisfy(m -> assertThat(m)
                .contains("no fallback for a resource value")).anySatisfy(m -> assertThat(m).contains("'north' is no number"));
        assertThat(result.mappings().get(1).getAdmin().getLongitude()).isEqualTo(8.5);
    }

    @Test
    void aValuePathPickingAnotherElementThanTheFirstIsNoted() {
        Examples.Case c = Examples.battery();
        var dragino = c.mapping("dragino-battery-sensor");
        AdminMappings.description(dragino.getAdmin(), ValueSources.at(ValueSources.path(
                ValueSources.steps(dragino.getTimestamp()).toArray(new org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature[0])), 1));
        var result = CwmToAtlas.convert(c.mappings(), EcoreIndex.of(packages), Options.defaults());
        assertThat(notes(result, AtlasNotes.PATH)).singleElement().asString().contains("rxInfo[1].time");
        var description = result.mappings().get(0).getAdmin().getResources().get(0);
        assertThat(description.getMid()).isEqualTo("description");
        assertThat(description.getEType()).isSameAs(EcorePackage.Literals.ESTRING);
        assertThat(description.getValueFeature()).extracting(f -> f.getName()).containsExactly("rxInfo", "time");
        assertThat(description.getTimestamp()).isSameAs(result.mappings().get(0).getTimestamp());
    }

    @Test
    void aMappingWithoutAdminGetsAnEmptyOne() {
        battery.mapping("em310udl-battery-sensor").setAdmin(null);
        battery.provider("battery-sensor").setFriendlyNameRequired(false);
        CwmToAtlas.Result result = convert(Options.defaults());
        assertThat(result.mappings().get(1).getAdmin().getMid()).isEqualTo("admin");
        assertThat(notes(result, AtlasNotes.ADMIN)).singleElement().asString().contains("an empty one is written");
    }

    @Test
    void anAssociationEndIsTheFeatureAtTheOtherEndsClass() {
        // CWM: Order -- customer: Customer (navigable); Ecore: Order.customer, Customer.name
        var model = ClassModels.model("shop");
        Package shop = ClassModels.pkg(model, "shop");
        DataType string = ClassModels.type(model, "EString");
        Class order = ClassModels.cls(shop, "Order");
        Class customer = ClassModels.cls(shop, "Customer");
        var name = ClassModels.attr(customer, "name", string);
        Association placed = RelationshipsFactory.eINSTANCE.createAssociation();
        placed.setName("Placed");
        AssociationEnd orderEnd = end("order", order, false);
        AssociationEnd customerEnd = end("customer", customer, true);
        placed.getFeature().add(orderEnd);
        placed.getFeature().add(customerEnd);
        shop.getOwnedElement().add(placed);
        Provider p = Providers.create(model, "orders");
        Service s = Services.create(p, "order");
        Resource buyer = Resources.create(s, "buyer", string);
        var pm = ProviderMappings.create(model, "orders", p, ValueSources.literal("order"), order);
        ResourceMappings.create(ServiceMappings.create(pm, s), buyer, ValueSources.path(customerEnd, name));

        EPackage eShop = EcoreFactory.eINSTANCE.createEPackage();
        eShop.setName("shop");
        eShop.setNsURI("http://example.org/shop");
        EClass eOrder = EcoreFactory.eINSTANCE.createEClass();
        eOrder.setName("Order");
        EClass eCustomer = EcoreFactory.eINSTANCE.createEClass();
        eCustomer.setName("Customer");
        EReference eCustomerRef = EcoreFactory.eINSTANCE.createEReference();
        eCustomerRef.setName("customer");
        eCustomerRef.setEType(eCustomer);
        eOrder.getEStructuralFeatures().add(eCustomerRef);
        EAttribute eName = EcoreFactory.eINSTANCE.createEAttribute();
        eName.setName("name");
        eName.setEType(EcorePackage.Literals.ESTRING);
        eCustomer.getEStructuralFeatures().add(eName);
        eShop.getEClassifiers().addAll(List.of(eOrder, eCustomer));

        var result = CwmToAtlas.convert(List.of(pm), EcoreIndex.of(List.of(eShop)), Options.defaults());
        assertThat(result.mappings().get(0).getServices().get(0).getResources().get(0).getValueFeature())
                .containsExactly(eCustomerRef, eName);
        assertThat(result.mappings().get(0).getProviderClasses()).containsExactly(eOrder);
    }

    @Test
    void theDataTypeRules() {
        var model = battery.model();
        Service extra = Services.optional(battery.provider("battery-sensor"), "extra");
        Resource cwmFloat = Resources.optional(extra, "cwmFloat", ClassModels.type(model, "Float"));
        Resource cwmInteger = Resources.optional(extra, "cwmInteger", ClassModels.type(model, "Integer"));
        Resource unknown = Resources.optional(extra, "unknown", ClassModels.type(model, "Geometry"));
        Resource enumeration = Resources.optional(extra, "state", ClassModels.enumeration(model, "State", "ON", "OFF"));
        var sm = ServiceMappings.create(battery.mapping("dragino-battery-sensor"), extra);
        var batV = ValueSources.steps(SensinactBindings.resourceMappings(
                SensinactBindings.serviceMappings(battery.mapping("dragino-battery-sensor")).get(0)).get(0).getValue());
        for (Resource r : List.of(cwmFloat, cwmInteger, unknown, enumeration)) {
            ResourceMappings.create(sm, r, ValueSources.path(batV.toArray(new org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature[0])));
        }
        CwmToAtlas.Result result = convert(Options.defaults());
        var resources = result.mappings().get(0).getServices().get(1).getResources();
        assertThat(resources.get(0).getEType()).isSameAs(EcorePackage.Literals.EDOUBLE);
        assertThat(resources.get(1).getEType()).isSameAs(EcorePackage.Literals.ELONG);
        assertThat(resources.get(2).getEType()).isSameAs(EcorePackage.Literals.ESTRING);
        assertThat(resources.get(3).getEType()).isSameAs(EcorePackage.Literals.ESTRING);
        List<Note> types = notes(result, AtlasNotes.DATA_TYPE);
        assertThat(types).extracting(Note::severity).containsExactly(Note.Severity.WARNING, Note.Severity.INFO);

        CwmToAtlas.Result overridden = convert(Options.defaults().withDataTypes(Map.of("Float", "EFloat", "Geometry", "EPoint")));
        var again = overridden.mappings().get(0).getServices().get(1).getResources();
        assertThat(again.get(0).getEType()).isSameAs(EcorePackage.Literals.EFLOAT);
        assertThat(notes(overridden, AtlasNotes.DATA_TYPE)).filteredOn(n -> n.severity() == Note.Severity.ERROR)
                .singleElement().asString().contains("EPoint");
    }

    @Test
    void theLifecycleRulesOfAResource() {
        Package rules = ClassModels.pkg(battery.model(), "rules");
        Resource level = levelResource();
        RecordingRules.absolute(rules, "abs-1", 0.1, level);
        Limits.count(RetentionSchedules.cleanupEvery(RetentionSchedules.values(rules, "keep", "PT90M",
                battery.provider("battery-sensor")), Duration.ofSeconds(90)), 500);
        CwmToAtlas.Result result = convert(Options.defaults());
        ResourceMapping out = level(result);
        assertThat(out.getChangeRule()).isInstanceOfSatisfying(AbsoluteChangeRule.class, r -> {
            assertThat(r.getDelta()).isEqualTo(0.1);
            assertThat(r.getId()).isEqualTo("abs-1");
        });
        assertThat(out.getDeletionRule().getRetention()).isEqualTo(90);
        assertThat(out.getDeletionRule().getRetentionUnit()).isEqualTo(DurationUnit.MINUTES);
        assertThat(out.getDeletionRule().getCleanupInterval()).isEqualTo(90);
        assertThat(out.getDeletionRule().getCleanupIntervalUnit()).isEqualTo(DurationUnit.SECONDS);
        assertThat(out.getDeletionRule().getMaxCount()).isEqualTo(500);
        assertThat(notes(result, AtlasNotes.RULE)).isEmpty();
        // the rule is copied per resource: the second mapping's resource has its own
        assertThat(result.mappings().get(1).getServices().get(0).getResources().get(0).getChangeRule())
                .isNotSameAs(out.getChangeRule()).isNotNull();
    }

    @Test
    void lifecycleRulesFennecCannotCarry() {
        Package rules = ClassModels.pkg(battery.model(), "rules");
        RecordingRules.changedValue(rules, "changes", levelResource());
        RetentionSchedules.keeping(rules, "keep-100", 100, levelResource());
        CwmToAtlas.Result result = convert(Options.defaults());
        assertThat(level(result).getChangeRule()).isNull();
        assertThat(level(result).getDeletionRule()).isNull();
        assertThat(notes(result, AtlasNotes.RULE))
                .anySatisfy(n -> {
                    assertThat(n.severity()).isEqualTo(Note.Severity.ERROR);
                    assertThat(n.message()).endsWith("changed values only through its other rules");
                })
                .anySatisfy(n -> {
                    assertThat(n.severity()).isEqualTo(Note.Severity.ERROR);
                    assertThat(n.message()).contains("needs a retention");
                });
    }

    @Test
    void aRangeRuleIsWrittenAsAnAbsoluteOne() {
        Package rules = ClassModels.pkg(battery.model(), "rules");
        RecordingRules.range(rules, "level-2pct", 2.0, 2.5, 4.2, levelResource());
        CwmToAtlas.Result result = convert(Options.defaults());
        assertThat(level(result).getChangeRule()).isInstanceOfSatisfying(AbsoluteChangeRule.class, r -> {
            assertThat(r.getDelta()).isEqualTo(0.034);
            assertThat(r.getId()).isEqualTo("level-2pct");
        });
        assertThat(notes(result, AtlasNotes.RULE)).first().satisfies(n -> {
            assertThat(n.severity()).isEqualTo(Note.Severity.INFO);
            assertThat(n.message()).endsWith("a change of 2.0 % of the range from 2.5 to 4.2 is written as an "
                    + "absolute change of 0.034");
        });
    }

    @Test
    void whatFennecCannotCarryIsAnError() {
        Resource level = levelResource();
        Resource quality = Resources.create((Service) level.getOwner(), "quality", level.getType());
        quality.setRequired(false);
        Package rules = ClassModels.pkg(battery.model(), "rules");
        var rule = RecordingRules.interpolation(RecordingRules.lateValues(RecordingRules.status(
                RecordingRules.minimumInterval(RecordingRules.heartbeat(RecordingRules.absolute(rules, "abs-1", 0.1,
                        level), Duration.ofHours(1)), Duration.ofMinutes(1)), quality),
                org.eclipse.daanse.cwm.model.daanse.governance.recording.LateValueHandling.INSERT),
                org.eclipse.daanse.cwm.model.daanse.governance.recording.Interpolation.STEPPED);

        CwmToAtlas.Result strict = convert(Options.defaults());
        assertThat(notes(strict, AtlasNotes.RULE)).hasSize(5).allSatisfy(n -> {
            assertThat(n.severity()).isEqualTo(Note.Severity.ERROR);
            assertThat(n.message()).startsWith("rule abs-1: Fennec's mapping model has no ");
        });
        assertThat(notes(strict, AtlasNotes.RULE)).extracting(Note::message).containsExactly(
                "rule abs-1: Fennec's mapping model has no heartbeat (maximumInterval PT1H)",
                "rule abs-1: Fennec's mapping model has no minimum interval beside a throttle (minimumInterval PT1M)",
                "rule abs-1: Fennec's mapping model has no status feature (quality)",
                "rule abs-1: Fennec's mapping model has no handling of late values but its rules' (lateValues insert)",
                "rule abs-1: Fennec's mapping model has no interpolation (stepped)");

        CwmToAtlas.Result lenient = CwmToAtlas.convert(battery.mappings(), EcoreIndex.of(packages), Options.defaults(),
                true);
        assertThat(notes(lenient, AtlasNotes.RULE)).hasSize(5)
                .allSatisfy(n -> assertThat(n.severity()).isEqualTo(Note.Severity.WARNING))
                .anySatisfy(n -> assertThat(n.message()).endsWith("(maximumInterval PT1H); it is left out"))
                .anySatisfy(n -> assertThat(n.message()).endsWith("(lateValues insert); late values are compared"));
        assertThat(level(lenient).getChangeRule()).as("the dead band stays")
                .isInstanceOfSatisfying(AbsoluteChangeRule.class, r -> assertThat(r.getDelta()).isEqualTo(0.1));
        assertThat(rule.getName()).isEqualTo("abs-1");
    }

    @Test
    void aCalendarPeriodIsAnError() {
        Package rules = ClassModels.pkg(battery.model(), "rules");
        RetentionSchedules.values(rules, "keep-1y", "P1Y", levelResource());
        CwmToAtlas.Result strict = convert(Options.defaults());
        assertThat(level(strict).getDeletionRule()).isNull();
        assertThat(notes(strict, AtlasNotes.RULE)).singleElement().satisfies(n -> {
            assertThat(n.severity()).isEqualTo(Note.Severity.ERROR);
            assertThat(n.message()).isEqualTo("schedule keep-1y: delete after P1Y is no exact period; Fennec's "
                    + "retention is a whole number of days or less");
        });
        CwmToAtlas.Result lenient = CwmToAtlas.convert(battery.mappings(), EcoreIndex.of(packages), Options.defaults(),
                true);
        assertThat(notes(lenient, AtlasNotes.RULE)).singleElement()
                .satisfies(n -> assertThat(n.message()).endsWith("; no deletion rule is written"));
    }

    @Test
    void aStartOtherThanTheTimeOfTheValueIsAnError() {
        Package rules = ClassModels.pkg(battery.model(), "rules");
        RetentionSchedules.startAt(RetentionSchedules.values(rules, "keep-90d", "P90D", levelResource()),
                PeriodStarts.aligned(PeriodStarts.valueTime(),
                        org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Alignment.END_OF_MONTH, "Europe/Berlin"));
        CwmToAtlas.Result strict = convert(Options.defaults());
        assertThat(notes(strict, AtlasNotes.RULE)).singleElement().satisfies(n -> {
            assertThat(n.severity()).isEqualTo(Note.Severity.ERROR);
            assertThat(n.message()).isEqualTo("schedule keep-90d: Fennec counts from the time of a value, not from the "
                    + "time of the value, end of month in Europe/Berlin");
        });
        CwmToAtlas.Result lenient = CwmToAtlas.convert(battery.mappings(), EcoreIndex.of(packages), Options.defaults(),
                true);
        assertThat(level(lenient).getDeletionRule().getRetention()).as("counted from the time of the value").isEqualTo(90);
    }

    @Test
    void stagesAndLimitsBesideTheDeleteAreErrors() {
        Package rules = ClassModels.pkg(battery.model(), "rules");
        Resource level = levelResource();
        var schedule = RetentionSchedules.create(rules, "level-history", PeriodStarts.valueTime(), level);
        Stages.tier(schedule, "cold", "P30D", "cold", true);
        Stages.delete(schedule, "delete", "P90D");
        Limits.count(schedule, 1000, level);
        Limits.size(schedule, 1_000_000);
        RetentionSchedules.maxDelete(schedule, 500);
        CwmToAtlas.Result strict = convert(Options.defaults());
        assertThat(notes(strict, AtlasNotes.RULE)).extracting(Note::severity).containsOnly(Note.Severity.ERROR);
        assertThat(notes(strict, AtlasNotes.RULE)).extracting(Note::message).containsExactly(
                "schedule level-history: Fennec's deletion rule has no stage tier cold compressed after P30D",
                "schedule level-history: Fennec's deletion rule has no count per key",
                "schedule level-history: Fennec's deletion rule has no limit of size",
                "schedule level-history: Fennec's deletion rule has no cap per cleanup (maxDelete 500)");
        CwmToAtlas.Result lenient = CwmToAtlas.convert(battery.mappings(), EcoreIndex.of(packages), Options.defaults(),
                true);
        assertThat(notes(lenient, AtlasNotes.RULE)).hasSize(4)
                .allSatisfy(n -> assertThat(n.message()).endsWith("; it is left out"));
        assertThat(level(lenient).getDeletionRule()).satisfies(d -> {
            assertThat(d.getRetention()).isEqualTo(90);
            assertThat(d.getMaxCount()).as("a count per key is no maxCount").isNull();
            assertThat(d.getId()).isEqualTo("level-history");
        });
    }

    @Test
    void aSwingingDoorAHistoryAndRowsAreErrors() {
        Package rules = ClassModels.pkg(battery.model(), "rules");
        RecordingRules.swingingDoor(rules, "door", 0.05, levelResource());
        org.eclipse.daanse.cwm.model.daanse.governance.recording.util.HistorisationRules.values(rules, "overwrite",
                org.eclipse.daanse.cwm.model.daanse.governance.recording.HistorisationKind.OVERWRITE,
                battery.provider("battery-sensor"));
        RetentionSchedules.rows(rules, "last-1000-rows", "P1D", PeriodStarts.valueTime(),
                battery.provider("battery-sensor"));
        CwmToAtlas.Result result = convert(Options.defaults());
        assertThat(level(result).getChangeRule()).isNull();
        assertThat(level(result).getDeletionRule()).as("a rule of rows is no rule of values").isNull();
        assertThat(notes(result, AtlasNotes.RULE)).allSatisfy(n -> assertThat(n.severity()).isEqualTo(Note.Severity.ERROR))
                .extracting(Note::message)
                .contains("rule door: Fennec's mapping model has no swinging door",
                        "schedule last-1000-rows keeps rows; Fennec keeps the values of each resource",
                        "rule overwrite: Fennec keeps every recorded value of a resource as a version of its own, not "
                                + "overwrite");
    }

    @Test
    void aNewVersionPerValueIsWhatFennecDoes() {
        Package rules = ClassModels.pkg(battery.model(), "rules");
        org.eclipse.daanse.cwm.model.daanse.governance.recording.util.HistorisationRules.values(rules, "versions",
                org.eclipse.daanse.cwm.model.daanse.governance.recording.HistorisationKind.NEW_VERSION, levelResource());
        assertThat(notes(convert(Options.defaults()), AtlasNotes.RULE)).isEmpty();
    }

    @Test
    void theStartOfTheDeleteIsItsOwnWhereItHasOne() {
        Package rules = ClassModels.pkg(battery.model(), "rules");
        var schedule = RetentionSchedules.create(rules, "keep-90d",
                PeriodStarts.aligned(PeriodStarts.valueTime(),
                        org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Alignment.END_OF_YEAR, "Europe/Berlin"),
                levelResource());
        Stages.from(Stages.delete(schedule, "delete", "P90D"), PeriodStarts.valueTime());
        CwmToAtlas.Result result = convert(Options.defaults());
        assertThat(notes(result, AtlasNotes.RULE)).isEmpty();
        assertThat(level(result).getDeletionRule().getRetention()).isEqualTo(90);
    }

    @Test
    void aHoldThatHasNotEndedKeepsTheDeletionRuleBack() {
        Package rules = ClassModels.pkg(battery.model(), "rules");
        RetentionSchedules.values(rules, "keep-90d", "P90D", levelResource());
        RetentionHold hold = RetentionHolds.create(rules, "dispute", "warranty dispute",
                Instant.parse("2026-11-01T00:00:00Z"), levelResource().getOwner());
        LegalSources.cite(LegalSources.create(rules, "contract", "https://example.org/contract", LegalSources.CONTRACT),
                hold);
        CwmToAtlas.Result strict = convert(Options.defaults());
        assertThat(level(strict).getDeletionRule()).isNull();
        assertThat(notes(strict, AtlasNotes.RULE)).singleElement().satisfies(n -> {
            assertThat(n.severity()).isEqualTo(Note.Severity.ERROR);
            assertThat(n.message()).isEqualTo("hold dispute (warranty dispute) is not over; Fennec's deletion rule "
                    + "cannot wait for it");
        });
        CwmToAtlas.Result lenient = CwmToAtlas.convert(battery.mappings(), EcoreIndex.of(packages), Options.defaults(),
                true);
        assertThat(level(lenient).getDeletionRule()).as("the data is kept").isNull();
        assertThat(notes(lenient, AtlasNotes.RULE)).singleElement()
                .satisfies(n -> assertThat(n.message()).endsWith("; no deletion rule is written"));

        RetentionHolds.release(hold, Instant.parse("2026-11-03T00:00:00Z"));
        CwmToAtlas.Result released = convert(Options.defaults());
        assertThat(notes(released, AtlasNotes.RULE)).isEmpty();
        assertThat(level(released).getDeletionRule().getRetention()).isEqualTo(90);
        assertThat(notes(convert(Options.defaults().withAt(Instant.parse("2026-11-02T00:00:00Z"))), AtlasNotes.RULE))
                .as("written for a time the hold lasts").hasSize(1);
        assertThat(notes(convert(Options.defaults().withAt(Instant.parse("2026-10-01T00:00:00Z"))), AtlasNotes.RULE))
                .as("written for a time before the hold: it is to come").hasSize(1);
    }

    @Test
    void aThrottleOfHoursAndMinutes() {
        Package rules = ClassModels.pkg(battery.model(), "rules");
        RecordingRules.throttle(rules, "throttle", Duration.ofMinutes(90), levelResource());
        assertThat(level(convert(Options.defaults())).getChangeRule()).isInstanceOfSatisfying(TimeThrottleChangeRule.class,
                r -> {
                    assertThat(r.getInterval()).isEqualTo(90);
                    assertThat(r.getIntervalUnit()).isEqualTo(DurationUnit.MINUTES);
                });
    }

    @Test
    void twoMappingsOfOneNameAreAnError() {
        battery.mapping("em310udl-battery-sensor").setName("dragino-battery-sensor");
        CwmToAtlas.Result result = CwmToAtlas.convert(battery.mappings(), EcoreIndex.of(packages), Options.defaults(), true);
        assertThat(notes(result, AtlasNotes.NAME)).singleElement()
                .satisfies(n -> assertThat(n.severity()).isEqualTo(Note.Severity.ERROR));
    }

    @Test
    void unresolvedClassesAndFeatures() {
        CwmToAtlas.Result result = CwmToAtlas.convert(battery.mappings(),
                EcoreIndex.of(AtlasGenerator.loadEcore(List.of(Corpus.resources().resolve("ecore/lorawan-uplink.ecore")))),
                Options.defaults());
        assertThat(notes(result, AtlasNotes.UNRESOLVED)).isNotEmpty().allMatch(n -> n.severity() == Note.Severity.ERROR);
    }

    @Test
    void leniently_validationFindingsAreNotesWithTheElementAndTheRule() {
        levelResource().setType(null);
        CwmToAtlas.Result result = CwmToAtlas.convert(battery.mappings(), EcoreIndex.of(packages), Options.defaults(), true);
        List<Note> validation = notes(result, AtlasNotes.VALIDATION);
        assertThat(validation).filteredOn(n -> n.severity() == Note.Severity.ERROR).singleElement().satisfies(n -> {
            assertThat(n.message()).isEqualTo("P2 resource battery.providers.battery-sensor.battery.level has no type.");
            assertThat(n.element()).isEqualTo("battery.providers.battery-sensor.battery.level");
        });
        assertThat(validation).filteredOn(n -> n.severity() == Note.Severity.WARNING)
                .allSatisfy(n -> assertThat(n.message()).startsWith("S8 "));
        assertThat(level(result).getEType()).isSameAs(EcorePackage.Literals.ESTRING);
    }

    private static AssociationEnd end(String name, Class type, boolean navigable) {
        AssociationEnd e = RelationshipsFactory.eINSTANCE.createAssociationEnd();
        e.setName(name);
        e.setType(type);
        e.setIsNavigable(navigable);
        e.setAggregation(AggregationKind.AK_NONE);
        return e;
    }
}
