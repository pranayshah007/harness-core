/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacm.plan.creator.step;

import io.harness.beans.steps.nodes.iacm.IACMApprovalStepNode;
import io.harness.beans.steps.stepinfo.IACMStepInfoType;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;

import com.google.common.collect.Sets;
import java.util.Set;

public class IACMApprovalStepPlanCreator extends CIPMSStepPlanCreatorV2<IACMApprovalStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(IACMStepInfoType.IACM_APPROVAL.getDisplayName());
  }

  @Override
  public Class<IACMApprovalStepNode> getFieldClass() {
    return IACMApprovalStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, IACMApprovalStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }
}
