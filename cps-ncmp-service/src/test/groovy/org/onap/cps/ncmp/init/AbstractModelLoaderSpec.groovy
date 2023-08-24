/*
 *  ============LICENSE_START=======================================================
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

package org.onap.cps.ncmp.init

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.core.read.ListAppender
import org.onap.cps.api.CpsAdminService
import org.onap.cps.api.CpsDataService
import org.onap.cps.api.CpsModuleService
import org.onap.cps.ncmp.api.impl.exception.NcmpStartUpException
import org.onap.cps.spi.exceptions.AlreadyDefinedException
import org.springframework.boot.SpringApplication
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Specification

class AbstractModelLoaderSpec extends Specification {

    def mockCpsAdminService = Mock(CpsAdminService)
    def mockCpsModuleService = Mock(CpsModuleService)
    def mockCpsDataService = Mock(CpsDataService)
    def objectUnderTest = Spy(new TestModelLoader(mockCpsAdminService, mockCpsModuleService, mockCpsDataService))

    def applicationContext = new AnnotationConfigApplicationContext()

    def yangResourceToContentMap
    def logger = (Logger) LoggerFactory.getLogger(AbstractModelLoader)
    def loggingListAppender

    void setup() {
        yangResourceToContentMap = objectUnderTest.createYangResourceToContentMap('subscription.yang')
        logger.setLevel(Level.DEBUG)
        loggingListAppender = new ListAppender()
        logger.addAppender(loggingListAppender)
        loggingListAppender.start()
        applicationContext.refresh()
    }

    void cleanup() {
        ((Logger) LoggerFactory.getLogger(SubscriptionModelLoader.class)).detachAndStopAllAppenders()
        applicationContext.close()
    }

    def 'Application ready event'() {
        when: 'Application (ready) event is triggered'
            objectUnderTest.onApplicationEvent(Mock(ApplicationReadyEvent))
        then: 'the onboard/upgrade method is executed'
            1 * objectUnderTest.onboardOrUpgradeModel()
    }

    def 'Application ready event with start up exception'() {
        given: 'a start up exception is thrown doing model onboarding'
            objectUnderTest.onboardOrUpgradeModel() >> { throw new NcmpStartUpException('test message','details are not logged') }
        when: 'Application (ready) event is triggered'
            objectUnderTest.onApplicationEvent(new ApplicationReadyEvent(new SpringApplication(), null, applicationContext, null))
        then: 'the exception message is logged'
            def logs = loggingListAppender.list.toString()
            assert logs.contains('test message')
    }

    def 'Wait for non-existing dataspace'() {
        when: 'wait for the dataspace'
            objectUnderTest.waitUntilDataspaceIsAvailable('some dataspace')
        then: 'a startup exception is thrown'
            def thrown = thrown(NcmpStartUpException)
            assert thrown.message.contains('Retrieval of NCMP dataspace failed')
    }

    def 'Create schema set.'() {
        when: 'creating a schema set'
            objectUnderTest.createSchemaSet('some dataspace','new name','subscription.yang')
        then: 'the operation is delegated to the admin service'
            1 * mockCpsModuleService.createSchemaSet('some dataspace','new name',_)
    }

    def 'Create schema set with already defined exception.'() {
        given: 'the module service throws an already defined exception'
            mockCpsModuleService.createSchemaSet(*_) >>  { throw AlreadyDefinedException.forSchemaSet('name','context',null) }
        when: 'attempt to create a schema set'
            objectUnderTest.createSchemaSet('some dataspace','new name','subscription.yang')
        then: 'the exception is ignored i.e. no exception thrown up'
            noExceptionThrown()
        and: 'the exception message is logged'
            def logs = loggingListAppender.list.toString()
            assert logs.contains('Creating new schema set failed as schema set already exists')
    }

    def 'Create schema set with non existing yang file.'() {
        when: 'attempt to create a schema set from a non existing file'
            objectUnderTest.createSchemaSet('some dataspace','some name','no such yang file')
        then: 'a startup exception with correct message and details is thrown'
            def thrown = thrown(NcmpStartUpException)
            assert thrown.message.contains('Creating schema set failed')
            assert thrown.details.contains('unable to read file')
    }

    def 'Create anchor.'() {
        when: 'creating an anchor'
            objectUnderTest.createAnchor('some dataspace','some schema set','new name')
        then: 'thr operation is delegated to the admin service'
            1 * mockCpsAdminService.createAnchor('some dataspace','some schema set', 'new name')
    }

    def 'Create anchor with already defined exception.'() {
        given: 'the admin service throws an already defined exception'
            mockCpsAdminService.createAnchor(*_)>>  { throw AlreadyDefinedException.forAnchor('name','context',null) }
        when: 'attempt to create anchor'
            objectUnderTest.createAnchor('some dataspace','some schema set','new name')
        then: 'the exception is ignored i.e. no exception thrown up'
            noExceptionThrown()
        and: 'the exception message is logged'
            def logs = loggingListAppender.list.toString()
            assert logs.contains('Creating new anchor failed as anchor already exists')
    }

    def 'Create anchor with any other exception.'() {
        given: 'the admin service throws a exception'
            mockCpsAdminService.createAnchor(*_)>>  { throw new RuntimeException('test message') }
        when: 'attempt to create anchor'
            objectUnderTest.createAnchor('some dataspace','some schema set','new name')
        then: 'a startup exception with correct message and details is thrown'
            def thrown = thrown(NcmpStartUpException)
            assert thrown.message.contains('Creating anchor failed')
            assert thrown.details.contains('test message')
    }

    def 'Create top level node.'() {
        when: 'top level node is created'
            objectUnderTest.createTopLevelDataNode('dataspace','anchor','new node')
        then: 'the correct json is saved using the data service'
            1 * mockCpsDataService.saveData('dataspace','anchor', '{"new node":{}}',_)
    }

    def 'Create top level node with already defined exception.'() {
        given: 'the data service throws an Already Defined exception'
            mockCpsDataService.saveData(*_) >> { throw AlreadyDefinedException.forDataNodes([], 'some context') }
        when: 'attempt to create top level node'
            objectUnderTest.createTopLevelDataNode('dataspace','anchor','new node')
        then: 'the exception is ignored i.e. no exception thrown up'
            noExceptionThrown()
        and: 'the exception message is logged'
            def logs = loggingListAppender.list.toString()
            assert logs.contains('failed as data node already exists')
    }

    def 'Create top level node with any other exception.'() {
        given: 'the data service throws an exception'
            mockCpsDataService.saveData(*_) >> { throw new RuntimeException('test message') }
        when: 'attempt to create top level node'
            objectUnderTest.createTopLevelDataNode('dataspace','anchor','new node')
        then: 'a startup exception with correct message and details is thrown'
            def thrown = thrown(NcmpStartUpException)
            assert thrown.message.contains('Creating data node failed')
            assert thrown.details.contains('test message')
    }

    class TestModelLoader extends AbstractModelLoader {

        TestModelLoader(final CpsAdminService cpsAdminService,
                        final CpsModuleService cpsModuleService,
                        final CpsDataService cpsDataService) {
            super(cpsAdminService, cpsModuleService, cpsDataService)
            super.maximumAttemptCount = 2
            super.retryTimeMs = 1
        }

        @Override
        void onboardOrUpgradeModel() { }
    }

}
