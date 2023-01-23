/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.WorkflowStepSupportStatus;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.plancreator.steps.resourceconstraint.QueueStepInfo;
import io.harness.plancreator.steps.resourceconstraint.QueueStepNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.resourcerestraint.beans.QueueHoldingScope;

import software.wings.beans.GraphNode;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.sm.State;
import software.wings.sm.states.HoldingScope;
import software.wings.sm.states.ResourceConstraintState;

import java.util.Map;

public class ResourceConstraintStepMapperImpl implements StepMapper {
  @Override
  public WorkflowStepSupportStatus stepSupportStatus(GraphNode graphNode) {
    return WorkflowStepSupportStatus.MANUAL_EFFORT;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.RESOURCE_CONSTRAINT;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = StepMapper.super.getProperties(stepYaml);
    ResourceConstraintState state = new ResourceConstraintState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities, GraphNode graphNode) {
    ResourceConstraintState state = (ResourceConstraintState) getState(graphNode);
    QueueStepNode queueStepNode = new QueueStepNode();
    baseSetup(graphNode, queueStepNode);
    QueueStepInfo queueStepInfo = new QueueStepInfo();
    queueStepInfo.setKey(MigratorUtility.RUNTIME_INPUT);
    queueStepInfo.setScope(getHoldingScope(state));
    queueStepNode.setQueueStepInfo(queueStepInfo);
    return queueStepNode;
  }

  private QueueHoldingScope getHoldingScope(ResourceConstraintState state) {
    if (HoldingScope.WORKFLOW.name().equals(state.getHoldingScope())) {
      return QueueHoldingScope.STAGE;
    }
    if (HoldingScope.PIPELINE.name().equals(state.getHoldingScope())) {
      return QueueHoldingScope.PIPELINE;
    }
    return QueueHoldingScope.STAGE;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return true;
  }
}
