/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.executions.steps.ExecutionNodeType;
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

import com.google.inject.Inject;
import java.util.Optional;
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
    publishInfraOutput(ambiance, infrastructureSpec);
    Optional<TaskRequest> taskRequest = super.obtainTaskInternal(ambiance, infrastructureSpec, inputPackage);
    return taskRequest.get();
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, Infrastructure stepParameters,
      ThrowingSupplier<DelegateResponseData> responseDataSupplier) throws Exception {
    InfrastructureOutcome infrastructureOutput = fetchInfraOutputOrThrow(ambiance);
    return super.handleTaskResultWithSecurityContext(ambiance, infrastructureOutput, responseDataSupplier);
  }

  @Override
  public Class<Infrastructure> getStepParametersClass() {
    return Infrastructure.class;
  }
}
