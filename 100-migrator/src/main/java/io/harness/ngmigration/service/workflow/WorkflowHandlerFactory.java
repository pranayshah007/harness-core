/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.workflow;

import software.wings.beans.Workflow;

import com.google.inject.Inject;

public class WorkflowHandlerFactory {
  @Inject RollingWorkflowHandlerImpl rollingWorkflowHandler;

  public WorkflowHandler getWorkflowHandler(Workflow workflow) {
    switch (workflow.getOrchestration().getOrchestrationWorkflowType()) {
      case ROLLING:
        return rollingWorkflowHandler;
      default:
        throw new UnsupportedOperationException();
    }
  }
}
