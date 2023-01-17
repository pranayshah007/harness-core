/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.terraform;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.terraform.TerraformApplyStepInfo;
import io.harness.cdng.provision.terraform.TerraformApplyStepNode;
import io.harness.cdng.provision.terraform.TerraformPlanExecutionData;
import io.harness.cdng.provision.terraform.TerraformPlanStepInfo;
import io.harness.cdng.provision.terraform.TerraformPlanStepNode;
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
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities, GraphNode graphNode) {
    ApplyTerraformState state = (ApplyTerraformState) getState(graphNode);
    ParameterField<String> provisionerId = ParameterField.createValueField(getProvisionerIdentifier(entities, state));
    if (state.isRunPlanOnly()) {
      TerraformPlanExecutionData executionData = getPlanExecutionData(entities, migratedEntities, state);
      TerraformPlanStepInfo stepInfo = TerraformPlanStepInfo.infoBuilder()
                                           .delegateSelectors(getDelegateSelectors(state))
                                           .provisionerIdentifier(provisionerId)
                                           .terraformPlanExecutionData(executionData)
                                           .build();
      TerraformPlanStepNode planStepNode = new TerraformPlanStepNode();
      baseSetup(graphNode, planStepNode);
      planStepNode.setTerraformPlanStepInfo(stepInfo);
      return planStepNode;
    } else {
      TerraformStepConfiguration stepConfiguration = new TerraformStepConfiguration();
      stepConfiguration.setTerraformExecutionData(getExecutionData(entities, migratedEntities, state));
      stepConfiguration.setTerraformStepConfigurationType(TerraformStepConfigurationType.INLINE);
      TerraformApplyStepInfo stepInfo = TerraformApplyStepInfo.infoBuilder()
                                            .delegateSelectors(getDelegateSelectors(state))
                                            .terraformStepConfiguration(stepConfiguration)
                                            .provisionerIdentifier(provisionerId)
                                            .build();
      TerraformApplyStepNode applyStepNode = new TerraformApplyStepNode();
      baseSetup(graphNode, applyStepNode);
      applyStepNode.setTerraformApplyStepInfo(stepInfo);
      return applyStepNode;
    }
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    ApplyTerraformState state1 = (ApplyTerraformState) getState(stepYaml1);
    ApplyTerraformState state2 = (ApplyTerraformState) getState(stepYaml2);
    return false;
  }
}
