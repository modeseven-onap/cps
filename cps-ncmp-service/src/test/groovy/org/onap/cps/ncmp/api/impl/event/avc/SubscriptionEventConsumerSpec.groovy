/*
 * ============LICENSE_START=======================================================
 * Copyright (c) 2022-2023 Nordix Foundation.
 *  ================================================================================
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an 'AS IS' BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
 *  ============LICENSE_END=========================================================
 */

package org.onap.cps.ncmp.api.impl.event.avc

import com.fasterxml.jackson.databind.ObjectMapper
import org.onap.cps.ncmp.api.kafka.MessagingBaseSpec
import org.onap.cps.ncmp.event.model.SubscriptionEvent
import org.onap.cps.ncmp.utils.TestUtils
import org.onap.cps.spi.exceptions.OperationNotYetSupportedException
import org.onap.cps.utils.JsonObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(classes = [ObjectMapper, JsonObjectMapper])
class SubscriptionEventConsumerSpec extends MessagingBaseSpec {

    def subscriptionEventForwarder = Mock(SubscriptionEventForwarder)
    def objectUnderTest = new SubscriptionEventConsumer(subscriptionEventForwarder)

    @Autowired
    JsonObjectMapper jsonObjectMapper

    def 'Consume and forward valid CM create message'() {
        given: 'an event with data category CM'
            def jsonData = TestUtils.getResourceFileContent('avcSubscriptionCreationEvent.json')
            def testEventSent = jsonObjectMapper.convertJsonString(jsonData, SubscriptionEvent.class)
        and: 'notifications are enabled'
            objectUnderTest.notificationFeatureEnabled = true
        when: 'the valid event is consumed'
            objectUnderTest.consumeSubscriptionEvent(testEventSent)
        then: 'the event is forwarded'
            1 * subscriptionEventForwarder.forwardCreateSubscriptionEvent(testEventSent)
    }

    def 'Consume valid CM create message where notifications are disabled'() {
        given: 'an event with data category CM'
            def jsonData = TestUtils.getResourceFileContent('avcSubscriptionCreationEvent.json')
            def testEventSent = jsonObjectMapper.convertJsonString(jsonData, SubscriptionEvent.class)
        and: 'notifications are disabled'
            objectUnderTest.notificationFeatureEnabled = false
        when: 'the valid event is consumed'
            objectUnderTest.consumeSubscriptionEvent(testEventSent)
        then: 'the event is forwarded'
            0 * subscriptionEventForwarder.forwardCreateSubscriptionEvent(testEventSent)
    }

    def 'Consume valid FM message'() {
        given: 'an event'
            def jsonData = TestUtils.getResourceFileContent('avcSubscriptionCreationEvent.json')
            def testEventSent = jsonObjectMapper.convertJsonString(jsonData, SubscriptionEvent.class)
        and: 'dataCategory is set to FM'
            testEventSent.getEvent().getDataType().setDataCategory("FM")
        when: 'the valid event is consumed'
            objectUnderTest.consumeSubscriptionEvent(testEventSent)
        then: 'no exception is thrown'
            noExceptionThrown()
        and: 'No event is forwarded'
            0 * subscriptionEventForwarder.forwardCreateSubscriptionEvent(*_)
    }

    def 'Consume event with wrong datastore causes an exception'() {
        given: 'an event'
            def jsonData = TestUtils.getResourceFileContent('avcSubscriptionCreationEvent.json')
            def testEventSent = jsonObjectMapper.convertJsonString(jsonData, SubscriptionEvent.class)
        and: 'datastore is set to a non passthrough datastore'
            testEventSent.getEvent().getPredicates().setDatastore("operational")
        when: 'the valid event is consumed'
            objectUnderTest.consumeSubscriptionEvent(testEventSent)
        then: 'an operation not yet supported exception is thrown'
            thrown(OperationNotYetSupportedException)
    }

}
