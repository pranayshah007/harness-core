/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsCallTracker;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsListEC2InstancesTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsListEC2InstancesTaskResponse;
import io.harness.exception.AwsInstanceException;
import io.harness.exception.ExceptionUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.api.DeploymentType;
import software.wings.beans.AwsInstanceFilter;
import software.wings.beans.AwsInstanceFilter.AwsInstanceFilterBuilder;
import software.wings.beans.AwsInstanceFilter.Tag;
import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;
import software.wings.service.impl.aws.model.AwsEC2Instance;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AwsListEC2InstancesDelegateTaskHelper {
  @Inject private AwsCallTracker tracker;
  @Inject private AwsUtils awsUtils;

  public DelegateResponseData getInstances(AwsListEC2InstancesTaskParamsRequest awsTaskParams) {
    List<AwsEC2Instance> result = getInstances(awsTaskParams.getAwsConnector(), awsTaskParams.getEncryptionDetails(),
        awsTaskParams.getRegion(), awsTaskParams.getVpcIds(), awsTaskParams.getTags(), awsTaskParams.isWinRm());

    return AwsListEC2InstancesTaskResponse.builder()
        .instances(result)
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }

  public List<AwsEC2Instance> getInstances(AwsConnectorDTO awsConnector, List<EncryptedDataDetail> encryptionDetails,
      String region, List<String> vpcIds, Map<String, String> tagsMap, boolean isWinRm) {
    awsUtils.decryptRequestDTOs(awsConnector, encryptionDetails);
    AwsInternalConfig awsInternalConfig = awsUtils.getAwsInternalConfig(awsConnector, region);

    AwsInstanceFilterBuilder awsInstanceFilterBuilder = AwsInstanceFilter.builder().vpcIds(vpcIds);

    if (tagsMap != null) {
      List<Tag> tags = tagsMap.entrySet()
                           .stream()
                           .map(entry -> Tag.builder().key(entry.getKey()).value(entry.getValue()).build())
                           .collect(toList());
      awsInstanceFilterBuilder.tags(tags);
    }

    DeploymentType deploymentType = isWinRm ? DeploymentType.WINRM : DeploymentType.SSH;
    List<Filter> filters = awsUtils.getFilters(deploymentType, awsInstanceFilterBuilder.build());
    Function<String, DescribeInstancesRequest> createDescribeInstancesRequest =
        nextToken -> new DescribeInstancesRequest().withNextToken(nextToken).withFilters(filters);

    return executeEc2ListInstancesTask(awsInternalConfig, region, createDescribeInstancesRequest, !isWinRm);
  }

  public List<AwsEC2Instance> getInstances(
      AwsInternalConfig awsInternalConfig, String region, List<String> instanceIds) {
    Function<String, DescribeInstancesRequest> createDescribeInstancesRequest =
        nextToken -> new DescribeInstancesRequest().withNextToken(nextToken).withInstanceIds(instanceIds);
    return executeEc2ListInstancesTask(awsInternalConfig, region, createDescribeInstancesRequest, false);
  }

  private List<AwsEC2Instance> convertToList(DescribeInstancesResult result, boolean excludeWinRm) {
    if (result.getReservations() == null) {
      return Collections.emptyList();
    }

    return result.getReservations()
        .stream()
        .map(Reservation::getInstances)
        .flatMap(List::stream)
        .filter(o -> excludeWinRmFilter(o, excludeWinRm))
        .map(this::mapInstance)
        .collect(Collectors.toList());
  }

  private boolean excludeWinRmFilter(Instance instance, boolean excludeWinRm) {
    return !excludeWinRm || excludeWinRm && !"windows".equals(instance.getPlatform());
  }

  private AwsEC2Instance mapInstance(Instance instance) {
    return AwsEC2Instance.builder()
        .instanceId(instance.getInstanceId())
        .publicDnsName(instance.getPublicDnsName())
        .build();
  }

  private List<AwsEC2Instance> executeEc2ListInstancesTask(AwsInternalConfig awsInternalConfig, String region,
      Function<String, DescribeInstancesRequest> createDescribeInstancesRequest, boolean excludeWinRm) {
    try (
        CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client = new CloseableAmazonWebServiceClient(
            awsUtils.getAmazonEc2Client(Regions.fromName(region), awsInternalConfig))) {
      List<AwsEC2Instance> result = new ArrayList<>();
      String nextToken = null;
      do {
        DescribeInstancesRequest describeInstancesRequest = createDescribeInstancesRequest.apply(nextToken);
        tracker.trackEC2Call("List Ec2 instances");
        DescribeInstancesResult describeInstancesResult =
            closeableAmazonEC2Client.getClient().describeInstances(describeInstancesRequest);
        result.addAll(convertToList(describeInstancesResult, excludeWinRm));
        nextToken = describeInstancesResult.getNextToken();
      } while (nextToken != null);
      if (isNotEmpty(result)) {
        tracker.trackEC2Call(
            "Found instances: " + result.stream().map(AwsEC2Instance::getInstanceId).collect(Collectors.joining(",")));
      } else {
        tracker.trackEC2Call("Found 0 instances");
      }
      return result;
    } catch (Exception e) {
      log.error("Exception get AWS instances", e);
      throw new AwsInstanceException(ExceptionUtils.getMessage(e), e);
    }
  }
}
