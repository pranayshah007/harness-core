/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.beans.FeatureName.INSTANCE_SYNC_V2_CG;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.InfrastructureMappingType.AWS_SSH;
import static software.wings.beans.InfrastructureMappingType.AZURE_INFRA;
import static software.wings.beans.InfrastructureMappingType.AZURE_WEBAPP;
import static software.wings.beans.InfrastructureMappingType.PHYSICAL_DATA_CENTER_SSH;
import static software.wings.beans.InfrastructureMappingType.PHYSICAL_DATA_CENTER_WINRM;
import static software.wings.service.InstanceSyncConstants.HARNESS_ACCOUNT_ID;
import static software.wings.service.InstanceSyncConstants.HARNESS_APPLICATION_ID;
import static software.wings.service.InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID;
import static software.wings.service.impl.instance.InstanceSyncFlow.MANUAL;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ExecutionStatus;
import io.harness.context.ContextElementType;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.exception.GeneralException;
import io.harness.exception.WingsException;
import io.harness.exception.runtime.NoInstancesException;
import io.harness.ff.FeatureFlagService;
import io.harness.instancesyncmonitoring.service.InstanceSyncMonitoringService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.queue.QueuePublisher;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.validation.Validator;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.DeploymentEvent;
import software.wings.api.DeploymentSummary;
import software.wings.api.HostElement;
import software.wings.api.PhaseExecutionData;
import software.wings.api.PhaseStepExecutionData;
import software.wings.api.ondemandrollback.OnDemandRollbackInfo;
import software.wings.beans.Application;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.AwsConfig;
import software.wings.beans.CodeDeployInfrastructureMapping;
import software.wings.beans.ElementExecutionSummary;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.PhaseStepType;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.infrastructure.Host;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceBuilder;
import software.wings.beans.infrastructure.instance.ManualSyncJob;
import software.wings.beans.infrastructure.instance.info.AzureVMSSInstanceInfo;
import software.wings.beans.infrastructure.instance.info.AzureWebAppInstanceInfo;
import software.wings.beans.infrastructure.instance.info.Ec2InstanceInfo;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.PhysicalHostInstanceInfo;
import software.wings.beans.infrastructure.instance.key.HostInstanceKey;
import software.wings.instancesyncv2.model.InstanceSyncTaskDetails;
import software.wings.instancesyncv2.service.CgInstanceSyncTaskDetailsService;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.instance.DeploymentService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionEvent;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.PhaseStepExecutionSummary;
import software.wings.sm.PipelineSummary;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.PhaseStepSubWorkflow;
import software.wings.utils.Utils;

import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.Reservation;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Both the normal instance and container instance are handled here.
 * Once it finds the deployment is of type container, it hands off the request to ContainerInstanceHelper.
 *
 * @author rktummala on 09/11/17
 */
@Singleton
@Slf4j
@OwnedBy(DX)
@TargetModule(HarnessModule._441_CG_INSTANCE_SYNC)
public class InstanceHelper {
  // This queue is used to asynchronously process all the instance information that the workflow touched upon.
  @Inject private QueuePublisher<DeploymentEvent> deploymentEventQueue;
  @Inject private InfrastructureMappingService infraMappingService;
  @Inject private AppService appService;
  @Inject private InstanceUtils instanceUtil;
  @Inject private HostService hostService;
  @Inject private AwsHelperService awsHelperService;
  @Inject private SettingsService settingsService;
  @Inject private SecretManager secretManager;
  @Inject private InstanceService instanceService;
  @Inject private PersistentLocker persistentLocker;
  @Inject private InstanceHandlerFactoryService instanceHandlerFactory;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private DeploymentService deploymentService;
  @Inject private ExecutorService executorService;
  @Inject private PerpetualTaskService perpetualTaskService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private InstanceSyncPerpetualTaskService instanceSyncPerpetualTaskService;
  @Inject private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;
  @Inject private InstanceSyncMonitoringService instanceSyncMonitoringService;
  @Inject private CgInstanceSyncTaskDetailsService taskDetailsService;
  @Inject private EnvironmentService environmentService;

  /**
   * The phaseExecutionData is used to process the instance information that is used by the service and infra
   * dashboards. The instance processing happens asynchronously.
   */
  public void extractInstanceOrDeploymentInfoBaseOnType(String stateExecutionInstanceId,
      PhaseExecutionData phaseExecutionData, PhaseStepExecutionData phaseStepExecutionData,
      WorkflowStandardParams workflowStandardParams, String appId, WorkflowExecution workflowExecution,
      PhaseStepSubWorkflow phaseStepSubWorkflow, ExecutionContext context) {
    try {
      if (phaseExecutionData == null) {
        log.error("phaseExecutionData is null for state execution {}", stateExecutionInstanceId);
        return;
      }

      if (phaseStepExecutionData == null) {
        log.error("phaseStepExecutionData is null for state execution {}", stateExecutionInstanceId);
        return;
      }

      if (workflowStandardParams == null) {
        log.warn("workflowStandardParams can't be null, skipping instance processing");
        return;
      }

      Artifact artifact = workflowStandardParamsExtensionService.getArtifactForService(
          workflowStandardParams, phaseExecutionData.getServiceId());
      if (artifact == null) {
        log.info("artifact is null for stateExecutionInstance:" + stateExecutionInstanceId);
      }

      if (context.fetchInfraMappingId() == null) {
        log.info("infraMappingId is null for appId:{}, WorkflowExecutionId:{}", appId, workflowExecution.getUuid());
        return;
      }

      InfrastructureMapping infrastructureMapping = infraMappingService.get(appId, context.fetchInfraMappingId());

      if (PHYSICAL_DATA_CENTER_SSH.getName().equals(infrastructureMapping.getInfraMappingType())
          || PHYSICAL_DATA_CENTER_WINRM.getName().equals(infrastructureMapping.getInfraMappingType())
          || AWS_SSH.getName().equals(infrastructureMapping.getInfraMappingType())
          || AZURE_INFRA.name().equals(infrastructureMapping.getInfraMappingType())) {
        List<Instance> instanceList = Lists.newArrayList();
        PhaseStepExecutionSummary phaseStepExecutionSummary = phaseStepExecutionData.getPhaseStepExecutionSummary();

        if (phaseStepExecutionSummary == null) {
          log.warn(
              "phaseStepExecutionSummary is null for InfraMappingType {}, appId: {}, WorkflowExecution<Name, Id> :<{},{}>",
              infrastructureMapping.getInfraMappingType(), appId, workflowExecution.normalizedName(),
              workflowExecution.getWorkflowId());
          return;
        }

        if (phaseStepExecutionData.getElementStatusSummary() == null) {
          log.warn(
              "elementStatusSummary is null for InfraMappingType {}, appId: {}, WorkflowExecution<Name, Id> :<{},{}>",
              infrastructureMapping.getInfraMappingType(), appId, workflowExecution.normalizedName(),
              workflowExecution.getWorkflowId());
          return;
        }

        for (ElementExecutionSummary summary : phaseStepExecutionData.getElementStatusSummary()) {
          List<InstanceStatusSummary> instanceStatusSummaries = summary.getInstanceStatusSummaries();
          if (isEmpty(instanceStatusSummaries)) {
            log.info("No instances to process");
            return;
          }

          for (InstanceStatusSummary instanceStatusSummary : instanceStatusSummaries) {
            if (ExecutionStatus.isPositiveStatus(instanceStatusSummary.getStatus())) {
              if (phaseStepSubWorkflow.isRollback()) {
                // this is same way, workflow get artifact for rollback
                // reference in CommandState.java
                Artifact rollbackArtifact = serviceResourceService.findPreviousArtifact(
                    appId, context.getWorkflowExecutionId(), instanceStatusSummary.getInstanceElement());
                if (rollbackArtifact != null) {
                  artifact = rollbackArtifact;
                }
              }
              Instance instance = buildInstanceUsingHostInfo(workflowExecution, artifact, instanceStatusSummary,
                  phaseExecutionData, phaseStepExecutionData, infrastructureMapping);
              if (instance != null) {
                instanceList.add(instance);
              }
            }
          }
        }

        instanceService.saveOrUpdate(instanceList);

        if (AWS_SSH.getName().equals(infrastructureMapping.getInfraMappingType())
            || PHYSICAL_DATA_CENTER_SSH.getName().equals(infrastructureMapping.getInfraMappingType())
            || PHYSICAL_DATA_CENTER_WINRM.getName().equals(infrastructureMapping.getInfraMappingType())) {
          createPerpetualTaskForNewDeploymentIfEnabled(infrastructureMapping, emptyList());
        }

      } else {
        Optional<InstanceHandler> instanceHandlerOptional = getInstanceHandler(infrastructureMapping);
        if (!instanceHandlerOptional.isPresent()) {
          if (!hasNoInstanceHandler(infrastructureMapping.getInfraMappingType())) {
            String msg =
                "Instance handler not found for infraMappingType: " + infrastructureMapping.getInfraMappingType();
            log.error(msg);
            throw new WingsException(msg);
          } else {
            log.info("Instance handler not supported for infra mapping type: {}",
                infrastructureMapping.getInfraMappingType());
            return;
          }
        }
        InstanceHandler instanceHandler = instanceHandlerOptional.get();
        List<DeploymentSummary> deploymentSummaries = instanceHandler.getDeploymentSummariesForEvent(phaseExecutionData,
            phaseStepExecutionData, workflowExecution, infrastructureMapping, stateExecutionInstanceId, artifact);

        if (isNotEmpty(deploymentSummaries)) {
          deploymentEventQueue.send(
              DeploymentEvent.builder()
                  .deploymentSummaries(deploymentSummaries)
                  .isRollback(phaseStepSubWorkflow.isRollback())
                  .onDemandRollbackInfo(OnDemandRollbackInfo.builder()
                                            .onDemandRollback(workflowExecution.isOnDemandRollback())
                                            .rollbackExecutionId(workflowExecution.getUuid())
                                            .build())
                  .build());
        }
      }
    } catch (Exception ex) {
      // we deliberately don't throw back the exception since we don't want the workflow to be affected
      log.error(
          "Error while updating instance change information for executionId [{}], ", workflowExecution.getUuid(), ex);
    }
  }

  private boolean hasNoInstanceHandler(String infraMappingType) {
    return false;
  }

  public Instance buildInstanceUsingHostInfo(WorkflowExecution workflowExecution, Artifact artifact,
      InstanceStatusSummary instanceStatusSummary, PhaseExecutionData phaseExecutionData,
      PhaseStepExecutionData phaseStepExecutionData, InfrastructureMapping infraMapping) {
    HostElement host = instanceStatusSummary.getInstanceElement().getHost();
    Validator.notNullCheck("Host is null for workflow execution:" + workflowExecution.getWorkflowId(), host);
    SettingAttribute cloudProviderSetting = settingsService.get(infraMapping.getComputeProviderSettingId());

    InstanceBuilder builder = buildInstanceBase(
        workflowExecution, artifact, phaseExecutionData, phaseStepExecutionData, infraMapping, cloudProviderSetting);
    String hostUuid = host.getUuid();

    String region = null;
    if (infraMapping instanceof AwsAmiInfrastructureMapping) {
      region = ((AwsAmiInfrastructureMapping) infraMapping).getRegion();
    } else if (infraMapping instanceof CodeDeployInfrastructureMapping) {
      region = ((CodeDeployInfrastructureMapping) infraMapping).getRegion();
    }

    if (hostUuid == null) {
      if (host.getEc2Instance() != null) {
        setInstanceInfoAndKey(builder, host.getEc2Instance(), infraMapping.getUuid());
      } else if (host.getInstanceId() != null && region != null) {
        // TODO:: Avoid sequential fetch for Instance
        List<EncryptedDataDetail> encryptionDetails =
            secretManager.getEncryptionDetails((EncryptableSetting) cloudProviderSetting.getValue(),
                workflowExecution.getAppId(), workflowExecution.getUuid());
        AwsConfig awsConfig = (AwsConfig) cloudProviderSetting.getValue();
        com.amazonaws.services.ec2.model.Instance instance =
            awsHelperService
                .describeEc2Instances(awsConfig, encryptionDetails, region,
                    new DescribeInstancesRequest().withInstanceIds(host.getInstanceId()))
                .getReservations()
                .stream()
                .findFirst()
                .orElse(new Reservation().withInstances(new ArrayList<>()))
                .getInstances()
                .stream()
                .findFirst()
                .orElse(null);
        if (instance != null) {
          setInstanceInfoAndKey(builder, instance, infraMapping.getUuid());
        } else {
          log.warn(
              "Cannot build host based instance info since instanceId is not found in AWS workflowId:{}, instanceId:{}",
              workflowExecution.getUuid(), host.getInstanceId());
          return null;
        }
      } else {
        log.warn(
            "Cannot build host based instance info since both hostId and ec2Instance are null for workflow execution {}",
            workflowExecution.getUuid());
        return null;
      }
    } else {
      Host hostInfo = hostService.get(workflowExecution.getAppId(), workflowExecution.getEnvId(), hostUuid);
      notNullCheck("Host is null for workflow execution:" + workflowExecution.getWorkflowId(), hostInfo);
      setInstanceInfoAndKey(builder, hostInfo, infraMapping.getInfraMappingType(), infraMapping.getUuid());
    }
    return builder.build();
  }

  public void setInstanceInfoAndKey(
      InstanceBuilder builder, com.amazonaws.services.ec2.model.Instance ec2Instance, String infraMappingId) {
    String privateDnsNameWithSuffix = ec2Instance.getPrivateDnsName();
    String privateDnsName = getPrivateDnsName(privateDnsNameWithSuffix);
    HostInstanceKey hostInstanceKey =
        HostInstanceKey.builder().hostName(privateDnsName).infraMappingId(infraMappingId).build();
    builder.hostInstanceKey(hostInstanceKey);

    InstanceInfo instanceInfo = Ec2InstanceInfo.builder()
                                    .ec2Instance(ec2Instance)
                                    .hostName(privateDnsName)
                                    .hostPublicDns(ec2Instance.getPublicDnsName())
                                    .build();

    builder.instanceInfo(instanceInfo);
  }

  @VisibleForTesting
  String getPrivateDnsName(String privateDnsNameWithSuffix) {
    // e.g. null, "", "   "
    if (StringUtils.isEmpty(privateDnsNameWithSuffix) || StringUtils.isBlank(privateDnsNameWithSuffix)) {
      return StringUtils.EMPTY;
    }

    // "ip-172-31-11-6.ec2.internal", we return ip-172-31-11-6
    if (privateDnsNameWithSuffix.indexOf('.') != -1) {
      return privateDnsNameWithSuffix.substring(0, privateDnsNameWithSuffix.indexOf('.'));
    }

    return privateDnsNameWithSuffix;
  }

  public InstanceBuilder buildInstanceBase(WorkflowExecution workflowExecution, Artifact artifact,
      PhaseExecutionData phaseExecutionData, PhaseStepExecutionData phaseStepExecutionData,
      InfrastructureMapping infrastructureMapping, SettingAttribute cloudProviderSetting) {
    PipelineSummary pipelineSummary = workflowExecution.getPipelineSummary();
    Application application = appService.get(workflowExecution.getAppId());
    notNullCheck("Application", application);
    EmbeddedUser triggeredBy = workflowExecution.getTriggeredBy();
    Validator.notNullCheck("triggeredBy", triggeredBy);
    String computeProviderName = cloudProviderSetting == null ? null : cloudProviderSetting.getName();

    InstanceBuilder builder =
        Instance.builder()
            .accountId(application.getAccountId())
            .appId(workflowExecution.getAppId())
            .appName(workflowExecution.getAppName())
            .envName(workflowExecution.getEnvName())
            .envId(workflowExecution.getEnvId())
            .envType(workflowExecution.getEnvType())
            .computeProviderId(phaseExecutionData.getComputeProviderId())
            .computeProviderName(computeProviderName)
            .infraMappingId(infrastructureMapping.getUuid())
            .infraMappingName(infrastructureMapping.getDisplayName())
            .infraMappingType(infrastructureMapping.getInfraMappingType())
            .lastDeployedAt(phaseStepExecutionData.getEndTs() != null ? phaseStepExecutionData.getEndTs()
                                                                      : System.currentTimeMillis())
            .lastDeployedById(triggeredBy.getUuid())
            .lastDeployedByName(triggeredBy.getName())
            .serviceId(phaseExecutionData.getServiceId())
            .serviceName(phaseExecutionData.getServiceName())
            .lastWorkflowExecutionId(workflowExecution.getUuid());
    if (artifact != null) {
      builder.lastArtifactId(artifact.getUuid())
          .lastArtifactName(artifact.getDisplayName())
          .lastArtifactStreamId(artifact.getArtifactStreamId())
          .lastArtifactSourceName(artifact.getArtifactSourceName())
          .lastArtifactBuildNum(artifact.getBuildNo());
    }

    if (pipelineSummary != null) {
      builder.lastPipelineExecutionId(pipelineSummary.getPipelineId())
          .lastPipelineExecutionName(pipelineSummary.getPipelineName());
    }

    String workflowName = instanceUtil.getWorkflowName(workflowExecution.normalizedName());
    notNullCheck("WorkflowName", workflowName);
    builder.lastWorkflowExecutionName(workflowName);

    instanceUtil.setInstanceType(builder, infrastructureMapping.getInfraMappingType());

    return builder;
  }

  private void setInstanceInfoAndKey(
      InstanceBuilder builder, Host host, String infraMappingType, String infraMappingId) {
    InstanceInfo instanceInfo = null;
    HostInstanceKey hostInstanceKey =
        HostInstanceKey.builder().hostName(host.getHostName()).infraMappingId(infraMappingId).build();
    builder.hostInstanceKey(hostInstanceKey);

    if (AZURE_INFRA.name().equals(infraMappingType)) {
      instanceInfo = AzureVMSSInstanceInfo.builder().host(host.getHostName()).build();
    }

    if (AZURE_WEBAPP.name().equals(infraMappingType)) {
      instanceInfo = AzureWebAppInstanceInfo.builder().host(host.getHostName()).build();
    }

    if (AWS_SSH.getName().equals(infraMappingType)) {
      instanceInfo = Ec2InstanceInfo.builder()
                         .ec2Instance(host.getEc2Instance())
                         .hostId(host.getUuid())
                         .hostName(host.getHostName())
                         .hostPublicDns(host.getPublicDns())
                         .build();
    }
    if (PHYSICAL_DATA_CENTER_SSH.getName().equals(infraMappingType)
        || PHYSICAL_DATA_CENTER_WINRM.getName().equals(infraMappingType)) {
      instanceInfo = PhysicalHostInstanceInfo.builder()
                         .hostPublicDns(host.getPublicDns())
                         .hostId(host.getUuid())
                         .hostName(host.getHostName())
                         .build();
    }

    builder.instanceInfo(instanceInfo);
  }

  public void processDeploymentEvent(DeploymentEvent deploymentEvent) {
    try {
      List<DeploymentSummary> deploymentSummaries = deploymentEvent.getDeploymentSummaries();

      if (isEmpty(deploymentSummaries)) {
        log.error("Deployment Summaries can not be empty or null");
        return;
      }

      deploymentSummaries = deploymentSummaries.stream().filter(this::hasDeploymentKey).collect(Collectors.toList());

      deploymentSummaries.forEach(deploymentSummary -> saveDeploymentSummary(deploymentSummary, false));

      processDeploymentSummaries(
          deploymentSummaries, deploymentEvent.isRollback(), deploymentEvent.getOnDemandRollbackInfo());
    } catch (Exception ex) {
      log.error("Error while processing deployment event {}. Skipping the deployment event", deploymentEvent.getId());
    }
  }

  @VisibleForTesting
  boolean shouldSaveDeploymentSummary(DeploymentSummary summary, boolean isRollback) {
    if (summary == null) {
      return false;
    }
    if (!isRollback) {
      return true;
    }
    // save rollback for lambda deployments
    return summary.getAwsLambdaDeploymentKey() != null;
  }

  @VisibleForTesting
  DeploymentSummary saveDeploymentSummary(DeploymentSummary deploymentSummary, boolean rollback) {
    if (shouldSaveDeploymentSummary(deploymentSummary, rollback)) {
      return deploymentService.save(deploymentSummary);
    }
    return deploymentSummary;
  }

  @VisibleForTesting
  boolean hasDeploymentKey(DeploymentSummary deploymentSummary) {
    return deploymentSummary.getPcfDeploymentKey() != null || deploymentSummary.getK8sDeploymentKey() != null
        || deploymentSummary.getContainerDeploymentKey() != null || deploymentSummary.getAwsAmiDeploymentKey() != null
        || deploymentSummary.getAwsCodeDeployDeploymentKey() != null
        || deploymentSummary.getSpotinstAmiDeploymentKey() != null
        || deploymentSummary.getAwsLambdaDeploymentKey() != null
        || deploymentSummary.getAzureVMSSDeploymentKey() != null
        || deploymentSummary.getAzureWebAppDeploymentKey() != null
        || deploymentSummary.getCustomDeploymentKey() != null;
  }

  private void processDeploymentSummaries(
      List<DeploymentSummary> deploymentSummaries, boolean isRollback, OnDemandRollbackInfo onDemandRollbackInfo) {
    if (isEmpty(deploymentSummaries)) {
      return;
    }

    long startTime = System.currentTimeMillis();
    String accountId = deploymentSummaries.iterator().next().getAccountId();
    String infraMappingId = deploymentSummaries.iterator().next().getInfraMappingId();
    String appId = deploymentSummaries.iterator().next().getAppId();
    String workflowExecutionId = deploymentSummaries.iterator().next().getWorkflowExecutionId();
    try (AcquiredLock lock = persistentLocker.waitToAcquireLock(
             InfrastructureMapping.class, infraMappingId, Duration.ofSeconds(200), Duration.ofSeconds(220))) {
      log.info("Handling deployment event for infraMappingId [{}] of appId [{}]", infraMappingId, appId);

      InfrastructureMapping infraMapping = infraMappingService.get(appId, infraMappingId);
      notNullCheck("Infra mapping is null for the given id: " + infraMappingId, infraMapping);

      InfrastructureMappingType infrastructureMappingType =
          Utils.getEnumFromString(InfrastructureMappingType.class, infraMapping.getInfraMappingType());
      Preconditions.checkNotNull(infrastructureMappingType, "InfrastructureMappingType should not be null");

      InstanceHandler instanceHandler = instanceHandlerFactory.getInstanceHandler(infraMapping);
      instanceHandler.handleNewDeployment(deploymentSummaries, isRollback, onDemandRollbackInfo);
      createPerpetualTaskForNewDeploymentIfEnabled(infraMapping, deploymentSummaries);
      log.info("Handled deployment event for infraMappingId [{}] successfully", infraMappingId);

    } catch (Exception ex) {
      // We have to catch all kinds of runtime exceptions, log it and move on, otherwise the queue impl keeps retrying
      // forever in case of exception
      log.warn("Exception while handling deployment event for executionId [{}], infraMappingId [{}]",
          workflowExecutionId, infraMappingId, ex);
    } finally {
      instanceSyncMonitoringService.recordMetrics(accountId, false, true, System.currentTimeMillis() - startTime);
    }
  }

  private Optional<InstanceHandler> getInstanceHandler(InfrastructureMapping infraMapping) {
    return Optional.of(instanceHandlerFactory.getInstanceHandler(infraMapping));
  }

  public boolean isDeployPhaseStep(PhaseStepType phaseStepType) {
    switch (phaseStepType) {
      case DEPLOY_SERVICE:
      case CONTAINER_DEPLOY:
      case CONTAINER_SETUP:
      case PCF_RESIZE:
      case PCF_SWICH_ROUTES:
      case DEPLOY_AWSCODEDEPLOY:
      case DEPLOY_AWS_LAMBDA:
      case AMI_DEPLOY_AUTOSCALING_GROUP:
      case HELM_DEPLOY:
      case CLUSTER_SETUP:
      case K8S_PHASE_STEP:
      case SPOTINST_DEPLOY:
      case SPOTINST_ROLLBACK:
      case SPOTINST_LISTENER_UPDATE_ROLLBACK:
      case AZURE_VMSS_DEPLOY:
      case AZURE_VMSS_ROLLBACK:
      case AZURE_VMSS_SWITCH_ROUTES:
      case AZURE_VMSS_SWITCH_ROLLBACK:
      case AZURE_WEBAPP_SLOT_SETUP:
      case AZURE_WEBAPP_SLOT_ROLLBACK:
      case CUSTOM_DEPLOYMENT_PHASE_STEP:
        return true;
      default:
        return false;
    }
  }

  public void extractInstance(PhaseStepSubWorkflow phaseStepSubWorkflow, ExecutionEvent executionEvent,
      WorkflowExecution workflowExecution, ExecutionContext context, StateExecutionInstance stateExecutionInstance) {
    if (isDeployPhaseStep(phaseStepSubWorkflow.getPhaseStepType())
        && ExecutionStatus.isFinalStatus(executionEvent.getExecutionStatus())) {
      WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
      notNullCheck("params are null for workflow " + workflowExecution.getUuid(), workflowStandardParams);

      PhaseStepExecutionData phaseStepExecutionData =
          (PhaseStepExecutionData) stateExecutionInstance.fetchStateExecutionData();
      notNullCheck("phase step execution data is null for phase step " + phaseStepSubWorkflow.getId(),
          phaseStepExecutionData, USER_SRE);

      StateExecutionInstance phaseStateExecutionInstance = workflowExecutionService.getStateExecutionData(
          workflowExecution.getAppId(), stateExecutionInstance.getParentInstanceId());

      if (phaseStateExecutionInstance != null) {
        StateExecutionData stateExecutionData = phaseStateExecutionInstance.fetchStateExecutionData();
        notNullCheck("state execution data is null for " + phaseStepSubWorkflow.getParentId(), stateExecutionData);

        if (stateExecutionData instanceof PhaseExecutionData) {
          extractInstanceOrDeploymentInfoBaseOnType(context.getStateExecutionInstanceId(),
              (PhaseExecutionData) stateExecutionData, phaseStepExecutionData, workflowStandardParams,
              context.getAppId(), workflowExecution, phaseStepSubWorkflow, context);
        } else {
          log.warn("Fetched execution data is not of type phase for phase step {}", phaseStepSubWorkflow.getId());
        }
      } else {
        log.warn("Could not locate phase for phase step {}", phaseStepSubWorkflow.getId());
      }
    }
  }

  public String manualSync(String appId, String infraMappingId) {
    String syncJobId = UUIDGenerator.generateUuid();
    InfrastructureMapping infrastructureMapping = infraMappingService.get(appId, infraMappingId);
    String accountId = infrastructureMapping.getAccountId();
    instanceService.saveManualSyncJob(
        ManualSyncJob.builder().uuid(syncJobId).accountId(accountId).appId(appId).build());
    executorService.submit(() -> {
      try {
        syncNow(appId, infrastructureMapping, MANUAL);
      } finally {
        instanceService.deleteManualSyncJob(appId, syncJobId);
      }
    });
    return syncJobId;
  }

  public void syncNow(String appId, InfrastructureMapping infraMapping, InstanceSyncFlow instanceSyncFlow) {
    if (infraMapping == null) {
      return;
    }

    String infraMappingId = infraMapping.getUuid();
    try (AcquiredLock lock =
             persistentLocker.tryToAcquireLock(InfrastructureMapping.class, infraMappingId, Duration.ofSeconds(180))) {
      if (lock == null) {
        log.warn("Couldn't acquire infra lock. appId [{}]", appId);
        return;
      }

      try {
        InstanceHandler instanceHandler = instanceHandlerFactory.getInstanceHandler(infraMapping);
        if (instanceHandler == null) {
          log.warn("Instance handler null. appId [{}]", appId);
          return;
        }
        log.info("Instance sync started");
        instanceHandler.syncInstances(appId, infraMappingId, instanceSyncFlow);
        instanceService.updateSyncSuccess(appId, infraMapping.getServiceId(), infraMapping.getEnvId(), infraMappingId,
            infraMapping.getDisplayName(), System.currentTimeMillis());
        log.info("Instance sync completed");
      } catch (Exception ex) {
        log.warn("Instance sync failed", ex);
        String errorMsg = getErrorMsg(ex);

        instanceService.handleSyncFailure(appId, infraMapping.getServiceId(), infraMapping.getEnvId(), infraMappingId,
            infraMapping.getDisplayName(), System.currentTimeMillis(), errorMsg);
      }
    }
  }

  public List<Boolean> getManualSyncJobsStatus(String accountId, Set<String> manualSyncJobIdSet) {
    return instanceService.getManualSyncJobsStatus(accountId, manualSyncJobIdSet);
  }

  public boolean shouldSkipIteratorInstanceSync(InfrastructureMapping infrastructureMapping) {
    Optional<InstanceHandler> instanceHandler = getInstanceHandler(infrastructureMapping);

    return instanceHandler.isPresent()
        && instanceHandler.get()
               .getFeatureFlagToStopIteratorBasedInstanceSync()
               .map(featureName -> featureFlagService.isEnabled(featureName, infrastructureMapping.getAccountId()))
               .orElse(true);
  }

  @VisibleForTesting
  void createPerpetualTaskForNewDeploymentIfEnabled(
      InfrastructureMapping infrastructureMapping, List<DeploymentSummary> deploymentSummaries) {
    if (isInstanceSyncByPerpetualTaskEnabled(infrastructureMapping)) {
      log.info("Creating Perpetual tasks for new deployment for infrastructure mapping [{}]",
          infrastructureMapping.getUuid());
      instanceSyncPerpetualTaskService.createPerpetualTasksForNewDeployment(infrastructureMapping, deploymentSummaries);
    }
  }

  private boolean isInstanceSyncByPerpetualTaskEnabled(InfrastructureMapping infrastructureMapping) {
    Optional<InstanceHandler> instanceHandler = getInstanceHandler(infrastructureMapping);
    if (!instanceHandler.isPresent()) {
      return false;
    }

    if (instanceHandler.get() instanceof InstanceSyncByPerpetualTaskHandler) {
      InstanceSyncByPerpetualTaskHandler handler = (InstanceSyncByPerpetualTaskHandler) instanceHandler.get();
      return handler.getFeatureFlagToEnablePerpetualTaskForInstanceSync()
          .map(featureName -> featureFlagService.isEnabled(featureName, infrastructureMapping.getAccountId()))
          .orElse(true);
    }

    return false;
  }

  public void processInstanceSyncResponseFromPerpetualTask(String perpetualTaskId, DelegateResponseData response) {
    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskService.getTaskRecord(perpetualTaskId);
    Map<String, String> clientParams = perpetualTaskRecord.getClientContext().getClientParams();

    String accountId = clientParams.get(HARNESS_ACCOUNT_ID);
    String appId = clientParams.get(HARNESS_APPLICATION_ID);
    String infrastructureMappingId = clientParams.get(INFRASTRUCTURE_MAPPING_ID);

    InfrastructureMapping infrastructureMapping = infraMappingService.get(appId, infrastructureMappingId);
    if (infrastructureMapping == null) {
      log.info(
          "Handling Instance sync response. Infrastructure Mapping does not exist for Id : [{}]. Deleting Perpetual Tasks ",
          infrastructureMappingId);
      instanceSyncPerpetualTaskService.deletePerpetualTasks(accountId, infrastructureMappingId);
      return;
    }

    if (featureFlagService.isEnabled(INSTANCE_SYNC_V2_CG, infrastructureMapping.getAccountId())) {
      InstanceSyncTaskDetails instanceSyncV2TaskDetails =
          taskDetailsService.getForInfraMapping(infrastructureMapping.getAccountId(), infrastructureMapping.getUuid());
      if (Objects.nonNull(instanceSyncV2TaskDetails) && instanceSyncV2TaskDetails.getLastSuccessfulRun() > 0) {
        log.info(
            "[INSTANCE_SYNC_V2_CG] Instance Sync for infra mapping: [{}] is moved to new Instance Sync V2 framework, and is handled via Perpetual Task Id: [{}], and instance sync task details id: [{}]. Skipping consuming response for this.",
            infrastructureMapping.getUuid(), instanceSyncV2TaskDetails.getPerpetualTaskId(),
            instanceSyncV2TaskDetails.getUuid());

        instanceSyncPerpetualTaskService.deletePerpetualTask(
            infrastructureMapping.getAccountId(), infrastructureMappingId, perpetualTaskId, true);
        log.info(
            "[INSTANCE_SYNC_V2_CG] Perpetual task with Id: [{}] deleted for infra mapping Id: [{}]. This is now migrated to new perpetual task: [{}], and instance sync task details: [{}]",
            perpetualTaskId, infrastructureMappingId, instanceSyncV2TaskDetails.getPerpetualTaskId(),
            instanceSyncV2TaskDetails.getUuid());
        return;
      }
    }
    Environment environment = environmentService.get(appId, infrastructureMapping.getEnvId(), false);
    Service service = serviceResourceService.getWithDetails(appId, infrastructureMapping.getServiceId());
    if (environment == null || service == null) {
      instanceSyncPerpetualTaskService.deletePerpetualTask(
          infrastructureMapping.getAccountId(), infrastructureMappingId, perpetualTaskId, true);
      if (environment == null) {
        throw new GeneralException("Environment is null for the given id: " + infrastructureMapping.getEnvId());
      } else {
        throw new GeneralException("Service is null for the given id: " + infrastructureMapping.getServiceId());
      }
    }

    log.debug("Handling Instance sync response. Infrastructure Mapping : [{}]", infrastructureMapping.getUuid());

    try (AcquiredLock<?> lock = persistentLocker.tryToAcquireLock(
             InfrastructureMapping.class, infrastructureMapping.getUuid(), Duration.ofSeconds(180))) {
      if (lock == null) {
        log.warn("Couldn't acquire lock on Infrastructure Mapping : [{}], Application Id : [{}]",
            infrastructureMappingId, appId);
        return;
      }
      handleInstanceSyncResponseFromPerpetualTask(infrastructureMapping, perpetualTaskRecord, response);
    }

    log.debug(
        "Handled Instance sync response successfully. Infrastructure Mapping : [{}]", infrastructureMapping.getUuid());
  }

  private void handleInstanceSyncResponseFromPerpetualTask(InfrastructureMapping infrastructureMapping,
      PerpetualTaskRecord perpetualTaskRecord, DelegateResponseData response) {
    long startTime = System.currentTimeMillis();
    Optional<InstanceHandler> instanceHandler = getInstanceHandler(infrastructureMapping);
    if (!instanceHandler.isPresent() || !isInstanceSyncByPerpetualTaskEnabled(infrastructureMapping)) {
      return;
    }

    InstanceSyncByPerpetualTaskHandler handler = (InstanceSyncByPerpetualTaskHandler) instanceHandler.get();

    try {
      handler.processInstanceSyncResponseFromPerpetualTask(infrastructureMapping, response);
    } catch (NoInstancesException ex) {
      checkAndDeletePerpetualTask(infrastructureMapping, ex);
    } catch (Exception ex) {
      checkAndDeletePerpetualTask(infrastructureMapping, ex);
      throw ex;
    } finally {
      Status status = handler.getStatus(infrastructureMapping, response);
      if (status.isSuccess()) {
        instanceService.updateSyncSuccess(infrastructureMapping.getAppId(), infrastructureMapping.getServiceId(),
            infrastructureMapping.getEnvId(), infrastructureMapping.getUuid(), infrastructureMapping.getDisplayName(),
            System.currentTimeMillis());
      }
      if (!status.isRetryable()) {
        log.info("Task Not Retryable. Deleting Perpetual Task. Infrastructure Mapping : [{}]",
            infrastructureMapping.getUuid());
        instanceSyncPerpetualTaskService.deletePerpetualTask(
            infrastructureMapping.getAccountId(), infrastructureMapping.getUuid(), perpetualTaskRecord.getUuid());
      }
      if (!status.isSuccess()) {
        log.info("Sync Failure. Reset Perpetual Task. Infrastructure Mapping : [{}]", infrastructureMapping.getUuid());
        instanceSyncPerpetualTaskService.resetPerpetualTask(
            infrastructureMapping.getAccountId(), perpetualTaskRecord.getUuid());
      }
      instanceSyncMonitoringService.recordMetrics(
          infrastructureMapping.getAccountId(), false, false, System.currentTimeMillis() - startTime);
    }
  }

  private void checkAndDeletePerpetualTask(InfrastructureMapping infrastructureMapping, Exception ex) {
    String errorMsg = getErrorMsg(ex);

    boolean continueSync = instanceService.handleSyncFailure(infrastructureMapping.getAppId(),
        infrastructureMapping.getServiceId(), infrastructureMapping.getEnvId(), infrastructureMapping.getUuid(),
        infrastructureMapping.getDisplayName(), System.currentTimeMillis(), errorMsg);

    if (!continueSync) {
      log.info(
          "Sync Status Failure for Infrastructure Mapping : [{}], Deleting all perpetual tasks for given infrastructure mapping",
          infrastructureMapping.getUuid());
      instanceSyncPerpetualTaskService.deletePerpetualTasks(infrastructureMapping);
    }
  }

  private String getErrorMsg(Exception ex) {
    String errorMsg;
    if (ex instanceof WingsException) {
      errorMsg = ex.getMessage();
    } else {
      errorMsg = ex.getMessage() != null ? ex.getMessage() : "Unknown error";
    }
    return errorMsg;
  }
}
