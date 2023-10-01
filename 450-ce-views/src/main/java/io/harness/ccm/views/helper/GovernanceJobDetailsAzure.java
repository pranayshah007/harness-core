/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.helper;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@OwnedBy(CE)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GovernanceJobDetailsAzure {
  // This goes to worker for custodian job processing. Any changes here should also be reflected in worker code
  String region;
  String tenantId;
  String subscriptionId;
  String policyId;
  String policy;
  String policyEnforcementId;
  Boolean isDryRun;
  Boolean isOOTB;
  String accountId;
  RuleExecutionType executionType;
  String cloudConnectorID;
}
