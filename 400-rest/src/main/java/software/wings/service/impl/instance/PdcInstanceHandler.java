/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.PDC_PERPETUAL_TASK;
import static io.harness.beans.FeatureName.STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_PDC_DEPLOYMENTS;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.PhaseExecutionData;
import software.wings.api.PhaseStepExecutionData;
import software.wings.api.ondemandrollback.OnDemandRollbackInfo;
import software.wings.beans.Base;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.HostReachabilityInfo;
import software.wings.beans.HostValidationTaskParameters;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.PhysicalInfrastructureMapping;
import software.wings.beans.PhysicalInfrastructureMappingBase;
import software.wings.beans.PhysicalInfrastructureMappingWinRm;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.info.PhysicalHostInstanceInfo;
import software.wings.beans.infrastructure.instance.key.deployment.DeploymentKey;
import software.wings.service.InstanceSyncPerpetualTaskCreator;
import software.wings.service.PdcInstanceSyncPerpetualTaskCreator;
import software.wings.service.impl.aws.model.response.HostReachabilityResponse;
import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class PdcInstanceHandler extends InstanceHandler implements InstanceSyncByPerpetualTaskHandler {
  @Inject DelegateService delegateService;
  @Inject private PdcInstanceSyncPerpetualTaskCreator perpetualTaskCreator;

  @Override
  public void syncInstances(String appId, String infraMappingId, InstanceSyncFlow instanceSyncFlow) {
    log.info("[PDC Instance sync]: InfraMappingId : [{}] flow: {}", infraMappingId, instanceSyncFlow);
    InfrastructureMapping infrastructureMapping = infraMappingService.get(appId, infraMappingId);
    if (!(infrastructureMapping instanceof PhysicalInfrastructureMappingBase)) {
      String msg = "Incompatible infra mapping type. Expecting PhysicalInfrastructureMappingBase, found:"
          + infrastructureMapping.getInfraMappingType();
      log.error(msg);
      throw WingsException.builder().message(msg).build();
    }
    boolean canUpdateDb = canUpdateInstancesInDb(instanceSyncFlow, infrastructureMapping.getAccountId());
    if (!canUpdateDb) {
      return;
    }

    SettingAttribute settingAttribute;
    List<EncryptedDataDetail> encryptedDataDetails;

    if (infrastructureMapping instanceof PhysicalInfrastructureMapping) {
      PhysicalInfrastructureMapping mapping = (PhysicalInfrastructureMapping) infrastructureMapping;
      settingAttribute = settingsService.get(mapping.getHostConnectionAttrs());
      HostConnectionAttributes value = (HostConnectionAttributes) settingAttribute.getValue();
      encryptedDataDetails = secretManager.getEncryptionDetails(value, null, null);
    } else if (infrastructureMapping instanceof PhysicalInfrastructureMappingWinRm) {
      PhysicalInfrastructureMappingWinRm mapping = (PhysicalInfrastructureMappingWinRm) infrastructureMapping;
      settingAttribute = settingsService.get(mapping.getWinRmConnectionAttributes());
      WinRmConnectionAttributes value = (WinRmConnectionAttributes) settingAttribute.getValue();
      encryptedDataDetails = secretManager.getEncryptionDetails(value, null, null);
    } else {
      String msg = "Unexpected infra mapping type found:" + infrastructureMapping.getInfraMappingType();
      log.error(msg);
      throw WingsException.builder().message(msg).build();
    }

    List<Instance> instances = getInstances(appId, infraMappingId);
    if (EmptyPredicate.isEmpty(instances)) {
      return;
    }

    List<String> hosts =
        instances.stream()
            .filter(i -> null != i.getInstanceInfo() && i.getInstanceInfo() instanceof PhysicalHostInstanceInfo)
            .map(i -> ((PhysicalHostInstanceInfo) i.getInstanceInfo()).getHostName())
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    updateInstances(hosts, settingAttribute, encryptedDataDetails, instances, infraMappingId, instanceSyncFlow);
  }

  @Override
  public void handleNewDeployment(
      List<DeploymentSummary> deploymentSummaries, boolean rollback, OnDemandRollbackInfo onDemandRollbackInfo) {
    throw WingsException.builder().message("Deployments should be handled at InstanceHelper for PDC ssh type.").build();
  }

  @Override
  public Optional<FeatureName> getFeatureFlagToStopIteratorBasedInstanceSync() {
    return Optional.of(STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_PDC_DEPLOYMENTS);
  }

  @Override
  public Optional<List<DeploymentInfo>> getDeploymentInfo(PhaseExecutionData phaseExecutionData,
      PhaseStepExecutionData phaseStepExecutionData, WorkflowExecution workflowExecution,
      InfrastructureMapping infrastructureMapping, String stateExecutionInstanceId, Artifact artifact) {
    throw WingsException.builder()
        .message("Deployments should be handled at InstanceHelper for aws ssh type except for with ASG.")
        .build();
  }

  @Override
  public DeploymentKey generateDeploymentKey(DeploymentInfo deploymentInfo) {
    return null;
  }

  @Override
  protected void setDeploymentKey(DeploymentSummary deploymentSummary, DeploymentKey deploymentKey) {
    // do nothing
  }

  @Override
  public Optional<FeatureName> getFeatureFlagToEnablePerpetualTaskForInstanceSync() {
    return Optional.of(PDC_PERPETUAL_TASK);
  }

  @Override
  public InstanceSyncPerpetualTaskCreator getInstanceSyncPerpetualTaskCreator() {
    return perpetualTaskCreator;
  }

  @Override
  public void processInstanceSyncResponseFromPerpetualTask(
      InfrastructureMapping infrastructureMapping, DelegateResponseData response) {
    syncInstances(infrastructureMapping.getAppId(), infrastructureMapping.getUuid(), InstanceSyncFlow.PERPETUAL_TASK);
  }

  @Override
  public Status getStatus(InfrastructureMapping infrastructureMapping, DelegateResponseData response) {
    HostReachabilityResponse reachabilityResponse = (HostReachabilityResponse) response;
    // If all instances are not reachable via same Delegate?
    boolean canDeleteTask = true;

    if (isNotEmpty(reachabilityResponse.getHostReachabilityInfoList())) {
      canDeleteTask =
          reachabilityResponse.getHostReachabilityInfoList().stream().noneMatch(HostReachabilityInfo::getReachable);

      if (canDeleteTask) {
        List<String> hosts = reachabilityResponse.getHostReachabilityInfoList()
                                 .stream()
                                 .map(HostReachabilityInfo::getHostName)
                                 .collect(Collectors.toList());
        log.info("[PDC Instance sync]: Hosts {} unreachable. Infrastructure Mapping : [{}]", hosts,
            infrastructureMapping.getUuid());
      }
    }

    boolean success = reachabilityResponse.getExecutionStatus() == ExecutionStatus.SUCCESS;
    String errorMessage = success ? null : reachabilityResponse.getErrorMessage();
    return Status.builder().success(success).errorMessage(errorMessage).retryable(!canDeleteTask).build();
  }

  private void updateInstances(List<String> hostNames, SettingAttribute settingAttribute,
      List<EncryptedDataDetail> encryptedDataDetails, List<Instance> instances, String infraMappingId,
      InstanceSyncFlow instanceSyncFlow) {
    Map<String, Boolean> reachableMap = checkReachability(hostNames, settingAttribute, encryptedDataDetails);

    Set<String> instancesToRemove = instances.stream()
                                        .filter(i -> {
                                          String hostName = i.getHostInstanceKey().getHostName();
                                          return !reachableMap.containsKey(hostName) || !reachableMap.get(hostName);
                                        })
                                        .map(Base::getUuid)
                                        .collect(Collectors.toSet());

    Set<String> hostsToRemove = instances.stream()
                                    .filter(i -> instancesToRemove.contains(i.getUuid()))
                                    .map(i -> i.getHostInstanceKey().getHostName())
                                    .collect(Collectors.toSet());
    log.info("[PDC Instance sync]: infraMappingId: [{}], hosts removed: {}, flow: {}", infraMappingId, hostsToRemove,
        instanceSyncFlow);
    instanceService.delete(instancesToRemove);
  }

  private Map<String, Boolean> checkReachability(
      List<String> hostNames, SettingAttribute settingAttribute, List<EncryptedDataDetail> encryptedDataDetails) {
    Map<String, Boolean> result = new HashMap<>();
    boolean activeInstanceFound = true;
    List<String> filteredHostNames = new ArrayList<>(hostNames);

    while (activeInstanceFound && !EmptyPredicate.isEmpty(filteredHostNames)) {
      List<HostReachabilityInfo> hostReachabilityInfos =
          executeTask(settingAttribute.getAccountId(), filteredHostNames, settingAttribute, encryptedDataDetails);
      activeInstanceFound = !EmptyPredicate.isEmpty(hostReachabilityInfos)
          && hostReachabilityInfos.stream().anyMatch(r -> Boolean.TRUE.equals(r.getReachable()));

      if (activeInstanceFound) {
        hostReachabilityInfos.stream()
            .filter(r -> Boolean.TRUE.equals(r.getReachable()))
            .forEach(r -> result.put(r.getHostName(), true));
        filteredHostNames.removeAll(result.keySet());
      }
    }

    filteredHostNames.forEach(h -> result.put(h, false));

    return result;
  }

  private List<HostReachabilityInfo> executeTask(String accountId, List<String> hostNames,
      SettingAttribute settingAttribute, List<EncryptedDataDetail> encryptedDataDetails) {
    HostValidationTaskParameters parameters = HostValidationTaskParameters.builder()
                                                  .hostNames(hostNames)
                                                  .connectionSetting(settingAttribute)
                                                  .encryptionDetails(encryptedDataDetails)
                                                  .checkOnlyReachability(true)
                                                  .checkOr(true)
                                                  .build();

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(accountId)
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
                                    .data(TaskData.builder()
                                              .async(false)
                                              .taskType(TaskType.HOST_VALIDATION.name())
                                              .parameters(new Object[] {parameters})
                                              .timeout(TimeUnit.MINUTES.toMillis(10))
                                              .build())
                                    .build();

    try {
      DelegateResponseData notifyResponseData = delegateService.executeTask(delegateTask);
      if (notifyResponseData instanceof HostReachabilityResponse) {
        HostReachabilityResponse value = (HostReachabilityResponse) notifyResponseData;
        return value.getHostReachabilityInfoList();
      } else {
        return Collections.emptyList();
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new InvalidRequestException(ex.getMessage(), USER);
    }
  }
}
