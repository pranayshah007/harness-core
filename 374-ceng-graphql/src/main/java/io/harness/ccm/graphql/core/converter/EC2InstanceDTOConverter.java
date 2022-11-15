package io.harness.ccm.graphql.core.converter;

import io.harness.ccm.commons.entities.ec2.recommendation.EC2RecommendationDetail;
import io.harness.ccm.graphql.dto.recommendation.EC2InstanceDTO;

/**
 * This converter impl class will help to convert the EC2Instance entity and DTO.
 */
public class EC2InstanceDTOConverter extends Converter<EC2InstanceDTO, EC2RecommendationDetail> {
  public EC2InstanceDTOConverter() {
    super(EC2InstanceDTOConverter::convertToEntity, EC2InstanceDTOConverter::convertToDto);
  }

  private static EC2InstanceDTO convertToDto(EC2RecommendationDetail recommendation) {
    return EC2InstanceDTO.builder()
        .instanceFamily(recommendation.getInstanceType())
        .memoryUtilisation(recommendation.getExpectedMaxMemory())
        .cpuUtilisation(recommendation.getExpectedMaxCPU())
        .vcpu(recommendation.getVcpu())
        .monthlyCost(recommendation.getExpectedMonthlyCost())
        .region(recommendation.getRegion())
        .memory(recommendation.getMemory())
        .build();
  }

  private static EC2RecommendationDetail convertToEntity(EC2InstanceDTO ec2InstanceDTO) {
    // this method is not in use right now
    return EC2RecommendationDetail.builder().build();
  }
}
