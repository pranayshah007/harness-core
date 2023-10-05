/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.service.impl.instance.InstanceSyncFlow.ITERATOR;
import static software.wings.service.impl.instance.InstanceSyncFlow.MANUAL;
import static software.wings.service.impl.instance.InstanceSyncFlow.NEW_DEPLOYMENT;
import static software.wings.service.impl.instance.InstanceSyncFlow.PERPETUAL_TASK;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.perpetualtask.PerpetualTaskService;

import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.PhaseExecutionData;
import software.wings.api.PhaseStepExecutionData;
import software.wings.api.ondemandrollback.OnDemandRollbackInfo;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceBuilder;
import software.wings.beans.infrastructure.instance.InstanceType;
import software.wings.beans.infrastructure.instance.key.HostInstanceKey;
import software.wings.beans.infrastructure.instance.key.deployment.DeploymentKey;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.instance.DeploymentService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.PipelineSummary;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(CDP)
@TargetModule(HarnessModule._441_CG_INSTANCE_SYNC)
public abstract class InstanceHandler {
  @Inject protected InstanceHelper instanceHelper;
  @Inject protected InstanceService instanceService;
  @Inject protected InfrastructureMappingService infraMappingService;
  @Inject protected SettingsService settingsService;
  @Inject protected SecretManager secretManager;
  @Inject protected TriggerService triggerService;
  @Inject protected AppService appService;
  @Inject protected EnvironmentService environmentService;
  @Inject protected WorkflowExecutionService workflowExecutionService;
  @Inject protected ServiceResourceService serviceResourceService;
  @Inject protected InstanceUtils instanceUtil;
  @Inject protected DeploymentService deploymentService;
  @Inject protected FeatureFlagService featureFlagService;
  @Inject protected PerpetualTaskService perpetualTaskService;

  public static final String AUTO_SCALE = "AUTO_SCALE";

  public abstract void syncInstances(String appId, String infraMappingId, InstanceSyncFlow instanceSyncFlow);

  public abstract void handleNewDeployment(
      List<DeploymentSummary> deploymentSummaries, boolean rollback, OnDemandRollbackInfo onDemandRollbackInfo);

  public boolean canUpdateInstancesInDb(InstanceSyncFlow instanceSyncFlow, String accountId) {
    if (instanceSyncFlow == NEW_DEPLOYMENT || instanceSyncFlow == MANUAL) {
      return true;
    }

    boolean isPerpetualTaskEnabled = false;

    if (this instanceof InstanceSyncByPerpetualTaskHandler) {
      InstanceSyncByPerpetualTaskHandler handler = (InstanceSyncByPerpetualTaskHandler) this;

      Optional<FeatureName> featureFlagOpt = handler.getFeatureFlagToEnablePerpetualTaskForInstanceSync();
      isPerpetualTaskEnabled =
          featureFlagOpt.map(featureName -> featureFlagService.isEnabled(featureName, accountId)).orElse(true);
    }

    return isPerpetualTaskEnabled ? instanceSyncFlow == PERPETUAL_TASK : instanceSyncFlow == ITERATOR;
  }

  public abstract Optional<FeatureName> getFeatureFlagToStopIteratorBasedInstanceSync();

  public abstract Optional<List<DeploymentInfo>> getDeploymentInfo(PhaseExecutionData phaseExecutionData,
      PhaseStepExecutionData phaseStepExecutionData, WorkflowExecution workflowExecution,
      InfrastructureMapping infrastructureMapping, String stateExecutionInstanceId, Artifact artifact);

  protected List<Instance> getInstances(String appId, String infraMappingId) {
    return instanceService.getInstancesForAppAndInframapping(appId, infraMappingId);
  }

  public abstract DeploymentKey generateDeploymentKey(DeploymentInfo deploymentInfo);

  public List<DeploymentSummary> getDeploymentSummariesForEvent(PhaseExecutionData phaseExecutionData,
      PhaseStepExecutionData phaseStepExecutionData, WorkflowExecution workflowExecution,
      InfrastructureMapping infrastructureMapping, String stateExecutionInstanceId, Artifact artifact) {
    Optional<List<DeploymentInfo>> deploymentInfoOptional = getDeploymentInfo(phaseExecutionData,
        phaseStepExecutionData, workflowExecution, infrastructureMapping, stateExecutionInstanceId, artifact);

    List<DeploymentSummary> deploymentSummaries = new ArrayList<>();

    if (deploymentInfoOptional.isPresent()) {
      for (DeploymentInfo deploymentInfo : deploymentInfoOptional.get()) {
        DeploymentSummary deploymentSummary = setValuesToDeploymentSummary(stateExecutionInstanceId, workflowExecution,
            phaseExecutionData, infrastructureMapping, artifact, deploymentInfo, generateDeploymentKey(deploymentInfo));

        deploymentSummaries.add(deploymentSummary);
      }
    }

    return deploymentSummaries;
  }

  private DeploymentSummary setValuesToDeploymentSummary(String stateExecutionInstanceId,
      WorkflowExecution workflowExecution, PhaseExecutionData phaseExecutionData,
      InfrastructureMapping infrastructureMapping, Artifact artifact, DeploymentInfo deploymentInfo,
      DeploymentKey deploymentKey) {
    PipelineSummary pipelineSummary = workflowExecution.getPipelineSummary();
    Application application = appService.get(workflowExecution.getAppId());
    notNullCheck("Application", application);
    EmbeddedUser triggeredBy = workflowExecution.getTriggeredBy();
    notNullCheck("triggeredBy", triggeredBy);
    String infraMappingType = infrastructureMapping.getInfraMappingType();

    String workflowName = instanceUtil.getWorkflowName(workflowExecution.normalizedName());
    notNullCheck("WorkflowName", workflowName);

    validateInstanceType(infraMappingType);

    DeploymentSummary deploymentSummary = DeploymentSummary.builder().build();
    deploymentSummary.setAppId(workflowExecution.getAppId());
    deploymentSummary.setAccountId(application.getAccountId());
    deploymentSummary.setInfraMappingId(infrastructureMapping.getUuid());
    deploymentSummary.setStateExecutionInstanceId(stateExecutionInstanceId);
    deploymentSummary.setWorkflowExecutionId(workflowExecution.getUuid());
    deploymentSummary.setWorkflowExecutionName(workflowExecution.normalizedName());
    deploymentSummary.setWorkflowId(workflowExecution.getWorkflowId());

    if (artifact != null) {
      deploymentSummary.setArtifactId(artifact.getUuid());
      deploymentSummary.setArtifactName(artifact.getDisplayName());
      deploymentSummary.setArtifactStreamId(artifact.getArtifactStreamId());
      deploymentSummary.setArtifactSourceName(artifact.getArtifactSourceName());
      deploymentSummary.setArtifactBuildNum(artifact.getBuildNo());
    }

    if (pipelineSummary != null) {
      deploymentSummary.setPipelineExecutionId(pipelineSummary.getPipelineId());
      deploymentSummary.setPipelineExecutionName(pipelineSummary.getPipelineName());
    }

    deploymentSummary.setDeployedById(triggeredBy.getUuid());
    deploymentSummary.setDeployedByName(triggeredBy.getName());
    deploymentSummary.setDeployedAt(
        phaseExecutionData.getEndTs() == null ? System.currentTimeMillis() : phaseExecutionData.getEndTs());

    deploymentSummary.setDeploymentInfo(deploymentInfo);
    setDeploymentKey(deploymentSummary, deploymentKey);
    return deploymentSummary;
  }

  protected void validateInstanceType(String infraMappingType) {
    InstanceType instanceType = instanceUtil.getInstanceType(infraMappingType);
    notNullCheck("InstanceType", instanceType);
  }

  protected abstract void setDeploymentKey(DeploymentSummary deploymentSummary, DeploymentKey deploymentKey);

  /**
   * This generates the deployment info from an instance deployed earlier.
   * This info is used while creating new instance when periodic sync identifies a new instance.
   * @param instance instance from a previous deployment
   * @param deploymentSummary deployment info to be constructed.
   * @return
   */
  protected DeploymentSummary generateDeploymentSummaryFromInstance(
      Instance instance, DeploymentSummary deploymentSummary) {
    deploymentSummary.setAppId(instance.getAppId());
    deploymentSummary.setAccountId(instance.getAccountId());
    deploymentSummary.setInfraMappingId(instance.getInfraMappingId());
    deploymentSummary.setInfraMappingId(instance.getInfraMappingId());
    deploymentSummary.setWorkflowExecutionId(instance.getLastWorkflowExecutionId());
    deploymentSummary.setWorkflowExecutionName(instance.getLastWorkflowExecutionName());
    deploymentSummary.setWorkflowId(instance.getLastWorkflowExecutionId());

    deploymentSummary.setArtifactId(instance.getLastArtifactId());
    deploymentSummary.setArtifactName(instance.getLastArtifactName());
    deploymentSummary.setArtifactStreamId(instance.getLastArtifactStreamId());
    deploymentSummary.setArtifactSourceName(instance.getLastArtifactSourceName());
    deploymentSummary.setArtifactBuildNum(instance.getLastArtifactBuildNum());

    deploymentSummary.setPipelineExecutionId(instance.getLastPipelineExecutionId());
    deploymentSummary.setPipelineExecutionName(instance.getLastPipelineExecutionName());

    // Commented this out, so we can distinguish between autoscales instances and instances we deployed
    deploymentSummary.setDeployedById(AUTO_SCALE);
    deploymentSummary.setDeployedByName(AUTO_SCALE);
    deploymentSummary.setDeployedAt(System.currentTimeMillis());
    deploymentSummary.setArtifactBuildNum(instance.getLastArtifactBuildNum());

    return deploymentSummary;
  }

  protected DeploymentSummary getDeploymentSummaryForInstanceCreation(
      DeploymentSummary newDeploymentSummary, boolean rollback) {
    DeploymentSummary deploymentSummary;
    if (rollback) {
      deploymentSummary = getDeploymentSummaryForRollback(newDeploymentSummary);
    } else {
      deploymentSummary = newDeploymentSummary;
    }

    return deploymentSummary;
  }

  protected DeploymentSummary getDeploymentSummaryForRollback(DeploymentSummary deploymentSummary) {
    Optional<DeploymentSummary> summaryOptional = deploymentService.get(deploymentSummary);
    if (summaryOptional != null && summaryOptional.isPresent()) {
      DeploymentSummary deploymentSummaryFromDB = summaryOptional.get();
      // Copy Artifact Information for rollback version for previous deployment summary
      deploymentSummary.setArtifactBuildNum(deploymentSummaryFromDB.getArtifactBuildNum());
      deploymentSummary.setArtifactName(deploymentSummaryFromDB.getArtifactName());
      deploymentSummary.setArtifactId(deploymentSummaryFromDB.getArtifactId());
      deploymentSummary.setArtifactSourceName(deploymentSummaryFromDB.getArtifactSourceName());
      deploymentSummary.setArtifactStreamId(deploymentSummaryFromDB.getArtifactStreamId());
      deploymentSummary.setWorkflowId(deploymentSummaryFromDB.getWorkflowId());
      deploymentSummary.setWorkflowExecutionName(deploymentSummaryFromDB.getWorkflowExecutionName());
      deploymentSummary.setWorkflowExecutionId(deploymentSummaryFromDB.getWorkflowExecutionId());
    } else {
      log.info("Unable to find DeploymentSummary while rolling back " + deploymentSummary);
    }
    return deploymentSummary;
  }

  protected InstanceBuilder buildInstanceBase(
      String instanceId, InfrastructureMapping infraMapping, DeploymentSummary deploymentSummary, Artifact artifact) {
    InstanceBuilder builder = this.buildInstanceBase(instanceId, infraMapping);
    if (deploymentSummary != null) {
      builder.lastDeployedAt(deploymentSummary.getDeployedAt())
          .lastDeployedById(deploymentSummary.getDeployedById())
          .lastDeployedByName(deploymentSummary.getDeployedByName())
          .lastWorkflowExecutionId(deploymentSummary.getWorkflowExecutionId())
          .lastWorkflowExecutionName(deploymentSummary.getWorkflowExecutionName())
          .lastPipelineExecutionId(deploymentSummary.getPipelineExecutionId())
          .lastPipelineExecutionName(deploymentSummary.getPipelineExecutionName());

      if (artifact != null) {
        builder.lastArtifactId(artifact.getUuid())
            .lastArtifactName(artifact.getDisplayName())
            .lastArtifactStreamId(artifact.getArtifactStreamId())
            .lastArtifactSourceName(artifact.getArtifactSourceName())
            .lastArtifactBuildNum(artifact.getBuildNo());
      } else {
        builder.lastArtifactId(deploymentSummary.getArtifactId())
            .lastArtifactName(deploymentSummary.getArtifactName())
            .lastArtifactStreamId(deploymentSummary.getArtifactStreamId())
            .lastArtifactSourceName(deploymentSummary.getArtifactSourceName())
            .lastArtifactBuildNum(deploymentSummary.getArtifactBuildNum());
      }
    }

    return builder;
  }

  protected InstanceBuilder buildInstanceBase(
      String instanceId, InfrastructureMapping infraMapping, DeploymentSummary deploymentSummary) {
    return buildInstanceBase(instanceId, infraMapping, deploymentSummary, null);
  }

  protected InstanceBuilder buildInstanceBase(String instanceUuid, InfrastructureMapping infraMapping) {
    String appId = infraMapping.getAppId();
    Application application = appService.get(appId);
    notNullCheck("Application is null for the given appId: " + appId, application);
    Environment environment = environmentService.get(appId, infraMapping.getEnvId(), false);
    notNullCheck("Environment is null for the given id: " + infraMapping.getEnvId(), environment);
    Service service = serviceResourceService.getWithDetails(appId, infraMapping.getServiceId());
    notNullCheck("Service is null for the given id: " + infraMapping.getServiceId(), service);
    String infraMappingType = infraMapping.getInfraMappingType();

    if (instanceUuid == null) {
      instanceUuid = generateUuid();
    }

    InstanceBuilder builder = Instance.builder()
                                  .uuid(instanceUuid)
                                  .accountId(application.getAccountId())
                                  .appId(appId)
                                  .appName(application.getName())
                                  .envName(environment.getName())
                                  .envId(infraMapping.getEnvId())
                                  .envType(environment.getEnvironmentType())
                                  .computeProviderId(infraMapping.getComputeProviderSettingId())
                                  .computeProviderName(infraMapping.getComputeProviderName())
                                  .infraMappingId(infraMapping.getUuid())
                                  .infraMappingName(infraMapping.getDisplayName())
                                  .infraMappingType(infraMappingType)
                                  .serviceId(infraMapping.getServiceId())
                                  .serviceName(service.getName());
    instanceUtil.setInstanceType(builder, infraMappingType);

    return builder;
  }

  protected Optional<Instance> getInstanceWithExecutionInfo(Collection<Instance> instances) {
    return instances.stream().filter(instance -> instance.getLastWorkflowExecutionId() != null).findFirst();
  }

  protected void handleEc2InstanceDelete(Map<String, Instance> instancesInDBMap,
      Map<String, com.amazonaws.services.ec2.model.Instance> latestEc2InstanceMap) {
    // Find the instances that are no longer present and to be deleted from db.
    SetView<String> instancesToBeDeleted = Sets.difference(instancesInDBMap.keySet(), latestEc2InstanceMap.keySet());

    Set<String> instanceIdsToBeDeleted = new HashSet<>();
    instancesToBeDeleted.forEach(ec2InstanceId -> {
      Instance instance = instancesInDBMap.get(ec2InstanceId);
      if (instance != null) {
        instanceIdsToBeDeleted.add(instance.getUuid());
      }
    });

    if (isNotEmpty(instanceIdsToBeDeleted)) {
      instanceService.delete(instanceIdsToBeDeleted);
      log.debug("Instances to be deleted {}", instanceIdsToBeDeleted.size());
    }
  }

  /**
   * @param ec2Instance     Ec2 instance
   * @param infraMappingId  Infra mapping id
   * @param instanceBuilder Instance builder
   * @return privateDnsName private dns name
   */
  protected String buildHostInstanceKey(
      com.amazonaws.services.ec2.model.Instance ec2Instance, String infraMappingId, InstanceBuilder instanceBuilder) {
    String privateDnsNameWithSuffix = ec2Instance.getPrivateDnsName();
    String privateDnsName = privateDnsNameWithSuffix == null
        ? StringUtils.EMPTY
        : privateDnsNameWithSuffix.substring(0, privateDnsNameWithSuffix.indexOf('.'));
    HostInstanceKey hostInstanceKey =
        HostInstanceKey.builder().hostName(privateDnsName).infraMappingId(infraMappingId).build();
    instanceBuilder.hostInstanceKey(hostInstanceKey);
    return privateDnsName;
  }
}
