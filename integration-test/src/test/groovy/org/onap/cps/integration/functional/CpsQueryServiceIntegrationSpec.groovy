/*
 *  ============LICENSE_START=======================================================
 *  Copyright (C) 2023 Nordix Foundation
 *  ================================================================================
 *  Licensed under the Apache License, Version 2.0 (the 'License');
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

package org.onap.cps.integration.functional

import org.onap.cps.api.CpsQueryService
import org.onap.cps.integration.base.FunctionalSpecBase
import org.onap.cps.spi.FetchDescendantsOption
import org.onap.cps.spi.exceptions.CpsPathException
import org.springframework.test.context.jdbc.Sql

import java.util.stream.Collectors

import static org.onap.cps.spi.FetchDescendantsOption.DIRECT_CHILDREN_ONLY
import static org.onap.cps.spi.FetchDescendantsOption.INCLUDE_ALL_DESCENDANTS
import static org.onap.cps.spi.FetchDescendantsOption.OMIT_DESCENDANTS
import static org.onap.cps.spi.FetchDescendantsOption.getFetchDescendantsOption

class CpsQueryServiceIntegrationSpec extends FunctionalSpecBase {

    CpsQueryService objectUnderTest

    def setup() { objectUnderTest = cpsQueryService }

    def 'Query bookstore using CPS path where #scenario.'() {
        when: 'query data nodes for bookstore container'
            def result = objectUnderTest.queryDataNodes(FUNCTIONAL_TEST_DATASPACE, BOOKSTORE_ANCHOR, cpsPath, INCLUDE_ALL_DESCENDANTS)
        then: 'the result contains expected number of nodes'
            assert result.size() == expectedResultSize
        and: 'the result contains the expected leaf values'
            result.leaves.forEach( dataNodeLeaves -> {
                expectedLeaves.forEach( (expectedLeafKey,expectedLeafValue) -> {
                    assert dataNodeLeaves[expectedLeafKey] == expectedLeafValue
                })
            })
        where:
            scenario                                      | cpsPath                                    || expectedResultSize | expectedLeaves
            'the and condition is used'                   | '//books[@lang="English" and @price=15]'   || 2                  | [lang:"English", price:15]
            'the and is used where result does not exist' | '//books[@lang="English" and @price=1000]' || 0                  | []
    }

    def 'Query for attribute by cps path of type ancestor with #scenario.'() {
        when: 'the given cps path is parsed'
            def result = objectUnderTest.queryDataNodes(FUNCTIONAL_TEST_DATASPACE, BOOKSTORE_ANCHOR, cpsPath, OMIT_DESCENDANTS)
        then: 'the xpaths of the retrieved data nodes are as expected'
            assert result.xpath.sort() == expectedXPaths.sort()
        where: 'the following data is used'
            scenario                                    | cpsPath                                               || expectedXPaths
            'multiple list-ancestors'                   | '//books/ancestor::categories'                        || ["/bookstore/categories[@code='1']", "/bookstore/categories[@code='2']", "/bookstore/categories[@code='3']"]
            'one ancestor with list value'              | '//books/ancestor::categories[@code="1"]'             || ["/bookstore/categories[@code='1']"]
            'top ancestor'                              | '//books/ancestor::bookstore'                         || ["/bookstore"]
            'list with index value in the xpath prefix' | '//categories[@code="1"]/books/ancestor::bookstore'   || ["/bookstore"]
            'ancestor with parent list'                 | '//books/ancestor::bookstore/categories'              || ["/bookstore/categories[@code='1']", "/bookstore/categories[@code='2']", "/bookstore/categories[@code='3']"]
            'ancestor with parent'                      | '//books/ancestor::bookstore/categories[@code="2"]'   || ["/bookstore/categories[@code='2']"]
            'ancestor combined with text condition'     | '//books/title[text()="Matilda"]/ancestor::bookstore' || ["/bookstore"]
            'ancestor with parent that does not exist'  | '//books/ancestor::parentDoesNoExist/categories'      || []
            'ancestor does not exist'                   | '//books/ancestor::ancestorDoesNotExist'              || []
    }

    def 'Query for attribute by cps path of type ancestor with #scenario descendants.'() {
        when: 'the given cps path is parsed'
            def result = objectUnderTest.queryDataNodes(FUNCTIONAL_TEST_DATASPACE, BOOKSTORE_ANCHOR, '//books/ancestor::bookstore', fetchDescendantsOption)
        then: 'the xpaths of the retrieved data nodes are as expected'
            assert countDataNodesInTree(result) == expectedNumberOfNodes
        where: 'the following data is used'
            scenario | fetchDescendantsOption  || expectedNumberOfNodes
            'no'     | OMIT_DESCENDANTS        || 1
            'direct' | DIRECT_CHILDREN_ONLY    || 4
            'all'    | INCLUDE_ALL_DESCENDANTS || 8
    }

    def 'Cps Path query with syntax error throws a CPS Path Exception.'() {
        when: 'trying to execute a query with a syntax (parsing) error'
            objectUnderTest.queryDataNodes(FUNCTIONAL_TEST_DATASPACE, BOOKSTORE_ANCHOR, 'cpsPath that cannot be parsed' , OMIT_DESCENDANTS)
        then: 'a cps path exception is thrown'
            thrown(CpsPathException)
    }

}
