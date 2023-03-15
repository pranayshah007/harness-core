/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.cdng.customDeployment.FetchInstanceScriptStepInfo;
import io.harness.cdng.customDeployment.FetchInstanceScriptStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.customdeployment.InstanceFetchState;

import java.util.Map;

public class CustomFetchInstancesStepMapperImpl extends StepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    InstanceFetchState state = new InstanceFetchState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    InstanceFetchState state = (InstanceFetchState) getState(graphNode);
    FetchInstanceScriptStepNode fetchInstanceScriptStepNode = new FetchInstanceScriptStepNode();
    baseSetup(graphNode, fetchInstanceScriptStepNode, context.getIdentifierCaseFormat());
    FetchInstanceScriptStepInfo stepInfo = new FetchInstanceScriptStepInfo();
    stepInfo.setDelegateSelectors(MigratorUtility.getDelegateSelectors(state.getTags()));
    fetchInstanceScriptStepNode.setFetchInstanceScriptStepInfo(stepInfo);
    return fetchInstanceScriptStepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return true;
  }
}
