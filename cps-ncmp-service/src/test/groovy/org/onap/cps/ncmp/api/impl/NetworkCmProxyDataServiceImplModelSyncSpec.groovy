/*
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2021-2022 Nordix Foundation
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

package org.onap.cps.ncmp.api.impl

import org.onap.cps.api.CpsAdminService
import org.onap.cps.api.CpsModuleService
import org.onap.cps.ncmp.api.impl.operations.DmiDataOperations
import org.onap.cps.ncmp.api.impl.operations.DmiModelOperations
import org.onap.cps.ncmp.api.models.CmHandle
import org.onap.cps.ncmp.api.models.PersistenceCmHandle
import org.onap.cps.spi.model.ModuleReference
import org.onap.cps.utils.JsonObjectMapper
import spock.lang.Specification

class NetworkCmProxyDataServiceImplModelSyncSpec extends Specification {

    def nullCpsDataService = null
    def mockJsonObjectMapper = Mock(JsonObjectMapper)
    def mockCpsModuleService = Mock(CpsModuleService)
    def mockCpsAdminService = Mock(CpsAdminService)
    def mockDmiModelOperations = Mock(DmiModelOperations)
    def mockDmiDataOperations = Mock(DmiDataOperations)
    def nullNetworkCmProxyDataServicePropertyHandler = null

    def objectUnderTest = new NetworkCmProxyDataServiceImpl(nullCpsDataService, mockJsonObjectMapper, mockDmiDataOperations, mockDmiModelOperations,
        mockCpsModuleService, mockCpsAdminService, nullNetworkCmProxyDataServicePropertyHandler)

    def expectedDataspaceName = 'NFP-Operational'

    def 'Sync model for a (new) cm handle with #scenario'() {
        given: 'persistence cm handle is given'
            def cmHandle = new CmHandle()
            def dmiServiceName = 'some service name'
            cmHandle.cmHandleID = 'cm handle id 1'
            cmHandle.dmiProperties = dmiProperties
            def persistenceCmHandle = PersistenceCmHandle.toPersistenceCmHandle(dmiServiceName, '' , '', cmHandle)
        and: 'DMI operations returns some module references'
            def moduleReferences =  [ new ModuleReference(moduleName:'module1',revision:'1'),
                                                            new ModuleReference(moduleName:'module2',revision:'2') ]
            mockDmiModelOperations.getModuleReferences(persistenceCmHandle) >> moduleReferences
        and: 'CPS-Core returns list of existing module resources'
            mockCpsModuleService.getYangResourceModuleReferences(expectedDataspaceName) >> existingModuleResourcesInCps
        and: 'DMI-Plugin returns resource(s) for "new" module(s)'
            mockDmiModelOperations.getNewYangResourcesFromDmi(persistenceCmHandle, [new ModuleReference('module1', '1')]) >> yangResourceToContentMap
        when: 'module sync is triggered'
            objectUnderTest.syncModulesAndCreateAnchor(persistenceCmHandle)
        then: 'the CPS module service is called once with the correct parameters'
            1 * mockCpsModuleService.createSchemaSetFromModules(expectedDataspaceName, persistenceCmHandle.getId(), yangResourceToContentMap, expectedKnownModules)
        and: 'admin service create anchor method has been called with correct parameters'
            1 * mockCpsAdminService.createAnchor(expectedDataspaceName, persistenceCmHandle.getId(), persistenceCmHandle.getId())
        where: 'the following parameters are used'
            scenario                        | dmiProperties        | existingModuleResourcesInCps                                               | yangResourceToContentMap      || expectedKnownModules
            'one unknown module'            | ['name1': 'value1']  | [new ModuleReference('module2', '2'), new ModuleReference('module3', '3')] | [module1: 'some yang source'] || [new ModuleReference('module2', '2')]
            'no add. properties'            | [:]                  | [new ModuleReference('module2', '2'), new ModuleReference('module3', '3')] | [module1: 'some yang source'] || [new ModuleReference('module2', '2')]
            'no unknown module'             | [:]                  | [new ModuleReference('module1', '1'), new ModuleReference('module2', '2')] | [:]                           || [new ModuleReference('module1', '1'), new ModuleReference('module2', '2')]
    }
}
