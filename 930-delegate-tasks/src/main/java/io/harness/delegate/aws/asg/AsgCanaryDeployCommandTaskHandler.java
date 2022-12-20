/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

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

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.google.inject.Inject;
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

  @Override
  protected AsgCommandResponse executeTaskInternal(AsgCommandRequest asgCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress)
      throws AsgNGException {
    if (!(asgCommandRequest instanceof AsgCanaryDeployRequest)) {
      throw new InvalidArgumentsException(Pair.of("asgCommandRequest", "Must be instance of AsgCanaryDeployRequest"));
    }

    AsgCanaryDeployRequest asgCanaryDeployRequest = (AsgCanaryDeployRequest) asgCommandRequest;
    AsgInfraConfig asgInfraConfig = asgCanaryDeployRequest.getAsgInfraConfig();
    Map<String, List<String>> asgStoreManifestsContent = asgCanaryDeployRequest.getAsgStoreManifestsContent();
    String serviceSuffix = asgCanaryDeployRequest.getServiceNameSuffix();
    Integer nrOfInstances = asgCanaryDeployRequest.getUnitValue();

    LogCallback logCallback = asgTaskHelper.getLogCallback(
        iLogStreamingTaskClient, AsgCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);

    try {
      String asgLaunchTemplateContent = asgTaskHelper.getAsgLaunchTemplateContent(asgStoreManifestsContent);
      String asgConfigurationContent = asgTaskHelper.getAsgConfigurationContent(asgStoreManifestsContent);

      AsgSdkManager asgSdkManager = asgTaskHelper.getAsgSdkManager(asgCommandRequest, logCallback);
      AutoScalingGroupContainer autoScalingGroupContainer = executeCanaryDeploy(
          asgSdkManager, asgLaunchTemplateContent, asgConfigurationContent, serviceSuffix, nrOfInstances);

      AsgCanaryDeployResult asgCanaryDeployResult = AsgCanaryDeployResult.builder()
                                                        .region(asgInfraConfig.getRegion())
                                                        .autoScalingGroupContainer(autoScalingGroupContainer)
                                                        .build();

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

  private AutoScalingGroupContainer executeCanaryDeploy(AsgSdkManager asgSdkManager, String asgLaunchTemplateContent,
      String asgConfigurationContent, String serviceSuffix, Integer nrOfInstances) {
    CreateAutoScalingGroupRequest createAutoScalingGroupRequest =
        AsgContentParser.parseJson(asgConfigurationContent, CreateAutoScalingGroupRequest.class);
    String asgName = createAutoScalingGroupRequest.getAutoScalingGroupName();
    if (isEmpty(asgName)) {
      throw new InvalidArgumentsException(Pair.of("AutoScalingGroup name", "Must not be empty"));
    }

    String canaryAsgName = format("%s__%s", asgName, serviceSuffix);
    createAutoScalingGroupRequest.setAutoScalingGroupName(canaryAsgName);

    AutoScalingGroup autoScalingGroup = asgSdkManager.getASG(canaryAsgName);
    if (autoScalingGroup != null) {
      asgSdkManager.info("Service with name `%s` already exists. Trying to delete it.", canaryAsgName);
      asgSdkManager.deleteService(autoScalingGroup);
    }

    autoScalingGroup =
        asgSdkManager.createAsgService(asgLaunchTemplateContent, createAutoScalingGroupRequest, nrOfInstances);

    return asgTaskHelper.mapToAutoScalingGroupContainer(autoScalingGroup);
  }
}
