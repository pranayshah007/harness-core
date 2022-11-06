package io.harness.ccm.graphql.dto.recommendation;

import io.leangen.graphql.annotations.GraphQLNonNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EC2RecommendationDTO implements RecommendationDetailsDTO {
  String id;
  String awsAccountId;
  EC2InstanceDTO current;
  @GraphQLNonNull @Builder.Default Boolean showTerminated = false;
  EC2InstanceDTO sameFamilyRecommendation;
  EC2InstanceDTO crossFamilyRecommendation;
}
