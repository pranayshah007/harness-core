/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.changehandlers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
class PlanExecutionSummaryCDConstants {
  static final String ACCOUNT_ID_KEY = "accountId";
  static final String ORG_IDENTIFIER_KEY = "orgIdentifier";
  static final String PROJECT_IDENTIFIER_KEY = "projectIdentifier";
  static final String SERVICE_START_TS = "service_startts";
  static final String SERVICE_END_TS = "service_endts";
  static final String INFRASTRUCTURE_IDENTIFIER_KEY = "infrastructureIdentifier";
  static final String IDENTIFIER_KEY = "identifier";
  static final String INFRASTRUCTURE_NAME_KEY = "infrastructureName";
  static final String GITOPS_ENABLED_KEY = "gitOpsEnabled";
  static final String ENV_GROUP_ID = "envGroupId";
  static final String ENV_GROUP_NAME = "envGroupName";
  static final String FAILURE_INFO = "failureInfo";
}
