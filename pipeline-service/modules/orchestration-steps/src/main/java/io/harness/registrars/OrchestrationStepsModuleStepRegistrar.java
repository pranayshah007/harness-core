/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.registrars;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.pms.execution.strategy.identity.IdentityStep;
import io.harness.engine.pms.execution.strategy.identity.IdentityStrategyInternalStep;
import io.harness.engine.pms.execution.strategy.identity.IdentityStrategyStep;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.registrar.NGCommonUtilStepsRegistrar;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.execution.CdSscaOrchestrationStep;
import io.harness.steps.StagesStep;
import io.harness.steps.approval.stage.ApprovalStageStep;
import io.harness.steps.approval.step.custom.CustomApprovalStep;
import io.harness.steps.approval.step.harness.HarnessApprovalStep;
import io.harness.steps.approval.step.jira.JiraApprovalStep;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalStep;
import io.harness.steps.barriers.BarrierStep;
import io.harness.steps.cf.FeatureFlagStageStep;
import io.harness.steps.cf.FlagConfigurationStep;
import io.harness.steps.common.pipeline.PipelineSetupStep;
import io.harness.steps.container.ContainerStep;
import io.harness.steps.container.InitContainerStep;
import io.harness.steps.container.execution.RunContainerStep;
import io.harness.steps.customstage.CustomStageStep;
import io.harness.steps.email.EmailStep;
import io.harness.steps.group.GroupStepV1;
import io.harness.steps.http.HttpStep;
import io.harness.steps.jira.create.JiraCreateStep;
import io.harness.steps.jira.update.JiraUpdateStep;
import io.harness.steps.policy.step.PolicyStep;
import io.harness.steps.resourcerestraint.QueueStep;
import io.harness.steps.resourcerestraint.ResourceRestraintStep;
import io.harness.steps.servicenow.create.ServiceNowCreateStep;
import io.harness.steps.servicenow.importset.ServiceNowImportSetStep;
import io.harness.steps.servicenow.update.ServiceNowUpdateStep;
import io.harness.steps.shellscript.ShellScriptStep;
import io.harness.steps.wait.WaitStep;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class OrchestrationStepsModuleStepRegistrar {
  public Map<StepType, Class<? extends Step>> getEngineSteps() {
    Map<StepType, Class<? extends Step>> engineSteps = new HashMap<>();

    engineSteps.put(BarrierStep.STEP_TYPE, BarrierStep.class);
    engineSteps.put(ResourceRestraintStep.STEP_TYPE, ResourceRestraintStep.class);
    engineSteps.put(QueueStep.STEP_TYPE, QueueStep.class);
    engineSteps.put(PipelineSetupStep.STEP_TYPE, PipelineSetupStep.class);

    engineSteps.put(ApprovalStageStep.STEP_TYPE, ApprovalStageStep.class);
    engineSteps.put(HarnessApprovalStep.STEP_TYPE, HarnessApprovalStep.class);
    engineSteps.put(CustomApprovalStep.STEP_TYPE, CustomApprovalStep.class);
    engineSteps.put(JiraApprovalStep.STEP_TYPE, JiraApprovalStep.class);
    engineSteps.put(JiraCreateStep.STEP_TYPE, JiraCreateStep.class);
    engineSteps.put(JiraUpdateStep.STEP_TYPE, JiraUpdateStep.class);

    engineSteps.put(HttpStep.STEP_TYPE, HttpStep.class);
    engineSteps.put(EmailStep.STEP_TYPE, EmailStep.class);
    engineSteps.put(ShellScriptStep.STEP_TYPE, ShellScriptStep.class);
    engineSteps.put(ServiceNowApprovalStep.STEP_TYPE, ServiceNowApprovalStep.class);
    engineSteps.put(ServiceNowCreateStep.STEP_TYPE, ServiceNowCreateStep.class);
    engineSteps.put(ServiceNowUpdateStep.STEP_TYPE, ServiceNowUpdateStep.class);
    engineSteps.put(ServiceNowImportSetStep.STEP_TYPE, ServiceNowImportSetStep.class);
    engineSteps.put(StagesStep.STEP_TYPE, StagesStep.class);
    engineSteps.put(CustomStageStep.STEP_TYPE, CustomStageStep.class);

    // Feature Flag
    engineSteps.put(FlagConfigurationStep.STEP_TYPE, FlagConfigurationStep.class);
    engineSteps.put(FeatureFlagStageStep.STEP_TYPE, FeatureFlagStageStep.class);

    engineSteps.put(PolicyStep.STEP_TYPE, PolicyStep.class);
    // IdentityStep
    engineSteps.put(IdentityStep.STEP_TYPE, IdentityStep.class);
    engineSteps.put(IdentityStrategyStep.STEP_TYPE, IdentityStrategyStep.class);
    engineSteps.put(IdentityStrategyInternalStep.STEP_TYPE, IdentityStrategyInternalStep.class);

    engineSteps.putAll(NGCommonUtilStepsRegistrar.getEngineSteps());
    engineSteps.put(WaitStep.STEP_TYPE, WaitStep.class);
    engineSteps.put(GroupStepV1.STEP_TYPE, GroupStepV1.class);
    engineSteps.put(ContainerStep.STEP_TYPE, ContainerStep.class);
    engineSteps.put(InitContainerStep.STEP_TYPE, InitContainerStep.class);
    engineSteps.put(RunContainerStep.STEP_TYPE, RunContainerStep.class);
    engineSteps.put(SscaConstants.CD_SSCA_ORCHESTRATION_STEP_TYPE, CdSscaOrchestrationStep.class);

    return engineSteps;
  }
}
