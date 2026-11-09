/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.emf.ecore;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.emf.ecore.CorpusModels.File;
import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * The cases of the test corpus: a corpus file converted with one set of options. The expected
 * output of a case lies in {@code src/test/resources/corpus/expected/<id>}: the notes of the
 * conversion in {@code notes.txt} and, when the conversion has no errors, the written files.
 * The cases with errors do not validate the result, so their notes are those of the conversion
 * alone.
 */
enum CorpusCase {

    PACKAGES("packages", File.PACKAGES, Options.defaults()),
    PACKAGES_SPLIT("packages-split", File.PACKAGES,
            Options.defaults().withSplitPackages(true).withBasePackage("org.example"), null,
            Map.of("classes", "shop-classes", "enumerations", "shop-enums", "pricing", "shop-pricing", "types",
                    "shop-types")),
    PACKAGES_GENMODEL("packages-genmodel", File.PACKAGES,
            Options.defaults().withBasePackage("org.example").withGenModelAnnotations(Map.of("oSGiCompatible",
                    "true", "copyrightText", "Example", "loadInitialization", "false", "prefix", "Ignored")),
            new EcoreWriter.GenModelSettings("org.example", null, "/shop/src/main/java", "shop"), Map.of()),
    CLASSIFIERS("classifiers", File.CLASSIFIERS, Options.defaults()),
    CLASSIFIERS_INVALID("classifiers-invalid", File.CLASSIFIERS_INVALID, Options.defaults().withValidate(false)),
    ATTRIBUTES("attributes", File.ATTRIBUTES, Options.defaults()),
    RELATIONSHIPS("relationships", File.RELATIONSHIPS, Options.defaults()),
    RELATIONSHIPS_SPLIT("relationships-split", File.RELATIONSHIPS, Options.defaults().withSplitPackages(true), null,
            Map.of("sales", "trade-sales", "catalog", "trade-catalog")),
    RELATIONSHIPS_INVALID("relationships-invalid", File.RELATIONSHIPS_INVALID,
            Options.defaults().withPackages(List.of("sales")).withValidate(false)),
    BEHAVIOUR("behaviour", File.BEHAVIOUR, Options.defaults()),
    BEHAVIOUR_INVALID("behaviour-invalid", File.BEHAVIOUR_INVALID, Options.defaults().withValidate(false)),
    CONSTRAINTS("constraints", File.CONSTRAINTS, Options.defaults()),
    CONSTRAINTS_OFF("constraints-off", File.CONSTRAINTS, Options.defaults().withConstraints(false)),
    DOCUMENTATION("documentation", File.DOCUMENTATION, Options.defaults()),
    DOCUMENTATION_OFF("documentation-off", File.DOCUMENTATION, Options.defaults().withDocumentation(false)),
    NAMES("names", File.NAMES, Options.defaults()),
    SELECTION_MISSING("selection-missing", File.SELECTION,
            Options.defaults().withPackages(List.of("a")).withValidate(false)),
    SELECTION("selection", File.SELECTION, Options.defaults().withPackages(List.of("a", "b", "types"))),
    SELECTION_REUSE("selection-reuse", File.SELECTION,
            Options.defaults().withPackages(List.of("a", "b")).withReuseEcoreDataTypes(true)),
    ECORE_FORMS("ecore-forms", File.ECORE_FORMS, Options.defaults());

    final String id;
    final File file;
    final Options options;
    final EcoreWriter.GenModelSettings genModel;
    final Map<String, String> fileNames;

    CorpusCase(String id, File file, Options options) {
        this(id, file, options, null, Map.of());
    }

    CorpusCase(String id, File file, Options options, EcoreWriter.GenModelSettings genModel,
            Map<String, String> fileNames) {
        this.id = id;
        this.file = file;
        this.options = options;
        this.genModel = genModel;
        this.fileNames = fileNames;
    }

    /** Whether the corpus files and the expected output are written anew instead of compared. */
    static final boolean UPDATE = Boolean.getBoolean("cwm.ecore.corpus.update");

    /** The corpus directory in the source tree, so that an update reaches the checked-in files. */
    static Path dir() {
        return Path.of(System.getProperty("user.dir"), "src", "test", "resources", "corpus");
    }

    Path expected() {
        return dir().resolve("expected").resolve(id);
    }

    /** Loads the corpus file into a new resource set and converts it with the options of the case. */
    Result convert() {
        CwmPackages.registerKnown();
        ResourceSet rs = CwmPackages.newResourceSet();
        Resource r = CwmPackages.load(rs, dir().resolve(file.fileName));
        EcoreUtil.resolveAll(rs);
        return CwmToEcore.convert(CwmToEcore.packagesOf(r), options);
    }

    @Override
    public String toString() {
        return id;
    }
}
