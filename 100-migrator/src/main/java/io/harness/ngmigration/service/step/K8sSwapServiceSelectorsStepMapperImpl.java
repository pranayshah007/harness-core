/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.cdng.k8s.K8sBGSwapServicesStepInfo;
import io.harness.cdng.k8s.K8sBGSwapServicesStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.sm.states.KubernetesSwapServiceSelectors;
import software.wings.yaml.workflow.StepYaml;

import java.util.Collections;
import java.util.Map;

public class K8sSwapServiceSelectorsStepMapperImpl implements StepMapper {
  @Override
  public String getStepType(StepYaml stepYaml) {
    return StepSpecTypeConstants.K8S_BG_SWAP_SERVICES;
  }

  @Override
  public AbstractStepNode getSpec(StepYaml stepYaml) {
    Map<String, Object> properties = StepMapper.super.getProperties(stepYaml);
    KubernetesSwapServiceSelectors state = new KubernetesSwapServiceSelectors(stepYaml.getName());
    state.parseProperties(properties);
    K8sBGSwapServicesStepNode k8sBGSwapServicesStepNode = new K8sBGSwapServicesStepNode();
    baseSetup(stepYaml, k8sBGSwapServicesStepNode);
    K8sBGSwapServicesStepInfo stepInfo = new K8sBGSwapServicesStepInfo();

    stepInfo.setSkipDryRun(ParameterField.createValueField(false));
    stepInfo.setBlueGreenSwapServicesStepFqn(state.getService2());
    stepInfo.setDelegateSelectors(ParameterField.createValueField(Collections.emptyList()));
    stepInfo.setBlueGreenStepFqn(state.getService1());

    k8sBGSwapServicesStepNode.setK8sBGSwapServicesStepInfo(stepInfo);
    return k8sBGSwapServicesStepNode;
  }
}
