/*
 * ============LICENSE_START=======================================================
 * Copyright (C) 2024 TechMahindra Ltd.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * ============LICENSE_END=========================================================
 */

package org.onap.cps.events;

import io.cloudevents.CloudEvent;
import io.micrometer.core.annotation.Timed;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.onap.cps.events.model.CpsDataUpdatedEvent;
import org.onap.cps.events.model.Data;
import org.onap.cps.events.model.Data.Operation;
import org.onap.cps.spi.model.Anchor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CpsDataUpdateEventsService {

    private final EventsPublisher<CpsDataUpdatedEvent> eventsPublisher;

    @Value("${app.cps.data-updated.topic:cps-data-updated-events}")
    private String topicName;

    @Value("${notification.enabled:false}")
    private boolean notificationsEnabled;

    /**
     * Publish the cps data update event with header to the public topic.
     *
     * @param anchor Anchor of the updated data
     * @param xpath  xpath of the updated data
     * @param operation operation performed on the data
     * @param observedTimestamp timestamp when data was updated.
     */
    @Timed(value = "cps.dataupdate.events.publish", description = "Time taken to publish Data Update event")
    public void publishCpsDataUpdateEvent(final Anchor anchor, final String xpath,
                                          final Operation operation, final OffsetDateTime observedTimestamp) {
        if (notificationsEnabled) {
            final CpsDataUpdatedEvent cpsDataUpdatedEvent = createCpsDataUpdatedEvent(anchor,
                    observedTimestamp, xpath, operation);
            final String updateEventId = anchor.getDataspaceName() + ":" + anchor.getName();
            final Map<String, String> extensions = createUpdateEventExtensions(updateEventId);
            final CloudEvent cpsDataUpdatedEventAsCloudEvent =
                    CpsEvent.builder().type(CpsDataUpdatedEvent.class.getTypeName()).data(cpsDataUpdatedEvent)
                            .extensions(extensions).build().asCloudEvent();
            eventsPublisher.publishCloudEvent(topicName, updateEventId, cpsDataUpdatedEventAsCloudEvent);
        } else {
            log.debug("Notifications disabled.");
        }
    }

    private CpsDataUpdatedEvent createCpsDataUpdatedEvent(final Anchor anchor, final OffsetDateTime observedTimestamp,
                                                          final String xpath,
                                                          final Operation rootNodeOperation) {
        final CpsDataUpdatedEvent cpsDataUpdatedEvent = new CpsDataUpdatedEvent();
        final Data updateEventData = new Data();
        updateEventData.setObservedTimestamp(observedTimestamp.toString());
        updateEventData.setDataspaceName(anchor.getDataspaceName());
        updateEventData.setAnchorName(anchor.getName());
        updateEventData.setSchemaSetName(anchor.getSchemaSetName());
        updateEventData.setOperation(getRootNodeOperation(xpath, rootNodeOperation));
        updateEventData.setXpath(xpath);
        cpsDataUpdatedEvent.setData(updateEventData);
        return cpsDataUpdatedEvent;
    }

    private Map<String, String> createUpdateEventExtensions(final String eventKey) {
        final Map<String, String> extensions = new HashMap<>();
        extensions.put("correlationid", eventKey);
        return extensions;
    }

    private Operation getRootNodeOperation(final String xpath, final Operation operation) {
        return isRootXpath(xpath) || isRootContainerNodeXpath(xpath) ? operation : Operation.UPDATE;
    }

    private static boolean isRootXpath(final String xpath) {
        return "/".equals(xpath) || "".equals(xpath);
    }

    private static boolean isRootContainerNodeXpath(final String xpath) {
        return 0 == xpath.lastIndexOf('/');
    }
}
