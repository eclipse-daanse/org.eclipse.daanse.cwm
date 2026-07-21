/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.load.jdbc.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Focused tests for the CHECK-body unwrapping helpers of
 * {@link CwmLoaderImpl}.
 */
class CwmLoaderImplExpressionTest {

    @Test
    void unwrap_plainCheckWrapper() {
        assertThat(CwmLoaderImpl.unwrapCheckBody("CHECK (LENGTH(\"EMAIL\") > 3)")).isEqualTo("LENGTH(\"EMAIL\") > 3");
    }

    @Test
    void unwrap_caseInsensitiveAndWhitespace() {
        assertThat(CwmLoaderImpl.unwrapCheckBody("  check   ( \"ID\" > 0 ) ")).isEqualTo("\"ID\" > 0");
    }

    @Test
    void unwrap_noWrapper_passesThrough() {
        assertThat(CwmLoaderImpl.unwrapCheckBody("\"ID\" > 0")).isEqualTo("\"ID\" > 0");
    }

    @Test
    void unwrap_nullAndBlank() {
        assertThat(CwmLoaderImpl.unwrapCheckBody(null)).isEmpty();
        assertThat(CwmLoaderImpl.unwrapCheckBody("   ")).isEmpty();
    }

    @Test
    void unwrap_unbalancedInner_keepsWrapper() {
        // Stripping the outer parens would break the expression — two separate
        // groups: CHECK (a > 0) AND (b > 0) must not become "a > 0) AND (b > 0".
        String twoGroups = "CHECK (\"A\" > 0) OR (\"B\" > 0)";
        assertThat(CwmLoaderImpl.unwrapCheckBody(twoGroups)).isEqualTo("(\"A\" > 0) OR (\"B\" > 0)");
    }

    @Test
    void unwrap_parensInsideStringLiteral() {
        assertThat(CwmLoaderImpl.unwrapCheckBody("CHECK (\"NAME\" <> '(x)')")).isEqualTo("\"NAME\" <> '(x)'");
    }

    @Test
    void isBalanced_basics() {
        assertThat(CwmLoaderImpl.isBalanced("a > 0")).isTrue();
        assertThat(CwmLoaderImpl.isBalanced("(a > 0)")).isTrue();
        assertThat(CwmLoaderImpl.isBalanced("(a > 0")).isFalse();
        assertThat(CwmLoaderImpl.isBalanced("a > 0)")).isFalse();
        assertThat(CwmLoaderImpl.isBalanced(")(")).isFalse();
    }

    @Test
    void isBalanced_ignoresParensInStrings() {
        assertThat(CwmLoaderImpl.isBalanced("name <> '((('")).isTrue();
        assertThat(CwmLoaderImpl.isBalanced("name <> ')))'")).isTrue();
    }

    @Test
    void isBalanced_sqlEscapedQuotes() {
        // '' inside a literal is an escaped quote, not a string boundary.
        assertThat(CwmLoaderImpl.isBalanced("name <> 'it''s (fine)'")).isTrue();
        assertThat(CwmLoaderImpl.isBalanced("name <> 'it''s' AND (a > 0)")).isTrue();
        // An escaped quote directly before the closing quote.
        assertThat(CwmLoaderImpl.isBalanced("name <> '('' )'")).isTrue();
    }
}
