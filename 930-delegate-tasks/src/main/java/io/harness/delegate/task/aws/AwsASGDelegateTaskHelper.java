/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsCallTracker;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.awsconnector.AwsListASGInstancesTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsListASGNamesTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsListEC2InstancesTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.exception.AwsAutoScaleException;
import io.harness.exception.AwsInstanceException;
import io.harness.exception.ExceptionUtils;
import io.harness.logging.CommandExecutionStatus;

import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;
import software.wings.service.impl.aws.model.AwsEC2Instance;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.Instance;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AwsASGDelegateTaskHelper {
  @Inject private AwsCallTracker tracker;
  @Inject private AwsListEC2InstancesDelegateTaskHelper awsListEC2InstancesDelegateTaskHelper;
  @Inject private AwsUtils awsUtils;

  public DelegateResponseData getInstances(AwsListASGInstancesTaskParamsRequest awsTaskParams) {
    awsUtils.decryptRequestDTOs(awsTaskParams.getAwsConnector(), awsTaskParams.getEncryptionDetails());
    AwsInternalConfig awsInternalConfig =
        awsUtils.getAwsInternalConfig(awsTaskParams.getAwsConnector(), awsTaskParams.getRegion());

    List<AwsEC2Instance> result = new ArrayList<>();

    List<String> instanceIds =
        getInstanceIds(awsInternalConfig, awsTaskParams.getRegion(), awsTaskParams.getAutoScalingGroupName());

    if (CollectionUtils.isNotEmpty(instanceIds)) {
      result =
          awsListEC2InstancesDelegateTaskHelper.getInstances(awsInternalConfig, awsTaskParams.getRegion(), instanceIds);
    }

    return AwsListEC2InstancesTaskResponse.builder()
        .instances(result)
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }

  public List<String> getInstanceIds(AwsInternalConfig awsInternalConfig, String region, String autoScalingGroupName) {
    List<String> result = new ArrayList<>();

    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(
                 awsUtils.getAmazonAutoScalingClient(Regions.fromName(region), awsInternalConfig))) {
      DescribeAutoScalingGroupsRequest describeAutoScalingGroupsRequest =
          new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(autoScalingGroupName);
      tracker.trackASGCall("Describe ASGs");
      DescribeAutoScalingGroupsResult describeAutoScalingGroupsResult =
          closeableAmazonAutoScalingClient.getClient().describeAutoScalingGroups(describeAutoScalingGroupsRequest);

      if (CollectionUtils.isNotEmpty(describeAutoScalingGroupsResult.getAutoScalingGroups())) {
        AutoScalingGroup autoScalingGroup = describeAutoScalingGroupsResult.getAutoScalingGroups().get(0);
        result = autoScalingGroup.getInstances().stream().map(Instance::getInstanceId).collect(toList());
      }

      return result;
    } catch (Exception e) {
      log.error("Exception getInstanceIds", e);
      throw new AwsInstanceException(ExceptionUtils.getMessage(e), e);
    }
  }

  public List<AutoScalingGroup> getAllASGs(AwsTaskParams awsTaskParams) {
    awsUtils.decryptRequestDTOs(awsTaskParams.getAwsConnector(), awsTaskParams.getEncryptionDetails());
    AwsInternalConfig awsInternalConfig =
        awsUtils.getAwsInternalConfig(awsTaskParams.getAwsConnector(), awsTaskParams.getRegion());

    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(
                 awsUtils.getAmazonAutoScalingClient(Regions.fromName(awsTaskParams.getRegion()), awsInternalConfig))) {
      DescribeAutoScalingGroupsRequest describeAutoScalingGroupsRequest;
      DescribeAutoScalingGroupsResult describeAutoScalingGroupsResult;
      String nextToken = null;
      List<AutoScalingGroup> result = new ArrayList<>();
      do {
        describeAutoScalingGroupsRequest =
            new DescribeAutoScalingGroupsRequest().withMaxRecords(100).withNextToken(nextToken);
        tracker.trackASGCall("Describe Autoscaling Group");
        describeAutoScalingGroupsResult =
            closeableAmazonAutoScalingClient.getClient().describeAutoScalingGroups(describeAutoScalingGroupsRequest);
        result.addAll(describeAutoScalingGroupsResult.getAutoScalingGroups());
        nextToken = describeAutoScalingGroupsResult.getNextToken();
      } while (nextToken != null);

      return result;
    } catch (Exception e) {
      log.error("Exception get ASG names", e);
      throw new AwsAutoScaleException(ExceptionUtils.getMessage(e), e);
    }
  }

  public AwsListASGNamesTaskResponse getASGNames(AwsTaskParams awsTaskParams) {
    List<String> names =
        getAllASGs(awsTaskParams).stream().map(AutoScalingGroup::getAutoScalingGroupName).collect(toList());
    return AwsListASGNamesTaskResponse.builder()
        .names(names)
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }
}
