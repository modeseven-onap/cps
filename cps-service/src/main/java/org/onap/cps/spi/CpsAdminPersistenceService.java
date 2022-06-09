/*
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2020-2022 Nordix Foundation.
 *  Modifications Copyright (C) 2020-2022 Bell Canada.
 *  Modifications Copyright (C) 2021 Pantheon.tech
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * ============LICENSE_END=========================================================
 */

package org.onap.cps.spi;

import java.util.Collection;
import org.onap.cps.spi.exceptions.AlreadyDefinedException;
import org.onap.cps.spi.model.Anchor;

/*
    Service for handling CPS admin data.
 */
public interface CpsAdminPersistenceService {

    /**
     * Create dataspace.
     *
     * @param dataspaceName dataspace name
     * @throws AlreadyDefinedException if dataspace with same name already exists
     */
    void createDataspace(String dataspaceName);

    /**
     * Delete dataspace.
     *
     * @param dataspaceName the name of the dataspace to delete
     */
    void deleteDataspace(String dataspaceName);

    /**
     * Create an Anchor.
     *
     * @param dataspaceName dataspace name
     * @param schemaSetName schema set name
     * @param anchorName    anchor name
     */
    void createAnchor(String dataspaceName, String schemaSetName, String anchorName);

    /**
     * Read all anchors associated the given schema-set in the given dataspace.
     *
     * @param dataspaceName dataspace name
     * @param schemaSetName schema-set name
     * @return a collection of anchors
     */
    Collection<Anchor> getAnchors(String dataspaceName, String schemaSetName);

    /**
     * Read all anchors in the given a dataspace.
     *
     * @param dataspaceName dataspace name
     * @return a collection of anchors
     */
    Collection<Anchor> getAnchors(String dataspaceName);

    /**
     * Query anchor names for the given module names in the provided dataspace.
     * If dataspace or one of the given module names does not exists, return with an empty collection.
     *
     * @param dataspaceName dataspace name
     * @param moduleNames a collection of module names
     * @return a collection of anchor names in the given dataspace. The schema set for each anchor must include all the
     *         given module names
     */
    Collection<Anchor> queryAnchors(String dataspaceName, Collection<String> moduleNames);

    /**
     * Get an anchor in the given dataspace using the anchor name.
     *
     * @param dataspaceName dataspace name
     * @param anchorName anchor name
     * @return an anchor
     */
    Anchor getAnchor(String dataspaceName, String anchorName);

    /**
     * Delete anchor by name in given dataspace.
     *
     * @param dataspaceName dataspace name
     * @param anchorName anchor name
     */
    void deleteAnchor(String dataspaceName, String anchorName);
}
