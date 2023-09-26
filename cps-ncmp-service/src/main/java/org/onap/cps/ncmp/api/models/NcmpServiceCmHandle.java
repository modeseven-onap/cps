/*
 *  ============LICENSE_START=======================================================
 *  Copyright (C) 2021-2023 Nordix Foundation
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

package org.onap.cps.ncmp.api.models;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.onap.cps.ncmp.api.impl.inventory.CompositeState;
import org.springframework.validation.annotation.Validated;

/**
 * The NCMP Service model used for the java service API.
 * NCMP Service CmHandle.
 */
@Validated
@Getter
@Setter
@NoArgsConstructor
public class NcmpServiceCmHandle {

    private String cmHandleId;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private Map<String, String> dmiProperties = Collections.emptyMap();

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private Map<String, String> publicProperties = Collections.emptyMap();

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private CompositeState compositeState;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private String moduleSetTag;

    /**
     * NcmpServiceCmHandle copy constructor.
     *
     * @param ncmpServiceCmHandle Ncmp Service CmHandle
     */
    public NcmpServiceCmHandle(final NcmpServiceCmHandle ncmpServiceCmHandle) {
        this.cmHandleId = ncmpServiceCmHandle.getCmHandleId();
        this.dmiProperties = new LinkedHashMap<>(ncmpServiceCmHandle.getDmiProperties());
        this.publicProperties = new LinkedHashMap<>(ncmpServiceCmHandle.getPublicProperties());
        this.compositeState = ncmpServiceCmHandle.getCompositeState() != null ? new CompositeState(
                ncmpServiceCmHandle.getCompositeState()) : null;
        this.moduleSetTag = ncmpServiceCmHandle.getModuleSetTag();
    }
}
