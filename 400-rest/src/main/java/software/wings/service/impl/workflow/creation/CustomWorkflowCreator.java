/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.workflow.creation;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.serializer.MapperUtils;

import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class CustomWorkflowCreator extends WorkflowCreator {
  @Override
  public Workflow createWorkflow(Workflow clientWorkflow) {
    Workflow workflow = aWorkflow().build();
    MapperUtils.mapObject(clientWorkflow, workflow);
    return workflow;
  }

  @Override
  public void attachWorkflowPhase(Workflow workflow, WorkflowPhase workflowPhase) {
    // Not supported yet
  }
}
