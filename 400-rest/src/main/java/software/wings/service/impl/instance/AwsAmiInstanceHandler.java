/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_AMI_DEPLOYMENTS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.validation.Validator.notNullCheck;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.AmiStepExecutionSummary;
import software.wings.api.AwsAutoScalingGroupDeploymentInfo;
import software.wings.api.ContainerServiceData;
import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.PhaseExecutionData;
import software.wings.api.PhaseStepExecutionData;
import software.wings.api.ondemandrollback.OnDemandRollbackInfo;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.AwsConfig;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.key.deployment.AwsAmiDeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.DeploymentKey;
import software.wings.service.AwsAmiInstanceSyncPerpetualTaskCreator;
import software.wings.service.InstanceSyncPerpetualTaskCreator;
import software.wings.service.impl.aws.model.AwsAsgListInstancesResponse;
import software.wings.sm.PhaseStepExecutionSummary;
import software.wings.sm.StepExecutionSummary;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * @author rktummala on 02/02/18
 */
@Singleton
@Slf4j
@OwnedBy(CDP)
public class AwsAmiInstanceHandler extends AwsInstanceHandler implements InstanceSyncByPerpetualTaskHandler {
  @Inject private AwsAmiInstanceSyncPerpetualTaskCreator perpetualTaskCreator;

  @Override
  public void syncInstances(String appId, String infraMappingId, InstanceSyncFlow instanceSyncFlow) {
    // Key - Auto scaling group with revision, Value - Instance
    Multimap<String, Instance> asgInstanceMap = ArrayListMultimap.create();
    syncInstancesInternal(appId, infraMappingId, asgInstanceMap, null, false, null, instanceSyncFlow);
  }

  private void syncInstancesInternal(String appId, String infraMappingId, Multimap<String, Instance> asgInstanceMap,
      List<DeploymentSummary> newDeploymentSummaries, boolean rollback,
      AwsAsgListInstancesResponse asgListInstancesResponse, InstanceSyncFlow instanceSyncFlow) {
    Map<String, DeploymentSummary> asgNamesDeploymentSummaryMap = getDeploymentSummaryMap(newDeploymentSummaries);

    InfrastructureMapping infrastructureMapping = infraMappingService.get(appId, infraMappingId);
    notNullCheck("Infra mapping is null for id:" + infraMappingId, infrastructureMapping);
    if (!(infrastructureMapping instanceof AwsAmiInfrastructureMapping)) {
      String msg =
          "Incompatible infra mapping type. Expecting ami type. Found:" + infrastructureMapping.getInfraMappingType();
      log.error(msg);
      throw WingsException.builder().message(msg).build();
    }

    // key - ec2 instance id, value - instance
    Map<String, Instance> ec2InstanceIdInstanceMap = new HashMap<>();

    loadInstanceMapBasedOnType(appId, infraMappingId, asgInstanceMap, ec2InstanceIdInstanceMap);

    SettingAttribute cloudProviderSetting = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    AwsConfig awsConfig = (AwsConfig) cloudProviderSetting.getValue();
    List<EncryptedDataDetail> encryptedDataDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) cloudProviderSetting.getValue(), null, null);

    AwsAmiInfrastructureMapping amiInfraMapping = (AwsAmiInfrastructureMapping) infrastructureMapping;
    String region = amiInfraMapping.getRegion();

    boolean canUpdateDb = canUpdateInstancesInDb(instanceSyncFlow, infrastructureMapping.getAccountId());

    if (instanceSyncFlow != InstanceSyncFlow.PERPETUAL_TASK) {
      handleEc2InstanceSync(
          ec2InstanceIdInstanceMap, awsConfig, encryptedDataDetails, region, Optional.empty(), canUpdateDb);
      handleAsgInstanceSync(region, asgInstanceMap, awsConfig, encryptedDataDetails, infrastructureMapping,
          asgNamesDeploymentSummaryMap, true, rollback, Optional.empty(), canUpdateDb);
    } else {
      handleAsgInstanceSyncForPerpetualTask(asgInstanceMap, infrastructureMapping, asgNamesDeploymentSummaryMap, true,
          rollback, asgListInstancesResponse);
    }
  }

  private void handleAsgInstanceSyncForPerpetualTask(Multimap<String, Instance> asgInstanceMap,
      InfrastructureMapping infrastructureMapping, Map<String, DeploymentSummary> asgNamesDeploymentSummaryMap,
      boolean isAmi, boolean rollback, AwsAsgListInstancesResponse response) {
    if (asgInstanceMap.size() > 0) {
      handleAutoScalingGroup(asgInstanceMap, infrastructureMapping, asgNamesDeploymentSummaryMap, isAmi, rollback,
          response.getAsgName(), response.getInstances());
    }
  }

  private Map<String, DeploymentSummary> getDeploymentSummaryMap(List<DeploymentSummary> newDeploymentSummaries) {
    if (isEmpty(newDeploymentSummaries)) {
      return Collections.emptyMap();
    }

    Map<String, DeploymentSummary> deploymentSummaryMap = new HashMap<>();
    newDeploymentSummaries.forEach(deploymentSummary
        -> deploymentSummaryMap.put(
            ((AwsAutoScalingGroupDeploymentInfo) deploymentSummary.getDeploymentInfo()).getAutoScalingGroupName(),
            deploymentSummary));

    return deploymentSummaryMap;
  }

  @Override
  public void handleNewDeployment(
      List<DeploymentSummary> deploymentSummaries, boolean rollback, OnDemandRollbackInfo onDemandRollbackInfo) {
    if (!(deploymentSummaries.iterator().next().getDeploymentInfo() instanceof AwsAutoScalingGroupDeploymentInfo)) {
      throw WingsException.builder().message("Incompatible deployment type.").build();
    }

    Multimap<String, Instance> asgInstanceMap = ArrayListMultimap.create();

    deploymentSummaries.forEach(deploymentSummary
        -> asgInstanceMap.put(
            ((AwsAutoScalingGroupDeploymentInfo) deploymentSummary.getDeploymentInfo()).getAutoScalingGroupName(),
            null));

    syncInstancesInternal(deploymentSummaries.iterator().next().getAppId(),
        deploymentSummaries.iterator().next().getInfraMappingId(), asgInstanceMap, deploymentSummaries, rollback, null,
        InstanceSyncFlow.NEW_DEPLOYMENT);
  }

  @Override
  public Optional<FeatureName> getFeatureFlagToStopIteratorBasedInstanceSync() {
    return Optional.of(STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_AMI_DEPLOYMENTS);
  }

  /**
   * Returns the auto scaling group names
   */
  private List<String> getASGFromAMIDeployment(PhaseExecutionData phaseExecutionData,
      PhaseStepExecutionData phaseStepExecutionData, WorkflowExecution workflowExecution) {
    List<String> autoScalingGroupNames = Lists.newArrayList();

    PhaseStepExecutionSummary phaseStepExecutionSummary = phaseStepExecutionData.getPhaseStepExecutionSummary();

    if (phaseStepExecutionSummary != null) {
      Optional<StepExecutionSummary> stepExecutionSummaryOptional =
          phaseStepExecutionSummary.getStepExecutionSummaryList()
              .stream()
              .filter(stepExecutionSummary -> stepExecutionSummary instanceof AmiStepExecutionSummary)
              .findFirst();

      if (stepExecutionSummaryOptional.isPresent()) {
        StepExecutionSummary stepExecutionSummary = stepExecutionSummaryOptional.get();

        AmiStepExecutionSummary amiStepExecutionSummary = (AmiStepExecutionSummary) stepExecutionSummary;

        // Capture the instances of the new revision
        if (isNotEmpty(amiStepExecutionSummary.getNewInstanceData())) {
          List<String> asgList = amiStepExecutionSummary.getNewInstanceData()
                                     .stream()
                                     .map(ContainerServiceData::getName)
                                     .collect(toList());
          if (isNotEmpty(asgList)) {
            autoScalingGroupNames.addAll(asgList);
          }
        }

        // Capture the instances of the old revision, note that the downsize operation need not bring the count
        // to zero.
        if (isNotEmpty(amiStepExecutionSummary.getOldInstanceData())) {
          List<String> asgList = amiStepExecutionSummary.getOldInstanceData()
                                     .stream()
                                     .map(ContainerServiceData::getName)
                                     .collect(toList());
          if (isNotEmpty(asgList)) {
            autoScalingGroupNames.addAll(asgList);
          }
        }
      } else {
        throw WingsException.builder()
            .message(
                "Step execution summary null for AMI Deploy Step for workflow: " + workflowExecution.normalizedName())
            .build();
      }
    } else {
      throw WingsException.builder()
          .message(
              "Phase step execution summary null for AMI Deploy for workflow: " + workflowExecution.normalizedName())
          .build();
    }

    return autoScalingGroupNames;
  }

  @Override
  public Optional<List<DeploymentInfo>> getDeploymentInfo(PhaseExecutionData phaseExecutionData,
      PhaseStepExecutionData phaseStepExecutionData, WorkflowExecution workflowExecution,
      InfrastructureMapping infrastructureMapping, String stateExecutionInstanceId, Artifact artifact) {
    List<String> autoScalingGroupNames =
        getASGFromAMIDeployment(phaseExecutionData, phaseStepExecutionData, workflowExecution);
    List<DeploymentInfo> deploymentInfos = new ArrayList<>();
    autoScalingGroupNames.forEach(autoScalingGroupName
        -> deploymentInfos.add(
            AwsAutoScalingGroupDeploymentInfo.builder().autoScalingGroupName(autoScalingGroupName).build()));
    return Optional.of(deploymentInfos);
  }

  @Override
  public DeploymentKey generateDeploymentKey(DeploymentInfo deploymentInfo) {
    return AwsAmiDeploymentKey.builder()
        .autoScalingGroupName(((AwsAutoScalingGroupDeploymentInfo) deploymentInfo).getAutoScalingGroupName())
        .build();
  }

  @Override
  protected void setDeploymentKey(DeploymentSummary deploymentSummary, DeploymentKey deploymentKey) {
    if (deploymentKey instanceof AwsAmiDeploymentKey) {
      deploymentSummary.setAwsAmiDeploymentKey((AwsAmiDeploymentKey) deploymentKey);
    } else {
      throw WingsException.builder()
          .message("Invalid deploymentKey passed for AwsAmiDeploymentKey" + deploymentKey)
          .build();
    }
  }

  @Override
  public Optional<FeatureName> getFeatureFlagToEnablePerpetualTaskForInstanceSync() {
    return Optional.of(FeatureName.MOVE_AWS_AMI_INSTANCE_SYNC_TO_PERPETUAL_TASK);
  }

  @Override
  public InstanceSyncPerpetualTaskCreator getInstanceSyncPerpetualTaskCreator() {
    return perpetualTaskCreator;
  }

  @Override
  public void processInstanceSyncResponseFromPerpetualTask(
      InfrastructureMapping infrastructureMapping, DelegateResponseData response) {
    Multimap<String, Instance> asgInstanceMap = ArrayListMultimap.create();
    syncInstancesInternal(infrastructureMapping.getAppId(), infrastructureMapping.getUuid(), asgInstanceMap, null,
        false, (AwsAsgListInstancesResponse) response, InstanceSyncFlow.PERPETUAL_TASK);
  }

  @Override
  public Status getStatus(InfrastructureMapping infrastructureMapping, DelegateResponseData response) {
    AwsAsgListInstancesResponse asgListInstancesResponse = (AwsAsgListInstancesResponse) response;
    boolean success = asgListInstancesResponse.getExecutionStatus() == ExecutionStatus.SUCCESS;
    boolean deleteTask = success && isEmpty(asgListInstancesResponse.getInstances());
    String errorMessage = success ? null : asgListInstancesResponse.getErrorMessage();

    return Status.builder().retryable(!deleteTask).errorMessage(errorMessage).success(success).build();
  }
}
