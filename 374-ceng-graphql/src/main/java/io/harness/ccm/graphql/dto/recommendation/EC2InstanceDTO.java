package io.harness.ccm.graphql.dto.recommendation;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EC2InstanceDTO {
  String instanceFamily;
  String region;
  String memory;
  String vcpu;
  String cpuUtilisation;
  String memoryUtilisation;
  String monthlyCost;
}
