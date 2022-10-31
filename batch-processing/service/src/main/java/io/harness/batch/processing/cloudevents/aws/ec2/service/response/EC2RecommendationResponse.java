package io.harness.batch.processing.cloudevents.aws.ec2.service.response;

import com.amazonaws.services.costexplorer.model.RecommendationTarget;
import com.amazonaws.services.costexplorer.model.RightsizingRecommendation;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
public class EC2RecommendationResponse {
  Map<RecommendationTarget, List<RightsizingRecommendation>> recommendationMap;
  @Builder
  public EC2RecommendationResponse(Map<RecommendationTarget, List<RightsizingRecommendation>> recommendationMap) {
    this.recommendationMap = recommendationMap;
  }
}
