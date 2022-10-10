/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service.support.impl;

import io.harness.batch.processing.cloudevents.aws.ecs.service.support.AwsCredentialHelper;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AwsCloudWatchHelperService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.request.AwsCloudWatchMetricDataRequest;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.response.AwsCloudWatchMetricDataResponse;
import io.harness.beans.ExecutionStatus;

import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.GetMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricDataResult;
import com.amazonaws.services.cloudwatch.model.MetricDataResult;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AwsCloudWatchHelperServiceImpl implements AwsCloudWatchHelperService {
  @Autowired private AwsCredentialHelper awsCredentialHelper;

  @Override
  public AwsCloudWatchMetricDataResponse getMetricData(AwsCloudWatchMetricDataRequest request) {
    GetMetricDataRequest metricDataRequest = new GetMetricDataRequest()
                                                 .withStartTime(request.getStartTime())
                                                 .withEndTime(request.getEndTime())
                                                 .withMetricDataQueries(request.getMetricDataQueries());
    List<MetricDataResult> metricDataResults = new ArrayList<>();
    String nextToken = null;
    do {
      metricDataRequest.withNextToken(nextToken);
      GetMetricDataResult metricDataResult =
          getMetricData(metricDataRequest, request.getAwsCrossAccountAttributes(), request.getRegion());
      metricDataResults.addAll(metricDataResult.getMetricDataResults());
      nextToken = metricDataResult.getNextToken();
    } while (nextToken != null);
    return AwsCloudWatchMetricDataResponse.builder()
        .metricDataResults(metricDataResults)
        .executionStatus(ExecutionStatus.SUCCESS)
        .build();
  }

  private GetMetricDataResult getMetricData(
      GetMetricDataRequest request, AwsCrossAccountAttributes awsCrossAccountAttributes, String region) {
    try (CloseableAmazonWebServiceClient<AmazonCloudWatchClient> closeableAmazonCloudWatchClient =
             new CloseableAmazonWebServiceClient(getAwsCloudWatchClient(region, awsCrossAccountAttributes))) {
      return closeableAmazonCloudWatchClient.getClient().getMetricData(request);
    } catch (Exception ex) {
      log.error("Exception getMetricData ", ex);
    }
    return new GetMetricDataResult();
  }

  @VisibleForTesting
  AmazonCloudWatchClient getAwsCloudWatchClient(String region, AwsCrossAccountAttributes awsCrossAccountAttributes) {
    AWSSecurityTokenService awsSecurityTokenService = awsCredentialHelper.constructAWSSecurityTokenService();
    AmazonCloudWatchClientBuilder builder = AmazonCloudWatchClientBuilder.standard().withRegion(region);
    AWSCredentialsProvider credentialsProvider =
        new STSAssumeRoleSessionCredentialsProvider
            .Builder(awsCrossAccountAttributes.getCrossAccountRoleArn(), UUID.randomUUID().toString())
            .withExternalId(awsCrossAccountAttributes.getExternalId())
            .withStsClient(awsSecurityTokenService)
            .build();
    builder.withCredentials(credentialsProvider);
    return (AmazonCloudWatchClient) builder.build();
  }
}
