/*
 *  ============LICENSE_START=======================================================
 *  Copyright (C) 2022 Nordix Foundation
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

package org.onap.cps.spi.performance

import org.onap.cps.spi.CpsModulePersistenceService
import org.onap.cps.spi.entities.SchemaSetEntity
import org.onap.cps.spi.impl.CpsPersistenceSpecBase
import org.onap.cps.spi.model.ModuleReference
import org.onap.cps.spi.repository.DataspaceRepository
import org.onap.cps.spi.repository.ModuleReferenceRepository
import org.onap.cps.spi.repository.SchemaSetRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql
import org.springframework.util.StopWatch

import java.util.concurrent.ThreadLocalRandom

class CpsModuleReferenceRepositoryPerfTest extends CpsPersistenceSpecBase {

    static final String PERF_TEST_DATA = '/data/perf-test.sql'

    def NEW_RESOURCE_CONTENT = 'module stores {\n' +
        '    yang-version 1.1;\n' +
        '    namespace "org:onap:ccsdk:sample";\n' +
        '\n' +
        '    prefix book-store;\n' +
        '\n' +
        '    revision "2020-09-15" {\n' +
        '        description\n' +
        '        "Sample Model";\n' +
        '    }' +
        '}'

    @Autowired
    CpsModulePersistenceService objectUnderTest

    @Autowired
    DataspaceRepository dataspaceRepository

    @Autowired
    SchemaSetRepository schemaSetRepository

    @Autowired
    ModuleReferenceRepository moduleReferenceRepository

    @Sql([CLEAR_DATA, PERF_TEST_DATA])
    def 'Store new schema set with many modules'() {
        when: 'a new schema set with 200 modules is stored'
            def newYangResourcesNameToContentMap = [:]
            (1..200).each {
                def year = 2000 + it
                def resourceName = "module${it}".toString()
                def moduleName = "stores${it}"
                def content = NEW_RESOURCE_CONTENT.replace('2020',String.valueOf(year)).replace('stores',moduleName)
                newYangResourcesNameToContentMap.put(resourceName, content)
            }
            objectUnderTest.storeSchemaSet('PERF-DATASPACE', 'perfSchemaSet', newYangResourcesNameToContentMap)
        then: 'the schema set is persisted correctly'
            def dataspaceEntity = dataspaceRepository.getByName('PERF-DATASPACE')
            SchemaSetEntity result = schemaSetRepository.getByDataspaceAndName(dataspaceEntity, 'perfSchemaSet')
            result.yangResources.size() == 200
        and: 'identification of new module resources is fast enough (1,000 executions less then 6,000 milliseconds)'
            def stopWatch = new StopWatch()
            1000.times() {
                def moduleReferencesToCheck = createModuleReferencesWithRandomMatchingExistingModuleReferences()
                stopWatch.start()
                def newModuleReferences = moduleReferenceRepository.identifyNewModuleReferences(moduleReferencesToCheck)
                stopWatch.stop()
                assert newModuleReferences.size() > 0 && newModuleReferences.size() < 300
            }
            assert stopWatch.getTotalTimeMillis() < 6000
    }

    def createModuleReferencesWithRandomMatchingExistingModuleReferences() {
        def moduleReferences = []
        (1..250).each {
            def randomNumber = ThreadLocalRandom.current().nextInt(1, 300)
            def year = 2000 + randomNumber
            def moduleName = "stores${randomNumber}"
            moduleReferences.add(new ModuleReference(moduleName, "${year}-09-15"))
        }
        return moduleReferences
    }

}
