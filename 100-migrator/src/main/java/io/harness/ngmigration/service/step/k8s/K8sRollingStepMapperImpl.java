/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.k8s;

import io.harness.cdng.k8s.K8sRollingStepInfo;
import io.harness.cdng.k8s.K8sRollingStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.sm.State;

public class K8sRollingStepMapperImpl extends StepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.K8S_ROLLING_DEPLOY;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    return null;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    K8sRollingStepNode k8sRollingStepNode = new K8sRollingStepNode();
    baseSetup(graphNode, k8sRollingStepNode, context.getIdentifierCaseFormat());
    k8sRollingStepNode.setK8sRollingStepInfo(
        K8sRollingStepInfo.infoBuilder().skipDryRun(ParameterField.createValueField(false)).build());
    return k8sRollingStepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return true;
  }
}
