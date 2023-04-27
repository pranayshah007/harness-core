/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

import static io.harness.aws.asg.manifest.AsgManifestType.AsgConfiguration;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgLaunchTemplate;
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
import io.harness.aws.asg.AsgContentParser;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.aws.asg.manifest.AsgConfigurationManifestHandler;
import io.harness.aws.asg.manifest.AsgLaunchTemplateManifestHandler;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainFactory;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainState;
import io.harness.aws.asg.manifest.request.AsgConfigurationManifestRequest;
import io.harness.aws.asg.manifest.request.AsgLaunchTemplateManifestRequest;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.AsgNGException;
import io.harness.delegate.task.aws.asg.AsgCanaryDeployRequest;
import io.harness.delegate.task.aws.asg.AsgCanaryDeployResponse;
import io.harness.delegate.task.aws.asg.AsgCanaryDeployResult;
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
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class AsgCanaryDeployCommandTaskHandler extends AsgCommandTaskNGHandler {
  @Inject private AsgTaskHelper asgTaskHelper;
  @Inject private ElbV2Client elbV2Client;
  @Inject private AwsUtils awsUtils;

  @Override
  protected AsgCommandResponse executeTaskInternal(AsgCommandRequest asgCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress)
      throws AsgNGException {
    if (!(asgCommandRequest instanceof AsgCanaryDeployRequest)) {
      throw new InvalidArgumentsException(Pair.of("asgCommandRequest", "Must be instance of AsgCanaryDeployRequest"));
    }

    AsgCanaryDeployRequest asgCanaryDeployRequest = (AsgCanaryDeployRequest) asgCommandRequest;
    Map<String, List<String>> asgStoreManifestsContent = asgCanaryDeployRequest.getAsgStoreManifestsContent();
    String serviceSuffix = asgCanaryDeployRequest.getServiceNameSuffix();
    Integer nrOfInstances = asgCanaryDeployRequest.getUnitValue();

    LogCallback logCallback = asgTaskHelper.getLogCallback(
        iLogStreamingTaskClient, AsgCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);

    try {
      AsgSdkManager asgSdkManager = asgTaskHelper.getAsgSdkManager(asgCommandRequest, logCallback, elbV2Client);
      AsgInfraConfig asgInfraConfig = asgCommandRequest.getAsgInfraConfig();
      String region = asgInfraConfig.getRegion();
      AwsInternalConfig awsInternalConfig = awsUtils.getAwsInternalConfig(asgInfraConfig.getAwsConnectorDTO(), region);
      AutoScalingGroupContainer autoScalingGroupContainer = executeCanaryDeploy(asgSdkManager, asgStoreManifestsContent,
          serviceSuffix, nrOfInstances, asgCanaryDeployRequest.getAmiImageId(), awsInternalConfig, region);

      AsgCanaryDeployResult asgCanaryDeployResult =
          AsgCanaryDeployResult.builder().autoScalingGroupContainer(autoScalingGroupContainer).build();

      logCallback.saveExecutionLog(
          color("Deployment Finished Successfully", Green, Bold), INFO, CommandExecutionStatus.SUCCESS);

      return AsgCanaryDeployResponse.builder()
          .asgCanaryDeployResult(asgCanaryDeployResult)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();

    } catch (Exception e) {
      logCallback.saveExecutionLog(
          color(format("Deployment Failed."), LogColor.Red, LogWeight.Bold), ERROR, CommandExecutionStatus.FAILURE);
      throw new AsgNGException(e);
    }
  }

  private AutoScalingGroupContainer executeCanaryDeploy(AsgSdkManager asgSdkManager,
      Map<String, List<String>> asgStoreManifestsContent, String serviceSuffix, Integer nrOfInstances,
      String amiImageId, AwsInternalConfig awsInternalConfig, String region) {
    String asgLaunchTemplateContent = asgTaskHelper.getAsgLaunchTemplateContent(asgStoreManifestsContent);
    String asgConfigurationContent = asgTaskHelper.getAsgConfigurationContent(asgStoreManifestsContent);

    CreateAutoScalingGroupRequest createAutoScalingGroupRequest =
        AsgContentParser.parseJson(asgConfigurationContent, CreateAutoScalingGroupRequest.class, true);
    String asgName = createAutoScalingGroupRequest.getAutoScalingGroupName();
    if (isEmpty(asgName)) {
      throw new InvalidArgumentsException(Pair.of("AutoScalingGroup name", "Must not be empty"));
    }

    String canaryAsgName = format("%s__%s", asgName, serviceSuffix);

    AutoScalingGroup autoScalingGroup = asgSdkManager.getASG(canaryAsgName);
    if (autoScalingGroup != null) {
      asgSdkManager.info("Asg with name `%s` already exists. Trying to delete it.", canaryAsgName);
      asgSdkManager.deleteAsg(canaryAsgName);
    }

    Map<String, Object> asgLaunchTemplateOverrideProperties =
        Collections.singletonMap(AsgLaunchTemplateManifestHandler.OverrideProperties.amiImageId, amiImageId);

    Map<String, Object> asgConfigurationOverrideProperties = new HashMap<>() {
      {
        put(AsgConfigurationManifestHandler.OverrideProperties.minSize, nrOfInstances);
        put(AsgConfigurationManifestHandler.OverrideProperties.maxSize, nrOfInstances);
        put(AsgConfigurationManifestHandler.OverrideProperties.desiredCapacity, nrOfInstances);
      }
    };

    AsgManifestHandlerChainState chainState =
        AsgManifestHandlerChainFactory.builder()
            .initialChainState(AsgManifestHandlerChainState.builder().asgName(canaryAsgName).build())
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
                    .build())
            .executeUpsert();

    autoScalingGroup = chainState.getAutoScalingGroup();

    return asgTaskHelper.mapToAutoScalingGroupContainer(autoScalingGroup);
  }
}
