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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.ServiceLoader;

/**
 * Opens a JDBC connection by URL, for {@link CwmJdbcImport}, {@link CwmJdbcExport} and every program
 * that starts from a URL rather than a {@code DataSource}.
 */
public final class Connections {

    private Connections() {
    }

    /**
     * Opens a connection to {@code url} with the drivers this class sees, as
     * {@link #connect(String, String, String, String, ClassLoader)} does without a driver class.
     */
    public static Connection connect(String url, String user, String password) throws SQLException {
        return connect(url, user, password, null, null);
    }

    /**
     * Opens a connection to {@code url}. The driver is taken from {@code loader}: the class
     * {@code driverClass} when given, otherwise the first {@link Driver} service there that accepts
     * the URL, finally {@link DriverManager}. Looking in {@code loader} first matters where the
     * driver is not on the class path of {@link DriverManager}, as for a driver that comes as a
     * dependency of a Maven plugin.
     *
     * @param user        {@code null} for a URL that carries the credentials, or none needed
     * @param password    {@code null} when there is none
     * @param driverClass {@code null} or blank: the driver that accepts the URL
     * @param loader      {@code null}: the class loader of this class
     * @throws SQLException when the driver class cannot be loaded, naming it; when no driver accepts
     *                      the URL ({@code No suitable driver found for …}); as the driver fails to
     *                      connect
     */
    public static Connection connect(String url, String user, String password, String driverClass,
            ClassLoader loader) throws SQLException {
        if (url == null || url.isBlank()) {
            throw new SQLException("no JDBC URL given");
        }
        Properties props = new Properties();
        if (user != null) {
            props.setProperty("user", user);
        }
        if (password != null) {
            props.setProperty("password", password);
        }
        Driver driver = driver(url, driverClass, loader == null ? Connections.class.getClassLoader() : loader);
        if (driver != null) {
            Connection c = driver.connect(url, props);
            if (c != null) {
                return c;
            }
        }
        return DriverManager.getConnection(url, props);
    }

    private static Driver driver(String url, String driverClass, ClassLoader loader) throws SQLException {
        if (driverClass != null && !driverClass.isBlank()) {
            try {
                return (Driver) Class.forName(driverClass.trim(), true, loader).getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
                throw new SQLException("JDBC driver " + driverClass + " is not available: " + e, e);
            }
        }
        for (Driver d : ServiceLoader.load(Driver.class, loader)) {
            if (d.acceptsURL(url)) {
                return d;
            }
        }
        return null;
    }
}
