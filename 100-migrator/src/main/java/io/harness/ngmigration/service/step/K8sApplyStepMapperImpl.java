/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.cdng.k8s.K8sApplyStepInfo;
import io.harness.cdng.k8s.K8sApplyStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.sm.State;
import software.wings.sm.states.k8s.K8sApplyState;
import software.wings.yaml.workflow.StepYaml;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class K8sApplyStepMapperImpl implements StepMapper {
  @Override
  public String getStepType(StepYaml stepYaml) {
    return StepSpecTypeConstants.K8S_APPLY;
  }

  @Override
  public State getState(StepYaml stepYaml) {
    Map<String, Object> properties = StepMapper.super.getProperties(stepYaml);
    K8sApplyState state = new K8sApplyState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(StepYaml stepYaml) {
    K8sApplyState state = (K8sApplyState) getState(stepYaml);
    K8sApplyStepNode k8sApplyStepNode = new K8sApplyStepNode();
    baseSetup(state, k8sApplyStepNode);
    K8sApplyStepInfo k8sApplyStepInfo =
        K8sApplyStepInfo.infoBuilder()
            .delegateSelectors(MigratorUtility.getDelegateSelectors(state.getDelegateSelectors()))
            .filePaths(ParameterField.createValueField(Arrays.stream(state.getFilePaths().split(","))
                                                           .map(String::trim)
                                                           .filter(StringUtils::isNotBlank)
                                                           .collect(Collectors.toList())))
            .skipDryRun(ParameterField.createValueField(state.isSkipDryRun()))
            .skipSteadyStateCheck(ParameterField.createValueField(state.isSkipSteadyStateCheck()))
            .skipRendering(ParameterField.createValueField(state.isSkipRendering()))
            .build();
    k8sApplyStepNode.setK8sApplyStepInfo(k8sApplyStepInfo);
    return k8sApplyStepNode;
  }

  @Override
  public boolean areSimilar(StepYaml stepYaml1, StepYaml stepYaml2) {
    // @deepak: Please re-evaluate
    return true;
  }
}
