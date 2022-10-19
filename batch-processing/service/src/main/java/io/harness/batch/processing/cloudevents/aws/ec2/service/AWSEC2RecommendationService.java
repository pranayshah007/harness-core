package io.harness.batch.processing.cloudevents.aws.ec2.service;

import io.harness.batch.processing.cloudevents.aws.ec2.service.request.EC2RecommendationRequest;
import io.harness.batch.processing.cloudevents.aws.ec2.service.response.EC2RecommendationResponse;

public interface AWSEC2RecommendationService {
  EC2RecommendationResponse getRecommendations(EC2RecommendationRequest request);
}
