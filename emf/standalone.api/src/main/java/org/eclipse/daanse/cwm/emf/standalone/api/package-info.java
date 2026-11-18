/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
/**
 * The interface by which a model module announces its generated package to a plain Java program:
 * {@link org.eclipse.daanse.cwm.emf.standalone.api.EPackageProvider}, found by {@link java.util.ServiceLoader}.
 */
@org.osgi.annotation.bundle.Export
@org.osgi.annotation.versioning.Version("1.0")
package org.eclipse.daanse.cwm.emf.standalone.api;
