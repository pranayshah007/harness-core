/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.beans.FeatureName.AZURE_VMSS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.validation.Validator.notNullCheck;

import static com.google.common.collect.Sets.difference;
import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toSet;

import io.harness.azure.model.AzureVMData;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.task.azure.response.AzureVMSSListVMDataResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.api.AzureVMSSDeploymentInfo;
import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.PhaseExecutionData;
import software.wings.api.PhaseStepExecutionData;
import software.wings.api.ondemandrollback.OnDemandRollbackInfo;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureVMSSInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceBuilder;
import software.wings.beans.infrastructure.instance.info.AzureVMSSInstanceInfo;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.key.deployment.AzureVMSSDeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.DeploymentKey;
import software.wings.service.AzureVMSSInstanceSyncPerpetualTaskCreator;
import software.wings.service.InstanceSyncPerpetualTaskCreator;
import software.wings.service.intfc.azure.manager.AzureVMSSHelperServiceManager;
import software.wings.sm.PhaseStepExecutionSummary;
import software.wings.sm.StepExecutionSummary;
import software.wings.sm.states.azure.AzureVMSSDeployExecutionSummary;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets.SetView;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class AzureVMSSInstanceHandler extends InstanceHandler implements InstanceSyncByPerpetualTaskHandler {
  @Inject private AzureVMSSHelperServiceManager azureVMSSHelperServiceManager;
  @Inject private AzureVMSSInstanceSyncPerpetualTaskCreator perpetualTaskCreator;

  @Override
  public void syncInstances(String appId, String infraMappingId, InstanceSyncFlow instanceSyncFlow) {
    AzureVMSSInfrastructureMapping infrastructureMapping = getInfraMapping(appId, infraMappingId);
    SettingAttribute settingAttribute = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    notNullCheck("Cloud provider is null", settingAttribute);
    AzureConfig azureConfig = (AzureConfig) settingAttribute.getValue();
    List<EncryptedDataDetail> encryptedDataDetails = secretManager.getEncryptionDetails(azureConfig, null, null);
    Multimap<String, Instance> vmssIdToInstancesInDbMap = getCurrentInstancesInDb(appId, infraMappingId);
    Set<String> vmssIds = vmssIdToInstancesInDbMap.keySet();
    if (isEmpty(vmssIds)) {
      return;
    }

    vmssIds.forEach(vmssId -> {
      Map<String, AzureVMData> latestVMIdToVMDataMap = getLatestInstancesForVMSS(azureConfig, encryptedDataDetails,
          infrastructureMapping.getSubscriptionId(), infrastructureMapping.getResourceGroupName(), vmssId, appId);
      syncInstancesForVMSS(vmssId, vmssIdToInstancesInDbMap.get(vmssId), latestVMIdToVMDataMap, false, null,
          infrastructureMapping, instanceSyncFlow);
    });
  }

  @Override
  public void handleNewDeployment(
      List<DeploymentSummary> deploymentSummaries, boolean rollback, OnDemandRollbackInfo onDemandRollbackInfo) {
    if (isEmpty(deploymentSummaries)) {
      return;
    }

    String appId = deploymentSummaries.iterator().next().getAppId();
    String infraMappingId = deploymentSummaries.iterator().next().getInfraMappingId();
    Multimap<String, Instance> vmssIdToInstanceInDbMap = getCurrentInstancesInDb(appId, infraMappingId);

    AzureVMSSInfrastructureMapping infrastructureMapping = getInfraMapping(appId, infraMappingId);
    SettingAttribute settingAttribute = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    notNullCheck("Cloud provider is null", settingAttribute);
    AzureConfig azureConfig = (AzureConfig) settingAttribute.getValue();
    List<EncryptedDataDetail> encryptedDataDetails = secretManager.getEncryptionDetails(azureConfig, null, null);

    Set<String> allVMSSIds = new HashSet<>();
    allVMSSIds.addAll(vmssIdToInstanceInDbMap.keySet());
    allVMSSIds.addAll(
        deploymentSummaries.stream().map(summary -> summary.getAzureVMSSDeploymentKey().getVmssId()).collect(toSet()));

    if (isNotEmpty(allVMSSIds)) {
      allVMSSIds.forEach(vmssId -> {
        Map<String, AzureVMData> latestVMIdToVMDataMap = getLatestInstancesForVMSS(azureConfig, encryptedDataDetails,
            infrastructureMapping.getSubscriptionId(), infrastructureMapping.getResourceGroupName(), vmssId, appId);
        DeploymentSummary eligibleDeploymentSummary =
            deploymentSummaries.stream()
                .filter(summary -> vmssId.equals(summary.getAzureVMSSDeploymentKey().getVmssId()))
                .findFirst()
                .orElse(null);
        syncInstancesForVMSS(vmssId, vmssIdToInstanceInDbMap.get(vmssId), latestVMIdToVMDataMap, rollback,
            eligibleDeploymentSummary, infrastructureMapping, InstanceSyncFlow.NEW_DEPLOYMENT);
      });
    }
  }

  @Override
  public void processInstanceSyncResponseFromPerpetualTask(
      InfrastructureMapping infrastructureMapping, DelegateResponseData response) {
    AzureVMSSTaskExecutionResponse azureVMSSTaskExecutionResponse = (AzureVMSSTaskExecutionResponse) response;
    AzureVMSSListVMDataResponse azureVMSSTaskResponse =
        (AzureVMSSListVMDataResponse) azureVMSSTaskExecutionResponse.getAzureVMSSTaskResponse();
    List<AzureVMData> vmData = azureVMSSTaskResponse.getVmData();
    Multimap<String, Instance> vmssIdToInstancesInDbMap =
        getCurrentInstancesInDb(infrastructureMapping.getAppId(), infrastructureMapping.getUuid());
    Map<String, AzureVMData> vmDataMap = new HashMap<>();
    if (isNotEmpty(vmData)) {
      vmData.forEach(data -> vmDataMap.put(data.getId(), data));
    }
    String vmssId = azureVMSSTaskResponse.getVmssId();
    Collection<Instance> currentInstancesInDb = vmssIdToInstancesInDbMap.get(vmssId);

    syncInstancesForVMSS(vmssId, currentInstancesInDb, vmDataMap, false, null,
        (AzureVMSSInfrastructureMapping) infrastructureMapping, InstanceSyncFlow.PERPETUAL_TASK);
  }

  @Override
  public FeatureName getFeatureFlagToEnablePerpetualTaskForInstanceSync() {
    return AZURE_VMSS;
  }

  @Override
  public InstanceSyncPerpetualTaskCreator getInstanceSyncPerpetualTaskCreator() {
    return perpetualTaskCreator;
  }

  @Override
  public Status getStatus(InfrastructureMapping infrastructureMapping, DelegateResponseData response) {
    AzureVMSSTaskExecutionResponse azureVMSSTaskExecutionResponse = (AzureVMSSTaskExecutionResponse) response;
    AzureVMSSListVMDataResponse azureVMSSTaskResponse =
        (AzureVMSSListVMDataResponse) azureVMSSTaskExecutionResponse.getAzureVMSSTaskResponse();
    List<AzureVMData> vmData = azureVMSSTaskResponse.getVmData();

    boolean success = SUCCESS == azureVMSSTaskExecutionResponse.getCommandExecutionStatus();
    boolean deleteTask = success && isEmpty(vmData);
    String errorMessage = success ? null : azureVMSSTaskExecutionResponse.getErrorMessage();
    return Status.builder().success(success).errorMessage(errorMessage).retryable(!deleteTask).build();
  }

  private Map<String, AzureVMData> getLatestInstancesForVMSS(AzureConfig azureConfig,
      List<EncryptedDataDetail> encryptedDataDetails, String subscriptionId, String resourceGroupName, String vmssId,
      String appId) {
    Map<String, AzureVMData> vmDataMap = new HashMap<>();
    List<AzureVMData> azureVMData = azureVMSSHelperServiceManager.listVMSSVirtualMachines(
        azureConfig, subscriptionId, resourceGroupName, vmssId, encryptedDataDetails, appId);
    if (isNotEmpty(azureVMData)) {
      azureVMData.forEach(data -> vmDataMap.put(data.getId(), data));
    }
    return vmDataMap;
  }

  private AzureVMSSInfrastructureMapping getInfraMapping(String appId, String infraMappingId) {
    InfrastructureMapping infrastructureMapping = infraMappingService.get(appId, infraMappingId);
    notNullCheck(format("Infra mapping is null for id: [%s]", infraMappingId), infrastructureMapping);
    if (!(infrastructureMapping instanceof AzureVMSSInfrastructureMapping)) {
      String msg = format("Incompatible infra mapping type. Expecting Azure VMSS type. Found: [%s]",
          infrastructureMapping.getInfraMappingType());
      log.error(msg);
      throw new InvalidRequestException(msg);
    }
    return (AzureVMSSInfrastructureMapping) infrastructureMapping;
  }

  private void syncInstancesForVMSS(String vmssId, Collection<Instance> currentInstancesInDb,
      Map<String, AzureVMData> latestVMIdToVMDataMap, boolean rollback, DeploymentSummary deploymentSummary,
      AzureVMSSInfrastructureMapping infrastructureMapping, InstanceSyncFlow instanceSyncFlow) {
    if (!canUpdateInstancesInDb(instanceSyncFlow, infrastructureMapping.getAccountId())) {
      return;
    }

    Map<String, Instance> vmIdToInstanceInDbMap = getVMIdToInstanceInDbMap(currentInstancesInDb);

    handleAzureVMDelete(vmIdToInstanceInDbMap, latestVMIdToVMDataMap);

    Set<String> vmIdsToBeAdded = difference(latestVMIdToVMDataMap.keySet(), vmIdToInstanceInDbMap.keySet());
    if (isNotEmpty(vmIdsToBeAdded)) {
      DeploymentSummary finalDeploymentSummary;
      if (deploymentSummary == null && isNotEmpty(currentInstancesInDb)) {
        Optional<Instance> instanceWithExecutionInfoOptional = getInstanceWithExecutionInfo(currentInstancesInDb);
        if (!instanceWithExecutionInfoOptional.isPresent()) {
          log.warn("Couldn't find an instance from a previous deployment for inframapping: [{}]",
              infrastructureMapping.getUuid());
          return;
        }
        DeploymentSummary deploymentSummaryFromPrevious =
            DeploymentSummary.builder().deploymentInfo(AzureVMSSDeploymentInfo.builder().build()).build();
        generateDeploymentSummaryFromInstance(instanceWithExecutionInfoOptional.get(), deploymentSummaryFromPrevious);
        finalDeploymentSummary = deploymentSummaryFromPrevious;
      } else {
        finalDeploymentSummary = getDeploymentSummaryForInstanceCreation(deploymentSummary, rollback);
      }
      vmIdsToBeAdded.forEach(vmId -> {
        AzureVMData azureVMData = latestVMIdToVMDataMap.get(vmId);
        InstanceBuilder instanceBuilder = buildInstanceBase(null, infrastructureMapping, finalDeploymentSummary);
        InstanceInfo instanceInfo = toAzureVMSSInstanceInfo(vmssId, azureVMData);
        instanceBuilder.instanceInfo(instanceInfo);
        Instance instance = instanceBuilder.build();
        instanceService.save(instance);
      });

      log.info("Instances to be added {}", vmIdsToBeAdded.size());
    }
  }

  private AzureVMSSInstanceInfo toAzureVMSSInstanceInfo(String vmssId, AzureVMData azureVMData) {
    return AzureVMSSInstanceInfo.builder()
        .vmssId(vmssId)
        .azureVMId(azureVMData.getId())
        .host(azureVMData.getIp())
        .instanceType(azureVMData.getSize())
        .state(azureVMData.getPowerState())
        .build();
  }

  private void handleAzureVMDelete(
      Map<String, Instance> instancesInDbMap, Map<String, AzureVMData> latestVMIdToVMDataMap) {
    SetView<String> vmIdsToBeDeleted = difference(instancesInDbMap.keySet(), latestVMIdToVMDataMap.keySet());
    Set<String> instanceIdsToBeDeleted = new HashSet<>();
    vmIdsToBeDeleted.forEach(vmId -> {
      Instance instance = instancesInDbMap.get(vmId);
      if (instance != null) {
        instanceIdsToBeDeleted.add(instance.getUuid());
      }
    });
    if (isNotEmpty(instanceIdsToBeDeleted)) {
      instanceService.delete(instanceIdsToBeDeleted);
      log.info("Instances to be deleted {}", instanceIdsToBeDeleted.size());
    }
  }

  private Multimap<String, Instance> getCurrentInstancesInDb(String appId, String infraMappingId) {
    Multimap<String, Instance> vmssIdToInstancesMap = ArrayListMultimap.create();
    List<Instance> instances = getInstances(appId, infraMappingId);
    if (isNotEmpty(instances)) {
      instances.forEach(instance -> {
        InstanceInfo instanceInfo = instance.getInstanceInfo();
        if (instanceInfo instanceof AzureVMSSInstanceInfo) {
          AzureVMSSInstanceInfo azureVMSSInstanceInfo = (AzureVMSSInstanceInfo) instanceInfo;
          vmssIdToInstancesMap.put(azureVMSSInstanceInfo.getVmssId(), instance);
        }
      });
    }
    return vmssIdToInstancesMap;
  }

  private Map<String, Instance> getVMIdToInstanceInDbMap(Collection<Instance> currentInstancesInDb) {
    if (isEmpty(currentInstancesInDb)) {
      return emptyMap();
    }

    Map<String, Instance> vmIdToInstanceMap = new HashMap<>();
    currentInstancesInDb.forEach(instance -> {
      if (instance != null) {
        InstanceInfo instanceInfo = instance.getInstanceInfo();
        if (instanceInfo instanceof AzureVMSSInstanceInfo) {
          vmIdToInstanceMap.put(((AzureVMSSInstanceInfo) instanceInfo).getAzureVMId(), instance);
        }
      }
    });
    return vmIdToInstanceMap;
  }

  @Override
  public Optional<List<DeploymentInfo>> getDeploymentInfo(PhaseExecutionData phaseExecutionData,
      PhaseStepExecutionData phaseStepExecutionData, WorkflowExecution workflowExecution,
      InfrastructureMapping infrastructureMapping, String stateExecutionInstanceId, Artifact artifact) {
    PhaseStepExecutionSummary phaseStepExecutionSummary = phaseStepExecutionData.getPhaseStepExecutionSummary();
    if (phaseStepExecutionSummary != null) {
      Optional<StepExecutionSummary> stepExecutionSummaryOptional =
          phaseStepExecutionSummary.getStepExecutionSummaryList()
              .stream()
              .filter(stepExecutionSummary -> stepExecutionSummary instanceof AzureVMSSDeployExecutionSummary)
              .findFirst();
      if (stepExecutionSummaryOptional.isPresent()) {
        AzureVMSSDeployExecutionSummary azureVMSSDeployExecutionSummary =
            (AzureVMSSDeployExecutionSummary) stepExecutionSummaryOptional.get();
        List<DeploymentInfo> deploymentInfoList = new ArrayList<>();
        if (isNotEmpty(azureVMSSDeployExecutionSummary.getNewVirtualMachineScaleSetId())) {
          deploymentInfoList.add(AzureVMSSDeploymentInfo.builder()
                                     .vmssId(azureVMSSDeployExecutionSummary.getNewVirtualMachineScaleSetId())
                                     .vmssName(azureVMSSDeployExecutionSummary.getNewVirtualMachineScaleSetName())
                                     .build());
        }
        if (isNotEmpty(azureVMSSDeployExecutionSummary.getOldVirtualMachineScaleSetId())) {
          deploymentInfoList.add(AzureVMSSDeploymentInfo.builder()
                                     .vmssId(azureVMSSDeployExecutionSummary.getOldVirtualMachineScaleSetId())
                                     .vmssName(azureVMSSDeployExecutionSummary.getOldVirtualMachineScaleSetName())
                                     .build());
        }
        return of(deploymentInfoList);
      } else {
        throw new InvalidRequestException(
            format("Step execution summary null for Azure VMSS Deploy Step for workflow: [%s]",
                workflowExecution.normalizedName()));
      }
    } else {
      throw new InvalidRequestException(
          format("Phase step execution summary null for Azure VMSS Deploy for workflow: [%s]",
              workflowExecution.normalizedName()));
    }
  }

  @Override
  public DeploymentKey generateDeploymentKey(DeploymentInfo deploymentInfo) {
    AzureVMSSDeploymentInfo azureVMSSDeploymentInfo = (AzureVMSSDeploymentInfo) deploymentInfo;
    return AzureVMSSDeploymentKey.builder()
        .vmssId(azureVMSSDeploymentInfo.getVmssId())
        .vmssName(azureVMSSDeploymentInfo.getVmssName())
        .build();
  }

  @Override
  protected void setDeploymentKey(DeploymentSummary deploymentSummary, DeploymentKey deploymentKey) {
    if (deploymentKey instanceof AzureVMSSDeploymentKey) {
      deploymentSummary.setAzureVMSSDeploymentKey((AzureVMSSDeploymentKey) deploymentKey);
    } else {
      throw new InvalidRequestException(
          format("Invalid deploymentKey passed for AzureVMSS deployment: [%s]", deploymentKey.getClass().getName()));
    }
  }

  @Override
  public FeatureName getFeatureFlagToStopIteratorBasedInstanceSync() {
    return AZURE_VMSS;
  }
}
