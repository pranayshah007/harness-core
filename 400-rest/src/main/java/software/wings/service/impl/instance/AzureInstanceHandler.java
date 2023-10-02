/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.validation.Validator.notNullCheck;

import static java.util.stream.Collectors.toSet;

import io.harness.beans.FeatureName;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.PhaseExecutionData;
import software.wings.api.PhaseStepExecutionData;
import software.wings.api.ondemandrollback.OnDemandRollbackInfo;
import software.wings.beans.AzureInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.infrastructure.Host;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.info.AzureVMSSInstanceInfo;
import software.wings.beans.infrastructure.instance.info.HostInstanceInfo;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.key.deployment.DeploymentKey;
import software.wings.helpers.ext.azure.AzureHelperService;
import software.wings.persistence.artifact.Artifact;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
@Slf4j
public class AzureInstanceHandler extends InstanceHandler {
  @Inject protected AzureHelperService azureHelperService;

  @Override
  public void syncInstances(String appId, String infraMappingId, InstanceSyncFlow instanceSyncFlow) {
    InfrastructureMapping infrastructureMapping = infraMappingService.get(appId, infraMappingId);
    notNullCheck("Infra mapping is null for id:" + infraMappingId, infrastructureMapping);

    if (!(infrastructureMapping instanceof AzureInfrastructureMapping)) {
      String msg = "Incompatible infra mapping type. Expecting AZURE_INFRA type. Found:"
          + infrastructureMapping.getInfraMappingType();
      log.error(msg);
      throw WingsException.builder().message(msg).build();
    }

    AzureInfrastructureMapping azureInfrastructureMapping = (AzureInfrastructureMapping) infrastructureMapping;
    Map<String, Instance> azureInstanceIdInstanceMap = new HashMap<>();

    loadInstanceMapBasedOnType(appId, infraMappingId, azureInstanceIdInstanceMap);

    log.info("Found {} azure instances for app {}", azureInstanceIdInstanceMap.size(), appId);

    SettingAttribute cloudProviderSetting = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    List<EncryptedDataDetail> encryptedDataDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) cloudProviderSetting.getValue(), null, null);

    if (azureInstanceIdInstanceMap.size() > 0) {
      handleInstanceSync(azureInstanceIdInstanceMap, encryptedDataDetails, azureInfrastructureMapping);
    }
  }

  protected void loadInstanceMapBasedOnType(
      String appId, String infraMappingId, Map<String, Instance> azureInstanceIdInstanceMap) {
    List<Instance> instanceList = getInstances(appId, infraMappingId);
    instanceList.forEach(instance -> {
      InstanceInfo instanceInfo = instance.getInstanceInfo();
      if (instanceInfo instanceof HostInstanceInfo) {
        HostInstanceInfo hostInstanceInfo = (HostInstanceInfo) instanceInfo;
        String hostName = hostInstanceInfo.getHostName();
        azureInstanceIdInstanceMap.put(hostName, instance);
      }

      if (instanceInfo instanceof AzureVMSSInstanceInfo) {
        AzureVMSSInstanceInfo hostInstanceInfo = (AzureVMSSInstanceInfo) instanceInfo;
        String hostName = hostInstanceInfo.getHost();
        azureInstanceIdInstanceMap.put(hostName, instance);
      }
    });
  }

  protected void handleInstanceSync(Map<String, Instance> azureInstanceIdInstanceMap,
      List<EncryptedDataDetail> encryptedDataDetails, AzureInfrastructureMapping azureInfrastructureMapping) {
    if (azureInstanceIdInstanceMap.size() > 0) {
      SettingAttribute settingAttribute = settingsService.get(azureInfrastructureMapping.getComputeProviderSettingId());

      List<Host> activeHostList =
          azureHelperService.listHosts(azureInfrastructureMapping, settingAttribute, encryptedDataDetails, null);
      deleteRunningInstancesFromMap(azureInstanceIdInstanceMap, activeHostList);
    }
  }

  private void deleteRunningInstancesFromMap(
      Map<String, Instance> azureInstanceIdInstanceMap, List<Host> activeHostList) {
    Instance azureInstance = azureInstanceIdInstanceMap.values().iterator().next();
    log.info(
        "Total no of Azure instances found in DB for InfraMappingId: {} and AppId: {}: {}, No of Running instances found in azure:{}",
        azureInstance.getInfraMappingId(), azureInstance.getAppId(), azureInstanceIdInstanceMap.size(),
        activeHostList.size());

    azureInstanceIdInstanceMap.keySet().removeAll(activeHostList.stream().map(Host::getHostName).collect(toSet()));

    Set<String> instanceIdsToBeDeleted = azureInstanceIdInstanceMap.entrySet()
                                             .stream()
                                             .map(entry -> entry.getValue().getUuid())
                                             .collect(Collectors.toSet());

    if (isNotEmpty(instanceIdsToBeDeleted)) {
      log.debug("Instances to be deleted {}", instanceIdsToBeDeleted.size());
      instanceService.delete(instanceIdsToBeDeleted);
    }
  }

  @Override
  public void handleNewDeployment(
      List<DeploymentSummary> deploymentSummaries, boolean rollback, OnDemandRollbackInfo onDemandRollbackInfo) {
    // Not Implemented
  }

  @Override
  public Optional<FeatureName> getFeatureFlagToStopIteratorBasedInstanceSync() {
    return Optional.of(FeatureName.STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AZURE_INFRA_DEPLOYMENTS);
  }

  @Override
  public Optional<List<DeploymentInfo>> getDeploymentInfo(PhaseExecutionData phaseExecutionData,
      PhaseStepExecutionData phaseStepExecutionData, WorkflowExecution workflowExecution,
      InfrastructureMapping infrastructureMapping, String stateExecutionInstanceId, Artifact artifact) {
    log.warn("Deployments should be handled at InstanceHelper for azure type.");
    return Optional.empty();
  }

  @Override
  public DeploymentKey generateDeploymentKey(DeploymentInfo deploymentInfo) {
    return null;
  }

  @Override
  protected void setDeploymentKey(DeploymentSummary deploymentSummary, DeploymentKey deploymentKey) {
    // Do Nothing
  }
}
