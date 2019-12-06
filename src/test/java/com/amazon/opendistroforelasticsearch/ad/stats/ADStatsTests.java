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

package com.amazon.opendistroforelasticsearch.ad.stats;

import org.elasticsearch.test.ESTestCase;

import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class ADStatsTests extends ESTestCase {
    private ADStats adStats;

    @Before
    public void setup() {
        adStats = ADStats.getInstance();
    }

    @Test
    public void testStatNamesGetNames() {
        assertEquals("getNames of StatNames returns the incorrect number of stats",
                ADStats.StatNames.getNames().size(), ADStats.StatNames.values().length);
    }

    @Test
    public void testGetStats() {
        Map<String, ADStat<?>> stats = adStats.getStats();

        assertEquals("getStats returns the incorrect number of stats",
                stats.size(), ADStats.StatNames.values().length);

        for (Map.Entry<String, ADStat<?>> stat : stats.entrySet()) {
            assertTrue("getStats returns incorrect stats",
                    adStats.getStats().containsKey(stat.getKey()) &&
                            adStats.getStats().get(stat.getKey()) == stat.getValue());
        }
    }

    @Test
    public void testGetStat() {
        ADStat<?> stat = adStats.getStat(ADStats.StatNames.AD_EXECUTE_REQUEST_COUNT.getName());

        assertTrue("getStat returns incorrect stat",
                adStats.getStats().containsKey(stat.getName()) &&
                        adStats.getStats().get(stat.getName()) == stat);
    }

    @Test
    public void testGetNodeStats() {
        Map<String, ADStat<?>> stats = adStats.getStats();
        Set<ADStat<?>> nodeStats = new HashSet<>(adStats.getNodeStats().values());

        for (ADStat<?> stat : stats.values()) {
            assertTrue("getNodeStats returns incorrect stats", stat.isClusterLevel() || nodeStats.contains(stat));
        }
    }
}