/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.custom;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.exception.ApprovalStepNGException;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.AsyncExecutableWithRollback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.custom.beans.CustomApprovalResponseData;
import io.harness.steps.approval.step.custom.entities.CustomApprovalInstance;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Map;

@OwnedBy(CDC)
public class CustomApprovalStep extends AsyncExecutableWithRollback {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.CUSTOM_APPROVAL).setStepCategory(StepCategory.STEP).build();

  @Inject private ApprovalInstanceService approvalInstanceService;
  @Inject private CustomApprovalInstanceHandler customApprovalInstanceHandler;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;

  @Override
  public AsyncExecutableResponse executeAsync(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.openStream(ShellScriptTaskNG.COMMAND_UNIT);
    CustomApprovalInstance approvalInstance = CustomApprovalInstance.fromStepParameters(ambiance, stepParameters);
    approvalInstance = (CustomApprovalInstance) approvalInstanceService.save(approvalInstance);
    customApprovalInstanceHandler.wakeup();
    return AsyncExecutableResponse.newBuilder()
        .addCallbackIds(approvalInstance.getId())
        .addAllLogKeys(CollectionUtils.emptyIfNull(StepUtils.generateLogKeys(
            StepUtils.generateLogAbstractions(ambiance), Collections.singletonList(ShellScriptTaskNG.COMMAND_UNIT))))
        .build();
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    try {
      CustomApprovalResponseData customApprovalResponseData =
          (CustomApprovalResponseData) responseDataMap.values().iterator().next();
      CustomApprovalInstance instance =
          (CustomApprovalInstance) approvalInstanceService.get(customApprovalResponseData.getInstanceId());
      if (instance.getStatus() == ApprovalStatus.FAILED) {
        throw new ApprovalStepNGException(
            instance.getErrorMessage() != null ? instance.getErrorMessage() : "Unknown error polling custom approval");
      }

      return StepResponse.builder()
          .status(instance.getStatus().toFinalExecutionStatus())
          .failureInfo(instance.getFailureInfo())
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .name(OutputExpressionConstants.OUTPUT)
                           .outcome(instance.toCustomApprovalOutcome(customApprovalResponseData.getTicket()))
                           .build())
          .build();
    } finally {
      closeLogStream(ambiance);
    }
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepParameters, AsyncExecutableResponse executableResponse) {
    approvalInstanceService.expireByNodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    closeLogStream(ambiance);
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  private void closeLogStream(Ambiance ambiance) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.closeStream(ShellScriptTaskNG.COMMAND_UNIT);
  }
}
