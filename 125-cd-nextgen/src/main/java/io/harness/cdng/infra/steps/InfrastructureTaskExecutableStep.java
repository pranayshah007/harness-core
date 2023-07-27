/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.ExceptionUtils;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.steps.executable.TaskExecutableWithRbac;
import io.harness.supplier.ThrowingSupplier;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class InfrastructureTaskExecutableStep extends AbstractInfrastructureTaskExecutableStep
    implements TaskExecutableWithRbac<Infrastructure, DelegateResponseData> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.INFRASTRUCTURE_V2.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private InfrastructureStepHelper infrastructureStepHelper;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;

  @Override
  public void validateResources(Ambiance ambiance, Infrastructure stepParameters) {
    ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    String principal = executionPrincipalInfo.getPrincipal();
    if (EmptyPredicate.isEmpty(principal)) {
      return;
    }
    Set<EntityDetailProtoDTO> entityDetails =
        entityReferenceExtractorUtils.extractReferredEntities(ambiance, stepParameters);
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails);
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, Infrastructure infrastructureSpec, StepInputPackage inputPackage) {
    final NGLogCallback logCallback = infrastructureStepHelper.getInfrastructureLogCallback(ambiance, true, "Execute");
    return obtainTaskInternal(ambiance, infrastructureSpec, logCallback, null,
        infrastructureStepHelper.getSkipInstances(infrastructureSpec), new HashMap<>())
        .getTaskRequest();
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, Infrastructure stepParameters,
      ThrowingSupplier<DelegateResponseData> responseDataSupplier) throws Exception {
    final long startTime = System.currentTimeMillis() - DEFAULT_START_TIME_INTERVAL;

    final NGLogCallback logCallback = infrastructureStepHelper.getInfrastructureLogCallback(ambiance, "Execute");
    final InfrastructureTaskExecutableStepSweepingOutput infrastructureOutput = fetchInfraStepOutputOrThrow(ambiance);
    final DelegateResponseData response;
    StepResponse stepResponse;
    try {
      response = responseDataSupplier.get();
    } catch (Exception ex) {
      stepResponse = buildFailureStepResponse(startTime, ExceptionUtils.getMessage(ex), logCallback);
      saveInfraExecutionDataToStageInfo(ambiance, stepResponse);
      return stepResponse;
    }
    stepResponse = super.handleTaskResult(ambiance, infrastructureOutput, response, logCallback);
    saveInfraExecutionDataToStageInfo(ambiance, stepResponse);
    return stepResponse;
  }

  @Override
  public Class<Infrastructure> getStepParametersClass() {
    return Infrastructure.class;
  }

  private void saveInfraExecutionDataToStageInfo(Ambiance ambiance, StepResponse stepResponse) {
    infrastructureStepHelper.saveInfraExecutionDataToStageInfo(ambiance, stepResponse);
  }
}
