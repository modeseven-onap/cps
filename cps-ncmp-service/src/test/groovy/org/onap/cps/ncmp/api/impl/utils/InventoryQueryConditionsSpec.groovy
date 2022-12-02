/*
 * ============LICENSE_START========================================================
 * Copyright (c) 2022 Nordix Foundation.
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

import spock.lang.Specification

class InventoryQueryConditionsSpec extends Specification {

    def 'Inventory query condition names.'() {
        expect: '3 conditions with the correct names'
            assert InventoryQueryConditions.ALL_CONDITION_NAMES.size() == 3
            assert InventoryQueryConditions.ALL_CONDITION_NAMES.containsAll('hasAllProperties',
                                                                            'hasAllAdditionalProperties',
                                                                            'cmHandleWithDmiPlugin')
    }


}
