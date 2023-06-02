/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.azure.webapp;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cdng.azure.webapp.AzureWebAppSwapSlotStepInfo;
import io.harness.cdng.azure.webapp.AzureWebAppSwapSlotStepNode;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.beans.Workflow;
import software.wings.sm.State;
import software.wings.sm.states.azure.appservices.AzureWebAppSlotSetup;
import software.wings.sm.states.azure.appservices.AzureWebAppSlotSwap;

import java.util.List;
import java.util.Map;

public class AzureSlotSwapMapperImpl extends StepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.AZURE_SWAP_SLOT;
  }

  @Override
  public ServiceDefinitionType inferServiceDef(WorkflowMigrationContext context, GraphNode graphNode) {
    return ServiceDefinitionType.AZURE_WEBAPP;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    AzureWebAppSlotSwap state = new AzureWebAppSlotSwap(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    AzureWebAppSlotSwap state = (AzureWebAppSlotSwap) getState(graphNode);
    AzureWebAppSwapSlotStepNode stepNode = new AzureWebAppSwapSlotStepNode();
    baseSetup(state, stepNode, context.getIdentifierCaseFormat());
    AzureWebAppSwapSlotStepInfo stepInfo =
        AzureWebAppSwapSlotStepInfo.infoBuilder().targetSlot(getTargetSlot(context.getWorkflow(), graphNode)).build();

    stepNode.setAzureWebAppSwapSlotStepInfo(stepInfo);
    return stepNode;
  }

  private ParameterField<String> getTargetSlot(Workflow workflow, GraphNode graphNode) {
    ParameterField<String> result = MigratorUtility.RUNTIME_INPUT;
    List<GraphNode> steps = MigratorUtility.getSteps(workflow);
    if (isNotEmpty(steps)) {
      for (GraphNode step : steps) {
        if ("AZURE_WEBAPP_SLOT_SETUP".equals(step.getType())) {
          Map<String, Object> properties = getProperties(step);
          AzureWebAppSlotSetup state = new AzureWebAppSlotSetup(step.getName());
          state.parseProperties(properties);
          result = ParameterField.createValueField(state.getTargetSlot());
          continue;
        }
        if (graphNode.getId().equals(step.getId())) {
          break;
        }
      }
    }
    return result;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return false;
  }
}
