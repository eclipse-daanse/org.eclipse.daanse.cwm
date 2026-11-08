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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.daanse.cwm.emf.standalone.CwmPath;
import org.eclipse.daanse.cwm.emf.standalone.EcoreIndex;
import org.eclipse.daanse.cwm.model.daanse.governance.lifecycle.util.LifecycleModelValidator;
import org.eclipse.daanse.cwm.model.daanse.governance.obligations.util.ObligationsModelValidator;
import org.eclipse.daanse.cwm.model.daanse.governance.recording.util.RecordingModelValidator;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.ProviderMapping;
import org.eclipse.daanse.cwm.model.daanse.mapping.sensinact.util.SensinactMappingModelValidator;
import org.eclipse.daanse.cwm.model.daanse.units.util.UnitsModelValidator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.fennec.event.atlas.model.mapping.MappingProfile;

/**
 * Converts CWM sensiNact mappings into the provider mappings and profiles of Eclipse Fennec
 * event.atlas.
 *
 * <p>The models the mappings live in are checked first — the sensiNact validator (rules S1 to S8),
 * the lifecycle validator (L1 to L10), the recording validator (R1 to R7), the obligations
 * validator (O1 to O9) and the units validator (U1 to U7); their findings become notes of rule {@link AtlasNotes#VALIDATION}, and an error stops
 * the conversion with an {@link InvalidMappingException} unless {@code lenient}. A rule, or a part of
 * one, that Fennec cannot carry is an error of the conversion ({@link AtlasNotes#RULE}); with
 * {@code lenient} it is a warning and the part is left out. Errors of the conversion — a rule Fennec
 * cannot carry, an unresolved class or feature, a name used twice — are notes of the result
 * ({@link Result#errors()}): the conversion goes on so that every error is reported, and
 * {@link AtlasGenerator} and the Maven goal write nothing while there is one. Source classes and
 * features are found in the Ecore model of the class model by names ({@link EcoreIndex}).</p>
 */
public final class CwmToAtlas {

    /**
     * The output of a conversion.
     *
     * @param mappings the provider mappings, one per CWM provider mapping, in order
     * @param profiles the profiles, one per provider, when the options ask for them
     * @param notes    the findings
     */
    public record Result(List<org.eclipse.fennec.event.atlas.model.mapping.ProviderMapping> mappings,
            List<MappingProfile> profiles, List<Note> notes) {

        /** The notes of severity error. */
        public List<Note> errors() {
            return notes.stream().filter(n -> n.severity() == Note.Severity.ERROR).toList();
        }
    }

    private CwmToAtlas() {
    }

    /** Validates and converts; validation errors stop it. */
    public static Result convert(Collection<ProviderMapping> mappings, EcoreIndex index, Options options) {
        return convert(mappings, index, options, false);
    }

    /**
     * Validates and converts; with {@code lenient} validation errors become notes and the conversion
     * goes on, and a rule Fennec cannot carry is a warning instead of an error.
     */
    public static Result convert(Collection<ProviderMapping> mappings, EcoreIndex index, Options options,
            boolean lenient) {
        List<Note> notes = validate(mappings);
        List<Note> errors = notes.stream().filter(n -> n.severity() == Note.Severity.ERROR).toList();
        if (!errors.isEmpty() && !lenient) {
            throw new InvalidMappingException(errors);
        }
        Result result = new AtlasConversion(mappings, index, options, lenient).run();
        notes.addAll(result.notes());
        return new Result(result.mappings(), result.profiles(), List.copyOf(notes));
    }

    /** The findings of the five validators on the models the mappings live in, as notes. */
    public static List<Note> validate(Collection<ProviderMapping> mappings) {
        Set<EObject> roots = new LinkedHashSet<>();
        mappings.forEach(m -> roots.add(EcoreUtil.getRootContainer(m)));
        List<Note> notes = new ArrayList<>();
        for (EObject root : roots) {
            for (SensinactMappingModelValidator.Finding f : SensinactMappingModelValidator.check(root)) {
                notes.add(note(f.severity() == SensinactMappingModelValidator.Severity.ERROR, f.element(), f.rule(),
                        f.message()));
            }
            List<LifecycleModelValidator.Finding> lifecycle = new ArrayList<>(LifecycleModelValidator.check(root));
            lifecycle.addAll(RecordingModelValidator.check(root));
            lifecycle.addAll(ObligationsModelValidator.check(root));
            for (LifecycleModelValidator.Finding f : lifecycle) {
                notes.add(note(f.severity() == LifecycleModelValidator.Severity.ERROR, f.element(), f.rule(),
                        f.message()));
            }
            for (UnitsModelValidator.Finding f : UnitsModelValidator.check(root)) {
                notes.add(note(f.severity() == UnitsModelValidator.Severity.ERROR, f.element(), f.rule(), f.message()));
            }
        }
        return notes;
    }

    private static Note note(boolean error, EObject element, String rule, String message) {
        return new Note(error ? Note.Severity.ERROR : Note.Severity.WARNING, AtlasNotes.VALIDATION,
                CwmPath.of(element), rule + " " + message);
    }

    /** The provider mappings in a resource, in document order. */
    public static List<ProviderMapping> mappingsIn(Resource resource) {
        List<ProviderMapping> out = new ArrayList<>();
        resource.getAllContents().forEachRemaining(o -> {
            if (o instanceof ProviderMapping pm) {
                out.add(pm);
            }
        });
        return out;
    }
}
