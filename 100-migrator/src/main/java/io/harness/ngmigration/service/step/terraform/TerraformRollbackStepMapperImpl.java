/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.terraform;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.terraform.TerraformRollbackStepNode;
import io.harness.cdng.provision.terraform.steps.rolllback.TerraformRollbackStepInfo;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.provision.TerraformRollbackState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class TerraformRollbackStepMapperImpl extends BaseTerraformProvisionerMapper {
  @Override
  public Set<String> getExpressions(GraphNode graphNode) {
    return new HashSet<>();
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.TERRAFORM_ROLLBACK;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = super.getProperties(stepYaml);
    TerraformRollbackState state = new TerraformRollbackState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    TerraformRollbackState state = (TerraformRollbackState) getState(graphNode);
    TerraformRollbackStepNode terraformRollbackStepNode = new TerraformRollbackStepNode();
    baseSetup(graphNode, terraformRollbackStepNode, context.getIdentifierCaseFormat());

    TerraformRollbackStepInfo terraformRollbackStepInfo = new TerraformRollbackStepInfo();
    terraformRollbackStepInfo.setDelegateSelectors(getDelegateSelectors(state));
    terraformRollbackStepInfo.setProvisionerIdentifier(MigratorUtility.RUNTIME_INPUT);

    terraformRollbackStepNode.setTerraformRollbackStepInfo(terraformRollbackStepInfo);
    return terraformRollbackStepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    TerraformRollbackState state1 = (TerraformRollbackState) getState(stepYaml1);
    TerraformRollbackState state2 = (TerraformRollbackState) getState(stepYaml2);
    return false;
  }
}
