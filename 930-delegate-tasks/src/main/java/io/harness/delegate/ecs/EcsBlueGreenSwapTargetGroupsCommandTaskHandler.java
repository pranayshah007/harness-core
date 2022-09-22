package io.harness.delegate.ecs;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
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
      AwsInternalConfig awsInternalConfig =
          awsNgConfigMapper.createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO());

      // modify target group of prod listener with stage target group and target group of stage listener with prod
      // target group
      ecsCommandTaskHelper.swapTargetGroups(ecsInfraConfig, swapTargetGroupLogCallback,
          ecsBlueGreenSwapTargetGroupsRequest.getEcsLoadBalancerConfig(), awsInternalConfig);

      // update service tag of new service with blue version
      ecsCommandTaskHelper.updateTag(ecsBlueGreenSwapTargetGroupsRequest.getNewServiceName(), ecsInfraConfig,
          EcsCommandTaskNGHelper.BG_BLUE, awsInternalConfig);

      // if its not a first deployment, update old service with zero desired count and change its tag
      if (!ecsBlueGreenSwapTargetGroupsRequest.isFirstDeployment()) {
        // update service tag of old service with green version
        ecsCommandTaskHelper.updateTag(ecsBlueGreenSwapTargetGroupsRequest.getOldServiceName(), ecsInfraConfig,
            EcsCommandTaskNGHelper.BG_GREEN, awsInternalConfig);

        // downsize old service desired count to zero
        ecsCommandTaskHelper.updateDesiredCount(
            ecsBlueGreenSwapTargetGroupsRequest.getOldServiceName(), ecsInfraConfig, awsInternalConfig, 0);
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
          color(format("%n Swapping Successful."), LogColor.Green, LogWeight.Bold), LogLevel.INFO,
          CommandExecutionStatus.SUCCESS);
      return ecsBlueGreenSwapTargetGroupsResponse;
    } catch (Exception e) {
      swapTargetGroupLogCallback.saveExecutionLog(color(format("%n Swapping Failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new EcsNGException(e);
    }
  }
}
