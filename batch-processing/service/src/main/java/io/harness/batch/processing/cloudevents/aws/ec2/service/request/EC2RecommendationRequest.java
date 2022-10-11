package io.harness.batch.processing.cloudevents.aws.ec2.service.request;

import lombok.Builder;
import lombok.Data;
import software.wings.beans.AwsCrossAccountAttributes;

@Data
public class EC2RecommendationRequest {
    private AwsCrossAccountAttributes awsCrossAccountAttributes;
    private String region;

    @Builder
    public EC2RecommendationRequest(AwsCrossAccountAttributes awsCrossAccountAttributes, String region) {
        this.awsCrossAccountAttributes = awsCrossAccountAttributes;
        this.region = region;
    }
}
