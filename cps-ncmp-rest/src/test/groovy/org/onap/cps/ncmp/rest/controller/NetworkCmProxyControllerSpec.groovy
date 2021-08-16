/*
 *  ============LICENSE_START=======================================================
 *  Copyright (C) 2021 Pantheon.tech
 *  Modification Copyright (C) 2021 highstreet technologies GmbH
 *  Modification Copyright (C) 2021 Nordix Foundation
 *  ================================================================================
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
 *  ============LICENSE_END=========================================================
 */

package org.onap.cps.ncmp.rest.controller

import static org.onap.cps.spi.FetchDescendantsOption.INCLUDE_ALL_DESCENDANTS
import static org.onap.cps.spi.FetchDescendantsOption.OMIT_DESCENDANTS
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import org.onap.cps.TestUtils
import org.onap.cps.ncmp.api.NetworkCmProxyDataService
import org.onap.cps.spi.model.DataNodeBuilder
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

@WebMvcTest
class NetworkCmProxyControllerSpec extends Specification {

    @Autowired
    MockMvc mvc

    @SpringBean
    NetworkCmProxyDataService mockNetworkCmProxyDataService = Mock()

    @SpringBean
    ObjectMapper objectMapper = new ObjectMapper()

    @Value('${rest.api.ncmp-base-path}')
    def basePath

    def deprecatedDataNodeBaseEndPoint

    def ncmpDmiEndpoint

    def setup() {
        deprecatedDataNodeBaseEndPoint = "$basePath/v1"
        ncmpDmiEndpoint = "$basePath/ncmp-dmi/v1"
    }

    def cmHandle = 'some handle'
    def xpath = 'some xpath'

    def 'Query data node by cps path for the given cm handle with #scenario.'() {
        given: 'service method returns a list containing a data node'
            def dataNode = new DataNodeBuilder().withXpath('/xpath').build()
            def cpsPath = 'some cps-path'
            mockNetworkCmProxyDataService.queryDataNodes(cmHandle, cpsPath, expectedCpsDataServiceOption) >> [dataNode]
        and: 'the query endpoint'
            def dataNodeEndpoint = "$deprecatedDataNodeBaseEndPoint/cm-handles/$cmHandle/nodes/query"
        when: 'query data nodes API is invoked'
            def response = mvc.perform(get(dataNodeEndpoint)
                    .param('cps-path', cpsPath)
                    .param('include-descendants', includeDescendantsOption))
                    .andReturn().response
        then: 'the response contains the the datanode in json format'
            response.status == HttpStatus.OK.value()
            def expectedJsonContent = new Gson().toJson(dataNode)
            response.getContentAsString().contains(expectedJsonContent)
        where: 'the following options for include descendants are provided in the request'
            scenario                    | includeDescendantsOption || expectedCpsDataServiceOption
            'no descendants by default' | ''                       || OMIT_DESCENDANTS
            'no descendant explicitly'  | 'false'                  || OMIT_DESCENDANTS
            'descendants'               | 'true'                   || INCLUDE_ALL_DESCENDANTS
    }

    def 'Create data node: #scenario.'() {
        given: 'json data'
            def jsonData = 'json data'
        when: 'post request is performed'
            def response = mvc.perform(
                    post("$deprecatedDataNodeBaseEndPoint/cm-handles/$cmHandle/nodes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonData)
                            .param('xpath', reqXpath)
            ).andReturn().response
        then: 'the service method is invoked once with expected parameters'
            1 * mockNetworkCmProxyDataService.createDataNode(cmHandle, usedXpath, jsonData)
        and: 'response status indicates success'
            response.status == HttpStatus.CREATED.value()
        where: 'following parameters were used'
            scenario             | reqXpath || usedXpath
            'no xpath parameter' | ''       || '/'
            'root xpath'         | '/'      || '/'
            'parent node xpath'  | '/xpath' || '/xpath'
    }

    def 'Add list-node elements.'() {
        given: 'json data and parent node xpath'
            def jsonData = 'json data'
            def parentNodeXpath = 'parent node xpath'
        when: 'post request is performed'
            def response = mvc.perform(
                    post("$deprecatedDataNodeBaseEndPoint/cm-handles/$cmHandle/list-node")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonData)
                            .param('xpath', parentNodeXpath)
            ).andReturn().response
        then: 'the service method is invoked once with expected parameters'
            1 * mockNetworkCmProxyDataService.addListNodeElements(cmHandle, parentNodeXpath, jsonData)
        and: 'response status indicates success'
            response.status == HttpStatus.CREATED.value()
    }

    def 'Update data node leaves.'() {
        given: 'json data'
            def jsonData = 'json data'
        and: 'the query endpoint'
            def endpoint = "$deprecatedDataNodeBaseEndPoint/cm-handles/$cmHandle/nodes"
        when: 'patch request is performed'
            def response = mvc.perform(
                    patch(endpoint)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonData)
                            .param('xpath', xpath)
            ).andReturn().response
        then: 'the service method is invoked once with expected parameters'
            1 * mockNetworkCmProxyDataService.updateNodeLeaves(cmHandle, xpath, jsonData)
        and: 'response status indicates success'
            response.status == HttpStatus.OK.value()
    }

    def 'Replace data node tree.'() {
        given: 'json data'
            def jsonData = 'json data'
        and: 'the query endpoint'
            def endpoint = "$deprecatedDataNodeBaseEndPoint/cm-handles/$cmHandle/nodes"
        when: 'put request is performed'
            def response = mvc.perform(
                    put(endpoint)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonData)
                            .param('xpath', xpath)
            ).andReturn().response
        then: 'the service method is invoked once with expected parameters'
            1 * mockNetworkCmProxyDataService.replaceNodeTree(cmHandle, xpath, jsonData)
        and: 'response status indicates success'
            response.status == HttpStatus.OK.value()
    }

    def 'Get data node.'() {
        given: 'the service returns a data node'
            def xpath = 'some xpath'
            def dataNode = new DataNodeBuilder().withXpath(xpath).withLeaves(["leaf": "value"]).build()
            mockNetworkCmProxyDataService.getDataNode(cmHandle, xpath, OMIT_DESCENDANTS) >> dataNode
        and: 'the query endpoint'
            def endpoint = "$deprecatedDataNodeBaseEndPoint/cm-handles/$cmHandle/node"
        when: 'get request is performed through REST API'
            def response = mvc.perform(get(endpoint).param('xpath', xpath)).andReturn().response
        then: 'a success response is returned'
            response.status == HttpStatus.OK.value()
        and: 'response contains expected leaf and value'
            response.contentAsString.contains('"leaf":"value"')
    }

    def 'Register CM Handle Event' () {
        given: 'jsonData'
            def jsonData = TestUtils.getResourceFileContent('dmi-registration.json')
        when: 'post request is performed'
            def response = mvc.perform(
                post("$ncmpDmiEndpoint/ch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonData)
            ).andReturn().response
        then: 'the cm handles are registered with the service'
            1 * mockNetworkCmProxyDataService.updateDmiPluginRegistration(_)
        and: 'response status is created'
            response.status == HttpStatus.CREATED.value()
    }

    def 'Get Resource Data from pass-through operational.' () {
        given: 'resource data url'
            def getUrl = "$basePath/v1/ch/testCmHandle/data/ds/ncmp-datastore:passthrough-operational" +
                    "/testResourceIdentifier?fields=testFields&depth=5"
        when: 'get data resource request is performed'
            def response = mvc.perform(
                    get(getUrl)
                            .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON_VALUE)
            ).andReturn().response
        then: 'the NCMP data service is called with getResourceDataOperationalFoCmHandle'
            1 * mockNetworkCmProxyDataService.getResourceDataOperationalFoCmHandle('testCmHandle',
                    'testResourceIdentifier',
                    'application/json',
                    'testFields',
                    5)
        and: 'response status is Ok'
            response.status == HttpStatus.OK.value()
    }

}

