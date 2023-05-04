/*
 * ============LICENSE_START=======================================================
 * Copyright (c) 2021 Bell Canada.
 * Modifications Copyright (C) 2021-2023 Nordix Foundation
 * Modifications Copyright (C) 2022-2023 TechMahindra Ltd.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
*/

package org.onap.cps.spi.impl

import com.fasterxml.jackson.databind.ObjectMapper
import org.hibernate.StaleStateException
import org.onap.cps.spi.FetchDescendantsOption
import org.onap.cps.spi.entities.AnchorEntity
import org.onap.cps.spi.entities.DataspaceEntity
import org.onap.cps.spi.entities.FragmentEntity
import org.onap.cps.spi.entities.FragmentExtract
import org.onap.cps.spi.exceptions.ConcurrencyException
import org.onap.cps.spi.exceptions.DataValidationException
import org.onap.cps.spi.model.DataNode
import org.onap.cps.spi.model.DataNodeBuilder
import org.onap.cps.spi.repository.AnchorRepository
import org.onap.cps.spi.repository.DataspaceRepository
import org.onap.cps.spi.repository.FragmentRepository
import org.onap.cps.spi.utils.SessionManager
import org.onap.cps.utils.JsonObjectMapper
import org.springframework.dao.DataIntegrityViolationException
import spock.lang.Specification

class CpsDataPersistenceServiceSpec extends Specification {

    def mockDataspaceRepository = Mock(DataspaceRepository)
    def mockAnchorRepository = Mock(AnchorRepository)
    def mockFragmentRepository = Mock(FragmentRepository)
    def jsonObjectMapper = new JsonObjectMapper(new ObjectMapper())
    def mockSessionManager = Mock(SessionManager)

    def objectUnderTest = Spy(new CpsDataPersistenceServiceImpl(mockDataspaceRepository, mockAnchorRepository,
            mockFragmentRepository, jsonObjectMapper, mockSessionManager))

    static def anchorEntity = new AnchorEntity(id: 123, dataspace: new DataspaceEntity(id: 1))

    def setup() {
        mockAnchorRepository.getByDataspaceAndName(_, _) >> anchorEntity
    }

    def 'Storing data nodes individually when batch operation fails'(){
        given: 'two data nodes and supporting repository mock behavior'
            def dataNode1 = createDataNodeAndMockRepositoryMethodSupportingIt('xpath1','OK')
            def dataNode2 = createDataNodeAndMockRepositoryMethodSupportingIt('xpath2','OK')
        and: 'the batch store operation will fail'
            mockFragmentRepository.saveAll(*_) >> { throw new DataIntegrityViolationException("Exception occurred") }
        when: 'trying to store data nodes'
            objectUnderTest.storeDataNodes('dataSpaceName', 'anchorName', [dataNode1, dataNode2])
        then: 'the two data nodes are saved individually'
            2 * mockFragmentRepository.save(_)
    }

    def 'Store single data node.'() {
        given: 'a data node'
            def dataNode = new DataNode()
        when: 'storing a single data node'
            objectUnderTest.storeDataNode('dataspace1', 'anchor1', dataNode)
        then: 'the call is redirected to storing a collection of data nodes with just the given data node'
            1 * objectUnderTest.storeDataNodes('dataspace1', 'anchor1', [dataNode])
    }

    def 'Handling of StaleStateException (caused by concurrent updates) during update data nodes and descendants.'() {
        given: 'the system can update one datanode and has two more datanodes that throw an exception while updating'
            def dataNodes = createDataNodesAndMockRepositoryMethodSupportingThem([
                '/node1': 'OK',
                '/node2': 'EXCEPTION',
                '/node3': 'EXCEPTION'])
        and: 'the batch update will therefore also fail'
            mockFragmentRepository.saveAll(*_) >> { throw new StaleStateException("concurrent updates") }
        when: 'attempt batch update data nodes'
            objectUnderTest.updateDataNodesAndDescendants('some-dataspace', 'some-anchor', dataNodes)
        then: 'concurrency exception is thrown'
            def thrown = thrown(ConcurrencyException)
            assert thrown.message == 'Concurrent Transactions'
        and: 'it does not contain the successfull datanode'
            assert !thrown.details.contains('/node1')
        and: 'it contains the failed datanodes'
            assert thrown.details.contains('/node2')
            assert thrown.details.contains('/node3')
    }

    def 'Retrieving a data node with a property JSON value of #scenario'() {
        given: 'the db has a fragment with an attribute property JSON value of #scenario'
            mockFragmentWithJson("{\"some attribute\": ${dataString}}")
        when: 'getting the data node represented by this fragment'
            def dataNode = objectUnderTest.getDataNodes('my-dataspace', 'my-anchor',
                    '/parent-01', FetchDescendantsOption.INCLUDE_ALL_DESCENDANTS)
        then: 'the leaf is of the correct value and data type'
            def attributeValue = dataNode[0].leaves.get('some attribute')
            assert attributeValue == expectedValue
            assert attributeValue.class == expectedDataClass
        where: 'the following Data Type is passed'
            scenario                              | dataString            || expectedValue     | expectedDataClass
            'just numbers'                        | '15174'               || 15174             | Integer
            'number with dot'                     | '15174.32'            || 15174.32          | Double
            'number with 0 value after dot'       | '15174.0'             || 15174.0           | Double
            'number with 0 value before dot'      | '0.32'                || 0.32              | Double
            'number higher than max int'          | '2147483648'          || 2147483648        | Long
            'just text'                           | '"Test"'              || 'Test'            | String
            'number with exponent'                | '1.2345e5'            || 1.2345e5          | Double
            'number higher than max int with dot' | '123456789101112.0'   || 123456789101112.0 | Double
            'text and numbers'                    | '"String = \'1234\'"' || "String = '1234'" | String
            'number as String'                    | '"12345"'             || '12345'           | String
    }

    def 'Retrieving a data node with invalid JSON'() {
        given: 'a fragment with invalid JSON'
            mockFragmentWithJson('{invalid json')
        when: 'getting the data node represented by this fragment'
            objectUnderTest.getDataNodes('my-dataspace', 'my-anchor',
                    '/parent-01', FetchDescendantsOption.INCLUDE_ALL_DESCENDANTS)
        then: 'a data validation exception is thrown'
            thrown(DataValidationException)
    }

    def 'Retrieving multiple data nodes.'() {
        given: 'fragment repository returns a collection of fragments'
            mockFragmentRepository.findExtractsWithDescendants(123, ['/xpath1', '/xpath2'] as Set, _) >> [
                mockFragmentExtract(1, null, 123, '/xpath1', null),
                mockFragmentExtract(2, null, 123, '/xpath2', null)
            ]
        when: 'getting data nodes for 2 xpaths'
            def result = objectUnderTest.getDataNodesForMultipleXpaths('some-dataspace', 'some-anchor', ['/xpath1', '/xpath2'], FetchDescendantsOption.INCLUDE_ALL_DESCENDANTS)
        then: '2 data nodes are returned'
            assert result.size() == 2
    }

    def 'start session'() {
        when: 'start session'
            objectUnderTest.startSession()
        then: 'the session manager method to start session is invoked'
            1 * mockSessionManager.startSession()
    }

    def 'close session'() {
        given: 'session ID'
            def someSessionId = 'someSessionId'
        when: 'close session method is called with session ID as parameter'
            objectUnderTest.closeSession(someSessionId)
        then: 'the session manager method to close session is invoked with parameter'
            1 * mockSessionManager.closeSession(someSessionId, mockSessionManager.WITH_COMMIT)
    }

    def 'Lock anchor.'(){
        when: 'lock anchor method is called with anchor entity details'
            objectUnderTest.lockAnchor('mySessionId', 'myDataspaceName', 'myAnchorName', 123L)
        then: 'the session manager method to lock anchor is invoked with same parameters'
            1 * mockSessionManager.lockAnchor('mySessionId', 'myDataspaceName', 'myAnchorName', 123L)
    }

    def 'update data node leaves: #scenario'(){
        given: 'A node exists for the given xpath'
            mockFragmentRepository.getByAnchorAndXpath(_, '/some/xpath') >> new FragmentEntity(xpath: '/some/xpath', attributes:  existingAttributes)
        when: 'the node leaves are updated'
            objectUnderTest.updateDataLeaves('some-dataspace', 'some-anchor', '/some/xpath', newAttributes as Map<String, Serializable>)
        then: 'the fragment entity saved has the original and new attributes'
            1 * mockFragmentRepository.save({fragmentEntity -> {
                assert fragmentEntity.getXpath() == '/some/xpath'
                assert fragmentEntity.getAttributes() == mergedAttributes
            }})
        where: 'the following attributes combinations are used'
            scenario                      | existingAttributes     | newAttributes         | mergedAttributes
            'add new leaf'                | '{"existing":"value"}' | ["new":"value"]       | '{"existing":"value","new":"value"}'
            'update existing leaf'        | '{"existing":"value"}' | ["existing":"value2"] | '{"existing":"value2"}'
            'update nothing with nothing' | ''                     | []                    | ''
            'update with nothing'         | '{"existing":"value"}' | []                    | '{"existing":"value"}'
            'update with same value'      | '{"existing":"value"}' | ["existing":"value"]  | '{"existing":"value"}'
    }

    def 'update data node and descendants: #scenario'(){
        given: 'the fragment repository returns fragment entities related to the xpath inputs'
            mockFragmentRepository.findExtractsWithDescendants(_, [] as Set, _) >> []
            mockFragmentRepository.findExtractsWithDescendants(_, ['/test/xpath'] as Set, _) >> [
                mockFragmentExtract(1, null, 123, '/test/xpath', null)
            ]
        when: 'replace data node tree'
            objectUnderTest.updateDataNodesAndDescendants('dataspaceName', 'anchorName', dataNodes)
        then: 'call fragment repository save all method'
            1 * mockFragmentRepository.saveAll({fragmentEntities -> assert fragmentEntities as List == expectedFragmentEntities})
        where: 'the following Data Type is passed'
            scenario                         | dataNodes                                                                          || expectedFragmentEntities
            'empty data node list'           | []                                                                                 || []
            'one data node in list'          | [new DataNode(xpath: '/test/xpath', leaves: ['id': 'testId'], childDataNodes: [])] || [new FragmentEntity(xpath: '/test/xpath', attributes: '{"id":"testId"}', anchor: anchorEntity, childFragments: [])]
    }

    def 'update data nodes and descendants'() {
        given: 'the fragment repository returns fragment entities related to the xpath inputs'
            mockFragmentRepository.findExtractsWithDescendants(_, ['/test/xpath1', '/test/xpath2'] as Set, _) >> [
                mockFragmentExtract(1, null, 123, '/test/xpath1', null),
                mockFragmentExtract(2, null, 123, '/test/xpath2', null)
            ]
        and: 'some data nodes with descendants'
            def dataNode1 = new DataNode(xpath: '/test/xpath1', leaves: ['id': 'testId1'], childDataNodes: [new DataNode(xpath: '/test/xpath1/child', leaves: ['id': 'childTestId1'])])
            def dataNode2 = new DataNode(xpath: '/test/xpath2', leaves: ['id': 'testId2'], childDataNodes: [new DataNode(xpath: '/test/xpath2/child', leaves: ['id': 'childTestId2'])])
        when: 'the fragment entities are update by the data nodes'
            objectUnderTest.updateDataNodesAndDescendants('dataspace', 'anchor', [dataNode1, dataNode2])
        then: 'call fragment repository save all method is called with the updated fragments'
            1 * mockFragmentRepository.saveAll({fragmentEntities -> {
                assert fragmentEntities.size() == 2
                def fragmentEntityPerXpath = fragmentEntities.collectEntries { [it.xpath, it] }
                assert fragmentEntityPerXpath.get('/test/xpath1').childFragments.first().attributes == '{"id":"childTestId1"}'
                assert fragmentEntityPerXpath.get('/test/xpath2').childFragments.first().attributes == '{"id":"childTestId2"}'
            }})
    }

    def createDataNodeAndMockRepositoryMethodSupportingIt(xpath, scenario) {
        def dataNode = new DataNodeBuilder().withXpath(xpath).build()
        def fragmentEntity = new FragmentEntity(xpath: xpath, childFragments: [])
        mockFragmentRepository.getByAnchorAndXpath(_, xpath) >> fragmentEntity
        if ('EXCEPTION' == scenario) {
            mockFragmentRepository.save(fragmentEntity) >> { throw new StaleStateException("concurrent updates") }
        }
        return dataNode
    }

    def createDataNodesAndMockRepositoryMethodSupportingThem(Map<String, String> xpathToScenarioMap) {
        def dataNodes = []
        def fragmentExtracts = []
        def fragmentId = 1
        xpathToScenarioMap.each {
            def xpath = it.key
            def scenario = it.value
            def dataNode = new DataNodeBuilder().withXpath(xpath).build()
            dataNodes.add(dataNode)
            def fragmentExtract = mockFragmentExtract(fragmentId, null, 123, xpath, null)
            fragmentExtracts.add(fragmentExtract)
            def fragmentEntity = new FragmentEntity(id: fragmentId, anchor: anchorEntity, xpath: xpath, childFragments: [])
            if ('EXCEPTION' == scenario) {
                mockFragmentRepository.save(fragmentEntity) >> { throw new StaleStateException("concurrent updates") }
            }
            fragmentId++
        }
        mockFragmentRepository.findExtractsWithDescendants(_, xpathToScenarioMap.keySet(), _) >> fragmentExtracts
        return dataNodes
    }

    def mockFragmentWithJson(json) {
        def fragmentExtract = mockFragmentExtract(456, null, 123, '/parent-01', json)
        mockFragmentRepository.findExtractsWithDescendants(123, ['/parent-01'] as Set, _) >> [fragmentExtract]
    }

    def mockFragmentExtract(id, parentId, anchorId, xpath, attributes) {
        def fragmentExtract = Mock(FragmentExtract)
        fragmentExtract.getId() >> id
        fragmentExtract.getParentId() >> parentId
        fragmentExtract.getAnchorId() >> anchorId
        fragmentExtract.getXpath() >> xpath
        fragmentExtract.getAttributes() >> attributes
        return fragmentExtract
    }

}
