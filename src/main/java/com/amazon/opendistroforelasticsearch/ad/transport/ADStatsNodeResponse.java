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

package com.amazon.opendistroforelasticsearch.ad.transport;

import org.elasticsearch.action.support.nodes.BaseNodeResponse;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ToXContentFragment;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Map;

/**
 * ADStatsNodeResponse
 */
public class ADStatsNodeResponse extends BaseNodeResponse implements ToXContentFragment {

    private Map<String, Object> statsMap;

    /**
     * Constructor
     */
    public ADStatsNodeResponse() {}

    /**
     * Constructor
     *
     * @param node node
     * @param statsToValues Mapping of stat name to value
     */
    public ADStatsNodeResponse(DiscoveryNode node, Map<String, Object> statsToValues) {
        super(node);
        this.statsMap = statsToValues;
    }

    /**
     * Creates a new ADStatsNodeResponse object and reads in the stats from an input stream
     *
     * @param in StreamInput to read from
     * @return ADStatsNodeResponse object corresponding to the input stream
     * @throws IOException throws an IO exception if the StreamInput cannot be read from
     */
    public static ADStatsNodeResponse readStats(StreamInput in) throws IOException {
        ADStatsNodeResponse adStats = new ADStatsNodeResponse();
        adStats.readFrom(in);
        return adStats;
    }

    /**
     * getStatsMap
     *
     * @return map of stats
     */
    public Map<String, Object> getStatsMap() {
        return statsMap;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        this.statsMap = in.readMap(StreamInput::readString, StreamInput::readGenericValue);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeMap(statsMap, StreamOutput::writeString, StreamOutput::writeGenericValue);
    }

    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        for (String stat : statsMap.keySet()) {
            builder.field(stat, statsMap.get(stat));
        }

        return builder;
    }
}