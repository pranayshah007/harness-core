/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

import static io.harness.aws.asg.manifest.AsgManifestType.AsgConfiguration;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgScalingPolicy;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgScheduledUpdateGroupAction;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgSwapService;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgCommandUnitConstants;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.aws.asg.manifest.AsgConfigurationManifestHandler;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainFactory;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainState;
import io.harness.aws.asg.manifest.request.AsgConfigurationManifestRequest;
import io.harness.aws.asg.manifest.request.AsgScalingPolicyManifestRequest;
import io.harness.aws.asg.manifest.request.AsgScheduledActionManifestRequest;
import io.harness.aws.asg.manifest.request.AsgSwapServiceManifestRequest;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.AsgNGException;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.aws.asg.AsgBlueGreenSwapServiceRequest;
import io.harness.delegate.task.aws.asg.AsgBlueGreenSwapServiceResponse;
import io.harness.delegate.task.aws.asg.AsgBlueGreenSwapServiceResult;
import io.harness.delegate.task.aws.asg.AsgCommandRequest;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class AsgBlueGreenSwapServiceCommandTaskHandler extends AsgCommandTaskNGHandler {
  @Inject private AsgTaskHelper asgTaskHelper;
  private AsgInfraConfig asgInfraConfig;
  private long timeoutInMillis;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private ElbV2Client elbV2Client;

  @Override
  protected AsgCommandResponse executeTaskInternal(AsgCommandRequest asgCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress)
      throws AsgNGException {
    if (!(asgCommandRequest instanceof AsgBlueGreenSwapServiceRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("asgCommandRequest", "Must be instance of AsgBlueGreenSwapServiceRequest"));
    }

    AsgBlueGreenSwapServiceRequest asgBlueGreenSwapServiceRequest = (AsgBlueGreenSwapServiceRequest) asgCommandRequest;

    asgInfraConfig = asgBlueGreenSwapServiceRequest.getAsgInfraConfig();

    LogCallback swapServiceLogCallback = asgTaskHelper.getLogCallback(
        iLogStreamingTaskClient, AsgCommandUnitConstants.swapService.toString(), true, commandUnitsProgress);

    try {
      AsgSdkManager asgSdkManager =
          asgTaskHelper.getAsgSdkManager(asgCommandRequest, swapServiceLogCallback, elbV2Client);
      asgSdkManager.info("Swapping autoscaling group services");
      AwsInternalConfig awsInternalConfig =
          awsNgConfigMapper.createAwsInternalConfig(asgInfraConfig.getAwsConnectorDTO());

      AutoScalingGroup prodAutoScalingGroup = asgSdkManager.getASG(asgBlueGreenSwapServiceRequest.getProdAsgName());

      AsgManifestHandlerChainFactory asgManifestHandlerChainFactory =
          (AsgManifestHandlerChainFactory) AsgManifestHandlerChainFactory.builder()
              .initialChainState(AsgManifestHandlerChainState.builder()
                                     .asgName(asgBlueGreenSwapServiceRequest.getProdAsgName())
                                     .newAsgName(asgBlueGreenSwapServiceRequest.getStageAsgName())
                                     .build())
              .asgSdkManager(asgSdkManager)
              .build()
              .addHandler(AsgSwapService,
                  AsgSwapServiceManifestRequest.builder()
                      .asgLoadBalancerConfig(asgBlueGreenSwapServiceRequest.getAsgLoadBalancerConfig())
                      .region(asgInfraConfig.getRegion())
                      .awsInternalConfig(awsInternalConfig)
                      .build());

      if (asgBlueGreenSwapServiceRequest.isDownsizeOldAsg() && prodAutoScalingGroup != null) {
        // if its not a first deployment, update old asg with zero desired instance count (if downsize flag is enabled)
        // and change its tag
        String asgConfigurationContent = "{}";
        Map<String, Object> asgConfigurationOverrideProperties = new HashMap<>();
        asgConfigurationOverrideProperties.put(AsgConfigurationManifestHandler.OverrideProperties.minSize, 0);
        asgConfigurationOverrideProperties.put(AsgConfigurationManifestHandler.OverrideProperties.maxSize, 0);
        asgConfigurationOverrideProperties.put(AsgConfigurationManifestHandler.OverrideProperties.desiredCapacity, 0);

        asgManifestHandlerChainFactory
            .addHandler(AsgScalingPolicy, AsgScalingPolicyManifestRequest.builder().manifests(null).build())
            .addHandler(
                AsgScheduledUpdateGroupAction, AsgScheduledActionManifestRequest.builder().manifests(null).build())
            .addHandler(AsgConfiguration,
                AsgConfigurationManifestRequest.builder()
                    .manifests(Arrays.asList(asgConfigurationContent))
                    .overrideProperties(asgConfigurationOverrideProperties)
                    .build());
      }

      AsgManifestHandlerChainState chainState = asgManifestHandlerChainFactory.executeUpsert();

      AutoScalingGroup prodAutoScalingGroupAfterTrafficShift = asgSdkManager.getASG(chainState.getNewAsgName());
      AutoScalingGroupContainer prodAutoScalingGroupContainerAfterTrafficShift =
          asgTaskHelper.mapToAutoScalingGroupContainer(prodAutoScalingGroupAfterTrafficShift);

      AutoScalingGroup stageAutoScalingGroupAfterTrafficShift = asgSdkManager.getASG(chainState.getAsgName());
      AutoScalingGroupContainer stageAutoScalingGroupContainerAfterTrafficShift =
          asgTaskHelper.mapToAutoScalingGroupContainer(stageAutoScalingGroupAfterTrafficShift);

      AsgBlueGreenSwapServiceResult asgBlueGreenSwapServiceResult =
          AsgBlueGreenSwapServiceResult.builder()
              .prodAutoScalingGroupContainer(prodAutoScalingGroupContainerAfterTrafficShift)
              .stageAutoScalingGroupContainer(stageAutoScalingGroupContainerAfterTrafficShift)
              .trafficShifted(true)
              .build();

      AsgBlueGreenSwapServiceResponse asgBlueGreenSwapServiceResponse =
          AsgBlueGreenSwapServiceResponse.builder()
              .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
              .asgBlueGreenSwapServiceResult(asgBlueGreenSwapServiceResult)
              .build();

      swapServiceLogCallback.saveExecutionLog(
          color(format("Swapping Finished Successfully. %n"), LogColor.Green, LogWeight.Bold), LogLevel.INFO,
          CommandExecutionStatus.SUCCESS);

      return asgBlueGreenSwapServiceResponse;

    } catch (Exception e) {
      swapServiceLogCallback.saveExecutionLog(color(format("Swapping Failed. %n"), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new AsgNGException(e);
    }
  }
}
