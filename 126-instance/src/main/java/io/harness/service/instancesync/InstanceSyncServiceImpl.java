/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesync;

import static io.harness.instancesyncmonitoring.service.InstanceSyncMonitoringServiceImpl.FAILED_STATUS;
import static io.harness.instancesyncmonitoring.service.InstanceSyncMonitoringServiceImpl.SUCCESS_STATUS;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.instancesync.InstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.dtos.DeploymentSummaryDTO;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.InstanceDTO.InstanceDTOBuilder;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instancesyncperpetualtaskinfo.DeploymentInfoDetailsDTO;
import io.harness.dtos.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.helper.InstanceSyncHelper;
import io.harness.helper.InstanceSyncLocalCacheManager;
import io.harness.instancesyncmonitoring.model.InstanceSyncMetricDetails;
import io.harness.instancesyncmonitoring.service.InstanceSyncMonitoringService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.AutoLogContext.OverrideBehavior;
import io.harness.models.DeploymentEvent;
import io.harness.models.constants.InstanceSyncConstants;
import io.harness.models.constants.InstanceSyncFlow;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.remote.client.RestClientUtils;
import io.harness.service.deploymentsummary.DeploymentSummaryService;
import io.harness.service.infrastructuremapping.InfrastructureMappingService;
import io.harness.service.instance.InstanceService;
import io.harness.service.instancesynchandler.AbstractInstanceSyncHandler;
import io.harness.service.instancesynchandlerfactory.InstanceSyncHandlerFactoryService;
import io.harness.service.instancesyncperpetualtask.InstanceSyncPerpetualTaskService;
import io.harness.service.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoService;
import io.harness.util.logging.InstanceSyncLogContext;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.DX)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class InstanceSyncServiceImpl implements InstanceSyncService {
  private PersistentLocker persistentLocker;
  private InstanceSyncPerpetualTaskService instanceSyncPerpetualTaskService;
  private InstanceSyncPerpetualTaskInfoService instanceSyncPerpetualTaskInfoService;
  private InstanceSyncHandlerFactoryService instanceSyncHandlerFactoryService;
  private InfrastructureMappingService infrastructureMappingService;
  private InstanceService instanceService;
  private DeploymentSummaryService deploymentSummaryService;
  private InstanceSyncHelper instanceSyncHelper;
  private InstanceSyncServiceUtils utils;
  private InstanceSyncMonitoringService instanceSyncMonitoringService;
  private AccountClient accountClient;
  private static final int NEW_DEPLOYMENT_EVENT_RETRY = 3;
  private static final long TWO_WEEKS_IN_MILLIS = (long) 14 * 24 * 60 * 60 * 1000;

  @Override
  public void processInstanceSyncForNewDeployment(DeploymentEvent deploymentEvent) {
    int retryCount = 0;
    long startTime = System.currentTimeMillis();
    DeploymentSummaryDTO deploymentSummaryDTO = deploymentEvent.getDeploymentSummaryDTO();
    InfrastructureMappingDTO infrastructureMappingDTO = deploymentSummaryDTO.getInfrastructureMapping();
    log.info("processInstanceSyncForNewDeployment, deploymentEvent: {}", deploymentEvent);
    try (AutoLogContext ignore1 =
             new AccountLogContext(infrastructureMappingDTO.getAccountIdentifier(), OverrideBehavior.OVERRIDE_ERROR);
         AutoLogContext ignore2 = InstanceSyncLogContext.builder()
                                      .instanceSyncFlow(InstanceSyncFlow.NEW_DEPLOYMENT.name())
                                      .infrastructureMappingId(infrastructureMappingDTO.getId())
                                      .build(OverrideBehavior.OVERRIDE_ERROR)) {
      while (retryCount < NEW_DEPLOYMENT_EVENT_RETRY) {
        try (AcquiredLock<?> acquiredLock = persistentLocker.waitToAcquireLock(
                 InstanceSyncConstants.INSTANCE_SYNC_PREFIX + deploymentSummaryDTO.getInfrastructureMappingId(),
                 InstanceSyncConstants.INSTANCE_SYNC_LOCK_TIMEOUT, InstanceSyncConstants.INSTANCE_SYNC_WAIT_TIMEOUT)) {
          AbstractInstanceSyncHandler abstractInstanceSyncHandler =
              instanceSyncHandlerFactoryService.getInstanceSyncHandler(
                  deploymentSummaryDTO.getDeploymentInfoDTO().getType(),
                  infrastructureMappingDTO.getInfrastructureKind());
          // check if existing instance sync perpetual task info record exists or not for incoming infrastructure
          // mapping
          Optional<InstanceSyncPerpetualTaskInfoDTO> instanceSyncPerpetualTaskInfoDTOOptional =
              instanceSyncPerpetualTaskInfoService.findByInfrastructureMappingId(infrastructureMappingDTO.getId());
          InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO;
          if (!instanceSyncPerpetualTaskInfoDTOOptional.isPresent()) {
            log.info(
                "processInstanceSyncForNewDeployment, perpetual task does not exist for infrastructure mapping id: {} ",
                infrastructureMappingDTO.getId());
            // no existing perpetual task info record found for given infrastructure mapping id
            // so create a new perpetual task and instance sync perpetual task info record
            String perpetualTaskId = instanceSyncPerpetualTaskService.createPerpetualTask(infrastructureMappingDTO,
                abstractInstanceSyncHandler, Collections.singletonList(deploymentSummaryDTO.getDeploymentInfoDTO()),
                deploymentEvent.getInfrastructureOutcome());
            instanceSyncPerpetualTaskInfoDTO = instanceSyncPerpetualTaskInfoService.save(
                prepareInstanceSyncPerpetualTaskInfoDTO(deploymentSummaryDTO, perpetualTaskId));
            log.info("processInstanceSyncForNewDeployment, instanceSyncPerpetualTaskInfoDTO: {}",
                instanceSyncPerpetualTaskInfoDTO);
          } else {
            log.info(
                "processInstanceSyncForNewDeployment, perpetual task does exists for infrastructure mapping id: {} ",
                infrastructureMappingDTO.getId());
            instanceSyncPerpetualTaskInfoDTO = instanceSyncPerpetualTaskInfoDTOOptional.get();
            log.info("processInstanceSyncForNewDeployment, instanceSyncPerpetualTaskInfoDTO: {}",
                instanceSyncPerpetualTaskInfoDTO);
            if (isNewDeploymentInfo(deploymentSummaryDTO.getDeploymentInfoDTO(),
                    instanceSyncPerpetualTaskInfoDTO.getDeploymentInfoDetailsDTOList())) {
              log.info("processInstanceSyncForNewDeployment, deployment info doesn't exist in the perpetual task info");
              // it means deployment info doesn't exist in the perpetual task info
              // add the deploymentinfo and deployment summary id to the instance sync pt info record
              addNewDeploymentInfoToInstanceSyncPerpetualTaskInfoRecord(
                  instanceSyncPerpetualTaskInfoDTO, deploymentSummaryDTO);
              instanceSyncPerpetualTaskInfoService.updateDeploymentInfoDetailsList(instanceSyncPerpetualTaskInfoDTO);
              // Reset perpetual task to update the execution bundle with latest information
              instanceSyncPerpetualTaskService.resetPerpetualTask(infrastructureMappingDTO.getAccountIdentifier(),
                  instanceSyncPerpetualTaskInfoDTO.getPerpetualTaskId(), infrastructureMappingDTO,
                  abstractInstanceSyncHandler,
                  getDeploymentInfoDTOListFromInstanceSyncPerpetualTaskInfo(instanceSyncPerpetualTaskInfoDTO),
                  deploymentEvent.getInfrastructureOutcome());
            }
          }

          InstanceSyncLocalCacheManager.setDeploymentSummary(
              deploymentSummaryDTO.getInstanceSyncKey(), deploymentSummaryDTO);

          if (RestClientUtils.getResponse(accountClient.isFeatureFlagEnabled(
                  FeatureName.FIX_CORRUPTED_INSTANCES.name(), infrastructureMappingDTO.getAccountIdentifier()))) {
            fixCorruptedInstances(infrastructureMappingDTO);
          }

          // Sync only for deployment infos / instance sync handler keys from instances from server
          performInstanceSync(instanceSyncPerpetualTaskInfoDTO, infrastructureMappingDTO,
              deploymentSummaryDTO.getServerInstanceInfoList(), abstractInstanceSyncHandler, true);

          InstanceSyncMetricDetails metricDetails = buildMetricDetails(
              infrastructureMappingDTO, deploymentSummaryDTO.getDeploymentInfoDTO().getType(), SUCCESS_STATUS);
          instanceSyncMonitoringService.recordMetrics(metricDetails, true, System.currentTimeMillis() - startTime);

          log.info("Instance sync completed for infrastructure mapping id : {}", infrastructureMappingDTO.getId());
          return;
        } catch (Exception exception) {
          log.error("Attempt {} : Exception occurred during instance sync", retryCount + 1, exception);
          retryCount += 1;
        }
      }
      InstanceSyncLocalCacheManager.removeDeploymentSummary(deploymentSummaryDTO.getInstanceSyncKey());
      InstanceSyncMetricDetails metricDetails = buildMetricDetails(
          infrastructureMappingDTO, deploymentSummaryDTO.getDeploymentInfoDTO().getType(), FAILED_STATUS);
      instanceSyncMonitoringService.recordMetrics(metricDetails, true, System.currentTimeMillis() - startTime);
      log.error("Instance sync failed after all retry attempts for deployment event : {}", deploymentEvent);
    }
  }

  @Override
  public void processInstanceSyncByPerpetualTask(String accountIdentifier, String perpetualTaskId,
      InstanceSyncPerpetualTaskResponse instanceSyncPerpetualTaskResponse) {
    long startTime = System.currentTimeMillis();
    String instanceSyncStatus = "";
    log.info("processInstanceSyncByPerpetualTask, accountIdentifier: {}", accountIdentifier);
    log.info("processInstanceSyncByPerpetualTask, perpetualTaskId: {}", perpetualTaskId);
    log.info(
        "processInstanceSyncByPerpetualTask, instanceSyncPerpetualTaskResponse: {}", instanceSyncPerpetualTaskResponse);
    try (AutoLogContext ignore1 = new AccountLogContext(accountIdentifier, OverrideBehavior.OVERRIDE_ERROR);
         AutoLogContext ignore2 = InstanceSyncLogContext.builder()
                                      .instanceSyncFlow(InstanceSyncFlow.PERPETUAL_TASK_FLOW.name())
                                      .perpetualTaskId(perpetualTaskId)
                                      .build(OverrideBehavior.OVERRIDE_ERROR)) {
      if (instanceSyncPerpetualTaskResponse.getServerInstanceDetails() == null) {
        log.error("server instances details cannot be null");
        return;
      }

      logServerInstances(instanceSyncPerpetualTaskResponse.getServerInstanceDetails());
      Optional<InstanceSyncPerpetualTaskInfoDTO> instanceSyncPerpetualTaskInfoDTOOptional =
          instanceSyncPerpetualTaskInfoService.findByPerpetualTaskId(accountIdentifier, perpetualTaskId);
      if (!instanceSyncPerpetualTaskInfoDTOOptional.isPresent()) {
        log.error("No instance sync perpetual task info record found for perpetual task id : {}", perpetualTaskId);
        instanceSyncPerpetualTaskService.deletePerpetualTask(accountIdentifier, perpetualTaskId);
        return;
      }

      InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO =
          instanceSyncPerpetualTaskInfoDTOOptional.get();
      log.info(
          "processInstanceSyncByPerpetualTask, instanceSyncPerpetualTaskInfoDTO: {}", instanceSyncPerpetualTaskInfoDTO);
      try (AutoLogContext ignore3 =
               InstanceSyncLogContext.builder()
                   .infrastructureMappingId(instanceSyncPerpetualTaskInfoDTO.getInfrastructureMappingId())
                   .build(OverrideBehavior.OVERRIDE_ERROR);) {
        Optional<InfrastructureMappingDTO> infrastructureMappingDTO =
            infrastructureMappingService.getByInfrastructureMappingId(
                instanceSyncPerpetualTaskInfoDTO.getInfrastructureMappingId());
        if (!infrastructureMappingDTO.isPresent()) {
          log.error("No infrastructure mapping found for infrastructure mapping id : {}",
              instanceSyncPerpetualTaskInfoDTO.getInfrastructureMappingId());
          // delete perpetual task as well as instance sync perpetual task info record
          instanceSyncHelper.cleanUpInstanceSyncPerpetualTaskInfo(instanceSyncPerpetualTaskInfoDTO);
          return;
        }

        try (
            AcquiredLock<?> acquiredLock = persistentLocker.waitToAcquireLock(InstanceSyncConstants.INSTANCE_SYNC_PREFIX
                    + instanceSyncPerpetualTaskInfoDTO.getInfrastructureMappingId(),
                InstanceSyncConstants.INSTANCE_SYNC_LOCK_TIMEOUT, InstanceSyncConstants.INSTANCE_SYNC_WAIT_TIMEOUT)) {
          AbstractInstanceSyncHandler instanceSyncHandler = instanceSyncHandlerFactoryService.getInstanceSyncHandler(
              instanceSyncPerpetualTaskResponse.getDeploymentType(),
              infrastructureMappingDTO.get().getInfrastructureKind());
          performInstanceSync(instanceSyncPerpetualTaskInfoDTO, infrastructureMappingDTO.get(),
              instanceSyncPerpetualTaskResponse.getServerInstanceDetails(), instanceSyncHandler, false);
          instanceSyncStatus = SUCCESS_STATUS;
          log.info("Instance Sync completed");
        } catch (Exception exception) {
          instanceSyncStatus = FAILED_STATUS;
          log.error("Exception occured during instance sync", exception);
        } finally {
          InstanceSyncMetricDetails metricDetails = buildMetricDetails(infrastructureMappingDTO.get(),
              instanceSyncPerpetualTaskResponse.getDeploymentType(), instanceSyncStatus);
          instanceSyncMonitoringService.recordMetrics(metricDetails, false, System.currentTimeMillis() - startTime);
        }
      }
    }
  }

  // ------------------------------- PRIVATE METHODS --------------------------------------

  /**
   * @param serverInstanceInfoList details of all instances present in current state of server
   */
  private void performInstanceSync(InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO,
      InfrastructureMappingDTO infrastructureMappingDTO, List<ServerInstanceInfo> serverInstanceInfoList,
      AbstractInstanceSyncHandler instanceSyncHandler, boolean isNewDeploymentSync) {
    // Prepare final list of instances to be added / deleted / updated
    Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified =
        handleInstanceSync(instanceSyncPerpetualTaskInfoDTO, infrastructureMappingDTO, serverInstanceInfoList,
            instanceSyncHandler, isNewDeploymentSync);
    utils.processInstances(instancesToBeModified);
  }

  /**
   * This method will process instances from DB and instances from server and return final list of
   * instances to be added / deleted / updated
   * Also, update deployment info status in instance sync perpetual task info based on instances from server
   */
  private Map<OperationsOnInstances, List<InstanceDTO>> handleInstanceSync(
      InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO,
      InfrastructureMappingDTO infrastructureMappingDTO, List<ServerInstanceInfo> serverInstanceInfoList,
      AbstractInstanceSyncHandler instanceSyncHandler, boolean isNewDeploymentSync) {
    serverInstanceInfoList.forEach(
        serverInstanceInfo -> log.info("handleInstanceSync, serverInstanceInfo: {}", serverInstanceInfo));
    log.info("handleInstanceSync, InstanceSyncPerpetualTaskInfoDTO: {}", instanceSyncPerpetualTaskInfoDTO);
    log.info("handleInstanceSync, InfrastructureMappingDTO: {}", infrastructureMappingDTO);
    log.info("handleInstanceSync, isNewDeploymentSync: {}", isNewDeploymentSync);
    List<InstanceDTO> instancesInDB = instanceService.getActiveInstancesByInfrastructureMappingId(
        infrastructureMappingDTO.getAccountIdentifier(), infrastructureMappingDTO.getOrgIdentifier(),
        infrastructureMappingDTO.getProjectIdentifier(), infrastructureMappingDTO.getId());

    instancesInDB.forEach(instance -> log.info("handleInstanceSync, Instance in DB: {}", instance));

    List<InstanceInfoDTO> instanceInfosFromServer =
        instanceSyncHandler.getInstanceDetailsFromServerInstances(serverInstanceInfoList);

    instanceInfosFromServer.forEach(
        instanceInfo -> log.info("handleInstanceSync, Instance from Server: {}", instanceInfo));

    // map all instances and server instances infos to instance sync handler key (corresponding to deployment info)
    // basically trying to group instances corresponding to a "cluster" together
    Map<String, List<InstanceDTO>> syncKeyToInstancesInDBMap =
        utils.getSyncKeyToInstances(instanceSyncHandler, instancesInDB);
    Map<String, List<InstanceInfoDTO>> syncKeyToInstanceInfoFromServerMap =
        utils.getSyncKeyToInstancesFromServerMap(instanceSyncHandler, instanceInfosFromServer);
    Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified =
        utils.initMapForTrackingFinalListOfInstances();

    syncKeyToInstancesInDBMap.forEach(
        (key, value) -> log.info("handleInstanceSync, syncKey: {}, InstanceInDBMap: {}", key, value));
    syncKeyToInstanceInfoFromServerMap.forEach(
        (key, value) -> log.info("handleInstanceSync, syncKey: {}, InstanceInfoFromServerMap: {}", key, value));

    processInstanceSyncForSyncKeysFromServerInstances(instanceSyncHandler, infrastructureMappingDTO,
        instanceSyncPerpetualTaskInfoDTO, syncKeyToInstancesInDBMap, syncKeyToInstanceInfoFromServerMap,
        instancesToBeModified, isNewDeploymentSync);
    if (!isNewDeploymentSync) {
      processInstanceSyncForSyncKeysNotFromServerInstances(
          getSyncKeysNotFromServerInstances(
              syncKeyToInstancesInDBMap.keySet(), syncKeyToInstanceInfoFromServerMap.keySet()),
          syncKeyToInstancesInDBMap, instancesToBeModified);
    }

    return instancesToBeModified;
  }

  private void processInstanceSyncForSyncKeysNotFromServerInstances(Set<String> syncKeysToBeDeleted,
      Map<String, List<InstanceDTO>> syncKeyToInstancesInDBMap,
      Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified) {
    syncKeysToBeDeleted.forEach(syncKey -> {
      instancesToBeModified.get(OperationsOnInstances.DELETE).addAll(syncKeyToInstancesInDBMap.get(syncKey));
      log.info("processInstanceSyncForSyncKeysNotFromServerInstances, Instance to delete: {}",
          syncKeyToInstancesInDBMap.get(syncKey));
    });
  }

  private void processInstanceSyncForSyncKeysFromServerInstances(AbstractInstanceSyncHandler instanceSyncHandler,
      InfrastructureMappingDTO infrastructureMappingDTO,
      InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO,
      Map<String, List<InstanceDTO>> syncKeyToInstancesInDBMap,
      Map<String, List<InstanceInfoDTO>> syncKeyToInstanceInfoFromServerMap,
      Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified, boolean isNewDeploymentSync) {
    Set<String> instanceSyncHandlerKeys = syncKeyToInstanceInfoFromServerMap.keySet();
    instanceSyncHandlerKeys.forEach(instanceSyncHandlerKey
        -> processInstancesByInstanceSyncHandlerKey(instanceSyncHandler, infrastructureMappingDTO,
            syncKeyToInstancesInDBMap.getOrDefault(instanceSyncHandlerKey, new ArrayList<>()),
            syncKeyToInstanceInfoFromServerMap.getOrDefault(instanceSyncHandlerKey, new ArrayList<>()),
            instancesToBeModified, instanceSyncHandlerKey, isNewDeploymentSync));

    // Update the deployment info details for all deployment infos for which we received instances from server
    // This is to track deployment infos for which we are not getting instances from server (probably now not in use)
    updateDeploymentInfoDetails(instanceSyncHandler, instanceSyncPerpetualTaskInfoDTO, instanceSyncHandlerKeys);
  }

  void processInstancesByInstanceSyncHandlerKey(AbstractInstanceSyncHandler instanceSyncHandler,
      InfrastructureMappingDTO infrastructureMappingDTO, List<InstanceDTO> instancesInDB,
      List<InstanceInfoDTO> instanceInfosFromServer,
      Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified, String instanceSyncKey,
      boolean isNewDeploymentSync) {
    // Now, map all instances by instance key and find out instances to be deleted/added/updated
    Map<String, InstanceDTO> instancesInDBMap = new HashMap<>();
    Map<String, InstanceInfoDTO> instanceInfosFromServerMap = new HashMap<>();
    instancesInDB.forEach(instanceDTO
        -> instancesInDBMap.put(instanceSyncHandler.getInstanceKey(instanceDTO.getInstanceInfoDTO()), instanceDTO));
    instanceInfosFromServer.forEach(instanceInfoDTO
        -> instanceInfosFromServerMap.put(instanceSyncHandler.getInstanceKey(instanceInfoDTO), instanceInfoDTO));

    prepareInstancesToBeDeleted(instancesToBeModified, instancesInDBMap, instanceInfosFromServerMap);
    prepareInstancesToBeAdded(instanceSyncHandler, infrastructureMappingDTO, instancesInDB, instanceSyncKey,
        instancesToBeModified, instancesInDBMap, instanceInfosFromServerMap, !isNewDeploymentSync);
    prepareInstancesToBeUpdated(instanceSyncHandler, infrastructureMappingDTO, instancesInDBMap,
        instanceInfosFromServerMap, instancesToBeModified, instanceSyncKey, isNewDeploymentSync);
  }

  private void prepareInstancesToBeDeleted(Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified,
      Map<String, InstanceDTO> instancesInDBMap, Map<String, InstanceInfoDTO> instanceInfosFromServerMap) {
    Sets.SetView<String> instancesToBeDeleted =
        Sets.difference(instancesInDBMap.keySet(), instanceInfosFromServerMap.keySet());

    log.info("prepareInstancesToBeDeleted, instanceInfosFromServerMap: {}", instanceInfosFromServerMap.keySet());
    log.info("prepareInstancesToBeDeleted, instancesInDBMap: {}", instancesInDBMap.keySet());
    log.info("prepareInstancesToBeDeleted, Instances to be deleted: {}", instancesToBeDeleted);

    // Add instances to be deleted to the global map
    instancesToBeModified.get(OperationsOnInstances.DELETE)
        .addAll(instancesToBeDeleted.stream().map(instancesInDBMap::get).collect(Collectors.toSet()));
  }

  private void prepareInstancesToBeAdded(AbstractInstanceSyncHandler instanceSyncHandler,
      InfrastructureMappingDTO infrastructureMappingDTO, List<InstanceDTO> instancesInDB, String instanceSyncKey,
      Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified, Map<String, InstanceDTO> instancesInDBMap,
      Map<String, InstanceInfoDTO> instanceInfosFromServerMap, boolean isAutoScaled) {
    Sets.SetView<String> instancesToBeAdded =
        Sets.difference(instanceInfosFromServerMap.keySet(), instancesInDBMap.keySet());

    log.info("prepareInstancesToBeAdded, instanceInfosFromServerMap: {}", instanceInfosFromServerMap.keySet());
    log.info("prepareInstancesToBeAdded, instancesInDBMap: {}", instancesInDBMap.keySet());
    log.info("prepareInstancesToBeAdded, Instances to be added: {}", instancesToBeAdded);

    DeploymentSummaryDTO deploymentSummaryDTO =
        getDeploymentSummary(instanceSyncKey, infrastructureMappingDTO, isAutoScaled, instancesInDB);
    instancesToBeModified.get(OperationsOnInstances.ADD)
        .addAll(buildInstances(instanceSyncHandler,
            instancesToBeAdded.stream().map(instanceInfosFromServerMap::get).collect(Collectors.toList()),
            deploymentSummaryDTO, infrastructureMappingDTO, isAutoScaled));
  }

  private void prepareInstancesToBeUpdated(AbstractInstanceSyncHandler instanceSyncHandler,
      InfrastructureMappingDTO infrastructureMappingDTO, Map<String, InstanceDTO> instancesInDBMap,
      Map<String, InstanceInfoDTO> instanceInfosFromServerMap,
      Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified, String instanceSyncKey,
      boolean isNewDeploymentSync) {
    Sets.SetView<String> instancesToBeUpdated =
        Sets.intersection(instanceInfosFromServerMap.keySet(), instancesInDBMap.keySet());

    log.info("prepareInstancesToBeUpdated, instanceInfosFromServerMap: {}", instanceInfosFromServerMap.keySet());
    log.info("prepareInstancesToBeUpdated, instancesInDBMap: {}", instancesInDBMap.keySet());
    log.info("prepareInstancesToBeUpdated, Instances to be updated: {}", instancesToBeUpdated);

    // updating deployedAt field in accordance with pipeline execution time
    if (isNewDeploymentSync) {
      DeploymentSummaryDTO deploymentSummaryFromDB =
          getDeploymentSummaryFromDB(instanceSyncKey, infrastructureMappingDTO);
      instancesToBeUpdated.forEach(instanceKey -> {
        InstanceDTO instanceDTO = instancesInDBMap.get(instanceKey);
        instanceDTO.setLastDeployedAt(deploymentSummaryFromDB.getDeployedAt());
        instanceDTO.setLastDeployedById(deploymentSummaryFromDB.getDeployedById());
        instanceDTO.setLastPipelineExecutionId(deploymentSummaryFromDB.getPipelineExecutionId());
        instanceDTO.setPrimaryArtifact(deploymentSummaryFromDB.getArtifactDetails());
        instanceDTO.setLastDeployedByName(deploymentSummaryFromDB.getDeployedByName());
        instanceDTO.setLastPipelineExecutionName(deploymentSummaryFromDB.getPipelineExecutionName());
        // known corner limitation for optimisations: We don't update Service name and environment name in case it is
        // updated.
      });
    }

    instancesToBeUpdated.forEach(instanceKey
        -> instancesToBeModified.get(OperationsOnInstances.UPDATE)
               .add(instanceSyncHandler.updateInstance(
                   instancesInDBMap.get(instanceKey), instanceInfosFromServerMap.get(instanceKey))));
  }

  // Update instance sync perpetual task info record with updated deployment info details list
  private void updateDeploymentInfoDetails(AbstractInstanceSyncHandler instanceSyncHandler,
      InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO, Set<String> processedInstanceSyncHandlerKeys) {
    List<DeploymentInfoDetailsDTO> updatedDeploymentInfoDetailsDTOList = new ArrayList<>();
    instanceSyncPerpetualTaskInfoDTO.getDeploymentInfoDetailsDTOList().forEach(deploymentInfoDetailsDTO -> {
      if (processedInstanceSyncHandlerKeys.contains(
              instanceSyncHandler.getInstanceSyncHandlerKey(deploymentInfoDetailsDTO.getDeploymentInfoDTO()))) {
        // We got instances from server for given deployment info, thus we mark it to denote its active
        deploymentInfoDetailsDTO.setLastUsedAt(System.currentTimeMillis());
        updatedDeploymentInfoDetailsDTOList.add(deploymentInfoDetailsDTO);
      } else {
        // Check if last time we received instances from server for given deployment info in last 2 weeks
        // If yes, then we will track if further, otherwise not
        if (System.currentTimeMillis() - deploymentInfoDetailsDTO.getLastUsedAt() < TWO_WEEKS_IN_MILLIS) {
          updatedDeploymentInfoDetailsDTOList.add(deploymentInfoDetailsDTO);
        }
      }
    });

    instanceSyncPerpetualTaskInfoDTO.setDeploymentInfoDetailsDTOList(updatedDeploymentInfoDetailsDTOList);
    if (updatedDeploymentInfoDetailsDTOList.isEmpty()) {
      // There is no deployment info left to process for instance sync
      instanceSyncHelper.cleanUpInstanceSyncPerpetualTaskInfo(instanceSyncPerpetualTaskInfoDTO);
      log.info(
          "Deleted instance sync perpetual task info : {} and corresponding perpetual task as there is no deployment info to do instance sync",
          instanceSyncPerpetualTaskInfoDTO);
    } else {
      // Update new updated deployment info details list to the instance sync perpetual task info record
      instanceSyncPerpetualTaskInfoService.updateDeploymentInfoDetailsList(instanceSyncPerpetualTaskInfoDTO);
    }
  }

  private InstanceSyncPerpetualTaskInfoDTO prepareInstanceSyncPerpetualTaskInfoDTO(
      DeploymentSummaryDTO deploymentSummaryDTO, String perpetualTaskId) {
    InfrastructureMappingDTO infrastructureMappingDTO = deploymentSummaryDTO.getInfrastructureMapping();
    return InstanceSyncPerpetualTaskInfoDTO.builder()
        .accountIdentifier(infrastructureMappingDTO.getAccountIdentifier())
        .infrastructureMappingId(deploymentSummaryDTO.getInfrastructureMappingId())
        .deploymentInfoDetailsDTOList(
            Collections.singletonList(DeploymentInfoDetailsDTO.builder()
                                          .deploymentInfoDTO(deploymentSummaryDTO.getDeploymentInfoDTO())
                                          .lastUsedAt(System.currentTimeMillis())
                                          .build()))
        .perpetualTaskId(perpetualTaskId)
        .build();
  }

  // Check if the incoming new deployment info is already part of instance sync
  private boolean isNewDeploymentInfo(
      DeploymentInfoDTO newDeploymentInfoDTO, List<DeploymentInfoDetailsDTO> deploymentInfoDetailsDTOList) {
    List<DeploymentInfoDTO> existingDeploymentInfoDTOList = getDeploymentInfoDTOList(deploymentInfoDetailsDTOList);
    return !existingDeploymentInfoDTOList.contains(newDeploymentInfoDTO);
  }

  private DeploymentSummaryDTO generateDeploymentSummaryFromExistingInstance(InstanceDTO instanceDTO) {
    return DeploymentSummaryDTO.builder()
        .accountIdentifier(instanceDTO.getAccountIdentifier())
        .orgIdentifier(instanceDTO.getOrgIdentifier())
        .projectIdentifier(instanceDTO.getProjectIdentifier())
        .infrastructureMappingId(instanceDTO.getInfrastructureMappingId())
        .deployedByName(instanceDTO.getLastDeployedByName())
        .deployedById(instanceDTO.getLastDeployedById())
        .pipelineExecutionName(instanceDTO.getLastPipelineExecutionName())
        .pipelineExecutionId(instanceDTO.getLastPipelineExecutionId())
        .artifactDetails(instanceDTO.getPrimaryArtifact())
        .deployedAt(instanceDTO.getLastDeployedAt())
        .infrastructureIdentifier(instanceDTO.getInfraIdentifier())
        .infrastructureName(instanceDTO.getInfraName())
        .build();
  }

  private List<DeploymentInfoDTO> getDeploymentInfoDTOList(
      List<DeploymentInfoDetailsDTO> deploymentInfoDetailsDTOList) {
    List<DeploymentInfoDTO> deploymentInfoDTOList = new ArrayList<>();
    deploymentInfoDetailsDTOList.forEach(
        deploymentInfoDetailsDTO -> deploymentInfoDTOList.add(deploymentInfoDetailsDTO.getDeploymentInfoDTO()));
    return deploymentInfoDTOList;
  }

  private void addNewDeploymentInfoToInstanceSyncPerpetualTaskInfoRecord(
      InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO, DeploymentSummaryDTO deploymentSummaryDTO) {
    instanceSyncPerpetualTaskInfoDTO.getDeploymentInfoDetailsDTOList().add(
        DeploymentInfoDetailsDTO.builder()
            .deploymentInfoDTO(deploymentSummaryDTO.getDeploymentInfoDTO())
            .lastUsedAt(System.currentTimeMillis())
            .build());
  }

  private List<InstanceDTO> buildInstances(AbstractInstanceSyncHandler abstractInstanceSyncHandler,
      List<InstanceInfoDTO> instanceInfoDTOList, DeploymentSummaryDTO deploymentSummaryDTO,
      InfrastructureMappingDTO infrastructureMappingDTO, boolean isAutocaled) {
    ServiceEntity serviceEntity = instanceSyncHelper.fetchService(infrastructureMappingDTO);
    Environment environment = instanceSyncHelper.fetchEnvironment(infrastructureMappingDTO);
    List<InstanceDTO> instanceDTOList = new ArrayList<>();
    instanceInfoDTOList.forEach(instanceInfoDTO
        -> instanceDTOList.add(buildInstance(abstractInstanceSyncHandler, instanceInfoDTO, deploymentSummaryDTO,
            infrastructureMappingDTO, serviceEntity, environment, isAutocaled)));
    return instanceDTOList;
  }

  private InstanceDTO buildInstance(AbstractInstanceSyncHandler abstractInstanceSyncHandler,
      InstanceInfoDTO instanceInfoDTO, DeploymentSummaryDTO deploymentSummaryDTO,
      InfrastructureMappingDTO infrastructureMappingDTO, ServiceEntity serviceEntity, Environment environment,
      boolean isAutoScaled) {
    InstanceDTOBuilder instanceDTOBuilder =
        InstanceDTO.builder()
            .accountIdentifier(deploymentSummaryDTO.getAccountIdentifier())
            .orgIdentifier(deploymentSummaryDTO.getOrgIdentifier())
            .envIdentifier(environment.getIdentifier())
            .envType(environment.getType())
            .envName(environment.getName())
            .serviceName(serviceEntity.getName())
            .serviceIdentifier(serviceEntity.getIdentifier())
            .projectIdentifier(deploymentSummaryDTO.getProjectIdentifier())
            .infrastructureMappingId(infrastructureMappingDTO.getId())
            .instanceType(abstractInstanceSyncHandler.getInstanceType())
            .instanceKey(abstractInstanceSyncHandler.getInstanceKey(instanceInfoDTO))
            .primaryArtifact(deploymentSummaryDTO.getArtifactDetails())
            .infrastructureKind(infrastructureMappingDTO.getInfrastructureKind())
            .connectorRef(infrastructureMappingDTO.getConnectorRef())
            .lastPipelineExecutionName(deploymentSummaryDTO.getPipelineExecutionName())
            .lastDeployedByName(deploymentSummaryDTO.getDeployedByName())
            .lastPipelineExecutionId(deploymentSummaryDTO.getPipelineExecutionId())
            .lastDeployedById(deploymentSummaryDTO.getDeployedById())
            .lastDeployedAt(deploymentSummaryDTO.getDeployedAt())
            .infraIdentifier(deploymentSummaryDTO.getInfrastructureIdentifier())
            .infraName(deploymentSummaryDTO.getInfrastructureName())
            .instanceInfoDTO(instanceInfoDTO);

    if (isAutoScaled) {
      instanceDTOBuilder.lastDeployedById(InstanceSyncConstants.AUTO_SCALED)
          .lastDeployedByName(InstanceSyncConstants.AUTO_SCALED);
    }
    return instanceDTOBuilder.build();
  }

  private DeploymentSummaryDTO getDeploymentSummary(String instanceSyncKey,
      InfrastructureMappingDTO infrastructureMappingDTO, boolean isAutoScaled, List<InstanceDTO> instancesInDB) {
    // Fur new deployment/rollback, fetch deployment summary from local cache
    // For autoscaled instances, first try to create deployment summary from present instances, otherwise fetch from DB
    // Required to put in metadata information for artifacts into the new instances to be created
    if (!isAutoScaled) {
      DeploymentSummaryDTO deploymentSummaryDTO = InstanceSyncLocalCacheManager.getDeploymentSummary(instanceSyncKey);
      if (deploymentSummaryDTO == null) {
        log.warn("Couldn't find deployment summary in local cache for new deployment / rollback case");
        return getDeploymentSummaryFromDB(instanceSyncKey, infrastructureMappingDTO);
      } else {
        return deploymentSummaryDTO;
      }
    }
    if (!instancesInDB.isEmpty()) {
      return generateDeploymentSummaryFromExistingInstance(instancesInDB.get(0));
    } else {
      return getDeploymentSummaryFromDB(instanceSyncKey, infrastructureMappingDTO);
    }
  }

  private DeploymentSummaryDTO getDeploymentSummaryFromDB(
      String instanceSyncKey, InfrastructureMappingDTO infrastructureMappingDTO) {
    Optional<DeploymentSummaryDTO> deploymentSummaryDTOOptional =
        deploymentSummaryService.getLatestByInstanceKey(instanceSyncKey, infrastructureMappingDTO);
    if (deploymentSummaryDTOOptional.isPresent()) {
      return deploymentSummaryDTOOptional.get();
    } else {
      throw new InvalidRequestException(String.format(
          "No deployment summary found for instanceSyncKey : %s , stopping instance sync", instanceSyncKey));
    }
  }

  private Sets.SetView<String> getSyncKeysNotFromServerInstances(
      Set<String> syncKeysfromDBInstances, Set<String> syncKeysFromServerInstances) {
    return Sets.difference(syncKeysfromDBInstances, syncKeysFromServerInstances);
  }

  private List<DeploymentInfoDTO> getDeploymentInfoDTOListFromInstanceSyncPerpetualTaskInfo(
      InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO) {
    List<DeploymentInfoDTO> deploymentInfoDTOList = new ArrayList<>();
    instanceSyncPerpetualTaskInfoDTO.getDeploymentInfoDetailsDTOList().forEach(
        deploymentInfoDetailsDTO -> deploymentInfoDTOList.add(deploymentInfoDetailsDTO.getDeploymentInfoDTO()));
    return deploymentInfoDTOList;
  }

  private void logServerInstances(List<ServerInstanceInfo> serverInstanceInfoList) {
    if (serverInstanceInfoList.isEmpty()) {
      return;
    }
    StringBuilder stringBuilder = new StringBuilder();
    serverInstanceInfoList.forEach(
        serverInstanceInfo -> stringBuilder.append(serverInstanceInfo.toString()).append(" :: "));
    log.info("Server Instances in the perpetual task response : {}", stringBuilder);
  }

  private InstanceSyncMetricDetails buildMetricDetails(
      InfrastructureMappingDTO infrastructureMappingDTO, String deploymentType, String status) {
    return InstanceSyncMetricDetails.builder()
        .accountId(infrastructureMappingDTO.getAccountIdentifier())
        .orgId(infrastructureMappingDTO.getOrgIdentifier())
        .projectId(infrastructureMappingDTO.getProjectIdentifier())
        .isNg(true)
        .deploymentType(deploymentType)
        .status(status)
        .build();
  }

  private void fixCorruptedInstances(InfrastructureMappingDTO infrastructureMappingDTO) {
    List<InfrastructureMappingDTO> infrastructureMappingDTOs = infrastructureMappingService.getAllByInfrastructureKey(
        infrastructureMappingDTO.getAccountIdentifier(), infrastructureMappingDTO.getInfrastructureKey());

    // remove the new/correct infrastructureMapping
    infrastructureMappingDTOs.remove(infrastructureMappingDTO);

    infrastructureMappingDTOs.forEach(oldInfrastructureMappingDTO -> {
      List<InstanceDTO> instancesInDB = instanceService.getActiveInstancesByInfrastructureMappingId(
          infrastructureMappingDTO.getAccountIdentifier(), infrastructureMappingDTO.getOrgIdentifier(),
          infrastructureMappingDTO.getProjectIdentifier(), oldInfrastructureMappingDTO.getId());

      List<String> instanceIds = instancesInDB.stream().map(InstanceDTO::getUuid).collect(Collectors.toList());
      if (!instanceIds.isEmpty()) {
        instanceService.updateInfrastructureMapping(instanceIds, infrastructureMappingDTO.getId());
        log.info("Updating instances {} from old infrastructureMappingId {} to new infrastructureMappingId {}",
            instanceIds, oldInfrastructureMappingDTO.getId(), infrastructureMappingDTO.getId());
      }
    });
  }
}
