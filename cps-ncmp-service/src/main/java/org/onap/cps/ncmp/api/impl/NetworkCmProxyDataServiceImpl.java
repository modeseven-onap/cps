/*
 *  ============LICENSE_START=======================================================
 *  Copyright (C) 2021 highstreet technologies GmbH
 *  Modifications Copyright (C) 2021-2023 Nordix Foundation
 *  Modifications Copyright (C) 2021 Pantheon.tech
 *  Modifications Copyright (C) 2021-2022 Bell Canada
 *  Modifications Copyright (C) 2023 TechMahindra Ltd.
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

package org.onap.cps.ncmp.api.impl;

import static org.onap.cps.ncmp.api.impl.constants.DmiRegistryConstants.NFP_OPERATIONAL_DATASTORE_DATASPACE_NAME;
import static org.onap.cps.ncmp.api.impl.operations.DmiRequestBody.OperationEnum;
import static org.onap.cps.ncmp.api.impl.utils.RestQueryParametersValidator.validateCmHandleQueryParameters;

import com.google.common.collect.Lists;
import com.hazelcast.map.IMap;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.onap.cps.api.CpsDataService;
import org.onap.cps.ncmp.api.NetworkCmProxyCmHandleQueryService;
import org.onap.cps.ncmp.api.NetworkCmProxyDataService;
import org.onap.cps.ncmp.api.impl.events.lcm.LcmEventsCmHandleStateHandler;
import org.onap.cps.ncmp.api.impl.operations.DmiDataOperations;
import org.onap.cps.ncmp.api.impl.operations.DmiOperations;
import org.onap.cps.ncmp.api.impl.utils.CmHandleQueryConditions;
import org.onap.cps.ncmp.api.impl.utils.InventoryQueryConditions;
import org.onap.cps.ncmp.api.impl.utils.YangDataConverter;
import org.onap.cps.ncmp.api.impl.yangmodels.YangModelCmHandle;
import org.onap.cps.ncmp.api.inventory.CmHandleQueries;
import org.onap.cps.ncmp.api.inventory.CmHandleState;
import org.onap.cps.ncmp.api.inventory.CompositeState;
import org.onap.cps.ncmp.api.inventory.CompositeStateUtils;
import org.onap.cps.ncmp.api.inventory.DataStoreSyncState;
import org.onap.cps.ncmp.api.inventory.InventoryPersistence;
import org.onap.cps.ncmp.api.models.CmHandleQueryApiParameters;
import org.onap.cps.ncmp.api.models.CmHandleQueryServiceParameters;
import org.onap.cps.ncmp.api.models.CmHandleRegistrationResponse;
import org.onap.cps.ncmp.api.models.CmHandleRegistrationResponse.RegistrationError;
import org.onap.cps.ncmp.api.models.DmiPluginRegistration;
import org.onap.cps.ncmp.api.models.DmiPluginRegistrationResponse;
import org.onap.cps.ncmp.api.models.NcmpServiceCmHandle;
import org.onap.cps.spi.FetchDescendantsOption;
import org.onap.cps.spi.exceptions.AlreadyDefinedExceptionBatch;
import org.onap.cps.spi.exceptions.CpsException;
import org.onap.cps.spi.exceptions.DataNodeNotFoundException;
import org.onap.cps.spi.exceptions.DataValidationException;
import org.onap.cps.spi.model.ModuleDefinition;
import org.onap.cps.spi.model.ModuleReference;
import org.onap.cps.utils.JsonObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkCmProxyDataServiceImpl implements NetworkCmProxyDataService {

    private static final int DELETE_BATCH_SIZE = 100;
    private final JsonObjectMapper jsonObjectMapper;
    private final DmiDataOperations dmiDataOperations;
    private final NetworkCmProxyDataServicePropertyHandler networkCmProxyDataServicePropertyHandler;
    private final InventoryPersistence inventoryPersistence;
    private final CmHandleQueries cmHandleQueries;
    private final NetworkCmProxyCmHandleQueryService networkCmProxyCmHandleQueryService;
    private final LcmEventsCmHandleStateHandler lcmEventsCmHandleStateHandler;
    private final CpsDataService cpsDataService;
    private final IMap<String, Object> moduleSyncStartedOnCmHandles;

    @Override
    public DmiPluginRegistrationResponse updateDmiRegistrationAndSyncModule(
            final DmiPluginRegistration dmiPluginRegistration) {
        dmiPluginRegistration.validateDmiPluginRegistration();
        final DmiPluginRegistrationResponse dmiPluginRegistrationResponse = new DmiPluginRegistrationResponse();

        if (!dmiPluginRegistration.getRemovedCmHandles().isEmpty()) {
            dmiPluginRegistrationResponse.setRemovedCmHandles(
                    parseAndRemoveCmHandlesInDmiRegistration(dmiPluginRegistration.getRemovedCmHandles()));
        }

        if (!dmiPluginRegistration.getCreatedCmHandles().isEmpty()) {
            dmiPluginRegistrationResponse.setCreatedCmHandles(
                    parseAndCreateCmHandlesInDmiRegistrationAndSyncModules(dmiPluginRegistration));
        }
        if (!dmiPluginRegistration.getUpdatedCmHandles().isEmpty()) {
            dmiPluginRegistrationResponse.setUpdatedCmHandles(
                    networkCmProxyDataServicePropertyHandler
                            .updateCmHandleProperties(dmiPluginRegistration.getUpdatedCmHandles()));
        }
        return dmiPluginRegistrationResponse;
    }

    @Override
    public Object getResourceDataOperationalForCmHandle(final String cmHandleId,
                                                        final String resourceIdentifier,
                                                        final String optionsParamInQuery,
                                                        final String topicParamInQuery,
                                                        final String requestId) {
        final ResponseEntity<?> responseEntity = dmiDataOperations.getResourceDataFromDmi(cmHandleId,
                resourceIdentifier,
                optionsParamInQuery,
                DmiOperations.DataStoreEnum.PASSTHROUGH_OPERATIONAL,
                requestId, topicParamInQuery);
        return responseEntity.getBody();
    }

    @Override
    public Object getResourceDataOperational(final String cmHandleId,
                                             final String resourceIdentifier,
                                             final FetchDescendantsOption fetchDescendantsOption) {
        return cpsDataService.getDataNodes(NFP_OPERATIONAL_DATASTORE_DATASPACE_NAME, cmHandleId, resourceIdentifier,
                fetchDescendantsOption).iterator().next();
    }

    @Override
    public Object getResourceDataPassThroughRunningForCmHandle(final String cmHandleId,
                                                               final String resourceIdentifier,
                                                               final String optionsParamInQuery,
                                                               final String topicParamInQuery,
                                                               final String requestId) {
        final ResponseEntity<?> responseEntity = dmiDataOperations.getResourceDataFromDmi(cmHandleId,
                resourceIdentifier,
                optionsParamInQuery,
                DmiOperations.DataStoreEnum.PASSTHROUGH_RUNNING,
                requestId, topicParamInQuery);
        return responseEntity.getBody();
    }

    @Override
    public Object writeResourceDataPassThroughRunningForCmHandle(final String cmHandleId,
                                                                 final String resourceIdentifier,
                                                                 final OperationEnum operation,
                                                                 final String requestData,
                                                                 final String dataType) {
        return dmiDataOperations.writeResourceDataPassThroughRunningFromDmi(cmHandleId, resourceIdentifier, operation,
                requestData, dataType);
    }

    @Override
    public Collection<ModuleReference> getYangResourcesModuleReferences(final String cmHandleId) {
        return inventoryPersistence.getYangResourcesModuleReferences(cmHandleId);
    }

    @Override
    public Collection<ModuleDefinition> getModuleDefinitionsByCmHandleId(final String cmHandleId) {
        return inventoryPersistence.getModuleDefinitionsByCmHandleId(cmHandleId);
    }

    /**
     * Retrieve cm handles with details for the given query parameters.
     *
     * @param cmHandleQueryApiParameters cm handle query parameters
     * @return cm handles with details
     */
    @Override
    public Collection<NcmpServiceCmHandle> executeCmHandleSearch(
            final CmHandleQueryApiParameters cmHandleQueryApiParameters) {
        final CmHandleQueryServiceParameters cmHandleQueryServiceParameters = jsonObjectMapper.convertToValueType(
                cmHandleQueryApiParameters, CmHandleQueryServiceParameters.class);
        validateCmHandleQueryParameters(cmHandleQueryServiceParameters, CmHandleQueryConditions.ALL_CONDITION_NAMES);
        return networkCmProxyCmHandleQueryService.queryCmHandles(cmHandleQueryServiceParameters);
    }

    /**
     * Retrieve cm handle ids for the given query parameters.
     *
     * @param cmHandleQueryApiParameters cm handle query parameters
     * @return cm handle ids
     */
    @Override
    public Collection<String> executeCmHandleIdSearch(final CmHandleQueryApiParameters cmHandleQueryApiParameters) {
        final CmHandleQueryServiceParameters cmHandleQueryServiceParameters = jsonObjectMapper.convertToValueType(
                cmHandleQueryApiParameters, CmHandleQueryServiceParameters.class);
        validateCmHandleQueryParameters(cmHandleQueryServiceParameters, CmHandleQueryConditions.ALL_CONDITION_NAMES);
        return networkCmProxyCmHandleQueryService.queryCmHandleIds(cmHandleQueryServiceParameters);
    }

    /**
     * Set the data sync enabled flag, along with the data sync state
     * based on the data sync enabled boolean for the cm handle id provided.
     *
     * @param cmHandleId      cm handle id
     * @param dataSyncEnabled data sync enabled flag
     */
    @Override
    public void setDataSyncEnabled(final String cmHandleId, final boolean dataSyncEnabled) {
        final CompositeState compositeState = inventoryPersistence
                .getCmHandleState(cmHandleId);
        if (compositeState.getDataSyncEnabled().equals(dataSyncEnabled)) {
            log.info("Data-Sync Enabled flag is already: {} ", dataSyncEnabled);
        } else if (compositeState.getCmHandleState() != CmHandleState.READY) {
            throw new CpsException("State mismatch exception.", "Cm-Handle not in READY state. Cm handle state is: "
                    + compositeState.getCmHandleState());
        } else {
            final DataStoreSyncState dataStoreSyncState = compositeState.getDataStores()
                    .getOperationalDataStore().getDataStoreSyncState();
            if (!dataSyncEnabled && dataStoreSyncState == DataStoreSyncState.SYNCHRONIZED) {
                cpsDataService.deleteDataNode(NFP_OPERATIONAL_DATASTORE_DATASPACE_NAME, cmHandleId,
                        "/netconf-state", OffsetDateTime.now());
            }
            CompositeStateUtils.setDataSyncEnabledFlagWithDataSyncState(dataSyncEnabled, compositeState);
            inventoryPersistence.saveCmHandleState(cmHandleId,
                    compositeState);
        }
    }

    /**
     * Get all cm handle IDs by DMI plugin identifier.
     *
     * @param dmiPluginIdentifier DMI plugin identifier
     * @return set of cm handle IDs
     */
    @Override
    public Collection<String> getAllCmHandleIdsByDmiPluginIdentifier(final String dmiPluginIdentifier) {
        return cmHandleQueries.getCmHandleIdsByDmiPluginIdentifier(dmiPluginIdentifier);
    }

    /**
     * Get all cm handle IDs by various properties.
     *
     * @param cmHandleQueryServiceParameters cm handle query parameters
     * @return set of cm handle IDs
     */
    @Override
    public Collection<String> executeCmHandleIdSearchForInventory(
            final CmHandleQueryServiceParameters cmHandleQueryServiceParameters) {
        validateCmHandleQueryParameters(cmHandleQueryServiceParameters, InventoryQueryConditions.ALL_CONDITION_NAMES);
        return networkCmProxyCmHandleQueryService.queryCmHandleIdsForInventory(cmHandleQueryServiceParameters);
    }

    /**
     * Retrieve cm handle details for a given cm handle.
     *
     * @param cmHandleId cm handle identifier
     * @return cm handle details
     */
    @Override
    public NcmpServiceCmHandle getNcmpServiceCmHandle(final String cmHandleId) {
        return YangDataConverter.convertYangModelCmHandleToNcmpServiceCmHandle(
                inventoryPersistence.getYangModelCmHandle(cmHandleId));
    }

    /**
     * Get cm handle public properties for a given cm handle id.
     *
     * @param cmHandleId cm handle identifier
     * @return cm handle public properties
     */
    @Override
    public Map<String, String> getCmHandlePublicProperties(final String cmHandleId) {
        final YangModelCmHandle yangModelCmHandle =
                inventoryPersistence.getYangModelCmHandle(cmHandleId);
        final List<YangModelCmHandle.Property> yangModelPublicProperties = yangModelCmHandle.getPublicProperties();
        final Map<String, String> cmHandlePublicProperties = new HashMap<>();
        YangDataConverter.asPropertiesMap(yangModelPublicProperties, cmHandlePublicProperties);
        return cmHandlePublicProperties;
    }

    /**
     * Get cm handle composite state for a given cm handle id.
     *
     * @param cmHandleId cm handle identifier
     * @return cm handle state
     */
    @Override
    public CompositeState getCmHandleCompositeState(final String cmHandleId) {
        return inventoryPersistence.getYangModelCmHandle(cmHandleId).getCompositeState();
    }

    /**
     * THis method registers a cm handle and initiates modules sync.
     *
     * @param dmiPluginRegistration dmi plugin registration information.
     * @return cm-handle registration response for create cm-handle requests.
     */
    public List<CmHandleRegistrationResponse> parseAndCreateCmHandlesInDmiRegistrationAndSyncModules(
            final DmiPluginRegistration dmiPluginRegistration) {
        List<CmHandleRegistrationResponse> cmHandleRegistrationResponses = new ArrayList<>();
        final Map<YangModelCmHandle, CmHandleState> cmHandleStatePerCmHandle = new HashMap<>();
        try {
            dmiPluginRegistration.getCreatedCmHandles()
                    .forEach(cmHandle -> {
                        final YangModelCmHandle yangModelCmHandle = YangModelCmHandle.toYangModelCmHandle(
                                dmiPluginRegistration.getDmiPlugin(),
                                dmiPluginRegistration.getDmiDataPlugin(),
                                dmiPluginRegistration.getDmiModelPlugin(),
                                cmHandle);
                        cmHandleStatePerCmHandle.put(yangModelCmHandle, CmHandleState.ADVISED);
                    });
            cmHandleRegistrationResponses = registerNewCmHandles(cmHandleStatePerCmHandle);
        } catch (final DataValidationException dataValidationException) {
            cmHandleRegistrationResponses.add(CmHandleRegistrationResponse.createFailureResponse(dmiPluginRegistration
                            .getCreatedCmHandles().stream()
                            .map(NcmpServiceCmHandle::getCmHandleId).findFirst().orElse(null),
                    RegistrationError.CM_HANDLE_INVALID_ID));
        }
        return cmHandleRegistrationResponses;
    }

    protected List<CmHandleRegistrationResponse> parseAndRemoveCmHandlesInDmiRegistration(
            final List<String> tobeRemovedCmHandles) {
        final List<CmHandleRegistrationResponse> cmHandleRegistrationResponses =
                new ArrayList<>(tobeRemovedCmHandles.size());
        final Collection<YangModelCmHandle> yangModelCmHandles =
            inventoryPersistence.getYangModelCmHandles(tobeRemovedCmHandles);

        updateCmHandleStateBatch(yangModelCmHandles, CmHandleState.DELETING);

        final Set<String> notDeletedCmHandles = new HashSet<>();
        for (final List<String> tobeRemovedCmHandleBatch : Lists.partition(tobeRemovedCmHandles, DELETE_BATCH_SIZE)) {
            try {
                batchDeleteCmHandlesFromDbAndModuleSyncMap(tobeRemovedCmHandleBatch);
                tobeRemovedCmHandleBatch.forEach(cmHandleId ->
                    cmHandleRegistrationResponses.add(CmHandleRegistrationResponse.createSuccessResponse(cmHandleId)));

            } catch (final RuntimeException batchException) {
                log.error("Unable to de-register cm-handle batch, retrying on each cm handle");
                for (final String cmHandleId : tobeRemovedCmHandleBatch) {
                    final CmHandleRegistrationResponse cmHandleRegistrationResponse =
                        deleteCmHandleAndGetCmHandleRegistrationResponse(cmHandleId);
                    cmHandleRegistrationResponses.add(cmHandleRegistrationResponse);
                    if (cmHandleRegistrationResponse.getStatus() != CmHandleRegistrationResponse.Status.SUCCESS) {
                        notDeletedCmHandles.add(cmHandleId);
                    }
                }
            }
        }

        yangModelCmHandles.removeIf(yangModelCmHandle -> notDeletedCmHandles.contains(yangModelCmHandle.getId()));
        updateCmHandleStateBatch(yangModelCmHandles, CmHandleState.DELETED);

        return cmHandleRegistrationResponses;
    }

    private CmHandleRegistrationResponse deleteCmHandleAndGetCmHandleRegistrationResponse(final String cmHandleId) {
        try {
            deleteCmHandleFromDbAndModuleSyncMap(cmHandleId);
            return CmHandleRegistrationResponse.createSuccessResponse(cmHandleId);
        } catch (final DataNodeNotFoundException dataNodeNotFoundException) {
            log.error("Unable to find dataNode for cmHandleId : {} , caused by : {}",
                cmHandleId, dataNodeNotFoundException.getMessage());
            return CmHandleRegistrationResponse.createFailureResponse(cmHandleId,
                RegistrationError.CM_HANDLE_DOES_NOT_EXIST);
        } catch (final DataValidationException dataValidationException) {
            log.error("Unable to de-register cm-handle id: {}, caused by: {}",
                cmHandleId, dataValidationException.getMessage());
            return CmHandleRegistrationResponse.createFailureResponse(cmHandleId,
                RegistrationError.CM_HANDLE_INVALID_ID);
        } catch (final Exception exception) {
            log.error("Unable to de-register cm-handle id : {} , caused by : {}", cmHandleId, exception.getMessage());
            return CmHandleRegistrationResponse.createFailureResponse(cmHandleId, exception);
        }
    }

    private void updateCmHandleStateBatch(final Collection<YangModelCmHandle> yangModelCmHandles,
                                          final CmHandleState cmHandleState) {
        final Map<YangModelCmHandle, CmHandleState> cmHandleStatePerCmHandle = new HashMap<>(yangModelCmHandles.size());
        yangModelCmHandles.forEach(yangModelCmHandle -> cmHandleStatePerCmHandle.put(yangModelCmHandle, cmHandleState));
        lcmEventsCmHandleStateHandler.updateCmHandleStateBatch(cmHandleStatePerCmHandle);
    }

    private void deleteCmHandleFromDbAndModuleSyncMap(final String cmHandleId) {
        inventoryPersistence.deleteSchemaSetWithCascade(cmHandleId);
        inventoryPersistence.deleteDataNode("/dmi-registry/cm-handles[@id='" + cmHandleId + "']");
        removeDeletedCmHandleFromModuleSyncMap(cmHandleId);
    }

    private void batchDeleteCmHandlesFromDbAndModuleSyncMap(final Collection<String> tobeRemovedCmHandles) {
        inventoryPersistence.deleteSchemaSetsWithCascade(tobeRemovedCmHandles);
        inventoryPersistence.deleteDataNodes(mapCmHandleIdsToXpaths(tobeRemovedCmHandles));
        tobeRemovedCmHandles.forEach(this::removeDeletedCmHandleFromModuleSyncMap);
    }

    private Collection<String> mapCmHandleIdsToXpaths(final Collection<String> cmHandles) {
        return cmHandles.stream()
            .map(cmHandleId -> "/dmi-registry/cm-handles[@id='" + cmHandleId + "']")
            .collect(Collectors.toSet());
    }

    // CPS-1239 Robustness cleaning of in progress cache
    private void removeDeletedCmHandleFromModuleSyncMap(final String deletedCmHandleId) {
        if (moduleSyncStartedOnCmHandles.remove(deletedCmHandleId) != null) {
            log.debug("{} removed from in progress map", deletedCmHandleId);
        }
    }

    private List<CmHandleRegistrationResponse> registerNewCmHandles(final Map<YangModelCmHandle, CmHandleState>
                                                                            cmHandleStatePerCmHandle) {
        final List<String> cmHandleIds = cmHandleStatePerCmHandle.keySet().stream().map(YangModelCmHandle::getId)
                .collect(Collectors.toList());
        try {
            lcmEventsCmHandleStateHandler.updateCmHandleStateBatch(cmHandleStatePerCmHandle);
            return CmHandleRegistrationResponse.createSuccessResponses(cmHandleIds);
        } catch (final AlreadyDefinedExceptionBatch alreadyDefinedExceptionBatch) {
            return CmHandleRegistrationResponse.createFailureResponses(
                    alreadyDefinedExceptionBatch.getAlreadyDefinedXpaths(),
                    RegistrationError.CM_HANDLE_ALREADY_EXIST);
        } catch (final Exception exception) {
            return CmHandleRegistrationResponse.createFailureResponses(cmHandleIds, exception);
        }
    }

}
