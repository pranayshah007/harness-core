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
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.awsconnector.AwsListVpcTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.exception.AwsVPCException;
import io.harness.exception.ExceptionUtils;
import io.harness.logging.CommandExecutionStatus;

import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;
import software.wings.service.impl.aws.model.AwsVPC;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Tag;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AwsListVpcDelegateTaskHelper {
  private static final String NAME = "Name";

  @Inject private AwsCallTracker tracker;
  @Inject private AwsUtils awsUtils;

  public DelegateResponseData getVpcList(AwsTaskParams awsTaskParams) {
    awsUtils.decryptRequestDTOs(awsTaskParams.getAwsConnector(), awsTaskParams.getEncryptionDetails());
    AwsInternalConfig awsInternalConfig =
        awsUtils.getAwsInternalConfig(awsTaskParams.getAwsConnector(), awsTaskParams.getRegion());

    try (
        CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client = new CloseableAmazonWebServiceClient(
            awsUtils.getAmazonEc2Client(Regions.fromName(awsTaskParams.getRegion()), awsInternalConfig))) {
      tracker.trackEC2Call("List VPCs");

      DescribeVpcsResult describeVpcsResult = closeableAmazonEC2Client.getClient().describeVpcs(
          new DescribeVpcsRequest().withFilters(new Filter("state").withValues("available")));

      List<AwsVPC> result = convertToList(describeVpcsResult);

      return AwsListVpcTaskResponse.builder()
          .vpcs(result)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();
    } catch (Exception e) {
      log.error("Exception get vpc list", e);
      throw new AwsVPCException(ExceptionUtils.getMessage(e), e);
    }
  }

  private List<AwsVPC> convertToList(DescribeVpcsResult result) {
    return CollectionUtils.emptyIfNull(result.getVpcs())
        .stream()
        .map(vpc
            -> AwsVPC.builder()
                   .id(vpc.getVpcId())
                   .name(CollectionUtils.emptyIfNull(vpc.getTags())
                             .stream()
                             .filter(tag -> AwsListVpcDelegateTaskHelper.NAME.equals(tag.getKey()))
                             .findFirst()
                             .orElse(new Tag(AwsListVpcDelegateTaskHelper.NAME, ""))
                             .getValue())
                   .build())
        .collect(toList());
  }
}
