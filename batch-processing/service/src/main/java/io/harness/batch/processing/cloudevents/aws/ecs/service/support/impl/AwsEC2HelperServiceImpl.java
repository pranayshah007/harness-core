/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service.support.impl;

import static java.util.Collections.emptyList;

import io.harness.batch.processing.cloudevents.aws.ecs.service.support.AwsCredentialHelper;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AwsEC2HelperService;

import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.account.AccountClient;
import software.amazon.awssdk.services.account.model.ListRegionsRequest;
import software.amazon.awssdk.services.account.model.RegionOptStatus;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

@Slf4j
@Service
public class AwsEC2HelperServiceImpl implements AwsEC2HelperService {
  @Autowired private AwsCredentialHelper awsCredentialHelper;
  private static final String AWS_DEFAULT_REGION = "us-east-1";
  private static final List<RegionOptStatus> REGION_OPT_STATUSES =
      Arrays.asList(RegionOptStatus.ENABLED, RegionOptStatus.ENABLED_BY_DEFAULT);

  @Override
  public List<Instance> listEc2Instances(
      AwsCrossAccountAttributes awsCrossAccountAttributes, Set<String> instanceIds, String region) {
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEC2Client(region, awsCrossAccountAttributes))) {
      List<Instance> ec2Instances = new ArrayList<>();
      DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest().withInstanceIds(instanceIds);
      String nextToken = null;
      do {
        describeInstancesRequest.withNextToken(nextToken);
        DescribeInstancesResult describeInstancesResult =
            closeableAmazonEC2Client.getClient().describeInstances(describeInstancesRequest);
        ec2Instances.addAll(getInstanceList(describeInstancesResult));
        nextToken = describeInstancesResult.getNextToken();
      } while (nextToken != null);
      return ec2Instances;
    } catch (Exception ex) {
      log.error("Exception listEc2Instances ", ex);
    }
    return emptyList();
  }

  @Override
  public List<String> listRegions(AwsCrossAccountAttributes awsCrossAccountAttributes) {
    try {
      AccountClient accountClient = getAmazonAccountClient(awsCrossAccountAttributes);
      List<String> regions =
          accountClient.listRegions(ListRegionsRequest.builder().regionOptStatusContains(REGION_OPT_STATUSES).build())
              .regions()
              .stream()
              .map(region -> region.regionName())
              .collect(Collectors.toList());
      return regions;
    } catch (Exception ex) {
      log.info("Exception while listing regions {}", ex);
    }
    return Arrays.asList("us-east-1", "us-east-2", "us-west-2", "ap-southeast-1", "eu-west-1");
  }

  private List<Instance> getInstanceList(DescribeInstancesResult result) {
    List<Instance> instanceList = Lists.newArrayList();
    result.getReservations().forEach(reservation -> instanceList.addAll(reservation.getInstances()));
    return instanceList;
  }

  @VisibleForTesting
  AmazonEC2Client getAmazonEC2Client(String region, AwsCrossAccountAttributes awsCrossAccountAttributes) {
    AWSSecurityTokenService awsSecurityTokenService = awsCredentialHelper.constructAWSSecurityTokenService();
    AmazonEC2ClientBuilder builder = AmazonEC2ClientBuilder.standard().withRegion(region);
    AWSCredentialsProvider credentialsProvider =
        new STSAssumeRoleSessionCredentialsProvider
            .Builder(awsCrossAccountAttributes.getCrossAccountRoleArn(), UUID.randomUUID().toString())
            .withExternalId(awsCrossAccountAttributes.getExternalId())
            .withStsClient(awsSecurityTokenService)
            .build();
    builder.withCredentials(credentialsProvider);
    return (AmazonEC2Client) builder.build();
  }

  @VisibleForTesting
  AccountClient getAmazonAccountClient(AwsCrossAccountAttributes awsCrossAccountAttributes) {
    return AccountClient.builder()
        .credentialsProvider(getStsAssumeRoleAwsCredentialsProvider(awsCrossAccountAttributes))
        .region(Region.of(AWS_DEFAULT_REGION))
        .build();
  }

  private AwsCredentialsProvider getStsAssumeRoleAwsCredentialsProvider(
      AwsCrossAccountAttributes awsCrossAccountAttributes) {
    AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder()
                                              .roleArn(awsCrossAccountAttributes.getCrossAccountRoleArn())
                                              .roleSessionName(UUID.randomUUID().toString())
                                              .externalId(awsCrossAccountAttributes.getExternalId())
                                              .build();

    StsClient stsClient = StsClient.builder()
                              .region(Region.of(AWS_DEFAULT_REGION))
                              .credentialsProvider(awsCredentialHelper.getAwsCredentialsProvider())
                              .build();

    return StsAssumeRoleCredentialsProvider.builder().stsClient(stsClient).refreshRequest(assumeRoleRequest).build();
  }
}
