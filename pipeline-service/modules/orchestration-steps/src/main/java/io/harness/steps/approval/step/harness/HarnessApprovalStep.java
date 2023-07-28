/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.harness;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.shell.ShellScriptTaskNG.COMMAND_UNIT;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.FeatureName;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.engine.executions.step.StepExecutionEntityService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.step.approval.harness.HarnessApprovalStepExecutionDetails;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.execution.AsyncTimeoutResponseData;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.approval.ApprovalNotificationHandler;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalUserGroupDTO;
import io.harness.steps.approval.step.harness.beans.ApproverInput;
import io.harness.steps.approval.step.harness.beans.AutoApprovalParams;
import io.harness.steps.approval.step.harness.beans.EmbeddedUserDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalAction;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityRequestDTO;
import io.harness.steps.approval.step.harness.beans.ScheduledDeadline;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;
import io.harness.steps.approval.step.harness.outcomes.HarnessApprovalStepOutcome;
import io.harness.steps.executables.PipelineAsyncExecutable;
import io.harness.tasks.ResponseData;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.TimeStampUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(CDC)
@Slf4j
public class HarnessApprovalStep extends PipelineAsyncExecutable {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.HARNESS_APPROVAL_STEP_TYPE;
  public static final String HARNESS_APPROVAL_STEP_OUTCOME = "Harness_approval_step_outcome";
  public static final String TIMEOUT_DATA = "timeoutData";
  public static final String HARNESS = "Harness";
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private ApprovalInstanceService approvalInstanceService;
  @Inject private ApprovalNotificationHandler approvalNotificationHandler;
  @Inject @Named("PipelineExecutorService") ExecutorService executorService;
  @Inject @Named("DashboardExecutorService") ExecutorService dashboardExecutorService;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private StepExecutionEntityService stepExecutionEntityService;

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

    HarnessApprovalSpecParameters specParameters = (HarnessApprovalSpecParameters) stepParameters.getSpec();

    sweepingOutputService.consume(ambiance, HARNESS_APPROVAL_STEP_OUTCOME,
        HarnessApprovalStepOutcome.builder().approvalInstanceId(approvalInstance.getId()).build(), "");

    AsyncExecutableResponse.Builder asyncExecutableResponseBuilder =
        AsyncExecutableResponse.newBuilder()
            .addCallbackIds(approvalInstance.getId())
            .addAllLogKeys(
                CollectionUtils.emptyIfNull(StepUtils.generateLogKeys(StepUtils.generateLogAbstractions(ambiance),
                    Collections.singletonList(ShellScriptTaskNG.COMMAND_UNIT))));

    if (pmsFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_AUTO_APPROVAL)
        && specParameters.getAutoApproval() != null) {
      asyncExecutableResponseBuilder.setTimeout(getTimeoutForAutoApproval(specParameters.getAutoApproval()));
    }

    return asyncExecutableResponseBuilder.build();
  }

  private int getTimeoutForAutoApproval(AutoApprovalParams autoApprovalParams) {
    ScheduledDeadline scheduledDeadline = autoApprovalParams.getScheduledDeadline();
    int autoApprovalDuration = Math.toIntExact(TimeStampUtils.getTotalDurationWRTCurrentTimeFromTimeStamp(
        scheduledDeadline.getTime(), scheduledDeadline.getTimeZone()));
    if (autoApprovalDuration <= 0) {
      throw new InvalidRequestException("Auto approval deadline should be greater than current time");
    }
    return autoApprovalDuration;
  }

  @Override
  public StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    try {
      if (pmsFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_AUTO_APPROVAL)
          && responseDataMap.get(TIMEOUT_DATA) != null
          && responseDataMap.get(TIMEOUT_DATA) instanceof AsyncTimeoutResponseData) {
        // Auto approve the pipeline in this case, as the step is over schedule time provided for approval

        final OptionalSweepingOutput outputOptional = sweepingOutputService.resolveOptional(
            ambiance, RefObjectUtils.getSweepingOutputRefObject(HARNESS_APPROVAL_STEP_OUTCOME));
        if (!outputOptional.isFound()) {
          log.error(HARNESS_APPROVAL_STEP_OUTCOME + " sweeping output not found. unable to perform auto approval");
          FailureInfo failureInfo = FailureInfo.newBuilder().setErrorMessage("Step timeout occurred").build();
          dashboardExecutorService.submit(
              ()
                  -> stepExecutionEntityService.updateStepExecutionEntity(
                      ambiance, failureInfo, null, stepParameters.getName(), Status.APPROVAL_WAITING));
          return StepResponse.builder().status(Status.FAILED).failureInfo(failureInfo).build();
        }
        NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, COMMAND_UNIT, false);
        logCallback.saveExecutionLog(
            "Scheduled deadline for auto approval has reached. Marking the step as approved...");

        HarnessApprovalStepOutcome harnessApprovalStepOutcome = (HarnessApprovalStepOutcome) outputOptional.getOutput();
        String approvalInstanceId = harnessApprovalStepOutcome.getApprovalInstanceId();

        HarnessApprovalInstance instance = handleAutoApprovalForStep(approvalInstanceId, stepParameters);
        executorService.submit(() -> approvalNotificationHandler.sendNotification(instance, ambiance));

        logCallback.saveExecutionLog("Step auto approved.");
        HarnessApprovalOutcome outcome = instance.toHarnessApprovalOutcome();
        dashboardExecutorService.submit(
            ()
                -> stepExecutionEntityService.updateStepExecutionEntity(ambiance, null,
                    createHarnessApprovalStepExecutionDetailsFromHarnessApprovalOutcome(outcome),
                    stepParameters.getName(), Status.APPROVAL_WAITING));
        return StepResponse.builder()
            .status(Status.SUCCEEDED)
            .stepOutcome(StepResponse.StepOutcome.builder().name("output").outcome(outcome).build())
            .build();
      }
      HarnessApprovalResponseData responseData =
          (HarnessApprovalResponseData) responseDataMap.values().iterator().next();
      HarnessApprovalInstance instance =
          (HarnessApprovalInstance) approvalInstanceService.get(responseData.getApprovalInstanceId());

      if (ApprovalStatus.APPROVED.equals(instance.getStatus())
          || ApprovalStatus.REJECTED.equals(instance.getStatus())) {
        executorService.submit(() -> approvalNotificationHandler.sendNotification(instance, ambiance));
      }
      HarnessApprovalOutcome outcome = instance.toHarnessApprovalOutcome();
      dashboardExecutorService.submit(
          ()
              -> stepExecutionEntityService.updateStepExecutionEntity(ambiance, instance.getFailureInfo(),
                  createHarnessApprovalStepExecutionDetailsFromHarnessApprovalOutcome(outcome),
                  stepParameters.getName(), Status.APPROVAL_WAITING));
      return StepResponse.builder()
          .status(instance.getStatus().toFinalExecutionStatus())
          .failureInfo(instance.getFailureInfo())
          .stepOutcome(StepResponse.StepOutcome.builder().name("output").outcome(outcome).build())
          .build();
    } finally {
      closeLogStream(ambiance);
    }
  }

  private HarnessApprovalStepExecutionDetails createHarnessApprovalStepExecutionDetailsFromHarnessApprovalOutcome(
      HarnessApprovalOutcome outcome) {
    List<HarnessApprovalStepExecutionDetails.HarnessApprovalExecutionActivity> approvalActivities = new ArrayList<>();
    if (outcome != null && outcome.getApprovalActivities() != null) {
      for (HarnessApprovalActivityDTO harnessApprovalActivityDTO : outcome.getApprovalActivities()) {
        String action = harnessApprovalActivityDTO.getAction().toString();
        Map<String, String> approverInputs = Collections.emptyMap();
        if (EmptyPredicate.isNotEmpty(harnessApprovalActivityDTO.getApproverInputs())) {
          approverInputs = harnessApprovalActivityDTO.getApproverInputs().stream().collect(
              Collectors.toMap(ApproverInput::getName, ApproverInput::getValue));
        }
        approvalActivities.add(HarnessApprovalStepExecutionDetails.HarnessApprovalExecutionActivity.builder()
                                   .user(EmbeddedUserDTO.toEmbeddedUser(harnessApprovalActivityDTO.getUser()))
                                   .approvalAction(action)
                                   .approverInputs(approverInputs)
                                   .comments(harnessApprovalActivityDTO.getComments())
                                   .approvedAt(harnessApprovalActivityDTO.getApprovedAt())
                                   .build());
      }
      return HarnessApprovalStepExecutionDetails.builder().approvalActivities(approvalActivities).build();
    }
    return null;
  }

  private HarnessApprovalInstance handleAutoApprovalForStep(
      String approvalInstanceId, StepElementParameters stepParameters) {
    HarnessApprovalSpecParameters specParameters = (HarnessApprovalSpecParameters) stepParameters.getSpec();

    if (isNull(specParameters.getAutoApproval())) {
      throw new InvalidRequestException("Step timed out");
    }

    String comment = "";
    if (ParameterField.isNotNull(specParameters.getAutoApproval().getComments())) {
      comment = specParameters.getAutoApproval().getComments().getValue();
    }

    HarnessApprovalActivityRequestDTO harnessApprovalActivityRequestDTO = HarnessApprovalActivityRequestDTO.builder()
                                                                              .action(HarnessApprovalAction.APPROVE)
                                                                              .comments(comment)
                                                                              .autoApprove(true)
                                                                              .build();

    EmbeddedUser user = EmbeddedUser.builder().name(HARNESS).email(HARNESS).build();
    return approvalInstanceService.addHarnessApprovalActivityV2(
        approvalInstanceId, user, harnessApprovalActivityRequestDTO, false);
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
