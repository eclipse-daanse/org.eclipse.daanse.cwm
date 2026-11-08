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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.emf.standalone.CwmPath;
import org.eclipse.daanse.cwm.emf.standalone.EcoreIndex;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.Enumeration;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ChangeableKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Alignment;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.CountLimit;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.DataScope;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Delete;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.LifecycleRule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Limit;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.PeriodStart;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.RetentionSchedule;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.SizeLimit;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.Stage;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.ValueTime;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Durations;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.PeriodStarts;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.Periods;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.RetentionSchedules;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.RetentionHold;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.LegalSources;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.RetentionHolds;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.AbsoluteChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.ChangedValueRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.HistorisationKind;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.Interpolation;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.LateValueHandling;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RangeChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RecordingRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.RelativeChangeRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.SamplingRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.SwingingDoorRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.ThrottleRule;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.util.HistorisationRules;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.util.RecordingRules;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ArrivalTimeSource;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.LiteralSource;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.PathSource;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ValueSource;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.SensinactBindings;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Provider;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Resource;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Service;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.SensinactStructure;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.eclipse.daanse.cwm.model.daanse.units.util.Quantities;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.fennec.event.atlas.model.mapping.ChangeRule;
import org.eclipse.fennec.event.atlas.model.mapping.DeletionRule;
import org.eclipse.fennec.event.atlas.model.mapping.DurationUnit;
import org.eclipse.fennec.event.atlas.model.mapping.FeatureMapping;
import org.eclipse.fennec.event.atlas.model.mapping.MappingFactory;
import org.eclipse.fennec.event.atlas.model.mapping.MappingProfile;
import org.eclipse.fennec.event.atlas.model.mapping.NameMapping;
import org.eclipse.fennec.event.atlas.model.mapping.PersistenceRule;
import org.eclipse.fennec.event.atlas.model.mapping.ProfileAdmin;
import org.eclipse.fennec.event.atlas.model.mapping.ProfileProvider;
import org.eclipse.fennec.event.atlas.model.mapping.ProfileResource;
import org.eclipse.fennec.event.atlas.model.mapping.ProfileService;
import org.eclipse.fennec.event.atlas.model.mapping.ProviderStrategy;
import org.eclipse.fennec.event.atlas.model.mapping.ReferenceMapping;
import org.eclipse.fennec.event.atlas.model.mapping.TimestampMapping;
import org.eclipse.fennec.event.atlas.model.mapping.TimestampStrategy;

/**
 * One conversion: CWM sensiNact mappings into event.atlas provider mappings and profiles.
 *
 * <ul>
 * <li>A provider mapping keeps its name as {@code mid}; source classes, id, timestamp, services
 * and admin follow the CWM mapping. Every resource refers to a timestamp (Fennec requires one): the
 * service's, else the provider mapping's, else a {@code NOW} timestamp written for the
 * purpose.</li>
 * <li>A service selector becomes a pure selecting reference mapping ({@code exclude=false}, no
 * filter attributes): it picks the element, it generates no resource.</li>
 * <li>Each resource names its type, unit, initial value, changeability, description (annotation
 * {@code sensinact.mapping}) and extra metadata (its tagged values); its unit is the symbol of the
 * unit of its quantity (model {@code units}: the quantity naming the resource, else its data type);
 * its change rule is the nearest recording rule (model {@code recording}) and its deletion rule the
 * nearest retention schedule of values (model {@code lifecycle}), each copied per resource.</li>
 * <li>What Fennec cannot carry — a heartbeat, a minimum interval beside a throttle, a status
 * feature, late values other than compared, stepped interpolation, a changed-value or swinging-door
 * rule, a historisation rule other than a new version per value; a stage of a schedule other than
 * one delete after an exact period, a
 * start other than the time of the value, a limit other than a count without key, a cap per
 * cleanup, a schedule of rows; a retention hold (model {@code obligations}) that is not over at the
 * time of the conversion ({@link Options#at}), which a deletion rule cannot wait for — is an error of rule
 * {@link AtlasNotes#RULE}; with {@code lenient} a warning, and the part is left out (with a hold:
 * the deletion rule). A range rule becomes an absolute one.</li>
 * <li>With profiles, every provider gets one {@code UNIFIED} profile with its services, resources,
 * expected types and units, and the admin requirements.</li>
 * </ul>
 */
final class AtlasConversion {

    private static final MappingFactory F = MappingFactory.eINSTANCE;
    private static final String ADMIN = "admin";
    private static final String ANNOTATION = "sensinact.mapping";

    /** The Ecore type of a CWM data type name, where no override and no Ecore model says otherwise. */
    private static final Map<String, String> DEFAULT_TYPES = new HashMap<>();

    static {
        for (String n : List.of("String", "Name", "Any", "Time", "Timestamp", "Date", "DateTime")) {
            DEFAULT_TYPES.put(n, "EString");
        }
        DEFAULT_TYPES.put("Boolean", "EBoolean");
        for (String n : List.of("Integer", "UnlimitedInteger", "Long")) {
            DEFAULT_TYPES.put(n, "ELong");
        }
        DEFAULT_TYPES.put("Int", "EInt");
        DEFAULT_TYPES.put("Short", "EShort");
        DEFAULT_TYPES.put("Byte", "EByte");
        DEFAULT_TYPES.put("Float", "EDouble");
        DEFAULT_TYPES.put("Double", "EDouble");
        DEFAULT_TYPES.put("Decimal", "EBigDecimal");
        DEFAULT_TYPES.put("Numeric", "EBigDecimal");
    }

    private final List<org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ProviderMapping> mappings;
    private final EcoreIndex index;
    private final Options options;
    private final List<Note> notes = new ArrayList<>();
    private final Map<Provider, MappingProfile> profiles = new LinkedHashMap<>();
    private final Map<Classifier, EDataType> types = new IdentityHashMap<>();
    private final Set<Object> noted = new HashSet<>();
    /** The time the mappings are written for: a hold not over then keeps its data from Fennec. */
    private final Instant now;
    private final boolean lenient;

    AtlasConversion(Collection<org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ProviderMapping> mappings, EcoreIndex index,
            Options options, boolean lenient) {
        this.mappings = List.copyOf(mappings);
        this.index = index;
        this.options = options;
        this.lenient = lenient;
        this.now = options.at() != null ? options.at() : Instant.now();
    }

    CwmToAtlas.Result run() {
        List<org.eclipse.fennec.event.atlas.model.mapping.ProviderMapping> out = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Map<Provider, Integer> perProvider = new LinkedHashMap<>();
        for (var pm : mappings) {
            if (!names.add(pm.getName())) {
                notes.add(Note.error(AtlasNotes.NAME, path(pm), "a second provider mapping named " + pm.getName()
                        + "; the name is the key of a mapping in Fennec's registry"));
            }
            if (pm.getProvider() != null) {
                perProvider.merge(pm.getProvider(), 1, Integer::sum);
            }
            out.add(providerMapping(pm));
        }
        if (!options.profiles()) {
            perProvider.forEach((provider, count) -> {
                if (count > 1) {
                    notes.add(Note.warning(AtlasNotes.PROFILE, path(provider), count + " mappings onto provider "
                            + provider.getName() + " without profiles: each makes its own provider model, named after "
                            + "the mapping"));
                }
            });
        }
        return new CwmToAtlas.Result(out, List.copyOf(profiles.values()), notes);
    }

    private org.eclipse.fennec.event.atlas.model.mapping.ProviderMapping providerMapping(
            org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ProviderMapping pm) {
        var out = F.createProviderMapping();
        out.setMid(pm.getName());
        for (var c : pm.getSourceClass()) {
            EClass eClass = index.eClass(c);
            if (eClass == null) {
                notes.add(Note.error(AtlasNotes.UNRESOLVED, path(c), "the source class " + c.getName()
                        + " has no EClass in the given Ecore model"));
            } else {
                out.getProviderClasses().add(eClass);
            }
        }
        if (pm.getId() != null) {
            NameMapping id = F.createNameMapping();
            if (pm.getId() instanceof PathSource path) {
                path(id, path, pm);
                id.setName(fallbackLiteral(path, pm, "the id"));
            } else if (pm.getId() instanceof LiteralSource literal) {
                id.setName(literal.getLiteral());
            }
            out.setName(id);
        }
        TimestampMapping providerTimestamp = pm.getTimestamp() == null ? null : timestamp(pm.getTimestamp(), pm);
        boolean needsOne = SensinactBindings.serviceMappings(pm).stream().anyMatch(sm -> sm.getTimestamp() == null)
                || pm.getAdmin() != null && pm.getAdmin().getDescription() != null;
        if (providerTimestamp == null && needsOne) {
            providerTimestamp = F.createTimestampMapping();
            providerTimestamp.setStrategy(TimestampStrategy.NOW);
        }
        out.setTimestamp(providerTimestamp);
        if (options.profiles() && pm.getProvider() != null) {
            out.setProfile(profile(pm.getProvider()));
        }
        for (var sm : SensinactBindings.serviceMappings(pm)) {
            out.getServices().add(serviceMapping(sm, providerTimestamp));
        }
        out.setAdmin(admin(pm, providerTimestamp));
        return out;
    }

    private org.eclipse.fennec.event.atlas.model.mapping.ServiceMapping serviceMapping(
            org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ServiceMapping sm, TimestampMapping providerTimestamp) {
        var out = F.createServiceMapping();
        Service service = sm.getService();
        out.setMid(service.getName());
        out.setName(staticName(service.getDisplayName() != null ? service.getDisplayName() : service.getName()));
        if (sm.getSelector() != null) {
            ReferenceMapping selector = F.createReferenceMapping();
            path(selector, sm.getSelector(), sm);
            selector.setExclude(false);
            fallbackLeftOut(sm.getSelector(), sm, "a selector");
            out.setReferencedResource(selector);
        }
        TimestampMapping timestamp = providerTimestamp;
        if (sm.getTimestamp() != null) {
            timestamp = timestamp(sm.getTimestamp(), sm);
            out.setTimestamp(timestamp);
        }
        for (var rm : SensinactBindings.resourceMappings(sm)) {
            out.getResources().add(resourceMapping(rm, timestamp));
        }
        return out;
    }

    private org.eclipse.fennec.event.atlas.model.mapping.ResourceMapping resourceMapping(
            org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ResourceMapping rm, TimestampMapping timestamp) {
        Resource resource = rm.getResource();
        var out = F.createResourceMapping();
        out.setMid(resource.getName());
        out.setName(resource.getDisplayName() != null ? resource.getDisplayName() : resource.getName());
        out.setEType(ecoreType(resource.getType(), resource));
        unit(resource).ifPresent(out::setUnit);
        ValueSource value = rm.getValue();
        if (value instanceof PathSource path) {
            out.getValueFeature().addAll(features(path, rm));
            elementZero(path, rm, "a resource value");
            fallbackLeftOut(path, rm, "a resource value");
        } else if (value instanceof LiteralSource literal) {
            out.setDefaultValueLiteral(literal.getLiteral());
            notes.add(Note.info(AtlasNotes.LITERAL, path(rm), "the literal value '" + literal.getLiteral()
                    + "' is written as the initial value of the resource; no feature overwrites it"));
        }
        if (resource.getInitialValue() != null && resource.getInitialValue().getBody() != null) {
            if (out.getDefaultValueLiteral() == null) {
                out.setDefaultValueLiteral(resource.getInitialValue().getBody());
            } else {
                notes.add(Note.warning(AtlasNotes.LITERAL, path(rm), "the initial value of the resource gives way to "
                        + "the literal value of the mapping"));
            }
        }
        if (resource.getChangeability() == ChangeableKind.CK_FROZEN) {
            out.setChangeable(false);
        }
        out.setTimestamp(timestamp);
        SensinactStructure.description(resource)
                .ifPresent(d -> EcoreUtil.setAnnotation(out, ANNOTATION, ANNOTATION + ".description", d));
        out.getExtraMetadata().putAll(SensinactStructure.extraMetadata(resource));
        RecordingRules.effective(resource).flatMap(r -> changeRule(r, resource)).ifPresent(out::setChangeRule);
        Optional<RetentionSchedule> values = RetentionSchedules.effective(resource, DataScope.VALUE);
        Optional<RetentionHold> held = values.isPresent() ? notEnded(resource) : Optional.empty();
        if (held.isPresent()) {
            RetentionHold hold = held.get();
            if (noted.add(List.of(hold, resource))) {
                notCarried(resource, "hold " + hold.getName() + " (" + hold.getReason() + ") is not over; Fennec's "
                        + "deletion rule cannot wait for it", "no deletion rule is written");
            }
        } else {
            values.flatMap(s -> deletionRule(s, resource)).ifPresent(out::setDeletionRule);
        }
        RetentionSchedules.effective(resource, DataScope.ROW).filter(s -> noted.add(List.of(s, resource)))
                .ifPresent(s -> notCarried(resource, "schedule " + s.getName() + " keeps rows; Fennec keeps the values "
                        + "of each resource", "it is left out"));
        HistorisationRules.effective(resource, DataScope.VALUE)
                .filter(r -> r.getKind() != HistorisationKind.NEW_VERSION && noted.add(List.of(r, resource)))
                .ifPresent(r -> notCarried(resource, "rule " + r.getName() + ": Fennec keeps every recorded value of "
                        + "a resource as a version of its own, not " + r.getKind().getLiteral(), "it is left out"));
        HistorisationRules.effective(resource, DataScope.ROW).filter(r -> noted.add(List.of(r, resource)))
                .ifPresent(r -> notCarried(resource, "rule " + r.getName() + " keeps the history of rows; Fennec "
                        + "keeps the values of each resource", "it is left out"));
        return out;
    }

    /** The symbol of the unit of a resource: of its own quantity, else of the quantity of its data type. */
    private static Optional<String> unit(Resource resource) {
        return Quantities.unitOf(resource).map(Unit::getSymbol);
    }

    private org.eclipse.fennec.event.atlas.model.mapping.AdminMapping admin(
            org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ProviderMapping pm, TimestampMapping providerTimestamp) {
        var out = F.createAdminMapping();
        out.setMid(ADMIN);
        out.setName(staticName("Admin"));
        var admin = pm.getAdmin();
        if (admin == null) {
            notes.add(Note.info(AtlasNotes.ADMIN, path(pm), "Fennec builds the admin service from the admin mapping; "
                    + "an empty one is written"));
            return out;
        }
        ValueSource friendlyName = admin.getFriendlyName();
        if (friendlyName instanceof PathSource path) {
            out.getFriendlyNameFeature().addAll(features(path, admin));
            elementZero(path, admin, "the friendly name");
            out.setFriendlyName(fallbackLiteral(path, admin, "the friendly name"));
        } else if (friendlyName instanceof LiteralSource literal) {
            out.setFriendlyName(literal.getLiteral());
        }
        ValueSource latitude = admin.getLatitude();
        if (latitude instanceof PathSource path) {
            out.getLatitudeRef().addAll(features(path, admin));
            elementZero(path, admin, "the latitude");
            out.setLatitude(number(fallbackLiteral(path, admin, "the latitude"), admin, "latitude"));
        } else if (latitude instanceof LiteralSource literal) {
            out.setLatitude(number(literal.getLiteral(), admin, "latitude"));
        }
        ValueSource longitude = admin.getLongitude();
        if (longitude instanceof PathSource path) {
            out.getLongitudeRef().addAll(features(path, admin));
            elementZero(path, admin, "the longitude");
            out.setLongitude(number(fallbackLiteral(path, admin, "the longitude"), admin, "longitude"));
        } else if (longitude instanceof LiteralSource literal) {
            out.setLongitude(number(literal.getLiteral(), admin, "longitude"));
        }
        ValueSource description = admin.getDescription();
        if (description != null) {
            var resource = F.createResourceMapping();
            resource.setMid("description");
            resource.setName("description");
            resource.setEType(EcorePackage.Literals.ESTRING);
            if (description instanceof PathSource path) {
                resource.getValueFeature().addAll(features(path, admin));
                elementZero(path, admin, "the description");
                fallbackLeftOut(path, admin, "the description");
            } else if (description instanceof LiteralSource literal) {
                resource.setDefaultValueLiteral(literal.getLiteral());
            }
            resource.setTimestamp(providerTimestamp);
            out.getResources().add(resource);
        }
        return out;
    }

    private MappingProfile profile(Provider provider) {
        return profiles.computeIfAbsent(provider, p -> {
            MappingProfile profile = F.createMappingProfile();
            profile.setProfileId(p.getName());
            profile.setName(p.getName());
            profile.setProviderStrategy(ProviderStrategy.UNIFIED);
            SensinactStructure.description(p).ifPresent(profile::setDescription);
            ProfileProvider structure = F.createProfileProvider();
            structure.setProviderId(p.getName());
            for (Service s : SensinactStructure.services(p)) {
                ProfileService service = F.createProfileService();
                service.setServiceId(s.getName());
                service.setServiceName(s.getDisplayName() != null ? s.getDisplayName() : s.getName());
                service.setRequired(s.isRequired());
                for (Resource r : SensinactStructure.resources(s)) {
                    ProfileResource resource = F.createProfileResource();
                    resource.setResourceId(r.getName());
                    resource.setResourceName(r.getDisplayName() != null ? r.getDisplayName() : r.getName());
                    resource.setRequired(r.isRequired());
                    resource.setExpectedType(ecoreType(r.getType(), r));
                    resource.setExpectedUnit(unit(r).orElse(null));
                    service.getResources().add(resource);
                }
                structure.getServices().add(service);
            }
            ProfileAdmin admin = F.createProfileAdmin();
            admin.setServiceId(ADMIN);
            admin.setServiceName("Admin");
            admin.setRequired(true);
            admin.setRequiresLocation(p.isLocationRequired());
            admin.setRequiresFriendlyName(p.isFriendlyNameRequired());
            structure.setAdmin(admin);
            profile.setProvider(structure);
            return profile;
        });
    }

    private Optional<ChangeRule> changeRule(RecordingRule rule, Resource resource) {
        boolean first = noted.add(List.of(rule, resource));
        if (first) {
            uncarriedParts(rule, resource);
        }
        ChangeRule out = null;
        if (rule instanceof AbsoluteChangeRule r && number(r.getDelta()) != null) {
            var absolute = F.createAbsoluteChangeRule();
            absolute.setDelta(number(r.getDelta()));
            out = absolute;
        } else if (rule instanceof RelativeChangeRule r && number(r.getPercent()) != null) {
            var percentage = F.createPercentageChangeRule();
            percentage.setPercentage(number(r.getPercent()));
            out = percentage;
        } else if (rule instanceof SamplingRule r) {
            var count = F.createCountChangeRule();
            count.setN((int) Math.min(Integer.MAX_VALUE, Math.max(1, r.getEvery())));
            out = count;
        } else if (rule instanceof ThrottleRule r) {
            Amount interval = amount(r.getMinimumInterval(), rule, resource, "minimumInterval");
            if (interval != null) {
                var throttle = F.createTimeThrottleChangeRule();
                throttle.setInterval(interval.amount());
                throttle.setIntervalUnit(interval.unit());
                out = throttle;
            }
        } else if (rule instanceof RangeChangeRule r && rangeDelta(r) != null) {
            BigDecimal delta = rangeDelta(r);
            var absolute = F.createAbsoluteChangeRule();
            absolute.setDelta(delta.doubleValue());
            out = absolute;
            if (first) {
                notes.add(Note.info(AtlasNotes.RULE, path(resource), "rule " + rule.getName() + ": a change of "
                        + r.getPercent() + " % of the range from " + r.getLow() + " to " + r.getHigh()
                        + " is written as an absolute change of " + delta.toPlainString()));
            }
        } else if (!first) {
            return Optional.empty();
        } else if (rule instanceof ChangedValueRule) {
            notCarried(resource, "rule " + rule.getName() + ": Fennec's mapping model records changed values only "
                    + "through its other rules", "no change rule is written");
        } else if (rule instanceof SwingingDoorRule) {
            notCarried(resource, "rule " + rule.getName() + ": Fennec's mapping model has no swinging door",
                    "no change rule is written");
        } else {
            notCarried(resource, "rule " + rule.getName() + " has a parameter that does not parse",
                    "no change rule is written");
        }
        if (out != null) {
            identity(out, rule);
        }
        return Optional.ofNullable(out);
    }

    /** The parts of a recording rule beside its criterion that Fennec's change rules have no place for. */
    private void uncarriedParts(RecordingRule rule, Resource resource) {
        String name = "rule " + rule.getName() + ": Fennec's mapping model has ";
        if (rule.getMaximumInterval() != null) {
            notCarried(resource, name + "no heartbeat (maximumInterval " + rule.getMaximumInterval() + ")",
                    "it is left out");
        }
        if (rule.getMinimumInterval() != null && !(rule instanceof ThrottleRule)) {
            notCarried(resource, name + "no minimum interval beside a throttle (minimumInterval "
                    + rule.getMinimumInterval() + ")", "it is left out");
        }
        if (rule.getStatusFeature() != null) {
            notCarried(resource, name + "no status feature (" + rule.getStatusFeature().getName() + ")",
                    "it is left out");
        }
        LateValueHandling late = rule.getLateValues();
        if (late == LateValueHandling.INSERT || late == LateValueHandling.DROP && !(rule instanceof ThrottleRule)) {
            notCarried(resource, name + "no handling of late values but its rules' (lateValues " + late.getLiteral()
                    + ")", "late values are compared");
        }
        if (rule.getInterpolation() == Interpolation.STEPPED) {
            notCarried(resource, name + "no interpolation (stepped)", "it is left out");
        }
    }

    /** {@code percent / 100 × (high − low)}, exact; {@code null} when the parameters are no range. */
    private static BigDecimal rangeDelta(RangeChangeRule rule) {
        try {
            BigDecimal low = new BigDecimal(rule.getLow().trim());
            BigDecimal high = new BigDecimal(rule.getHigh().trim());
            BigDecimal percent = new BigDecimal(rule.getPercent().trim());
            if (high.compareTo(low) <= 0 || percent.signum() < 0) {
                return null;
            }
            return percent.multiply(high.subtract(low)).movePointLeft(2).stripTrailingZeros();
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    /** The first hold on a resource that is not over at the time of the conversion: begun or to come. */
    private Optional<RetentionHold> notEnded(Resource resource) {
        return RetentionHolds.of(resource).stream().filter(h -> {
            Instant until = LegalSources.instant(h.getUntil());
            return until == null || until.isAfter(now);
        }).findFirst();
    }

    /**
     * A part of a rule Fennec cannot carry: an error, so that nothing is written; with
     * {@code lenient} a warning, and the part is left out.
     */
    private void notCarried(Resource resource, String message, String leftOut) {
        notes.add(lenient ? Note.warning(AtlasNotes.RULE, path(resource), message + "; " + leftOut)
                : Note.error(AtlasNotes.RULE, path(resource), message));
    }

    /**
     * Fennec's deletion rule from the schedule of values of a resource: the one {@code Delete} stage
     * after an exact period from the time of the value (unaligned, without offset), a count limit
     * without key as {@code maxCount}, the cleanup interval. Every other stage, start and limit and
     * {@code maxDelete} have no place in it.
     */
    private Optional<DeletionRule> deletionRule(RetentionSchedule schedule, Resource resource) {
        boolean first = noted.add(List.of(schedule, resource));
        String name = "schedule " + schedule.getName();
        Delete delete = null;
        for (Stage stage : schedule.getStage()) {
            if (stage instanceof Delete d && delete == null) {
                delete = d;
            } else if (first) {
                notCarried(resource, name + ": Fennec's deletion rule has no stage " + RetentionSchedules.render(stage),
                        "it is left out");
            }
        }
        PeriodStart start = delete != null && delete.getStart() != null ? delete.getStart() : schedule.getStart();
        if (first && start != null && !fromTheValue(start)) {
            notCarried(resource, name + ": Fennec counts from the time of a value, not from "
                    + PeriodStarts.render(start), "it counts from the time of the value");
        }
        Integer maxCount = null;
        for (Limit limit : schedule.getLimit()) {
            if (limit instanceof CountLimit c && c.getKey().isEmpty() && maxCount == null) {
                maxCount = (int) Math.min(Integer.MAX_VALUE, c.getKeep());
            } else if (first) {
                notCarried(resource, name + ": Fennec's deletion rule has no " + (limit instanceof SizeLimit
                        ? "limit of size" : limit instanceof CountLimit c && !c.getKey().isEmpty() ? "count per key"
                                : "second count limit"), "it is left out");
            }
        }
        if (first && schedule.isSetMaxDelete()) {
            notCarried(resource, name + ": Fennec's deletion rule has no cap per cleanup (maxDelete "
                    + schedule.getMaxDelete() + ")", "it is left out");
        }
        if (delete == null || delete.getAfter() == null) {
            if (first) {
                notCarried(resource, name + ": Fennec's deletion rule needs a retention, a stage delete after a period",
                        "no deletion rule is written");
            }
            return Optional.empty();
        }
        if (!Periods.isExact(delete.getAfter())) {
            if (first) {
                notCarried(resource, name + ": delete after " + delete.getAfter() + " is no exact period; Fennec's "
                        + "retention is a whole number of days or less", "no deletion rule is written");
            }
            return Optional.empty();
        }
        Amount retention = amount(Durations.format(Periods.toDuration(delete.getAfter())), schedule, resource,
                "retention");
        if (retention == null) {
            return Optional.empty();
        }
        DeletionRule out = F.createDeletionRule();
        out.setRetention(retention.amount());
        out.setRetentionUnit(retention.unit());
        if (schedule.getCleanupInterval() != null) {
            Amount cleanup = amount(schedule.getCleanupInterval(), schedule, resource, "cleanupInterval");
            if (cleanup != null) {
                out.setCleanupInterval(cleanup.amount());
                out.setCleanupIntervalUnit(cleanup.unit());
            }
        }
        if (maxCount != null) {
            out.setMaxCount(maxCount);
        }
        identity(out, schedule);
        return Optional.of(out);
    }

    /** Whether a start is the time of the value itself, as Fennec counts. */
    private static boolean fromTheValue(PeriodStart start) {
        return start instanceof ValueTime && start.getAlignment() == Alignment.NONE && start.getOffset() == null;
    }

    private static void identity(PersistenceRule out, LifecycleRule rule) {
        out.setId(rule.getName());
        SensinactStructure.description(rule).ifPresent(out::setDescription);
    }

    /** A duration as an amount and one of Fennec's units. */
    private record Amount(int amount, DurationUnit unit) {
    }

    private Amount amount(String literal, LifecycleRule rule, Resource resource, String feature) {
        if (!Durations.isValid(literal)) {
            notCarried(resource, "rule " + rule.getName() + ": " + feature + " '" + literal + "' is no duration",
                    "the rule is left out");
            return null;
        }
        Duration d = Durations.parse(literal);
        Durations.Amount a = Durations.largestUnit(d);
        if (a == null || a.amount() > Integer.MAX_VALUE) {
            notCarried(resource, "rule " + rule.getName() + ": " + feature + " " + literal + " is no whole number of "
                    + "milliseconds to days within an int", "the rule is left out");
            return null;
        }
        DurationUnit unit = switch (a.unit()) {
            case DAYS -> DurationUnit.DAYS;
            case HOURS -> DurationUnit.HOURS;
            case MINUTES -> DurationUnit.MINUTES;
            case SECONDS -> DurationUnit.SECONDS;
            default -> DurationUnit.MILLISECONDS;
        };
        return new Amount((int) a.amount(), unit);
    }

    /**
     * The Ecore type of a resource type: an override of the options by its name; else what the
     * given Ecore model made of it (an EEnum of the source model, say); else the Ecore data type of
     * the same name ({@code EDouble}); else the CWM default ({@code Float → EDouble}); else
     * {@code EString}.
     */
    private EDataType ecoreType(Classifier type, ModelElement at) {
        if (type == null) {
            return EcorePackage.Literals.ESTRING;
        }
        return types.computeIfAbsent(type, t -> {
            String override = options.dataTypes().get(t.getName());
            if (override != null) {
                if (EcorePackage.eINSTANCE.getEClassifier(override) instanceof EDataType d) {
                    return d;
                }
                notes.add(Note.error(AtlasNotes.DATA_TYPE, path(at), "the data type override " + t.getName() + " → "
                        + override + " names no Ecore data type"));
                return EcorePackage.Literals.ESTRING;
            }
            if (index.eClassifier(t) instanceof EDataType d) {
                return d;
            }
            if (EcorePackage.eINSTANCE.getEClassifier(t.getName()) instanceof EDataType d) {
                return d;
            }
            String standard = DEFAULT_TYPES.get(t.getName());
            if (standard != null) {
                return (EDataType) EcorePackage.eINSTANCE.getEClassifier(standard);
            }
            if (t instanceof Enumeration) {
                notes.add(Note.info(AtlasNotes.DATA_TYPE, path(t), "the enumeration " + t.getName() + " is not in the "
                        + "given Ecore model: EString (Fennec stores the name of a literal)"));
            } else {
                notes.add(Note.warning(AtlasNotes.DATA_TYPE, path(t), "no Ecore type for the data type " + t.getName()
                        + ": EString"));
            }
            return EcorePackage.Literals.ESTRING;
        });
    }

    private List<EStructuralFeature> features(PathSource source, ModelElement at) {
        List<EStructuralFeature> out = new ArrayList<>();
        for (StructuralFeature step : source.getPath()) {
            EStructuralFeature f = index.feature(step);
            if (f == null) {
                notes.add(Note.error(AtlasNotes.UNRESOLVED, path(at), "the feature " + CwmPath.of(step)
                        + " has no Ecore feature in the given Ecore model"));
            } else {
                out.add(f);
            }
        }
        return out;
    }

    /** Path, index and filter of a path source into a feature mapping of Fennec. */
    private void path(FeatureMapping out, PathSource source, ModelElement at) {
        out.getFeaturePath().addAll(features(source, at));
        if (source.isSetIndex()) {
            out.setCollectionIndex((int) source.getIndex());
        }
        if (source.getFilter() != null) {
            out.setCollectionFilter(source.getFilter().getBody());
        }
    }

    /**
     * A path is the strategy {@code FEATURE} (a literal fallback becomes the fixed timestamp, the
     * arrival time as fallback is what Fennec does anyway), a literal the fixed timestamp, the
     * arrival time the strategy {@code NOW}.
     */
    private TimestampMapping timestamp(ValueSource source, ModelElement at) {
        TimestampMapping out = F.createTimestampMapping();
        if (source instanceof PathSource path) {
            path(out, path, at);
            out.setStrategy(TimestampStrategy.FEATURE);
            out.setHint(path.getFormat());
            if (!(path.getFallback() instanceof ArrivalTimeSource)) {
                out.setTimestamp(fallbackLiteral(path, at, "the timestamp"));
            }
        } else if (source instanceof LiteralSource literal) {
            out.setTimestamp(literal.getLiteral());
        } else {
            out.setStrategy(TimestampStrategy.NOW);
        }
        return out;
    }

    /**
     * The literal a path falls back to, for the places where Fennec keeps one beside the path; any
     * other fallback is left out with a note.
     */
    private String fallbackLiteral(PathSource path, ModelElement at, String what) {
        if (path.getFallback() instanceof LiteralSource literal) {
            return literal.getLiteral();
        }
        if (path.getFallback() != null) {
            notes.add(Note.warning(AtlasNotes.PATH, path(at), what + " falls back to "
                    + SensinactBindings.render(path.getFallback()) + "; Fennec falls back to a literal only, the "
                    + "fallback is left out"));
        }
        return null;
    }

    /** Where Fennec keeps no fallback beside a path: a note for the one the path has. */
    private void fallbackLeftOut(PathSource path, ModelElement at, String what) {
        if (path.getFallback() instanceof LiteralSource literal) {
            notes.add(Note.warning(AtlasNotes.LITERAL, path(at), "Fennec has no fallback for " + what + "; the "
                    + "literal '" + literal.getLiteral() + "' is left out"));
        } else if (path.getFallback() != null) {
            notes.add(Note.warning(AtlasNotes.PATH, path(at), "Fennec has no fallback for " + what + "; the "
                    + "fallback " + SensinactBindings.render(path.getFallback()) + " is left out"));
        }
    }

    /** Fennec reads element 0 of every collection on a resource value or an admin path. */
    private void elementZero(PathSource source, ModelElement at, String what) {
        if (source.getFilter() != null || source.isSetIndex() && source.getIndex() != 0) {
            notes.add(Note.warning(AtlasNotes.PATH, path(at), what + " " + SensinactBindings.render(source)
                    + ": Fennec reads element 0 of a collection here; the index or filter is left out"));
        }
    }

    private static NameMapping staticName(String name) {
        NameMapping out = F.createNameMapping();
        out.setName(name);
        return out;
    }

    private Double number(String literal, ModelElement at, String what) {
        if (literal == null) {
            return null;
        }
        Double d = number(literal);
        if (d == null) {
            notes.add(Note.warning(AtlasNotes.LITERAL, path(at), "the " + what + " '" + literal + "' is no number; "
                    + "it is left out"));
        }
        return d;
    }

    private static Double number(String literal) {
        try {
            return literal == null ? null : Double.valueOf(literal.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String path(ModelElement element) {
        return CwmPath.of(element);
    }
}
