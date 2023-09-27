/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

import static io.harness.aws.asg.manifest.AsgManifestType.AsgConfiguration;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgInstanceRefresh;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgLaunchTemplate;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgScalingPolicy;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgScheduledUpdateGroupAction;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgCommandUnitConstants;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.aws.asg.manifest.AsgLaunchTemplateManifestHandler;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainFactory;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainState;
import io.harness.aws.asg.manifest.request.AsgConfigurationManifestRequest;
import io.harness.aws.asg.manifest.request.AsgInstanceRefreshManifestRequest;
import io.harness.aws.asg.manifest.request.AsgLaunchTemplateManifestRequest;
import io.harness.aws.asg.manifest.request.AsgScalingPolicyManifestRequest;
import io.harness.aws.asg.manifest.request.AsgScheduledActionManifestRequest;
import io.harness.aws.beans.AsgCapacityConfig;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.AsgNGException;
import io.harness.delegate.task.aws.asg.AsgCommandRequest;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgRollingDeployRequest;
import io.harness.delegate.task.aws.asg.AsgRollingDeployResponse;
import io.harness.delegate.task.aws.asg.AsgRollingDeployResult;
import io.harness.delegate.task.aws.asg.AsgTaskHelper;
import io.harness.delegate.task.aws.asg.AutoScalingGroupContainer;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.service.impl.AwsUtils;

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
public class AsgRollingDeployCommandTaskHandler extends AsgCommandTaskNGHandler {
  @Inject private AsgTaskHelper asgTaskHelper;
  @Inject private ElbV2Client elbV2Client;
  @Inject private AwsUtils awsUtils;

  @Override
  protected AsgCommandResponse executeTaskInternal(AsgCommandRequest asgCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress)
      throws AsgNGException {
    if (!(asgCommandRequest instanceof AsgRollingDeployRequest)) {
      throw new InvalidArgumentsException(Pair.of("asgCommandRequest", "Must be instance of AsgRollingDeployRequest"));
    }

    AsgRollingDeployRequest asgRollingDeployRequest = (AsgRollingDeployRequest) asgCommandRequest;
    Boolean skipMatching = asgRollingDeployRequest.isSkipMatching();
    Boolean useAlreadyRunningInstances = asgRollingDeployRequest.isUseAlreadyRunningInstances();
    Integer instanceWarmup = asgRollingDeployRequest.getInstanceWarmup();
    Integer minimumHealthyPercentage = asgRollingDeployRequest.getMinimumHealthyPercentage();

    LogCallback logCallback = asgTaskHelper.getLogCallback(
        iLogStreamingTaskClient, AsgCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);

    try {
      AsgSdkManager asgSdkManager = asgTaskHelper.getAsgSdkManager(asgCommandRequest, logCallback, elbV2Client);
      Map<String, List<String>> asgStoreManifestsContent = asgTaskHelper.getAsgStoreManifestsContent(
          asgCommandRequest.getAsgInfraConfig(), asgRollingDeployRequest.getAsgStoreManifestsContent(), asgSdkManager);
      AsgInfraConfig asgInfraConfig = asgCommandRequest.getAsgInfraConfig();
      String region = asgInfraConfig.getRegion();
      AwsInternalConfig awsInternalConfig = awsUtils.getAwsInternalConfig(asgInfraConfig.getAwsConnectorDTO(), region);
      asgSdkManager.info("Starting Rolling Deployment");

      AutoScalingGroupContainer autoScalingGroupContainer = executeRollingDeployWithInstanceRefresh(asgSdkManager,
          asgStoreManifestsContent, skipMatching, useAlreadyRunningInstances, instanceWarmup, minimumHealthyPercentage,
          asgRollingDeployRequest.getAmiImageId(), awsInternalConfig, region,
          asgRollingDeployRequest.getAsgCapacityConfig(), asgRollingDeployRequest);

      AsgRollingDeployResult asgRollingDeployResult = AsgRollingDeployResult.builder()
                                                          .autoScalingGroupContainer(autoScalingGroupContainer)
                                                          .asgStoreManifestsContent(asgStoreManifestsContent)
                                                          .build();

      logCallback.saveExecutionLog(
          color("Rolling Deployment Finished Successfully", Green, Bold), INFO, CommandExecutionStatus.SUCCESS);

      return AsgRollingDeployResponse.builder()
          .asgRollingDeployResult(asgRollingDeployResult)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();

    } catch (Exception e) {
      logCallback.saveExecutionLog(
          color(format("Deployment Failed."), LogColor.Red, LogWeight.Bold), ERROR, CommandExecutionStatus.FAILURE);
      throw new AsgNGException(e);
    }
  }

  private AutoScalingGroupContainer executeRollingDeployWithInstanceRefresh(AsgSdkManager asgSdkManager,
      Map<String, List<String>> asgStoreManifestsContent, Boolean skipMatching, Boolean useAlreadyRunningInstances,
      Integer instanceWarmup, Integer minimumHealthyPercentage, String amiImageId, AwsInternalConfig awsInternalConfig,
      String region, AsgCapacityConfig asgCapacityConfig, AsgRollingDeployRequest asgRollingDeployRequest) {
    // Get the content of all required manifest files
    String asgLaunchTemplateContent = asgTaskHelper.getAsgLaunchTemplateContent(asgStoreManifestsContent);
    String asgConfigurationContent = asgTaskHelper.getAsgConfigurationContent(asgStoreManifestsContent);
    List<String> asgScalingPolicyContent = asgTaskHelper.getAsgScalingPolicyContent(asgStoreManifestsContent);
    List<String> asgScheduledActionContent = asgTaskHelper.getAsgScheduledActionContent(asgStoreManifestsContent);

    String asgName = asgTaskHelper.getAsgName(asgRollingDeployRequest, asgStoreManifestsContent);
    if (isEmpty(asgName)) {
      throw new InvalidArgumentsException(Pair.of("AutoScalingGroup name", "Must not be empty"));
    }

    Map<String, Object> asgLaunchTemplateOverrideProperties = new HashMap<>();
    asgLaunchTemplateOverrideProperties.put(AsgLaunchTemplateManifestHandler.OverrideProperties.amiImageId, amiImageId);
    asgTaskHelper.overrideLaunchTemplateWithUserData(asgLaunchTemplateOverrideProperties, asgStoreManifestsContent);

    Map<String, Object> asgConfigurationOverrideProperties = null;
    if (asgCapacityConfig != null) {
      asgConfigurationOverrideProperties = new HashMap<>();
      asgTaskHelper.overrideCapacity(asgConfigurationOverrideProperties, asgCapacityConfig);
    }

    // Chain factory code to handle each manifest one by one in a chain
    AsgManifestHandlerChainState chainState =
        AsgManifestHandlerChainFactory.builder()
            .initialChainState(AsgManifestHandlerChainState.builder().asgName(asgName).build())
            .asgSdkManager(asgSdkManager)
            .build()
            .addHandler(AsgLaunchTemplate,
                AsgLaunchTemplateManifestRequest.builder()
                    .manifests(Arrays.asList(asgLaunchTemplateContent))
                    .overrideProperties(asgLaunchTemplateOverrideProperties)
                    .build())
            .addHandler(AsgConfiguration,
                AsgConfigurationManifestRequest.builder()
                    .manifests(Arrays.asList(asgConfigurationContent))
                    .overrideProperties(asgConfigurationOverrideProperties)
                    .useAlreadyRunningInstances(useAlreadyRunningInstances)
                    .awsInternalConfig(awsInternalConfig)
                    .region(region)
                    .build())
            .addHandler(
                AsgScalingPolicy, AsgScalingPolicyManifestRequest.builder().manifests(asgScalingPolicyContent).build())
            .addHandler(AsgScheduledUpdateGroupAction,
                AsgScheduledActionManifestRequest.builder().manifests(asgScheduledActionContent).build())
            .addHandler(AsgInstanceRefresh,
                AsgInstanceRefreshManifestRequest.builder()
                    .skipMatching(skipMatching)
                    .instanceWarmup(instanceWarmup)
                    .minimumHealthyPercentage(minimumHealthyPercentage)
                    .build())
            .executeUpsert();

    AutoScalingGroup autoScalingGroup = chainState.getAutoScalingGroup();

    return asgTaskHelper.mapToAutoScalingGroupContainer(autoScalingGroup);
  }
}
