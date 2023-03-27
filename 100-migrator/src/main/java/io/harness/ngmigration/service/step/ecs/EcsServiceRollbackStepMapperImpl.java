/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.ecs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.ecs.EcsRollingRollbackStepInfo;
import io.harness.cdng.ecs.EcsRollingRollbackStepNode;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.EcsServiceRollback;

import java.util.Collections;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class EcsServiceRollbackStepMapperImpl extends StepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.MANUAL_EFFORT;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.ECS_ROLLING_ROLLBACK;
  }

  @Override
  public ServiceDefinitionType inferServiceDef(WorkflowMigrationContext context, GraphNode graphNode) {
    return ServiceDefinitionType.ECS;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    EcsServiceRollback state = new EcsServiceRollback(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    EcsRollingRollbackStepNode stepNode = new EcsRollingRollbackStepNode();
    baseSetup(graphNode, stepNode, context.getIdentifierCaseFormat());
    EcsRollingRollbackStepInfo stepInfo =
        EcsRollingRollbackStepInfo.infoBuilder()
            .delegateSelectors(ParameterField.createValueField(Collections.emptyList()))
            .build();
    stepNode.setEcsRollingRollbackStepInfo(stepInfo);
    return stepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return true;
  }
}
