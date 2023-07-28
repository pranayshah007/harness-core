/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.ecs.EcsBlueGreenPrepareRollbackDataResult;
import io.harness.delegate.beans.ecs.EcsMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsLoadBalancerConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsBlueGreenPrepareRollbackRequest;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenPrepareRollbackDataResponse;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.Service;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsBlueGreenPrepareRollbackCommandTaskHandler extends EcsCommandTaskNGHandler {
  @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Inject private EcsTaskHelperBase ecsTaskHelperBase;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  private EcsInfraConfig ecsInfraConfig;
  private long timeoutInMillis;

  @Override
  protected EcsCommandResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(ecsCommandRequest instanceof EcsBlueGreenPrepareRollbackRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("ecsCommandRequest", "Must be instance of EcsBlueGreenPrepareRollbackRequest"));
    }

    EcsBlueGreenPrepareRollbackRequest ecsBlueGreenPrepareRollbackRequest =
        (EcsBlueGreenPrepareRollbackRequest) ecsCommandRequest;

    timeoutInMillis = ecsBlueGreenPrepareRollbackRequest.getTimeoutIntervalInMin() * 60000;
    ecsInfraConfig = ecsBlueGreenPrepareRollbackRequest.getEcsInfraConfig();

    LogCallback prepareRollbackDataLogCallback = ecsTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, EcsCommandUnitConstants.prepareRollbackData.toString(), true, commandUnitsProgress);

    try {
      prepareRollbackDataLogCallback.saveExecutionLog(format("Preparing Rollback Data..%n%n"), LogLevel.INFO);
      AwsInternalConfig awsInternalConfig =
          awsNgConfigMapper.createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO());

      // Get Ecs Service Name
      String ecsServiceDefinitionManifestContent =
          ecsBlueGreenPrepareRollbackRequest.getEcsServiceDefinitionManifestContent();
      CreateServiceRequest createServiceRequest =
          ecsCommandTaskHelper
              .parseYamlAsObject(ecsServiceDefinitionManifestContent, CreateServiceRequest.serializableBuilderClass())
              .build();

      // update EcsLoadBalancerConfig with default listener rules if listener rules provided are empty
      ecsCommandTaskHelper.updateECSLoadbalancerConfigWithDefaultListenerRulesIfEmpty(
          ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig(), awsInternalConfig, ecsInfraConfig,
          prepareRollbackDataLogCallback);

      prepareRollbackDataLogCallback.saveExecutionLog(
          format("Fetching Target group for ELB Prod Listener: %s ",
              ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getProdListenerArn()),
          LogLevel.INFO);
      // Get prod targetGroup Arn
      String prodTargetGroupArn = ecsCommandTaskHelper.getTargetGroupArnFromLoadBalancer(ecsInfraConfig,
          ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getProdListenerArn(),
          ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getProdListenerRuleArn(),
          ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getLoadBalancer(), awsInternalConfig);

      prepareRollbackDataLogCallback.saveExecutionLog(
          format("Fetched Target group for ELB Prod Listener: %s %n"
                  + "with targetGroupArn: %s",
              ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getProdListenerArn(), prodTargetGroupArn),
          LogLevel.INFO);

      prepareRollbackDataLogCallback.saveExecutionLog(
          format("Fetching Target group for ELB Stage Listener: %s ",
              ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getStageListenerArn()),
          LogLevel.INFO);
      // Get stage targetGroup Arn
      String stageTargetGroupArn = ecsCommandTaskHelper.getTargetGroupArnFromLoadBalancer(ecsInfraConfig,
          ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getStageListenerArn(),
          ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getStageListenerRuleArn(),
          ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getLoadBalancer(), awsInternalConfig);
      prepareRollbackDataLogCallback.saveExecutionLog(
          format("Fetched Target group for ELB Stage Listener: %s %n"
                  + "with targetGroupArn: %s",
              ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getStageListenerArn(), stageTargetGroupArn),
          LogLevel.INFO);

      EcsLoadBalancerConfig ecsLoadBalancerConfig =
          EcsLoadBalancerConfig.builder()
              .loadBalancer(ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getLoadBalancer())
              .prodListenerArn(ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getProdListenerArn())
              .prodListenerRuleArn(
                  ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getProdListenerRuleArn())
              .prodTargetGroupArn(prodTargetGroupArn)
              .stageListenerArn(ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getStageListenerArn())
              .stageListenerRuleArn(
                  ecsBlueGreenPrepareRollbackRequest.getEcsLoadBalancerConfig().getStageListenerRuleArn())
              .stageTargetGroupArn(stageTargetGroupArn)
              .build();

      Optional<String> optionalServiceName = ecsCommandTaskHelper.getBlueVersionServiceName(
          createServiceRequest.serviceName() + EcsCommandTaskNGHelper.DELIMITER, ecsInfraConfig);
      if (!optionalServiceName.isPresent() || EmptyPredicate.isEmpty(optionalServiceName.get())) {
        // If no blue version service found
        return getFirstTimeDeploymentResponse(prepareRollbackDataLogCallback, prodTargetGroupArn,
            ecsBlueGreenPrepareRollbackRequest, ecsLoadBalancerConfig);
      }
      String serviceName = optionalServiceName.get();

      prepareRollbackDataLogCallback.saveExecutionLog(
          format("Fetching Service Definition Details for Service %s..", serviceName), LogLevel.INFO);

      // Describe ecs service and get service details
      Optional<Service> optionalService = ecsCommandTaskHelper.describeService(
          ecsInfraConfig.getCluster(), serviceName, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

      if (optionalService.isPresent()
          && ecsCommandTaskHelper.isServiceActive(optionalService.get())) { // If service exists
        Service service = optionalService.get();

        Integer maxDesiredCount = service.desiredCount();
        // compare max desired count with current desired count
        if (createServiceRequest.desiredCount() != null) {
          maxDesiredCount = Math.max(maxDesiredCount, createServiceRequest.desiredCount());
        }
        Service updatedService = service.toBuilder().desiredCount(maxDesiredCount).build();

        // Get createServiceRequestBuilderString from service
        String createServiceRequestBuilderString =
            ecsCommandTaskHelper.toYaml(EcsMapper.createCreateServiceRequestBuilderFromService(updatedService));
        prepareRollbackDataLogCallback.saveExecutionLog(
            format("Fetched Service Definition Details for Service %s", serviceName), LogLevel.INFO);

        // Get registerScalableTargetRequestBuilderStrings if present
        List<String> registerScalableTargetRequestBuilderStrings = ecsCommandTaskHelper.getScalableTargetsAsString(
            prepareRollbackDataLogCallback, serviceName, service, ecsInfraConfig);

        // Get putScalingPolicyRequestBuilderStrings if present
        List<String> registerScalingPolicyRequestBuilderStrings = ecsCommandTaskHelper.getScalingPoliciesAsString(
            prepareRollbackDataLogCallback, serviceName, service, ecsInfraConfig);

        EcsBlueGreenPrepareRollbackDataResult ecsBlueGreenPrepareRollbackDataResult =
            EcsBlueGreenPrepareRollbackDataResult.builder()
                .createServiceRequestBuilderString(createServiceRequestBuilderString)
                .registerScalableTargetRequestBuilderStrings(registerScalableTargetRequestBuilderStrings)
                .registerScalingPolicyRequestBuilderStrings(registerScalingPolicyRequestBuilderStrings)
                .ecsLoadBalancerConfig(ecsLoadBalancerConfig)
                .isFirstDeployment(false)
                .serviceName(serviceName)
                .build();
        EcsBlueGreenPrepareRollbackDataResponse ecsBlueGreenPrepareRollbackDataResponse =
            EcsBlueGreenPrepareRollbackDataResponse.builder()
                .ecsBlueGreenPrepareRollbackDataResult(ecsBlueGreenPrepareRollbackDataResult)
                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                .build();
        prepareRollbackDataLogCallback.saveExecutionLog(
            "Preparing Rollback Data complete", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
        log.info("Completed task execution for command: {}", ecsCommandRequest.getEcsCommandType().name());
        return ecsBlueGreenPrepareRollbackDataResponse;
      } else { // If service doesn't exist
        return getFirstTimeDeploymentResponse(prepareRollbackDataLogCallback, prodTargetGroupArn,
            ecsBlueGreenPrepareRollbackRequest, ecsLoadBalancerConfig);
      }
    } catch (Exception e) {
      prepareRollbackDataLogCallback.saveExecutionLog(
          color(format("%n Deployment Failed."), LogColor.Red, LogWeight.Bold), LogLevel.ERROR,
          CommandExecutionStatus.FAILURE);
      throw new EcsNGException(e);
    }
  }

  private EcsBlueGreenPrepareRollbackDataResponse getFirstTimeDeploymentResponse(LogCallback logCallback,
      String targetGroupArn, EcsBlueGreenPrepareRollbackRequest ecsBlueGreenPrepareRollbackRequest,
      EcsLoadBalancerConfig ecsLoadBalancerConfig) {
    logCallback.saveExecutionLog("Blue version of Service doesn't exist. Skipping Prepare Rollback Data..",
        LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    // Send EcsBlueGreenPrepareRollbackDataResult with isFirstDeployment as true
    EcsBlueGreenPrepareRollbackDataResult ecsBlueGreenPrepareRollbackDataResult =
        EcsBlueGreenPrepareRollbackDataResult.builder()
            .isFirstDeployment(true)
            .ecsLoadBalancerConfig(ecsLoadBalancerConfig)
            .build();

    return EcsBlueGreenPrepareRollbackDataResponse.builder()
        .ecsBlueGreenPrepareRollbackDataResult(ecsBlueGreenPrepareRollbackDataResult)
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }
}
