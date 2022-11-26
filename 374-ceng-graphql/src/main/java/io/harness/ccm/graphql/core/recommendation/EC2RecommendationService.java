/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.recommendation;

import io.harness.ccm.commons.dao.recommendation.EC2RecommendationDAO;
import io.harness.ccm.commons.entities.ec2.recommendation.EC2Recommendation;
import io.harness.ccm.graphql.core.converter.EC2RecommendationDTOConverter;
import io.harness.ccm.graphql.dto.recommendation.EC2RecommendationDTO;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * This service is a helper class between the API classes and DAOs.
 */
@Singleton
@Slf4j
public class EC2RecommendationService {
  @Inject private EC2RecommendationDAO ec2RecommendationDAO;
  @Inject private EC2RecommendationDTOConverter dtoConverter;
  @Inject private EC2InstanceUtilizationService ec2InstanceUtilizationService;

  /**
   * This method fetches the ec2 instances recommendations from mongo table EC2Recommendations.
   * @param accountIdentifier
   * @param id
   * @return
   */
  @Nullable
  public EC2RecommendationDTO getEC2RecommendationById(@NonNull final String accountIdentifier, String id) {
    final Optional<EC2Recommendation> ec2Recommendation =
        ec2RecommendationDAO.fetchEC2RecommendationById(accountIdentifier, id);

    if (!ec2Recommendation.isPresent()) {
      return EC2RecommendationDTO.builder().build();
    }
    EC2Recommendation recommendation = ec2Recommendation.get();
    return dtoConverter.convertFromEntity(recommendation);
  }
}
