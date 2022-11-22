/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.cdng.k8s.CountInstanceSelection;
import io.harness.cdng.k8s.InstanceSelectionBase;
import io.harness.cdng.k8s.InstanceSelectionWrapper;
import io.harness.cdng.k8s.K8sInstanceUnitType;
import io.harness.cdng.k8s.K8sScaleStepInfo;
import io.harness.cdng.k8s.K8sScaleStepNode;
import io.harness.cdng.k8s.PercentageInstanceSelection;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.InstanceUnitType;
import software.wings.sm.states.k8s.K8sScale;
import software.wings.yaml.workflow.StepYaml;

import java.util.Map;

public class K8sScaleStepMapperImpl implements StepMapper {
  @Override
  public String getStepType(StepYaml stepYaml) {
    return StepSpecTypeConstants.K8S_SCALE;
  }

  @Override
  public AbstractStepNode getSpec(StepYaml stepYaml) {
    Map<String, Object> properties = StepMapper.super.getProperties(stepYaml);
    K8sScale state = new K8sScale(stepYaml.getName());
    state.parseProperties(properties);
    K8sScaleStepNode k8sScaleStepNode = new K8sScaleStepNode();
    baseSetup(stepYaml, k8sScaleStepNode);

    InstanceSelectionBase spec;
    if (state.getInstanceUnitType().equals(InstanceUnitType.COUNT)) {
      spec = new CountInstanceSelection();
      ((CountInstanceSelection) spec).setCount(ParameterField.createValueField(state.getInstances()));
    } else {
      spec = new PercentageInstanceSelection();
      ((PercentageInstanceSelection) spec).setPercentage(ParameterField.createValueField(state.getInstances()));
    }

    K8sScaleStepInfo k8sScaleStepInfo =
        K8sScaleStepInfo.infoBuilder()
            .instanceSelection(
                InstanceSelectionWrapper.builder()
                    .type(InstanceUnitType.COUNT.equals(state.getInstanceUnitType()) ? K8sInstanceUnitType.Count
                                                                                     : K8sInstanceUnitType.Percentage)
                    .spec(spec)
                    .build())
            .workload(ParameterField.createValueField(state.getWorkload()))
            .skipDryRun(ParameterField.createValueField(false))
            .skipSteadyStateCheck(ParameterField.createValueField(state.isSkipSteadyStateCheck()))
            .delegateSelectors(MigratorUtility.getDelegateSelectors(state.getDelegateSelectors()))
            .build();
    k8sScaleStepNode.setK8sScaleStepInfo(k8sScaleStepInfo);
    return k8sScaleStepNode;
  }
}
