/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Modifies saved integration stage execution plan by prepending init step for setting up build stage infra e.g. pod or
 * VM.
 */

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CI)
public class CIIntegrationStageModifier implements StageExecutionModifier {
  @Inject private CIStepGroupUtils ciStepGroupUtils;

  public ExecutionElementConfig modifyExecutionPlan(ExecutionElementConfig execution,
      IntegrationStageNode stageElementConfig, PlanCreationContext context, CodeBase ciCodeBase,
      Infrastructure infrastructure, ExecutionSource executionSource) {
    log.info("Modifying execution plan to prepend initialize step for integration stage {}",
        stageElementConfig.getIdentifier());

    PlanCreationContextValue planCreationContextValue = context.getGlobalContext().get("metadata");

    CIExecutionArgs ciExecutionArgs = CIExecutionArgs.builder().executionSource(executionSource).build();

    log.info("Build execution args for integration stage  {}", stageElementConfig.getIdentifier());
    return ExecutionElementConfig.builder()
        .uuid(execution.getUuid())
        .steps(ciStepGroupUtils.createExecutionWrapperWithInitializeStep(stageElementConfig, ciExecutionArgs,
            ciCodeBase, infrastructure, planCreationContextValue.getAccountIdentifier()))
        .build();
  }
}
