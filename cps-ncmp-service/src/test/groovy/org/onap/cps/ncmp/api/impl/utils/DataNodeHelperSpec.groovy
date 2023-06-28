/*
 * ============LICENSE_START========================================================
 * Copyright (c) 2023 Nordix Foundation.
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

package org.onap.cps.ncmp.api.impl.utils

import org.onap.cps.ncmp.api.impl.subscriptions.SubscriptionStatus
import org.onap.cps.spi.model.DataNodeBuilder

class DataNodeHelperSpec extends DataNodeBaseSpec {

    def 'Get data node leaves as expected from a nested data node.'() {
        given: 'a nested data node'
            def dataNode = new DataNodeBuilder().withDataspace('NCMP-Admin')
                .withAnchor('AVC-Subscriptions').withXpath('/subscription-registry/subscription')
                .withLeaves([clientID:'SCO-9989752', isTagged:false, subscriptionName:'cm-subscription-001'])
                .withChildDataNodes([dataNode4]).build()
        when: 'the nested data node is flatten and retrieves the leaves '
            def result = DataNodeHelper.getDataNodeLeaves([dataNode])
        then: 'the result list size is 5'
            result.size() == 5
        and: 'all the leaves result list are equal to given leaves of data nodes'
            result[0] == [clientID:'SCO-9989752', isTagged:false, subscriptionName:'cm-subscription-001']
            result[1] == [datastore:'passthrough-running']
            result[2] == [status:'PENDING', cmHandleId:'CMHandle3']
            result[3] == [status:'ACCEPTED', cmHandleId:'CMHandle2']
            result[4] == [status:'REJECTED', cmHandleId:'CMHandle1']
    }

    def 'Get cm handle id to status as expected from a nested data node.'() {
        given: 'a nested data node'
            def dataNode = new DataNodeBuilder().withDataspace('NCMP-Admin')
                .withAnchor('AVC-Subscriptions').withXpath('/subscription-registry/subscription')
                .withLeaves([clientID:'SCO-9989752', isTagged:false, subscriptionName:'cm-subscription-001'])
                .withChildDataNodes([dataNode4]).build()
        and: 'the nested data node is flatten and retrieves the leaves '
            def leaves = DataNodeHelper.getDataNodeLeaves([dataNode])
        when:'cm handle id to status is retrieved'
            def result = DataNodeHelper.getCmHandleIdToStatus(leaves)
        then: 'the result list size is 3'
            result.size() == 3
        and: 'the result contains expected values'
            result[0] as List == ['PENDING', 'CMHandle3']
            result[1] as List == ['ACCEPTED', 'CMHandle2']
            result[2] as List == ['REJECTED', 'CMHandle1']
    }

    def 'Get cm handle id to status map as expected from list of collection' () {
        given: 'a list of collection'
            def cmHandleCollection = [['PENDING', 'CMHandle3'], ['ACCEPTED', 'CMHandle2'], ['REJECTED', 'CMHandle1']]
        when: 'the map is formed up with a method call'
            def result = DataNodeHelper.getCmHandleIdToStatusMap(cmHandleCollection)
        then: 'the map values are as expected'
            result.keySet() == ['CMHandle3', 'CMHandle2', 'CMHandle1'] as Set
            result.values() as List == [SubscriptionStatus.PENDING, SubscriptionStatus.ACCEPTED, SubscriptionStatus.REJECTED]
    }


    def 'Get cm handle id to status map as expected from a nested data node.'() {
        given: 'a nested data node'
            def dataNode = new DataNodeBuilder().withDataspace('NCMP-Admin')
                .withAnchor('AVC-Subscriptions').withXpath('/subscription-registry/subscription')
                .withLeaves([clientID:'SCO-9989752', isTagged:false, subscriptionName:'cm-subscription-001'])
                .withChildDataNodes([dataNode4]).build()
        when:'cm handle id to status is being extracted'
            def result = DataNodeHelper.getCmHandleIdToStatusMapFromDataNodes([dataNode]);
        then: 'the keys are retrieved as expected'
            result.keySet() == ['CMHandle3','CMHandle2','CMHandle1'] as Set
    }
}
