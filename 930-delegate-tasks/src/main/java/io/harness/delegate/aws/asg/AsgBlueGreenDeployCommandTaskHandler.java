/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

import static io.harness.aws.asg.manifest.AsgManifestType.AsgConfiguration;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgLaunchTemplate;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgScalingPolicy;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgScheduledUpdateGroupAction;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.aws.asg.AsgBlueGreenPrepareRollbackCommandTaskHandler.VERSION_DELIMITER;
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
import io.harness.aws.asg.manifest.AsgConfigurationManifestHandler;
import io.harness.aws.asg.manifest.AsgLaunchTemplateManifestHandler;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainFactory;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainState;
import io.harness.aws.asg.manifest.request.AsgConfigurationManifestRequest;
import io.harness.aws.asg.manifest.request.AsgLaunchTemplateManifestRequest;
import io.harness.aws.asg.manifest.request.AsgScalingPolicyManifestRequest;
import io.harness.aws.asg.manifest.request.AsgScheduledActionManifestRequest;
import io.harness.aws.beans.AsgLoadBalancerConfig;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.AsgNGException;
import io.harness.delegate.task.aws.asg.AsgBlueGreenDeployRequest;
import io.harness.delegate.task.aws.asg.AsgBlueGreenDeployResponse;
import io.harness.delegate.task.aws.asg.AsgBlueGreenDeployResult;
import io.harness.delegate.task.aws.asg.AsgCommandRequest;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class AsgBlueGreenDeployCommandTaskHandler extends AsgCommandTaskNGHandler {
  @Inject private AsgTaskHelper asgTaskHelper;
  @Inject private ElbV2Client elbV2Client;
  @Inject private AwsUtils awsUtils;

  @Override
  protected AsgCommandResponse executeTaskInternal(AsgCommandRequest asgCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress)
      throws AsgNGException {
    if (!(asgCommandRequest instanceof AsgBlueGreenDeployRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("asgCommandRequest", "Must be instance of AsgBlueGreenDeployRequest"));
    }

    AsgBlueGreenDeployRequest asgBlueGreenDeployRequest = (AsgBlueGreenDeployRequest) asgCommandRequest;
    Map<String, List<String>> asgStoreManifestsContent = asgBlueGreenDeployRequest.getAsgStoreManifestsContent();
    AsgLoadBalancerConfig lbConfig = asgBlueGreenDeployRequest.getAsgLoadBalancerConfig();
    String asgName = asgBlueGreenDeployRequest.getAsgName();
    String amiImageId = asgBlueGreenDeployRequest.getAmiImageId();
    boolean isFirstDeployment = asgBlueGreenDeployRequest.isFirstDeployment();
    boolean useAlreadyRunningInstances = asgBlueGreenDeployRequest.isUseAlreadyRunningInstances();

    List<String> targetGroupArnsList =
        isFirstDeployment ? lbConfig.getProdTargetGroupArnsList() : lbConfig.getStageTargetGroupArnsList();

    if (isEmpty(asgName)) {
      throw new InvalidArgumentsException(Pair.of("AutoScalingGroup name", "Must not be empty"));
    }

    LogCallback logCallback = asgTaskHelper.getLogCallback(
        iLogStreamingTaskClient, AsgCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);

    try {
      AsgSdkManager asgSdkManager = asgTaskHelper.getAsgSdkManager(asgCommandRequest, logCallback, elbV2Client);
      AsgInfraConfig asgInfraConfig = asgCommandRequest.getAsgInfraConfig();

      String region = asgInfraConfig.getRegion();
      AwsInternalConfig awsInternalConfig = awsUtils.getAwsInternalConfig(asgInfraConfig.getAwsConnectorDTO(), region);

      asgSdkManager.info("Starting Blue Green Deployment");

      AutoScalingGroupContainer stageAutoScalingGroupContainer =
          executeBGDeploy(asgSdkManager, asgStoreManifestsContent, asgName, amiImageId, targetGroupArnsList,
              isFirstDeployment, awsInternalConfig, region, useAlreadyRunningInstances);

      String asgNameWithoutSuffix = asgName.substring(0, asgName.length() - 3);
      String asgNameSuffix = asgName.substring(asgName.length() - 1);
      String prodAsgName = asgNameWithoutSuffix + VERSION_DELIMITER + 1;
      if (asgNameSuffix.equalsIgnoreCase(String.valueOf(1))) {
        prodAsgName = asgNameWithoutSuffix + VERSION_DELIMITER + 2;
      }

      AutoScalingGroupContainer prodAutoScalingGroupContainer = null;
      if (!isFirstDeployment) {
        prodAutoScalingGroupContainer = asgTaskHelper.mapToAutoScalingGroupContainer(asgSdkManager.getASG(prodAsgName));
      }

      AsgBlueGreenDeployResult asgBlueGreenDeployResult =
          AsgBlueGreenDeployResult.builder()
              .prodAutoScalingGroupContainer(prodAutoScalingGroupContainer)
              .stageAutoScalingGroupContainer(stageAutoScalingGroupContainer)
              .build();

      logCallback.saveExecutionLog(
          color("Blue Green Deployment Finished Successfully", Green, Bold), INFO, CommandExecutionStatus.SUCCESS);

      return AsgBlueGreenDeployResponse.builder()
          .asgBlueGreenDeployResult(asgBlueGreenDeployResult)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();

    } catch (Exception e) {
      logCallback.saveExecutionLog(
          color(format("Deployment Failed."), LogColor.Red, LogWeight.Bold), ERROR, CommandExecutionStatus.FAILURE);
      throw new AsgNGException(e);
    }
  }

  private AutoScalingGroupContainer executeBGDeploy(AsgSdkManager asgSdkManager,
      Map<String, List<String>> asgStoreManifestsContent, String asgName, String amiImageId,
      List<String> targetGroupArnList, boolean isFirstDeployment, AwsInternalConfig awsInternalConfig, String region,
      boolean useAlreadyRunningInstances) {
    if (isEmpty(asgName)) {
      throw new InvalidArgumentsException(Pair.of("AutoScalingGroup name", "Must not be empty"));
    }

    if (isEmpty(targetGroupArnList)) {
      throw new InvalidArgumentsException(Pair.of("Target Group Arns", "Must not be empty"));
    }

    // Get the content of all required manifest files
    String asgLaunchTemplateContent = asgTaskHelper.getAsgLaunchTemplateContent(asgStoreManifestsContent);
    String asgConfigurationContent = asgTaskHelper.getAsgConfigurationContent(asgStoreManifestsContent);
    List<String> asgScalingPolicyContent = asgTaskHelper.getAsgScalingPolicyContent(asgStoreManifestsContent);
    List<String> asgScheduledActionContent = asgTaskHelper.getAsgScheduledActionContent(asgStoreManifestsContent);

    Map<String, Object> asgLaunchTemplateOverrideProperties =
        Collections.singletonMap(AsgLaunchTemplateManifestHandler.OverrideProperties.amiImageId, amiImageId);

    Map<String, Object> asgConfigurationOverrideProperties = Collections.singletonMap(
        AsgConfigurationManifestHandler.OverrideProperties.targetGroupARNs, targetGroupArnList);

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
                    .awsInternalConfig(awsInternalConfig)
                    .region(region)
                    .useAlreadyRunningInstances(useAlreadyRunningInstances)
                    .build())
            .addHandler(
                AsgScalingPolicy, AsgScalingPolicyManifestRequest.builder().manifests(asgScalingPolicyContent).build())
            .addHandler(AsgScheduledUpdateGroupAction,
                AsgScheduledActionManifestRequest.builder().manifests(asgScheduledActionContent).build())
            .executeUpsert();

    AutoScalingGroup autoScalingGroup = chainState.getAutoScalingGroup();

    // set BLUE | GREEN tag
    String bgTagValue = isFirstDeployment ? AsgSdkManager.BG_BLUE : AsgSdkManager.BG_GREEN;
    asgSdkManager.updateBGTags(asgName, bgTagValue);
    asgSdkManager.info("Set tag %s=%s for asg %s", AsgSdkManager.BG_VERSION, bgTagValue, asgName);

    return asgTaskHelper.mapToAutoScalingGroupContainer(autoScalingGroup);
  }
}
