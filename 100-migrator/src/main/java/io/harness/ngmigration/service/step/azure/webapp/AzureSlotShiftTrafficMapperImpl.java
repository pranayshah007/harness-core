/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.azure.webapp;

import io.harness.cdng.azure.webapp.AzureWebAppTrafficShiftStepInfo;
import io.harness.cdng.azure.webapp.AzureWebAppTrafficShiftStepNode;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.azure.appservices.AzureWebAppSlotShiftTraffic;

import java.util.Map;

public class AzureSlotShiftTrafficMapperImpl extends StepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.AZURE_TRAFFIC_SHIFT;
  }

  @Override
  public ServiceDefinitionType inferServiceDef(WorkflowMigrationContext context, GraphNode graphNode) {
    return ServiceDefinitionType.AZURE_WEBAPP;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    AzureWebAppSlotShiftTraffic state = new AzureWebAppSlotShiftTraffic(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    AzureWebAppSlotShiftTraffic state = (AzureWebAppSlotShiftTraffic) getState(graphNode);
    AzureWebAppTrafficShiftStepNode stepNode = new AzureWebAppTrafficShiftStepNode();
    baseSetup(state, stepNode, context.getIdentifierCaseFormat());
    AzureWebAppTrafficShiftStepInfo stepInfo =
        AzureWebAppTrafficShiftStepInfo.infoBuilder()
            .traffic(ParameterField.createValueField(state.getTrafficWeightExpr()))
            .build();

    stepNode.setAzureWebAppTrafficShiftStepInfo(stepInfo);

    return stepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return false;
  }
}
