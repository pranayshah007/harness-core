package io.harness.ccm.graphql.dto.recommendation;

import io.leangen.graphql.annotations.GraphQLNonNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(name = "EC2InstanceRecommendation", description = "EC2 instance recommendation")
public class EC2RecommendationDTO implements RecommendationDetailsDTO {
  String id;
  String awsAccountId;
  @Schema(name = "CurrentConfigurations", description = "Current instance configurations") EC2InstanceDTO current;
  @GraphQLNonNull @Builder.Default Boolean showTerminated = false;
  @Schema(name = "SameFamilyRecommendation", description = "Recommendation with same instance family")
  EC2InstanceDTO sameFamilyRecommendation;
  @Schema(name = "CrossFamilyRecommendation", description = "Recommendation with cross instance family")
  EC2InstanceDTO crossFamilyRecommendation;
}
