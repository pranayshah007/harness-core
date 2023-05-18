/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.harness;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.exception.InvalidRequestException;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.approval.ApprovalNotificationHandler;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalUserGroupDTO;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;
import io.harness.steps.executables.PipelineAsyncExecutable;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@OwnedBy(CDC)
public class HarnessApprovalStep extends PipelineAsyncExecutable {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.HARNESS_APPROVAL_STEP_TYPE;

  @Inject private ApprovalInstanceService approvalInstanceService;
  @Inject private ApprovalNotificationHandler approvalNotificationHandler;
  @Inject @Named("PipelineExecutorService") ExecutorService executorService;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.openStream(ShellScriptTaskNG.COMMAND_UNIT);
    HarnessApprovalInstance approvalInstance = HarnessApprovalInstance.fromStepParameters(ambiance, stepParameters);

    List<UserGroupDTO> validatedUserGroups = approvalNotificationHandler.getUserGroups(approvalInstance);
    if (EmptyPredicate.isEmpty(validatedUserGroups)) {
      throw new InvalidRequestException("At least 1 valid user group is required");
    }
    approvalInstance.setValidatedUserGroups(validatedUserGroups);
    approvalInstance.setValidatedApprovalUserGroups(
        validatedUserGroups.stream().map(ApprovalUserGroupDTO::toApprovalUserGroupDTO).collect(Collectors.toList()));
    HarnessApprovalInstance savedApprovalInstance =
        (HarnessApprovalInstance) approvalInstanceService.save(approvalInstance);
    executorService.submit(() -> approvalNotificationHandler.sendNotification(savedApprovalInstance, ambiance));

    return AsyncExecutableResponse.newBuilder()
        .addCallbackIds(approvalInstance.getId())
        .addAllLogKeys(CollectionUtils.emptyIfNull(StepUtils.generateLogKeys(
            StepUtils.generateLogAbstractions(ambiance), Collections.singletonList(ShellScriptTaskNG.COMMAND_UNIT))))
        .build();
  }

  @Override
  public StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    try {
      HarnessApprovalResponseData responseData =
          (HarnessApprovalResponseData) responseDataMap.values().iterator().next();
      HarnessApprovalInstance instance =
          (HarnessApprovalInstance) approvalInstanceService.get(responseData.getApprovalInstanceId());

      if (ApprovalStatus.APPROVED.equals(instance.getStatus())
          || ApprovalStatus.REJECTED.equals(instance.getStatus())) {
        executorService.submit(() -> approvalNotificationHandler.sendNotification(instance, ambiance));
      }
      return StepResponse.builder()
          .status(instance.getStatus().toFinalExecutionStatus())
          .failureInfo(instance.getFailureInfo())
          .stepOutcome(
              StepResponse.StepOutcome.builder().name("output").outcome(instance.toHarnessApprovalOutcome()).build())
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
