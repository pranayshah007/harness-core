/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.custom;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eraro.ErrorCode.APPROVAL_STEP_NG_ERROR;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.delegate.task.shell.WinRmShellScriptTaskNG;
import io.harness.engine.executions.step.StepExecutionEntityService;
import io.harness.eraro.Level;
import io.harness.exception.ApprovalStepNGException;
import io.harness.execution.step.approval.custom.CustomApprovalStepExecutionDetails;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.custom.beans.CustomApprovalResponseData;
import io.harness.steps.approval.step.custom.entities.CustomApprovalInstance;
import io.harness.steps.executables.PipelineAsyncExecutable;
import io.harness.steps.shellscript.ShellType;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(CDC)
@Slf4j
public class CustomApprovalStep extends PipelineAsyncExecutable {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.CUSTOM_APPROVAL_STEP_TYPE;

  @Inject private ApprovalInstanceService approvalInstanceService;
  @Inject private CustomApprovalInstanceHandler customApprovalInstanceHandler;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private StepExecutionEntityService stepExecutionEntityService;
  @Inject @Named("DashboardExecutorService") ExecutorService dashboardExecutorService;

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    CustomApprovalInstance approvalInstance = CustomApprovalInstance.fromStepParameters(ambiance, stepParameters);
    openLogStream(ambiance, approvalInstance);
    approvalInstance = (CustomApprovalInstance) approvalInstanceService.save(approvalInstance);
    customApprovalInstanceHandler.wakeup();
    return AsyncExecutableResponse.newBuilder()
        .addCallbackIds(approvalInstance.getId())
        .addAllLogKeys(CollectionUtils.emptyIfNull(StepUtils.generateLogKeys(
            StepUtils.generateLogAbstractions(ambiance), Collections.singletonList(ShellScriptTaskNG.COMMAND_UNIT))))
        .build();
  }

  private void openLogStream(Ambiance ambiance, CustomApprovalInstance approvalInstance) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    List<String> units;
    if (ShellType.Bash.equals(approvalInstance.getShellType())) {
      units = Collections.singletonList(ShellScriptTaskNG.COMMAND_UNIT);
    } else {
      units = Arrays.asList(WinRmShellScriptTaskNG.INIT_UNIT, WinRmShellScriptTaskNG.COMMAND_UNIT);
    }

    for (String unit : units) {
      logStreamingStepClient.openStream(unit);
    }
  }

  @Override
  public StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    try {
      CustomApprovalResponseData customApprovalResponseData =
          (CustomApprovalResponseData) responseDataMap.values().iterator().next();
      CustomApprovalInstance instance =
          (CustomApprovalInstance) approvalInstanceService.get(customApprovalResponseData.getInstanceId());
      if (instance.getStatus() == ApprovalStatus.FAILED) {
        String errorMsg =
            instance.getErrorMessage() != null ? instance.getErrorMessage() : "Unknown error polling custom approval";
        FailureInfo failureInfo = FailureInfo.newBuilder()
                                      .addFailureData(FailureData.newBuilder()
                                                          .setLevel(Level.ERROR.name())
                                                          .setCode(APPROVAL_STEP_NG_ERROR.name())
                                                          .setMessage(errorMsg)
                                                          .build())
                                      .build();
        dashboardExecutorService.submit(()
                                            -> stepExecutionEntityService.updateStepExecutionEntity(
                                                ambiance, failureInfo, null, stepParameters.getName(), Status.FAILED));
        throw new ApprovalStepNGException(errorMsg);
      }
      CustomApprovalOutcome outcome = instance.toCustomApprovalOutcome(customApprovalResponseData.getTicket());
      dashboardExecutorService.submit(
          ()
              -> stepExecutionEntityService.updateStepExecutionEntity(ambiance, instance.getFailureInfo(),
                  createCustomApprovalStepExecutionDetailsFromCustomApprovalOutcome(outcome), stepParameters.getName(),
                  instance.getStatus().toFinalExecutionStatus()));
      return StepResponse.builder()
          .status(instance.getStatus().toFinalExecutionStatus())
          .failureInfo(instance.getFailureInfo())
          .stepOutcome(
              StepResponse.StepOutcome.builder().name(OutputExpressionConstants.OUTPUT).outcome(outcome).build())
          .build();
    } finally {
      closeLogStream(ambiance);
    }
  }

  private CustomApprovalStepExecutionDetails createCustomApprovalStepExecutionDetailsFromCustomApprovalOutcome(
      CustomApprovalOutcome outcome) {
    if (outcome != null) {
      return CustomApprovalStepExecutionDetails.builder().outputVariables(outcome.getOutputVariables()).build();
    }
    return null;
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepParameters, AsyncExecutableResponse executableResponse) {
    approvalInstanceService.abortByNodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    closeLogStream(ambiance);
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  private void closeLogStream(Ambiance ambiance) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.closeAllOpenStreamsWithPrefix(StepUtils.generateLogKeys(ambiance, emptyList()).get(0));
  }
}
