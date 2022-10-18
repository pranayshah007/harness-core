package io.harness.ccm.commons.entities.ec2.recommendation;

import lombok.Builder;
import lombok.Data;


@Data
@Builder(toBuilder = true)
public class EC2RecommendationDetail {
    String instanceType;
    String platform;
    String region;
    String memory;
    String sku;
    String hourlyOnDemandRate;
    String expectedMonthlySaving;
    String expectedMonthlyCost;
    String vcpu;
}
