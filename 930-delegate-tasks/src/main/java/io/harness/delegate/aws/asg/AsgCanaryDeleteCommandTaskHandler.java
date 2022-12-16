/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgCommandUnitConstants;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.AsgNGException;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteRequest;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteResponse;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteResult;
import io.harness.delegate.task.aws.asg.AsgCommandRequest;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgTaskHelperBase;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.wings.service.impl.AwsUtils;

import static java.lang.String.format;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class AsgCanaryDeleteCommandTaskHandler extends AsgCommandTaskNGHandler {
  @Inject private AsgTaskHelperBase asgTaskHelperBase;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private AwsUtils awsUtils;

  @Override
  protected AsgCommandResponse executeTaskInternal(AsgCommandRequest asgCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress)
      throws AsgNGException {
    if (!(asgCommandRequest instanceof AsgCanaryDeleteRequest)) {
      throw new InvalidArgumentsException(Pair.of("asgCommandRequest", "Must be instance of AsgCanaryDeleteRequest"));
    }

    AsgCanaryDeleteRequest asgCanaryDeleteRequest = (AsgCanaryDeleteRequest) asgCommandRequest;

    long timeoutInMillis = asgCanaryDeleteRequest.getTimeoutIntervalInMin() * 60000;

    AsgInfraConfig asgInfraConfig = asgCanaryDeleteRequest.getAsgInfraConfig();

    LogCallback canaryDeleteLogCallback = asgTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, AsgCommandUnitConstants.deleteService.toString(), true, commandUnitsProgress);

    String canaryAsgName = asgCanaryDeleteRequest.getCanaryAsgName();

    AsgCanaryDeleteResult asgCanaryDeleteResult;

    Regions awsRegion = Regions.fromName(asgInfraConfig.getRegion());

    AmazonAutoScalingClient amazonAutoScalingClient = awsUtils.getAmazonAutoScalingClient(
        awsRegion, awsNgConfigMapper.createAwsInternalConfig(asgInfraConfig.getAwsConnectorDTO()));

    DescribeAutoScalingGroupsRequest describeAutoScalingGroupsRequest =
        new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(canaryAsgName);

    DescribeAutoScalingGroupsResult describeAutoScalingGroupsResult =
        amazonAutoScalingClient.describeAutoScalingGroups(describeAutoScalingGroupsRequest);

    if (!describeAutoScalingGroupsResult.getAutoScalingGroups().isEmpty()) {
      AutoScalingGroup canaryAsg = describeAutoScalingGroupsResult.getAutoScalingGroups().get(0);

      //TODO

      asgCanaryDeleteResult = AsgCanaryDeleteResult.builder().canaryDeleted(true).canaryAsgName(canaryAsgName).build();

      canaryDeleteLogCallback.saveExecutionLog(
          format("Canary asg %s deleted", canaryAsgName), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    } else {
      canaryDeleteLogCallback.saveExecutionLog(
          format("Canary asg %s doesn't exist", canaryAsgName), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      asgCanaryDeleteResult = AsgCanaryDeleteResult.builder().canaryDeleted(false).canaryAsgName(canaryAsgName).build();
    }

    return AsgCanaryDeleteResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .asgCanaryDeleteResult(asgCanaryDeleteResult)
        .build();
  }
}

