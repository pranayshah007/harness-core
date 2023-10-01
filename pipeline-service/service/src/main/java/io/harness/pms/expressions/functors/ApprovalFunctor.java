/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.EngineFunctorException;
import io.harness.expression.LateBindingValue;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;

import java.util.Optional;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(HarnessTeam.CDP)
public class ApprovalFunctor implements LateBindingValue {
  private final String planExecutionId;
  private final ApprovalInstanceService approvalInstanceService;

  public ApprovalFunctor(String planExecutionId, ApprovalInstanceService approvalInstanceService) {
    this.planExecutionId = planExecutionId;
    this.approvalInstanceService = approvalInstanceService;
  }

  @Override
  public Object bind() {
    Optional<ApprovalInstance> latestApprovalInstance =
        approvalInstanceService.findLatestApprovalInstanceByPlanExecutionIdAndType(
            planExecutionId, ApprovalType.HARNESS_APPROVAL);
    if (latestApprovalInstance.isEmpty()) {
      return null;
    }

    ApprovalInstance approvalInstance = latestApprovalInstance.get();
    if (!(approvalInstance instanceof HarnessApprovalInstance)) {
      throw new EngineFunctorException(String.format(
          "Found invalid approval instance for approval expression, type: %s", approvalInstance.getClass().getName()));
    }

    return ((HarnessApprovalInstance) approvalInstance).toHarnessApprovalOutcome();
  }
}
