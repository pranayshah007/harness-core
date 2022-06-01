/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler.deploy;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.InstanceUnitType.PERCENTAGE;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.substringBefore;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.container.ContainerInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.Misc;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;

import software.wings.api.ContainerServiceData;
import software.wings.beans.AwsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.command.EcsResizeParams;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.container.AwsAutoScalarConfig;
import software.wings.cloudprovider.aws.AwsClusterService;
import software.wings.cloudprovider.aws.EcsContainerService;
import software.wings.delegatetasks.aws.ecs.ecstaskhandler.EcsCommandTaskHelper;
import software.wings.helpers.ext.ecs.request.EcsRunTaskDeployRequest;
import software.wings.helpers.ext.ecs.response.EcsDeployRollbackDataFetchResponse;
import software.wings.helpers.ext.ecs.response.EcsRunTaskDeployResponse;
import software.wings.helpers.ext.ecs.response.EcsServiceDeployResponse;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.intfc.aws.delegate.AwsAppAutoScalingHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsEcsHelperServiceDelegate;

import com.amazonaws.services.applicationautoscaling.model.DeregisterScalableTargetRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsResult;
import com.amazonaws.services.applicationautoscaling.model.ScalableTarget;
import com.amazonaws.services.applicationautoscaling.model.ServiceNamespace;
import com.amazonaws.services.ecs.model.AssignPublicIp;
import com.amazonaws.services.ecs.model.AwsVpcConfiguration;
import com.amazonaws.services.ecs.model.DeregisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.DeregisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DesiredStatus;
import com.amazonaws.services.ecs.model.LaunchType;
import com.amazonaws.services.ecs.model.ListTasksRequest;
import com.amazonaws.services.ecs.model.NetworkConfiguration;
import com.amazonaws.services.ecs.model.NetworkMode;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RunTaskRequest;
import com.amazonaws.services.ecs.model.RunTaskResult;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.Task;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class EcsDeployCommandTaskHelper {
  private static final String DELIMITER = "__";
  private static final String CONTAINER_NAME_PLACEHOLDER_REGEX = "\\$\\{CONTAINER_NAME}";
  @Inject private AwsClusterService awsClusterService;
  @Inject private AwsAppAutoScalingHelperServiceDelegate awsAppAutoScalingService;
  @Inject private AwsHelperService awsHelperService;
  @Inject private AwsEcsHelperServiceDelegate awsEcsHelperServiceDelegate;
  @Inject private EcsContainerService ecsContainerService;
  @Inject private EcsCommandTaskHelper ecsCommandTaskHelper;

  public void deregisterAutoScalarsIfExists(ContextData contextData, ExecutionLogCallback executionLogCallback) {
    EcsResizeParams resizeParams = contextData.getResizeParams();
    if (resizeParams.isPreviousEcsAutoScalarsAlreadyRemoved()
        || isEmpty(resizeParams.getPreviousAwsAutoScalarConfigs())) {
      return;
    }

    resizeParams.getPreviousAwsAutoScalarConfigs().forEach(awsAutoScalarConfig -> {
      if (StringUtils.isNotBlank(awsAutoScalarConfig.getScalableTargetJson())) {
        ScalableTarget target =
            awsAppAutoScalingService.getScalableTargetFromJson(awsAutoScalarConfig.getScalableTargetJson());

        // Fetch target from AWS
        DescribeScalableTargetsResult result = awsAppAutoScalingService.listScalableTargets(resizeParams.getRegion(),
            contextData.getAwsConfig(), contextData.getEncryptedDataDetails(),
            new DescribeScalableTargetsRequest()
                .withResourceIds(target.getResourceId())
                .withServiceNamespace(ServiceNamespace.Ecs)
                .withScalableDimension(target.getScalableDimension()));

        // If Target exists, delete it
        if (isNotEmpty(result.getScalableTargets())) {
          executionLogCallback.saveExecutionLog("De-registering Scalable Target :" + target.getResourceId());
          awsAppAutoScalingService.deregisterScalableTarget(resizeParams.getRegion(), contextData.getAwsConfig(),
              contextData.getEncryptedDataDetails(),
              new DeregisterScalableTargetRequest()
                  .withResourceId(target.getResourceId())
                  .withScalableDimension(target.getScalableDimension())
                  .withServiceNamespace(ServiceNamespace.Ecs));
          executionLogCallback.saveExecutionLog(new StringBuilder()
                                                    .append("De-registered Scalable Target Successfully: ")
                                                    .append(target.getResourceId())
                                                    .append("\n")
                                                    .toString());
        }
      }
    });
  }

  /**
   * Delete autoScalar targets and policies created for New service that is being downsized in rollback
   *
   * @param contextData
   * @param executionLogCallback
   */
  public void deleteAutoScalarForNewService(ContextData contextData, ExecutionLogCallback executionLogCallback) {
    EcsResizeParams resizeParams = contextData.getResizeParams();
    AwsConfig awsConfig = contextData.getAwsConfig();
    String serviceName = resizeParams.getContainerServiceName();
    String resourceId = ecsCommandTaskHelper.getResourceIdForEcsService(serviceName, resizeParams.getClusterName());

    DescribeScalableTargetsResult targetsResult = awsAppAutoScalingService.listScalableTargets(resizeParams.getRegion(),
        awsConfig, contextData.getEncryptedDataDetails(),
        new DescribeScalableTargetsRequest()
            .withServiceNamespace(ServiceNamespace.Ecs)
            .withResourceIds(Arrays.asList(resourceId)));

    if (isEmpty(targetsResult.getScalableTargets())) {
      return;
    }

    targetsResult.getScalableTargets().forEach(scalableTarget -> {
      executionLogCallback.saveExecutionLog("De-registering AutoScalable Target : " + scalableTarget.getResourceId());
      awsAppAutoScalingService.deregisterScalableTarget(resizeParams.getRegion(), awsConfig,
          contextData.getEncryptedDataDetails(),
          new DeregisterScalableTargetRequest()
              .withServiceNamespace(ServiceNamespace.Ecs)
              .withResourceId(resourceId)
              .withScalableDimension(scalableTarget.getScalableDimension()));
      executionLogCallback.saveExecutionLog(
          "Successfully De-registered AutoScalable Target : " + scalableTarget.getResourceId());
    });
  }

  void restoreAutoScalarConfigsRedesigned(ContextData contextData, ExecutionLogCallback executionLogCallback) {
    EcsResizeParams resizeParams = contextData.getResizeParams();

    // This is for services those are being upsized in Rollback
    List<AwsAutoScalarConfig> awsAutoScalarConfigs = resizeParams.getPreviousAwsAutoScalarConfigs();

    if (isEmpty(awsAutoScalarConfigs)) {
      executionLogCallback.saveExecutionLog("No Auto-scalar configs to restore");
      return;
    }

    AwsConfig awsConfig = contextData.getAwsConfig();

    awsAutoScalarConfigs.forEach(awsAutoScalarConfig -> {
      if (StringUtils.isNotBlank(awsAutoScalarConfig.getScalableTargetJson())) {
        ScalableTarget scalableTarget =
            awsAppAutoScalingService.getScalableTargetFromJson(awsAutoScalarConfig.getScalableTargetJson());

        DescribeScalableTargetsResult result = awsAppAutoScalingService.listScalableTargets(resizeParams.getRegion(),
            awsConfig, contextData.getEncryptedDataDetails(),
            new DescribeScalableTargetsRequest()
                .withResourceIds(Arrays.asList(scalableTarget.getResourceId()))
                .withScalableDimension(scalableTarget.getScalableDimension())
                .withServiceNamespace(ServiceNamespace.Ecs));

        if (isEmpty(result.getScalableTargets())) {
          ecsCommandTaskHelper.registerScalableTargetForEcsService(awsAppAutoScalingService, resizeParams.getRegion(),
              awsConfig, contextData.getEncryptedDataDetails(), executionLogCallback, scalableTarget);

          if (isNotEmpty(awsAutoScalarConfig.getScalingPolicyJson())) {
            for (String policyJson : awsAutoScalarConfig.getScalingPolicyJson()) {
              ecsCommandTaskHelper.upsertScalingPolicyIfRequired(policyJson, scalableTarget.getResourceId(),
                  scalableTarget.getScalableDimension(), resizeParams.getRegion(), awsConfig, awsAppAutoScalingService,
                  contextData.getEncryptedDataDetails(), executionLogCallback);
            }
          }
        }
      }
    });
  }

  public void restoreAutoScalarConfigs(
      ContextData contextData, ContainerServiceData containerServiceData, ExecutionLogCallback executionLogCallback) {
    EcsResizeParams resizeParams = contextData.getResizeParams();

    // This action is required in rollback stage only
    if (!resizeParams.isRollback()) {
      return;
    }

    // Delete eauto scalar for newly created Service that will be downsized as part of rollback
    deleteAutoScalarForNewService(contextData, executionLogCallback);

    // This is for services those are being upsized in Rollbak
    List<AwsAutoScalarConfig> awsAutoScalarConfigs = resizeParams.getPreviousAwsAutoScalarConfigs();

    if (isEmpty(awsAutoScalarConfigs)) {
      executionLogCallback.saveExecutionLog("No Auto-scalar configs to restore");
      return;
    }

    AwsConfig awsConfig = contextData.getAwsConfig();

    awsAutoScalarConfigs.forEach(awsAutoScalarConfig -> {
      if (StringUtils.isNotBlank(awsAutoScalarConfig.getScalableTargetJson())) {
        ScalableTarget scalableTarget =
            awsAppAutoScalingService.getScalableTargetFromJson(awsAutoScalarConfig.getScalableTargetJson());

        DescribeScalableTargetsResult result = awsAppAutoScalingService.listScalableTargets(resizeParams.getRegion(),
            awsConfig, contextData.getEncryptedDataDetails(),
            new DescribeScalableTargetsRequest()
                .withResourceIds(Arrays.asList(scalableTarget.getResourceId()))
                .withScalableDimension(scalableTarget.getScalableDimension())
                .withServiceNamespace(ServiceNamespace.Ecs));

        if (isEmpty(result.getScalableTargets())) {
          ecsCommandTaskHelper.registerScalableTargetForEcsService(awsAppAutoScalingService, resizeParams.getRegion(),
              awsConfig, contextData.getEncryptedDataDetails(), executionLogCallback, scalableTarget);

          if (isNotEmpty(awsAutoScalarConfig.getScalingPolicyJson())) {
            for (String policyJson : awsAutoScalarConfig.getScalingPolicyJson()) {
              ecsCommandTaskHelper.upsertScalingPolicyIfRequired(policyJson, scalableTarget.getResourceId(),
                  scalableTarget.getScalableDimension(), resizeParams.getRegion(), awsConfig, awsAppAutoScalingService,
                  contextData.getEncryptedDataDetails(), executionLogCallback);
            }
          }
        }
      }
    });
  }

  void createAutoScalarConfigIfServiceReachedMaxSizeRedesigned(
      ContextData contextData, ContainerServiceData containerServiceData, ExecutionLogCallback executionLogCallback) {
    EcsResizeParams resizeParams = contextData.getResizeParams();
    if (resizeParams.getAwsAutoScalarConfigForNewService() == null
        || isEmpty(resizeParams.getAwsAutoScalarConfigForNewService())) {
      executionLogCallback.saveExecutionLog("No Autoscalar config provided.");
      return;
    }

    String resourceId =
        ecsCommandTaskHelper.getResourceIdForEcsService(containerServiceData.getName(), resizeParams.getClusterName());

    AwsConfig awsConfig = contextData.getAwsConfig();
    executionLogCallback.saveExecutionLog(
        "\nRegistering Scalable Target for Service : " + containerServiceData.getName());
    resizeParams.getAwsAutoScalarConfigForNewService().forEach(awsAutoScalarConfig -> {
      if (awsAutoScalarConfig == null) {
        String exceptionMsg =
            "Aws Auto-scaler config is Invalid.\n Check Aws Autoscaler config, Scalable Target Json and Scaling Policy Json";
        throw new InvalidRequestException(exceptionMsg);
      }
      if (StringUtils.isNotBlank(awsAutoScalarConfig.getScalableTargetJson())) {
        ScalableTarget scalableTarget =
            awsAppAutoScalingService.getScalableTargetFromJson(awsAutoScalarConfig.getScalableTargetJson());
        scalableTarget.withResourceId(resourceId);
        ecsCommandTaskHelper.registerScalableTargetForEcsService(awsAppAutoScalingService, resizeParams.getRegion(),
            awsConfig, contextData.getEncryptedDataDetails(), executionLogCallback, scalableTarget);

        ecsContainerService.waitForServiceToReachStableState(resizeParams.getRegion(), awsConfig,
            contextData.getEncryptedDataDetails(), resizeParams.getClusterName(),
            resizeParams.getContainerServiceName(), executionLogCallback, resizeParams.getServiceSteadyStateTimeout());

        if (isNotEmpty(awsAutoScalarConfig.getScalingPolicyJson())) {
          executionLogCallback.saveExecutionLog(
              "Creating Auto Scaling Policies for Service: " + containerServiceData.getName());
          for (String policyJson : awsAutoScalarConfig.getScalingPolicyJson()) {
            ecsCommandTaskHelper.upsertScalingPolicyIfRequired(policyJson, resourceId,
                scalableTarget.getScalableDimension(), resizeParams.getRegion(), awsConfig, awsAppAutoScalingService,
                contextData.getEncryptedDataDetails(), executionLogCallback);
          }
        }
      }
    });
  }

  public void createAutoScalarConfigIfServiceReachedMaxSize(
      ContextData contextData, ContainerServiceData containerServiceData, ExecutionLogCallback executionLogCallback) {
    EcsResizeParams resizeParams = contextData.getResizeParams();

    int maxDesiredCount =
        resizeParams.isUseFixedInstances() ? resizeParams.getFixedInstances() : resizeParams.getMaxInstances();

    /*
     * If Rollback or this is not final resize stage, just return.
     * We create AutoScalar after new service has been completely upsized.
     * This method should also not get invoked if the service being sized is NOT the
     * new service.
     */
    if (resizeParams.isRollback() || maxDesiredCount > containerServiceData.getDesiredCount()
        || !resizeParams.getContainerServiceName().equals(containerServiceData.getName())) {
      return;
    }

    if (resizeParams.getAwsAutoScalarConfigForNewService() == null
        || isEmpty(resizeParams.getAwsAutoScalarConfigForNewService())) {
      executionLogCallback.saveExecutionLog("No Autoscalar config provided.");
      return;
    }

    String resourceId =
        ecsCommandTaskHelper.getResourceIdForEcsService(containerServiceData.getName(), resizeParams.getClusterName());

    AwsConfig awsConfig = contextData.getAwsConfig();
    executionLogCallback.saveExecutionLog(
        "\nRegistering Scalable Target for Service : " + containerServiceData.getName());
    resizeParams.getAwsAutoScalarConfigForNewService().forEach(awsAutoScalarConfig -> {
      if (StringUtils.isNotBlank(awsAutoScalarConfig.getScalableTargetJson())) {
        ScalableTarget scalableTarget =
            awsAppAutoScalingService.getScalableTargetFromJson(awsAutoScalarConfig.getScalableTargetJson());
        scalableTarget.withResourceId(resourceId);
        ecsCommandTaskHelper.registerScalableTargetForEcsService(awsAppAutoScalingService, resizeParams.getRegion(),
            awsConfig, contextData.getEncryptedDataDetails(), executionLogCallback, scalableTarget);

        ecsContainerService.waitForServiceToReachStableState(resizeParams.getRegion(), awsConfig,
            contextData.getEncryptedDataDetails(), resizeParams.getClusterName(),
            resizeParams.getContainerServiceName(), executionLogCallback, resizeParams.getServiceSteadyStateTimeout());

        if (isNotEmpty(awsAutoScalarConfig.getScalingPolicyJson())) {
          executionLogCallback.saveExecutionLog(
              "Creating Auto Scaling Policies for Service: " + containerServiceData.getName());
          for (String policyJson : awsAutoScalarConfig.getScalingPolicyJson()) {
            ecsCommandTaskHelper.upsertScalingPolicyIfRequired(policyJson, resourceId,
                scalableTarget.getScalableDimension(), resizeParams.getRegion(), awsConfig, awsAppAutoScalingService,
                contextData.getEncryptedDataDetails(), executionLogCallback);
          }
        }
      }
    });
  }

  public TaskDefinition createRunTaskDefinition(String taskDefinitionJson, String ecsServiceName) {
    TaskDefinition ecsRunTaskDefinition = JsonUtils.asObject(taskDefinitionJson, TaskDefinition.class);
    if (ecsRunTaskDefinition.getFamily() == null) {
      ecsRunTaskDefinition.setFamily(ecsServiceName);
    }
    return ecsRunTaskDefinition;
  }

  public RegisterTaskDefinitionRequest createRunTaskRegisterTaskDefinitionRequest(
      String taskDefinitionJson, String ecsServiceName) {
    RegisterTaskDefinitionRequest registerTaskDefinitionRequest =
        JsonUtils.asObject(taskDefinitionJson, RegisterTaskDefinitionRequest.class);
    if (registerTaskDefinitionRequest.getFamily() == null) {
      registerTaskDefinitionRequest.setFamily(ecsServiceName);
    }
    return registerTaskDefinitionRequest;
  }

  public RunTaskRequest createAwsRunTaskRequest(
      TaskDefinition registeredRunTaskDefinition, EcsRunTaskDeployRequest ecsRunTaskDeployRequest) {
    RunTaskRequest runTaskRequest = new RunTaskRequest();
    runTaskRequest.withCluster(ecsRunTaskDeployRequest.getCluster());
    runTaskRequest.withLaunchType(ecsRunTaskDeployRequest.getLaunchType());

    // For Awsvpc Network mode (Fargate / ECS Ec2 deployment with awsvpc mode), we need to setup
    // NetworkConfig, as it will be used by aws to create ENI
    if (isFargateTaskLauchType(ecsRunTaskDeployRequest.getLaunchType())
        || NetworkMode.Awsvpc.name().equalsIgnoreCase(registeredRunTaskDefinition.getNetworkMode())) {
      AssignPublicIp assignPublicIp = AssignPublicIp.DISABLED;

      if (isFargateTaskLauchType(ecsRunTaskDeployRequest.getLaunchType())) {
        assignPublicIp = ecsRunTaskDeployRequest.isAssignPublicIps() ? AssignPublicIp.ENABLED : AssignPublicIp.DISABLED;
      }

      runTaskRequest.withNetworkConfiguration(new NetworkConfiguration().withAwsvpcConfiguration(
          new AwsVpcConfiguration()
              .withSecurityGroups(ecsRunTaskDeployRequest.getSecurityGroupIds())
              .withSubnets(ecsRunTaskDeployRequest.getSubnetIds())
              .withAssignPublicIp(assignPublicIp)));
    }

    runTaskRequest.withPropagateTags("TASK_DEFINITION");
    runTaskRequest.withTaskDefinition(registeredRunTaskDefinition.getTaskDefinitionArn());

    return runTaskRequest;
  }

  public TaskDefinition registerRunTaskDefinition(SettingAttribute cloudProviderSetting,
      TaskDefinition runTaskDefinition, String launchType, String region,
      List<EncryptedDataDetail> encryptedDataDetails, ExecutionLogCallback executionLogCallback) {
    RegisterTaskDefinitionRequest registerTaskDefinitionRequest =
        new RegisterTaskDefinitionRequest()
            .withContainerDefinitions(runTaskDefinition.getContainerDefinitions())
            .withFamily(runTaskDefinition.getFamily())
            .withTaskRoleArn(runTaskDefinition.getTaskRoleArn())
            .withNetworkMode(runTaskDefinition.getNetworkMode())
            .withPlacementConstraints(runTaskDefinition.getPlacementConstraints())
            .withVolumes(runTaskDefinition.getVolumes());

    if (isNotEmpty(runTaskDefinition.getExecutionRoleArn())) {
      registerTaskDefinitionRequest.withExecutionRoleArn(runTaskDefinition.getExecutionRoleArn());
    }

    // Add extra parameters for Fargate launch type
    if (isFargateTaskLauchType(launchType)) {
      registerTaskDefinitionRequest.withNetworkMode(NetworkMode.Awsvpc);
      registerTaskDefinitionRequest.setRequiresCompatibilities(Collections.singletonList(LaunchType.FARGATE.name()));
      registerTaskDefinitionRequest.withCpu(runTaskDefinition.getCpu());
      registerTaskDefinitionRequest.withMemory(runTaskDefinition.getMemory());
    }

    executionLogCallback.saveExecutionLog(
        format("Registering task definition with family => %s", runTaskDefinition.getFamily()), LogLevel.INFO);

    return awsClusterService.createTask(
        region, cloudProviderSetting, encryptedDataDetails, registerTaskDefinitionRequest);
  }

  public TaskDefinition registerRunTaskDefinitionWithRegisterTaskDefinitionRequest(
      SettingAttribute cloudProviderSetting, RegisterTaskDefinitionRequest registerTaskDefinitionRequest,
      String launchType, String region, List<EncryptedDataDetail> encryptedDataDetails,
      ExecutionLogCallback executionLogCallback) {
    if (isEmpty(registerTaskDefinitionRequest.getExecutionRoleArn())) {
      registerTaskDefinitionRequest.withExecutionRoleArn(null);
    }

    // Add extra parameters for Fargate launch type
    if (isFargateTaskLauchType(launchType)) {
      registerTaskDefinitionRequest.withNetworkMode(NetworkMode.Awsvpc);
      registerTaskDefinitionRequest.setRequiresCompatibilities(Collections.singletonList(LaunchType.FARGATE.name()));
    } else {
      registerTaskDefinitionRequest.withCpu(null);
      registerTaskDefinitionRequest.withMemory(null);
    }

    executionLogCallback.saveExecutionLog(
        format("Registering task definition with family => %s", registerTaskDefinitionRequest.getFamily()),
        LogLevel.INFO);

    return awsClusterService.createTask(
        region, cloudProviderSetting, encryptedDataDetails, registerTaskDefinitionRequest);
  }

  public RunTaskResult triggerRunTask(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, RunTaskRequest runTaskRequest) {
    return awsClusterService.triggerRunTask(region, cloudProviderSetting, encryptedDataDetails, runTaskRequest);
  }

  private boolean isFargateTaskLauchType(String launchType) {
    return LaunchType.FARGATE.name().equals(launchType);
  }

  public boolean getDeployingToHundredPercent(EcsResizeParams resizeParams) {
    boolean deployingToHundredPercent;
    if (resizeParams.isRollback()) {
      deployingToHundredPercent = false;
    } else {
      Preconditions.checkNotNull(resizeParams.getInstanceCount());
      deployingToHundredPercent = resizeParams.getInstanceUnitType() == PERCENTAGE
          ? resizeParams.getInstanceCount() >= 100
          : (resizeParams.isUseFixedInstances() && resizeParams.getInstanceCount() >= resizeParams.getFixedInstances())
              || (!resizeParams.isUseFixedInstances()
                  && resizeParams.getInstanceCount() >= resizeParams.getMaxInstances());
    }

    return deployingToHundredPercent;
  }

  public ContainerServiceData getNewInstanceData(ContextData contextData, ExecutionLogCallback logCallback) {
    Optional<Integer> previousDesiredCount = getServiceDesiredCount(contextData);

    String containerServiceName = contextData.getResizeParams().getContainerServiceName();
    if (!previousDesiredCount.isPresent()) {
      throw new InvalidRequestException("Service setup not done, service name: " + containerServiceName);
    }

    int previousCount = previousDesiredCount.get();
    int desiredCount = getNewInstancesDesiredCount(contextData);

    if (desiredCount < previousCount) {
      if (contextData.getResizeParams().isEcsAutoscalarRedesignEnabled()) {
        String message =
            format("Desired count: [%d] is less than previous count: [%d]. Updating desired count to: [%d]",
                desiredCount, previousCount, previousCount);
        logCallback.saveExecutionLog(message);
        desiredCount = previousCount;
      } else {
        String msg = "Desired instance count must be greater than or equal to the current instance count: {current: "
            + previousCount + ", desired: " + desiredCount + "}";
        log.error(msg);
        throw new InvalidRequestException(msg);
      }
    }

    return ContainerServiceData.builder()
        .name(containerServiceName)
        .image(contextData.getResizeParams().getImage())
        .previousCount(previousCount)
        .desiredCount(desiredCount)
        .previousTraffic(0)
        .desiredTraffic(0)
        .build();
  }

  public Optional<Integer> getServiceDesiredCount(ContextData contextData) {
    EcsResizeParams resizeParams = contextData.getResizeParams();
    Optional<Service> service = awsClusterService.getService(resizeParams.getRegion(),
        contextData.getSettingAttribute(), contextData.getEncryptedDataDetails(), resizeParams.getClusterName(),
        resizeParams.getContainerServiceName());
    return service.map(Service::getDesiredCount);
  }

  public int getNewInstancesDesiredCount(ContextData contextData) {
    EcsResizeParams resizeParams = contextData.getResizeParams();
    Preconditions.checkNotNull(resizeParams.getInstanceCount());
    int instanceCount = resizeParams.getInstanceCount();
    int totalTargetInstances =
        resizeParams.isUseFixedInstances() ? resizeParams.getFixedInstances() : resizeParams.getMaxInstances();
    return resizeParams.getInstanceUnitType() == PERCENTAGE
        ? (int) Math.round(Math.min(instanceCount, 100) * totalTargetInstances / 100.0)
        : Math.min(instanceCount, totalTargetInstances);
  }

  /**
   * Logic to find downsize count of older service(s)
   * <p>
   * If downsizeInstanceCount is not provided, downsize by removing all old instances for last step
   * or downsize equally (number of upsize instances == number of downsize instances)
   * <pre>
   * eg: upsizeDesiredCount = 5, upsizePreviousCount = 4, totalOtherInstances = 100 then downsize = 5-4 = 1, reducing
   * older instances count to 99. if last step: downsize = 100, reducing older instances count to 0.
   * </pre>
   * <p>
   * If downsizeInstanceCount is provided in upgrade step, downsize by <code>downsizeInstanceCount</code> and for
   * percentage, by <code>(downsizeInstanceCount% * total new instances)/100</code>
   * <p>
   * <pre>
   * eg: downsizeInstanceCount = 5, totalOtherInstances = 100, reduce older instances to 95 downsizeInstanceCount = 5%,
   * total new instances = 50, reduce by 5% of 50 = 3
   * </pre>
   *
   * @param contextData         context data
   * @param totalOtherInstances old service total instances
   * @param upsizeDesiredCount  new service desired count
   * @param upsizePreviousCount new service existing count
   * @return
   */
  public int getDownsizeByAmount(
      ContextData contextData, int totalOtherInstances, int upsizeDesiredCount, int upsizePreviousCount) {
    if (contextData.isBlueGreen()) {
      return 0;
    }

    EcsResizeParams resizeParams = contextData.getResizeParams();
    Integer downsizeDesiredCount = resizeParams.getDownsizeInstanceCount();
    if (downsizeDesiredCount != null) {
      int downsizeInstanceCount = resizeParams.getDownsizeInstanceCount();
      int totalTargetInstances =
          resizeParams.isUseFixedInstances() ? resizeParams.getFixedInstances() : resizeParams.getMaxInstances();
      int downsizeToCount = resizeParams.getDownsizeInstanceUnitType() == PERCENTAGE
          ? (int) Math.round(Math.min(downsizeInstanceCount, 100) * totalTargetInstances / 100.0)
          : Math.min(downsizeInstanceCount, totalTargetInstances);
      return Math.max(totalOtherInstances - downsizeToCount, 0);
    } else {
      return contextData.isDeployingToHundredPercent() ? totalOtherInstances
                                                       : Math.max(upsizeDesiredCount - upsizePreviousCount, 0);
    }
  }

  public List<ContainerServiceData> getOldInstanceData(ContextData contextData, ContainerServiceData newServiceData) {
    List<ContainerServiceData> oldInstanceData = new ArrayList<>();
    Map<String, Integer> previousCounts = getActiveServiceCounts(contextData);
    previousCounts.remove(newServiceData.getName());
    Map<String, String> previousImages = getActiveServiceImages(contextData);
    previousImages.remove(newServiceData.getName());

    int downsizeCount =
        getDownsizeByAmount(contextData, previousCounts.values().stream().mapToInt(Integer::intValue).sum(),
            newServiceData.getDesiredCount(), newServiceData.getPreviousCount());

    for (Entry<String, Integer> entry : previousCounts.entrySet()) {
      String serviceName = entry.getKey();
      String previousImage = previousImages.get(serviceName);
      int previousCount = entry.getValue();
      int desiredCount = Math.max(previousCount - downsizeCount, 0);

      oldInstanceData.add(ContainerServiceData.builder()
                              .name(serviceName)
                              .image(previousImage)
                              .previousCount(previousCount)
                              .desiredCount(desiredCount)
                              .build());
      downsizeCount -= previousCount - desiredCount;
    }
    return oldInstanceData;
  }

  public Map<String, Integer> getActiveServiceCounts(ContextData contextData) {
    EcsResizeParams resizeParams = contextData.getResizeParams();
    return awsClusterService.getActiveServiceCounts(resizeParams.getRegion(), contextData.getSettingAttribute(),
        contextData.getEncryptedDataDetails(), resizeParams.getClusterName(), resizeParams.getContainerServiceName());
  }

  public Map<String, String> getActiveServiceImages(ContextData contextData) {
    EcsResizeParams resizeParams = contextData.getResizeParams();
    String imagePrefix = substringBefore(resizeParams.getImage(), ":");
    return awsClusterService.getActiveServiceImages(resizeParams.getRegion(), contextData.getSettingAttribute(),
        contextData.getEncryptedDataDetails(), resizeParams.getClusterName(), resizeParams.getContainerServiceName(),
        imagePrefix);
  }

  public Map<String, Integer> listOfStringArrayToMap(List<String[]> listOfStringArray) {
    return Optional.ofNullable(listOfStringArray)
        .orElse(new ArrayList<>())
        .stream()
        .collect(Collectors.toMap(item -> item[0], item -> Integer.valueOf(item[1])));
  }

  public List<Task> getTasksFromTaskArn(AwsConfig awsConfig, String clusterName, String region,
      List<String> taskDefinitionArns, List<EncryptedDataDetail> encryptedDataDetails,
      ExecutionLogCallback executionLogCallback) {
    if (EmptyPredicate.isNotEmpty(taskDefinitionArns)) {
      return awsHelperService
          .describeTasks(region, awsConfig, encryptedDataDetails,
              new DescribeTasksRequest().withCluster(clusterName).withTasks(taskDefinitionArns), false)
          .getTasks();
    }
    return Collections.emptyList();
  }

  public List<String> deregisterTaskDefinitions(AwsConfig awsConfig, String region,
      List<String> taskDefinitionArnsToDeregister, List<EncryptedDataDetail> encryptedDataDetails,
      ExecutionLogCallback executionLogCallback) {
    taskDefinitionArnsToDeregister.forEach(t -> {
      executionLogCallback.saveExecutionLog(format("De-registering task definition arn => %s", t));
      DeregisterTaskDefinitionResult deregisterTaskDefinitionResult = awsHelperService.deregisterTaskDefinitions(
          region, awsConfig, encryptedDataDetails, new DeregisterTaskDefinitionRequest().withTaskDefinition(t));
      executionLogCallback.saveExecutionLog(format(
          "Task de-registered => %s", deregisterTaskDefinitionResult.getTaskDefinition().getTaskDefinitionArn()));
    });
    return taskDefinitionArnsToDeregister;
  }

  public List<Task> getExistingTasks(AwsConfig awsConfig, String clusterName, String region, String runTaskFamilyName,
      List<EncryptedDataDetail> encryptedDataDetails, ExecutionLogCallback executionLogCallback) {
    List<String> runningTaskArns = awsHelperService
                                       .listTasks(region, awsConfig, encryptedDataDetails,
                                           new ListTasksRequest()
                                               .withCluster(clusterName)
                                               .withFamily(runTaskFamilyName)
                                               .withDesiredStatus(DesiredStatus.RUNNING),
                                           false)
                                       .getTaskArns();
    List<String> stoppedTaskArns = awsHelperService
                                       .listTasks(region, awsConfig, encryptedDataDetails,
                                           new ListTasksRequest()
                                               .withCluster(clusterName)
                                               .withFamily(runTaskFamilyName)
                                               .withDesiredStatus(DesiredStatus.STOPPED),
                                           false)
                                       .getTaskArns();
    List<String> taskArns = new ArrayList<>();
    taskArns.addAll(runningTaskArns);
    taskArns.addAll(stoppedTaskArns);

    if (EmptyPredicate.isNotEmpty(taskArns)) {
      executionLogCallback.saveExecutionLog(
          format("%d tasks were found in task family %s, %d are stopped and %d are running", taskArns.size(),
              runTaskFamilyName, stoppedTaskArns.size(), runningTaskArns.size()));
      return awsHelperService
          .describeTasks(region, awsConfig, encryptedDataDetails,
              new DescribeTasksRequest().withCluster(clusterName).withTasks(taskArns), false)
          .getTasks();
    }
    executionLogCallback.saveExecutionLog(format("No tasks were found in task family %s", runTaskFamilyName));
    return Collections.emptyList();
  }

  public void setDesiredToOriginal(
      List<ContainerServiceData> newInstanceDataList, Map<String, Integer> originalServiceCounts) {
    for (ContainerServiceData containerServiceData : newInstanceDataList) {
      containerServiceData.setDesiredCount(
          Optional.ofNullable(originalServiceCounts.get(containerServiceData.getName())).orElse(0));
    }
  }

  public void logContainerInfos(List<ContainerInfo> containerInfos, ExecutionLogCallback executionLogCallback) {
    try {
      if (isNotEmpty(containerInfos)) {
        containerInfos.sort(Comparator.comparing(ContainerInfo::isNewContainer).reversed());
        executionLogCallback.saveExecutionLog("\nContainer IDs:");
        containerInfos.forEach(info
            -> executionLogCallback.saveExecutionLog("  " + info.getHostName()
                + (isNotEmpty(info.getHostName()) && info.getHostName().equals(info.getIp()) ? ""
                                                                                             : " - " + info.getIp())
                + (isNotEmpty(info.getHostName()) && info.getHostName().equals(info.getContainerId())
                        ? ""
                        : " - " + info.getContainerId())
                + (info.isNewContainer() ? " (new)" : "")));
        executionLogCallback.saveExecutionLog("");
      }
    } catch (Exception e) {
      Misc.logAllMessages(e, executionLogCallback);
    }
  }

  public EcsRunTaskDeployResponse getEmptyRunTaskDeployResponse() {
    EcsRunTaskDeployResponse ecsRunTaskDeployResponse = EcsRunTaskDeployResponse.builder().build();
    ecsRunTaskDeployResponse.setCommandExecutionStatus(CommandExecutionStatus.SUCCESS);
    ecsRunTaskDeployResponse.setOutput(StringUtils.EMPTY);
    return ecsRunTaskDeployResponse;
  }

  public EcsServiceDeployResponse getEmptyEcsServiceDeployResponse() {
    EcsServiceDeployResponse ecsServiceDeployResponse = EcsServiceDeployResponse.builder().build();
    ecsServiceDeployResponse.setCommandExecutionStatus(CommandExecutionStatus.SUCCESS);
    ecsServiceDeployResponse.setOutput(StringUtils.EMPTY);
    return ecsServiceDeployResponse;
  }

  public EcsDeployRollbackDataFetchResponse getEmptyEcsDeployRollbackDataFetchResponse() {
    EcsDeployRollbackDataFetchResponse response = EcsDeployRollbackDataFetchResponse.builder().build();
    response.setCommandExecutionStatus(CommandExecutionStatus.SUCCESS);
    response.setOutput(StringUtils.EMPTY);
    return response;
  }
}
