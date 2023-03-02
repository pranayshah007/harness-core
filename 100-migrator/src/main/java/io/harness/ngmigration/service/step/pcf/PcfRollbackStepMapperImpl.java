/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.pcf;

import io.harness.beans.OrchestrationWorkflowType;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.tas.TasRollbackStepInfo;
import io.harness.cdng.tas.TasRollbackStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.plancreator.steps.AbstractStepNode;

import software.wings.beans.GraphNode;
import software.wings.beans.Workflow;
import software.wings.sm.State;
import software.wings.sm.states.pcf.PcfRollbackState;

import java.util.Map;

public class PcfRollbackStepMapperImpl extends PcfAbstractStepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.TAS_ROLLBACK;
  }

  @Override
  public ServiceDefinitionType inferServiceDef(WorkflowMigrationContext context, GraphNode graphNode) {
    return ServiceDefinitionType.TAS;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    PcfRollbackState state = new PcfRollbackState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    PcfRollbackState state = (PcfRollbackState) getState(graphNode);
    Workflow workflow = context.getWorkflow();
    if (workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType() != OrchestrationWorkflowType.BLUE_GREEN) {
      TasRollbackStepNode tasRollbackStepNode = new TasRollbackStepNode();
      baseSetup(state, tasRollbackStepNode);

      TasRollbackStepInfo tasRollbackStepInfo = TasRollbackStepInfo.infoBuilder().build();
      tasRollbackStepNode.setTasRollbackStepInfo(tasRollbackStepInfo);
      return tasRollbackStepNode;
    } else {
      return null;
    }
  }
  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return false;
  }
}
