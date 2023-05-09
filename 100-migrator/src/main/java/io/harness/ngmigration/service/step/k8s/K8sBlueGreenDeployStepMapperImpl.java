/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.k8s;

import io.harness.cdng.k8s.K8sBlueGreenStepInfo;
import io.harness.cdng.k8s.K8sBlueGreenStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.k8s.K8sBlueGreenDeploy;

import java.util.Map;

public class K8sBlueGreenDeployStepMapperImpl extends K8sAbstractStepMapperImpl {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.K8S_BLUE_GREEN_DEPLOY;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    K8sBlueGreenDeploy state = new K8sBlueGreenDeploy(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    K8sBlueGreenDeploy state = (K8sBlueGreenDeploy) getState(graphNode);
    K8sBlueGreenStepNode stepNode = new K8sBlueGreenStepNode();
    baseSetup(graphNode, stepNode, context.getIdentifierCaseFormat());
    K8sBlueGreenStepInfo stepInfo =
        K8sBlueGreenStepInfo.infoBuilder()
            .pruningEnabled(ParameterField.createValueField(false))
            .delegateSelectors(MigratorUtility.getDelegateSelectors(state.getDelegateSelectors()))
            .skipDryRun(ParameterField.createValueField(state.isSkipDryRun()))
            .build();
    stepNode.setK8sBlueGreenStepInfo(stepInfo);
    return stepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return true;
  }
}
