/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.dto;

import io.harness.ccm.governance.entities.RecommendationAdhocDTO;
import io.harness.ccm.views.helper.RuleCloudProviderType;

import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GovernanceAdhocEnqueueDTO {
  Map<String, RecommendationAdhocDTO> targetAccountDetails;
  List<String> targetAccounts;
  List<String> targetRegions;
  String ruleId;
  String policy;
  Boolean isDryRun;
  Boolean isOOTB;
  RuleCloudProviderType ruleCloudProviderType;
}
