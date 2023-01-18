/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.terraform;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.terraform.TerraformDestroyStepInfo;
import io.harness.cdng.provision.terraform.TerraformDestroyStepNode;
import io.harness.cdng.provision.terraform.TerraformStepConfiguration;
import io.harness.cdng.provision.terraform.TerraformStepConfigurationType;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.sm.State;
import software.wings.sm.states.provision.DestroyTerraformProvisionState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class TerraformDestroyStepMapperImpl extends BaseTerraformProvisionerMapper {
  @Override
  public Set<String> getExpressions(GraphNode graphNode) {
    return new HashSet<>();
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.TERRAFORM_DESTROY;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = super.getProperties(stepYaml);
    DestroyTerraformProvisionState state = new DestroyTerraformProvisionState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities, GraphNode graphNode) {
    DestroyTerraformProvisionState state = (DestroyTerraformProvisionState) getState(graphNode);
    TerraformDestroyStepNode terraformDestroyStepNode = new TerraformDestroyStepNode();
    baseSetup(graphNode, terraformDestroyStepNode);

    //    TerraformStepConfigurationType.INHERIT_FROM_APPLY;
    //    TerraformStepConfigurationType.INHERIT_FROM_PLAN;
    TerraformStepConfiguration stepConfiguration = new TerraformStepConfiguration();
    // TODO
    stepConfiguration.setTerraformStepConfigurationType(TerraformStepConfigurationType.INLINE);
    stepConfiguration.setTerraformExecutionData(getExecutionData(entities, migratedEntities, state));

    TerraformDestroyStepInfo stepInfo =
        TerraformDestroyStepInfo.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField(getProvisionerIdentifier(entities, state)))
            .delegateSelectors(getDelegateSelectors(state))
            .terraformStepConfiguration(stepConfiguration)
            .build();

    terraformDestroyStepNode.setTerraformDestroyStepInfo(stepInfo);
    terraformDestroyStepNode.setDelegateSelectors(getDelegateSel(state));
    return terraformDestroyStepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    DestroyTerraformProvisionState state1 = (DestroyTerraformProvisionState) getState(stepYaml1);
    DestroyTerraformProvisionState state2 = (DestroyTerraformProvisionState) getState(stepYaml2);
    return false;
  }
}
