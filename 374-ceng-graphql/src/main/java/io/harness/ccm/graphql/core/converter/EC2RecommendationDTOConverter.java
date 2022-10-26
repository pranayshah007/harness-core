package io.harness.ccm.graphql.core.converter;

import io.harness.ccm.commons.entities.ec2.recommendation.EC2Recommendation;
import io.harness.ccm.commons.entities.ec2.recommendation.EC2RecommendationDetail;
import io.harness.ccm.graphql.core.EC2InstanceDTOConverter;
import io.harness.ccm.graphql.dto.recommendation.EC2InstanceDTO;
import io.harness.ccm.graphql.dto.recommendation.EC2RecommendationDTO;

import com.amazonaws.services.costexplorer.model.RecommendationTarget;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class EC2RecommendationDTOConverter extends Converter<EC2RecommendationDTO, EC2Recommendation> {
  @Inject private static EC2InstanceDTOConverter instanceConverter;

  public EC2RecommendationDTOConverter() {
    super(EC2RecommendationDTOConverter::convertToEntity, EC2RecommendationDTOConverter::convertToDto);
  }

  private static EC2RecommendationDTO convertToDto(EC2Recommendation recommendation) {
    Optional<EC2RecommendationDetail> sameFamilyRecommendation =
        recommendation.getRecommendationInfo()
            .stream()
            .filter(recommendationDetail
                -> RecommendationTarget.SAME_INSTANCE_FAMILY.equals(recommendationDetail.getRecommendationType()))
            .findFirst();
    Optional<EC2RecommendationDetail> crossFamilyRecommendation =
        recommendation.getRecommendationInfo()
            .stream()
            .filter(recommendationDetail
                -> RecommendationTarget.CROSS_INSTANCE_FAMILY.equals(recommendationDetail.getRecommendationType()))
            .findFirst();

    return EC2RecommendationDTO.builder()
        .id(recommendation.getInstanceId())
        .awsAccountId(recommendation.getAwsAccountId())
        .current(EC2InstanceDTO.builder()
                     .instanceFamily(recommendation.getInstanceType())
                     .memory(recommendation.getMemory())
                     .monthlyCost(recommendation.getCurrentMonthlyCost())
                     .region(recommendation.getRegion())
                     .vcpu(recommendation.getVcpu())
                     .cpuUtilisation(recommendation.getCurrentMaxCPU())
                     .memoryUtilisation(recommendation.getCurrentMaxMemory())
                     .build())
        .sameFamilyRecommendation((sameFamilyRecommendation.isPresent())
                ? instanceConverter.convertFromEntity(sameFamilyRecommendation.get())
                : null)
        .crossFamilyRecommendation((crossFamilyRecommendation.isPresent())
                ? instanceConverter.convertFromEntity(crossFamilyRecommendation.get())
                : null)
        .build();
  }

  private static EC2Recommendation convertToEntity(EC2RecommendationDTO ec2RecommendationDTO) {
    // this method is not in use right now.
    return EC2Recommendation.builder().build();
  }
}
