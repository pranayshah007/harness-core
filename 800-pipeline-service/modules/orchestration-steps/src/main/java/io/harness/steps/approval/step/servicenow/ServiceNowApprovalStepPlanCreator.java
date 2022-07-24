/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.servicenow;

import io.harness.plancreator.steps.internal.PMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.collect.Sets;
import java.util.Set;

public class ServiceNowApprovalStepPlanCreator extends PMSStepPlanCreatorV2<ServiceNowApprovalStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.SERVICENOW_APPROVAL);
  }

  @Override
  public Class<ServiceNowApprovalStepNode> getFieldClass() {
    return ServiceNowApprovalStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, ServiceNowApprovalStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }
}
