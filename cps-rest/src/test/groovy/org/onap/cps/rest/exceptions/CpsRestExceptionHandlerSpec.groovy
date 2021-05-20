/*
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2020 Pantheon.tech
 *  Copyright (C) 2021 Nordix Foundation
 *  Modifications Copyright (C) 2021 Bell Canada.
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

package org.onap.cps.rest.exceptions

import groovy.json.JsonSlurper
import org.modelmapper.ModelMapper
import org.onap.cps.api.CpsAdminService
import org.onap.cps.api.CpsDataService
import org.onap.cps.api.CpsModuleService
import org.onap.cps.api.CpsQueryService
import org.onap.cps.spi.exceptions.AlreadyDefinedException
import org.onap.cps.spi.exceptions.CpsException
import org.onap.cps.spi.exceptions.CpsPathException
import org.onap.cps.spi.exceptions.DataInUseException
import org.onap.cps.spi.exceptions.DataNodeNotFoundException
import org.onap.cps.spi.exceptions.DataValidationException
import org.onap.cps.spi.exceptions.ModelValidationException
import org.onap.cps.spi.exceptions.NotFoundInDataspaceException
import org.onap.cps.spi.exceptions.SchemaSetInUseException
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Shared
import spock.lang.Specification

import static org.springframework.http.HttpStatus.BAD_REQUEST
import static org.springframework.http.HttpStatus.CONFLICT
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import static org.springframework.http.HttpStatus.NOT_FOUND
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

@WebMvcTest
class CpsRestExceptionHandlerSpec extends Specification {

    @SpringBean
    CpsAdminService mockCpsAdminService = Mock()

    @SpringBean
    CpsModuleService mockCpsModuleService = Mock()

    @SpringBean
    CpsDataService mockCpsDataService = Mock()

    @SpringBean
    CpsQueryService mockCpsQueryService = Mock()

    @SpringBean
    ModelMapper modelMapper = Mock()

    @Autowired
    MockMvc mvc

    @Value('${rest.api.cps-base-path}')
    def basePath

    @Shared
    def errorMessage = 'some error message'
    @Shared
    def errorDetails = 'some error details'
    @Shared
    def dataspaceName = 'MyDataSpace'
    @Shared
    def existingObjectName = 'MyAdminObject'


    def 'Get request with runtime exception returns HTTP Status Internal Server Error'() {
        when: 'runtime exception is thrown by the service'
            setupTestException(new IllegalStateException(errorMessage))
            def response = performTestRequest()
        then: 'an HTTP Internal Server Error response is returned with correct message and details'
            assertTestResponse(response, INTERNAL_SERVER_ERROR, errorMessage, null)
    }

    def 'Get request with generic CPS exception returns HTTP Status Internal Server Error'() {
        when: 'generic CPS exception is thrown by the service'
            setupTestException(new CpsException(errorMessage, errorDetails))
            def response = performTestRequest()
        then: 'an HTTP Internal Server Error response is returned with correct message and details'
            assertTestResponse(response, INTERNAL_SERVER_ERROR, errorMessage, errorDetails)
    }

    def 'Get request with no data found CPS exception returns HTTP Status Not Found'() {
        when: 'no data found CPS exception is thrown by the service'
            def dataspaceName = 'MyDataSpace'
            def descriptionOfObject = 'Description'
            setupTestException(new NotFoundInDataspaceException(dataspaceName, descriptionOfObject))
            def response = performTestRequest()
        then: 'an HTTP Not Found response is returned with correct message and details'
            assertTestResponse(response, NOT_FOUND, 'Object not found',
                    'Description does not exist in dataspace MyDataSpace.')
    }

    def 'Request with an object already defined exception returns HTTP Status Conflict.'() {
        when: 'AlreadyDefinedException exception is thrown by the service'
            setupTestException(new AlreadyDefinedException("Anchor", existingObjectName, dataspaceName, new Throwable()))
            def response = performTestRequest()
        then: 'a HTTP conflict response is returned with correct message an details'
            assertTestResponse(response, CONFLICT,
                    "Already defined exception",
                    "Anchor with name ${existingObjectName} already exists for ${dataspaceName}.")
    }

    def 'Get request with a #exceptionThrown.class.simpleName returns HTTP Status Bad Request'() {
        when: 'CPS validation exception is thrown by the service'
            setupTestException(exceptionThrown)
            def response = performTestRequest()
        then: 'an HTTP Bad Request response is returned with correct message and details'
            assertTestResponse(response, BAD_REQUEST, expectedErrorMessage, expectedErrorDetails)
        where: 'the following exceptions are thrown'
            exceptionThrown                                                || expectedErrorMessage           | expectedErrorDetails
            new ModelValidationException(errorMessage, errorDetails, null) || errorMessage                   | errorDetails
            new DataValidationException(errorMessage, errorDetails, null)  || errorMessage                   | errorDetails
            new CpsPathException(errorDetails)                             || CpsPathException.ERROR_MESSAGE | errorDetails
    }

    def 'Delete request with a #exceptionThrown.class.simpleName returns HTTP Status Conflict'() {
        when: 'CPS validation exception is thrown by the service'
            setupTestException(exceptionThrown)
            def response = performTestRequest()
        then: 'an HTTP Conflict response is returned with correct message and details'
            assertTestResponse(response, CONFLICT, exceptionThrown.getMessage(), exceptionThrown.getDetails())
        where: 'the following exceptions are thrown'
            exceptionThrown << [new DataInUseException(dataspaceName, existingObjectName),
                                new SchemaSetInUseException(dataspaceName, existingObjectName)]
    }

    /*
     * NB. This method tests the expected behavior for POST request only;
     * testing of PUT and PATCH requests omitted due to same NOT 'GET' condition is being used.
     */
    def 'Post request with #exceptionThrown.class.simpleName returns HTTP Status Bad Request.'() {
        given: '#exception is thrown the service indicating data is not found'
            mockCpsDataService.saveData(_, _, _, _) >> { throw exceptionThrown }
        when: 'data update request is performed'
            def response = mvc.perform(
                    post("$basePath/v1/dataspaces/dataspace-name/anchors/anchor-name/nodes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .param('xpath', 'parent node xpath')
                            .content('json data')
            ).andReturn().response
        then: 'response code indicates bad input parameters'
            response.status == BAD_REQUEST.value()
        where: 'the following exceptions are thrown'
            exceptionThrown << [new DataNodeNotFoundException('', ''), new NotFoundInDataspaceException('', '')]
    }

    /*
     * NB. The test uses 'get anchors' endpoint and associated service method invocation
     * to test the exception handling. The endpoint chosen is not a subject of test.
     */

    def setupTestException(exception) {
        mockCpsAdminService.getAnchors(_) >> { throw exception }
    }

    def performTestRequest() {
        return mvc.perform(
                get("$basePath/v1/dataspaces/dataspace-name/anchors"))
                .andReturn().response
    }

    static void assertTestResponse(response, expectedStatus, expectedErrorMessage, expectedErrorDetails) {
        assert response.status == expectedStatus.value()
        def content = new JsonSlurper().parseText(response.contentAsString)
        assert content['status'] == expectedStatus.toString()
        assert content['message'] == expectedErrorMessage
        assert expectedErrorDetails == null || content['details'] == expectedErrorDetails
    }
}
