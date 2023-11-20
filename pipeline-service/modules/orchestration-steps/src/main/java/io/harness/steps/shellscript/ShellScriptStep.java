/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.shellscript;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.shell.ShellScriptTaskResponseNG;
import io.harness.exception.UnsupportedOperationException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.TaskExecutableResponse;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.serializer.KryoSerializer;
import io.harness.shell.ShellExecutionData;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.StepHelper;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.steps.executables.PipelineTaskExecutable;
import io.harness.supplier.ThrowingSupplier;
import io.harness.utils.PmsFeatureFlagHelper;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
@Slf4j
public class ShellScriptStep extends PipelineTaskExecutable<ShellScriptTaskResponseNG> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.SHELL_SCRIPT_STEP_TYPE;

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private ShellScriptHelperService shellScriptHelperService;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    io.harness.steps.shellscript.ShellScriptStepParameters shellScriptStepParameters =
        ShellScriptHelperService.getShellScriptStepParameters(stepParameters);
    TaskParameters taskParameters =
        shellScriptHelperService.buildShellScriptTaskParametersNG(ambiance, shellScriptStepParameters,
            stepParameters.getTimeout() != null ? stepParameters.getTimeout().getValue() : null, null);

    switch (shellScriptStepParameters.getShell()) {
      case Bash:
        return obtainBashTask(ambiance, stepParameters, shellScriptStepParameters, taskParameters);
      case PowerShell:
        return obtainPowerShellTask(ambiance, stepParameters, shellScriptStepParameters, taskParameters);
      default:
        throw new UnsupportedOperationException("Shell type not supported: " + shellScriptStepParameters.getShell());
    }
  }

  private TaskRequest obtainBashTask(Ambiance ambiance, StepBaseParameters stepParameters,
      io.harness.steps.shellscript.ShellScriptStepParameters shellScriptStepParameters, TaskParameters taskParameters) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    final List<String> units = shellScriptStepParameters.getAllCommandUnits();
    units.forEach(logStreamingStepClient::openStream);
    TaskData taskData =
        TaskData.builder()
            .async(true)
            .taskType(TaskType.SHELL_SCRIPT_TASK_NG.name())
            .parameters(new Object[] {taskParameters})
            .timeout(StepUtils.getTimeoutMillis(stepParameters.getTimeout(), StepUtils.DEFAULT_STEP_TIMEOUT))
            .build();
    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
        CollectionUtils.emptyIfNull(StepUtils.generateLogKeys(StepUtils.generateLogAbstractions(ambiance), units)),
        null, null, TaskSelectorYaml.toTaskSelector(shellScriptStepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  private TaskRequest obtainPowerShellTask(Ambiance ambiance, StepBaseParameters stepParameters,
      io.harness.steps.shellscript.ShellScriptStepParameters shellScriptStepParameters, TaskParameters taskParameters) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);

    final List<String> units = shellScriptStepParameters.getAllCommandUnits();
    units.forEach(logStreamingStepClient::openStream);

    TaskData taskData =
        TaskData.builder()
            .async(true)
            .taskType(TaskType.WIN_RM_SHELL_SCRIPT_TASK_NG.name())
            .parameters(new Object[] {taskParameters})
            .timeout(StepUtils.getTimeoutMillis(stepParameters.getTimeout(), StepUtils.DEFAULT_STEP_TIMEOUT))
            .build();
    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer, units,
        TaskType.WIN_RM_SHELL_SCRIPT_TASK_NG.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(shellScriptStepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepBaseParameters stepParameters,
      ThrowingSupplier<ShellScriptTaskResponseNG> responseSupplier) throws Exception {
    return handleTaskResultWithSecurityContext(ambiance, stepParameters, responseSupplier, HarnessYamlVersion.V0);
  }

  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepBaseParameters stepParameters,
      ThrowingSupplier<ShellScriptTaskResponseNG> responseSupplier, String version) throws Exception {
    try {
      StepResponseBuilder stepResponseBuilder = StepResponse.builder();
      ShellScriptTaskResponseNG taskResponse = responseSupplier.get();
      io.harness.steps.shellscript.ShellScriptStepParameters shellScriptStepParameters =
          ShellScriptHelperService.getShellScriptStepParameters(stepParameters);
      List<UnitProgress> unitProgresses = taskResponse.getUnitProgressData() == null
          ? emptyList()
          : taskResponse.getUnitProgressData().getUnitProgresses();
      stepResponseBuilder.unitProgressList(unitProgresses);

      stepResponseBuilder.status(StepUtils.getStepStatus(taskResponse.getStatus()));

      FailureInfo.Builder failureInfoBuilder = FailureInfo.newBuilder();
      if (taskResponse.getErrorMessage() != null) {
        failureInfoBuilder.setErrorMessage(taskResponse.getErrorMessage());
      }
      stepResponseBuilder.failureInfo(failureInfoBuilder.build());

      if (taskResponse.getStatus() == CommandExecutionStatus.SUCCESS) {
        ShellExecutionData commandExecutionData =
            (ShellExecutionData) taskResponse.getExecuteCommandResponse().getCommandExecutionData();
        ShellScriptBaseOutcome shellScriptOutcome = ShellScriptHelperService.prepareShellScriptOutcome(
            commandExecutionData.getSweepingOutputEnvVariables(), shellScriptStepParameters.getOutputVariables(),
            shellScriptStepParameters.getSecretOutputVariables(), version);
        if (shellScriptOutcome != null) {
          stepResponseBuilder.stepOutcome(StepResponse.StepOutcome.builder()
                                              .name(OutputExpressionConstants.OUTPUT)
                                              .outcome(shellScriptOutcome)
                                              .build());
          if (pmsFeatureFlagHelper.isEnabled(
                  AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_SHELL_VARIABLES_EXPORT)) {
            shellScriptHelperService.exportOutputVariablesUsingAlias(
                ambiance, shellScriptStepParameters, shellScriptOutcome);
          }
        }
      }
      return stepResponseBuilder.build();
    } finally {
      closeLogStream(ambiance, stepParameters);
    }
  }

  @Override
  public void handleAbort(Ambiance ambiance, StepBaseParameters stepParameters,
      TaskExecutableResponse executableResponse, boolean userMarked) {
    closeLogStream(ambiance, stepParameters);
  }

  @Override
  public void handleExpire(
      Ambiance ambiance, StepBaseParameters stepParameters, TaskExecutableResponse executableResponse) {
    closeLogStream(ambiance, stepParameters);
  }

  private void closeLogStream(Ambiance ambiance, StepBaseParameters stepParameters) {
    try {
      Thread.sleep(500, 0);
    } catch (InterruptedException e) {
      log.error("Close Log Stream was interrupted", e);
    } finally {
      ILogStreamingStepClient logStreamingStepClient =
          logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
      // Once log-service provide the API to pass list of log-keys as parameter. Then only one call will be enough to
      // close all log-stream. Right now we are doing forEach.
      io.harness.steps.shellscript.ShellScriptStepParameters shellScriptStepParameters =
          ShellScriptHelperService.getShellScriptStepParameters(stepParameters);
      shellScriptStepParameters.getAllCommandUnits().forEach(logStreamingStepClient::closeStream);
    }
  }
}
