/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plan;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.persistence.UuidAccess;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepparameters.PmsStepParameters;

import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public interface Node extends UuidAccess {
  NodeType getNodeType();

  String getIdentifier();

  String getStageFqn();

  String getName();

  StepType getStepType();

  String getGroup();

  default String getExecutionInputTemplate() {
    return "";
  }

  default boolean isSkipExpressionChain() {
    return false;
  }

  @Deprecated String getServiceName();

  PmsStepParameters getStepParameters();

  default String getWhenCondition() {
    return null;
  }

  default String getSkipCondition() {
    return null;
  }

  default SkipType getSkipGraphType() {
    return SkipType.NOOP;
  }

  default StepCategory getStepCategory() {
    return getStepType().getStepCategory();
  }

  boolean isSkipUnresolvedExpressionsCheck();

  default List<AdviserObtainment> getAdviserObtainments() {
    return null;
  }

  default Map<ExecutionMode, List<AdviserObtainment>> getAdvisorObtainmentsForExecutionMode() {
    return null;
  }
}
