/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

import static io.harness.aws.asg.manifest.AsgManifestType.AsgConfiguration;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgScalingPolicy;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgSwapService;

import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

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
import java.util.List;
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
      asgSdkManager.info(format("Swapping autoscaling group services", Bold));
      AwsInternalConfig awsInternalConfig =
          awsNgConfigMapper.createAwsInternalConfig(asgInfraConfig.getAwsConnectorDTO());

      AutoScalingGroup autoScalingGroup = asgSdkManager.getASG(asgBlueGreenSwapServiceRequest.getOldAsgName());

      AsgManifestHandlerChainState chainState;
      // check downsize old flag and downsize it
      if (asgBlueGreenSwapServiceRequest.isDownsizeOldAsg() && autoScalingGroup != null) {
        // if its not a first deployment, update old asg with zero desired instance count (if downsize flag is enabled)
        // and change its tag
        // Chain factory code to handle each manifest one by one in a chain
        String asgConfigurationContent = "{}";
        Map<String, Object> asgConfigurationOverrideProperties = new HashMap<>() {
          {
            put(AsgConfigurationManifestHandler.OverrideProperties.minSize, 0);
            put(AsgConfigurationManifestHandler.OverrideProperties.maxSize, 0);
            put(AsgConfigurationManifestHandler.OverrideProperties.desiredCapacity, 0);
          }
        };
        List<String> asgScalingPolicyContentList = null;

        chainState = AsgManifestHandlerChainFactory.builder()
                         .initialChainState(AsgManifestHandlerChainState.builder()
                                                .asgName(asgBlueGreenSwapServiceRequest.getOldAsgName())
                                                .newAsgName(asgBlueGreenSwapServiceRequest.getNewAsgName())
                                                .build())
                         .asgSdkManager(asgSdkManager)
                         .build()
                         .addHandler(AsgSwapService,
                             AsgSwapServiceManifestRequest.builder()
                                 .asgLoadBalancerConfig(asgBlueGreenSwapServiceRequest.getAsgLoadBalancerConfig())
                                 .region(asgInfraConfig.getRegion())
                                 .awsInternalConfig(awsInternalConfig)
                                 .build())
                         .addHandler(AsgScalingPolicy,
                             AsgScalingPolicyManifestRequest.builder().manifests(asgScalingPolicyContentList).build())
                         .addHandler(AsgConfiguration,
                             AsgConfigurationManifestRequest.builder()
                                 .manifests(Arrays.asList(asgConfigurationContent))
                                 .overrideProperties(asgConfigurationOverrideProperties)
                                 .build())

                         .executeUpsert();
      } else {
        // Chain factory code to handle each manifest one by one in a chain
        chainState = AsgManifestHandlerChainFactory.builder()
                         .initialChainState(AsgManifestHandlerChainState.builder()
                                                .asgName(asgBlueGreenSwapServiceRequest.getOldAsgName())
                                                .newAsgName(asgBlueGreenSwapServiceRequest.getNewAsgName())
                                                .build())
                         .asgSdkManager(asgSdkManager)
                         .build()
                         .addHandler(AsgSwapService,
                             AsgSwapServiceManifestRequest.builder()
                                 .asgLoadBalancerConfig(asgBlueGreenSwapServiceRequest.getAsgLoadBalancerConfig())
                                 .region(asgInfraConfig.getRegion())
                                 .awsInternalConfig(awsInternalConfig)
                                 .build())
                         .executeUpsert();
      }

      AutoScalingGroup newAutoScalingGroup = asgSdkManager.getASG(chainState.getNewAsgName());
      AutoScalingGroupContainer newAutoScalingGroupContainer =
          asgTaskHelper.mapToAutoScalingGroupContainer(newAutoScalingGroup);

      AsgBlueGreenSwapServiceResult asgBlueGreenSwapServiceResult =
          AsgBlueGreenSwapServiceResult.builder()
              .autoScalingGroupContainer(newAutoScalingGroupContainer)
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

      log.info("Completed task execution for command: Asg Swap Service");
      return asgBlueGreenSwapServiceResponse;
    } catch (Exception e) {
      swapServiceLogCallback.saveExecutionLog(color(format("Swapping Failed. %n"), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new AsgNGException(e);
    }
  }
}
