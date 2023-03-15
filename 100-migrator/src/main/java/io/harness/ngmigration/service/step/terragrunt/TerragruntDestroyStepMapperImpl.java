/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.terragrunt;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.terragrunt.TerragruntDestroyStepInfo;
import io.harness.cdng.provision.terragrunt.TerragruntDestroyStepNode;
import io.harness.cdng.provision.terragrunt.TerragruntStepConfiguration;
import io.harness.cdng.provision.terragrunt.TerragruntStepConfigurationType;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.provision.TerragruntDestroyState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class TerragruntDestroyStepMapperImpl extends BaseTerragruntProvisionerMapper {
  @Override
  public Set<String> getExpressions(GraphNode graphNode) {
    return new HashSet<>();
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.TERRAGRUNT_DESTROY;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = super.getProperties(stepYaml);
    TerragruntDestroyState state = new TerragruntDestroyState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    TerragruntDestroyState state = (TerragruntDestroyState) getState(graphNode);
    TerragruntDestroyStepNode terragruntDestroyStepNode = new TerragruntDestroyStepNode();
    baseSetup(graphNode, terragruntDestroyStepNode, context.getIdentifierCaseFormat());

    TerragruntStepConfiguration stepConfiguration = new TerragruntStepConfiguration();
    if ((Boolean) graphNode.getProperties().getOrDefault("inheritFromLast", false)) {
      stepConfiguration.setTerragruntStepConfigurationType(TerragruntStepConfigurationType.INHERIT_FROM_APPLY);
    } else {
      stepConfiguration.setTerragruntStepConfigurationType(TerragruntStepConfigurationType.INLINE);
      stepConfiguration.setTerragruntExecutionData(
          getExecutionData(context.getEntities(), context.getMigratedEntities(), state));
    }

    TerragruntDestroyStepInfo stepInfo = TerragruntDestroyStepInfo.infoBuilder()
                                             .provisionerIdentifier(MigratorUtility.RUNTIME_INPUT)
                                             .delegateSelectors(getDelegateSelectors(state))
                                             .terragruntStepConfiguration(stepConfiguration)
                                             .build();

    terragruntDestroyStepNode.setTerragruntDestroyStepInfo(stepInfo);
    terragruntDestroyStepNode.setDelegateSelectors(getDelegateSel(state));
    return terragruntDestroyStepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    TerragruntDestroyState state1 = (TerragruntDestroyState) getState(stepYaml1);
    TerragruntDestroyState state2 = (TerragruntDestroyState) getState(stepYaml2);
    return false;
  }
}
