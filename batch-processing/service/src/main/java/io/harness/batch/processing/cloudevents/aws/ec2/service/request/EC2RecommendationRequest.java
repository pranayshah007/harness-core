package io.harness.batch.processing.cloudevents.aws.ec2.service.request;

import software.wings.beans.AwsCrossAccountAttributes;

import lombok.Builder;
import lombok.Data;

@Data
public class EC2RecommendationRequest {
  private AwsCrossAccountAttributes awsCrossAccountAttributes;
  @Builder
  public EC2RecommendationRequest(AwsCrossAccountAttributes awsCrossAccountAttributes) {
    this.awsCrossAccountAttributes = awsCrossAccountAttributes;
  }
}
