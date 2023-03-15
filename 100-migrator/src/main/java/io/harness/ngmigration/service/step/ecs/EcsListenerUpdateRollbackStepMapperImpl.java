/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.ecs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.ecs.EcsBlueGreenRollbackStepInfo;
import io.harness.cdng.ecs.EcsBlueGreenRollbackStepNode;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.EcsBGUpdateListnerRollbackState;

import java.util.Collections;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class EcsListenerUpdateRollbackStepMapperImpl extends StepMapper {
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
    return StepSpecTypeConstants.ECS_BLUE_GREEN_ROLLBACK;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    EcsBGUpdateListnerRollbackState state = new EcsBGUpdateListnerRollbackState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    EcsBGUpdateListnerRollbackState state = (EcsBGUpdateListnerRollbackState) getState(graphNode);
    EcsBlueGreenRollbackStepNode stepNode = new EcsBlueGreenRollbackStepNode();
    baseSetup(state, stepNode);
    EcsBlueGreenRollbackStepInfo stepInfo =
        EcsBlueGreenRollbackStepInfo.infoBuilder()
            .delegateSelectors(ParameterField.createValueField(Collections.emptyList()))
            .build();
    stepNode.setEcsBlueGreenRollbackStepInfo(stepInfo);
    return stepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return true;
  }
}
