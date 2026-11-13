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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GlobsTest {

    @ParameterizedTest(name = "name {0} ~ {1}: {2}")
    @CsvSource(delimiter = '|', textBlock = """
            tmp_*     | tmp_orders   | true
            tmp_*     | tmp_         | true
            tmp_*     | xtmp_orders  | false
            tmp_*     | Tmp_orders   | false
            *_log     | order_log    | true
            *_log     | order_logs   | false
            t?p       | top          | true
            t?p       | toop         | false
            a.b       | a.b          | true
            a.b       | axb          | false
            a+b(1)    | a+b(1)       | true
            [ab]      | a            | false
            *         | a.b.c        | true
            """)
    void name(String glob, String name, boolean matches) {
        assertThat(Globs.name(glob).matcher(name).matches()).isEqualTo(matches);
    }

    @ParameterizedTest(name = "path {0} ~ {1}: {2}")
    @CsvSource(delimiter = '|', textBlock = """
            shop.sales.orders     | shop.sales.orders         | true
            shop.sales.orders     | shop.sales.orders.id      | false
            shop.*.orders         | shop.sales.orders         | true
            shop.*.orders         | shop.a.b.orders           | false
            shop.*                | shop.sales                | true
            shop.*                | shop.sales.orders         | false
            shop.*                | shop                      | false
            shop.sa*              | shop.sales                | true
            shop.s?les            | shop.sales                | true
            shop.s?les            | shop.s.les                | false
            shop.**               | shop                      | true
            shop.**               | shop.sales.orders.id      | true
            shop.**               | shopping.sales            | false
            **.id                 | shop.sales.orders.id      | true
            **.id                 | id                        | true
            **.id                 | shop.sales.orders.uid     | false
            shop.**.id            | shop.id                   | true
            shop.**.id            | shop.sales.orders.id      | true
            shop.**.id            | shop.sales.orders.idx     | false
            shop.**.**.id         | shop.sales.id             | true
            **                    | shop.sales.orders         | true
            **.sales.**           | shop.sales.orders         | true
            **.sales.**           | shop.presales.orders      | false
            shop.**.*_log         | shop.sales.order_log      | true
            shop.a**b             | shop.axxb                 | true
            shop.a**b             | shop.ax.xb                | false
            Shop.**               | shop.sales                | false
            """)
    void path(String glob, String path, boolean matches) {
        assertThat(Globs.path(glob).matcher(path).matches()).isEqualTo(matches);
    }
}
