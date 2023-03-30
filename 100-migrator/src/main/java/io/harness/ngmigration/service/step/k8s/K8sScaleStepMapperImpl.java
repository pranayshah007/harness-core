/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.k8s;

import io.harness.cdng.k8s.CountInstanceSelection;
import io.harness.cdng.k8s.InstanceSelectionBase;
import io.harness.cdng.k8s.InstanceSelectionWrapper;
import io.harness.cdng.k8s.K8sInstanceUnitType;
import io.harness.cdng.k8s.K8sScaleStepInfo;
import io.harness.cdng.k8s.K8sScaleStepNode;
import io.harness.cdng.k8s.PercentageInstanceSelection;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.beans.InstanceUnitType;
import software.wings.sm.State;
import software.wings.sm.states.k8s.K8sScale;

import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class K8sScaleStepMapperImpl extends StepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.K8S_SCALE;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    K8sScale state = new K8sScale(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    K8sScale state = (K8sScale) getState(graphNode);
    K8sScaleStepNode k8sScaleStepNode = new K8sScaleStepNode();
    baseSetup(graphNode, k8sScaleStepNode, context.getIdentifierCaseFormat());

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
            .skipSteadyStateCheck(ParameterField.createValueField(state.isSkipSteadyStateCheck()))
            .delegateSelectors(MigratorUtility.getDelegateSelectors(state.getDelegateSelectors()))
            .build();
    k8sScaleStepNode.setK8sScaleStepInfo(k8sScaleStepInfo);
    return k8sScaleStepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    K8sScale state1 = (K8sScale) getState(stepYaml1);
    K8sScale state2 = (K8sScale) getState(stepYaml2);
    if (!state2.getInstanceUnitType().equals(state1.getInstanceUnitType())) {
      return false;
    }
    if (!state1.getInstances().equals(state2.getInstances())) {
      return false;
    }
    if (!StringUtils.equals(state1.getWorkload(), state2.getWorkload())) {
      return false;
    }
    return true;
  }
}
