/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.barrier.BarrierStepInfo;
import io.harness.plancreator.steps.common.StepElementParameters.StepElementParametersBuilder;
import io.harness.plancreator.steps.common.StepParametersUtils;
import io.harness.plancreator.steps.common.WithStepElementParameters;
import io.harness.plancreator.steps.http.HttpStepInfo;
import io.harness.plancreator.steps.http.PmsAbstractStepNode;
import io.harness.plancreator.steps.resourceconstraint.QueueStepInfo;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.steps.StepUtils;
import io.harness.steps.approval.step.harness.HarnessApprovalStepInfo;
import io.harness.steps.approval.step.jira.JiraApprovalStepInfo;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalStepInfo;
import io.harness.steps.jira.create.JiraCreateStepInfo;
import io.harness.steps.jira.update.JiraUpdateStepInfo;
import io.harness.steps.policy.PolicyStepInfo;
import io.harness.steps.servicenow.create.ServiceNowCreateStepInfo;
import io.harness.steps.servicenow.update.ServiceNowUpdateStepInfo;
import io.harness.steps.shellscript.ShellScriptStepInfo;
import io.harness.yaml.core.StepSpecType;
import io.swagger.annotations.ApiModel;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

@OwnedBy(PIPELINE)
@ApiModel(subTypes = {BarrierStepInfo.class, HttpStepInfo.class, FlagConfigurationStepInfo.class,
              HarnessApprovalStepInfo.class, JiraApprovalStepInfo.class, JiraCreateStepInfo.class,
              JiraUpdateStepInfo.class, ShellScriptStepInfo.class, ServiceNowApprovalStepInfo.class,
              PolicyStepInfo.class, ServiceNowCreateStepInfo.class, ServiceNowUpdateStepInfo.class, QueueStepInfo.class})
public interface PMSStepInfo extends StepSpecType, WithStepElementParameters {
  default StepParameters getStepParameters(
      PmsAbstractStepNode stepElementConfig, OnFailRollbackParameters failRollbackParameters, PlanCreationContext ctx) {
    StepElementParametersBuilder stepParametersBuilder =
        StepParametersUtils.getStepParameters(stepElementConfig, failRollbackParameters);
    StepUtils.appendDelegateSelectorsToSpecParameters(stepElementConfig.getStepSpecType(), ctx);
    stepParametersBuilder.spec(getSpecParameters());
    return stepParametersBuilder.build();
  }
}
