/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.creation;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.doNothing;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cf.pipeline.CfExecutionPMSPlanCreator;
import io.harness.cf.pipeline.FeatureFlagStageFilterJsonCreator;
import io.harness.cf.pipeline.FeatureFlagStagePlanCreator;
import io.harness.filters.ExecutionPMSFilterJsonCreator;
import io.harness.filters.GroupFilterJsonCreator;
import io.harness.filters.ParallelFilterJsonCreator;
import io.harness.filters.PipelineFilterJsonCreator;
import io.harness.filters.StepGroupPmsFilterJsonCreator;
import io.harness.plancreator.approval.ApprovalStageFilterJsonCreator;
import io.harness.plancreator.approval.ApprovalStagePlanCreatorV2;
import io.harness.plancreator.execution.ExecutionPmsPlanCreator;
import io.harness.plancreator.group.GroupPlanCreatorV1;
import io.harness.plancreator.pipeline.NGPipelinePlanCreator;
import io.harness.plancreator.stages.StagesPlanCreator;
import io.harness.plancreator.stages.parallel.ParallelPlanCreator;
import io.harness.plancreator.stages.parallel.v1.ParallelPlanCreatorV1;
import io.harness.plancreator.steps.NGStageStepsPlanCreator;
import io.harness.plancreator.steps.SpecNodePlanCreator;
import io.harness.plancreator.steps.StepGroupPMSPlanCreator;
import io.harness.plancreator.steps.barrier.BarrierStepPlanCreator;
import io.harness.plancreator.steps.http.HTTPStepVariableCreator;
import io.harness.plancreator.steps.internal.FlagConfigurationStepPlanCreator;
import io.harness.plancreator.steps.internal.HarnessApprovalStepFilterJsonCreatorV2;
import io.harness.plancreator.steps.internal.PMSStepPlanCreator;
import io.harness.plancreator.steps.internal.PmsStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.ShellScriptStepFilterJsonCreatorV2;
import io.harness.plancreator.steps.pluginstep.ContainerStepVariableCreator;
import io.harness.plancreator.steps.resourceconstraint.QueueStepPlanCreator;
import io.harness.plancreator.steps.resourceconstraint.ResourceConstraintStepPlanCreator;
import io.harness.plancreator.strategy.StrategyConfigPlanCreator;
import io.harness.plancreator.strategy.v1.StrategyConfigPlanCreatorV1;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.pipelinestage.PipelineStageFilterCreator;
import io.harness.pms.pipelinestage.plancreator.PipelineStagePlanCreator;
import io.harness.pms.pipelinestage.v1.plancreator.PipelineStagePlanCreatorV1;
import io.harness.pms.sdk.PmsSdkInitValidator;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.pipeline.variables.ApprovalStageVariableCreator;
import io.harness.pms.sdk.core.pipeline.variables.PipelineVariableCreator;
import io.harness.pms.sdk.core.pipeline.variables.StepGroupVariableCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.pms.sdk.core.variables.StrategyVariableCreator;
import io.harness.pms.sdk.core.variables.VariableCreator;
import io.harness.pms.utils.InjectorUtils;
import io.harness.rule.Owner;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.cd.execution.filtercreator.CdSscaStepFilterJsonCreator;
import io.harness.ssca.cd.execution.variablecreator.CdSscaStepVariableCreator;
import io.harness.ssca.plancreator.CdSscaOrchestrationStepPlanCreator;
import io.harness.steps.approval.ApprovalStepVariableCreator;
import io.harness.steps.approval.step.custom.CustomApprovalStepPlanCreator;
import io.harness.steps.approval.step.custom.CustomApprovalStepVariableCreator;
import io.harness.steps.approval.step.harness.HarnessApprovalStepPlanCreator;
import io.harness.steps.approval.step.jira.JiraApprovalStepPlanCreator;
import io.harness.steps.approval.step.jira.JiraApprovalStepVariableCreator;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalStepPlanCreator;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalStepVariableCreator;
import io.harness.steps.barriers.BarrierStepVariableCreator;
import io.harness.steps.customstage.CustomStageFilterCreator;
import io.harness.steps.customstage.CustomStagePlanCreator;
import io.harness.steps.customstage.CustomStageVariableCreator;
import io.harness.steps.jira.JiraStepVariableCreator;
import io.harness.steps.jira.JiraUpdateStepVariableCreator;
import io.harness.steps.jira.create.JiraCreateStepPlanCreator;
import io.harness.steps.jira.update.JiraUpdateStepPlanCreator;
import io.harness.steps.pipelinestage.PipelineStageVariableCreator;
import io.harness.steps.policy.step.PolicyStepPlanCreator;
import io.harness.steps.policy.variables.PolicyStepVariableCreator;
import io.harness.steps.resourcerestraint.QueueStepVariableCreator;
import io.harness.steps.servicenow.ServiceNowCreateStepVariableCreator;
import io.harness.steps.servicenow.ServiceNowImportSetStepVariableCreator;
import io.harness.steps.servicenow.ServiceNowUpdateStepVariableCreator;
import io.harness.steps.servicenow.create.ServiceNowCreateStepPlanCreator;
import io.harness.steps.servicenow.importset.ServiceNowImportSetStepPlanCreator;
import io.harness.steps.servicenow.update.ServiceNowUpdateStepPlanCreator;
import io.harness.steps.shellscript.ShellScriptStepVariableCreator;
import io.harness.steps.wait.WaitStepPlanCreator;
import io.harness.steps.wait.WaitStepVariableCreator;
import io.harness.variables.ExecutionVariableCreator;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PipelineServiceInternalInfoProviderTest extends CategoryTest {
  @InjectMocks PipelineServiceInternalInfoProvider pipelineServiceInternalInfoProvider;
  @Mock InjectorUtils injectorUtils;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidatePlanCreatorFilterAndVariable() {
    PmsSdkInitValidator.validatePlanCreators(pipelineServiceInternalInfoProvider);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPlanCreators() {
    doNothing().when(injectorUtils).injectMembers(anyList());
    Set<? extends Class<? extends PartialPlanCreator>> planCreatorClasses =
        pipelineServiceInternalInfoProvider.getPlanCreators()
            .stream()
            .map(e -> e.getClass())
            .collect(Collectors.toSet());
    assertThat(planCreatorClasses).hasSize(44);
    assertThat(planCreatorClasses.contains(CustomApprovalStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(NGPipelinePlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(StagesPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(ParallelPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(PMSStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(ApprovalStagePlanCreatorV2.class)).isTrue();
    assertThat(planCreatorClasses.contains(ExecutionPmsPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(StepGroupPMSPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(ResourceConstraintStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(FeatureFlagStagePlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(CfExecutionPMSPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(ServiceNowApprovalStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(JiraUpdateStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(JiraCreateStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(JiraApprovalStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(HarnessApprovalStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(BarrierStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(FlagConfigurationStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(NGStageStepsPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(PolicyStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(ServiceNowCreateStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(ServiceNowUpdateStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(ServiceNowImportSetStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(StrategyConfigPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(StrategyConfigPlanCreatorV1.class)).isTrue();
    assertThat(planCreatorClasses.contains(CustomStagePlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(QueueStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(SpecNodePlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(WaitStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(PipelineStagePlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(PipelineStagePlanCreatorV1.class)).isTrue();
    assertThat(planCreatorClasses.contains(ParallelPlanCreatorV1.class)).isTrue();
    assertThat(planCreatorClasses.contains(GroupPlanCreatorV1.class)).isTrue();
    assertThat(planCreatorClasses.contains(PMSStepPlanCreator.class)).isTrue();
    assertThat(planCreatorClasses.contains(CdSscaOrchestrationStepPlanCreator.class)).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetFilterJsonCreators() {
    doNothing().when(injectorUtils).injectMembers(anyList());
    Set<? extends Class<? extends FilterJsonCreator>> filterCreatorClasses =
        pipelineServiceInternalInfoProvider.getFilterJsonCreators()
            .stream()
            .map(e -> e.getClass())
            .collect(Collectors.toSet());
    assertThat(filterCreatorClasses).hasSize(16);
    assertThat(filterCreatorClasses.contains(ShellScriptStepFilterJsonCreatorV2.class)).isTrue();
    assertThat(filterCreatorClasses.contains(PipelineFilterJsonCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(ParallelFilterJsonCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(ApprovalStageFilterJsonCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(PmsStepFilterJsonCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(ExecutionPMSFilterJsonCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(StepGroupPmsFilterJsonCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(FeatureFlagStageFilterJsonCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(CustomStageFilterCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(PipelineStageFilterCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(GroupFilterJsonCreator.class)).isTrue();
    assertThat(filterCreatorClasses.contains(HarnessApprovalStepFilterJsonCreatorV2.class)).isTrue();
    assertThat(filterCreatorClasses.contains(CdSscaStepFilterJsonCreator.class)).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetVariableCreators() {
    doNothing().when(injectorUtils).injectMembers(anyList());
    Set<? extends Class<? extends VariableCreator>> variableCreatorClasses =
        pipelineServiceInternalInfoProvider.getVariableCreators()
            .stream()
            .map(e -> e.getClass())
            .collect(Collectors.toSet());
    assertThat(variableCreatorClasses).hasSize(27);
    assertThat(variableCreatorClasses.contains(CustomApprovalStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(PipelineVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(HTTPStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(StepGroupVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(ShellScriptStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(JiraStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(ApprovalStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(ApprovalStageVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(ExecutionVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(ServiceNowApprovalStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(PolicyStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(JiraApprovalStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(JiraUpdateStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(ServiceNowCreateStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(ServiceNowUpdateStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(ServiceNowImportSetStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(CustomStageVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(QueueStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(StrategyVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(PipelineStageVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(ContainerStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(WaitStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(BarrierStepVariableCreator.class)).isTrue();
    assertThat(variableCreatorClasses.contains(CdSscaStepVariableCreator.class)).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStepInfo() {
    List<StepInfo> steps = pipelineServiceInternalInfoProvider.getStepInfo();
    assertThat(steps).isNotEmpty();
    assertThat(steps)
        .hasSize(2)
        .extracting(StepInfo::getName)
        .containsExactly("Flag Configuration", SscaConstants.SSCA_ORCHESTRATION_STEP);
  }
}
