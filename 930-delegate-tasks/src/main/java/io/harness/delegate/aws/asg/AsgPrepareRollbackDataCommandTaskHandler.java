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
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgCommandUnitConstants;
import io.harness.aws.asg.AsgContentParser;
import io.harness.aws.asg.AsgMapper;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.AsgNGException;
import io.harness.delegate.task.aws.asg.AsgCommandRequest;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
import io.harness.delegate.task.aws.asg.AsgPrepareRollbackDataRequest;
import io.harness.delegate.task.aws.asg.AsgPrepareRollbackDataResponse;
import io.harness.delegate.task.aws.asg.AsgPrepareRollbackDataResult;
import io.harness.delegate.task.aws.asg.AsgTaskHelper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.ScalingPolicy;
import com.google.inject.Inject;
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
public class AsgPrepareRollbackDataCommandTaskHandler extends AsgCommandTaskNGHandler {
  @Inject private AsgTaskHelper asgTaskHelper;
  @Inject private AsgMapper asgMapper;

  @Override
  protected AsgCommandResponse executeTaskInternal(AsgCommandRequest asgCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress)
      throws AsgNGException {
    if (!(asgCommandRequest instanceof AsgPrepareRollbackDataRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("asgCommandRequest", "Must be instance of AsgPrepareRollbackDataRequest"));
    }

    AsgPrepareRollbackDataRequest asgPrepareRollbackDataRequest = (AsgPrepareRollbackDataRequest) asgCommandRequest;
    Map<String, List<String>> asgStoreManifestsContent = asgPrepareRollbackDataRequest.getAsgStoreManifestsContent();

    LogCallback logCallback = asgTaskHelper.getLogCallback(
        iLogStreamingTaskClient, AsgCommandUnitConstants.prepareRollbackData.toString(), true, commandUnitsProgress);

    try {
      AsgSdkManager asgSdkManager = asgTaskHelper.getAsgSdkManager(asgCommandRequest, logCallback);
      AsgPrepareRollbackDataResult asgPrepareRollbackDataResult =
          executePrepareRollbackData(asgSdkManager, asgStoreManifestsContent, logCallback);

      return AsgPrepareRollbackDataResponse.builder()
          .asgPrepareRollbackDataResult(asgPrepareRollbackDataResult)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();

    } catch (Exception e) {
      logCallback.saveExecutionLog(
          color(format("Prepare Rollback Data Operation Failed."), LogColor.Red, LogWeight.Bold), ERROR,
          CommandExecutionStatus.FAILURE);
      throw new AsgNGException(e);
    }
  }

  private AsgPrepareRollbackDataResult executePrepareRollbackData(
      AsgSdkManager asgSdkManager, Map<String, List<String>> asgStoreManifestsContent, LogCallback logCallback) {
    asgSdkManager.info("Prepare Rollback Data Operation Started");
    // Get ASG name from asg configuration manifest
    String asgConfigurationContent = asgTaskHelper.getAsgConfigurationContent(asgStoreManifestsContent);
    CreateAutoScalingGroupRequest createAutoScalingGroupRequest =
        AsgContentParser.parseJson(asgConfigurationContent, CreateAutoScalingGroupRequest.class);
    String asgName = createAutoScalingGroupRequest.getAutoScalingGroupName();
    if (isEmpty(asgName)) {
      throw new InvalidArgumentsException(Pair.of("AutoScalingGroup name", "Must not be empty"));
    }

    // Check if an ASG with this name already exists
    AutoScalingGroup autoScalingGroup = asgSdkManager.getASG(asgName);

    // If ASG with given name already exists then get the details of launchtemplate version, asg configuration & scaling
    // policies attached to it as string data type
    // & pass those values to prepare rollback data sweeping output
    if (autoScalingGroup != null) {
      List<ScalingPolicy> scalingPoliciesList = asgSdkManager.listAllScalingPoliciesOfAsg(asgName);

      String launchTemplateVersion = autoScalingGroup.getLaunchTemplate().getVersion();
      String asgConfiguration =
          asgMapper.createAutoScalingGroupRequestFromAutoScalingGroupConfiguration(autoScalingGroup);
      List<String> scalingPolicies =
          asgMapper.createScalingPolicyRequestsListFromScalingPoliciesList(scalingPoliciesList);

      logCallback.saveExecutionLog(color("Prepare Rollback Data Operation Finished Successfully", Green, Bold), INFO,
          CommandExecutionStatus.SUCCESS);

      Map<String, List<String>> prepareRollbackDataAsgStoreManifestsContent = new HashMap<>() {
        {
          put(AsgLaunchTemplate, Collections.singletonList(launchTemplateVersion));
          put(AsgConfiguration, Collections.singletonList(asgConfiguration));
          put(AsgScalingPolicy, scalingPolicies);
        }
      };

      return AsgPrepareRollbackDataResult
          .builder()
          //.asgStoreManifestsContent(prepareRollbackDataAsgStoreManifestsContent)
          .build();

    } else {
      logCallback.saveExecutionLog(
          color(
              format("Asg %s doesn't exist. Skipping Prepare Rollback Data Operation", asgName), White, LogWeight.Bold),
          INFO, CommandExecutionStatus.SUCCESS);
      return AsgPrepareRollbackDataResult.builder().build();
    }
  }
}
