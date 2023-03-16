/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.k8s;

import io.harness.cdng.k8s.K8sBGSwapServicesStepInfo;
import io.harness.cdng.k8s.K8sBGSwapServicesStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.KubernetesSwapServiceSelectors;

import java.util.Collections;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class K8sSwapServiceSelectorsStepMapperImpl extends StepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.K8S_BG_SWAP_SERVICES;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    KubernetesSwapServiceSelectors state = new KubernetesSwapServiceSelectors(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    KubernetesSwapServiceSelectors state = (KubernetesSwapServiceSelectors) getState(graphNode);
    K8sBGSwapServicesStepNode k8sBGSwapServicesStepNode = new K8sBGSwapServicesStepNode();
    baseSetup(graphNode, k8sBGSwapServicesStepNode, context.getIdentifierCaseFormat());
    K8sBGSwapServicesStepInfo stepInfo = new K8sBGSwapServicesStepInfo();

    stepInfo.setBlueGreenSwapServicesStepFqn(state.getService2());
    stepInfo.setDelegateSelectors(ParameterField.createValueField(Collections.emptyList()));
    stepInfo.setBlueGreenStepFqn(state.getService1());

    k8sBGSwapServicesStepNode.setK8sBGSwapServicesStepInfo(stepInfo);
    return k8sBGSwapServicesStepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    KubernetesSwapServiceSelectors state1 = (KubernetesSwapServiceSelectors) getState(stepYaml1);
    KubernetesSwapServiceSelectors state2 = (KubernetesSwapServiceSelectors) getState(stepYaml2);
    return StringUtils.equals(state1.getService1(), state2.getService1())
        && StringUtils.equals(state1.getService2(), state2.getService2());
  }
}
