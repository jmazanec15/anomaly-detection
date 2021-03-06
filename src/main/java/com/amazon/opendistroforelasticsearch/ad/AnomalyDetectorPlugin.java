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

package com.amazon.opendistroforelasticsearch.ad;

import com.amazon.opendistroforelasticsearch.ad.cluster.ADClusterEventListener;
import com.amazon.opendistroforelasticsearch.ad.cluster.ADMetaData;
import com.amazon.opendistroforelasticsearch.ad.cluster.ADMetaData.ADMetaDataDiff;
import com.amazon.opendistroforelasticsearch.ad.cluster.DailyCron;
import com.amazon.opendistroforelasticsearch.ad.cluster.DeleteDetector;
import com.amazon.opendistroforelasticsearch.ad.cluster.HashRing;
import com.amazon.opendistroforelasticsearch.ad.cluster.HourlyCron;
import com.amazon.opendistroforelasticsearch.ad.cluster.MasterEventListener;
import com.amazon.opendistroforelasticsearch.ad.constant.CommonName;
import com.amazon.opendistroforelasticsearch.ad.dataprocessor.Interpolator;
import com.amazon.opendistroforelasticsearch.ad.feature.FeatureManager;
import com.amazon.opendistroforelasticsearch.ad.feature.SearchFeatureDao;
import com.amazon.opendistroforelasticsearch.ad.indices.AnomalyDetectionIndices;
import com.amazon.opendistroforelasticsearch.ad.ml.CheckpointDao;
import com.amazon.opendistroforelasticsearch.ad.ml.HybridThresholdingModel;
import com.amazon.opendistroforelasticsearch.ad.ml.ModelManager;
import com.amazon.opendistroforelasticsearch.ad.model.AnomalyDetector;
import com.amazon.opendistroforelasticsearch.ad.rest.RestGetAnomalyDetectorAction;
import com.amazon.opendistroforelasticsearch.ad.rest.RestIndexAnomalyDetectorAction;
import com.amazon.opendistroforelasticsearch.ad.rest.RestSearchAnomalyDetectorAction;
import com.amazon.opendistroforelasticsearch.ad.rest.RestDeleteAnomalyDetectorAction;
import com.amazon.opendistroforelasticsearch.ad.rest.RestExecuteAnomalyDetectorAction;

import com.amazon.opendistroforelasticsearch.ad.rest.RestSearchAnomalyResultAction;
import com.amazon.opendistroforelasticsearch.ad.settings.AnomalyDetectorSettings;


import com.amazon.opendistroforelasticsearch.ad.transport.ADStateManager;
import com.amazon.opendistroforelasticsearch.ad.transport.DeleteModelAction;
import com.amazon.opendistroforelasticsearch.ad.transport.DeleteDetectorAction;
import com.amazon.opendistroforelasticsearch.ad.transport.RCFResultAction;
import com.amazon.opendistroforelasticsearch.ad.transport.StopDetectorAction;
import com.amazon.opendistroforelasticsearch.ad.transport.StopDetectorTransportAction;
import com.amazon.opendistroforelasticsearch.ad.transport.ThresholdResultAction;
import com.amazon.opendistroforelasticsearch.ad.transport.AnomalyResultAction;
import com.amazon.opendistroforelasticsearch.ad.transport.CronAction;

import com.amazon.opendistroforelasticsearch.ad.transport.DeleteModelTransportAction;
import com.amazon.opendistroforelasticsearch.ad.transport.DeleteDetectorTransportAction;
import com.amazon.opendistroforelasticsearch.ad.transport.RCFResultTransportAction;
import com.amazon.opendistroforelasticsearch.ad.transport.ThresholdResultTransportAction;
import com.amazon.opendistroforelasticsearch.ad.transport.AnomalyResultTransportAction;
import com.amazon.opendistroforelasticsearch.ad.transport.CronTransportAction;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.Clock;

import org.elasticsearch.SpecialPermission;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.NamedDiff;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.MetaData.Custom;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.IndexScopedSettings;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.NodeEnvironment;
import org.elasticsearch.monitor.jvm.JvmService;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.watcher.ResourceWatcherService;

import com.amazon.opendistroforelasticsearch.ad.util.ColdStartRunner;
import com.amazon.opendistroforelasticsearch.ad.util.ClientUtil;
import com.amazon.opendistroforelasticsearch.ad.dataprocessor.IntegerSensitiveSingleFeatureLinearUniformInterpolator;
import com.amazon.opendistroforelasticsearch.ad.dataprocessor.LinearUniformInterpolator;
import com.amazon.opendistroforelasticsearch.ad.dataprocessor.SingleFeatureLinearUniformInterpolator;
import com.amazon.randomcutforest.serialize.RandomCutForestSerDe;

/**
 * Entry point of AD plugin.
 */
public class AnomalyDetectorPlugin extends Plugin implements ActionPlugin, ScriptPlugin {

    public static final String AD_BASE_URI = "/_opendistro/_anomaly_detection";
    public static final String AD_BASE_DETECTORS_URI = AD_BASE_URI + "/detectors";
    private static Gson gson;
    private AnomalyDetectionIndices anomalyDetectionIndices;
    private AnomalyDetectorRunner anomalyDetectorRunner;
    private ClusterService clusterService;

    static {
        SpecialPermission.check();
        // gson intialization requires "java.lang.RuntimePermission" "accessDeclaredMembers" to
        // initialize ConstructorConstructor
        AccessController.doPrivileged((PrivilegedAction<Void>) AnomalyDetectorPlugin::initGson);
    }

    public AnomalyDetectorPlugin() {
    }

    @Override
    public List<RestHandler> getRestHandlers(Settings settings,
                                             RestController restController,
                                             ClusterSettings clusterSettings,
                                             IndexScopedSettings indexScopedSettings,
                                             SettingsFilter settingsFilter,
                                             IndexNameExpressionResolver indexNameExpressionResolver,
                                             Supplier<DiscoveryNodes> nodesInCluster) {
        RestGetAnomalyDetectorAction restGetAnomalyDetectorAction = new RestGetAnomalyDetectorAction(settings,
                restController);
        RestIndexAnomalyDetectorAction restIndexAnomalyDetectorAction = new RestIndexAnomalyDetectorAction(settings,
                restController, clusterService, anomalyDetectionIndices);
        RestSearchAnomalyDetectorAction searchAnomalyDetectorAction = new RestSearchAnomalyDetectorAction(settings,
                restController);
        RestSearchAnomalyResultAction searchAnomalyResultAction = new RestSearchAnomalyResultAction(settings,
                restController);
        RestDeleteAnomalyDetectorAction deleteAnomalyDetectorAction = new RestDeleteAnomalyDetectorAction(settings,
                restController, clusterService);
        RestExecuteAnomalyDetectorAction executeAnomalyDetectorAction = new RestExecuteAnomalyDetectorAction(settings,
            restController, clusterService, anomalyDetectorRunner);

        return ImmutableList.of(restGetAnomalyDetectorAction,
                restIndexAnomalyDetectorAction,
                searchAnomalyDetectorAction,
                searchAnomalyResultAction,
                deleteAnomalyDetectorAction,
                executeAnomalyDetectorAction);
    }

    private static Void initGson() {
        gson = new Gson();
        return null;
    }

    @Override
    public Collection<Object> createComponents(Client client, ClusterService clusterService, ThreadPool threadPool,
                                               ResourceWatcherService resourceWatcherService, ScriptService scriptService,
                                               NamedXContentRegistry xContentRegistry, Environment environment,
                                               NodeEnvironment nodeEnvironment,
                                               NamedWriteableRegistry namedWriteableRegistry) {
        Settings settings = environment.settings();
        ClientUtil clientUtil = new ClientUtil(settings);
        anomalyDetectionIndices = new AnomalyDetectionIndices(client, clusterService, threadPool, settings,
                clientUtil);
        this.clusterService = clusterService;

        SingleFeatureLinearUniformInterpolator singleFeatureLinearUniformInterpolator =
                new IntegerSensitiveSingleFeatureLinearUniformInterpolator();
        Interpolator interpolator = new LinearUniformInterpolator(singleFeatureLinearUniformInterpolator);
        SearchFeatureDao searchFeatureDao = new SearchFeatureDao(client, scriptService, xContentRegistry, interpolator,
                clientUtil);

        JvmService jvmService = new JvmService(environment.settings());
        RandomCutForestSerDe rcfSerde = new RandomCutForestSerDe();
        CheckpointDao checkpoint = new CheckpointDao(client, clientUtil, CommonName.CHECKPOINT_INDEX_NAME);
        Clock clock = Clock.systemUTC();

        ModelManager modelManager = new ModelManager(clusterService, jvmService, rcfSerde, checkpoint, gson, clock,
                AnomalyDetectorSettings.DESIRED_MODEL_SIZE_PERCENTAGE,
                AnomalyDetectorSettings.MODEL_MAX_SIZE_PERCENTAGE, AnomalyDetectorSettings.NUM_TREES,
                AnomalyDetectorSettings.NUM_SAMPLES_PER_TREE, AnomalyDetectorSettings.TIME_DECAY,
                AnomalyDetectorSettings.THRESHOLD_MIN_PVALUE, AnomalyDetectorSettings.THRESHOLD_MAX_RANK_ERROR,
                AnomalyDetectorSettings.THRESHOLD_MAX_SCORE, AnomalyDetectorSettings.THRESHOLD_NUM_LOGNORMAL_QUANTILES,
                AnomalyDetectorSettings.THRESHOLD_DOWNSAMPLES, AnomalyDetectorSettings.THRESHOLD_MAX_SAMPLES,
                HybridThresholdingModel.class, AnomalyDetectorSettings.MIN_PREVIEW_SIZE,
                AnomalyDetectorSettings.HOURLY_MAINTENANCE, AnomalyDetectorSettings.HOURLY_MAINTENANCE);

        HashRing hashRing = new HashRing(clusterService, clock, settings);
        ADStateManager stateManager = new ADStateManager(client, xContentRegistry, modelManager, settings, clientUtil,
                clock, AnomalyDetectorSettings.HOURLY_MAINTENANCE);
        ColdStartRunner runner = new ColdStartRunner();
        FeatureManager featureManager = new FeatureManager(searchFeatureDao, interpolator, clock,
                AnomalyDetectorSettings.MAX_TRAIN_SAMPLE, AnomalyDetectorSettings.MAX_SAMPLE_STRIDE, AnomalyDetectorSettings.SHINGLE_SIZE,
                AnomalyDetectorSettings.MAX_MISSING_POINTS, AnomalyDetectorSettings.MAX_NEIGHBOR_DISTANCE,
                AnomalyDetectorSettings.MAX_PREVIEW_SAMPLES, AnomalyDetectorSettings.HOURLY_MAINTENANCE);
        anomalyDetectorRunner = new AnomalyDetectorRunner(modelManager, featureManager);

        DeleteDetector deleteUtil = new DeleteDetector(clusterService, clock);
        DailyCron dailyCron = new DailyCron(deleteUtil, clock, client,
                AnomalyDetectorSettings.CHECKPOINT_TTL);
        HourlyCron hourlyCron = new HourlyCron(clusterService, client);

        return ImmutableList.of(anomalyDetectionIndices, anomalyDetectorRunner, searchFeatureDao,
                singleFeatureLinearUniformInterpolator, interpolator, gson, jvmService, hashRing, featureManager,
                modelManager, clock, stateManager, runner,
                new ADClusterEventListener(clusterService, hashRing, modelManager),
                deleteUtil, dailyCron, hourlyCron,
                new MasterEventListener(clusterService, threadPool, deleteUtil, client, clock)
                );
    }

    @Override
    public List<Setting<?>> getSettings() {
        return ImmutableList.of(
                AnomalyDetectorSettings.MAX_ANOMALY_DETECTORS,
                AnomalyDetectorSettings.MAX_ANOMALY_FEATURES,
                AnomalyDetectorSettings.REQUEST_TIMEOUT,
                AnomalyDetectorSettings.DETECTION_INTERVAL,
                AnomalyDetectorSettings.DETECTION_WINDOW_DELAY,
                AnomalyDetectorSettings.AD_RESULT_HISTORY_INDEX_MAX_AGE,
                AnomalyDetectorSettings.AD_RESULT_HISTORY_ROLLOVER_PERIOD,
                AnomalyDetectorSettings.AD_RESULT_HISTORY_MAX_DOCS,
                AnomalyDetectorSettings.AD_RESULT_ROLLOVER_PERIOD,
                AnomalyDetectorSettings.MAX_RETRY_FOR_UNRESPONSIVE_NODE,
                AnomalyDetectorSettings.COOLDOWN_MINUTES,
                AnomalyDetectorSettings.BACKOFF_MINUTES,
                AnomalyDetectorSettings.BACKOFF_INITIAL_DELAY,
                AnomalyDetectorSettings.MAX_RETRY_FOR_BACKOFF
        );
    }

    @Override
    public List<NamedXContentRegistry.Entry> getNamedXContent() {
        return ImmutableList.of(AnomalyDetector.XCONTENT_REGISTRY,
                ADMetaData.XCONTENT_REGISTRY);
    }

    @Override
    public List<NamedWriteableRegistry.Entry> getNamedWriteables() {
        return Arrays.asList(
                new NamedWriteableRegistry.Entry(Custom.class, ADMetaData.TYPE, ADMetaData::new),
                new NamedWriteableRegistry.Entry(NamedDiff.class, ADMetaData.TYPE , ADMetaDataDiff::new)
                );
    }

    /*
     * Register action and handler so that transportClient can find proxy for action
     */
    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        return Arrays.asList(
                new ActionHandler<>(DeleteModelAction.INSTANCE, DeleteModelTransportAction.class),
                new ActionHandler<>(DeleteDetectorAction.INSTANCE, DeleteDetectorTransportAction.class),
                new ActionHandler<>(StopDetectorAction.INSTANCE, StopDetectorTransportAction.class),
                new ActionHandler<>(RCFResultAction.INSTANCE, RCFResultTransportAction.class),
                new ActionHandler<>(ThresholdResultAction.INSTANCE, ThresholdResultTransportAction.class),
                new ActionHandler<>(AnomalyResultAction.INSTANCE, AnomalyResultTransportAction.class),
                new ActionHandler<>(CronAction.INSTANCE, CronTransportAction.class)
        );
    }
}
