/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.elastigroup;

import io.harness.cdng.elastigroup.ElastigroupSwapRouteStepInfo;
import io.harness.cdng.elastigroup.ElastigroupSwapRouteStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.spotinst.SpotInstListenerUpdateState;

import java.util.Map;

public class ElastigroupSwapRouteStepMapperImpl extends StepMapper {
  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.ELASTIGROUP_SWAP_ROUTE;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    SpotInstListenerUpdateState state = new SpotInstListenerUpdateState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    SpotInstListenerUpdateState state = (SpotInstListenerUpdateState) getState(graphNode);

    ElastigroupSwapRouteStepNode node = new ElastigroupSwapRouteStepNode();
    baseSetup(state, node);
    ElastigroupSwapRouteStepInfo elastigroupDeployStepInfo =
        ElastigroupSwapRouteStepInfo.infoBuilder()
            .downsizeOldElastigroup(ParameterField.createValueField(state.isDownsizeOldElastiGroup()))
            .build();

    node.setElastigroupSwapRouteStepInfo(elastigroupDeployStepInfo);
    return node;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    SpotInstListenerUpdateState state1 = (SpotInstListenerUpdateState) getState(stepYaml1);
    SpotInstListenerUpdateState state2 = (SpotInstListenerUpdateState) getState(stepYaml2);
    return state1.isDownsizeOldElastiGroup() == state2.isDownsizeOldElastiGroup();
  }

  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }
}
