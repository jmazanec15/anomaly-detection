/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.opendistroforelasticsearch.ad.stats.suppliers;

import com.amazon.opendistroforelasticsearch.ad.stats.counters.Counter;
import java.util.function.Supplier;

/**
 * CounterSupplier provides a stateful count as the value
 */
public class CounterSupplier implements Supplier<Long> {
    private Counter counter;

    public CounterSupplier(Counter counter) {
        this.counter = counter;
    }

    @Override
    public Long get() {
        return counter.getValue();
    }

    public void increment() {
        counter.increment();
    }
}