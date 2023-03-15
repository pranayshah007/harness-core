/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plan;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.timeout.SdkTimeoutObtainment;

import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.NonFinal;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class PlanNode {
  // Identifiers
  @NotNull String uuid;
  @NotNull String name;
  @NotNull StepType stepType;
  @NotNull String identifier;
  String group;

  // Input/Outputs
  StepParameters stepParameters;
  String stepInputs;
  @NonFinal @lombok.Setter String executionInputTemplate;
  @Singular List<RefObject> refObjects;

  // todo: deprecate adviserObtainments and slowly move plan creators to fill advisorObtainmentsForExecutionMode
  // Hooks
  @Singular List<AdviserObtainment> adviserObtainments;
  @Singular(value = "advisorObtainmentForExecutionMode")
  Map<ExecutionMode, List<AdviserObtainment>> advisorObtainmentsForExecutionMode;
  @Singular List<FacilitatorObtainment> facilitatorObtainments;
  @Singular List<SdkTimeoutObtainment> timeoutObtainments;

  // Skip
  String skipCondition;
  String whenCondition;

  // stage fqn
  @NonFinal @lombok.Setter String stageFqn;

  // Config
  boolean skipExpressionChain;
  @Builder.Default @NotNull ExpressionMode expressionMode = ExpressionMode.RETURN_NULL_IF_UNRESOLVED;
  @Builder.Default SkipType skipGraphType = SkipType.NOOP;
  @Builder.Default boolean skipUnresolvedExpressionsCheck = true;
}
