/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.MOVE_PCF_INSTANCE_SYNC_TO_PERPETUAL_TASK;
import static io.harness.beans.FeatureName.STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_PCF_DEPLOYMENTS;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.service.impl.instance.InstanceSyncFlow.NEW_DEPLOYMENT;
import static software.wings.service.impl.instance.InstanceSyncFlow.PERPETUAL_TASK;

import static java.util.function.Function.identity;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.NoDelegatesException;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfInstanceSyncResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.pcf.PcfAppNotFoundException;

import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.PcfDeploymentInfo;
import software.wings.api.PhaseExecutionData;
import software.wings.api.PhaseStepExecutionData;
import software.wings.api.ondemandrollback.OnDemandRollbackInfo;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.PcfConfig;
import software.wings.beans.PcfInfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceBuilder;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.PcfInstanceInfo;
import software.wings.beans.infrastructure.instance.key.PcfInstanceKey;
import software.wings.beans.infrastructure.instance.key.deployment.DeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.PcfDeploymentKey;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.InstanceSyncPerpetualTaskCreator;
import software.wings.service.PCFInstanceSyncPerpetualTaskCreator;
import software.wings.service.impl.PcfHelperService;
import software.wings.sm.PhaseStepExecutionSummary;
import software.wings.sm.StepExecutionSummary;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class PcfInstanceHandler extends InstanceHandler implements InstanceSyncByPerpetualTaskHandler {
  @Inject private PcfHelperService pcfHelperService;
  @Inject private PCFInstanceSyncPerpetualTaskCreator pcfInstanceSyncPerpetualTaskCreator;

  @Override
  public void syncInstances(String appId, String infraMappingId, InstanceSyncFlow instanceSyncFlow) {
    Multimap<String, Instance> pcfAppNameInstanceMap = ArrayListMultimap.create();
    syncInstancesInternal(appId, infraMappingId, pcfAppNameInstanceMap, null, false,
        OnDemandRollbackInfo.builder().onDemandRollback(false).build(), null, instanceSyncFlow);
  }

  @Override
  public void processInstanceSyncResponseFromPerpetualTask(
      InfrastructureMapping infrastructureMapping, DelegateResponseData response) {
    Multimap<String, Instance> pcfAppNameInstanceMap = ArrayListMultimap.create();

    syncInstancesInternal(infrastructureMapping.getAppId(), infrastructureMapping.getUuid(), pcfAppNameInstanceMap,
        null, false, OnDemandRollbackInfo.builder().onDemandRollback(false).build(),
        (CfCommandExecutionResponse) response, PERPETUAL_TASK);
  }

  private void syncInstancesInternal(String appId, String infraMappingId,
      Multimap<String, Instance> pcfAppNameInstanceMap, List<DeploymentSummary> newDeploymentSummaries,
      boolean rollback, OnDemandRollbackInfo onDemandRollbackInfo,
      CfCommandExecutionResponse cfCommandExecutionResponse, InstanceSyncFlow instanceSyncFlow) {
    log.info("Performing PCF Instance sync via [{}], Infrastructure Mapping : [{}]", instanceSyncFlow, infraMappingId);
    InfrastructureMapping infrastructureMapping = infraMappingService.get(appId, infraMappingId);
    Objects.requireNonNull(infrastructureMapping);

    if (!(infrastructureMapping instanceof PcfInfrastructureMapping)) {
      String msg =
          "Incompatible infra mapping type. Expecting PCF type. Found:" + infrastructureMapping.getInfraMappingType();
      log.error(msg);
      throw WingsException.builder().message(msg).build();
    }

    Map<String, DeploymentSummary> pcfAppNamesNewDeploymentSummaryMap = getDeploymentSummaryMap(newDeploymentSummaries);

    CfInstanceSyncResponse cfInstanceSyncResponse = cfCommandExecutionResponse == null
        ? null
        : (CfInstanceSyncResponse) cfCommandExecutionResponse.getPcfCommandResponse();
    String applicationNameIfSupplied = (cfInstanceSyncResponse == null) ? null : cfInstanceSyncResponse.getName();
    loadPcfAppNameInstanceMap(appId, infraMappingId, pcfAppNameInstanceMap, applicationNameIfSupplied);

    PcfInfrastructureMapping pcfInfrastructureMapping = (PcfInfrastructureMapping) infrastructureMapping;
    SettingAttribute settingAttribute = settingsService.get(pcfInfrastructureMapping.getComputeProviderSettingId());
    PcfConfig pcfConfig = (PcfConfig) settingAttribute.getValue();

    // This is to handle the case of the instances stored in the new schema.
    if (pcfAppNameInstanceMap.size() > 0) {
      pcfAppNameInstanceMap.keySet().forEach(pcfApplicationName -> {
        log.info("Performing Instance sync for PCF Application : [{}], Harness Application : [{}]", pcfApplicationName,
            appId);
        // Get all the instances for the given containerSvcName (In kubernetes, this is replication Controller and in
        // ECS it is taskDefinition)
        boolean failedToRetrieveData = false;
        List<PcfInstanceInfo> latestpcfInstanceInfoList = new ArrayList<>();
        try {
          latestpcfInstanceInfoList =
              pcfHelperService.getApplicationDetails(pcfApplicationName, pcfInfrastructureMapping.getOrganization(),
                  pcfInfrastructureMapping.getSpace(), pcfConfig, cfCommandExecutionResponse);
        } catch (NoDelegatesException e) {
          log.warn("Delegates are not available", e.getMessage());
          failedToRetrieveData = true;
        } catch (Exception e) {
          if (e instanceof PcfAppNotFoundException) {
            throw new InvalidRequestException(
                String.format(
                    "Application not found for PCF application Name: [%s] with InfraMappingId: [%s], Failed to perform instance sync for this PCF Application with Exception: [%s]",
                    pcfApplicationName, infraMappingId, ExceptionUtils.getMessage(e)),
                e);
          } else {
            throw new InvalidRequestException(
                String.format(
                    "Failed to retrieve data for PCF Application Name: [%s] with InfraMappingId: [%s],  Failed to perform instance sync for this PCF Application with Exception: [%s]",
                    pcfApplicationName, infraMappingId, ExceptionUtils.getMessage(e)),
                e);
          }
        }

        if (!failedToRetrieveData) {
          notNullCheck("latestpcfInstanceInfoList", latestpcfInstanceInfoList);
          log.info("Received Instance details for PCF Application : [{}]. Instance count : [{}] ", pcfApplicationName,
              latestpcfInstanceInfoList.size());

          Map<String, PcfInstanceInfo> latestPcfInstanceInfoMap =
              latestpcfInstanceInfoList.stream().collect(Collectors.toMap(PcfInstanceInfo::getId, identity()));

          Collection<Instance> instancesInDB = pcfAppNameInstanceMap.get(pcfApplicationName);

          Map<String, Instance> instancesInDBMap = new HashMap<>();

          // If there are prior instances in db already
          if (isNotEmpty(instancesInDB)) {
            instancesInDB.forEach(instance -> {
              if (instance != null) {
                instancesInDBMap.put(instance.getPcfInstanceKey().getId(), instance);
              }
            });
          }

          // Find the instances that were yet to be added to db
          SetView<String> instancesToBeAdded =
              Sets.difference(latestPcfInstanceInfoMap.keySet(), instancesInDBMap.keySet());

          SetView<String> instancesToBeDeleted =
              Sets.difference(instancesInDBMap.keySet(), latestPcfInstanceInfoMap.keySet());

          if (onDemandRollbackInfo.isOnDemandRollback()) {
            handleOnDemandRollbackDeployment(
                instancesInDBMap, latestPcfInstanceInfoMap, onDemandRollbackInfo.getRollbackExecutionId());
          }

          Set<String> instanceIdsToBeDeleted = new HashSet<>();
          instancesToBeDeleted.forEach(id -> {
            Instance instance = instancesInDBMap.get(id);
            if (instance != null) {
              instanceIdsToBeDeleted.add(instance.getUuid());
            }
          });
          log.info("Flow : [{}], PCF Application : [{}], "
                  + "DB Instance Count : [{}], Running Instance Count: [{}], "
                  + "Instances To Add : [{}], Instances To Delete: [{}]",
              instanceSyncFlow, pcfApplicationName, instancesInDB.size(), latestPcfInstanceInfoMap.keySet().size(),
              instancesToBeAdded.size(), instancesToBeDeleted.size());

          if (!canUpdateInstancesInDb(instanceSyncFlow, infrastructureMapping.getAccountId())) {
            return;
          }

          log.info("Updating Instances in DB via Flow : [{}]", instanceSyncFlow);

          if (isNotEmpty(instanceIdsToBeDeleted)) {
            instanceService.delete(instanceIdsToBeDeleted);
          }

          DeploymentSummary deploymentSummary;
          if (isNotEmpty(instancesToBeAdded)) {
            // newDeploymentInfo would be null in case of sync job.
            if ((newDeploymentSummaries == null || !pcfAppNamesNewDeploymentSummaryMap.containsKey(pcfApplicationName))
                && isNotEmpty(instancesInDB)) {
              Optional<Instance> instanceWithExecutionInfoOptional = getInstanceWithExecutionInfo(instancesInDB);
              if (!instanceWithExecutionInfoOptional.isPresent()) {
                log.warn("Couldn't find an instance from a previous deployment for infra mapping");
                return;
              }
              DeploymentSummary deploymentSummaryFromPrevious =
                  DeploymentSummary.builder().deploymentInfo(PcfDeploymentInfo.builder().build()).build();
              generateDeploymentSummaryFromInstance(
                  instanceWithExecutionInfoOptional.get(), deploymentSummaryFromPrevious);
              deploymentSummary = deploymentSummaryFromPrevious;
            } else {
              deploymentSummary = getDeploymentSummaryForInstanceCreation(
                  pcfAppNamesNewDeploymentSummaryMap.get(pcfApplicationName), rollback);
            }

            instancesToBeAdded.forEach(containerId -> {
              PcfInstanceInfo pcfInstanceInfo = latestPcfInstanceInfoMap.get(containerId);
              // For rollback fetch existing data from deployment summary table
              Instance instance =
                  buildInstanceFromPCFInfo(pcfInfrastructureMapping, pcfInstanceInfo, deploymentSummary);
              instanceService.save(instance);
            });
            log.info("Instances to be added {}", instancesToBeAdded.size());
          }
        }
      });
    }
  }

  private void handleOnDemandRollbackDeployment(Map<String, Instance> instancesInDBMap,
      Map<String, PcfInstanceInfo> latestPcfInstanceInfoMap, String rollbackExecutionId) {
    SetView<String> instancesToBeUpdated =
        Sets.intersection(instancesInDBMap.keySet(), latestPcfInstanceInfoMap.keySet());
    instancesToBeUpdated.forEach(id -> {
      Instance instance = instancesInDBMap.get(id);
      if (instance != null && rollbackExecutionId != null) {
        instance.setLastWorkflowExecutionId(rollbackExecutionId);
        instance.setLastDeployedAt(System.currentTimeMillis());
        instanceService.saveOrUpdate(instance);
      }
    });
  }

  private Map<String, DeploymentSummary> getDeploymentSummaryMap(List<DeploymentSummary> newDeploymentSummaries) {
    if (EmptyPredicate.isEmpty(newDeploymentSummaries)) {
      return Collections.emptyMap();
    }

    Map<String, DeploymentSummary> deploymentSummaryMap = new HashMap<>();
    newDeploymentSummaries.forEach(deploymentSummary
        -> deploymentSummaryMap.put(
            ((PcfDeploymentInfo) deploymentSummary.getDeploymentInfo()).getApplicationName(), deploymentSummary));

    return deploymentSummaryMap;
  }

  private Instance buildInstanceFromPCFInfo(InfrastructureMapping infrastructureMapping,
      PcfInstanceInfo pcfInstanceInfo, DeploymentSummary deploymentSummary) {
    InstanceBuilder builder = buildInstanceBase(null, infrastructureMapping, deploymentSummary);
    builder.pcfInstanceKey(PcfInstanceKey.builder().id(pcfInstanceInfo.getId()).build());
    builder.instanceInfo(pcfInstanceInfo);

    return builder.build();
  }

  private void loadPcfAppNameInstanceMap(String appId, String infraMappingId,
      Multimap<String, Instance> pcfApplicationNameInstanceMap, String applicationNameIfSupplied) {
    List<Instance> instanceListInDBForInfraMapping = getInstances(appId, infraMappingId);
    for (Instance instance : instanceListInDBForInfraMapping) {
      InstanceInfo instanceInfo = instance.getInstanceInfo();
      if (instanceInfo instanceof PcfInstanceInfo) {
        PcfInstanceInfo pcfInstanceInfo = (PcfInstanceInfo) instanceInfo;
        String pcfAppName = pcfInstanceInfo.getPcfApplicationName();
        // todo(aman) this logic needs to be reviewed.
        if (isNotEmpty(applicationNameIfSupplied) && !pcfAppName.equals(applicationNameIfSupplied)) {
          continue;
        }
        pcfApplicationNameInstanceMap.put(pcfAppName, instance);
      } else {
        throw WingsException.builder()
            .message("UnSupported instance deploymentInfo type" + instance.getInstanceType().name())
            .build();
      }
    }
  }

  @Override
  public void handleNewDeployment(
      List<DeploymentSummary> deploymentSummaries, boolean rollback, OnDemandRollbackInfo onDemandRollbackInfo) {
    Multimap<String, Instance> pcfApplicationNameInstanceMap = ArrayListMultimap.create();
    deploymentSummaries.forEach(deploymentSummary -> {
      PcfDeploymentInfo pcfDeploymentInfo = (PcfDeploymentInfo) deploymentSummary.getDeploymentInfo();
      pcfApplicationNameInstanceMap.put(pcfDeploymentInfo.getApplicationName(), null);
    });

    syncInstancesInternal(deploymentSummaries.iterator().next().getAppId(),
        deploymentSummaries.iterator().next().getInfraMappingId(), pcfApplicationNameInstanceMap, deploymentSummaries,
        rollback, onDemandRollbackInfo, null, NEW_DEPLOYMENT);
  }

  @Override
  public Optional<List<DeploymentInfo>> getDeploymentInfo(PhaseExecutionData phaseExecutionData,
      PhaseStepExecutionData phaseStepExecutionData, WorkflowExecution workflowExecution,
      InfrastructureMapping infrastructureMapping, String stateExecutionInstanceId, Artifact artifact) {
    PhaseStepExecutionSummary phaseStepExecutionSummary = phaseStepExecutionData.getPhaseStepExecutionSummary();
    if (phaseStepExecutionSummary == null) {
      if (log.isDebugEnabled()) {
        log.debug("PhaseStepExecutionSummary is null for stateExecutionInstanceId: " + stateExecutionInstanceId);
      }
      return Optional.empty();
    }

    List<StepExecutionSummary> stepExecutionSummaryList = phaseStepExecutionSummary.getStepExecutionSummaryList();
    // This was observed when the "deploy containers" step was executed in rollback and no commands were
    // executed since setup failed.
    if (stepExecutionSummaryList == null) {
      if (log.isDebugEnabled()) {
        log.debug("StepExecutionSummaryList is null for stateExecutionInstanceId: " + stateExecutionInstanceId);
      }
      return Optional.empty();
    }

    for (StepExecutionSummary stepExecutionSummary : stepExecutionSummaryList) {
      if (stepExecutionSummary instanceof DeploymentInfoExtractor) {
        return ((DeploymentInfoExtractor) stepExecutionSummary).extractDeploymentInfo();
      }
    }
    return Optional.empty();
  }

  @Override
  public DeploymentKey generateDeploymentKey(DeploymentInfo deploymentInfo) {
    return PcfDeploymentKey.builder()
        .applicationName(((PcfDeploymentInfo) deploymentInfo).getApplicationName())
        .build();
  }

  @Override
  protected void setDeploymentKey(DeploymentSummary deploymentSummary, DeploymentKey deploymentKey) {
    if (deploymentKey instanceof PcfDeploymentKey) {
      deploymentSummary.setPcfDeploymentKey((PcfDeploymentKey) deploymentKey);
    } else {
      throw WingsException.builder()
          .message("Invalid deploymentKey passed for PcfDeploymentKey" + deploymentKey)
          .build();
    }
  }

  @Override
  public Optional<FeatureName> getFeatureFlagToStopIteratorBasedInstanceSync() {
    return Optional.of(STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_PCF_DEPLOYMENTS);
  }

  @Override
  public Optional<FeatureName> getFeatureFlagToEnablePerpetualTaskForInstanceSync() {
    return Optional.of(MOVE_PCF_INSTANCE_SYNC_TO_PERPETUAL_TASK);
  }

  @Override
  public InstanceSyncPerpetualTaskCreator getInstanceSyncPerpetualTaskCreator() {
    return pcfInstanceSyncPerpetualTaskCreator;
  }

  public int getInstanceCount(DelegateResponseData response) {
    return pcfHelperService.getInstanceCount((CfCommandExecutionResponse) response);
  }

  @Override
  public Status getStatus(InfrastructureMapping infrastructureMapping, DelegateResponseData response) {
    CfCommandExecutionResponse cfCommandExecutionResponse = (CfCommandExecutionResponse) response;
    boolean isSuccess = cfCommandExecutionResponse.getCommandExecutionStatus() == SUCCESS;
    String errorMsg = isSuccess ? null : cfCommandExecutionResponse.getErrorMessage();
    boolean canRetry = true;

    CfInstanceSyncResponse cfInstanceSyncResponse =
        (CfInstanceSyncResponse) cfCommandExecutionResponse.getPcfCommandResponse();
    if (isSuccess && getInstanceCount(response) == 0) {
      log.info("Got 0 instances. Infrastructure Mapping : [{}]. Application Name : [{}]",
          infrastructureMapping.getUuid(), cfInstanceSyncResponse.getName());
      canRetry = false;
    } else {
      try {
        pcfHelperService.validatePcfInstanceSyncResponse(cfInstanceSyncResponse.getName(),
            cfInstanceSyncResponse.getOrganization(), cfInstanceSyncResponse.getSpace(), cfCommandExecutionResponse);
      } catch (PcfAppNotFoundException ex) {
        log.info(String.format("PCF Application : [%s] not found", cfInstanceSyncResponse.getName()), ex);
        canRetry = false;
      } catch (Exception ex) {
        log.info(String.format("Unexpected Error. PCF Application : [%s]", cfInstanceSyncResponse.getName()), ex);
      }
    }

    return Status.builder().success(isSuccess).errorMessage(errorMsg).retryable(canRetry).build();
  }
}
