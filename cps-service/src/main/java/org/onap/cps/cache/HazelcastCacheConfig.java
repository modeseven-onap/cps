/*
 * ============LICENSE_START========================================================
 *  Copyright (C) 2023 Nordix Foundation
 *  ================================================================================
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
 *  ============LICENSE_END=========================================================
 */

package org.onap.cps.cache;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.NamedConfig;
import com.hazelcast.config.QueueConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

/**
 * Core infrastructure of the hazelcast distributed cache.
 */
@Slf4j
public class HazelcastCacheConfig {

    @Value("${hazelcast.cluster-name}")
    protected String clusterName;

    @Value("${hazelcast.mode.kubernetes.enabled}")
    protected boolean cacheKubernetesEnabled;

    @Value("${hazelcast.mode.kubernetes.service-name}")
    protected String cacheKubernetesServiceName;

    protected HazelcastInstance createHazelcastInstance(final String hazelcastInstanceName,
                                                        final NamedConfig namedConfig) {
        return Hazelcast.newHazelcastInstance(initializeConfig(hazelcastInstanceName, namedConfig));
    }

    private Config initializeConfig(final String instanceName, final NamedConfig namedConfig) {
        final Config config = new Config(instanceName);
        if (namedConfig instanceof MapConfig) {
            config.addMapConfig((MapConfig) namedConfig);
        }
        if (namedConfig instanceof QueueConfig) {
            config.addQueueConfig((QueueConfig) namedConfig);
        }
        config.setClusterName(clusterName);
        updateDiscoveryMode(config);
        return config;
    }

    protected static MapConfig createMapConfig(final String configName) {
        final MapConfig mapConfig = new MapConfig(configName);
        mapConfig.setBackupCount(3);
        mapConfig.setAsyncBackupCount(3);
        return mapConfig;
    }

    protected static QueueConfig createQueueConfig(final String configName) {
        final QueueConfig commonQueueConfig = new QueueConfig(configName);
        commonQueueConfig.setBackupCount(3);
        commonQueueConfig.setAsyncBackupCount(3);
        return commonQueueConfig;
    }

    protected void updateDiscoveryMode(final Config config) {
        if (cacheKubernetesEnabled) {
            log.info("Enabling kubernetes mode with service-name : {}", cacheKubernetesServiceName);
            config.getNetworkConfig().getJoin().getKubernetesConfig().setEnabled(true)
                .setProperty("service-name", cacheKubernetesServiceName);
        }
    }

}
