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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.InternalEList;
import org.eclipse.fennec.event.atlas.model.mapping.AbsoluteChangeRule;
import org.eclipse.fennec.event.atlas.model.mapping.AdminMapping;
import org.eclipse.fennec.event.atlas.model.mapping.ChangeRule;
import org.eclipse.fennec.event.atlas.model.mapping.CountChangeRule;
import org.eclipse.fennec.event.atlas.model.mapping.DeletionRule;
import org.eclipse.fennec.event.atlas.model.mapping.FeatureMapping;
import org.eclipse.fennec.event.atlas.model.mapping.MappingPackage;
import org.eclipse.fennec.event.atlas.model.mapping.MappingProfile;
import org.eclipse.fennec.event.atlas.model.mapping.PercentageChangeRule;
import org.eclipse.fennec.event.atlas.model.mapping.ProviderMapping;
import org.eclipse.fennec.event.atlas.model.mapping.ReferenceMapping;
import org.eclipse.fennec.event.atlas.model.mapping.ReferenceResourceBinding;
import org.eclipse.fennec.event.atlas.model.mapping.ResourceMapping;
import org.eclipse.fennec.event.atlas.model.mapping.ServiceMapping;
import org.eclipse.fennec.event.atlas.model.mapping.TimeThrottleChangeRule;
import org.eclipse.fennec.event.atlas.model.mapping.TimestampMapping;

/**
 * What a Fennec provider mapping means at run time, as text to compare: source classes, id,
 * timestamp, admin values, and per service its selector, timestamp and resources (type, unit,
 * value path, initial value, rules). Resources a reference mapping generates are expanded the way
 * Fennec expands them (attributes of the target class minus or only the filter, nested references
 * prefixed, settings from the bindings). Features read by name ({@code Class/feature}), also where
 * a reference does not resolve. Left out: what has no effect at run time (strategy names,
 * {@code providerPackage}, the admin timestamp, the timestamp references of resources).
 */
final class Normalizer {

    private Normalizer() {
    }

    /** The meaning of a mapping. */
    static String of(ProviderMapping m) {
        List<String> lines = new ArrayList<>();
        lines.add("mapping " + m.getMid());
        lines.add("  classes " + names(list(m, MappingPackage.Literals.PROVIDER_MAPPING__PROVIDER_CLASSES)));
        lines.add("  id " + (m.getName() == null ? "-" : featureMapping(m.getName()) + " name=" + m.getName().getName()));
        lines.add("  timestamp " + timestamp(m.getTimestamp()));
        AdminMapping admin = m.getAdmin();
        List<ServiceMapping> services = new ArrayList<>();
        for (ServiceMapping s : m.getServices()) {
            if (s instanceof AdminMapping a) {
                admin = a;
            } else {
                services.add(s);
            }
        }
        lines.add("  admin " + admin(admin));
        services.sort(Comparator.comparing(ServiceMapping::getMid));
        for (ServiceMapping s : services) {
            lines.add("  service " + s.getMid() + " '" + (s.getName() == null ? "" : s.getName().getName()) + "'");
            ReferenceMapping ref = s.getReferencedResource();
            if (ref != null) {
                lines.add("    selector " + featureMapping(ref));
            }
            lines.add("    timestamp " + (s.getTimestamp() == null ? "inherited" : timestamp(s.getTimestamp())));
            List<String> resources = new ArrayList<>();
            for (ResourceMapping r : s.getResources()) {
                resources.add(resource(r.getMid(), r.getName(), type(r), unit(r),
                        path(list(r, MappingPackage.Literals.RESOURCE_MAPPING__VALUE_FEATURE)), r.getDefaultValueLiteral(),
                        r.isChangeable(), r.getChangeRule(), r.getDeletionRule()));
            }
            if (ref != null) {
                expand(ref, "", List.of(), new Bindings(null, ref.getBindings()), resources);
            }
            resources.sort(Comparator.naturalOrder());
            lines.addAll(resources);
        }
        return String.join("\n", lines);
    }

    /** The meaning of a profile: provider id and admin requirements. */
    static String of(MappingProfile p) {
        if (p == null) {
            return "no profile";
        }
        var admin = p.getProvider() == null ? null : p.getProvider().getAdmin();
        return "profile " + p.getProfileId() + " provider " + (p.getProvider() == null ? "-" : p.getProvider().getProviderId())
                + " admin(required=" + (admin != null && admin.isRequired()) + ", location="
                + (admin != null && admin.isRequiresLocation()) + ", friendlyName="
                + (admin != null && admin.isRequiresFriendlyName()) + ")";
    }

    private static String resource(String mid, String name, String type, String unit, String value, String literal,
            boolean changeable, ChangeRule change, DeletionRule deletion) {
        return "    resource " + mid + " '" + name + "' type=" + type + " unit=" + unit + " value=" + value
                + (literal == null ? "" : " literal=" + literal) + (changeable ? "" : " frozen") + " change=" + change(change)
                + " deletion=" + deletion(deletion);
    }

    /** The resources a reference mapping generates, as Fennec generates them. */
    private static void expand(ReferenceMapping ref, String prefix, List<String> basePath, Bindings bindings,
            List<String> out) {
        EClass target = ref.getTargetEClass();
        List<EStructuralFeature> path = list(ref, MappingPackage.Literals.FEATURE_MAPPING__FEATURE_PATH);
        if (target == null && !path.isEmpty()) {
            EStructuralFeature last = (EStructuralFeature) EcoreUtil.resolve(path.get(path.size() - 1), ref);
            if (last instanceof EReference r) {
                target = r.getEReferenceType();
            }
        }
        if (target == null) {
            return;
        }
        List<EAttribute> filter = ref.getFilter();
        List<EAttribute> selected = new ArrayList<>();
        for (EAttribute a : target.getEAllAttributes()) {
            if (ref.isExclude() ? !filter.contains(a) : filter.contains(a)) {
                selected.add(a);
            }
        }
        for (EAttribute a : selected) {
            String mid = prefix.isEmpty() ? a.getName() : prefix + a.getName().substring(0, 1).toUpperCase(Locale.ROOT)
                    + a.getName().substring(1);
            List<String> value = new ArrayList<>(basePath);
            value.add(name(a));
            String unit = bindings.unit(a);
            if (unit == null) {
                unit = EcoreUtil.getAnnotation(a, "sensinact.mapping", "sensinact.mapping.unit");
            }
            out.add(resource(mid, a.getName(), a.getEAttributeType().getName(), unit, String.join(",", value), null, true,
                    bindings.change(a), bindings.deletion(a)));
        }
        for (ReferenceMapping nested : ref.getReferenceMappings()) {
            List<EStructuralFeature> nestedPath = list(nested, MappingPackage.Literals.FEATURE_MAPPING__FEATURE_PATH);
            EStructuralFeature last = (EStructuralFeature) EcoreUtil.resolve(nestedPath.get(nestedPath.size() - 1), nested);
            String nestedPrefix = prefix.isEmpty() ? last.getName()
                    : prefix + last.getName().substring(0, 1).toUpperCase(Locale.ROOT) + last.getName().substring(1);
            expand(nested, nestedPrefix, List.of(name(last)), new Bindings(bindings, nested.getBindings()), out);
        }
    }

    /** The settings of the bindings of a reference mapping: named first, then the default, then the parent. */
    private record Bindings(Bindings parent, List<ReferenceResourceBinding> bindings) {

        ChangeRule change(EAttribute a) {
            for (ReferenceResourceBinding b : candidates(a)) {
                if (b.getChangeRule() != null) {
                    return b.getChangeRule();
                }
            }
            return parent == null ? null : parent.change(a);
        }

        DeletionRule deletion(EAttribute a) {
            for (ReferenceResourceBinding b : candidates(a)) {
                if (b.getDeletionRule() != null) {
                    return b.getDeletionRule();
                }
            }
            return parent == null ? null : parent.deletion(a);
        }

        String unit(EAttribute a) {
            for (ReferenceResourceBinding b : candidates(a)) {
                if (b.getUnit() != null) {
                    return b.getUnit();
                }
            }
            return parent == null ? null : parent.unit(a);
        }

        private List<ReferenceResourceBinding> candidates(EAttribute a) {
            List<ReferenceResourceBinding> c = new ArrayList<>();
            bindings.stream().filter(b -> b.getAttributes().contains(a)).findFirst().ifPresent(c::add);
            bindings.stream().filter(b -> b.getAttributes().isEmpty()).findFirst().ifPresent(c::add);
            return c;
        }
    }

    private static String admin(AdminMapping a) {
        if (a == null) {
            return "-";
        }
        String description = a.getResources().stream().filter(r -> "description".equals(r.getMid()))
                .map(r -> path(list(r, MappingPackage.Literals.RESOURCE_MAPPING__VALUE_FEATURE)) + "|" + r.getDefaultValueLiteral())
                .collect(Collectors.joining());
        return "friendlyName=" + path(list(a, MappingPackage.Literals.ADMIN_MAPPING__FRIENDLY_NAME_FEATURE)) + "|"
                + a.getFriendlyName() + " latitude=" + path(list(a, MappingPackage.Literals.ADMIN_MAPPING__LATITUDE_REF)) + "|"
                + a.getLatitude() + " longitude=" + path(list(a, MappingPackage.Literals.ADMIN_MAPPING__LONGITUDE_REF)) + "|"
                + a.getLongitude() + " description=" + description;
    }

    private static String timestamp(TimestampMapping t) {
        List<EStructuralFeature> path = t == null ? List.of() : list(t, MappingPackage.Literals.FEATURE_MAPPING__FEATURE_PATH);
        if (t == null || path.isEmpty() && t.getTimestamp() == null) {
            return "now";
        }
        return featureMapping(t) + (t.getHint() == null ? "" : " hint=" + t.getHint())
                + (t.getTimestamp() == null ? "" : " literal=" + t.getTimestamp());
    }

    private static String featureMapping(FeatureMapping f) {
        return "path=" + path(list(f, MappingPackage.Literals.FEATURE_MAPPING__FEATURE_PATH)) + " index="
                + f.getCollectionIndex() + (f.getCollectionFilter() == null ? "" : " filter=" + f.getCollectionFilter());
    }

    private static String change(ChangeRule rule) {
        if (rule instanceof PercentageChangeRule r) {
            return "percent " + r.getPercentage();
        }
        if (rule instanceof AbsoluteChangeRule r) {
            return "absolute " + r.getDelta();
        }
        if (rule instanceof CountChangeRule r) {
            return "count " + r.getN();
        }
        if (rule instanceof TimeThrottleChangeRule r) {
            return "throttle " + r.getInterval() + " " + r.getIntervalUnit();
        }
        return "-";
    }

    private static String deletion(DeletionRule rule) {
        if (rule == null) {
            return "-";
        }
        return "retention " + rule.getRetention() + " " + rule.getRetentionUnit()
                + (rule.getCleanupInterval() == null ? "" : " cleanup " + rule.getCleanupInterval() + " "
                        + rule.getCleanupIntervalUnit())
                + (rule.getMaxCount() == null ? "" : " max " + rule.getMaxCount());
    }

    private static String type(ResourceMapping r) {
        EClassifier t = (EClassifier) r.eGet(org.eclipse.emf.ecore.EcorePackage.Literals.ETYPED_ELEMENT__ETYPE, false);
        return t == null ? "-" : simpleName(t);
    }

    private static String unit(ResourceMapping r) {
        return r.getUnit() != null ? r.getUnit() : EcoreUtil.getAnnotation(r, "sensinact.mapping", "sensinact.mapping.unit");
    }

    private static String path(List<EStructuralFeature> features) {
        return features.stream().map(Normalizer::name).collect(Collectors.joining(","));
    }

    private static String names(List<? extends EObject> objects) {
        return objects.stream().map(Normalizer::simpleName).sorted().collect(Collectors.joining(","));
    }

    /** {@code Class/feature} of a feature, resolved or not. */
    private static String name(EStructuralFeature f) {
        if (f.eIsProxy()) {
            return ((InternalEObject) f).eProxyURI().fragment().replaceFirst("^//", "");
        }
        return f.getEContainingClass().getName() + "/" + f.getName();
    }

    private static String simpleName(EObject o) {
        if (o.eIsProxy()) {
            String fragment = ((InternalEObject) o).eProxyURI().fragment();
            return fragment.substring(fragment.lastIndexOf('/') + 1);
        }
        return ((ENamedElement) o).getName();
    }

    /** The values of a many-valued reference, unresolved proxies kept. */
    @SuppressWarnings("unchecked")
    private static <T> List<T> list(EObject owner, EStructuralFeature feature) {
        return List.copyOf(((InternalEList<T>) owner.eGet(feature, false)).basicList());
    }
}
