/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.dto.recommendation;

import io.harness.ccm.commons.beans.recommendation.CCMJiraDetails;
import io.harness.ccm.commons.beans.recommendation.CCMServiceNowDetails;

import io.leangen.graphql.annotations.GraphQLNonNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(name = "EC2InstanceRecommendation", description = "EC2 instance recommendation")
public class EC2RecommendationDTO implements RecommendationDetailsDTO {
  String id;
  String awsAccountId;
  @Schema(name = "CurrentConfigurations", description = "Current instance configurations") EC2InstanceDTO current;
  @GraphQLNonNull @Builder.Default Boolean showTerminated = false;
  @Schema(name = "SameFamilyRecommendation", description = "Recommendation with same instance family")
  EC2InstanceDTO sameFamilyRecommendation;
  @Schema(name = "CrossFamilyRecommendation", description = "Recommendation with cross instance family")
  EC2InstanceDTO crossFamilyRecommendation;
  CCMJiraDetails jiraDetails;
  CCMServiceNowDetails serviceNowDetails;
}
