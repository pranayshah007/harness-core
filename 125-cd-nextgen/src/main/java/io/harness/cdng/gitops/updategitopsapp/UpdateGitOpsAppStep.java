/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops.updategitopsapp;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.UPDATE_GITOPS_APP_OUTCOME;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.gitops.models.Application;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.steps.StepUtils;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITOPS})
@Slf4j
@OwnedBy(GITOPS)
public class UpdateGitOpsAppStep implements AsyncExecutableWithRbac<StepElementParameters> {
  private static final String LOG_SUFFIX = "Execute";

  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.UPDATE_GITOPS_APP.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private Injector injector;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // check if rbac is there for GitOps apps
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    log.info("Starting execution for Update GitOps App step [{}]", stepParameters);

    String taskId = getTaskId(stepParameters);

    UpdateGitOpsAppRunnable runnable = new UpdateGitOpsAppRunnable(taskId, ambiance, stepParameters);
    injector.injectMembers(runnable);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.execute(runnable);

    return AsyncExecutableResponse.newBuilder()
        .addCallbackIds(taskId)
        .addAllLogKeys(StepUtils.generateLogKeys(ambiance, List.of(LOG_SUFFIX)))
        .build();
  }

  private String getTaskId(StepElementParameters stepParameters) {
    return "UpdateGitOpsApp" + stepParameters.getUuid();
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    log.info("Handling Update GitOps App step response");
    ResponseData responseData = responseDataMap.get(getTaskId(stepParameters));
    if (responseData instanceof ErrorNotifyResponseData) {
      throw new WingsException(((ErrorNotifyResponseData) responseData).getErrorMessage());
    }
    return handleResponse(ambiance, (UpdateGitOpsAppResponse) responseData).build();
  }

  private StepResponseBuilder handleResponse(Ambiance ambiance, UpdateGitOpsAppResponse updateGitOpsAppResponse) {
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();

    Application application = updateGitOpsAppResponse.getUpdatedApplication();
    if (application == null) {
      FailureData failureMessage = FailureData.newBuilder()
                                       .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                       .setLevel(Level.ERROR.name())
                                       .setCode(GENERAL_ERROR.name())
                                       .setMessage("Failed to Update GitOps Application")
                                       .build();
      return stepResponseBuilder.status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder().addFailureData(failureMessage).build());
    }

    UpdateGitOpsAppOutcome outcome = UpdateGitOpsAppOutcome.builder().application(application).build();
    executionSweepingOutputResolver.consume(
        ambiance, UPDATE_GITOPS_APP_OUTCOME, outcome, StepOutcomeGroup.STAGE.name());

    return stepResponseBuilder.status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder().name(UPDATE_GITOPS_APP_OUTCOME).outcome(outcome).build());
  }
}
