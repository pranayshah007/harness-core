/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ec2.service.impl;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import io.harness.batch.processing.cloudevents.aws.ec2.service.AWSEC2RecommendationService;
import io.harness.batch.processing.cloudevents.aws.ec2.service.request.EC2RecommendationRequest;
import io.harness.batch.processing.cloudevents.aws.ec2.service.response.EC2RecommendationResponse;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.AwsCredentialHelper;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.remote.CEProxyConfig;

import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.costexplorer.AWSCostExplorerClient;
import com.amazonaws.services.costexplorer.AWSCostExplorerClientBuilder;
import com.amazonaws.services.costexplorer.model.GetRightsizingRecommendationRequest;
import com.amazonaws.services.costexplorer.model.GetRightsizingRecommendationResult;
import com.amazonaws.services.costexplorer.model.RecommendationTarget;
import com.amazonaws.services.costexplorer.model.RightsizingRecommendation;
import com.amazonaws.services.costexplorer.model.RightsizingRecommendationConfiguration;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AWSEC2RecommendationServiceImpl implements AWSEC2RecommendationService {
  @Autowired private AwsCredentialHelper awsCredentialHelper;
  @Autowired private BatchMainConfig batchMainConfig;
  private static final String aWSRegion = AWS_DEFAULT_REGION;
  private static final String AMAZON_EC2 = "AmazonEC2";

  @Override
  public EC2RecommendationResponse getRecommendations(EC2RecommendationRequest request) {
    Map<RecommendationTarget, List<RightsizingRecommendation>> recommendationTargetListMap = new HashMap<>();
    List<RightsizingRecommendation> recommendationsOnCrossFamilyType =
        getRecommendationsBasedOnRecommendationTarget(RecommendationTarget.CROSS_INSTANCE_FAMILY, request);
    if (!recommendationsOnCrossFamilyType.isEmpty()) {
      recommendationTargetListMap.put(RecommendationTarget.CROSS_INSTANCE_FAMILY, recommendationsOnCrossFamilyType);
    }
    List<RightsizingRecommendation> recommendationsOnSameFamilyType =
        getRecommendationsBasedOnRecommendationTarget(RecommendationTarget.SAME_INSTANCE_FAMILY, request);
    if (!recommendationsOnSameFamilyType.isEmpty()) {
      recommendationTargetListMap.put(RecommendationTarget.SAME_INSTANCE_FAMILY, recommendationsOnSameFamilyType);
    }
    return EC2RecommendationResponse.builder().recommendationMap(recommendationTargetListMap).build();
  }

  List<RightsizingRecommendation> getRecommendationsBasedOnRecommendationTarget(
      RecommendationTarget recommendationTarget, EC2RecommendationRequest request) {
    GetRightsizingRecommendationRequest recommendationRequest =
        new GetRightsizingRecommendationRequest()
            .withConfiguration(
                new RightsizingRecommendationConfiguration().withRecommendationTarget(recommendationTarget))
            .withService(AMAZON_EC2);
    String nextPageToken = null;
    List<RightsizingRecommendation> recommendationsResult = new ArrayList<>();
    do {
      recommendationRequest.withNextPageToken(nextPageToken);
      GetRightsizingRecommendationResult recommendationResult =
          getRecommendations(request.getAwsCrossAccountAttributes(), recommendationRequest);
      if (Objects.nonNull(recommendationResult)
          && Objects.nonNull(recommendationResult.getRightsizingRecommendations())) {
        recommendationsResult.addAll(recommendationResult.getRightsizingRecommendations());
      }
      nextPageToken = recommendationResult.getNextPageToken();
    } while (nextPageToken != null);

    return recommendationsResult;
  }

  GetRightsizingRecommendationResult getRecommendations(
      AwsCrossAccountAttributes awsCrossAccountAttributes, GetRightsizingRecommendationRequest request) {
    try (CloseableAmazonWebServiceClient<AWSCostExplorerClient> closeableAWSCostExplorerClient =
             new CloseableAmazonWebServiceClient(getAWSCostExplorerClient(awsCrossAccountAttributes))) {
      return closeableAWSCostExplorerClient.getClient().getRightsizingRecommendation(request);
    } catch (Exception ex) {
      log.info("Exception from getRightsizingRecommendation api: ", ex);
    }
    return new GetRightsizingRecommendationResult();
  }

  AWSCostExplorerClient getAWSCostExplorerClient(AwsCrossAccountAttributes awsCrossAccountAttributes) {
    AWSSecurityTokenService awsSecurityTokenService = awsCredentialHelper.constructAWSSecurityTokenService();
    AWSCostExplorerClientBuilder builder = AWSCostExplorerClientBuilder.standard().withRegion(aWSRegion);
    if (batchMainConfig.getCeProxyConfig() != null && batchMainConfig.getCeProxyConfig().isEnabled()) {
      log.info("AWSCostExplorerClientBuilder initializing with proxy config");
      builder.withClientConfiguration(getClientConfiguration(batchMainConfig.getCeProxyConfig()));
    }
    AWSCredentialsProvider credentialsProvider =
        new STSAssumeRoleSessionCredentialsProvider
            .Builder(awsCrossAccountAttributes.getCrossAccountRoleArn(), UUID.randomUUID().toString())
            .withExternalId(awsCrossAccountAttributes.getExternalId())
            .withStsClient(awsSecurityTokenService)
            .build();
    builder.withCredentials(credentialsProvider);
    return (AWSCostExplorerClient) builder.build();
  }

  private ClientConfiguration getClientConfiguration(CEProxyConfig ceProxyConfig) {
    ClientConfiguration clientConfiguration = new ClientConfiguration();
    clientConfiguration.setProxyHost(ceProxyConfig.getHost());
    clientConfiguration.setProxyPort(ceProxyConfig.getPort());
    if (!ceProxyConfig.getUsername().isEmpty()) {
      clientConfiguration.setProxyUsername(ceProxyConfig.getUsername());
    }
    if (!ceProxyConfig.getPassword().isEmpty()) {
      clientConfiguration.setProxyPassword(ceProxyConfig.getPassword());
    }
    clientConfiguration.setProtocol(
        ceProxyConfig.getProtocol().equalsIgnoreCase("http") ? Protocol.HTTP : Protocol.HTTPS);
    return clientConfiguration;
  }
}
