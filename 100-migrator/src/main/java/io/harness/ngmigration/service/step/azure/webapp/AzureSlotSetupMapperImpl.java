/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.azure.webapp;

import io.harness.cdng.azure.webapp.AzureWebAppSlotDeploymentStepInfo;
import io.harness.cdng.azure.webapp.AzureWebAppSlotDeploymentStepNode;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.azure.appservices.AzureWebAppSlotSetup;

import java.util.Map;

public class AzureSlotSetupMapperImpl extends StepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.AZURE_SLOT_DEPLOYMENT;
  }

  @Override
  public ServiceDefinitionType inferServiceDef(WorkflowMigrationContext context, GraphNode graphNode) {
    return ServiceDefinitionType.AZURE_WEBAPP;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    AzureWebAppSlotSetup state = new AzureWebAppSlotSetup(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    AzureWebAppSlotSetup state = (AzureWebAppSlotSetup) getState(graphNode);
    AzureWebAppSlotDeploymentStepNode stepNode = new AzureWebAppSlotDeploymentStepNode();
    baseSetup(state, stepNode, context.getIdentifierCaseFormat());
    AzureWebAppSlotDeploymentStepInfo stepInfo =
        AzureWebAppSlotDeploymentStepInfo.infoBuilder()
            .webApp(ParameterField.createValueField(state.getAppService()))
            .deploymentSlot(ParameterField.createValueField(state.getDeploymentSlot()))
            .build();

    stepNode.setAzureWebAppSlotDeploymentStepInfo(stepInfo);

    return stepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return false;
  }
}
