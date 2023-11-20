/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

import static io.harness.aws.asg.manifest.AsgManifestType.AsgShiftTraffic;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.aws.asg.AsgCommandUnitConstants;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainFactory;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainState;
import io.harness.aws.asg.manifest.request.AsgShiftTrafficManifestRequest;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.AsgNGException;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.aws.asg.AsgCommandRequest;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgShiftTrafficRequest;
import io.harness.delegate.task.aws.asg.AsgShiftTrafficResponse;
import io.harness.delegate.task.aws.asg.AsgShiftTrafficResult;
import io.harness.delegate.task.aws.asg.AsgTaskHelper;
import io.harness.delegate.task.aws.asg.AutoScalingGroupContainer;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.google.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_AMI_ASG})
@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class AsgShiftTrafficCommandTaskHandler extends AsgCommandTaskNGHandler {
  @Inject private AsgTaskHelper asgTaskHelper;
  private AsgInfraConfig asgInfraConfig;
  private long timeoutInMillis;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private ElbV2Client elbV2Client;

  @Override
  protected AsgCommandResponse executeTaskInternal(AsgCommandRequest asgCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress)
      throws AsgNGException {
    if (!(asgCommandRequest instanceof AsgShiftTrafficRequest)) {
      throw new InvalidArgumentsException(Pair.of("asgCommandRequest", "Must be instance of AsgShiftTrafficRequest"));
    }

    AsgShiftTrafficRequest asgShiftTrafficRequest = (AsgShiftTrafficRequest) asgCommandRequest;

    asgInfraConfig = asgShiftTrafficRequest.getAsgInfraConfig();

    LogCallback shiftTrafficLogCallback = asgTaskHelper.getLogCallback(
        iLogStreamingTaskClient, AsgCommandUnitConstants.shiftTraffic.toString(), true, commandUnitsProgress);

    try {
      AsgSdkManager asgSdkManager =
          asgTaskHelper.getAsgSdkManager(asgCommandRequest, shiftTrafficLogCallback, elbV2Client);
      asgSdkManager.info("Shifting traffic for ASG");
      AwsInternalConfig awsInternalConfig =
          awsNgConfigMapper.createAwsInternalConfig(asgInfraConfig.getAwsConnectorDTO());

      AutoScalingGroup prodAutoScalingGroup = asgSdkManager.getASG(asgShiftTrafficRequest.getProdAsgName());

      AsgManifestHandlerChainFactory asgManifestHandlerChainFactory =
          (AsgManifestHandlerChainFactory) AsgManifestHandlerChainFactory.builder()
              .initialChainState(AsgManifestHandlerChainState.builder()
                                     .asgName(asgShiftTrafficRequest.getProdAsgName())
                                     .newAsgName(asgShiftTrafficRequest.getStageAsgName())
                                     .build())
              .asgSdkManager(asgSdkManager)
              .build()
              .addHandler(AsgShiftTraffic,
                  AsgShiftTrafficManifestRequest.builder()
                      .loadBalancers(asgShiftTrafficRequest.getLoadBalancers())
                      .region(asgInfraConfig.getRegion())
                      .awsInternalConfig(awsInternalConfig)
                      .weight(asgShiftTrafficRequest.getWeight())
                      .build());

      AsgManifestHandlerChainState chainState = asgManifestHandlerChainFactory.executeUpsert();

      // if it's not a first deployment, update old asg with zero desired instance count (if downsize flag is enabled)
      if (asgShiftTrafficRequest.isDownsizeOldAsg() && prodAutoScalingGroup != null) {
        asgSdkManager.clearAllScalingPoliciesForAsg(prodAutoScalingGroup.getAutoScalingGroupName());
        asgSdkManager.clearAllScheduledActionsForAsg(prodAutoScalingGroup.getAutoScalingGroupName());
        asgSdkManager.downSizeToZero(prodAutoScalingGroup.getAutoScalingGroupName());
      }

      AutoScalingGroup prodAutoScalingGroupAfterTrafficShift = asgSdkManager.getASG(chainState.getNewAsgName());
      AutoScalingGroupContainer prodAutoScalingGroupContainerAfterTrafficShift =
          asgTaskHelper.mapToAutoScalingGroupContainer(prodAutoScalingGroupAfterTrafficShift);

      AutoScalingGroup stageAutoScalingGroupAfterTrafficShift = asgSdkManager.getASG(chainState.getAsgName());
      AutoScalingGroupContainer stageAutoScalingGroupContainerAfterTrafficShift =
          asgTaskHelper.mapToAutoScalingGroupContainer(stageAutoScalingGroupAfterTrafficShift);

      AsgShiftTrafficResult asgShiftTrafficResult =
          AsgShiftTrafficResult.builder()
              .prodAutoScalingGroupContainer(prodAutoScalingGroupContainerAfterTrafficShift)
              .stageAutoScalingGroupContainer(stageAutoScalingGroupContainerAfterTrafficShift)
              .trafficShifted(true)
              .build();

      AsgShiftTrafficResponse asgShiftTrafficResponse = AsgShiftTrafficResponse.builder()
                                                            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                            .result(asgShiftTrafficResult)
                                                            .build();

      shiftTrafficLogCallback.saveExecutionLog(
          color(format("Traffic shifted successfully. %n"), LogColor.Green, LogWeight.Bold), LogLevel.INFO,
          CommandExecutionStatus.SUCCESS);

      return asgShiftTrafficResponse;
    } catch (Exception e) {
      shiftTrafficLogCallback.saveExecutionLog(
          color(format("Shifting traffic Failed. %n"), LogColor.Red, LogWeight.Bold), LogLevel.ERROR,
          CommandExecutionStatus.FAILURE);
      throw new AsgNGException(e);
    }
  }
}
