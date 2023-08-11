/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.exception.EngineFunctorException;
import io.harness.expression.LateBindingValue;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.resolver.outcome.mapper.PmsOutcomeMapper;
import io.harness.steps.approval.step.harness.HarnessApprovalOutcome;
import io.harness.steps.approval.step.harness.HarnessApprovalStep;

import java.util.List;

public class ApprovalFunctor implements LateBindingValue {
  private final Ambiance ambiance;
  private final PmsOutcomeService pmsOutcomeService;
  ;

  public ApprovalFunctor(Ambiance ambiance, PmsOutcomeService pmsOutcomeService) {
    this.ambiance = ambiance;
    this.pmsOutcomeService = pmsOutcomeService;
  }

  @Override
  public Object bind() {
    List<String> outcomes = pmsOutcomeService.fetchOutcomesByStepTypeAndCategory(ambiance.getPlanExecutionId(),
        HarnessApprovalStep.STEP_TYPE.getType(), HarnessApprovalStep.STEP_TYPE.getStepCategory().name());
    if (EmptyPredicate.isEmpty(outcomes)) {
      return null;
    }

    Outcome outcome = PmsOutcomeMapper.convertJsonToOutcome(outcomes.get(0));
    if (!(outcome instanceof HarnessApprovalOutcome)) {
      throw new EngineFunctorException(String.format("Found invalid outcome for approval expression, type: %s",
          outcome != null ? outcome.getClass().getName() : null));
    }

    return outcome;
  }
}
