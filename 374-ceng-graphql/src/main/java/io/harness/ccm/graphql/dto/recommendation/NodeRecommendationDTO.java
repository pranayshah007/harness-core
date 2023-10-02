/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.dto.recommendation;

import io.harness.ccm.commons.beans.recommendation.CCMJiraDetails;
import io.harness.ccm.commons.beans.recommendation.CCMServiceNowDetails;
import io.harness.ccm.commons.beans.recommendation.NodePoolId;
import io.harness.ccm.commons.beans.recommendation.TotalResourceUsage;
import io.harness.ccm.commons.beans.recommendation.models.RecommendClusterRequest;
import io.harness.ccm.commons.beans.recommendation.models.RecommendationResponse;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NodeRecommendationDTO implements RecommendationDetailsDTO {
  String id;
  NodePoolId nodePoolId;

  RecommendClusterRequest resourceRequirement;

  RecommendationResponse current;
  RecommendationResponse recommended;
  TotalResourceUsage totalResourceUsage;
  CCMJiraDetails jiraDetails;
  CCMServiceNowDetails serviceNowDetails;
}
