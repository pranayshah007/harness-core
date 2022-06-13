/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsCallTracker;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.awsconnector.AwsListTagsTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsListTagsTaskResponse;
import io.harness.exception.AwsTagException;
import io.harness.exception.ExceptionUtils;
import io.harness.logging.CommandExecutionStatus;

import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.DescribeTagsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.TagDescription;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AwsListTagsDelegateTaskHelper {
  @Inject private AwsCallTracker tracker;
  @Inject private AwsUtils awsUtils;

  public DelegateResponseData getTagList(AwsListTagsTaskParamsRequest awsTaskParams) {
    awsUtils.decryptRequestDTOs(awsTaskParams.getAwsConnector(), awsTaskParams.getEncryptionDetails());
    AwsInternalConfig awsInternalConfig =
        awsUtils.getAwsInternalConfig(awsTaskParams.getAwsConnector(), awsTaskParams.getRegion());

    try (
        CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client = new CloseableAmazonWebServiceClient(
            awsUtils.getAmazonEc2Client(Regions.fromName(awsTaskParams.getRegion()), awsInternalConfig))) {
      String nextToken = null;
      Map<String, String> result = new HashMap<>();
      do {
        tracker.trackEC2Call("List Tags");
        DescribeTagsResult describeTagsResult = closeableAmazonEC2Client.getClient().describeTags(
            new DescribeTagsRequest()
                .withNextToken(nextToken)
                .withFilters(new Filter("resource-type").withValues(awsTaskParams.getResourceType()))
                .withMaxResults(1000));
        result.putAll(convertToMap(describeTagsResult));
        nextToken = describeTagsResult.getNextToken();
      } while (nextToken != null);

      return AwsListTagsTaskResponse.builder()
          .tags(result)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();

    } catch (Exception e) {
      log.error("Exception get tag list", e);
      throw new AwsTagException(ExceptionUtils.getMessage(e), e);
    }
  }

  private Map<String, String> convertToMap(DescribeTagsResult result) {
    return CollectionUtils.emptyIfNull(result.getTags())
        .stream()
        .collect(Collectors.toMap(TagDescription::getKey, TagDescription::getValue, (key1, key2) -> key1));
  }
}
