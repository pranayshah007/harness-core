package io.harness.delegate.beans.connector.awsconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@OwnedBy(HarnessTeam.CDP)
public class AwsListElbListenersTaskParamsRequest extends AwsTaskParams {
  private String elasticLoadBalancer;
}
