package io.harness.ccm.commons.entities.ec2.recommendation;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class EC2RecommendationDetail {
  String instanceType;
  String recommendationType;
  String platform;
  String region;
  String memory;
  String sku;
  String hourlyOnDemandRate;
  String vcpu;
  String expectedMonthlySaving;
  String expectedMonthlyCost;
  String expectedMaxCPU;
  String expectedMaxMemory;
}
