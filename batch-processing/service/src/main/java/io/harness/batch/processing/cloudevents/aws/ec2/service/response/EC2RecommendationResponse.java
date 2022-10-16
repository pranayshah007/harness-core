package io.harness.batch.processing.cloudevents.aws.ec2.service.response;

import com.amazonaws.services.costexplorer.model.RightsizingRecommendation;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data

public class EC2RecommendationResponse {
    List<RightsizingRecommendation> recommendationList;
    @Builder
    public EC2RecommendationResponse(List<RightsizingRecommendation> recommendationList) {
        this.recommendationList = recommendationList;
    }
}
