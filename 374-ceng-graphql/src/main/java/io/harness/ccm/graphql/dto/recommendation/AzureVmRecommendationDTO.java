/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.dto.recommendation;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.recommendation.CCMJiraDetails;
import io.harness.ccm.commons.beans.recommendation.CCMServiceNowDetails;

import io.leangen.graphql.annotations.GraphQLNonNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(name = "AzureVmRecommendation", description = "Azure VM recommendation")
@OwnedBy(CE)
public class AzureVmRecommendationDTO implements RecommendationDetailsDTO {
  String id;
  String tenantId;
  String subscriptionId;
  String resourceGroupId;
  String vmName;
  String vmId;
  String connectorName;
  String connectorId;
  int duration;
  @Schema(name = "CurrentConfigurations", description = "Current vm configurations") AzureVmDTO current;
  @GraphQLNonNull @Builder.Default Boolean showTerminated = false;
  @Schema(name = "TargetConfigurations", description = "Target vm configurations") AzureVmDTO target;
  CCMJiraDetails jiraDetails;
  CCMServiceNowDetails serviceNowDetails;
}
