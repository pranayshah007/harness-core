package io.harness.ccm.graphql.dto.recommendation;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EC2RecommendationDTO implements RecommendationDetailsDTO {
  String id;
  String awsAccountId;
  EC2InstanceDTO current;
  EC2InstanceDTO sameFamilyRecommendation;
  EC2InstanceDTO crossFamilyRecommendation;
}
