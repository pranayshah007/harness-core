/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.terraform;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.plancreator.steps.AbstractStepNode;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.provision.ApplyTerraformState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class TerraformApplyStepMapperImpl extends BaseTerraformProvisionerMapper {
  @Override
  public Set<String> getExpressions(GraphNode graphNode) {
    return new HashSet<>();
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    ApplyTerraformState state = (ApplyTerraformState) getState(stepYaml);
    if (state.isRunPlanOnly()) {
      return StepSpecTypeConstants.TERRAFORM_PLAN;
    }
    return StepSpecTypeConstants.TERRAFORM_APPLY;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = super.getProperties(stepYaml);
    ApplyTerraformState state = new ApplyTerraformState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    return getStepNode(
        context.getEntities(), context.getMigratedEntities(), graphNode, context.getIdentifierCaseFormat());
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    ApplyTerraformState state1 = (ApplyTerraformState) getState(stepYaml1);
    ApplyTerraformState state2 = (ApplyTerraformState) getState(stepYaml2);
    return false;
  }
}
