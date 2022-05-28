package io.harness.cdng.service.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.steps.executables.ChildExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.StepUtils;
import io.harness.tasks.ResponseData;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
// TODO(archit): Check why and how serviceConfigStepOutcome will be replicated here, is it needed?
// TODO(archit): check getPipelineLevelModuleInfo for v2 impl
public class ServiceSectionStep implements ChildExecutable<ServiceSectionStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.SERVICE_SECTION.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private ServiceStepsHelper serviceStepsHelper;

  @Override
  public Class<ServiceSectionStepParameters> getStepParametersClass() {
    return ServiceSectionStepParameters.class;
  }

  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, ServiceSectionStepParameters stepParameters, StepInputPackage inputPackage) {
    NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance, true);
    logCallback.saveExecutionLog("Starting service step...");
    return ChildExecutableResponse.newBuilder()
        .setChildNodeId(stepParameters.getChildNodeId())
        .addAllLogKeys(CollectionUtils.emptyIfNull(
            StepUtils.generateLogKeys(StepUtils.generateLogAbstractions(ambiance), Collections.emptyList())))
        .build();
  }

  @Override
  public StepResponse handleChildResponse(
      Ambiance ambiance, ServiceSectionStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    StepResponse stepResponse = StepUtils.createStepResponseFromChildResponse(responseDataMap);
    NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
    if (StatusUtils.brokeStatuses().contains(stepResponse.getStatus())) {
      logCallback.saveExecutionLog(LogHelper.color("Failed to complete service step", LogColor.Red), LogLevel.INFO,
          CommandExecutionStatus.FAILURE);
    } else {
      logCallback.saveExecutionLog("Completed service step", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    }
    return stepResponse;
  }
}
