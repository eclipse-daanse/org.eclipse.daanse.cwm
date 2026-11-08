/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.Enumeration;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.AdminMapping;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ArrivalTimeSource;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.LiteralSource;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.PathSource;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ProviderMapping;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ResourceMapping;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ServiceMapping;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ValueSource;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Provider;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Resource;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.Service;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.SensinactModelValidator;
import org.eclipse.daanse.cwm.model.daanse.resource.sensinact.util.SensinactStructure;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.InternalEObject;

/**
 * Checks sensiNact mappings: what a runtime would default is stated in the model. A check also
 * reports the findings {@code P1} to {@code P3} of the structure validator of
 * {@code resource.sensinact} for the providers below the root, so that one call covers what a
 * converter reads.
 *
 * <ul>
 * <li>{@code S1} completeness — every required service of the provider has exactly one service
 * mapping, every required resource of a mapped service exactly one resource mapping; mappings
 * only onto services of the provider and resources of the mapped service, each at most once.</li>
 * <li>{@code S2} paths — the first step is a feature of the class the value source starts at,
 * every further step a feature of the type of the step before (an attribute of the class or an
 * ancestor, or a navigable association end seen from the other end); a feature of a subclass is a
 * downcast (warning); at most one step holds many values, and its element is picked by index or by
 * filter; a filter is {@code path=literal} in
 * {@value SensinactBindings#FILTER_LANGUAGE}; the fallbacks of a path are checked alike; the
 * arrival time is a timestamp only.</li>
 * <li>{@code S3} (warning) types — the value of a path converts into the type of the resource, a
 * literal for a numeric resource is a number.</li>
 * <li>{@code S4} id — every provider mapping says where the id of an instance comes from.</li>
 * <li>{@code S5} timestamps — a format is a valid pattern; (warning) a path that ends neither in a
 * time, a whole number of milliseconds nor a text, a literal that is no ISO-8601 instant, a format without a zone.</li>
 * <li>{@code S6} admin — latitude and longitude both or neither; what the provider requires
 * (friendly name, location) is given.</li>
 * <li>{@code S7} names — provider mappings unique in a namespace; the name is the mapping id.</li>
 * <li>{@code S8} (warning) reverse direction — attributes of a class the mapping reads from that
 * no path reads.</li>
 * </ul>
 */
public final class SensinactMappingModelValidator {

    /** Severity of a finding; only {@link #ERROR} makes a mapping invalid. */
    public enum Severity {
        ERROR, WARNING
    }

    /** One finding: severity, rule code, the element it is about, message. */
    public record Finding(Severity severity, String rule, EObject element, String message) {
        @Override
        public String toString() {
            return severity + " " + rule + ": " + message;
        }
    }

    private static final Pattern FILTER = Pattern.compile("\\s*[A-Za-z_][\\w]*(\\.[A-Za-z_][\\w]*)*\\s*=\\s*\\S.*");
    private static final Set<String> NUMERIC = Set.of("integer", "int", "long", "short", "byte", "float", "double",
            "real", "decimal", "numeric", "number", "bigint", "smallint", "tinyint", "biginteger", "bigdecimal",
            "unlimitedinteger");
    private static final Set<String> WHOLE = Set.of("integer", "int", "long", "short", "byte", "biginteger",
            "unlimitedinteger");
    private static final Set<String> TIME = Set.of("time", "timestamp", "date", "datetime", "instant");
    private static final Set<String> TEXT = Set.of("string", "name", "text", "varchar", "char");

    private SensinactMappingModelValidator() {
    }

    /** All findings for the providers and mappings in {@code root} and below. */
    public static List<Finding> check(EObject root) {
        List<Finding> findings = new ArrayList<>();
        for (SensinactModelValidator.Finding f : SensinactModelValidator.check(root)) {
            findings.add(new Finding(Severity.valueOf(f.severity().name()), f.rule(), f.element(), f.message()));
        }
        List<EObject> all = new ArrayList<>();
        all.add(root);
        for (TreeIterator<EObject> it = root.eAllContents(); it.hasNext();) {
            all.add(it.next());
        }
        for (EObject o : all) {
            if (o instanceof Namespace ns && !(o instanceof Provider)) {
                uniqueMappingNames(ns, findings);
            }
        }
        for (EObject o : all) {
            if (o instanceof ProviderMapping pm) {
                new Check(pm, findings).run();
            }
        }
        return findings;
    }

    /** The errors of {@link #check(EObject)} as {@code "rule message"} lines. */
    public static List<String> validate(EObject root) {
        return check(root).stream().filter(f -> f.severity() == Severity.ERROR)
                .map(f -> f.rule() + " " + f.message()).toList();
    }

    private static void uniqueMappingNames(Namespace ns, List<Finding> findings) {
        Set<String> names = new HashSet<>();
        for (ModelElement e : ns.getOwnedElement()) {
            if (e instanceof ProviderMapping pm && !names.add(pm.getName())) {
                findings.add(error("S7", pm, path(ns) + " has two provider mappings named " + pm.getName()
                        + "; the name is the mapping id"));
            }
        }
    }

    /** The checks of one provider mapping. */
    private static final class Check {
        private final ProviderMapping pm;
        private final List<Finding> findings;
        private final Map<Classifier, Set<StructuralFeature>> read = new LinkedHashMap<>();

        Check(ProviderMapping pm, List<Finding> findings) {
            this.pm = pm;
            this.findings = findings;
        }

        void run() {
            Provider provider = pm.getProvider();
            if (provider == null) {
                findings.add(error("S1", pm, name() + " names no provider"));
                return;
            }
            if (pm.getSourceClass().isEmpty()) {
                findings.add(error("S1", pm, name() + " names no source class"));
                return;
            }
            List<Class> sources = List.copyOf(pm.getSourceClass());
            if (pm.getId() == null) {
                findings.add(error("S4", pm, name() + " does not say where the id of an instance comes from"));
            } else {
                source(pm.getId(), sources, "id", pm, false);
            }
            if (pm.getTimestamp() != null) {
                source(pm.getTimestamp(), sources, "timestamp", pm, true);
                timestamp(pm.getTimestamp(), "timestamp", pm);
            }
            admin(provider, sources);
            completeness(provider);
            for (ServiceMapping sm : SensinactBindings.serviceMappings(pm)) {
                if (sm.getSelector() != null) {
                    source(sm.getSelector(), sources, "selector of " + sm.getName(), sm, false);
                }
                List<Class> context = SensinactBindings.contextClasses(sm);
                if (sm.getTimestamp() != null) {
                    source(sm.getTimestamp(), context, "timestamp of " + sm.getName(), sm, true);
                    timestamp(sm.getTimestamp(), "timestamp of " + sm.getName(), sm);
                }
                for (ResourceMapping rm : SensinactBindings.resourceMappings(sm)) {
                    if (rm.getValue() == null) {
                        findings.add(error("S1", rm, name() + ": " + rm.getName() + " has no value"));
                        continue;
                    }
                    source(rm.getValue(), context, "value of " + sm.getName() + "." + rm.getName(), rm, false);
                    types(rm);
                }
            }
            unread();
        }

        private void completeness(Provider provider) {
            Map<Service, Integer> mapped = new HashMap<>();
            for (ServiceMapping sm : SensinactBindings.serviceMappings(pm)) {
                Service s = sm.getService();
                if (s == null) {
                    findings.add(error("S1", sm, name() + ": " + sm.getName() + " names no service"));
                    continue;
                }
                if (s.getNamespace() != provider) {
                    findings.add(error("S1", sm, name() + ": service " + path(s) + " is no service of " + provider.getName()));
                }
                if (mapped.merge(s, 1, Integer::sum) == 2) {
                    findings.add(error("S1", sm, name() + " maps service " + s.getName() + " twice"));
                }
                Map<Resource, Integer> resources = new HashMap<>();
                for (ResourceMapping rm : SensinactBindings.resourceMappings(sm)) {
                    Resource r = rm.getResource();
                    if (r == null) {
                        findings.add(error("S1", rm, name() + ": " + rm.getName() + " names no resource"));
                        continue;
                    }
                    if (r.getOwner() != s) {
                        findings.add(error("S1", rm, name() + ": resource " + path(r) + " is no resource of " + path(s)));
                    }
                    if (resources.merge(r, 1, Integer::sum) == 2) {
                        findings.add(error("S1", rm, name() + " maps resource " + path(r) + " twice"));
                    }
                }
                for (Resource r : SensinactStructure.resources(s)) {
                    if (r.isRequired() && !resources.containsKey(r)) {
                        findings.add(error("S1", sm, name() + " does not map the required resource " + path(r)));
                    }
                }
            }
            for (Service s : SensinactStructure.services(provider)) {
                if (s.isRequired() && !mapped.containsKey(s)) {
                    findings.add(error("S1", pm, name() + " does not map the required service " + path(s)));
                }
            }
        }

        private void admin(Provider provider, List<Class> sources) {
            AdminMapping admin = pm.getAdmin();
            if (admin != null) {
                for (String feature : List.of("friendlyName", "description", "latitude", "longitude")) {
                    ValueSource vs = (ValueSource) admin.eGet(admin.eClass().getEStructuralFeature(feature));
                    if (vs != null) {
                        source(vs, sources, "admin " + feature, admin, false);
                    }
                }
                if ((admin.getLatitude() == null) != (admin.getLongitude() == null)) {
                    findings.add(error("S6", admin, name() + ": a location needs latitude and longitude"));
                }
            }
            if (provider.isFriendlyNameRequired() && (admin == null || admin.getFriendlyName() == null)) {
                findings.add(error("S6", pm, name() + ": provider " + provider.getName() + " requires a friendly name"));
            }
            if (provider.isLocationRequired() && (admin == null || admin.getLatitude() == null || admin.getLongitude() == null)) {
                findings.add(error("S6", pm, name() + ": provider " + provider.getName() + " requires a location"));
            }
        }

        /** S2 for a value source and its fallbacks; the arrival time only where {@code time} allows it. */
        private void source(ValueSource vs, List<Class> contexts, String what, EObject at, boolean time) {
            String label = what;
            for (ValueSource s : ValueSources.chain(vs)) {
                if (s instanceof PathSource path) {
                    checkPath(path, contexts, label, at);
                } else if (s instanceof ArrivalTimeSource && !time) {
                    findings.add(error("S2", at, name() + ": " + label + " is the arrival time, which only a "
                            + "timestamp can be"));
                }
                label = label + " fallback";
            }
        }

        private void checkPath(PathSource vs, List<Class> contexts, String what, EObject at) {
            List<StructuralFeature> path = vs.getPath();
            if (path.isEmpty()) {
                findings.add(error("S2", at, name() + ": " + what + " is a path without a step"));
                return;
            }
            for (Class context : contexts) {
                Classifier current = context;
                for (int i = 0; i < path.size(); i++) {
                    StructuralFeature step = path.get(i);
                    if (step.eIsProxy()) {
                        findings.add(error("S2", at, name() + ": " + what + ": step " + (i + 1) + " "
                                + ((InternalEObject) step).eProxyURI() + " does not resolve"));
                        break;
                    }
                    if (current == null) {
                        findings.add(error("S2", at, name() + ": " + what + ": step " + step.getName()
                                + " follows a step without a type"));
                        break;
                    }
                    if (SensinactBindings.isStepFrom(step, current)) {
                        if (i == 0) {
                            read.computeIfAbsent(context, k -> new LinkedHashSet<>()).add(step);
                        }
                    } else if (SensinactBindings.isDowncastFrom(step, current)) {
                        findings.add(warning("S2", at, name() + ": " + what + ": " + step.getName() + " is a feature of "
                                + SensinactBindings.holder(step).getName() + ", a subclass of " + current.getName()
                                + ": only instances of the subclass have it"));
                    } else {
                        findings.add(error("S2", at, name() + ": " + what + ": " + step.getName()
                                + " is no feature of " + current.getName()));
                        break;
                    }
                    current = step.getType();
                }
            }
            int many = 0;
            for (StructuralFeature step : path) {
                if (SensinactStructure.isMany(step)) {
                    many++;
                }
            }
            if (many > 1) {
                findings.add(error("S2", at, name() + ": " + what + " crosses " + many
                        + " steps that hold many values; one element is picked per path"));
            }
            if (many > 0 && vs.getFilter() == null && !vs.isSetIndex()) {
                findings.add(error("S2", at, name() + ": " + what + " crosses a step that holds many values without "
                        + "an index or a filter"));
            }
            if (many == 0 && (vs.getFilter() != null || vs.isSetIndex())) {
                findings.add(error("S2", at, name() + ": " + what + " picks an element, but no step holds many values"));
            }
            if (vs.getFilter() != null && (!SensinactBindings.FILTER_LANGUAGE.equals(vs.getFilter().getLanguage())
                    || vs.getFilter().getBody() == null || !FILTER.matcher(vs.getFilter().getBody()).matches())) {
                findings.add(error("S2", at, name() + ": " + what + ": the filter is no 'path=literal' in "
                        + SensinactBindings.FILTER_LANGUAGE));
            }
            if (vs.isSetIndex() && vs.getIndex() < 0) {
                findings.add(error("S2", at, name() + ": " + what + ": the index is negative"));
            }
        }

        private void types(ResourceMapping rm) {
            Resource r = rm.getResource();
            if (r == null || r.getType() == null) {
                return;
            }
            ValueSource vs = rm.getValue();
            Classifier from = SensinactBindings.typeOf(vs);
            if (from != null && !convertible(from, r.getType())) {
                findings.add(warning("S3", rm, name() + ": " + rm.getName() + " reads a " + from.getName()
                        + " into a resource of type " + r.getType().getName()));
            }
            for (ValueSource s : ValueSources.chain(vs)) {
                if (s instanceof LiteralSource literal && isNumeric(r.getType()) && !isNumber(literal.getLiteral())) {
                    findings.add(warning("S3", rm, name() + ": " + rm.getName() + " gives the literal '"
                            + literal.getLiteral() + "' to a resource of type " + r.getType().getName()));
                }
            }
        }

        /** S5 for a timestamp and its fallbacks. */
        private void timestamp(ValueSource vs, String what, EObject at) {
            String label = what;
            for (ValueSource s : ValueSources.chain(vs)) {
                if (s instanceof PathSource path) {
                    timePath(path, label, at);
                } else if (s instanceof LiteralSource literal) {
                    try {
                        Instant.parse(literal.getLiteral());
                    } catch (DateTimeParseException e) {
                        findings.add(warning("S5", at, name() + ": " + label + ": the literal '" + literal.getLiteral()
                                + "' is no ISO-8601 instant with a zone; it is read as no timestamp"));
                    }
                }
                label = label + " fallback";
            }
        }

        private void timePath(PathSource vs, String what, EObject at) {
            Classifier type = SensinactBindings.typeOf(vs);
            if (type != null) {
                String n = lower(type.getName());
                if (!TIME.contains(n) && !WHOLE.contains(n) && !TEXT.contains(n)) {
                    findings.add(warning("S5", at, name() + ": " + what + " ends at a " + type.getName()
                            + ", neither a time, a whole number of milliseconds nor a text"));
                }
            }
            if (vs.getFormat() != null) {
                try {
                    DateTimeFormatter.ofPattern(vs.getFormat());
                    if (!vs.getFormat().replaceAll("'[^']*'", "").matches(".*[XxZzOV].*")) {
                        findings.add(warning("S5", at, name() + ": " + what + ": the format '" + vs.getFormat()
                                + "' has no zone; the time is read as UTC"));
                    }
                } catch (IllegalArgumentException e) {
                    findings.add(error("S5", at, name() + ": " + what + ": the format '" + vs.getFormat()
                            + "' is no DateTimeFormatter pattern"));
                }
            }
        }

        private void unread() {
            for (Map.Entry<Classifier, Set<StructuralFeature>> e : read.entrySet()) {
                List<String> unread = new ArrayList<>();
                for (Attribute a : SensinactStructure.allAttributes(e.getKey())) {
                    if (a.getType() instanceof DataType && !SensinactStructure.isMany(a) && !e.getValue().contains(a)) {
                        unread.add(a.getName());
                    }
                }
                if (!unread.isEmpty()) {
                    findings.add(warning("S8", pm, name() + " reads no path from " + e.getKey().getName() + "."
                            + String.join(", " + e.getKey().getName() + ".", unread)));
                }
            }
        }

        private String name() {
            return "ProviderMapping '" + pm.getName() + "'";
        }
    }

    private static boolean convertible(Classifier from, Classifier to) {
        if (from == to) {
            return true;
        }
        String f = lower(from.getName());
        String t = lower(to.getName());
        if (f.equals(t) || TEXT.contains(t)) {
            return true;
        }
        if (NUMERIC.contains(f) && NUMERIC.contains(t)) {
            return true;
        }
        return from instanceof Enumeration && TEXT.contains(t);
    }

    private static boolean isNumeric(Classifier type) {
        return NUMERIC.contains(lower(type.getName()));
    }

    private static boolean isNumber(String literal) {
        try {
            Double.parseDouble(literal.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * A type name for comparison: lower case, and an Ecore type name in its plain form
     * ({@code EDouble} → {@code double}, {@code EFloatObject} → {@code float}).
     */
    private static String lower(String s) {
        if (s == null) {
            return "";
        }
        String n = s;
        if (n.length() > 1 && n.charAt(0) == 'E' && Character.isUpperCase(n.charAt(1))) {
            n = n.substring(1);
            if (n.endsWith("Object")) {
                n = n.substring(0, n.length() - "Object".length());
            }
        }
        return n.toLowerCase(Locale.ROOT);
    }

    private static Finding error(String rule, EObject element, String message) {
        return new Finding(Severity.ERROR, rule, element, message + ".");
    }

    private static Finding warning(String rule, EObject element, String message) {
        return new Finding(Severity.WARNING, rule, element, message + ".");
    }

    /** The names of an element and its named containers, dot separated. */
    static String path(EObject element) {
        Deque<String> names = new ArrayDeque<>();
        for (EObject o = element; o != null; o = o.eContainer()) {
            if (o instanceof ModelElement me && me.getName() != null) {
                names.addFirst(me.getName());
            }
        }
        return String.join(".", names);
    }
}
