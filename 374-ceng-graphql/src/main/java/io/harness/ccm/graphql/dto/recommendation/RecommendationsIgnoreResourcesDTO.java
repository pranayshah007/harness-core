/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.dto.recommendation;

import io.harness.ccm.commons.entities.recommendations.RecommendationAzureVmId;
import io.harness.ccm.commons.entities.recommendations.RecommendationEC2InstanceId;
import io.harness.ccm.commons.entities.recommendations.RecommendationECSServiceId;
import io.harness.ccm.commons.entities.recommendations.RecommendationGovernanceRuleId;
import io.harness.ccm.commons.entities.recommendations.RecommendationNodepoolId;
import io.harness.ccm.commons.entities.recommendations.RecommendationWorkloadId;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
public class RecommendationsIgnoreResourcesDTO {
  Set<RecommendationWorkloadId> workloads;
  Set<RecommendationNodepoolId> nodepools;
  Set<RecommendationECSServiceId> ecsServices;
  Set<RecommendationEC2InstanceId> ec2Instances;
  Set<RecommendationAzureVmId> azureVmIds;
  Set<RecommendationGovernanceRuleId> governanceRuleIds;
}
