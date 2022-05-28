/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.apache.commons.lang3.StringUtils.substringBefore;

import io.harness.container.ContainerInfo;

import software.wings.api.ContainerServiceData;
import software.wings.api.DeploymentType;
import software.wings.beans.AwsConfig;
import software.wings.beans.container.AwsAutoScalarConfig;
import software.wings.cloudprovider.aws.AwsClusterService;
import software.wings.delegatetasks.aws.ecs.ecstaskhandler.EcsCommandTaskHelper;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.intfc.aws.delegate.AwsAppAutoScalingHelperServiceDelegate;

import com.amazonaws.services.applicationautoscaling.model.DeregisterScalableTargetRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsResult;
import com.amazonaws.services.applicationautoscaling.model.ScalableTarget;
import com.amazonaws.services.applicationautoscaling.model.ServiceNamespace;
import com.amazonaws.services.ecs.model.Service;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.annotations.Transient;

@JsonTypeName("RESIZE")
public class ResizeCommandUnit extends ContainerResizeCommandUnit {
  @Inject @Transient private transient AwsClusterService awsClusterService;
  @Inject @Transient private transient AwsHelperService awsHelperService;
  @Inject @Transient private transient AwsAppAutoScalingHelperServiceDelegate awsAppAutoScalingService;
  @Inject @Transient private transient EcsCommandTaskHelper ecsCommandTaskHelper;

  public ResizeCommandUnit() {
    super(CommandUnitType.RESIZE);
    setDeploymentType(DeploymentType.ECS.name());
  }

  @Override
  protected List<ContainerInfo> executeResize(
      ContextData contextData, ContainerServiceData containerServiceData, ExecutionLogCallback executionLogCallback) {
    EcsResizeParams resizeParams = (EcsResizeParams) contextData.resizeParams;

    // As a part of Rollback, restore AutoScalingConfig if required
    if (resizeParams.isRollback()) {
      restoreAutoScalarConfigs(contextData, containerServiceData, executionLogCallback);
    } else {
      deregisterAutoScalarsIfExists(contextData, executionLogCallback);
    }

    List<ContainerInfo> containerInfos = awsClusterService.resizeCluster(resizeParams.getRegion(),
        contextData.settingAttribute, contextData.encryptedDataDetails, resizeParams.getClusterName(),
        containerServiceData.getName(), containerServiceData.getPreviousCount(), containerServiceData.getDesiredCount(),
        resizeParams.getServiceSteadyStateTimeout(), executionLogCallback, false);

    createAutoScalarConfigIfServiceReachedMaxSize(contextData, containerServiceData, executionLogCallback);

    return containerInfos;
  }

  private void deregisterAutoScalarsIfExists(ContextData contextData, ExecutionLogCallback executionLogCallback) {
    EcsResizeParams resizeParams = (EcsResizeParams) contextData.resizeParams;
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
            (AwsConfig) contextData.settingAttribute.getValue(), contextData.encryptedDataDetails,
            new DescribeScalableTargetsRequest()
                .withResourceIds(target.getResourceId())
                .withServiceNamespace(ServiceNamespace.Ecs)
                .withScalableDimension(target.getScalableDimension()));

        // If Target exists, delete it
        if (isNotEmpty(result.getScalableTargets())) {
          executionLogCallback.saveExecutionLog("De-registering Scalable Target :" + target.getResourceId());
          awsAppAutoScalingService.deregisterScalableTarget(resizeParams.getRegion(),
              (AwsConfig) contextData.settingAttribute.getValue(), contextData.encryptedDataDetails,
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

  private void createAutoScalarConfigIfServiceReachedMaxSize(
      ContextData contextData, ContainerServiceData containerServiceData, ExecutionLogCallback executionLogCallback) {
    EcsResizeParams resizeParams = (EcsResizeParams) contextData.resizeParams;

    int maxDesiredCount =
        resizeParams.isUseFixedInstances() ? resizeParams.getFixedInstances() : resizeParams.getMaxInstances();

    // If Rollback or this is not final resize stage, just return.
    // We create AutoScalar after new service has been completely upsized.
    if (resizeParams.isRollback() || maxDesiredCount > containerServiceData.getDesiredCount()) {
      return;
    }

    if (resizeParams.getAwsAutoScalarConfigForNewService() == null
        || isEmpty(resizeParams.getAwsAutoScalarConfigForNewService())) {
      executionLogCallback.saveExecutionLog("No Autoscalar config provided.");
      return;
    }

    String resourceId =
        ecsCommandTaskHelper.getResourceIdForEcsService(containerServiceData.getName(), resizeParams.getClusterName());

    AwsConfig awsConfig = (AwsConfig) contextData.settingAttribute.getValue();
    executionLogCallback.saveExecutionLog(
        "\nRegistering Scalable Target for Service : " + containerServiceData.getName());
    resizeParams.getAwsAutoScalarConfigForNewService().forEach(awsAutoScalarConfig -> {
      if (StringUtils.isNotBlank(awsAutoScalarConfig.getScalableTargetJson())) {
        ScalableTarget scalableTarget =
            awsAppAutoScalingService.getScalableTargetFromJson(awsAutoScalarConfig.getScalableTargetJson());
        scalableTarget.withResourceId(resourceId);
        ecsCommandTaskHelper.registerScalableTargetForEcsService(awsAppAutoScalingService, resizeParams.getRegion(),
            awsConfig, contextData.encryptedDataDetails, executionLogCallback, scalableTarget);

        if (isNotEmpty(awsAutoScalarConfig.getScalingPolicyJson())) {
          executionLogCallback.saveExecutionLog(
              "Creating Auto Scaling Policies for Service: " + containerServiceData.getName());
          for (String policyJson : awsAutoScalarConfig.getScalingPolicyJson()) {
            ecsCommandTaskHelper.upsertScalingPolicyIfRequired(policyJson, resourceId,
                scalableTarget.getScalableDimension(), resizeParams.getRegion(), awsConfig, awsAppAutoScalingService,
                contextData.encryptedDataDetails, executionLogCallback);
          }
        }
      }
    });
  }

  /**
   * Restore Autoscalar targets and policies for existing services as part of rollback,
   * those were removed in setup state
   * @param contextData
   * @param containerServiceData
   * @param executionLogCallback
   */
  private void restoreAutoScalarConfigs(
      ContextData contextData, ContainerServiceData containerServiceData, ExecutionLogCallback executionLogCallback) {
    EcsResizeParams resizeParams = (EcsResizeParams) contextData.resizeParams;

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

    AwsConfig awsConfig = (AwsConfig) contextData.settingAttribute.getValue();

    awsAutoScalarConfigs.forEach(awsAutoScalarConfig -> {
      if (StringUtils.isNotBlank(awsAutoScalarConfig.getScalableTargetJson())) {
        ScalableTarget scalableTarget =
            awsAppAutoScalingService.getScalableTargetFromJson(awsAutoScalarConfig.getScalableTargetJson());

        DescribeScalableTargetsResult result = awsAppAutoScalingService.listScalableTargets(resizeParams.getRegion(),
            awsConfig, contextData.encryptedDataDetails,
            new DescribeScalableTargetsRequest()
                .withResourceIds(Arrays.asList(scalableTarget.getResourceId()))
                .withScalableDimension(scalableTarget.getScalableDimension())
                .withServiceNamespace(ServiceNamespace.Ecs));

        if (isEmpty(result.getScalableTargets())) {
          ecsCommandTaskHelper.registerScalableTargetForEcsService(awsAppAutoScalingService, resizeParams.getRegion(),
              awsConfig, contextData.encryptedDataDetails, executionLogCallback, scalableTarget);

          if (isNotEmpty(awsAutoScalarConfig.getScalingPolicyJson())) {
            for (String policyJson : awsAutoScalarConfig.getScalingPolicyJson()) {
              ecsCommandTaskHelper.upsertScalingPolicyIfRequired(policyJson, scalableTarget.getResourceId(),
                  scalableTarget.getScalableDimension(), resizeParams.getRegion(), awsConfig, awsAppAutoScalingService,
                  contextData.encryptedDataDetails, executionLogCallback);
            }
          }
        }
      }
    });
  }

  /**
   * Delete autoScalar targets and policies created for New service that is being downsized in rollback
   * @param contextData
   * @param executionLogCallback
   */
  private void deleteAutoScalarForNewService(ContextData contextData, ExecutionLogCallback executionLogCallback) {
    EcsResizeParams resizeParams = (EcsResizeParams) contextData.resizeParams;
    AwsConfig awsConfig = (AwsConfig) contextData.settingAttribute.getValue();
    String serviceName = resizeParams.getContainerServiceName();
    String resourceId = ecsCommandTaskHelper.getResourceIdForEcsService(serviceName, resizeParams.getClusterName());

    DescribeScalableTargetsResult targetsResult = awsAppAutoScalingService.listScalableTargets(resizeParams.getRegion(),
        awsConfig, contextData.encryptedDataDetails,
        new DescribeScalableTargetsRequest()
            .withServiceNamespace(ServiceNamespace.Ecs)
            .withResourceIds(Arrays.asList(resourceId)));

    if (isEmpty(targetsResult.getScalableTargets())) {
      return;
    }

    targetsResult.getScalableTargets().forEach(scalableTarget -> {
      executionLogCallback.saveExecutionLog("De-registering AutoScalable Target : " + scalableTarget.getResourceId());
      awsAppAutoScalingService.deregisterScalableTarget(resizeParams.getRegion(), awsConfig,
          contextData.encryptedDataDetails,
          new DeregisterScalableTargetRequest()
              .withServiceNamespace(ServiceNamespace.Ecs)
              .withResourceId(resourceId)
              .withScalableDimension(scalableTarget.getScalableDimension()));
      executionLogCallback.saveExecutionLog(
          "Successfully De-registered AutoScalable Target : " + scalableTarget.getResourceId());
    });
  }

  @Override
  protected Map<String, Integer> getActiveServiceCounts(ContextData contextData) {
    EcsResizeParams resizeParams = (EcsResizeParams) contextData.resizeParams;
    return awsClusterService.getActiveServiceCounts(resizeParams.getRegion(), contextData.settingAttribute,
        contextData.encryptedDataDetails, resizeParams.getClusterName(), resizeParams.getContainerServiceName());
  }

  @Override
  protected Map<String, String> getActiveServiceImages(ContextData contextData) {
    EcsResizeParams resizeParams = (EcsResizeParams) contextData.resizeParams;
    String imagePrefix = substringBefore(contextData.resizeParams.getImage(), ":");
    return awsClusterService.getActiveServiceImages(resizeParams.getRegion(), contextData.settingAttribute,
        contextData.encryptedDataDetails, resizeParams.getClusterName(), resizeParams.getContainerServiceName(),
        imagePrefix);
  }

  @Override
  protected Optional<Integer> getServiceDesiredCount(ContextData contextData) {
    EcsResizeParams resizeParams = (EcsResizeParams) contextData.resizeParams;
    Optional<Service> service = awsClusterService
                                    .getServices(resizeParams.getRegion(), contextData.settingAttribute,
                                        contextData.encryptedDataDetails, resizeParams.getClusterName())
                                    .stream()
                                    .filter(svc -> svc.getServiceName().equals(resizeParams.getContainerServiceName()))
                                    .findFirst();
    return service.map(Service::getDesiredCount);
  }

  @Override
  protected Map<String, Integer> getTrafficWeights(ContextData contextData) {
    return new HashMap<>();
  }

  @Override
  protected int getPreviousTrafficPercent(ContextData contextData) {
    return 0;
  }

  @Override
  protected Integer getDesiredTrafficPercent(ContextData contextData) {
    return 0;
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName("RESIZE")
  public static class Yaml extends ContainerResizeCommandUnit.Yaml {
    public Yaml() {
      super(CommandUnitType.RESIZE.name());
    }

    @Builder
    public Yaml(String name, String deploymentType) {
      super(name, CommandUnitType.RESIZE.name(), deploymentType);
    }
  }
}
