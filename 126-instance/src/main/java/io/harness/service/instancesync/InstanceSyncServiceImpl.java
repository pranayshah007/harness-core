/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesync;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import static java.util.Objects.isNull;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.instancesync.InstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.dtos.DeploymentSummaryDTO;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.InstanceDTO.InstanceDTOBuilder;
import io.harness.dtos.InstanceSyncPerpetualTaskMappingDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instancesyncperpetualtaskinfo.DeploymentInfoDetailsDTO;
import io.harness.dtos.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoDTO;
import io.harness.entities.InstanceSyncPerpetualTaskMappingService;
import io.harness.entities.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfo.InstanceSyncPerpetualTaskInfoKeys;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.helper.InstanceSyncHelper;
import io.harness.helper.InstanceSyncLocalCacheManager;
import io.harness.instancesyncmonitoring.service.InstanceSyncMonitoringService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.AutoLogContext.OverrideBehavior;
import io.harness.models.DeploymentEvent;
import io.harness.models.constants.InstanceSyncConstants;
import io.harness.models.constants.InstanceSyncFlow;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.perpetualtask.instancesync.DeploymentReleaseDetails;
import io.harness.perpetualtask.instancesync.InstanceSyncData;
import io.harness.perpetualtask.instancesync.InstanceSyncResponseV2;
import io.harness.perpetualtask.instancesync.InstanceSyncTaskDetails;
import io.harness.perpetualtask.instancesync.ResponseBatchConfig;
import io.harness.serializer.KryoSerializer;
import io.harness.service.deploymentsummary.DeploymentSummaryService;
import io.harness.service.infrastructuremapping.InfrastructureMappingService;
import io.harness.service.instance.InstanceService;
import io.harness.service.instancesynchandler.AbstractInstanceSyncHandler;
import io.harness.service.instancesynchandlerfactory.InstanceSyncHandlerFactoryService;
import io.harness.service.instancesyncperpetualtask.InstanceSyncPerpetualTaskService;
import io.harness.service.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoService;
import io.harness.util.logging.InstanceSyncLogContext;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@OwnedBy(HarnessTeam.DX)
@Singleton
@Slf4j
public class InstanceSyncServiceImpl implements InstanceSyncService {
  private PersistentLocker persistentLocker;
  private InstanceSyncPerpetualTaskService instanceSyncPerpetualTaskService;

  private InstanceSyncPerpetualTaskInfoService instanceSyncPerpetualTaskInfoService;
  private InstanceSyncPerpetualTaskMappingService instanceSyncPerpetualTaskMappingService;
  private InstanceSyncHandlerFactoryService instanceSyncHandlerFactoryService;
  private InfrastructureMappingService infrastructureMappingService;
  private InstanceService instanceService;
  private DeploymentSummaryService deploymentSummaryService;
  private InstanceSyncHelper instanceSyncHelper;

  @Named(DEFAULT_CONNECTOR_SERVICE) private ConnectorService connectorService;
  private InstanceSyncServiceUtils utils;
  private InstanceSyncMonitoringService instanceSyncMonitoringService;
  private AccountClient accountClient;
  private KryoSerializer kryoSerializer;
  private static final int NEW_DEPLOYMENT_EVENT_RETRY = 3;
  private static final String CONNECTOR = "connector";
  private static final int PAGE_SIZE = 100;
  private static final long TWO_WEEKS_IN_MILLIS = (long) 14 * 24 * 60 * 60 * 1000;

  private static final int INSTANCE_COUNT_LIMIT =
      Integer.parseInt(System.getenv().getOrDefault("INSTANCE_SYNC_RESPONSE_BATCH_INSTANCE_COUNT", "100"));
  private static final int RELEASE_COUNT_LIMIT =
      Integer.parseInt(System.getenv().getOrDefault("INSTANCE_SYNC_RESPONSE_BATCH_RELEASE_COUNT", "5"));

  static final long RELEASE_PRESERVE_TIME = TimeUnit.DAYS.toMillis(7);

  @Inject
  public InstanceSyncServiceImpl(PersistentLocker persistentLocker,
      InstanceSyncPerpetualTaskService instanceSyncPerpetualTaskService,
      InstanceSyncPerpetualTaskInfoService instanceSyncPerpetualTaskInfoService,
      InstanceSyncPerpetualTaskMappingService instanceSyncPerpetualTaskMappingService,
      InstanceSyncHandlerFactoryService instanceSyncHandlerFactoryService,
      InfrastructureMappingService infrastructureMappingService, InstanceService instanceService,
      DeploymentSummaryService deploymentSummaryService, InstanceSyncHelper instanceSyncHelper,
      @Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      InstanceSyncServiceUtils instanceSyncServiceUtils, InstanceSyncMonitoringService instanceSyncMonitoringService,
      AccountClient accountClient, KryoSerializer kryoSerializer) {
    this.persistentLocker = persistentLocker;
    this.instanceSyncPerpetualTaskService = instanceSyncPerpetualTaskService;
    this.instanceSyncPerpetualTaskInfoService = instanceSyncPerpetualTaskInfoService;
    this.instanceSyncPerpetualTaskMappingService = instanceSyncPerpetualTaskMappingService;
    this.instanceSyncHandlerFactoryService = instanceSyncHandlerFactoryService;
    this.infrastructureMappingService = infrastructureMappingService;
    this.instanceService = instanceService;
    this.deploymentSummaryService = deploymentSummaryService;
    this.instanceSyncHelper = instanceSyncHelper;
    this.connectorService = connectorService;
    this.utils = instanceSyncServiceUtils;
    this.instanceSyncMonitoringService = instanceSyncMonitoringService;
    this.accountClient = accountClient;
    this.kryoSerializer = kryoSerializer;
  }

  @Override
  public void processInstanceSyncForNewDeployment(DeploymentEvent deploymentEvent) {
    int retryCount = 0;
    long startTime = System.currentTimeMillis();
    DeploymentSummaryDTO deploymentSummaryDTO = deploymentEvent.getDeploymentSummaryDTO();
    InfrastructureMappingDTO infrastructureMappingDTO = deploymentSummaryDTO.getInfrastructureMapping();
    logServerInstances(deploymentSummaryDTO.getServerInstanceInfoList());
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

          InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO;

          if (abstractInstanceSyncHandler.isInstanceSyncV2EnabledAndSupported(
                  deploymentSummaryDTO.getAccountIdentifier())) {
            instanceSyncPerpetualTaskInfoDTO = handlingInstanceSyncPerpetualTaskV2(
                abstractInstanceSyncHandler, infrastructureMappingDTO, deploymentSummaryDTO);
          } else {
            instanceSyncPerpetualTaskInfoDTO = handlingInstanceSyncPerpetualTaskV1(
                abstractInstanceSyncHandler, infrastructureMappingDTO, deploymentSummaryDTO, deploymentEvent);
          }

          InstanceSyncLocalCacheManager.setDeploymentSummary(
              deploymentSummaryDTO.getInstanceSyncKey(), deploymentSummaryDTO);

          // fix instances mapped to old/wrong infrastructure mapping
          fixCorruptedInstances(infrastructureMappingDTO);

          // Sync only for deployment infos / instance sync handler keys from instances from server
          performInstanceSync(instanceSyncPerpetualTaskInfoDTO, infrastructureMappingDTO,
              deploymentSummaryDTO.getServerInstanceInfoList(), abstractInstanceSyncHandler, true,
              abstractInstanceSyncHandler.isInstanceSyncV2EnabledAndSupported(
                  deploymentSummaryDTO.getAccountIdentifier()));

          instanceSyncMonitoringService.recordMetrics(
              infrastructureMappingDTO.getAccountIdentifier(), true, true, System.currentTimeMillis() - startTime);

          log.info("Instance sync completed");
          return;
        } catch (Exception exception) {
          log.warn("Attempt {} : Exception occurred during instance sync", retryCount + 1, exception);
          retryCount += 1;
        }
      }
      InstanceSyncLocalCacheManager.removeDeploymentSummary(deploymentSummaryDTO.getInstanceSyncKey());
      instanceSyncMonitoringService.recordMetrics(
          infrastructureMappingDTO.getAccountIdentifier(), true, true, System.currentTimeMillis() - startTime);
      log.warn("Instance sync failed after all retry attempts for deployment event : {}", deploymentEvent);
    }
  }

  private InstanceSyncPerpetualTaskInfoDTO handlingInstanceSyncPerpetualTaskV1(
      AbstractInstanceSyncHandler abstractInstanceSyncHandler, InfrastructureMappingDTO infrastructureMappingDTO,
      DeploymentSummaryDTO deploymentSummaryDTO, DeploymentEvent deploymentEvent) {
    Optional<InstanceSyncPerpetualTaskInfoDTO> instanceSyncPerpetualTaskInfoDTOOptional =
        instanceSyncPerpetualTaskInfoService.findByInfrastructureMappingId(infrastructureMappingDTO.getId());
    InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO;
    if (instanceSyncPerpetualTaskInfoDTOOptional.isEmpty()) {
      // no existing perpetual task info record found for given infrastructure mapping id
      // so create a new perpetual task and instance sync perpetual task info record
      String perpetualTaskId = instanceSyncPerpetualTaskService.createPerpetualTask(infrastructureMappingDTO,
          abstractInstanceSyncHandler, Collections.singletonList(deploymentSummaryDTO.getDeploymentInfoDTO()),
          deploymentEvent.getInfrastructureOutcome());
      instanceSyncPerpetualTaskInfoDTO =
          instanceSyncPerpetualTaskInfoService.save(prepareInstanceSyncPerpetualTaskInfoDTO(
              deploymentSummaryDTO, perpetualTaskId, infrastructureMappingDTO.getConnectorRef()));
    } else {
      instanceSyncPerpetualTaskInfoDTO = instanceSyncPerpetualTaskInfoDTOOptional.get();
      if (isNewDeploymentInfo(deploymentSummaryDTO.getDeploymentInfoDTO(),
              instanceSyncPerpetualTaskInfoDTO.getDeploymentInfoDetailsDTOList())) {
        // it means deployment info doesn't exist in the perpetual task info
        // add the deploymentinfo and deployment summary id to the instance sync pt info record
        addNewDeploymentInfoToInstanceSyncPerpetualTaskInfoRecord(
            instanceSyncPerpetualTaskInfoDTO, deploymentSummaryDTO);

        instanceSyncPerpetualTaskInfoService.updateDeploymentInfoDetailsList(instanceSyncPerpetualTaskInfoDTO);
        // Reset perpetual task to update the execution bundle with the latest information
        instanceSyncPerpetualTaskService.resetPerpetualTask(infrastructureMappingDTO.getAccountIdentifier(),
            instanceSyncPerpetualTaskInfoDTO.getPerpetualTaskId(), infrastructureMappingDTO,
            abstractInstanceSyncHandler,
            getDeploymentInfoDTOListFromInstanceSyncPerpetualTaskInfo(instanceSyncPerpetualTaskInfoDTO),
            deploymentEvent.getInfrastructureOutcome());
      }
    }
    return instanceSyncPerpetualTaskInfoDTO;
  }

  private InstanceSyncPerpetualTaskInfoDTO handlingInstanceSyncPerpetualTaskV2(
      AbstractInstanceSyncHandler abstractInstanceSyncHandler, InfrastructureMappingDTO infrastructureMappingDTO,
      DeploymentSummaryDTO deploymentSummaryDTO) {
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.getByRef(
        infrastructureMappingDTO.getAccountIdentifier(), deploymentSummaryDTO.getOrgIdentifier(),
        deploymentSummaryDTO.getProjectIdentifier(), infrastructureMappingDTO.getConnectorRef());
    Optional<InstanceSyncPerpetualTaskMappingDTO> instanceSyncPerpetualTaskMappingDTOOptional;
    InstanceSyncPerpetualTaskMappingDTO instanceSyncPerpetualTaskMappingDTO;
    if (connectorDTO.isPresent()) {
      ConnectorInfoDTO connectorInfoDTO = connectorDTO.get().getConnector();
      instanceSyncPerpetualTaskMappingDTOOptional = instanceSyncPerpetualTaskMappingService.findByConnectorRef(
          infrastructureMappingDTO.getAccountIdentifier(), connectorInfoDTO.getOrgIdentifier(),
          connectorInfoDTO.getProjectIdentifier(), infrastructureMappingDTO.getConnectorRef());
      if (instanceSyncPerpetualTaskMappingDTOOptional.isEmpty()) {
        instanceSyncPerpetualTaskMappingDTO = instanceSyncPerpetualTaskMappingService.save(
            InstanceSyncPerpetualTaskMappingDTO.builder()
                .accountId(infrastructureMappingDTO.getAccountIdentifier())
                .orgId(connectorInfoDTO.getOrgIdentifier())
                .projectId(connectorInfoDTO.getProjectIdentifier())
                .perpetualTaskId(instanceSyncPerpetualTaskService.createPerpetualTaskV2(
                    abstractInstanceSyncHandler, infrastructureMappingDTO, connectorInfoDTO))
                .connectorIdentifier(infrastructureMappingDTO.getConnectorRef())
                .build());
      } else {
        instanceSyncPerpetualTaskMappingDTO = instanceSyncPerpetualTaskMappingDTOOptional.get();
      }

      Optional<InstanceSyncPerpetualTaskInfoDTO> instanceSyncPerpetualTaskInfoDTOOptional =
          instanceSyncPerpetualTaskInfoService.findByInfrastructureMappingId(infrastructureMappingDTO.getId());
      if (instanceSyncPerpetualTaskInfoDTOOptional.isEmpty()) {
        return instanceSyncPerpetualTaskInfoService.save(prepareInstanceSyncPerpetualTaskInfoDTOV2(deploymentSummaryDTO,
            instanceSyncPerpetualTaskMappingDTO.getPerpetualTaskId(), infrastructureMappingDTO.getConnectorRef()));
      } else {
        InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO =
            instanceSyncPerpetualTaskInfoDTOOptional.get();
        if (isNewDeploymentInfo(deploymentSummaryDTO.getDeploymentInfoDTO(),
                instanceSyncPerpetualTaskInfoDTO.getDeploymentInfoDetailsDTOList())
            || instanceSyncPerpetualTaskInfoDTO.getConnectorIdentifier() == null) {
          instanceSyncPerpetualTaskInfoDTO.setPerpetualTaskIdV2(
              instanceSyncPerpetualTaskMappingDTO.getPerpetualTaskId());
          addNewDeploymentInfoToInstanceSyncPerpetualTaskInfoRecord(
              instanceSyncPerpetualTaskInfoDTO, deploymentSummaryDTO);
          instanceSyncPerpetualTaskInfoDTO =
              instanceSyncPerpetualTaskInfoService.updateDeploymentInfoListAndConnectorId(
                  instanceSyncPerpetualTaskInfoDTO, infrastructureMappingDTO.getConnectorRef());

          // Reset perpetual task to update the execution bundle with the latest information
          instanceSyncPerpetualTaskService.resetPerpetualTaskV2(infrastructureMappingDTO.getAccountIdentifier(),
              instanceSyncPerpetualTaskInfoDTO.getPerpetualTaskIdV2(), infrastructureMappingDTO,
              abstractInstanceSyncHandler, connectorInfoDTO);
        } else if (instanceSyncPerpetualTaskInfoDTO.getPerpetualTaskIdV2() == null) {
          // Adding PerpetualTaskIdV2 in InstanceSyncPerpetualTaskInfo
          instanceSyncPerpetualTaskInfoDTO.setPerpetualTaskIdV2(
              instanceSyncPerpetualTaskMappingDTO.getPerpetualTaskId());
          instanceSyncPerpetualTaskInfoDTO =
              instanceSyncPerpetualTaskInfoService.updatePerpetualTaskIdV1OrV2(instanceSyncPerpetualTaskInfoDTO);
        }
        return instanceSyncPerpetualTaskInfoDTO;
      }
    } else {
      // if connector is not found we have to delete all PTs V2 related to that connector Id
      IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRefOrThrowException(
          infrastructureMappingDTO.getConnectorRef(), infrastructureMappingDTO.getAccountIdentifier(),
          deploymentSummaryDTO.getOrgIdentifier(), deploymentSummaryDTO.getProjectIdentifier(), CONNECTOR);
      instanceSyncPerpetualTaskMappingDTOOptional = instanceSyncPerpetualTaskMappingService.findByConnectorRef(
          identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(),
          infrastructureMappingDTO.getConnectorRef());
      instanceSyncPerpetualTaskMappingDTOOptional.ifPresent(syncPerpetualTaskMappingDTO
          -> instanceSyncPerpetualTaskService.deletePerpetualTask(
              infrastructureMappingDTO.getAccountIdentifier(), syncPerpetualTaskMappingDTO.getPerpetualTaskId()));
    }

    throw new InvalidRequestException(
        String.format("No connector found for  connectorRef : [%s]", infrastructureMappingDTO.getConnectorRef()), USER);
  }

  @Override
  public void processInstanceSyncByPerpetualTask(String accountIdentifier, String perpetualTaskId,
      InstanceSyncPerpetualTaskResponse instanceSyncPerpetualTaskResponse) {
    long startTime = System.currentTimeMillis();
    try (AutoLogContext ignore1 = new AccountLogContext(accountIdentifier, OverrideBehavior.OVERRIDE_ERROR);
         AutoLogContext ignore2 = InstanceSyncLogContext.builder()
                                      .instanceSyncFlow(InstanceSyncFlow.PERPETUAL_TASK_FLOW.name())
                                      .perpetualTaskId(perpetualTaskId)
                                      .build(OverrideBehavior.OVERRIDE_ERROR)) {
      log.info("Process instance sync by perpetual task");
      if (instanceSyncPerpetualTaskResponse.getServerInstanceDetails() == null) {
        log.error("server instances details cannot be null");
        return;
      }

      logServerInstances(instanceSyncPerpetualTaskResponse.getServerInstanceDetails());
      Optional<InstanceSyncPerpetualTaskInfoDTO> instanceSyncPerpetualTaskInfoDTOOptional =
          instanceSyncPerpetualTaskInfoService.findByPerpetualTaskId(accountIdentifier, perpetualTaskId);
      if (!instanceSyncPerpetualTaskInfoDTOOptional.isPresent()) {
        log.error("Instance sync perpetual task info not found");
        instanceSyncPerpetualTaskService.deletePerpetualTask(accountIdentifier, perpetualTaskId);
        return;
      }

      InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO =
          instanceSyncPerpetualTaskInfoDTOOptional.get();
      try (AutoLogContext ignore3 =
               InstanceSyncLogContext.builder()
                   .instanceSyncFlow(InstanceSyncFlow.PERPETUAL_TASK_FLOW.name())
                   .infrastructureMappingId(instanceSyncPerpetualTaskInfoDTO.getInfrastructureMappingId())
                   .build(OverrideBehavior.OVERRIDE_ERROR);) {
        Optional<InfrastructureMappingDTO> infrastructureMappingDTO =
            infrastructureMappingService.getByInfrastructureMappingId(
                instanceSyncPerpetualTaskInfoDTO.getInfrastructureMappingId());
        if (!infrastructureMappingDTO.isPresent()) {
          log.error(
              "Infrastructure mapping not found for {}", instanceSyncPerpetualTaskInfoDTO.getInfrastructureMappingId());
          // delete perpetual task as well as instance sync perpetual task info record
          instanceSyncHelper.cleanUpInstanceSyncPerpetualTaskInfo(instanceSyncPerpetualTaskInfoDTO, false);
          return;
        }

        if (!doSvcAndEnvExist(infrastructureMappingDTO.get())) {
          // as either or both of svc and env don't exist, we delete the instances before deleting the perpetual task
          deleteInstances(infrastructureMappingDTO.get());
          instanceSyncHelper.cleanUpInstanceSyncPerpetualTaskInfo(instanceSyncPerpetualTaskInfoDTO, false);
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
              instanceSyncPerpetualTaskResponse.getServerInstanceDetails(), instanceSyncHandler, false, false);
          log.info("Instance Sync completed");
        } catch (Exception exception) {
          log.warn("Exception occurred during instance sync", exception);
        } finally {
          instanceSyncMonitoringService.recordMetrics(infrastructureMappingDTO.get().getAccountIdentifier(), true,
              false, System.currentTimeMillis() - startTime);
        }
      }
    }
  }

  @Override
  public void processInstanceSyncByPerpetualTaskV2(
      String accountIdentifier, String perpetualTaskId, InstanceSyncResponseV2 result) {
    long startTime = System.currentTimeMillis();
    try (AutoLogContext ignore1 = new AccountLogContext(accountIdentifier, OverrideBehavior.OVERRIDE_ERROR);
         AutoLogContext ignore2 = InstanceSyncLogContext.builder()
                                      .instanceSyncFlow(InstanceSyncFlow.PERPETUAL_TASK_FLOW.name())
                                      .perpetualTaskId(perpetualTaskId)
                                      .build(OverrideBehavior.OVERRIDE_ERROR)) {
      log.info("Process instance sync by perpetual task");

      List<InstanceSyncPerpetualTaskInfoDTO> instanceSyncPerpetualTaskInfoDTOList =
          instanceSyncPerpetualTaskInfoService.findAll(accountIdentifier, perpetualTaskId);

      if (instanceSyncPerpetualTaskInfoDTOList.isEmpty()) {
        log.error("Instance sync perpetual task info not found");
        instanceSyncPerpetualTaskService.deletePerpetualTask(accountIdentifier, perpetualTaskId);
        instanceSyncPerpetualTaskMappingService.delete(accountIdentifier, perpetualTaskId);
        return;
      }

      if (!result.getStatus().getExecutionStatus().isEmpty() && !result.getStatus().getIsSuccessful()) {
        log.error("Instance Sync failed for perpetual task: [{}] and response [{}], with error: [{}]", perpetualTaskId,
            result, result.getStatus().getErrorMessage());
        for (InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO : instanceSyncPerpetualTaskInfoDTOList) {
          cleanPerpetualTaskIfFailingForSevenDays(instanceSyncPerpetualTaskInfoDTO);
        }
        return;
      }

      Map<String, InstanceSyncPerpetualTaskInfoDTO> instanceSyncPerpetualTaskInfoMap = new HashMap<>();
      for (InstanceSyncPerpetualTaskInfoDTO taskInfoDTO : instanceSyncPerpetualTaskInfoDTOList) {
        instanceSyncPerpetualTaskInfoMap.put(taskInfoDTO.getId(), taskInfoDTO);
      }

      Map<String, InstanceSyncData> instancesPerTask = new HashMap<>();
      for (InstanceSyncData instanceSyncData : result.getInstanceDataList()) {
        if (instanceSyncData.getStatus().getIsSuccessful()
            && !instancesPerTask.containsKey(instanceSyncData.getTaskInfoId())) {
          instancesPerTask.put(instanceSyncData.getTaskInfoId(), instanceSyncData);
        } else {
          cleanPerpetualTaskIfFailingForSevenDays(
              instanceSyncPerpetualTaskInfoMap.get(instanceSyncData.getTaskInfoId()));
        }
      }

      handlingInstanceSyncV2(
          accountIdentifier, perpetualTaskId, instanceSyncPerpetualTaskInfoMap, instancesPerTask, startTime);
    }
  }

  private void handlingInstanceSyncV2(String accountIdentifier, String perpetualTaskId,
      Map<String, InstanceSyncPerpetualTaskInfoDTO> instanceSyncPerpetualTaskInfoMap,
      Map<String, InstanceSyncData> instancesPerTask, long startTime) {
    for (String taskInfoId : instancesPerTask.keySet()) {
      InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO =
          instanceSyncPerpetualTaskInfoMap.get(taskInfoId);
      if (isNull(instanceSyncPerpetualTaskInfoDTO)) {
        log.warn("No InstanceSyncPerpetualTaskInfo Present for taskInfoId: [{}]", taskInfoId);
        continue;
      }

      try (AutoLogContext ignore3 =
               InstanceSyncLogContext.builder()
                   .instanceSyncFlow(InstanceSyncFlow.PERPETUAL_TASK_FLOW.name())
                   .infrastructureMappingId(instanceSyncPerpetualTaskInfoDTO.getInfrastructureMappingId())
                   .build(OverrideBehavior.OVERRIDE_ERROR);) {
        Optional<InfrastructureMappingDTO> infrastructureMappingDTO =
            infrastructureMappingService.getByInfrastructureMappingId(
                instanceSyncPerpetualTaskInfoDTO.getInfrastructureMappingId());
        if (infrastructureMappingDTO.isEmpty()) {
          log.error(
              "Infrastructure mapping not found for {}", instanceSyncPerpetualTaskInfoDTO.getInfrastructureMappingId());
          // delete instance sync perpetual task info record
          instanceSyncHelper.cleanUpOnlyInstanceSyncPerpetualTaskInfo(instanceSyncPerpetualTaskInfoDTO);
          continue;
        }

        if (isEmpty(instanceSyncPerpetualTaskInfoDTO.getDeploymentInfoDetailsDTOList())) {
          // There is no deployment info left to process for instance sync
          instanceSyncPerpetualTaskInfoService.deleteById(
              instanceSyncPerpetualTaskInfoDTO.getAccountIdentifier(), instanceSyncPerpetualTaskInfoDTO.getId());
          log.info("Deleted instance sync perpetual task info : {} as there is no deployment info to do instance sync",
              instanceSyncPerpetualTaskInfoDTO.getId());
          continue;
        }

        AbstractInstanceSyncHandler abstractInstanceSyncHandler =
            instanceSyncHandlerFactoryService.getInstanceSyncHandler(
                instanceSyncPerpetualTaskInfoDTO.getDeploymentInfoDetailsDTOList()
                    .get(0)
                    .getDeploymentInfoDTO()
                    .getType(),
                infrastructureMappingDTO.get().getInfrastructureKind());

        if (!abstractInstanceSyncHandler.isInstanceSyncV2EnabledAndSupported(accountIdentifier)) {
          migrateToInstanceSyncV1(
              instanceSyncPerpetualTaskInfoDTO, infrastructureMappingDTO.get(), abstractInstanceSyncHandler);
          continue;
        }

        if (!doSvcAndEnvExist(infrastructureMappingDTO.get())) {
          // as either or both of svc and env don't exist, we delete the instances
          instanceSyncPerpetualTaskMappingService.delete(accountIdentifier, perpetualTaskId);
          deleteInstances(infrastructureMappingDTO.get());
          continue;
        }

        InstanceSyncData instanceSyncData = instancesPerTask.get(taskInfoId);
        try (
            AcquiredLock<?> acquiredLock = persistentLocker.waitToAcquireLock(InstanceSyncConstants.INSTANCE_SYNC_PREFIX
                    + instanceSyncPerpetualTaskInfoDTO.getInfrastructureMappingId(),
                InstanceSyncConstants.INSTANCE_SYNC_LOCK_TIMEOUT, InstanceSyncConstants.INSTANCE_SYNC_WAIT_TIMEOUT)) {
          AbstractInstanceSyncHandler instanceSyncHandler = instanceSyncHandlerFactoryService.getInstanceSyncHandler(
              instanceSyncData.getDeploymentType(), infrastructureMappingDTO.get().getInfrastructureKind());

          List<ServerInstanceInfo> serverInstanceInfoList = (List<ServerInstanceInfo>) kryoSerializer.asObject(
              instanceSyncData.getServerInstanceInfo().toByteArray());

          try {
            performInstanceSync(instanceSyncPerpetualTaskInfoDTO, infrastructureMappingDTO.get(),
                serverInstanceInfoList, instanceSyncHandler, false, true);
            log.info("Instance Sync completed");
            // cleaning up V1 perpetual task
            cleanupPerpetualTaskV1(instanceSyncPerpetualTaskInfoDTO);
            instanceSyncPerpetualTaskInfoDTO.setLastSuccessfulRun(System.currentTimeMillis());
            instanceSyncPerpetualTaskInfoService.updateLastSuccessfulRun(instanceSyncPerpetualTaskInfoDTO);
          } catch (Exception exception) {
            log.error("Exception occurred during instance sync", exception);
          } finally {
            instanceSyncMonitoringService.recordMetrics(infrastructureMappingDTO.get().getAccountIdentifier(), true,
                false, System.currentTimeMillis() - startTime);
          }
        }
      }
    }
  }

  private void migrateToInstanceSyncV1(InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO,
      InfrastructureMappingDTO infrastructureMappingDTO, AbstractInstanceSyncHandler abstractInstanceSyncHandler) {
    if (instanceSyncPerpetualTaskInfoDTO.getPerpetualTaskId() == null) {
      String perpetualTaskIdV1 =
          instanceSyncPerpetualTaskService.createPerpetualTask(infrastructureMappingDTO, abstractInstanceSyncHandler,
              instanceSyncPerpetualTaskInfoDTO.getDeploymentInfoDetailsDTOList()
                  .stream()
                  .map(DeploymentInfoDetailsDTO::getDeploymentInfoDTO)
                  .collect(Collectors.toList()),
              abstractInstanceSyncHandler.getInfrastructureOutcome(infrastructureMappingDTO.getInfrastructureKind(),
                  instanceSyncPerpetualTaskInfoDTO.getDeploymentInfoDetailsDTOList().get(0).getDeploymentInfoDTO(),
                  instanceSyncPerpetualTaskInfoDTO.getConnectorIdentifier()));
      instanceSyncPerpetualTaskInfoDTO.setPerpetualTaskIdV2(null);
      instanceSyncPerpetualTaskInfoDTO.setPerpetualTaskId(perpetualTaskIdV1);
      instanceSyncPerpetualTaskInfoService.updatePerpetualTaskIdV1OrV2(instanceSyncPerpetualTaskInfoDTO);
    }
  }

  private void cleanPerpetualTaskIfFailingForSevenDays(
      InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO) {
    if (instanceSyncPerpetualTaskInfoDTO.getLastSuccessfulRun() != null
        && instanceSyncPerpetualTaskInfoDTO.getLastSuccessfulRun() != 0
        && instanceSyncPerpetualTaskInfoDTO.getLastSuccessfulRun() + RELEASE_PRESERVE_TIME
            < System.currentTimeMillis()) {
      instanceSyncPerpetualTaskInfoService.deleteById(
          instanceSyncPerpetualTaskInfoDTO.getAccountIdentifier(), instanceSyncPerpetualTaskInfoDTO.getId());
    }
  }

  private void cleanupPerpetualTaskV1(InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO) {
    if (instanceSyncPerpetualTaskInfoDTO.getPerpetualTaskId() != null) {
      instanceSyncPerpetualTaskService.deletePerpetualTask(instanceSyncPerpetualTaskInfoDTO.getAccountIdentifier(),
          instanceSyncPerpetualTaskInfoDTO.getPerpetualTaskId());
      instanceSyncPerpetualTaskInfoDTO.setPerpetualTaskId(null);
      instanceSyncPerpetualTaskInfoService.updatePerpetualTaskIdV1OrV2(instanceSyncPerpetualTaskInfoDTO);
    }
  }

  public InstanceSyncTaskDetails fetchTaskDetails(
      String perpetualTaskId, String accountIdentifier, int page, int size) {
    Pageable pageRequest =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, InstanceSyncPerpetualTaskInfoKeys.createdAt));
    Page<InstanceSyncPerpetualTaskInfoDTO> instanceSyncPerpetualTaskInfoDTOList =
        instanceSyncPerpetualTaskInfoService.findAllInPages(pageRequest, accountIdentifier, perpetualTaskId);
    List<DeploymentReleaseDetails> deploymentReleaseDetailsList = new ArrayList<>();
    for (InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO : instanceSyncPerpetualTaskInfoDTOList) {
      Optional<InfrastructureMappingDTO> infrastructureMappingDTOOptional =
          infrastructureMappingService.getByInfrastructureMappingId(
              instanceSyncPerpetualTaskInfoDTO.getInfrastructureMappingId());
      if (infrastructureMappingDTOOptional.isEmpty()) {
        log.error(
            "Infrastructure mapping not found for {}", instanceSyncPerpetualTaskInfoDTO.getInfrastructureMappingId());
        continue;
      }
      InfrastructureMappingDTO infrastructureMappingDTO = infrastructureMappingDTOOptional.get();

      if (instanceSyncPerpetualTaskInfoDTO.getDeploymentInfoDetailsDTOList().isEmpty()) {
        continue;
      }

      AbstractInstanceSyncHandler instanceSyncHandler = instanceSyncHandlerFactoryService.getInstanceSyncHandler(
          instanceSyncPerpetualTaskInfoDTO.getDeploymentInfoDetailsDTOList().get(0).getDeploymentInfoDTO().getType(),
          infrastructureMappingDTO.getInfrastructureKind());

      deploymentReleaseDetailsList.add(
          instanceSyncHandler.getDeploymentReleaseDetails(instanceSyncPerpetualTaskInfoDTO));
    }

    return InstanceSyncTaskDetails.builder()
        .details(getNGPageResponse(instanceSyncPerpetualTaskInfoDTOList, deploymentReleaseDetailsList))
        .responseBatchConfig(
            ResponseBatchConfig.builder().releaseCount(RELEASE_COUNT_LIMIT).instanceCount(INSTANCE_COUNT_LIMIT).build())
        .build();
  }

  private static PageResponse<DeploymentReleaseDetails> getNGPageResponse(
      Page<InstanceSyncPerpetualTaskInfoDTO> instanceSyncPerpetualTaskInfoDTOList,
      List<DeploymentReleaseDetails> deploymentReleaseDetailsList) {
    return PageResponse.<DeploymentReleaseDetails>builder()
        .totalPages(instanceSyncPerpetualTaskInfoDTOList.getTotalPages())
        .totalItems(instanceSyncPerpetualTaskInfoDTOList.getTotalElements())
        .pageItemCount(instanceSyncPerpetualTaskInfoDTOList.getContent().size())
        .content(deploymentReleaseDetailsList)
        .pageSize(instanceSyncPerpetualTaskInfoDTOList.getSize())
        .pageIndex(instanceSyncPerpetualTaskInfoDTOList.getNumber())
        .empty(instanceSyncPerpetualTaskInfoDTOList.isEmpty())
        .build();
  }

  // ------------------------------- PRIVATE METHODS --------------------------------------

  /**
   * @param serverInstanceInfoList details of all instances present in current state of server
   */
  private void performInstanceSync(InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO,
      InfrastructureMappingDTO infrastructureMappingDTO, List<ServerInstanceInfo> serverInstanceInfoList,
      AbstractInstanceSyncHandler instanceSyncHandler, boolean isNewDeploymentSync, boolean isInstanceSyncV2) {
    // Prepare final list of instances to be added / deleted / updated
    Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified =
        handleInstanceSync(instanceSyncPerpetualTaskInfoDTO, infrastructureMappingDTO, serverInstanceInfoList,
            instanceSyncHandler, isNewDeploymentSync, isInstanceSyncV2);
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
      AbstractInstanceSyncHandler instanceSyncHandler, boolean isNewDeploymentSync, boolean isInstanceSyncV2) {
    log.info("isNewDeploymentSync: {}", isNewDeploymentSync);

    // get active instances by infra mapping id. this can span across projects, orgs in case
    // multiple pipelines deploy service, env of org and account level
    List<InstanceDTO> instancesInDB = instanceService.getActiveInstancesByInfrastructureMappingId(
        infrastructureMappingDTO.getAccountIdentifier(), infrastructureMappingDTO.getOrgIdentifier(),
        infrastructureMappingDTO.getProjectIdentifier(), infrastructureMappingDTO.getId());
    log.info(
        "Instances in DB: [{}]", instancesInDB.stream().map(InstanceDTO::getInstanceKey).collect(Collectors.toList()));

    List<InstanceInfoDTO> instanceInfosFromServer =
        instanceSyncHandler.getInstanceDetailsFromServerInstances(serverInstanceInfoList);

    // map all instances and server instances infos to instance sync handler key (corresponding to deployment info)
    // basically trying to group instances corresponding to a "cluster" together
    Map<String, List<InstanceDTO>> syncKeyToInstancesInDBMap =
        utils.getSyncKeyToInstances(instanceSyncHandler, instancesInDB);
    Map<String, List<InstanceInfoDTO>> syncKeyToInstanceInfoFromServerMap =
        utils.getSyncKeyToInstancesFromServerMap(instanceSyncHandler, instanceInfosFromServer);
    Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified =
        utils.initMapForTrackingFinalListOfInstances();

    processInstanceSyncForSyncKeysFromServerInstances(instanceSyncHandler, infrastructureMappingDTO,
        instanceSyncPerpetualTaskInfoDTO, syncKeyToInstancesInDBMap, syncKeyToInstanceInfoFromServerMap,
        instancesToBeModified, isNewDeploymentSync, isInstanceSyncV2);
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
      log.info("Instance sync key {}, instances to be deleted: [{}]", syncKey,
          syncKeyToInstancesInDBMap.get(syncKey)
              .stream()
              .map(InstanceDTO::getInstanceKey)
              .collect(Collectors.toList()));
    });
  }

  private void processInstanceSyncForSyncKeysFromServerInstances(AbstractInstanceSyncHandler instanceSyncHandler,
      InfrastructureMappingDTO infrastructureMappingDTO,
      InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO,
      Map<String, List<InstanceDTO>> syncKeyToInstancesInDBMap,
      Map<String, List<InstanceInfoDTO>> syncKeyToInstanceInfoFromServerMap,
      Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified, boolean isNewDeploymentSync,
      boolean isInstanceSyncV2) {
    Set<String> instanceSyncHandlerKeys = syncKeyToInstanceInfoFromServerMap.keySet();
    instanceSyncHandlerKeys.forEach(instanceSyncHandlerKey
        -> processInstancesByInstanceSyncHandlerKey(instanceSyncHandler, infrastructureMappingDTO,
            syncKeyToInstancesInDBMap.getOrDefault(instanceSyncHandlerKey, new ArrayList<>()),
            syncKeyToInstanceInfoFromServerMap.getOrDefault(instanceSyncHandlerKey, new ArrayList<>()),
            instancesToBeModified, instanceSyncHandlerKey, isNewDeploymentSync));

    // Update the deployment info details for all deployment infos for which we received instances from server
    // This is to track deployment infos for which we are not getting instances from server (probably now not in use)
    updateDeploymentInfoDetails(
        instanceSyncHandler, instanceSyncPerpetualTaskInfoDTO, instanceSyncHandlerKeys, isInstanceSyncV2);
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

    log.info("Instances from server: {}", instanceInfosFromServerMap.keySet());
    log.info("Instances in DB: {}", instancesInDBMap.keySet());

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

    log.info("Instances to be deleted: {}", instancesToBeDeleted);

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

    log.info("Instances to be added: {}", instancesToBeAdded);

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

    log.info("Instances to be updated: {}", instancesToBeUpdated);

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
        instanceDTO.setRollbackStatus(deploymentSummaryFromDB.getRollbackStatus());
        instanceDTO.setStageNodeExecutionId(deploymentSummaryFromDB.getStageNodeExecutionId());
        instanceDTO.setStageSetupId(deploymentSummaryFromDB.getStageSetupId());
        instanceDTO.setStageStatus(deploymentSummaryFromDB.getStageStatus());

        // instance will be owned by the org/project which last deployed with the infra mapping, instance sync key
        updateOrgProjectIdentifiers(instanceDTO, deploymentSummaryFromDB);
        // known corner limitation for optimisations: We don't update Service name and environment name in case it is
        // updated.
      });
    }

    instancesToBeUpdated.forEach(instanceKey
        -> instancesToBeModified.get(OperationsOnInstances.UPDATE)
               .add(instanceSyncHandler.updateInstance(
                   instancesInDBMap.get(instanceKey), instanceInfosFromServerMap.get(instanceKey))));
  }

  private void updateOrgProjectIdentifiers(InstanceDTO instanceDTO, DeploymentSummaryDTO deploymentSummaryFromDB) {
    if (EmptyPredicate.isNotEmpty(instanceDTO.getOrgIdentifier())
        && EmptyPredicate.isNotEmpty(deploymentSummaryFromDB.getOrgIdentifier())
        && StringUtils.compare(instanceDTO.getOrgIdentifier(), deploymentSummaryFromDB.getOrgIdentifier()) != 0) {
      instanceDTO.setOrgIdentifier(deploymentSummaryFromDB.getOrgIdentifier());
    }

    if (EmptyPredicate.isNotEmpty(instanceDTO.getProjectIdentifier())
        && EmptyPredicate.isNotEmpty(deploymentSummaryFromDB.getProjectIdentifier())
        && StringUtils.compare(instanceDTO.getProjectIdentifier(), deploymentSummaryFromDB.getProjectIdentifier())
            != 0) {
      instanceDTO.setProjectIdentifier(deploymentSummaryFromDB.getProjectIdentifier());
    }
  }

  // Update instance sync perpetual task info record with updated deployment info details list
  private void updateDeploymentInfoDetails(AbstractInstanceSyncHandler instanceSyncHandler,
      InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO, Set<String> processedInstanceSyncHandlerKeys,
      boolean isInstanceSyncV2) {
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
      instanceSyncHelper.cleanUpInstanceSyncPerpetualTaskInfo(instanceSyncPerpetualTaskInfoDTO, isInstanceSyncV2);
      log.info(
          "Deleted instance sync perpetual task info : {} and perpetual task as there is no deployment info to do instance sync",
          instanceSyncPerpetualTaskInfoDTO.getId());
    } else {
      // Update new updated deployment info details list to the instance sync perpetual task info record
      instanceSyncPerpetualTaskInfoService.updateDeploymentInfoDetailsList(instanceSyncPerpetualTaskInfoDTO);
    }
  }

  private InstanceSyncPerpetualTaskInfoDTO prepareInstanceSyncPerpetualTaskInfoDTO(
      DeploymentSummaryDTO deploymentSummaryDTO, String perpetualTaskId, String connectorIdentifier) {
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
        .connectorIdentifier(connectorIdentifier)
        .build();
  }

  private InstanceSyncPerpetualTaskInfoDTO prepareInstanceSyncPerpetualTaskInfoDTOV2(
      DeploymentSummaryDTO deploymentSummaryDTO, String perpetualTaskId, String connectorIdentifier) {
    InfrastructureMappingDTO infrastructureMappingDTO = deploymentSummaryDTO.getInfrastructureMapping();
    return InstanceSyncPerpetualTaskInfoDTO.builder()
        .accountIdentifier(infrastructureMappingDTO.getAccountIdentifier())
        .infrastructureMappingId(deploymentSummaryDTO.getInfrastructureMappingId())
        .deploymentInfoDetailsDTOList(
            Collections.singletonList(DeploymentInfoDetailsDTO.builder()
                                          .deploymentInfoDTO(deploymentSummaryDTO.getDeploymentInfoDTO())
                                          .lastUsedAt(System.currentTimeMillis())
                                          .build()))
        .perpetualTaskIdV2(perpetualTaskId)
        .connectorIdentifier(connectorIdentifier)
        .lastSuccessfulRun(System.currentTimeMillis())
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
        .envGroupRef(instanceDTO.getEnvGroupRef())
        .stageNodeExecutionId(instanceDTO.getStageNodeExecutionId())
        .stageSetupId(instanceDTO.getStageSetupId())
        .stageStatus(instanceDTO.getStageStatus())
        .rollbackStatus(instanceDTO.getRollbackStatus())
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
            .envIdentifier(IdentifierRefHelper.getRefFromIdentifierOrRef(environment.getAccountId(),
                environment.getOrgIdentifier(), environment.getProjectIdentifier(), environment.getIdentifier()))
            .envType(environment.getType())
            .envName(environment.getName())
            .envGroupRef(deploymentSummaryDTO.getEnvGroupRef())
            .serviceName(serviceEntity.getName())
            .serviceIdentifier(IdentifierRefHelper.getRefFromIdentifierOrRef(serviceEntity.getAccountId(),
                serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier(), serviceEntity.getIdentifier()))
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
            .stageNodeExecutionId(deploymentSummaryDTO.getStageNodeExecutionId())
            .stageSetupId(deploymentSummaryDTO.getStageSetupId())
            .rollbackStatus(deploymentSummaryDTO.getRollbackStatus())
            .stageStatus(deploymentSummaryDTO.getStageStatus())
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
    serverInstanceInfoList.forEach(serverInstanceInfo -> {
      if (stringBuilder.length() > 0) {
        stringBuilder.append(" :: ");
      }
      stringBuilder.append(serverInstanceInfo.toString());
    });
    log.info("Server Instances in the perpetual task response : {}", stringBuilder);
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
        log.info("Updating instances [{}] from old infrastructureMappingId {} to new infrastructureMappingId {}",
            instanceIds, oldInfrastructureMappingDTO.getId(), infrastructureMappingDTO.getId());
      }
    });
  }

  private boolean doSvcAndEnvExist(InfrastructureMappingDTO infrastructureMappingDTO) {
    try {
      ServiceEntity serviceEntity = instanceSyncHelper.fetchService(infrastructureMappingDTO);
    } catch (EntityNotFoundException e) {
      log.error("Service not found", e);
      return false;
    }
    try {
      Environment environment = instanceSyncHelper.fetchEnvironment(infrastructureMappingDTO);
    } catch (EntityNotFoundException e) {
      log.error("Environment not found", e);
      return false;
    }
    return true;
  }

  private void deleteInstances(InfrastructureMappingDTO mappingDTO) {
    List<InstanceDTO> instancesInDB =
        instanceService.getActiveInstancesByInfrastructureMappingId(mappingDTO.getAccountIdentifier(),
            mappingDTO.getOrgIdentifier(), mappingDTO.getProjectIdentifier(), mappingDTO.getId());
    instanceService.deleteAll(instancesInDB);
  }
}
