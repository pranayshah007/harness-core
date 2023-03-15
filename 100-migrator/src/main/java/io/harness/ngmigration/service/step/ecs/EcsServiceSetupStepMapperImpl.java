/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.ecs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.ecs.EcsRollingDeployStepInfo;
import io.harness.cdng.ecs.EcsRollingDeployStepNode;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.EcsServiceSetup;

import java.util.Collections;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public class EcsServiceSetupStepMapperImpl extends StepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.MANUAL_EFFORT;
  }

  @Override
  public ServiceDefinitionType inferServiceDef(WorkflowMigrationContext context, GraphNode graphNode) {
    return ServiceDefinitionType.ECS;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.ECS_ROLLING_DEPLOY;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    EcsServiceSetup state = new EcsServiceSetup(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    EcsServiceSetup state = (EcsServiceSetup) getState(graphNode);
    EcsRollingDeployStepNode stepNode = new EcsRollingDeployStepNode();
    baseSetup(state, stepNode);
    ParameterField<Boolean> sameAsAlreadyRunningInstances = ParameterField.createValueField(false);
    if ("runningInstances".equals(state.getDesiredInstanceCount())) {
      sameAsAlreadyRunningInstances = ParameterField.createValueField(true);
    }
    EcsRollingDeployStepInfo stepInfo = EcsRollingDeployStepInfo.infoBuilder()
                                            .forceNewDeployment(ParameterField.createValueField(false))
                                            .sameAsAlreadyRunningInstances(sameAsAlreadyRunningInstances)
                                            .delegateSelectors(ParameterField.createValueField(Collections.emptyList()))
                                            .build();
    stepNode.setEcsRollingDeployStepInfo(stepInfo);
    return stepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    EcsServiceSetup state1 = (EcsServiceSetup) getState(stepYaml1);
    EcsServiceSetup state2 = (EcsServiceSetup) getState(stepYaml2);
    return StringUtils.equals(state1.getDesiredInstanceCount(), state2.getDesiredInstanceCount());
  }
}
