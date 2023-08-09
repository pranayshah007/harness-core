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
import io.harness.delegate.beans.ecs.EcsBlueGreenSwapTargetGroupsResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsInfraConfigHelper;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsBlueGreenSwapTargetGroupsRequest;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenSwapTargetGroupsResponse;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsBlueGreenSwapTargetGroupsCommandTaskHandler extends EcsCommandTaskNGHandler {
  @Inject private EcsTaskHelperBase ecsTaskHelperBase;
  @Inject private EcsInfraConfigHelper ecsInfraConfigHelper;
  @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  private EcsInfraConfig ecsInfraConfig;
  private long timeoutInMillis;

  @Override
  protected EcsCommandResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(ecsCommandRequest instanceof EcsBlueGreenSwapTargetGroupsRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("ecsCommandRequest", "Must be instance of EcsBlueGreenSwapTargetGroupsRequest"));
    }
    EcsBlueGreenSwapTargetGroupsRequest ecsBlueGreenSwapTargetGroupsRequest =
        (EcsBlueGreenSwapTargetGroupsRequest) ecsCommandRequest;

    timeoutInMillis = ecsBlueGreenSwapTargetGroupsRequest.getTimeoutIntervalInMin() * 60000;
    ecsInfraConfig = ecsBlueGreenSwapTargetGroupsRequest.getEcsInfraConfig();

    LogCallback swapTargetGroupLogCallback = ecsTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, EcsCommandUnitConstants.swapTargetGroup.toString(), true, commandUnitsProgress);

    try {
      swapTargetGroupLogCallback.saveExecutionLog(format("Swapping Target Groups..%n%n"), LogLevel.INFO);
      AwsInternalConfig awsInternalConfig =
          awsNgConfigMapper.createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO());

      // modify target group of prod listener with stage target group and target group of stage listener with prod
      // target group
      ecsCommandTaskHelper.swapTargetGroups(ecsInfraConfig, swapTargetGroupLogCallback,
          ecsBlueGreenSwapTargetGroupsRequest.getEcsLoadBalancerConfig(), awsInternalConfig);

      swapTargetGroupLogCallback.saveExecutionLog(
          format("Updating tag of new service: %s", ecsBlueGreenSwapTargetGroupsRequest.getNewServiceName()));
      // update service tag of new service with blue version
      ecsCommandTaskHelper.updateTag(ecsBlueGreenSwapTargetGroupsRequest.getNewServiceName(), ecsInfraConfig,
          EcsCommandTaskNGHelper.BG_BLUE, awsInternalConfig, swapTargetGroupLogCallback);

      swapTargetGroupLogCallback.saveExecutionLog(
          color(format("Successfully updated tag %n%n"), LogColor.White, LogWeight.Bold), LogLevel.INFO);

      // if its not a first deployment, update old service with zero desired count and change its tag
      if (!ecsBlueGreenSwapTargetGroupsRequest.isFirstDeployment()) {
        swapTargetGroupLogCallback.saveExecutionLog(
            format("Updating tag of old service: %s", ecsBlueGreenSwapTargetGroupsRequest.getOldServiceName()));
        // update service tag of old service with green version
        ecsCommandTaskHelper.updateTag(ecsBlueGreenSwapTargetGroupsRequest.getOldServiceName(), ecsInfraConfig,
            EcsCommandTaskNGHelper.BG_GREEN, awsInternalConfig, swapTargetGroupLogCallback);

        swapTargetGroupLogCallback.saveExecutionLog(
            color(format("Successfully updated tag %n%n"), LogColor.White, LogWeight.Bold), LogLevel.INFO);

        // check downsize old flag and downsize it
        if (!ecsBlueGreenSwapTargetGroupsRequest.isDoNotDownsizeOldService()) {
          if (ecsBlueGreenSwapTargetGroupsRequest.getDownsizeOldServiceDelayInSecs() != null
              && ecsBlueGreenSwapTargetGroupsRequest.getDownsizeOldServiceDelayInSecs() > 0L) {
            swapTargetGroupLogCallback.saveExecutionLog(
                color(format("Waiting for %d seconds before downsizing service %s",
                          ecsBlueGreenSwapTargetGroupsRequest.getDownsizeOldServiceDelayInSecs(),
                          ecsBlueGreenSwapTargetGroupsRequest.getOldServiceName()),
                    LogColor.White, LogWeight.Bold));
            ecsCommandTaskHelper.sleepInSeconds(ecsBlueGreenSwapTargetGroupsRequest.getDownsizeOldServiceDelayInSecs());
          }
          swapTargetGroupLogCallback.saveExecutionLog(format(
              "Removing green service:  %s scaling policies", ecsBlueGreenSwapTargetGroupsRequest.getOldServiceName()));
          // deleting scaling policies for old service
          ecsCommandTaskHelper.deleteScalingPolicies(ecsInfraConfig.getAwsConnectorDTO(),
              ecsBlueGreenSwapTargetGroupsRequest.getOldServiceName(), ecsInfraConfig.getCluster(),
              ecsInfraConfig.getRegion(), swapTargetGroupLogCallback);

          swapTargetGroupLogCallback.saveExecutionLog(format(
              "Removing green service:  %s scalable targets", ecsBlueGreenSwapTargetGroupsRequest.getOldServiceName()));
          // de-registering scalable target for old service
          ecsCommandTaskHelper.deregisterScalableTargets(ecsInfraConfig.getAwsConnectorDTO(),
              ecsBlueGreenSwapTargetGroupsRequest.getOldServiceName(), ecsInfraConfig.getCluster(),
              ecsInfraConfig.getRegion(), swapTargetGroupLogCallback);

          swapTargetGroupLogCallback.saveExecutionLog(format("Downsizing green service:  %s with zero desired count",
              ecsBlueGreenSwapTargetGroupsRequest.getOldServiceName()));
          // downsize old service desired count to zero
          UpdateServiceResponse updateServiceResponse = ecsCommandTaskHelper.updateDesiredCount(
              ecsBlueGreenSwapTargetGroupsRequest.getOldServiceName(), ecsInfraConfig, awsInternalConfig, 0);

          if (updateServiceResponse.service() != null) {
            swapTargetGroupLogCallback.saveExecutionLog(format("Current desired count for green service:  %s is %s",
                ecsBlueGreenSwapTargetGroupsRequest.getOldServiceName(),
                updateServiceResponse.service().desiredCount()));
          }
          swapTargetGroupLogCallback.saveExecutionLog(
              "Waiting 30s for downsize to complete green service to synchronize");
        }
      }

      EcsBlueGreenSwapTargetGroupsResult ecsBlueGreenSwapTargetGroupsResult =
          EcsBlueGreenSwapTargetGroupsResult.builder()
              .region(ecsInfraConfig.getRegion())
              .ecsTasks(ecsCommandTaskHelper.getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(),
                  ecsInfraConfig.getCluster(), ecsBlueGreenSwapTargetGroupsRequest.getNewServiceName(),
                  ecsInfraConfig.getRegion()))
              .loadBalancer(ecsBlueGreenSwapTargetGroupsRequest.getEcsLoadBalancerConfig().getLoadBalancer())
              .prodTargetGroupArn(
                  ecsBlueGreenSwapTargetGroupsRequest.getEcsLoadBalancerConfig().getProdTargetGroupArn())
              .prodListenerArn(ecsBlueGreenSwapTargetGroupsRequest.getEcsLoadBalancerConfig().getProdListenerArn())
              .prodListenerRuleArn(
                  ecsBlueGreenSwapTargetGroupsRequest.getEcsLoadBalancerConfig().getProdListenerRuleArn())
              .stageListenerArn(ecsBlueGreenSwapTargetGroupsRequest.getEcsLoadBalancerConfig().getStageListenerArn())
              .stageListenerRuleArn(
                  ecsBlueGreenSwapTargetGroupsRequest.getEcsLoadBalancerConfig().getStageListenerRuleArn())
              .stageTargetGroupArn(
                  ecsBlueGreenSwapTargetGroupsRequest.getEcsLoadBalancerConfig().getStageTargetGroupArn())
              .trafficShifted(true)
              .infrastructureKey(ecsInfraConfig.getInfraStructureKey())
              .build();
      EcsBlueGreenSwapTargetGroupsResponse ecsBlueGreenSwapTargetGroupsResponse =
          EcsBlueGreenSwapTargetGroupsResponse.builder()
              .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
              .ecsBlueGreenSwapTargetGroupsResult(ecsBlueGreenSwapTargetGroupsResult)
              .build();
      swapTargetGroupLogCallback.saveExecutionLog(
          color(format("Swapping Successful. %n"), LogColor.Green, LogWeight.Bold), LogLevel.INFO,
          CommandExecutionStatus.SUCCESS);
      log.info("Completed task execution for command: {}", ecsCommandRequest.getEcsCommandType().name());
      return ecsBlueGreenSwapTargetGroupsResponse;
    } catch (Exception e) {
      swapTargetGroupLogCallback.saveExecutionLog(color(format("Swapping Failed. %n"), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new EcsNGException(e);
    }
  }
}
