/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.dto.recommendation;

import io.harness.ccm.commons.beans.recommendation.CCMJiraDetails;
import io.harness.ccm.commons.beans.recommendation.CCMServiceNowDetails;
import io.harness.ccm.views.helper.ExecutionSummary;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.bson.types.ObjectId;

@Data
@Builder
public class RuleRecommendationDTO implements RecommendationDetailsDTO {
  ObjectId uuid;
  String name;
  String resourceType;
  String actionType;
  String accountId;
  Boolean isValid;
  List<ExecutionSummary> executions;
  CCMJiraDetails jiraDetails;
  CCMServiceNowDetails serviceNowDetails;
}
