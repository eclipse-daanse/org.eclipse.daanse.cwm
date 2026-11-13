/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.ddl.render;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ElementRule;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.QualifiedNames;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.RuleVocabulary;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selections;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.UnmatchedRulesException;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeRules;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlStatement;
import org.eclipse.daanse.cwm.resource.relational.ddl.render.Dialects.NamedDialect;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Writes the DDL of a CWM model, cut to a selection, as script files: one {@code CREATE} script
 * per SQL dialect, {@code <baseName>-<dialect>.sql}, and on request the matching
 * {@code <baseName>-<dialect>-drop.sql}. Each script starts with a comment naming what it holds;
 * every statement ends with the delimiter and a blank line, so the script runs as it is in a SQL
 * console or a migration tool. One call cuts the model once, writes the files and hands back their
 * statements with the cut model, so a caller that also runs them renders nothing twice.
 *
 * <pre>{@code
 * Resource model = CwmPackages.loadModel(Path.of("catalog.xmi"), loader, null);
 * CwmDdlScripts scripts = new CwmDdlScripts(List.of("postgresql", "h2"), DdlSettings.defaults(), ";");
 * Written<ModelElement> written = scripts.write(model, CwmDdlScripts.selection(List.of("schema:sales")),
 *         Path.of("ddl"), null, true);                         // catalog-postgresql.sql, catalog-h2.sql, …
 * }</pre>
 *
 * <p>The rules of a selection read as on the reading side ({@link #vocabulary()}): the words of the
 * relational, business information and privilege models. Roles and grants are part of the scripts
 * when the cut model holds them.</p>
 */
public final class CwmDdlScripts {

    /** Whether a script creates or drops. */
    public enum Kind {
        CREATE, DROP
    }

    /**
     * One written script.
     *
     * @param file       where it was written
     * @param dialect    the dialect's display name
     * @param kind       whether it creates or drops
     * @param statements its statements, each with the element it is written for
     */
    public record Script(Path file, String dialect, Kind kind, List<DdlStatement> statements) {

        public Script {
            statements = List.copyOf(statements);
        }

        /** The SQL of the statements, without delimiter, in their order. */
        public List<String> sql() {
            return DdlStatement.sql(statements);
        }
    }

    /**
     * What one {@link CwmDdlScripts#write} did.
     *
     * @param selected the model the scripts were written from, cut to the selection, with what the
     *                 cut left out and retained
     * @param scripts  the written scripts, the {@code CREATE} ones first, in the order of the dialects
     */
    public record Written<T extends ModelElement>(Selected<T> selected, List<Script> scripts) {

        public Written {
            Objects.requireNonNull(selected, "selected");
            scripts = List.copyOf(scripts);
        }

        /** The scripts of {@code kind}, in the order of the dialects. */
        public List<Script> scripts(Kind kind) {
            return scripts.stream().filter(s -> s.kind() == kind).toList();
        }
    }

    private final List<String> ids = new ArrayList<>();
    private final CwmDdlRenderer renderer;
    private final String delimiter;

    /**
     * Scripts for the dialects {@code dialectIds} ({@link Dialects#IDS} and their aliases, any case;
     * empty or {@code null}: {@code ansi}), rendered with {@code settings}: with
     * {@link DdlSettings#strict()}, writing fails where a dialect cannot write what the model holds,
     * otherwise that is written plainer or left out, and the log names each case.
     *
     * @param delimiter written after every statement that does not end with it; {@code null} or
     *                  empty: none
     * @throws IllegalArgumentException for an unknown dialect, naming the known ones
     */
    public CwmDdlScripts(List<String> dialectIds, DdlSettings settings, String delimiter) {
        List<NamedDialect> named = new ArrayList<>();
        for (String id : dialectIds == null || dialectIds.isEmpty() ? List.of("ansi") : dialectIds) {
            ids.add(Dialects.id(id));
            named.add(Dialects.require(id));
        }
        this.renderer = new CwmDdlRenderer(named, settings == null ? DdlSettings.defaults() : settings);
        this.delimiter = delimiter == null ? "" : delimiter;
    }

    /**
     * The words a selection for DDL is written in: those of the relational model, the business
     * information and the privileges, {@link PrivilegeRules#VOCABULARY}, as on the reading side.
     */
    public static RuleVocabulary vocabulary() {
        return PrivilegeRules.VOCABULARY;
    }

    /**
     * The selection the rule texts {@code rules} describe in {@link #vocabulary()}, one rule per
     * text or line: {@code schema:sales}, {@code !table:tmp_*}, {@code !is:privilege}.
     *
     * @throws IllegalArgumentException for a rule the vocabulary does not know
     */
    public static Selection selection(List<String> rules) {
        return Selection.parse(vocabulary(), rules == null ? List.of() : rules);
    }

    /**
     * Cuts the model of {@code resource} to {@code selection} and writes its scripts, as
     * {@link #write(ModelElement, Selection, Path, String, boolean)} does. A resource with several
     * model elements at its root is cut as one model: a copy of them, side by side below a package
     * without a name, which adds nothing to their qualified names.
     *
     * @param baseName {@code null} or blank: the resource's file name without extension
     *                 ({@code ddl} for a resource without file)
     * @throws IllegalArgumentException when the resource holds no relational schema
     */
    public Written<ModelElement> write(Resource resource, Selection selection, Path directory, String baseName,
            boolean drop) throws IOException {
        Objects.requireNonNull(resource, "resource");
        List<ModelElement> roots = resource.getContents().stream().filter(ModelElement.class::isInstance)
                .map(ModelElement.class::cast).toList();
        String source = fileName(resource);
        ModelElement model = roots.size() == 1 ? roots.get(0) : together(roots);
        return write(model, selection, directory, baseName != null && !baseName.isBlank() ? baseName : base(source),
                drop, source);
    }

    /**
     * Cuts {@code model} to {@code selection} ({@link CwmDdlRenderer#select}) and writes the
     * {@code CREATE} script of the cut model per dialect into {@code directory} and, with
     * {@code drop}, the {@code DROP} script, its exact reverse. The file names take the dialect's
     * id, also for an alias ({@code pg} writes {@code <baseName>-postgresql.sql}).
     *
     * @param baseName {@code null} or blank: the name of the model's file without extension
     *                 ({@code ddl} when it is in no file)
     * @return the cut model and the written scripts, the {@code CREATE} ones first
     * @throws UnmatchedRulesException  when an include rule matches nothing in the model
     * @throws IllegalArgumentException when the model holds no relational schema, or as
     *                                  {@link CwmDdlRenderer#scripts}
     * @throws IllegalStateException    as {@link CwmDdlRenderer#scripts}; an
     *                                  {@code UnwritableException} with every case where the
     *                                  settings are strict and a dialect cannot write what the
     *                                  model holds. No file is written then.
     */
    public <T extends ModelElement> Written<T> write(T model, Selection selection, Path directory, String baseName,
            boolean drop) throws IOException {
        Objects.requireNonNull(model, "model");
        String source = fileName(model.eResource());
        return write(model, selection, directory, baseName != null && !baseName.isBlank() ? baseName : base(source),
                drop, source);
    }

    private <T extends ModelElement> Written<T> write(T model, Selection selection, Path directory, String base,
            boolean drop, String source) throws IOException {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(directory, "directory");
        String where = source != null ? source : QualifiedNames.of(model).isEmpty() ? "the model"
                : QualifiedNames.of(model);
        if (Selections.list(model, Selection.all(), Schema.class).isEmpty()) {
            throw new IllegalArgumentException("no relational schema in " + where);
        }
        Selected<T> cut = CwmDdlRenderer.select(model, selection);
        List<DdlScript> scripts = renderer.scripts(cut.model());
        String what = String.join(", ",
                Selections.list(cut.model(), Selection.all(), Schema.class).stream().map(QualifiedNames::of).toList())
                + (source == null ? "" : " from " + source);
        String rules = rules(selection);
        Files.createDirectories(directory);
        List<Script> written = new ArrayList<>();
        for (int i = 0; i < scripts.size(); i++) {
            written.add(write(directory.resolve(base.trim() + "-" + ids.get(i) + ".sql"), scripts.get(i).dialect(),
                    Kind.CREATE, scripts.get(i).create(), what, rules));
        }
        if (drop) {
            for (int i = 0; i < scripts.size(); i++) {
                written.add(write(directory.resolve(base.trim() + "-" + ids.get(i) + "-drop.sql"),
                        scripts.get(i).dialect(), Kind.DROP, scripts.get(i).drop(), what, rules));
            }
        }
        return new Written<>(cut, written);
    }

    private Script write(Path file, String dialect, Kind kind, List<DdlStatement> statements, String what,
            String rules) throws IOException {
        StringBuilder sql = new StringBuilder("-- ").append(kind).append(" script for ").append(dialect).append(": ")
                .append(what).append('\n');
        if (!rules.isEmpty()) {
            sql.append("-- selected by ").append(rules).append('\n');
        }
        sql.append('\n');
        for (DdlStatement statement : statements) {
            String s = statement.sql().strip();
            sql.append(s);
            if (!delimiter.isEmpty() && !s.endsWith(delimiter)) {
                sql.append(delimiter);
            }
            sql.append("\n\n");
        }
        Files.writeString(file, sql.toString(), StandardCharsets.UTF_8);
        return new Script(file, dialect, kind, statements);
    }

    /** The rules of {@code selection} as written, exclude rules with a leading {@code !}; empty for none. */
    private static String rules(Selection selection) {
        return Stream.concat(selection.includes().stream().map(ElementRule::describe),
                selection.excludes().stream().map(r -> "!" + r.describe())).collect(Collectors.joining(", "));
    }

    /** A copy of {@code roots}, side by side below a package without a name. */
    private static ModelElement together(Collection<ModelElement> roots) {
        EcoreUtil.Copier copier = new EcoreUtil.Copier();
        Collection<EObject> copies = copier.copyAll(roots);
        copier.copyReferences();
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package holder = CoreFactory.eINSTANCE.createPackage();
        copies.forEach(copy -> holder.getOwnedElement().add((ModelElement) copy));
        return holder;
    }

    private static String base(String file) {
        if (file == null) {
            return "ddl";
        }
        int dot = file.lastIndexOf('.');
        return dot > 0 ? file.substring(0, dot) : file;
    }

    /** The file name of {@code resource}, {@code null} for none. */
    private static String fileName(Resource resource) {
        return resource == null || resource.getURI() == null ? null : URI.decode(resource.getURI().lastSegment());
    }
}
