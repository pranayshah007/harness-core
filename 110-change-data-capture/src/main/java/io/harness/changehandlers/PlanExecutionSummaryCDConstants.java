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
}
