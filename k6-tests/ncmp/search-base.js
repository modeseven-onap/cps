/*
 *  ============LICENSE_START=======================================================
 *  Copyright (C) 2024 Nordix Foundation
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

import http from 'k6/http';
import { check } from 'k6';
import { NCMP_BASE_URL, TOTAL_CM_HANDLES } from './utils.js';

export function searchRequest(searchType, searchFilter) {
    const response = http.post(NCMP_BASE_URL + '/ncmp/v1/ch/' + searchType, searchFilter, {
        headers: { 'Content-Type': 'application/json' },
    });
    check(response, {
        'status equals 200': (r) => r.status === 200,
    });
    check(JSON.parse(response.body), {
        'returned list has expected CM-handles': (arr) => arr.length === TOTAL_CM_HANDLES,
    });
}
