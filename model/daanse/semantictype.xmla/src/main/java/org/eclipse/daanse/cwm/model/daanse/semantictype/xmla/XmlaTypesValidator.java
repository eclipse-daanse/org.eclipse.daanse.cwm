/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.semantictype.xmla;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.ExternalType;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.SemanticType;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.Typing;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.SemanticTypeModelValidator;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.SemanticTypes;
import org.eclipse.daanse.cwm.model.daanse.semantictype.base.util.Typings;
import org.eclipse.daanse.cwm.model.daanse.validation.Finding;
import org.eclipse.daanse.cwm.model.daanse.validation.Findings;
import org.eclipse.daanse.cwm.model.daanse.validation.Rule;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

/**
 * Checks the typings and types of a model that name or reach the types of an Analysis Services server. The rules
 * that hold for any vocabulary are those of {@link SemanticTypeModelValidator}; run it with
 * {@link ServerTypes#LIBRARIES} as the delivered libraries where the model does not hold the server's libraries.
 *
 * <p>A type reaches a list of the server where it is a type of it or reaches one through its same, external or
 * broader types ({@link XmlaCodes#lists}). An element named by a typing fits a list: an attribute, a column, a field,
 * a data type or a class that is no table or view fits the attribute list (the values are of the type); a class (a
 * table, a view, an OLAP dimension) fits the dimension list (the rows or members are); an element that is neither
 * feature nor namespace (it stands for one account type) fits the account list. One type of one's own may reach
 * several lists, and then types a column and a table alike.</p>
 *
 * <ul>
 * <li>{@code T1}, {@code T2}, {@code T3} an element named by a typing fits one of the lists its type reaches; where
 * it fits none, a finding for each list: T1 the attribute list, T2 the dimension list, T3 the account list.</li>
 * <li>{@code T4} the typings of an element reach at most one type of each list: one LEVEL_TYPE for a level, one
 * DIMENSION_TYPE for a dimension.</li>
 * <li>{@code T6} (warning) a typing whose type is the server's type Regular says nothing: an untyped element is
 * regular.</li>
 * <li>{@code T7} (warning) the server's types ScdStartDate, ScdEndDate and ScdStatus come from the history rules of
 * a model (valid from, valid to, current flag or status), not from a typing.</li>
 * <li>{@code T15} an external type that names a library under {@value ServerTypes#URI} names one of the three lists
 * and a type in it.</li>
 * </ul>
 */
public final class XmlaTypesValidator {

    /**
     * The module the rules of this validator belong to: the rules of semantic types, beside those of
     * {@link SemanticTypeModelValidator}, whose codes they do not repeat.
     */
    public static final String MODULE = SemanticTypeModelValidator.MODULE;

    /** An element typed by an attribute type of the server is an attribute, a column, a field or a data type. */
    public static final Rule T1 = Rule.error(MODULE, "T1",
            "A typing whose type reaches the server's attribute types names attributes, columns, fields, data types "
                    + "or classes that are no table or view.");

    /** An element typed by a dimension type of the server is a table, a view or a dimension. */
    public static final Rule T2 = Rule.error(MODULE, "T2",
            "A typing whose type reaches the server's dimension types names classes: tables, views, dimensions.");

    /** An element typed by an account type of the server stands for one account type. */
    public static final Rule T3 = Rule.error(MODULE, "T3",
            "A typing whose type reaches the server's account types names elements that stand for one account "
                    + "type.");

    /** The typings of an element reach one type of each list of the server. */
    public static final Rule T4 = Rule.error(MODULE, "T4",
            "The typings of an element reach at most one type of each list of the server.");

    /** A typing of the server's type Regular says nothing. */
    public static final Rule T6 = Rule.warning(MODULE, "T6", "A typing of the server's type Regular says nothing.");

    /** The server's history types come from the history rules. */
    public static final Rule T7 = Rule.warning(MODULE, "T7",
            "The history types come from the history rules, not from a typing.");

    /** An external type naming the server names one of its lists and a type in it. */
    public static final Rule T15 = Rule.error(MODULE, "T15",
            "An external type that names a library of the server names one of its three lists and a type in it.");

    /** Every rule of this validator, in the order of their codes. */
    public static final List<Rule> RULES = List.of(T1, T2, T3, T4, T6, T7, T15);

    private static final String RELATIONAL = "http://www.omg.org/spec/CWM/1.1/resource/relational";

    private XmlaTypesValidator() {
    }

    /** All findings for the typings and types in {@code root} and below that name or reach a type of the server. */
    public static List<Finding> check(EObject root) {
        List<Finding> findings = new ArrayList<>();
        Map<ModelElement, Map<String, Set<String>>> byElement = new LinkedHashMap<>();
        for (Typing typing : Typings.all(root)) {
            SemanticType type = typing.getType();
            if (type == null || type.eIsProxy()) {
                continue;
            }
            Map<String, String> reached = new LinkedHashMap<>();
            for (String list : XmlaCodes.lists(type)) {
                reached.put(list, XmlaCodes.serverName(type, list).orElseThrow());
            }
            for (ModelElement element : typing.getModelElement()) {
                if (element.eIsProxy() || reached.isEmpty()) {
                    continue;
                }
                Map<String, Set<String>> lists = byElement.computeIfAbsent(element, e -> new LinkedHashMap<>());
                reached.forEach((list, name) -> lists.computeIfAbsent(list, l -> new LinkedHashSet<>()).add(name));
                if (reached.keySet().stream().noneMatch(list -> fits(element, list))) {
                    for (String list : reached.keySet()) {
                        findings.add(rule(list).at(typing, path(typing) + " of the type " + type.getName()
                                + ", which reaches the server's " + word(list) + " type " + reached.get(list)
                                + ", names " + element.eClass().getName() + " " + path(element)
                                + "; it names " + fitting(list)));
                    }
                }
            }
            ServerTypes.listOf(type).ifPresent(list -> {
                String name = ServerTypes.serverName(type);
                if (ServerTypes.REGULAR.equals(name)) {
                    findings.add(T6.at(typing, path(typing) + " types as Regular, which an untyped element is"));
                }
                if (ServerTypes.ATTRIBUTE_URI.equals(list) && ServerTypes.HISTORY.contains(name)) {
                    findings.add(T7.at(typing, path(typing) + " types as " + name
                            + ", which the history rules of a model say"));
                }
            });
        }
        byElement.forEach((element, lists) -> lists.forEach((list, names) -> {
            if (names.size() > 1) {
                findings.add(T4.at(element, path(element) + " is typed as the server's " + word(list) + " types "
                        + String.join(", ", names) + "; " + holder(list) + " has one"));
            }
        }));
        for (SemanticType type : SemanticTypes.all(root)) {
            for (ExternalType external : type.getSameAsExternal()) {
                String uri = external.getLibraryUri();
                if (uri == null || !uri.startsWith(ServerTypes.URI)) {
                    continue;
                }
                if (!ServerTypes.LISTS.contains(uri)) {
                    findings.add(T15.at(type, path(type) + " names " + uri + ", which is none of the server's lists "
                            + String.join(", ", ServerTypes.LISTS)));
                } else if (XmlaCodes.external(external).isEmpty()) {
                    findings.add(T15.at(type, path(type) + " names a type of " + uri + " that is not in it: "
                            + (external.getSourceName() != null ? external.getSourceName()
                                    : external.isSetSourceNumber() ? "number " + external.getSourceNumber()
                                            : "neither name nor number")));
                }
            }
        }
        return findings;
    }

    /** The errors of {@link #check(EObject)} as {@code "rule message"} lines. */
    public static List<String> validate(EObject root) {
        return Findings.errorLines(check(root));
    }

    /** Whether {@code element} fits the server's list {@code list}. */
    static boolean fits(ModelElement element, String list) {
        boolean cls = element instanceof org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
        if (ServerTypes.ATTRIBUTE_URI.equals(list)) {
            return element instanceof Attribute || element instanceof DataType || cls && !isColumnSet(element);
        }
        if (ServerTypes.DIMENSION_URI.equals(list)) {
            return cls;
        }
        return !(element instanceof Feature) && !(element instanceof Namespace);
    }

    private static Rule rule(String list) {
        return ServerTypes.ATTRIBUTE_URI.equals(list) ? T1 : ServerTypes.DIMENSION_URI.equals(list) ? T2 : T3;
    }

    private static String word(String list) {
        return ServerTypes.ATTRIBUTE_URI.equals(list) ? "attribute"
                : ServerTypes.DIMENSION_URI.equals(list) ? "dimension" : "account";
    }

    private static String holder(String list) {
        return ServerTypes.ATTRIBUTE_URI.equals(list) ? "a level" : ServerTypes.DIMENSION_URI.equals(list)
                ? "a dimension" : "an account type";
    }

    private static String fitting(String list) {
        return ServerTypes.ATTRIBUTE_URI.equals(list) ? "attributes, columns, fields, data types or classes that are "
                + "no table or view" : ServerTypes.DIMENSION_URI.equals(list) ? "tables, views or dimensions"
                        : "elements that stand for one account type";
    }

    /** Whether the element is a relational column set (a table, a view, a query), found by the CWM namespace. */
    private static boolean isColumnSet(EObject element) {
        List<EClass> types = new ArrayList<>(List.of(element.eClass()));
        types.addAll(element.eClass().getEAllSuperTypes());
        return types.stream().anyMatch(c -> "ColumnSet".equals(c.getName()) && c.getEPackage() != null
                && RELATIONAL.equals(c.getEPackage().getNsURI()));
    }

    /** The names from the root down to {@code element}, joined by dots. */
    private static String path(EObject element) {
        Deque<String> names = new ArrayDeque<>();
        for (EObject o = element; o != null; o = o.eContainer()) {
            if (o instanceof ModelElement me && me.getName() != null) {
                names.addFirst(me.getName());
            }
        }
        return String.join(".", names);
    }
}
