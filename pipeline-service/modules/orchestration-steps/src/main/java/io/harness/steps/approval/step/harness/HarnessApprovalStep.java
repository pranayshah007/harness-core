/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.harness;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.AsyncExecutableWithRollback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.approval.ApprovalNotificationHandler;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@OwnedBy(CDC)
public class HarnessApprovalStep extends AsyncExecutableWithRollback {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.HARNESS_APPROVAL_STEP_TYPE;

  @Inject private ApprovalInstanceService approvalInstanceService;
  @Inject private ApprovalNotificationHandler approvalNotificationHandler;
  @Inject @Named("PipelineExecutorService") ExecutorService executorService;

  @Override
  public AsyncExecutableResponse executeAsync(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    HarnessApprovalInstance approvalInstance = HarnessApprovalInstance.fromStepParameters(ambiance, stepParameters);
    HarnessApprovalInstance savedApprovalInstance =
        (HarnessApprovalInstance) approvalInstanceService.save(approvalInstance);
    executorService.submit(() -> approvalNotificationHandler.sendNotification(savedApprovalInstance, ambiance));

    return AsyncExecutableResponse.newBuilder().addCallbackIds(approvalInstance.getId()).build();
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    HarnessApprovalResponseData responseData = (HarnessApprovalResponseData) responseDataMap.values().iterator().next();
    HarnessApprovalInstance instance =
        (HarnessApprovalInstance) approvalInstanceService.get(responseData.getApprovalInstanceId());
    return StepResponse.builder()
        .status(instance.getStatus().toFinalExecutionStatus())
        .failureInfo(instance.getFailureInfo())
        .stepOutcome(
            StepResponse.StepOutcome.builder().name("output").outcome(instance.toHarnessApprovalOutcome()).build())
        .build();
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepParameters, AsyncExecutableResponse executableResponse) {
    approvalInstanceService.expireByNodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
