/*
 * ============LICENSE_START=======================================================
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

package org.onap.cps.ncmp.api.impl.events.avcsubscription;

import com.hazelcast.map.IMap;
import io.cloudevents.CloudEvent;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.onap.cps.ncmp.api.impl.config.embeddedcache.ForwardedSubscriptionEventCacheConfig;
import org.onap.cps.ncmp.api.impl.subscriptions.SubscriptionPersistence;
import org.onap.cps.ncmp.api.impl.subscriptions.SubscriptionStatus;
import org.onap.cps.ncmp.api.impl.utils.DataNodeHelper;
import org.onap.cps.ncmp.api.impl.utils.SubscriptionEventResponseCloudMapper;
import org.onap.cps.ncmp.api.impl.yangmodels.YangModelSubscriptionEvent;
import org.onap.cps.ncmp.events.avcsubscription1_0_0.dmi_to_ncmp.SubscriptionEventResponse;
import org.onap.cps.spi.model.DataNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SubscriptionEventResponseConsumer {

    private final IMap<String, Set<String>> forwardedSubscriptionEventCache;
    private final SubscriptionPersistence subscriptionPersistence;
    private final SubscriptionEventResponseMapper subscriptionEventResponseMapper;
    private final SubscriptionEventResponseOutcome subscriptionEventResponseOutcome;
    private final SubscriptionEventResponseCloudMapper subscriptionEventResponseCloudMapper;

    @Value("${notification.enabled:true}")
    private boolean notificationFeatureEnabled;

    @Value("${ncmp.model-loader.subscription:false}")
    private boolean subscriptionModelLoaderEnabled;

    /**
     * Consume subscription response event.
     *
     * @param subscriptionEventResponseConsumerRecord the event to be consumed
     */
    @KafkaListener(topics = "${app.ncmp.avc.subscription-response-topic}",
            containerFactory = "cloudEventConcurrentKafkaListenerContainerFactory")
    public void consumeSubscriptionEventResponse(
            final ConsumerRecord<String, CloudEvent> subscriptionEventResponseConsumerRecord) {
        final CloudEvent cloudEvent = subscriptionEventResponseConsumerRecord.value();
        final String eventType = subscriptionEventResponseConsumerRecord.value().getType();
        final SubscriptionEventResponse subscriptionEventResponse =
                subscriptionEventResponseCloudMapper.toSubscriptionEventResponse(cloudEvent);
        final String clientId = subscriptionEventResponse.getData().getClientId();
        log.info("subscription event response of clientId: {} is received.", clientId);
        final String subscriptionName = subscriptionEventResponse.getData().getSubscriptionName();
        final String subscriptionEventId = clientId + subscriptionName;
        boolean createOutcomeResponse = false;
        if (forwardedSubscriptionEventCache.containsKey(subscriptionEventId)) {
            final Set<String> dmiNames = forwardedSubscriptionEventCache.get(subscriptionEventId);
            dmiNames.remove(subscriptionEventResponse.getData().getDmiName());
            forwardedSubscriptionEventCache.put(subscriptionEventId, dmiNames,
                    ForwardedSubscriptionEventCacheConfig.SUBSCRIPTION_FORWARD_STARTED_TTL_SECS, TimeUnit.SECONDS);
            createOutcomeResponse = forwardedSubscriptionEventCache.get(subscriptionEventId).isEmpty();
        }
        if (subscriptionModelLoaderEnabled) {
            updateSubscriptionEvent(subscriptionEventResponse);
        }
        if (createOutcomeResponse
                && notificationFeatureEnabled
                && hasNoPendingCmHandles(clientId, subscriptionName)) {
            subscriptionEventResponseOutcome.sendResponse(subscriptionEventResponse, eventType);
            forwardedSubscriptionEventCache.remove(subscriptionEventId);
        }
    }

    private boolean hasNoPendingCmHandles(final String clientId, final String subscriptionName) {
        final Collection<DataNode> dataNodeSubscription = subscriptionPersistence.getCmHandlesForSubscriptionEvent(
                clientId, subscriptionName);
        final Map<String, Map<String, String>> cmHandleIdToStatusAndDetailsAsMapOriginal =
                DataNodeHelper.cmHandleIdToStatusAndDetailsAsMapFromDataNode(dataNodeSubscription);
        for (final Map<String, String> statusAndDetailsMap : cmHandleIdToStatusAndDetailsAsMapOriginal.values()) {
            final String status = statusAndDetailsMap.get("status");
            if (SubscriptionStatus.PENDING.toString().equals(status)) {
                return false;
            }
        }
        return true;
    }

    private void updateSubscriptionEvent(final SubscriptionEventResponse subscriptionEventResponse) {
        final YangModelSubscriptionEvent yangModelSubscriptionEvent =
                subscriptionEventResponseMapper
                        .toYangModelSubscriptionEvent(subscriptionEventResponse);
        subscriptionPersistence.saveSubscriptionEvent(yangModelSubscriptionEvent);
    }
}