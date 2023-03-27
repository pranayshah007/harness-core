/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.helm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.helm.HelmDeployStepInfo;
import io.harness.cdng.helm.HelmDeployStepNode;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.HelmDeployState;

import java.util.Collections;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class HelmDeployStepMapperImpl extends StepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.HELM_DEPLOY;
  }

  @Override
  public ServiceDefinitionType inferServiceDef(WorkflowMigrationContext context, GraphNode graphNode) {
    return ServiceDefinitionType.NATIVE_HELM;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    HelmDeployState state = new HelmDeployState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    HelmDeployState state = (HelmDeployState) getState(graphNode);
    HelmDeployStepNode stepNode = new HelmDeployStepNode();
    baseSetup(state, stepNode, context.getIdentifierCaseFormat());
    HelmDeployStepInfo stepInfo = HelmDeployStepInfo.infoBuilder()
                                      .helmDeployFqn(state.getHelmReleaseNamePrefix())
                                      .delegateSelectors(ParameterField.createValueField(Collections.emptyList()))
                                      .ignoreReleaseHistFailStatus(ParameterField.createValueField(false))
                                      .build();
    stepNode.setHelmDeployStepInfo(stepInfo);
    return stepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    // @deepak: Please re-evaluate
    return false;
  }
}
