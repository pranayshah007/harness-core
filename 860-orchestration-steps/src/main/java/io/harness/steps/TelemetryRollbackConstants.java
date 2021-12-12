package io.harness.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDP)
public interface TelemetryRollbackConstants {
  // field values
  String TELEMETRY_ROLLBACK_PROP_VAL_UNASSIGNED = "N/A";

  // field names
  String TELEMETRY_ROLLBACK_PROP_PROJECT_ID = "projectId";
  String TELEMETRY_ROLLBACK_PROP_ORG_ID = "organizationId";
  String TELEMETRY_ROLLBACK_PROP_ACCOUNT_ID = "accountId";
  String TELEMETRY_ROLLBACK_PROP_ACCOUNT_NAME = "accountName";
  String TELEMETRY_ROLLBACK_PROP_EXECUTION_ID = "executionId";
  String TELEMETRY_ROLLBACK_PROP_PIPELINE_ID = "pipelineId";
  String TELEMETRY_ROLLBACK_PROP_STAGE_ID = "stageId";
  String TELEMETRY_ROLLBACK_PROP_STEP_ID = "stepId";
  String TELEMETRY_ROLLBACK_PROP_STATUS = "status";

  // telemetry event names
  String TELEMETRY_ROLLBACK_EXECUTION =
      "cd_rollback_execution"; // event sent for every rollback that happens during pipeline execution
}
