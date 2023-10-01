/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.converter;

import io.harness.ccm.commons.entities.ec2.recommendation.EC2Recommendation;
import io.harness.ccm.commons.entities.ec2.recommendation.EC2RecommendationDetail;
import io.harness.ccm.graphql.dto.recommendation.EC2InstanceDTO;
import io.harness.ccm.graphql.dto.recommendation.EC2RecommendationDTO;

import com.amazonaws.services.costexplorer.model.RecommendationTarget;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;

/**
 * This converter impl class will help to convert the EC2Recommendation entity and DTO.
 */
@Singleton
public class EC2RecommendationDTOConverter extends Converter<EC2RecommendationDTO, EC2Recommendation> {
  @Inject private static EC2InstanceDTOConverter instanceConverter = new EC2InstanceDTOConverter();
  private static final String MODIFY = "Modify";
  public EC2RecommendationDTOConverter() {
    super(EC2RecommendationDTOConverter::convertToEntity, EC2RecommendationDTOConverter::convertToDto);
  }

  private static EC2RecommendationDTO convertToDto(EC2Recommendation recommendation) {
    Optional<EC2RecommendationDetail> sameFamilyRecommendation = Optional.empty();
    Optional<EC2RecommendationDetail> crossFamilyRecommendation = Optional.empty();
    if (recommendation.getRecommendationInfo() != null) {
      sameFamilyRecommendation = recommendation.getRecommendationInfo()
                                     .stream()
                                     .filter(recommendationDetail
                                         -> RecommendationTarget.SAME_INSTANCE_FAMILY.name().equals(
                                             recommendationDetail.getRecommendationType()))
                                     .findAny();
      crossFamilyRecommendation = recommendation.getRecommendationInfo()
                                      .stream()
                                      .filter(recommendationDetail
                                          -> RecommendationTarget.CROSS_INSTANCE_FAMILY.name().equals(
                                              recommendationDetail.getRecommendationType()))
                                      .findAny();
    }
    return EC2RecommendationDTO.builder()
        .id(recommendation.getInstanceId())
        .awsAccountId(recommendation.getAwsAccountId())
        .showTerminated(!(recommendation.getRightsizingType().equalsIgnoreCase(MODIFY)))
        .current(EC2InstanceDTO.builder()
                     .instanceFamily(recommendation.getInstanceType())
                     .memory(recommendation.getMemory())
                     .monthlyCost(recommendation.getCurrentMonthlyCost())
                     .monthlySavings("0")
                     .region(recommendation.getRegion())
                     .vcpu(recommendation.getVcpu())
                     .cpuUtilisation(recommendation.getCurrentMaxCPU())
                     .memoryUtilisation(recommendation.getCurrentMaxMemory())
                     .build())
        .sameFamilyRecommendation(
            sameFamilyRecommendation
                .map(ec2RecommendationDetail -> instanceConverter.convertFromEntity(ec2RecommendationDetail))
                .orElse(null))
        .crossFamilyRecommendation(
            crossFamilyRecommendation
                .map(recommendationDetail -> instanceConverter.convertFromEntity(recommendationDetail))
                .orElse(null))
        .jiraDetails(recommendation.getJiraDetails())
        .serviceNowDetails(recommendation.getServiceNowDetails())
        .build();
  }

  private static EC2Recommendation convertToEntity(EC2RecommendationDTO ec2RecommendationDTO) {
    // this method is not in use right now.
    return EC2Recommendation.builder().build();
  }
}
