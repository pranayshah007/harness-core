/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops.syncstep;
import static io.harness.cdng.gitops.constants.GitopsConstants.GITOPS_SWEEPING_OUTPUT;
import static io.harness.cdng.gitops.constants.GitopsConstants.GITOPS_SYNC_SWEEPING_OUTPUT;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITOPS})
@Slf4j
public class SyncStep implements AsyncExecutableWithRbac<StepElementParameters> {
  private static final String LOG_SUFFIX = "Execute";

  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.GITOPS_SYNC.getYamlType())
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
    log.info("Starting execution for Sync step [{}]", stepParameters);

    String taskId = getTaskId(stepParameters);

    SyncRunnable runnable = new SyncRunnable(taskId, ambiance, stepParameters);
    injector.injectMembers(runnable);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.execute(runnable);

    return AsyncExecutableResponse.newBuilder()
        .addCallbackIds(taskId)
        .addAllLogKeys(StepUtils.generateLogKeys(ambiance, List.of(LOG_SUFFIX)))
        .build();
  }

  private String getTaskId(StepElementParameters stepParameters) {
    return "GitOpsSync" + stepParameters.getUuid();
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepParameters, AsyncExecutableResponse executableResponse) {}

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    log.info("Handling GitOps Sync step response");
    ResponseData responseData = responseDataMap.get(getTaskId(stepParameters));
    if (responseData instanceof ErrorNotifyResponseData) {
      throw new WingsException(((ErrorNotifyResponseData) responseData).getErrorMessage());
    }
    return handleResponse(ambiance, (SyncResponse) responseData).build();
  }

  private StepResponseBuilder handleResponse(Ambiance ambiance, SyncResponse syncResponse) {
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();

    Set<Application> applicationsFailedToSync = syncResponse.getApplicationsFailedToSync();
    Set<Application> syncStillRunningForApplications = syncResponse.getSyncStillRunningForApplications();
    Set<Application> applicationsSucceededOnArgoSync = syncResponse.getApplicationsSucceededOnArgoSync();
    if (isNotEmpty(applicationsFailedToSync) || isNotEmpty(syncStillRunningForApplications)) {
      FailureData failureMessage =
          FailureData.newBuilder()
              .addFailureTypes(FailureType.APPLICATION_FAILURE)
              .setLevel(Level.ERROR.name())
              .setCode(GENERAL_ERROR.name())
              .setMessage(format(
                  "Sync is successful for application(s) %s, failed for application(s) %s and is still running for application(s) %s",
                  applicationsSucceededOnArgoSync, applicationsFailedToSync, syncStillRunningForApplications))
              .build();
      return stepResponseBuilder.status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder().addFailureData(failureMessage).build());
    }

    SyncStepOutcome outcome = SyncStepOutcome.builder().applications(applicationsSucceededOnArgoSync).build();
    executionSweepingOutputResolver.consume(
        ambiance, GITOPS_SYNC_SWEEPING_OUTPUT, outcome, StepOutcomeGroup.STAGE.name());

    return stepResponseBuilder.status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder().name(GITOPS_SWEEPING_OUTPUT).outcome(outcome).build());
  }
}
