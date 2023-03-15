/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.terragrunt;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.terragrunt.TerragruntRollbackStepInfo;
import io.harness.cdng.provision.terragrunt.TerragruntRollbackStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.provision.TerragruntRollbackState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class TerragruntRollbackStepMapperImpl extends BaseTerragruntProvisionerMapper {
  @Override
  public Set<String> getExpressions(GraphNode graphNode) {
    return new HashSet<>();
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.TERRAGRUNT_ROLLBACK;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = super.getProperties(stepYaml);
    TerragruntRollbackState state = new TerragruntRollbackState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    TerragruntRollbackState state = (TerragruntRollbackState) getState(graphNode);
    TerragruntRollbackStepNode terragruntRollbackStepNode = new TerragruntRollbackStepNode();
    baseSetup(graphNode, terragruntRollbackStepNode, context.getIdentifierCaseFormat());

    TerragruntRollbackStepInfo terragruntRollbackStepInfo = new TerragruntRollbackStepInfo();
    terragruntRollbackStepInfo.setDelegateSelectors(getDelegateSelectors(state));
    terragruntRollbackStepInfo.setProvisionerIdentifier(MigratorUtility.RUNTIME_INPUT);

    terragruntRollbackStepNode.setTerragruntRollbackStepInfo(terragruntRollbackStepInfo);
    return terragruntRollbackStepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    TerragruntRollbackState state1 = (TerragruntRollbackState) getState(stepYaml1);
    TerragruntRollbackState state2 = (TerragruntRollbackState) getState(stepYaml2);
    return false;
  }
}
