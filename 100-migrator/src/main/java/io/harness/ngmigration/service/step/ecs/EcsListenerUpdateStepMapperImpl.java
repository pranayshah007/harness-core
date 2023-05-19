/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.ecs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.ecs.EcsBlueGreenSwapTargetGroupsStepInfo;
import io.harness.cdng.ecs.EcsBlueGreenSwapTargetGroupsStepNode;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.EcsBGUpdateListnerState;

import java.util.Collections;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class EcsListenerUpdateStepMapperImpl extends EcsBaseStepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.MANUAL_EFFORT;
  }

  @Override
  public ServiceDefinitionType inferServiceDef(WorkflowMigrationContext context, GraphNode graphNode) {
    return ServiceDefinitionType.ECS;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    EcsBGUpdateListnerState state = new EcsBGUpdateListnerState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    EcsBGUpdateListnerState state = (EcsBGUpdateListnerState) getState(graphNode);
    EcsBlueGreenSwapTargetGroupsStepNode stepNode = new EcsBlueGreenSwapTargetGroupsStepNode();
    baseSetup(state, stepNode, context.getIdentifierCaseFormat());
    EcsBlueGreenSwapTargetGroupsStepInfo stepInfo =
        EcsBlueGreenSwapTargetGroupsStepInfo.infoBuilder()
            .delegateSelectors(ParameterField.createValueField(Collections.emptyList()))
            .doNotDownsizeOldService(ParameterField.createValueField(!state.isDownsizeOldService()))
            .build();
    stepNode.setEcsBlueGreenSwapTargetGroupsStepInfo(stepInfo);
    return stepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    EcsBGUpdateListnerState state1 = (EcsBGUpdateListnerState) getState(stepYaml1);
    EcsBGUpdateListnerState state2 = (EcsBGUpdateListnerState) getState(stepYaml2);
    return state1.isDownsizeOldService() == state2.isDownsizeOldService();
  }
}
