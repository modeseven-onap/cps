/*
 * ============LICENSE_START=======================================================
 * Copyright (C) 2022 Nordix Foundation
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

package org.onap.cps.ncmp.api.impl.event

import org.onap.cps.ncmp.api.impl.utils.YangDataConverter
import org.onap.cps.ncmp.api.impl.yangmodels.YangModelCmHandle
import org.onap.cps.ncmp.api.inventory.InventoryPersistence
import org.onap.ncmp.cmhandle.lcm.event.NcmpEvent
import spock.lang.Specification

class NcmpEventsServiceSpec extends Specification {

    def mockInventoryPersistence = Mock(InventoryPersistence)
    def mockNcmpEventsPublisher = Mock(NcmpEventsPublisher)
    def mockNcmpEventsMapper = Mock(NcmpEventsCreator)

    def objectUnderTest = new NcmpEventsService(mockInventoryPersistence, mockNcmpEventsPublisher, mockNcmpEventsMapper)

    def 'Create and Publish event for #operation'() {
        given: 'a cm handle id and operation and responses are mocked'
            mockResponses('test-cm-handle-id', 'test-topic')
        when: 'service is called to publish ncmp event'
            objectUnderTest.publishNcmpEvent('test-cm-handle-id')
        then: 'no exception is thrown'
            noExceptionThrown()
    }

    def mockResponses(cmHandleId, topicName) {

        def yangModelCmHandle = new YangModelCmHandle(id: cmHandleId, publicProperties: [new YangModelCmHandle.Property('publicProperty1', 'value1')], dmiProperties: [])
        def ncmpEvent = new NcmpEvent(eventId: UUID.randomUUID().toString(), eventCorrelationId: cmHandleId)
        def ncmpServiceCmhandle = YangDataConverter.convertYangModelCmHandleToNcmpServiceCmHandle(yangModelCmHandle)

        mockInventoryPersistence.getYangModelCmHandle(cmHandleId) >> yangModelCmHandle
        mockNcmpEventsMapper.populateNcmpEvent(cmHandleId, ncmpServiceCmhandle) >> ncmpEvent
        mockNcmpEventsPublisher.publishEvent(topicName, cmHandleId, ncmpEvent) >> {}
    }


}
