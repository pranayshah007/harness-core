/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.dto;

import io.harness.ccm.views.helper.RuleCloudProviderType;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GovernanceJobEnqueueDTO {
  String ruleEnforcementId;
  String roleArn;
  String externalId;
  RuleCloudProviderType ruleCloudProviderType;
  String ruleId;
  String policy;
  String targetAccountId;
  String targetRegion;
  Boolean isDryRun;
  Boolean isOOTB;
  String identifier;
}
