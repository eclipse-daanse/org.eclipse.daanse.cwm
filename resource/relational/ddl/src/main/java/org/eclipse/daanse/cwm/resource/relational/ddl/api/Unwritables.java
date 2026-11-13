/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.resource.relational.ddl.api;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The cases one run of a generator finds that the dialect cannot write ({@link Unwritable}), and what
 * becomes of them when the run ends ({@link #finish()}). Where the settings are strict
 * ({@link DdlSettings#strict()}), the run fails with one {@link UnwritableException} that names all of
 * them. Otherwise what is written in their place stands, and the log names each case, as a warning,
 * where the run reports them: a run that writes a drop script finds the cases of the create script of
 * the same model and leaves them to that one, so the log names each case once.
 *
 * <p>
 * A run made of other runs, such as a migration or the scripts of several dialects, collects theirs
 * ({@link #collect(Supplier)}): strict, it fails once, with the cases of all of them. Not thread-safe:
 * one per run.
 * </p>
 */
public final class Unwritables {

    private static final Logger LOGGER = LoggerFactory.getLogger(Unwritables.class);

    private final boolean strict;
    private final boolean report;
    private final Set<Unwritable> found = new LinkedHashSet<>();

    /**
     * @param strict whether the run fails where it found a case
     * @param report whether the log names the cases where the run does not fail
     */
    public Unwritables(boolean strict, boolean report) {
        this.strict = strict;
        this.report = report;
    }

    /** For a run that writes what creates: strict as {@code settings} say, and reporting. */
    public static Unwritables forCreate(DdlSettings settings) {
        return new Unwritables(settings.strict(), true);
    }

    /** For a run that writes what drops: strict as {@code settings} say, and reporting nothing. */
    public static Unwritables forDrop(DdlSettings settings) {
        return new Unwritables(settings.strict(), false);
    }

    /** Adds a case; a case found again is the same case. */
    public void add(Unwritable unwritable) {
        found.add(unwritable);
    }

    /**
     * Runs {@code generation}, a run of its own, and takes the cases it fails with into this run;
     * nothing comes of a generation that fails so, as nothing of this run is written either.
     */
    public <T> List<T> collect(Supplier<List<T>> generation) {
        try {
            return generation.get();
        } catch (UnwritableException e) {
            found.addAll(e.cases());
            return List.of();
        }
    }

    /** The cases found so far, in the order they were found. */
    public List<Unwritable> cases() {
        return List.copyOf(found);
    }

    /**
     * Ends the run: fails where it is strict and found a case, otherwise names each case in the log
     * where it reports them.
     *
     * @throws UnwritableException strict, with every case of the run
     */
    public void finish() {
        if (found.isEmpty()) {
            return;
        }
        if (strict) {
            throw new UnwritableException(List.copyOf(found));
        }
        if (report) {
            found.forEach(c -> LOGGER.warn("db={} — {}: cannot write {}; {}", c.dialect(), c.element(), c.what(),
                    c.instead()));
        }
    }
}
