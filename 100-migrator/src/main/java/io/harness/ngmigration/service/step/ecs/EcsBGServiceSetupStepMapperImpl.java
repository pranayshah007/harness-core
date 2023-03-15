/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.ecs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.ecs.EcsBlueGreenCreateServiceStepInfo;
import io.harness.cdng.ecs.EcsBlueGreenCreateServiceStepNode;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.timeout.Timeout;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.EcsBlueGreenServiceSetup;

import java.util.Collections;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public class EcsBGServiceSetupStepMapperImpl extends StepMapper {
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
    return StepSpecTypeConstants.ECS_BLUE_GREEN_CREATE_SERVICE;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    EcsBlueGreenServiceSetup state = new EcsBlueGreenServiceSetup(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  public ParameterField<Timeout> getTimeout(State state) {
    EcsBlueGreenServiceSetup ecsBlueGreenServiceSetup = (EcsBlueGreenServiceSetup) state;
    return MigratorUtility.getTimeout(ecsBlueGreenServiceSetup.getServiceSteadyStateTimeout());
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    EcsBlueGreenServiceSetup state = (EcsBlueGreenServiceSetup) getState(graphNode);
    EcsBlueGreenCreateServiceStepNode stepNode = new EcsBlueGreenCreateServiceStepNode();
    baseSetup(state, stepNode);
    EcsBlueGreenCreateServiceStepInfo stepInfo =
        EcsBlueGreenCreateServiceStepInfo.infoBuilder()
            .loadBalancer(ParameterField.createValueField(state.getLoadBalancerName()))
            .prodListener(ParameterField.createValueField(state.getProdListenerArn()))
            .prodListenerRuleArn(ParameterField.createValueField(state.getProdListenerRuleArn()))
            .stageListener(ParameterField.createValueField(state.getStageListenerArn()))
            .stageListenerRuleArn(ParameterField.createValueField(state.getStageListenerRuleArn()))
            .delegateSelectors(ParameterField.createValueField(Collections.emptyList()))
            .build();

    stepNode.setEcsBlueGreenCreateServiceStepInfo(stepInfo);
    return stepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    EcsBlueGreenServiceSetup state1 = (EcsBlueGreenServiceSetup) getState(stepYaml1);
    EcsBlueGreenServiceSetup state2 = (EcsBlueGreenServiceSetup) getState(stepYaml2);
    return StringUtils.equals(state1.getLoadBalancerName(), state2.getLoadBalancerName())
        && StringUtils.equals(state1.getProdListenerArn(), state2.getProdListenerArn())
        && StringUtils.equals(state1.getProdListenerRuleArn(), state2.getProdListenerRuleArn())
        && StringUtils.equals(state1.getStageListenerArn(), state2.getStageListenerArn())
        && StringUtils.equals(state1.getStageListenerRuleArn(), state2.getStageListenerRuleArn());
  }
}
