package io.harness.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.ng.core.environment.beans.EnvironmentType.Production;
import static io.harness.steps.TelemetryRollbackConstants.TELEMETRY_ROLLBACK_EXECUTION;
import static io.harness.steps.TelemetryRollbackConstants.TELEMETRY_ROLLBACK_PROP_ACCOUNT_ID;
import static io.harness.steps.TelemetryRollbackConstants.TELEMETRY_ROLLBACK_PROP_ACCOUNT_NAME;
import static io.harness.steps.TelemetryRollbackConstants.TELEMETRY_ROLLBACK_PROP_EXECUTION_ID;
import static io.harness.steps.TelemetryRollbackConstants.TELEMETRY_ROLLBACK_PROP_ORG_ID;
import static io.harness.steps.TelemetryRollbackConstants.TELEMETRY_ROLLBACK_PROP_PIPELINE_ID;
import static io.harness.steps.TelemetryRollbackConstants.TELEMETRY_ROLLBACK_PROP_PROJECT_ID;
import static io.harness.steps.TelemetryRollbackConstants.TELEMETRY_ROLLBACK_PROP_STAGE_ID;
import static io.harness.steps.TelemetryRollbackConstants.TELEMETRY_ROLLBACK_PROP_STATUS;
import static io.harness.steps.TelemetryRollbackConstants.TELEMETRY_ROLLBACK_PROP_STEP_ID;
import static io.harness.steps.TelemetryRollbackConstants.TELEMETRY_ROLLBACK_PROP_VAL_UNASSIGNED;
import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
@Singleton
public class StepHelper {
  @Inject ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private TelemetryReporter telemetryReporter;

  public EnvironmentType getEnvironmentType(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.ENVIRONMENT));
    if (!optionalSweepingOutput.isFound()) {
      return EnvironmentType.ALL;
    }

    EnvironmentOutcome envOutcome = (EnvironmentOutcome) optionalSweepingOutput.getOutput();

    if (envOutcome == null || envOutcome.getType() == null) {
      return EnvironmentType.ALL;
    }

    return Production == envOutcome.getType() ? EnvironmentType.PROD : EnvironmentType.NON_PROD;
  }

  public Map<String, Object> sendRollbackTelemetryEvent(Ambiance ambiance, Status status) {
    return sendRollbackTelemetryEvent(ambiance, status, TELEMETRY_ROLLBACK_PROP_VAL_UNASSIGNED);
  }

  public Map<String, Object> sendRollbackTelemetryEvent(Ambiance ambiance, Status status, String accountName) {
    Map<String, Object> properties = null;

    try {
      if (ambiance != null && !String.valueOf(ambiance).isEmpty() && status != null) {
        if (telemetryReporter != null) {
          properties = new HashMap<>();

          String identity = ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getIdentifier();
          String accountId = AmbianceUtils.getAccountId(ambiance);

          properties.put(TELEMETRY_ROLLBACK_PROP_PROJECT_ID, AmbianceUtils.getProjectIdentifier(ambiance));
          properties.put(TELEMETRY_ROLLBACK_PROP_ORG_ID, AmbianceUtils.getOrgIdentifier(ambiance));
          properties.put(TELEMETRY_ROLLBACK_PROP_ACCOUNT_ID, accountId);
          properties.put(TELEMETRY_ROLLBACK_PROP_ACCOUNT_NAME,
              accountName != null ? accountName : TELEMETRY_ROLLBACK_PROP_VAL_UNASSIGNED);
          properties.put(TELEMETRY_ROLLBACK_PROP_EXECUTION_ID, ambiance.getPlanExecutionId());
          properties.put(TELEMETRY_ROLLBACK_PROP_PIPELINE_ID, ambiance.getMetadata().getPipelineIdentifier());
          properties.put(TELEMETRY_ROLLBACK_PROP_STAGE_ID,
              AmbianceUtils.getStageLevelFromAmbiance(ambiance).get().getIdentifier());
          properties.put(TELEMETRY_ROLLBACK_PROP_STEP_ID, AmbianceUtils.obtainStepIdentifier(ambiance));
          properties.put(TELEMETRY_ROLLBACK_PROP_STATUS, String.valueOf(status));

          log.info(String.format(
              "Sending Rollback Telemetry event: [execution=%s] [pipeline=%s], [stage=%s], [step=%s], [status=%s]",
              properties.get(TELEMETRY_ROLLBACK_PROP_EXECUTION_ID), properties.get(TELEMETRY_ROLLBACK_PROP_PIPELINE_ID),
              properties.get(TELEMETRY_ROLLBACK_PROP_STAGE_ID), properties.get(TELEMETRY_ROLLBACK_PROP_STEP_ID),
              properties.get(TELEMETRY_ROLLBACK_PROP_STATUS)));

          telemetryReporter.sendTrackEvent(TELEMETRY_ROLLBACK_EXECUTION, identity, accountId,
              (HashMap<String, Object>) properties, Collections.singletonMap(AMPLITUDE, true),
              io.harness.telemetry.Category.GLOBAL, TelemetryOption.builder().sendForCommunity(true).build());

          return properties;
        } else {
          log.error("TelemetryReporter was not injected.");
        }
      } else {
        log.error(
            "One or more arguments for method io.harness.steps.StepHelper.sendRollbackTelemetryEvent are invalid.");
      }
    } catch (Exception e) {
      log.error("Problem with sending rollback telemetry event.", e);
    }

    log.error("Unable to send rollback telemetry event!");
    return properties;
  }
}
