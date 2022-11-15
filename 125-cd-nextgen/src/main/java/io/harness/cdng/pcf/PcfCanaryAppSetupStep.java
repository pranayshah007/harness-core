/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pcf;

import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.CustomDeploymentInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.CustomDeploymentServerInstanceInfo;
import io.harness.delegate.task.customdeployment.FetchInstanceScriptTaskNGResponse;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class PcfCanaryAppSetupStep extends TaskChainExecutableWithRollbackAndRbac {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.PCF_CANARY_APP_SETUP.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  public static final String OUTPUT_PATH_KEY = "INSTANCE_OUTPUT_PATH";
  public static final String WORKING_DIRECTORY = "/tmp";
  public static final String INSTANCE_NAME = "instancename";
  @Inject private KryoSerializer kryoSerializer;
  @Inject private StepHelper stepHelper;

  @Inject private PcfStepHelper pcfStepHelper;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private InstanceInfoService instanceInfoService;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private CDStepHelper cdStepHelper;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.PCF_NG)) {
      throw new AccessDeniedException(
          "PCF_NG FF is not enabled for this account. Please contact harness customer care.",
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    return null;
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    try {
      FetchInstanceScriptTaskNGResponse response;
      try {
        response = (FetchInstanceScriptTaskNGResponse) responseDataSupplier.get();
      } catch (Exception ex) {
        log.error("Error while processing Fetch Instance script task response: {}", ExceptionUtils.getMessage(ex), ex);
        throw ex;
      }
      if (response.getCommandExecutionStatus() != SUCCESS) {
        return StepResponse.builder()
            .unitProgressList(response.getUnitProgressData().getUnitProgresses())
            .status(Status.FAILED)
            .failureInfo(FailureInfo.newBuilder().setErrorMessage(response.getErrorMessage()).build())
            .build();
      }
      StepResponseBuilder builder = StepResponse.builder()
                                        .unitProgressList(response.getUnitProgressData().getUnitProgresses())
                                        .status(Status.SUCCEEDED);
      List<CustomDeploymentServerInstanceInfo> instanceElements = new ArrayList<>();
      CustomDeploymentInfrastructureOutcome infrastructureOutcome =
          (CustomDeploymentInfrastructureOutcome) cdStepHelper.getInfrastructureOutcome(ambiance);
      StepResponse.StepOutcome stepOutcome = instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance,
          instanceElements.stream().map(element -> (ServerInstanceInfo) element).collect(Collectors.toList()));
      return builder.stepOutcome(stepOutcome).build();
    } finally {
      closeLogStream(ambiance);
    }
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    return pcfStepHelper.startChainLink(ambiance, stepParameters);
    //    PcfCanaryAppSetupStepParameters stepSpec = (PcfCanaryAppSetupStepParameters) stepParameters.getSpec();
    //    ILogStreamingStepClient logStreamingStepClient =
    //    logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    //    logStreamingStepClient.openStream(ShellScriptTaskNG.COMMAND_UNIT);
    //
    //    CustomDeploymentInfrastructureOutcome infrastructureOutcome =
    //            (CustomDeploymentInfrastructureOutcome) cdStepHelper.getInfrastructureOutcome(ambiance);
    //
    //    FetchInstanceScriptTaskNGRequest taskParameters =
    //            FetchInstanceScriptTaskNGRequest.builder()
    //                    .accountId(AmbianceUtils.getAccountId(ambiance))
    //                    .executionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
    //                    .variables(new HashMap<>())
    //                    .outputPathKey(OUTPUT_PATH_KEY)
    //                    .timeoutInMillis(CDStepHelper.getTimeoutInMillis(stepParameters))
    //                    .build();
    //
    //    final TaskData taskData = TaskData.builder()
    //            .async(true)
    //            .timeout(CDStepHelper.getTimeoutInMillis(stepParameters))
    //            .taskType(TaskType.FETCH_INSTANCE_SCRIPT_TASK_NG.name())
    //            .parameters(new Object[] {taskParameters})
    //            .build();
    //    return StepUtils.prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
    //            Collections.singletonList(FetchInstanceScriptTaskNG.COMMAND_UNIT),
    //            FETCH_INSTANCE_SCRIPT_TASK_NG.getDisplayName(),
    //            TaskSelectorYaml.toTaskSelector(stepSpec.getDelegateSelectors()),
    //            stepHelper.getEnvironmentType(ambiance));
  }

  private void closeLogStream(Ambiance ambiance) {
    try {
      Thread.sleep(500, 0);
    } catch (InterruptedException e) {
      log.error("Close Log Stream was interrupted", e);
    } finally {
      ILogStreamingStepClient logStreamingStepClient =
          logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
      logStreamingStepClient.closeAllOpenStreamsWithPrefix(StepUtils.generateLogKeys(ambiance, emptyList()).get(0));
    }
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
