package io.harness.batch.processing.cloudevents.aws.ec2.service.helper;

import com.amazonaws.services.costexplorer.model.RightsizingRecommendation;
import lombok.Builder;
import lombok.Data;

@Data
public class EC2InstanceRecommendationInfo {
  private String recommendationType;
  private RightsizingRecommendation recommendation;

  @Builder
  public EC2InstanceRecommendationInfo(String recommendationType, RightsizingRecommendation recommendation) {
    this.recommendationType = recommendationType;
    this.recommendation = recommendation;
  }
}
