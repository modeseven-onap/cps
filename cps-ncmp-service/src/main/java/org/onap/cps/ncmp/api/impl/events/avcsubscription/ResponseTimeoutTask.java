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
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.onap.cps.ncmp.events.avcsubscription1_0_0.dmi_to_ncmp.SubscriptionEventResponse;

@Slf4j
@RequiredArgsConstructor
public class ResponseTimeoutTask implements Runnable {

    private final IMap<String, Set<String>> forwardedSubscriptionEventCache;
    private final SubscriptionEventResponseOutcome subscriptionEventResponseOutcome;
    private final SubscriptionEventResponse subscriptionEventResponse;

    @Override
    public void run() {
        generateTimeoutResponse();
    }

    private void generateTimeoutResponse() {
        final String subscriptionClientId = subscriptionEventResponse.getData().getClientId();
        final String subscriptionName = subscriptionEventResponse.getData().getSubscriptionName();
        final String subscriptionEventId = subscriptionClientId + subscriptionName;
        if (forwardedSubscriptionEventCache.containsKey(subscriptionEventId)) {
            subscriptionEventResponseOutcome.sendResponse(subscriptionEventResponse,
                    "subscriptionCreatedStatus");
            forwardedSubscriptionEventCache.remove(subscriptionEventId);
        }
    }
}