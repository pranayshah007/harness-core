/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.pcf;

import static io.harness.delegate.beans.pcf.ResizeStrategy.DOWNSIZE_OLD_FIRST;
import static io.harness.delegate.beans.pcf.TasResizeStrategyType.DOWNSCALE_OLD_FIRST;
import static io.harness.delegate.beans.pcf.TasResizeStrategyType.UPSCALE_NEW_FIRST;

import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.tas.TasBGAppSetupStepInfo;
import io.harness.cdng.tas.TasBGAppSetupStepNode;
import io.harness.cdng.tas.TasCanaryAppSetupStepInfo;
import io.harness.cdng.tas.TasCanaryAppSetupStepNode;
import io.harness.cdng.tas.TasInstanceCountType;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.pcf.PcfSetupState;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class PcfSetupStepMapperImpl extends PcfAbstractStepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    PcfSetupState state = (PcfSetupState) getState(stepYaml);
    if (state.isBlueGreen()) {
      return StepSpecTypeConstants.TAS_BG_APP_SETUP;
    } else {
      return StepSpecTypeConstants.TAS_CANARY_APP_SETUP;
    }
  }

  @Override
  public ServiceDefinitionType inferServiceDef(WorkflowMigrationContext context, GraphNode graphNode) {
    return ServiceDefinitionType.TAS;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    PcfSetupState state = new PcfSetupState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    PcfSetupState state = (PcfSetupState) getState(graphNode);
    if (null == state.getOlderActiveVersionCountToKeep()) {
      state.setOlderActiveVersionCountToKeep(3);
    }

    if (state.isBlueGreen()) {
      TasBGAppSetupStepNode tasBGAppSetupStepNode = new TasBGAppSetupStepNode();
      baseSetup(state, tasBGAppSetupStepNode, context.getIdentifierCaseFormat());

      TasBGAppSetupStepInfo tasBGAppSetupStepInfo =
          TasBGAppSetupStepInfo.infoBuilder()
              .tasInstanceCountType(getInstanceCountType(state.isUseCurrentRunningCount()))
              .additionalRoutes(
                  ParameterField.createValueField(Arrays.stream(state.getFinalRouteMap()).collect(Collectors.toList())))
              .existingVersionToKeep(
                  new ParameterField(state.getOlderActiveVersionCountToKeep(), null, false, true, null, null, false))
              .tempRoutes(
                  ParameterField.createValueField(Arrays.stream(state.getTempRouteMap()).collect(Collectors.toList())))
              .delegateSelectors(MigratorUtility.getDelegateSelectors(state.getTags()))
              .tempRoutes(
                  ParameterField.createValueField(Arrays.stream(state.getTempRouteMap()).collect(Collectors.toList())))
              .build();

      tasBGAppSetupStepNode.setTasBGAppSetupStepInfo(tasBGAppSetupStepInfo);
      return tasBGAppSetupStepNode;
    } else {
      TasCanaryAppSetupStepNode tasCanaryAppSetupStepNode = new TasCanaryAppSetupStepNode();
      baseSetup(state, tasCanaryAppSetupStepNode, context.getIdentifierCaseFormat());

      TasCanaryAppSetupStepInfo tasCanaryAppSetupStepInfo =
          TasCanaryAppSetupStepInfo.infoBuilder()
              .resizeStrategy(ParameterField.createValueField(
                  state.getResizeStrategy() == DOWNSIZE_OLD_FIRST ? DOWNSCALE_OLD_FIRST : UPSCALE_NEW_FIRST))
              .instanceCountType(getInstanceCountType(state.isUseCurrentRunningCount()))
              .additionalRoutes(
                  ParameterField.createValueField(Arrays.stream(state.getFinalRouteMap()).collect(Collectors.toList())))
              .existingVersionToKeep(
                  new ParameterField(state.getOlderActiveVersionCountToKeep(), null, false, true, null, null, false))
              .delegateSelectors(MigratorUtility.getDelegateSelectors(state.getTags()))
              .build();

      tasCanaryAppSetupStepNode.setTasCanaryAppSetupStepInfo(tasCanaryAppSetupStepInfo);
      return tasCanaryAppSetupStepNode;
    }
  }

  private TasInstanceCountType getInstanceCountType(boolean useCurrentRunningCount) {
    if (useCurrentRunningCount) {
      return TasInstanceCountType.MATCH_RUNNING_INSTANCES;
    } else {
      return TasInstanceCountType.FROM_MANIFEST;
    }
  }
  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return false;
  }
}
