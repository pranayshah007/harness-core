/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.asg;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgConfiguration;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgLaunchTemplate;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgScalingPolicy;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgScheduledUpdateGroupAction;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgUserData;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgContentParser;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.aws.asg.manifest.AsgConfigurationManifestHandler;
import io.harness.aws.asg.manifest.AsgLaunchTemplateManifestHandler;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainFactory;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainState;
import io.harness.aws.asg.manifest.request.AsgConfigurationManifestRequest;
import io.harness.aws.asg.manifest.request.AsgScalingPolicyManifestRequest;
import io.harness.aws.asg.manifest.request.AsgScheduledActionManifestRequest;
import io.harness.aws.beans.AsgCapacityConfig;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.AutoScalingGroupContainerToServerInstanceInfoMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.LogCallback;

import software.wings.service.impl.AwsUtils;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.RequestLaunchTemplateData;
import com.amazonaws.services.ec2.model.ResponseLaunchTemplateData;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class AsgTaskHelper {
  static final String VERSION_DELIMITER = "__";
  @Inject private AwsUtils awsUtils;
  @Inject private TimeLimiter timeLimiter;
  @Inject private AsgInfraConfigHelper asgInfraConfigHelper;
  private static final String CANARY_SUFFIX = "Canary";
  private static final String EXEC_STRATEGY_CANARY = "canary";
  private static final String EXEC_STRATEGY_BLUEGREEN = "blue-green";
  private static final String BG_GREEN = "GREEN";
  private static final String BG_BLUE = "BLUE";
  public LogCallback getLogCallback(ILogStreamingTaskClient logStreamingTaskClient, String commandUnitName,
      boolean shouldOpenStream, CommandUnitsProgress commandUnitsProgress) {
    return new NGDelegateLogCallback(logStreamingTaskClient, commandUnitName, shouldOpenStream, commandUnitsProgress);
  }

  public String getAsgLaunchTemplateContent(Map<String, List<String>> asgStoreManifestsContent) {
    return asgStoreManifestsContent.get(AsgLaunchTemplate).get(0);
  }

  public String getAsgConfigurationContent(Map<String, List<String>> asgStoreManifestsContent) {
    return asgStoreManifestsContent.get(AsgConfiguration).get(0);
  }

  public List<String> getAsgScalingPolicyContent(Map<String, List<String>> asgStoreManifestsContent) {
    return asgStoreManifestsContent.get(AsgScalingPolicy);
  }

  public List<String> getAsgScheduledActionContent(Map<String, List<String>> asgStoreManifestsContent) {
    return asgStoreManifestsContent.get(AsgScheduledUpdateGroupAction);
  }

  public String getUserData(Map<String, List<String>> asgStoreManifestsContent) {
    List<String> contents = asgStoreManifestsContent.get(AsgUserData);
    if (isEmpty(contents)) {
      return null;
    }

    if (contents.size() > 1) {
      throw new InvalidRequestException("userData should contain only one file");
    }

    return contents.get(0);
  }

  public AutoScalingGroupContainer mapToAutoScalingGroupContainer(AutoScalingGroup autoScalingGroup) {
    if (autoScalingGroup != null) {
      return AutoScalingGroupContainer.builder()
          .autoScalingGroupName(autoScalingGroup.getAutoScalingGroupName())
          .launchTemplateName(autoScalingGroup.getLaunchTemplate().getLaunchTemplateName())
          .launchTemplateVersion(autoScalingGroup.getLaunchTemplate().getVersion())
          .autoScalingGroupInstanceList(
              autoScalingGroup.getInstances()
                  .stream()
                  .map(instance
                      -> AutoScalingGroupInstance.builder()
                             .autoScalingGroupName(autoScalingGroup.getAutoScalingGroupName())
                             .instanceId(instance.getInstanceId())
                             .instanceType(instance.getInstanceType())
                             .launchTemplateVersion(autoScalingGroup.getLaunchTemplate().getVersion())
                             .build())
                  .collect(Collectors.toList()))
          .build();
    } else {
      return AutoScalingGroupContainer.builder().build();
    }
  }

  public AsgSdkManager getAsgSdkManager(AsgCommandRequest asgCommandRequest, LogCallback logCallback) {
    Integer timeoutInMinutes = asgCommandRequest.getTimeoutIntervalInMin();
    AsgInfraConfig asgInfraConfig = asgCommandRequest.getAsgInfraConfig();

    String region = asgInfraConfig.getRegion();
    AwsInternalConfig awsInternalConfig = awsUtils.getAwsInternalConfig(asgInfraConfig.getAwsConnectorDTO(), region);

    Supplier<AmazonEC2Client> ec2ClientSupplier =
        () -> awsUtils.getAmazonEc2Client(Regions.fromName(region), awsInternalConfig);
    Supplier<AmazonAutoScalingClient> asgClientSupplier =
        () -> awsUtils.getAmazonAutoScalingClient(Regions.fromName(region), awsInternalConfig);

    return AsgSdkManager.builder()
        .ec2ClientSupplier(ec2ClientSupplier)
        .asgClientSupplier(asgClientSupplier)
        .logCallback(logCallback)
        .steadyStateTimeOutInMinutes(timeoutInMinutes)
        .timeLimiter(timeLimiter)
        .build();
  }

  public AsgSdkManager getAsgSdkManager(
      AsgCommandRequest asgCommandRequest, LogCallback logCallback, ElbV2Client elbV2Client) {
    Integer timeoutInMinutes = asgCommandRequest.getTimeoutIntervalInMin();
    AsgInfraConfig asgInfraConfig = asgCommandRequest.getAsgInfraConfig();

    String region = asgInfraConfig.getRegion();
    AwsInternalConfig awsInternalConfig = awsUtils.getAwsInternalConfig(asgInfraConfig.getAwsConnectorDTO(), region);

    Supplier<AmazonEC2Client> ec2ClientSupplier =
        () -> awsUtils.getAmazonEc2Client(Regions.fromName(region), awsInternalConfig);
    Supplier<AmazonAutoScalingClient> asgClientSupplier =
        () -> awsUtils.getAmazonAutoScalingClient(Regions.fromName(region), awsInternalConfig);

    return AsgSdkManager.builder()
        .ec2ClientSupplier(ec2ClientSupplier)
        .asgClientSupplier(asgClientSupplier)
        .logCallback(logCallback)
        .steadyStateTimeOutInMinutes(timeoutInMinutes)
        .timeLimiter(timeLimiter)
        .elbV2Client(elbV2Client)
        .build();
  }

  public AsgSdkManager getInternalAsgSdkManager(AsgInfraConfig asgInfraConfig) {
    String region = asgInfraConfig.getRegion();
    AwsInternalConfig awsInternalConfig = awsUtils.getAwsInternalConfig(asgInfraConfig.getAwsConnectorDTO(), region);

    Supplier<AmazonEC2Client> ec2ClientSupplier =
        () -> awsUtils.getAmazonEc2Client(Regions.fromName(region), awsInternalConfig);
    Supplier<AmazonAutoScalingClient> asgClientSupplier =
        () -> awsUtils.getAmazonAutoScalingClient(Regions.fromName(region), awsInternalConfig);

    return AsgSdkManager.builder()
        .ec2ClientSupplier(ec2ClientSupplier)
        .asgClientSupplier(asgClientSupplier)
        .timeLimiter(timeLimiter)
        .build();
  }

  public String getExceptionMessage(Exception e) {
    Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
    return ExceptionUtils.getMessage(sanitizedException);
  }

  public List<ServerInstanceInfo> getAsgServerInstanceInfos(AsgDeploymentReleaseData deploymentReleaseData) {
    AsgInfraConfig asgInfraConfig = deploymentReleaseData.getAsgInfraConfig();
    asgInfraConfigHelper.decryptAsgInfraConfig(asgInfraConfig);
    AsgSdkManager asgSdkManager = getInternalAsgSdkManager(asgInfraConfig);
    String asgNameWithoutSuffix = deploymentReleaseData.getAsgNameWithoutSuffix();
    String executionStrategy = deploymentReleaseData.getExecutionStrategy();

    if (executionStrategy.equals(EXEC_STRATEGY_CANARY)) {
      String canaryAsgName = format("%s__%s", asgNameWithoutSuffix, CANARY_SUFFIX);
      AutoScalingGroup autoScalingGroup = asgSdkManager.getASG(canaryAsgName);
      AutoScalingGroupContainer autoScalingGroupContainer = mapToAutoScalingGroupContainer(autoScalingGroup);
      return AutoScalingGroupContainerToServerInstanceInfoMapper.toServerInstanceInfoList(autoScalingGroupContainer,
          asgInfraConfig.getInfraStructureKey(), asgInfraConfig.getRegion(), executionStrategy, asgNameWithoutSuffix,
          true);
    }

    else if (executionStrategy.equals(EXEC_STRATEGY_BLUEGREEN)) {
      String asgOne = asgNameWithoutSuffix + VERSION_DELIMITER + 1;
      String asgTwo = asgNameWithoutSuffix + VERSION_DELIMITER + 2;
      String blueAsg = asgTwo;
      String greenAsg = asgOne;
      AutoScalingGroup autoScalingGroupOne = asgSdkManager.getASG(asgOne);
      AutoScalingGroup autoScalingGroupTwo = asgSdkManager.getASG(asgTwo);
      if (autoScalingGroupOne != null && autoScalingGroupTwo == null) {
        String asgOneColour = asgSdkManager.describeBGTags(asgOne);
        if (asgOneColour == BG_BLUE) {
          blueAsg = asgOne;
          greenAsg = asgTwo;
        }
      } else if (autoScalingGroupOne == null && autoScalingGroupTwo != null) {
        String asgTwoColour = asgSdkManager.describeBGTags(asgTwo);
        if (asgTwoColour == BG_GREEN) {
          blueAsg = asgOne;
          greenAsg = asgTwo;
        }
      } else if (autoScalingGroupOne != null && autoScalingGroupTwo != null) {
        String asgOneColour = asgSdkManager.describeBGTags(asgOne);
        if (asgOneColour == BG_BLUE) {
          blueAsg = asgOne;
          greenAsg = asgTwo;
        }
      }

      List<ServerInstanceInfo> serverInstanceInfoList = new ArrayList<>();

      AutoScalingGroup autoScalingGroupBlue = asgSdkManager.getASG(blueAsg);
      AutoScalingGroupContainer autoScalingGroupContainerBlue = mapToAutoScalingGroupContainer(autoScalingGroupBlue);
      serverInstanceInfoList.addAll(AutoScalingGroupContainerToServerInstanceInfoMapper.toServerInstanceInfoList(
          autoScalingGroupContainerBlue, asgInfraConfig.getInfraStructureKey(), asgInfraConfig.getRegion(),
          executionStrategy, asgNameWithoutSuffix, true));

      AutoScalingGroup autoScalingGroupGreen = asgSdkManager.getASG(greenAsg);
      AutoScalingGroupContainer autoScalingGroupContainerGreen = mapToAutoScalingGroupContainer(autoScalingGroupGreen);
      serverInstanceInfoList.addAll(AutoScalingGroupContainerToServerInstanceInfoMapper.toServerInstanceInfoList(
          autoScalingGroupContainerGreen, asgInfraConfig.getInfraStructureKey(), asgInfraConfig.getRegion(),
          executionStrategy, asgNameWithoutSuffix, false));

      return serverInstanceInfoList;
    }

    else {
      AutoScalingGroup autoScalingGroup = asgSdkManager.getASG(asgNameWithoutSuffix);
      AutoScalingGroupContainer autoScalingGroupContainer = mapToAutoScalingGroupContainer(autoScalingGroup);
      return AutoScalingGroupContainerToServerInstanceInfoMapper.toServerInstanceInfoList(autoScalingGroupContainer,
          asgInfraConfig.getInfraStructureKey(), asgInfraConfig.getRegion(), executionStrategy, asgNameWithoutSuffix,
          true);
    }
  }

  public void overrideLaunchTemplateWithUserData(
      Map<String, Object> asgLaunchTemplateOverrideProperties, Map<String, List<String>> asgStoreManifestsContent) {
    String userData = getUserData(asgStoreManifestsContent);
    if (isNotEmpty(userData)) {
      asgLaunchTemplateOverrideProperties.put(AsgLaunchTemplateManifestHandler.OverrideProperties.userData, userData);
    }
  }

  public void overrideCapacity(
      Map<String, Object> asgConfigurationOverrideProperties, AsgCapacityConfig asgCapacityConfig) {
    if (asgCapacityConfig != null) {
      asgConfigurationOverrideProperties.put(
          AsgConfigurationManifestHandler.OverrideProperties.minSize, asgCapacityConfig.getMin());
      asgConfigurationOverrideProperties.put(
          AsgConfigurationManifestHandler.OverrideProperties.maxSize, asgCapacityConfig.getMax());
      asgConfigurationOverrideProperties.put(
          AsgConfigurationManifestHandler.OverrideProperties.desiredCapacity, asgCapacityConfig.getDesired());
    }
  }

  public boolean isBaseAsgDeployment(AsgInfraConfig asgInfraConfig) {
    return isNotEmpty(asgInfraConfig.getBaseAsgName());
  }

  public String getAsgName(AsgCommandRequest asgCommandRequest, Map<String, List<String>> asgStoreManifestsContent) {
    if (isNotEmpty(asgCommandRequest.getAsgName())) {
      return asgCommandRequest.getAsgName();
    }

    String asgConfigurationContent = getAsgConfigurationContent(asgStoreManifestsContent);
    CreateAutoScalingGroupRequest createAutoScalingGroupRequest =
        AsgContentParser.parseJson(asgConfigurationContent, CreateAutoScalingGroupRequest.class, true);
    return createAutoScalingGroupRequest.getAutoScalingGroupName();
  }

  public Map<String, List<String>> getAsgStoreManifestsContent(
      AsgInfraConfig asgInfraConfig, Map<String, List<String>> asgStoreManifestsContent, AsgSdkManager asgSdkManager) {
    boolean isBaseAsg = isBaseAsgDeployment(asgInfraConfig);
    if (isBaseAsg) {
      Map<String, List<String>> map =
          createAsgStoreManifestsContentFromAsg(asgInfraConfig.getBaseAsgName(), asgSdkManager);
      // merge userData
      if (isNotEmpty(asgStoreManifestsContent) && isNotEmpty(asgStoreManifestsContent.get(AsgUserData))) {
        map.put(AsgUserData, asgStoreManifestsContent.get(AsgUserData));
      }
      return map;
    }

    return asgStoreManifestsContent;
  }

  private Map<String, List<String>> createAsgStoreManifestsContentFromAsg(
      String baseAsgName, AsgSdkManager asgSdkManager) {
    asgSdkManager.info("Getting Asg configuration for ASG `%s`", baseAsgName);

    // Chain factory code to handle each manifest one by one in a chain
    AsgManifestHandlerChainState chainState =
        AsgManifestHandlerChainFactory.builder()
            .initialChainState(AsgManifestHandlerChainState.builder().asgName(baseAsgName).build())
            .asgSdkManager(asgSdkManager)
            .build()
            .addHandler(AsgConfiguration, AsgConfigurationManifestRequest.builder().build())
            .addHandler(AsgScalingPolicy, AsgScalingPolicyManifestRequest.builder().build())
            .addHandler(AsgScheduledUpdateGroupAction, AsgScheduledActionManifestRequest.builder().build())
            .getContent();

    if (chainState.getAutoScalingGroup() == null) {
      throw new InvalidRequestException(format("base ASG with name `%s` does not exist", baseAsgName));
    }

    Map<String, List<String>> result = chainState.getAsgManifestsDataForRollback();
    String launchTemplateContent = generateManifestContentForLaunchTemplateForBaseDeploy(asgSdkManager, baseAsgName);
    result.put(AsgLaunchTemplate, Arrays.asList(launchTemplateContent));

    return chainState.getAsgManifestsDataForRollback();
  }

  private String generateManifestContentForLaunchTemplateForBaseDeploy(
      AsgSdkManager asgSdkManager, String baseAsgName) {
    AutoScalingGroup baseAsg = asgSdkManager.getASG(baseAsgName);
    LaunchTemplateSpecification launchTemplateSpecification = baseAsg.getLaunchTemplate();

    ResponseLaunchTemplateData responseLaunchTemplateData = asgSdkManager.getLaunchTemplateData(
        launchTemplateSpecification.getLaunchTemplateName(), launchTemplateSpecification.getVersion());

    RequestLaunchTemplateData requestLaunchTemplateData = mapToRequestLaunchTemplateData(responseLaunchTemplateData);

    // don't use new CreateLaunchTemplateRequest() as it creates properties with bidirectional relations and leads to
    // stackOverflow
    Map<String, RequestLaunchTemplateData> map = Map.of("launchTemplateData", requestLaunchTemplateData);
    return AsgContentParser.toString(map, false);
  }

  private RequestLaunchTemplateData mapToRequestLaunchTemplateData(
      ResponseLaunchTemplateData responseLaunchTemplateData) {
    String responseLaunchTemplateDataJson = AsgContentParser.toString(responseLaunchTemplateData, false);
    return AsgContentParser.parseJson(responseLaunchTemplateDataJson, RequestLaunchTemplateData.class, false);
  }
}
