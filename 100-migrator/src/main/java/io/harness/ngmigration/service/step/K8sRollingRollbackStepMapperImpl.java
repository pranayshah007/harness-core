/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.cdng.k8s.K8sRollingRollbackStepInfo;
import io.harness.cdng.k8s.K8sRollingRollbackStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.ngmigration.CgEntityId;
import software.wings.sm.State;

import java.util.Map;

public class K8sRollingRollbackStepMapperImpl implements StepMapper {
  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.K8S_ROLLING_ROLLBACK;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    return null;
  }

  @Override
  public AbstractStepNode getSpec(Map<CgEntityId, NGYamlFile> migratedEntities, GraphNode graphNode) {
    K8sRollingRollbackStepNode k8sRollingStepNode = new K8sRollingRollbackStepNode();
    baseSetup(graphNode, k8sRollingStepNode);
    k8sRollingStepNode.setK8sRollingRollbackStepInfo(
        K8sRollingRollbackStepInfo.infoBuilder().pruningEnabled(ParameterField.createValueField(false)).build());
    return k8sRollingStepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return true;
  }
}
