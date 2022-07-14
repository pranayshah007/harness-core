/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;
import io.harness.exception.ApprovalStepNGException;
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
import io.harness.steps.StepUtils;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.jira.beans.JiraApprovalResponseData;
import io.harness.steps.approval.step.jira.entities.JiraApprovalInstance;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Map;

@OwnedBy(CDC)
public class JiraApprovalStep extends AsyncExecutableWithRollback {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.JIRA_APPROVAL_STEP_TYPE;

  @Inject private ApprovalInstanceService approvalInstanceService;

  @Override
  public AsyncExecutableResponse executeAsync(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    JiraApprovalInstance approvalInstance = JiraApprovalInstance.fromStepParameters(ambiance, stepParameters);
    approvalInstance = (JiraApprovalInstance) approvalInstanceService.save(approvalInstance);
    return AsyncExecutableResponse.newBuilder()
        .addCallbackIds(approvalInstance.getId())
        .addAllLogKeys(CollectionUtils.emptyIfNull(
            StepUtils.generateLogKeys(StepUtils.generateLogAbstractions(ambiance), Collections.emptyList())))
        .build();
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    JiraApprovalResponseData jiraApprovalResponseData =
        (JiraApprovalResponseData) responseDataMap.values().iterator().next();
    JiraApprovalInstance instance =
        (JiraApprovalInstance) approvalInstanceService.get(jiraApprovalResponseData.getInstanceId());
    if (instance.getStatus() == ApprovalStatus.FAILED) {
      throw new ApprovalStepNGException(
          instance.getErrorMessage() != null ? instance.getErrorMessage() : "Unknown error polling jira issue");
    }
    return StepResponse.builder()
        .status(instance.getStatus().toFinalExecutionStatus())
        .failureInfo(instance.getFailureInfo())
        .stepOutcome(
            StepResponse.StepOutcome.builder().name("output").outcome(instance.toJiraApprovalOutcome()).build())
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
