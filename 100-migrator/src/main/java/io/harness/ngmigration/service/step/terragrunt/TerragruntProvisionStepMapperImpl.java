/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.terragrunt;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.plancreator.steps.AbstractStepNode;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.provision.TerragruntApplyState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class TerragruntProvisionStepMapperImpl extends BaseTerragruntProvisionerMapper {
  @Override
  public Set<String> getExpressions(GraphNode graphNode) {
    return new HashSet<>();
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    TerragruntApplyState state = (TerragruntApplyState) getState(stepYaml);
    if (state.isRunPlanOnly()) {
      return StepSpecTypeConstants.TERRAGRUNT_PLAN;
    } else {
      return StepSpecTypeConstants.TERRAGRUNT_APPLY;
    }
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = super.getProperties(stepYaml);
    TerragruntApplyState state = new TerragruntApplyState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    return getStepNode(
        context.getEntities(), context.getMigratedEntities(), graphNode, context.getIdentifierCaseFormat());
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    TerragruntApplyState state1 = (TerragruntApplyState) getState(stepYaml1);
    TerragruntApplyState state2 = (TerragruntApplyState) getState(stepYaml2);
    return false;
  }
}
