/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.MOVE_AWS_CODE_DEPLOY_INSTANCE_SYNC_TO_PERPETUAL_TASK;
import static io.harness.beans.FeatureName.STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_CODE_DEPLOY_DEPLOYMENTS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.validation.Validator.notNullCheck;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.AwsCodeDeployDeploymentInfo;
import software.wings.api.CommandStepExecutionSummary;
import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.PhaseExecutionData;
import software.wings.api.PhaseStepExecutionData;
import software.wings.api.ondemandrollback.OnDemandRollbackInfo;
import software.wings.beans.AwsConfig;
import software.wings.beans.CodeDeployInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.command.CodeDeployParams;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceBuilder;
import software.wings.beans.infrastructure.instance.info.CodeDeployInstanceInfo;
import software.wings.beans.infrastructure.instance.info.Ec2InstanceInfo;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.key.deployment.AwsCodeDeployDeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.DeploymentKey;
import software.wings.service.AwsCodeDeployInstanceSyncPerpetualTaskCreator;
import software.wings.service.InstanceSyncPerpetualTaskCreator;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentInstancesResponse;
import software.wings.service.intfc.aws.manager.AwsCodeDeployHelperServiceManager;
import software.wings.sm.PhaseStepExecutionSummary;
import software.wings.sm.StepExecutionSummary;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * @author rktummala on 01/30/18
 */
@Slf4j
@OwnedBy(CDP)
public class AwsCodeDeployInstanceHandler extends AwsInstanceHandler implements InstanceSyncByPerpetualTaskHandler {
  @Inject private AwsCodeDeployHelperServiceManager awsCodeDeployHelperServiceManager;
  @Inject private AwsCodeDeployInstanceSyncPerpetualTaskCreator taskCreator;

  @Override
  public Optional<List<DeploymentInfo>> getDeploymentInfo(PhaseExecutionData phaseExecutionData,
      PhaseStepExecutionData phaseStepExecutionData, WorkflowExecution workflowExecution,
      InfrastructureMapping infrastructureMapping, String stateExecutionInstanceId, Artifact artifact) {
    PhaseStepExecutionSummary phaseStepExecutionSummary = phaseStepExecutionData.getPhaseStepExecutionSummary();

    if (phaseStepExecutionSummary != null) {
      Optional<StepExecutionSummary> stepExecutionSummaryOptional =
          phaseStepExecutionSummary.getStepExecutionSummaryList()
              .stream()
              .filter(stepExecutionSummary -> stepExecutionSummary instanceof CommandStepExecutionSummary)
              .findFirst();

      if (stepExecutionSummaryOptional.isPresent()) {
        StepExecutionSummary stepExecutionSummary = stepExecutionSummaryOptional.get();

        CommandStepExecutionSummary commandStepExecutionSummary = (CommandStepExecutionSummary) stepExecutionSummary;
        CodeDeployParams codeDeployParams = commandStepExecutionSummary.getCodeDeployParams();
        if (codeDeployParams == null) {
          log.warn("Phase step execution summary null for Deploy for workflow:{} Can't create deployment event",
              workflowExecution.normalizedName());
          return Optional.empty();
        }

        return Optional.of(singletonList(AwsCodeDeployDeploymentInfo.builder()
                                             .deploymentGroupName(codeDeployParams.getDeploymentGroupName())
                                             .key(codeDeployParams.getKey())
                                             .applicationName(codeDeployParams.getApplicationName())
                                             .deploymentId(commandStepExecutionSummary.getCodeDeployDeploymentId())
                                             .build()));

      } else {
        throw WingsException.builder()
            .message("Command step execution summary null for workflow: " + workflowExecution.normalizedName())
            .build();
      }
    } else {
      return Optional.empty();
    }
  }

  @Override
  public void syncInstances(String appId, String infraMappingId, InstanceSyncFlow instanceSyncFlow) {
    InfrastructureMapping infrastructureMapping = infraMappingService.get(appId, infraMappingId);
    notNullCheck("Infra mapping is null for id:" + infraMappingId, infrastructureMapping);
    syncInstancesInternal(infrastructureMapping, null, false, Optional.empty(), instanceSyncFlow);
  }

  @Override
  public void processInstanceSyncResponseFromPerpetualTask(
      InfrastructureMapping infrastructureMapping, DelegateResponseData response) {
    AwsCodeDeployListDeploymentInstancesResponse listInstancesResponse =
        (AwsCodeDeployListDeploymentInstancesResponse) response;
    syncInstancesInternal(infrastructureMapping, null, false, Optional.of(listInstancesResponse.getInstances()),
        InstanceSyncFlow.PERPETUAL_TASK);
  }

  private void syncInstancesInternal(InfrastructureMapping infraMapping, List<DeploymentSummary> newDeploymentSummaries,
      boolean rollback, Optional<List<com.amazonaws.services.ec2.model.Instance>> instances,
      InstanceSyncFlow instanceSyncFlow) {
    if (!(infraMapping instanceof CodeDeployInfrastructureMapping)) {
      String msg =
          "Incompatible infra mapping type. Expecting code deploy type. Found:" + infraMapping.getInfraMappingType();
      log.error(msg);
      throw WingsException.builder().message(msg).build();
    }

    // key - ec2 instance id, value - instance
    Map<String, Instance> ec2InstanceIdInstanceMap = new HashMap<>();

    List<Instance> instancesInDB = getInstances(infraMapping.getAppId(), infraMapping.getUuid());
    boolean canUpdateDb = canUpdateInstancesInDb(instanceSyncFlow, infraMapping.getAccountId());

    instancesInDB.forEach(instance -> {
      InstanceInfo instanceInfo = instance.getInstanceInfo();
      if (instanceInfo instanceof Ec2InstanceInfo) {
        Ec2InstanceInfo ec2InstanceInfo = (Ec2InstanceInfo) instanceInfo;
        com.amazonaws.services.ec2.model.Instance ec2Instance = ec2InstanceInfo.getEc2Instance();
        String ec2InstanceId = ec2Instance.getInstanceId();
        ec2InstanceIdInstanceMap.put(ec2InstanceId, instance);
      }
    });

    SettingAttribute cloudProviderSetting = settingsService.get(infraMapping.getComputeProviderSettingId());
    AwsConfig awsConfig = (AwsConfig) cloudProviderSetting.getValue();

    List<EncryptedDataDetail> encryptedDataDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) cloudProviderSetting.getValue(), null, null);

    CodeDeployInfrastructureMapping codeDeployInfraMapping = (CodeDeployInfrastructureMapping) infraMapping;
    String region = codeDeployInfraMapping.getRegion();

    if (isNotEmpty(newDeploymentSummaries)) {
      newDeploymentSummaries.forEach(newDeploymentSummary -> {
        AwsCodeDeployDeploymentInfo awsCodeDeployDeploymentInfo =
            (AwsCodeDeployDeploymentInfo) newDeploymentSummary.getDeploymentInfo();

        // instancesInDBMap contains all instancesInDB for current appId and infraMapId
        Map<String, Instance> instancesInDBMap = instancesInDB.stream()
                                                     .filter(Objects::nonNull)
                                                     .collect(Collectors.toMap(this::getKeyFromInstance, identity()));

        // This will create filter for "instance-state-name" = "running"
        List<com.amazonaws.services.ec2.model.Instance> latestEc2Instances =
            awsCodeDeployHelperServiceManager.listDeploymentInstances(awsConfig, encryptedDataDetails, region,
                awsCodeDeployDeploymentInfo.getDeploymentId(), codeDeployInfraMapping.getAppId());
        Map<String, com.amazonaws.services.ec2.model.Instance> latestEc2InstanceMap =
            latestEc2Instances.stream().collect(
                Collectors.toMap(com.amazonaws.services.ec2.model.Instance::getInstanceId, identity()));

        SetView<String> instancesToBeUpdated =
            Sets.intersection(latestEc2InstanceMap.keySet(), instancesInDBMap.keySet());

        instancesToBeUpdated.forEach(ec2InstanceId -> {
          // change to codeDeployInstance builder
          Instance oldInstance = instancesInDBMap.get(ec2InstanceId);
          com.amazonaws.services.ec2.model.Instance ec2Instance = latestEc2InstanceMap.get(ec2InstanceId);
          Instance instance = buildInstanceUsingEc2Instance(
              null, ec2Instance, infraMapping, getDeploymentSummaryForInstanceCreation(newDeploymentSummary, rollback));
          if (oldInstance != null) {
            instanceService.update(instance, oldInstance.getUuid());
          } else {
            log.error("Instance doesn't exist for given ec2 instance id {}", ec2InstanceId);
          }
        });

        log.info("Instances to be updated {}", instancesToBeUpdated.size());

        // Find the instances that were yet to be added to db
        SetView<String> instancesToBeAdded = Sets.difference(latestEc2InstanceMap.keySet(), instancesInDBMap.keySet());

        DeploymentSummary deploymentSummary;
        if (isNotEmpty(instancesToBeAdded)) {
          deploymentSummary = getDeploymentSummaryForInstanceCreation(newDeploymentSummary, rollback);

          instancesToBeAdded.forEach(ec2InstanceId -> {
            com.amazonaws.services.ec2.model.Instance ec2Instance = latestEc2InstanceMap.get(ec2InstanceId);
            // change to codeDeployInstance builder
            Instance instance = buildInstanceUsingEc2Instance(null, ec2Instance, infraMapping, deploymentSummary);
            instanceService.save(instance);
          });

          log.info("Instances to be added {}", instancesToBeAdded.size());
        }
      });
    }

    handleEc2InstanceSync(ec2InstanceIdInstanceMap, awsConfig, encryptedDataDetails, region, instances, canUpdateDb);
  }

  private Instance buildInstanceUsingEc2Instance(String instanceUuid,
      com.amazonaws.services.ec2.model.Instance ec2Instance, InfrastructureMapping infraMapping,
      DeploymentSummary deploymentSummary) {
    InstanceBuilder builder = buildInstanceBase(instanceUuid, infraMapping, deploymentSummary);
    instanceHelper.setInstanceInfoAndKey(builder, ec2Instance, infraMapping.getUuid());
    return builder.build();
  }

  private String getKeyFromInstance(Instance instance) {
    String instanceInfoString;
    if (instance.getInstanceInfo() instanceof Ec2InstanceInfo) {
      Ec2InstanceInfo ec2InstanceInfo = (Ec2InstanceInfo) instance.getInstanceInfo();
      instanceInfoString = ec2InstanceInfo.getEc2Instance().getInstanceId();
    } else {
      CodeDeployInstanceInfo instanceInfo = (CodeDeployInstanceInfo) instance.getInstanceInfo();
      instanceInfoString = instanceInfo.getEc2Instance().getInstanceId();
    }

    return instanceInfoString;
  }

  @Override
  public void handleNewDeployment(List<DeploymentSummary> deploymentSummaries, boolean rollback,
      OnDemandRollbackInfo onDemandRollbackInfo) throws WingsException {
    String appId = deploymentSummaries.iterator().next().getAppId();
    String infraMappingId = deploymentSummaries.iterator().next().getInfraMappingId();
    InfrastructureMapping infrastructureMapping = infraMappingService.get(appId, infraMappingId);
    notNullCheck("Infra mapping is null for id:" + infraMappingId, infrastructureMapping);
    syncInstancesInternal(
        infrastructureMapping, deploymentSummaries, rollback, Optional.empty(), InstanceSyncFlow.NEW_DEPLOYMENT);
  }

  @Override
  public Status getStatus(InfrastructureMapping infrastructureMapping, DelegateResponseData response) {
    AwsCodeDeployListDeploymentInstancesResponse listInstancesResponse =
        (AwsCodeDeployListDeploymentInstancesResponse) response;
    boolean success = listInstancesResponse.getExecutionStatus() == ExecutionStatus.SUCCESS;
    boolean deleteTask = success && isEmpty(listInstancesResponse.getInstances());
    String errorMessage = success ? null : listInstancesResponse.getErrorMessage();

    return Status.builder().retryable(!deleteTask).errorMessage(errorMessage).success(success).build();
  }

  @Override
  public FeatureName getFeatureFlagToStopIteratorBasedInstanceSync() {
    return STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_CODE_DEPLOY_DEPLOYMENTS;
  }

  @Override
  public FeatureName getFeatureFlagToEnablePerpetualTaskForInstanceSync() {
    return MOVE_AWS_CODE_DEPLOY_INSTANCE_SYNC_TO_PERPETUAL_TASK;
  }

  @Override
  public InstanceSyncPerpetualTaskCreator getInstanceSyncPerpetualTaskCreator() {
    return taskCreator;
  }

  @Override
  public DeploymentKey generateDeploymentKey(DeploymentInfo deploymentInfo) {
    return AwsCodeDeployDeploymentKey.builder().key(((AwsCodeDeployDeploymentInfo) deploymentInfo).getKey()).build();
  }

  @Override
  protected void setDeploymentKey(DeploymentSummary deploymentSummary, DeploymentKey deploymentKey) {
    if (deploymentKey instanceof AwsCodeDeployDeploymentKey) {
      deploymentSummary.setAwsCodeDeployDeploymentKey((AwsCodeDeployDeploymentKey) deploymentKey);
    } else {
      throw WingsException.builder()
          .message("Invalid deploymentKey passed for AwsCodeDeployDeploymentKey" + deploymentKey)
          .build();
    }
  }
}
