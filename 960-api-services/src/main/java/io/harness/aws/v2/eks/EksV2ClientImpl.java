/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.v2.eks;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.AwsClientHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eks.EksClient;
import software.amazon.awssdk.services.eks.model.DescribeClusterRequest;
import software.amazon.awssdk.services.eks.model.DescribeClusterResponse;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class EksV2ClientImpl extends AwsClientHelper implements EksV2Client {
  @Override
  public SdkClient getClient(AwsInternalConfig awsConfig, String region) {
    return EksClient.builder()
        .credentialsProvider(getAwsCredentialsProvider(awsConfig))
        .region(Region.of(region))
        .overrideConfiguration(getClientOverrideFromBackoffOverride(awsConfig))
        .build();
  }

  @Override
  public String client() {
    return "EKS";
  }

  @Override
  public void handleClientServiceException(AwsServiceException awsServiceException) {
    throw new InvalidRequestException(awsServiceException.getMessage(), awsServiceException, USER);
  }

  @Override
  public DescribeClusterResponse describeClusters(AwsInternalConfig awsConfig, String region, String clusterName) {
    try {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      DescribeClusterRequest describeClusterRequest =
          (DescribeClusterRequest) DescribeClusterRequest.builder().name(clusterName).build();
      EksClient eksClient = (EksClient) getClient(awsConfig, region);
      DescribeClusterResponse describeClusterResponse = eksClient.describeCluster(describeClusterRequest);
      return describeClusterResponse;
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }

    return (DescribeClusterResponse) DescribeClusterResponse.builder().build();
  }
}
