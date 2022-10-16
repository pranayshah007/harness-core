package io.harness.batch.processing.cloudevents.aws.ec2.service.impl;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.costexplorer.AWSCostExplorerClient;
import com.amazonaws.services.costexplorer.AWSCostExplorerClientBuilder;
import com.amazonaws.services.costexplorer.model.*;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import io.harness.batch.processing.cloudevents.aws.ec2.service.AWSEC2RecommendationService;
import io.harness.batch.processing.cloudevents.aws.ec2.service.request.EC2RecommendationRequest;
import io.harness.batch.processing.cloudevents.aws.ec2.service.request.EC2RecommendationResponse;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.AwsCredentialHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

@Slf4j
@Service
public class AWSEC2RecommendationServiceImpl implements AWSEC2RecommendationService {
    @Autowired private AwsCredentialHelper awsCredentialHelper;
    private static final String aWSRegion = AWS_DEFAULT_REGION;

    @Override
    public EC2RecommendationResponse getRecommendations(EC2RecommendationRequest request) {

        GetRightsizingRecommendationRequest recommendationRequest = new GetRightsizingRecommendationRequest()
                .withConfiguration(new RightsizingRecommendationConfiguration()
                        .withRecommendationTarget(RecommendationTarget.CROSS_INSTANCE_FAMILY))
                .withService("AmazonEC2");
        String nextPageToken = null;
        List<RightsizingRecommendation> recommendationsResult = new ArrayList<>();
        do {
            recommendationRequest.withNextPageToken(nextPageToken);
            GetRightsizingRecommendationResult recommendationResult =
                    getRecommendations(request.getRegion(), request.getAwsCrossAccountAttributes(), recommendationRequest);
            log.info("recommendationResult.size() = {}", recommendationResult.getRightsizingRecommendations().size());
            if (!recommendationResult.getRightsizingRecommendations().isEmpty()) {
                log.info("recommendationResult = {}", recommendationResult);
            }
            recommendationsResult.addAll(recommendationResult.getRightsizingRecommendations());
            nextPageToken = recommendationResult.getNextPageToken();
        } while (nextPageToken != null);
        return null;
    }

    GetRightsizingRecommendationResult getRecommendations(String region, AwsCrossAccountAttributes awsCrossAccountAttributes,
                                                          GetRightsizingRecommendationRequest request) {
        try (CloseableAmazonWebServiceClient<AWSCostExplorerClient> closeableAWSCostExplorerClient =
                     new CloseableAmazonWebServiceClient(getAWSCostExplorerClient(region, awsCrossAccountAttributes))) {
            log.info("AWSCostExplorerClient created! {}", closeableAWSCostExplorerClient.getClient());
            return closeableAWSCostExplorerClient.getClient().getRightsizingRecommendation(request);
        } catch (Exception ex) {
            log.error("Exception getRightsizingRecommendation ", ex);
        }
        return new GetRightsizingRecommendationResult();
    }

    AWSCostExplorerClient getAWSCostExplorerClient(String region, AwsCrossAccountAttributes awsCrossAccountAttributes) {
        AWSSecurityTokenService awsSecurityTokenService = awsCredentialHelper.constructAWSSecurityTokenService();
        AWSCostExplorerClientBuilder builder = AWSCostExplorerClientBuilder.standard().withRegion(aWSRegion);
        AWSCredentialsProvider credentialsProvider =
                new STSAssumeRoleSessionCredentialsProvider
                        .Builder(awsCrossAccountAttributes.getCrossAccountRoleArn(), UUID.randomUUID().toString())
                        .withExternalId(awsCrossAccountAttributes.getExternalId())
                        .withStsClient(awsSecurityTokenService)
                        .build();
        builder.withCredentials(credentialsProvider);
        return (AWSCostExplorerClient) builder.build();
    }
}
