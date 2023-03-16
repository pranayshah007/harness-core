/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.k8s;

import io.harness.cdng.k8s.K8sApplyStepInfo;
import io.harness.cdng.k8s.K8sApplyStepNode;
import io.harness.data.structure.CompareUtils;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.k8s.K8sApplyState;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class K8sApplyStepMapperImpl extends StepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.K8S_APPLY;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    K8sApplyState state = new K8sApplyState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    K8sApplyState state = (K8sApplyState) getState(graphNode);
    K8sApplyStepNode k8sApplyStepNode = new K8sApplyStepNode();
    baseSetup(state, k8sApplyStepNode, context.getIdentifierCaseFormat());
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
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    K8sApplyState state1 = (K8sApplyState) getState(stepYaml1);
    K8sApplyState state2 = (K8sApplyState) getState(stepYaml2);
    return StringUtils.compare(state1.getFilePaths(), state2.getFilePaths()) == 0
        && state1.isInheritManifests() == state2.isInheritManifests()
        && StringUtils.compare(state1.getInlineStepOverride(), state2.getInlineStepOverride()) == 0
        && CompareUtils.compareObjects(state1.getRemoteStepOverride(), state2.getRemoteStepOverride());
  }
}
