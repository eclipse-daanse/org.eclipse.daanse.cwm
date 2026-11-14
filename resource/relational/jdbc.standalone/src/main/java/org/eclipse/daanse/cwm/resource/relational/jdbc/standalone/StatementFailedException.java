/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.jdbc.standalone;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.QualifiedNames;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlStatement;
import org.eclipse.daanse.cwm.resource.relational.ddl.render.CwmDdlScripts.Kind;

/**
 * A statement of an export the database rejected: which one, where it stands in its script, the
 * element it is written for, and what the export leaves behind. The {@link SQLException} of the
 * database is the cause; its SQL state and vendor code are this exception's too.
 */
public final class StatementFailedException extends SQLException {

    private static final long serialVersionUID = 1L;

    private final transient DdlStatement statement;
    private final Kind script;
    private final int position;
    private final String element;
    private final boolean rolledBack;
    private final transient List<DdlStatement> executed;

    /**
     * @param script     the script the statement belongs to
     * @param position   its position in that script, from 1, counting every statement of the
     *                   script, also those an export with {@code dropFirst} skipped
     * @param statement  the statement and the element it is written for
     * @param rolledBack whether everything the export ran was rolled back
     * @param executed   the statements that ran before, in their order
     * @param cause      what the database reported
     */
    StatementFailedException(Kind script, int position, DdlStatement statement, boolean rolledBack,
            List<DdlStatement> executed, SQLException cause) {
        super(message(script, position, statement, rolledBack, executed, cause), cause.getSQLState(),
                cause.getErrorCode(), cause);
        this.statement = Objects.requireNonNull(statement, "statement");
        this.script = script;
        this.position = position;
        this.element = describe(statement);
        this.rolledBack = rolledBack;
        this.executed = List.copyOf(executed);
    }

    /** The statement the database rejected, with the element it is written for. */
    public DdlStatement statement() {
        return statement;
    }

    /** The script it belongs to: the {@code DROP} script that runs first, or the {@code CREATE} script. */
    public Kind script() {
        return script;
    }

    /**
     * Its position in {@link #script()}, from 1, as the script file of the same model and settings
     * numbers it: every statement of the script counts, also one an export skipped.
     */
    public int position() {
        return position;
    }

    /**
     * The element the statement is written for, by its kind and qualified name:
     * {@code ForeignKey shop.sales.orders.fk_orders_customer}.
     */
    public String element() {
        return element;
    }

    /**
     * Whether the database is as before the export: the export ran in one transaction, which is
     * rolled back. Otherwise {@link #executed()} remains.
     */
    public boolean rolledBack() {
        return rolledBack;
    }

    /**
     * The statements that ran before the failing one, in their order; where the export was not
     * {@link #rolledBack() rolled back}, what they did remains in the database.
     */
    public List<DdlStatement> executed() {
        return executed;
    }

    /** The kind and qualified name of the element {@code statement} is written for. */
    static String describe(DdlStatement statement) {
        String name = QualifiedNames.of(statement.element());
        String kind = statement.element().eClass().getName();
        return name.isEmpty() ? kind : kind + " " + name;
    }

    private static String message(Kind script, int position, DdlStatement statement, boolean rolledBack,
            List<DdlStatement> executed, SQLException cause) {
        String remains = rolledBack ? "rolled back, the database is as before"
                : executed.isEmpty() ? "nothing ran before it"
                        : "not rolled back, the " + executed.size() + " statements before it remain";
        return "statement " + position + " of the " + script + " script, for " + describe(statement)
                + ", failed: " + statement.sql() + " — " + cause.getMessage() + " (" + remains + ")";
    }
}
