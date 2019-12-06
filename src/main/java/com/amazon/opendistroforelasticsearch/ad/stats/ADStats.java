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

import com.amazon.opendistroforelasticsearch.ad.stats.counters.BasicCounter;
import com.amazon.opendistroforelasticsearch.ad.stats.suppliers.CounterSupplier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stats
 *
 * This class is the main entrypoint for access to the stats that
 * the AD plugin keeps track of.
 */
public class ADStats {

    private static ADStats adStats = null;

    public static ADStats getInstance() {

        if (adStats == null) {
            adStats = new ADStats();
        }

        return adStats;
    }

    private Map<String, ADStat<?>> stats;

    /**
     * Enum containing names of all stats
     */
    public enum StatNames {
        AD_EXECUTE_REQUEST_COUNT("ad_execute_request_count"),
        AD_EXECUTE_FAIL_COUNT("ad_execute_failure_count");

        private String name;

        StatNames(String name) { this.name = name; }
        public String getName() { return name; }

        public static List<String> getNames() {
            ArrayList<String> names = new ArrayList<>();

            for (StatNames statName : StatNames.values()) {
                names.add(statName.getName());
            }
            return names;
        }
    }

    /**
     * ADStats constructor
     */
    private ADStats() {
        initStats();
    }

    /**
     * Initialize the map that keeps track of all of the stats
     */
    private void initStats() {
        stats = new HashMap<String, ADStat<?>>() {
            {
                // Stateful Node stats
                put(StatNames.AD_EXECUTE_REQUEST_COUNT.getName(), new ADStat<>(StatNames.AD_EXECUTE_REQUEST_COUNT.getName(),
                        false, new CounterSupplier(new BasicCounter())));
                put(StatNames.AD_EXECUTE_FAIL_COUNT.getName(), new ADStat<>(StatNames.AD_EXECUTE_FAIL_COUNT.getName(),
                        false, new CounterSupplier(new BasicCounter())));
            }
        };
    }

    /**
     * Get the stats
     * @return all of the stats
     */
    public Map<String, ADStat<?>> getStats() {
        return stats;
    }

    /**
     * Get individual statName
     * @param key Stat name
     * @return ADStat
     * @throws IllegalArgumentException thrown on illegal statName
     */
    public ADStat<?> getStat(String key) throws IllegalArgumentException {
        if (!stats.keySet().contains(key)) {
            throw new IllegalArgumentException("Stat=\"" + key + "\" does not exist");
        }
        return stats.get(key);
    }

    /**
     * Get a map of the stats that are kept at the node level
     * @return HashMap of stats kept at the node level
     */
    public Map<String, ADStat<?>> getNodeStats() {
        Map<String, ADStat<?>> nodeStats = new HashMap<>();

        for (Map.Entry<String, ADStat<?>> entry : stats.entrySet()) {
            if (!entry.getValue().isClusterLevel()) {
                nodeStats.put(entry.getKey(), entry.getValue());
            }
        }
        return nodeStats;
    }
}