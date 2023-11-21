/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.servicenow;

import static io.harness.eraro.ErrorCode.APPROVAL_STEP_NG_ERROR;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.engine.executions.step.StepExecutionEntityService;
import io.harness.eraro.Level;
import io.harness.exception.ApprovalStepNGException;
import io.harness.execution.step.approval.servicenow.ServiceNowApprovalStepExecutionDetails;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.custom.IrregularApprovalInstanceHandler;
import io.harness.steps.approval.step.servicenow.beans.ServiceNowApprovalResponseData;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance;
import io.harness.steps.executables.PipelineAsyncExecutable;
import io.harness.tasks.ResponseData;
import io.harness.telemetry.helpers.ApprovalInstrumentationHelper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@Slf4j
public class ServiceNowApprovalStep extends PipelineAsyncExecutable {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.SERVICE_NOW_APPROVAL_STEP_TYPE;

  @Inject private ApprovalInstanceService approvalInstanceService;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private StepExecutionEntityService stepExecutionEntityService;
  @Inject private ServiceNowApprovalHelperService serviceNowApprovalHelperService;
  @Inject private IrregularApprovalInstanceHandler irregularApprovalInstanceHandler;
  @Inject @Named("DashboardExecutorService") ExecutorService dashboardExecutorService;
  @Inject ApprovalInstrumentationHelper instrumentationHelper;

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.openStream(ShellScriptTaskNG.COMMAND_UNIT);
    ServiceNowApprovalInstance approvalInstance =
        ServiceNowApprovalInstance.fromStepParameters(ambiance, stepParameters);
    instrumentationHelper.sendApprovalEvent(approvalInstance);
    serviceNowApprovalHelperService.getServiceNowConnector(AmbianceUtils.getAccountId(ambiance),
        AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance),
        approvalInstance.getConnectorRef());
    approvalInstance = (ServiceNowApprovalInstance) approvalInstanceService.save(approvalInstance);
    if (ParameterField.isNotNull(approvalInstance.getRetryInterval())) {
      irregularApprovalInstanceHandler.wakeup();
    }
    return AsyncExecutableResponse.newBuilder()
        .addCallbackIds(approvalInstance.getId())
        .addAllLogKeys(CollectionUtils.emptyIfNull(StepUtils.generateLogKeys(
            StepUtils.generateLogAbstractions(ambiance), Collections.singletonList(ShellScriptTaskNG.COMMAND_UNIT))))
        .build();
  }

  @Override
  public StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepBaseParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    try {
      ServiceNowApprovalResponseData approvalResponseData =
          (ServiceNowApprovalResponseData) responseDataMap.values().iterator().next();
      ServiceNowApprovalInstance instance =
          (ServiceNowApprovalInstance) approvalInstanceService.get(approvalResponseData.getInstanceId());
      if (instance.getStatus() == ApprovalStatus.FAILED) {
        String errorMsg =
            instance.getErrorMessage() != null ? instance.getErrorMessage() : "Unknown error polling serviceNow ticket";
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
      dashboardExecutorService.submit(
          ()
              -> stepExecutionEntityService.updateStepExecutionEntity(ambiance, instance.getFailureInfo(),
                  createServiceNowApprovalStepExecutionDetailsFromServiceNowApprovalInstance(instance),
                  stepParameters.getName(), instance.getStatus().toFinalExecutionStatus()));
      return StepResponse.builder()
          .status(instance.getStatus().toFinalExecutionStatus())
          .failureInfo(instance.getFailureInfo())
          .stepOutcome(
              StepResponse.StepOutcome.builder().name("output").outcome(instance.toServiceNowApprovalOutcome()).build())
          .build();
    } finally {
      closeLogStream(ambiance);
    }
  }

  private ServiceNowApprovalStepExecutionDetails
  createServiceNowApprovalStepExecutionDetailsFromServiceNowApprovalInstance(
      ServiceNowApprovalInstance serviceNowApprovalInstance) {
    if (serviceNowApprovalInstance != null) {
      return ServiceNowApprovalStepExecutionDetails.builder()
          .ticketType(serviceNowApprovalInstance.getTicketType())
          .ticketNumber(serviceNowApprovalInstance.getTicketNumber())
          .build();
    }
    return null;
  }

  @Override
  public void handleAbort(Ambiance ambiance, StepBaseParameters stepParameters,
      AsyncExecutableResponse executableResponse, boolean userMarked) {
    approvalInstanceService.abortByNodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    closeLogStream(ambiance);
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  private void closeLogStream(Ambiance ambiance) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.closeStream(ShellScriptTaskNG.COMMAND_UNIT);
  }
}
